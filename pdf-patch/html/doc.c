// Copyright (C) 2004-2025 Artifex Software, Inc.
//
// This file is part of MuPDF.
//
// MuPDF is free software: you can redistribute it and/or modify it under the
// terms of the GNU Affero General Public License as published by the Free
// Software Foundation, either version 3 of the License, or (at your option)
// any later version.
//
// MuPDF is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
// details.
//
// You should have received a copy of the GNU Affero General Public License
// along with MuPDF. If not, see <https://www.gnu.org/licenses/agpl-3.0.en.html>
//
// Alternative licensing terms are available from the licensor.
// For commercial licensing, see <https://www.artifex.com/> or contact
// Artifex Software, Inc., 39 Mesa Street, Suite 108A, San Francisco,
// CA 94129, USA, for further information.

/* Legacy binary Word (.doc) support.
 *
 * The file is decoded by antiword into DocBook XML, which is then streamed
 * through a small transformer into HTML and handed to the HTML layout
 * engine, exactly like the DOCX/PPTX paths in office.c.
 *
 * Memory notes:
 *  - antiword reads the document straight out of the caller's fz_buffer via
 *    the aw_* / bReadBytes hooks (no temporary input file, no full copy).
 *  - antiword writes its DocBook output to a temporary FILE which is read
 *    back once and immediately discarded.
 *  - the generated HTML is capped at MAX_DOC_HTML_BYTES so a hostile or huge
 *    document cannot exhaust memory in the layout engine.
 *  - antiword keeps a lot of global state, so conversions are serialised with
 *    a native mutex.
 */

#include "mupdf/fitz.h"
#include "html-imp.h"

#include <ctype.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#if defined(MUPDF_ANTIWORD)

/* Serialise antiword conversions. antiword keeps process-global state, so a
 * per-context MuPDF lock is not enough; use a native process-wide mutex. */
#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
static INIT_ONCE g_antiword_once = INIT_ONCE_STATIC_INIT;
static CRITICAL_SECTION g_antiword_cs;
static BOOL CALLBACK
antiword_init_once(PINIT_ONCE once, PVOID param, PVOID *context)
{
	(void)once;
	(void)param;
	(void)context;
	InitializeCriticalSection(&g_antiword_cs);
	return TRUE;
}
static void
antiword_lock(void)
{
	InitOnceExecuteOnce(&g_antiword_once, antiword_init_once, NULL, NULL);
	EnterCriticalSection(&g_antiword_cs);
}
static void
antiword_unlock(void)
{
	LeaveCriticalSection(&g_antiword_cs);
}
#else
#include <pthread.h>
static pthread_mutex_t g_antiword_lock = PTHREAD_MUTEX_INITIALIZER;
static void
antiword_lock(void)
{
	pthread_mutex_lock(&g_antiword_lock);
}
static void
antiword_unlock(void)
{
	pthread_mutex_unlock(&g_antiword_lock);
}
#endif

#include "antiword.h"

/* Hard limit on the generated HTML. */
#define MAX_DOC_HTML_BYTES (16 * 1024 * 1024)
/* Hard limit on the intermediate DocBook payload. */
#define MAX_DOC_DOCBOOK_BYTES (64 * 1024 * 1024)

/* ------------------------------------------------------------------ */
/* In-memory document backing store for antiword's file primitives.    */
/* ------------------------------------------------------------------ */

static const unsigned char *g_doc_data;
static size_t g_doc_len;
static size_t g_doc_pos;
static FILE *g_doc_out;

void
aw_rewind(FILE *pFile)
{
	(void)pFile;
	g_doc_pos = 0;
}

int
aw_getc(FILE *pFile)
{
	(void)pFile;
	if (g_doc_pos >= g_doc_len)
		return EOF;
	return (int)g_doc_data[g_doc_pos++];
}

BOOL
bReadBytes(UCHAR *aucBytes, size_t tMemb, ULONG ulOffset, FILE *pFile)
{
	(void)pFile;
	if (aucBytes == NULL)
		return FALSE;
	if ((size_t)ulOffset > g_doc_len)
		return FALSE;
	if (tMemb > g_doc_len - (size_t)ulOffset)
		return FALSE;
	memcpy(aucBytes, g_doc_data + ulOffset, tMemb);
	return TRUE;
}

/* antiword asks us for the current paragraph style. We turn that into a
 * marker comment in the DocBook stream; the HTML transformer below attaches
 * the alignment/indentation to the following paragraph (or heading). */
void
vMuPDFStoreStyle(diagram_type *pDiag, const style_block_type *pStyle)
{
	if (pDiag == NULL || pStyle == NULL || g_doc_out == NULL)
		return;
	if (pDiag->pOutFile != g_doc_out)
		return;

	fprintf(g_doc_out, "<!--awp a=%u l=%d r=%d f=%d b=%u af=%u-->\n",
		(unsigned)pStyle->ucAlignment,
		(int)pStyle->sLeftIndent,
		(int)pStyle->sRightIndent,
		(int)pStyle->sLeftIndent1,
		(unsigned)pStyle->usBeforeIndent,
		(unsigned)pStyle->usAfterIndent);
}

/* Images are not extracted for the reflowable view; the placeholder written
 * by antiword is dropped by the transformer. */
void
vMuPDFAddEmptyImage(void)
{
}

/* ------------------------------------------------------------------ */
/* DocBook -> HTML transformer                                         */
/* ------------------------------------------------------------------ */

typedef struct
{
	fz_context *ctx;
	fz_output *out;
	size_t written;
	int truncated;

	int heading;		/* current chapter/sectN level, 0 = none */
	char emphasis;		/* 'b', 'i', 'u', 's' or 0 */
	int footnote_depth;

	/* Pending paragraph style, in twips. */
	int ps_valid;
	int ps_align;
	int ps_left;
	int ps_right;
	int ps_indent;
	int ps_before;
	int ps_after;
} doc_html;

static void
put_html(doc_html *d, const char *s, size_t n)
{
	fz_context *ctx = d->ctx;

	if (d->truncated || n == 0)
		return;

	if (d->written + n > MAX_DOC_HTML_BYTES)
	{
		size_t allow = MAX_DOC_HTML_BYTES - d->written;
		if (allow > 0)
		{
			fz_write_data(ctx, d->out, s, allow);
			d->written += allow;
		}
		fz_write_string(ctx, d->out, "\n<p>[Document truncated: too large]</p>\n");
		d->truncated = 1;
		return;
	}

	fz_write_data(ctx, d->out, s, n);
	d->written += n;
}

static void
put_str(doc_html *d, const char *s)
{
	put_html(d, s, strlen(s));
}

/* Emit valid UTF-8 only, dropping C0 control bytes (the image placeholder
 * and various Word field markers end up here). */
static void
put_text(doc_html *d, const char *s, const char *e)
{
	const char *run = s;

	while (s < e)
	{
		unsigned char c = (unsigned char)*s;

		if (c >= 0x20 && c < 0x7f)
		{
			s++;
			continue;
		}

		if (s > run)
			put_html(d, run, (size_t)(s - run));

		if (c == '\t' || c == '\n' || c == '\r')
		{
			put_html(d, s, 1);
			s++;
		}
		else if (c < 0x20 || c == 0x7f)
		{
			/* Drop control characters. */
			s++;
		}
		else
		{
			int n;
			int ok = 1;
			int i;

			if ((c & 0xe0) == 0xc0)
				n = 2;
			else if ((c & 0xf0) == 0xe0)
				n = 3;
			else if ((c & 0xf8) == 0xf0)
				n = 4;
			else
				n = 0;

			if (n == 0 || s + n > e)
				ok = 0;

			if (ok)
			{
				for (i = 1; i < n; i++)
					if (((unsigned char)s[i] & 0xc0) != 0x80)
						ok = 0;
			}
			if (ok && n == 2 && c < 0xc2)
				ok = 0;
			if (ok && n == 3 && c == 0xe0 && (unsigned char)s[1] < 0xa0)
				ok = 0;
			if (ok && n == 3 && c == 0xed && (unsigned char)s[1] >= 0xa0)
				ok = 0;
			if (ok && n == 4 && c == 0xf0 && (unsigned char)s[1] < 0x90)
				ok = 0;
			if (ok && n == 4 && (c > 0xf4 || (c == 0xf4 && (unsigned char)s[1] >= 0x90)))
				ok = 0;

			if (ok)
			{
				put_html(d, s, (size_t)n);
				s += n;
			}
			else
			{
				put_html(d, "?", 1);
				s++;
			}
		}
		run = s;
	}

	if (s > run)
		put_html(d, run, (size_t)(s - run));
}

static void
emit_style(doc_html *d)
{
	char num[64];
	int a, l, r, f, b, af;

	if (!d->ps_valid)
		return;

	a = d->ps_align;
	l = d->ps_left;
	r = d->ps_right;
	f = d->ps_indent;
	b = d->ps_before;
	af = d->ps_after;

	if (l < 0)
		l = 0;
	if (l > 10000)
		l = 10000;
	if (r < 0)
		r = 0;
	if (r > 10000)
		r = 10000;
	if (f < -6000)
		f = -6000;
	if (f > 4000)
		f = 4000;
	if (b < 0)
		b = 0;
	if (b > 4000)
		b = 4000;
	if (af < 0)
		af = 0;
	if (af > 4000)
		af = 4000;

	if (a <= ALIGNMENT_LEFT && l == 0 && r == 0 && f == 0 && b == 0 && af == 0)
		return;

	put_str(d, " style=\"");
	if (a == ALIGNMENT_CENTER)
		put_str(d, "text-align:center;");
	else if (a == ALIGNMENT_RIGHT)
		put_str(d, "text-align:right;");
	else if (a == ALIGNMENT_JUSTIFY)
		put_str(d, "text-align:justify;");
	if (l > 0)
	{
		fz_snprintf(num, sizeof num, "margin-left:%.1fpt;", l / 20.0f);
		put_str(d, num);
	}
	if (r > 0)
	{
		fz_snprintf(num, sizeof num, "margin-right:%.1fpt;", r / 20.0f);
		put_str(d, num);
	}
	if (f != 0)
	{
		fz_snprintf(num, sizeof num, "text-indent:%.1fpt;", f / 20.0f);
		put_str(d, num);
	}
	if (b > 0)
	{
		fz_snprintf(num, sizeof num, "margin-top:%.1fpt;", b / 20.0f);
		put_str(d, num);
	}
	if (af > 0)
	{
		fz_snprintf(num, sizeof num, "margin-bottom:%.1fpt;", af / 20.0f);
		put_str(d, num);
	}
	put_str(d, "\"");
}

static void
open_paragraph(doc_html *d, int is_heading)
{
	char tag[8];

	if (is_heading && d->heading >= 1 && d->heading <= 6)
		fz_snprintf(tag, sizeof tag, "h%d", d->heading);
	else
		strcpy(tag, "p");

	put_str(d, "<");
	put_str(d, tag);
	emit_style(d);
	put_str(d, ">\n");
}

static void
close_paragraph(doc_html *d, int is_heading)
{
	char tag[8];

	if (is_heading && d->heading >= 1 && d->heading <= 6)
		fz_snprintf(tag, sizeof tag, "</h%d>\n", d->heading);
	else
		strcpy(tag, "</p>\n");

	put_str(d, tag);
}

static int
name_is(const char *name, size_t len, const char *lit)
{
	size_t n = strlen(lit);
	return len == n && memcmp(name, lit, n) == 0;
}

static char
emphasis_tag(const char *attrs, const char *end)
{
	const char *p = attrs;

	while (p + 4 <= end)
	{
		if (p[0] == 'r' && p[1] == 'o' && p[2] == 'l' && p[3] == 'e')
		{
			const char *q = p + 4;
			while (q < end && (*q == ' ' || *q == '\t'))
				q++;
			if (q < end && *q == '=')
			{
				char quote;
				const char *v;
				size_t n;

				q++;
				while (q < end && (*q == ' ' || *q == '\t'))
					q++;
				if (q >= end || (*q != '\'' && *q != '"'))
					break;
				quote = *q++;
				v = q;
				while (q < end && *q != quote)
					q++;
				n = (size_t)(q - v);
				if (n == 4 && memcmp(v, "bold", 4) == 0)
					return 'b';
				if (n == 9 && memcmp(v, "underline", 9) == 0)
					return 'u';
				if (n == 13 && memcmp(v, "strikethrough", 13) == 0)
					return 's';
				return 'i';
			}
		}
		p++;
	}

	return 'i';
}

static void
begin_tag(doc_html *d, const char *name, size_t nlen, int selfclose,
	const char *attrs, const char *attrend)
{
	if (name_is(name, nlen, "title"))
	{
		if (selfclose)
			return;
		open_paragraph(d, 1);
	}
	else if (name_is(name, nlen, "para"))
	{
		if (selfclose || d->footnote_depth > 0)
			return;
		open_paragraph(d, 0);
	}
	else if (name_is(name, nlen, "emphasis"))
	{
		char c;
		if (selfclose)
			return;
		c = emphasis_tag(attrs, attrend);
		d->emphasis = c;
		put_str(d, "<");
		put_html(d, &c, 1);
		put_str(d, ">");
	}
	else if (name_is(name, nlen, "superscript"))
	{
		if (!selfclose)
			put_str(d, "<sup>");
	}
	else if (name_is(name, nlen, "subscript"))
	{
		if (!selfclose)
			put_str(d, "<sub>");
	}
	else if (name_is(name, nlen, "itemizedlist"))
	{
		put_str(d, "<ul>\n");
	}
	else if (name_is(name, nlen, "orderedlist"))
	{
		put_str(d, "<ol>\n");
	}
	else if (name_is(name, nlen, "listitem"))
	{
		put_str(d, "<li>\n");
	}
	else if (name_is(name, nlen, "informaltable"))
	{
		put_str(d, "<table style=\"width:100%;border-collapse:collapse;margin:0.5em 0;\">\n");
	}
	else if (name_is(name, nlen, "row"))
	{
		put_str(d, "<tr>\n");
	}
	else if (name_is(name, nlen, "entry"))
	{
		put_str(d, "<td style=\"border:1px solid #999999;padding:0.15em 0.3em;vertical-align:top;\">");
	}
	else if (name_is(name, nlen, "footnote"))
	{
		if (!selfclose)
		{
			put_str(d, "<span class=\"footnote\">");
			d->footnote_depth++;
		}
	}
	else if (name_is(name, nlen, "chapter"))
	{
		d->heading = 1;
	}
	else if (name_is(name, nlen, "sect1"))
	{
		d->heading = 2;
	}
	else if (name_is(name, nlen, "sect2"))
	{
		d->heading = 3;
	}
	else if (name_is(name, nlen, "sect3"))
	{
		d->heading = 4;
	}
	else if (name_is(name, nlen, "sect4"))
	{
		d->heading = 5;
	}
	else if (name_is(name, nlen, "sect5"))
	{
		d->heading = 6;
	}
}

static void
end_tag(doc_html *d, const char *name, size_t nlen)
{
	if (name_is(name, nlen, "title"))
	{
		close_paragraph(d, 1);
	}
	else if (name_is(name, nlen, "para"))
	{
		if (d->footnote_depth == 0)
			close_paragraph(d, 0);
	}
	else if (name_is(name, nlen, "emphasis"))
	{
		if (d->emphasis)
		{
			put_str(d, "</");
			put_html(d, &d->emphasis, 1);
			put_str(d, ">");
			d->emphasis = 0;
		}
	}
	else if (name_is(name, nlen, "superscript"))
	{
		put_str(d, "</sup>");
	}
	else if (name_is(name, nlen, "subscript"))
	{
		put_str(d, "</sub>");
	}
	else if (name_is(name, nlen, "itemizedlist"))
	{
		put_str(d, "</ul>\n");
	}
	else if (name_is(name, nlen, "orderedlist"))
	{
		put_str(d, "</ol>\n");
	}
	else if (name_is(name, nlen, "listitem"))
	{
		put_str(d, "</li>\n");
	}
	else if (name_is(name, nlen, "informaltable"))
	{
		put_str(d, "</table>\n");
	}
	else if (name_is(name, nlen, "row"))
	{
		put_str(d, "</tr>\n");
	}
	else if (name_is(name, nlen, "entry"))
	{
		put_str(d, "</td>\n");
	}
	else if (name_is(name, nlen, "footnote"))
	{
		if (d->footnote_depth > 0)
		{
			put_str(d, "</span>");
			d->footnote_depth--;
		}
	}
	else if (name_is(name, nlen, "chapter"))
	{
		d->heading = 0;
	}
	else if (name_is(name, nlen, "sect1"))
	{
		d->heading = 1;
	}
	else if (name_is(name, nlen, "sect2"))
	{
		d->heading = 2;
	}
	else if (name_is(name, nlen, "sect3"))
	{
		d->heading = 3;
	}
	else if (name_is(name, nlen, "sect4"))
	{
		d->heading = 4;
	}
	else if (name_is(name, nlen, "sect5"))
	{
		d->heading = 5;
	}
}

static void
parse_style_marker(doc_html *d, const char *s, const char *e)
{
	char buf[128];
	size_t n = (size_t)(e - s);
	int a = 0, l = 0, r = 0, f = 0, b = 0, af = 0;

	if (n >= sizeof buf)
		n = sizeof buf - 1;
	memcpy(buf, s, n);
	buf[n] = 0;

	if (sscanf(buf, "a=%d l=%d r=%d f=%d b=%d af=%d", &a, &l, &r, &f, &b, &af) != 6)
		return;

	d->ps_valid = 1;
	d->ps_align = a;
	d->ps_left = l;
	d->ps_right = r;
	d->ps_indent = f;
	d->ps_before = b;
	d->ps_after = af;
}

static const char *
skip_to_after(const char *end, const char *p, const char *lit)
{
	size_t n = strlen(lit);

	while (p + n <= end)
	{
		if (memcmp(p, lit, n) == 0)
			return p + n;
		p++;
	}
	return end;
}

static void
docbook_to_html(doc_html *d, const char *s, size_t len)
{
	const char *p = s;
	const char *end = s + len;

	while (p < end && !d->truncated)
	{
		const char *lt;

		lt = p;
		while (lt < end && *lt != '<')
			lt++;
		if (lt > p)
		{
			put_text(d, p, lt);
			p = lt;
			continue;
		}

		/* At a '<'. */
		if (p + 1 < end && p[1] == '!')
		{
			if (p + 3 < end && p[2] == '-' && p[3] == '-')
			{
				const char *close = skip_to_after(end, p + 4, "-->");
				if (close == end)
				{
					p = end;
					break;
				}
				if (p + 8 <= end && memcmp(p, "<!--awp ", 8) == 0)
					parse_style_marker(d, p + 8, close - 3);
				p = close;
				continue;
			}
			/* <!DOCTYPE ...> and friends. */
			{
				const char *q = p;
				while (q < end && *q != '>')
					q++;
				p = (q < end) ? q + 1 : end;
				continue;
			}
		}
		if (p + 1 < end && p[1] == '?')
		{
			const char *q = skip_to_after(end, p + 2, "?>");
			p = q;
			continue;
		}

		{
			const char *q = p + 1;
			const char *name;
			const char *gt;
			size_t nlen;
			int closing = 0;
			int selfclose = 0;

			if (q < end && *q == '/')
			{
				closing = 1;
				q++;
			}
			name = q;
			while (q < end && !isspace((unsigned char)*q) && *q != '>' && *q != '/')
				q++;
			nlen = (size_t)(q - name);

			gt = q;
			while (gt < end && *gt != '>')
				gt++;

			if (gt < end && gt > p && gt[-1] == '/')
				selfclose = 1;

			if (!closing && !selfclose && name_is(name, nlen, "bookinfo"))
			{
				p = skip_to_after(end, (gt < end) ? gt + 1 : end, "</bookinfo>");
				continue;
			}
			if (!closing && name_is(name, nlen, "title") && d->heading == 0)
			{
				if (selfclose)
					p = (gt < end) ? gt + 1 : end;
				else
					p = skip_to_after(end, (gt < end) ? gt + 1 : end, "</title>");
				continue;
			}

			if (closing)
				end_tag(d, name, nlen);
			else
				begin_tag(d, name, nlen, selfclose, q, gt);

			p = (gt < end) ? gt + 1 : end;
		}
	}
}

static fz_buffer *
doc_antiword_to_html(fz_context *ctx, fz_buffer *buf)
{
	fz_buffer *html_buf = NULL;
	fz_output *out = NULL;
	FILE *out_file = NULL;
	diagram_type *diag = NULL;
	unsigned char *docbook = NULL;
	unsigned char *data;
	size_t blen;
	long sz;
	options_type opts;
	doc_html d;

	memset(&d, 0, sizeof d);

	antiword_lock();
	fz_var(out);
	fz_var(diag);
	fz_var(out_file);
	fz_var(docbook);
	fz_var(html_buf);

	fz_try(ctx)
	{
		blen = fz_buffer_storage(ctx, buf, &data);

		if (blen < 8)
			fz_throw(ctx, FZ_ERROR_FORMAT, "Not a binary Word (OLE2) document");
		if (blen > (size_t)LONG_MAX)
			fz_throw(ctx, FZ_ERROR_FORMAT, "Binary Word document is too large");

		/* OLE2 compound document magic. */
		if (data[0] != 0xd0 || data[1] != 0xcf || data[2] != 0x11 || data[3] != 0xe0 ||
			data[4] != 0xa1 || data[5] != 0xb1 || data[6] != 0x1a || data[7] != 0xe1)
		{
			if (data[0] == 'P' && data[1] == 'K')
				fz_throw(ctx, FZ_ERROR_FORMAT, "Not a binary Word document (looks like OOXML)");
			fz_throw(ctx, FZ_ERROR_FORMAT, "Not a binary Word (OLE2) document");
		}

		g_doc_data = data;
		g_doc_len = blen;
		g_doc_pos = 0;

		out_file = tmpfile();
		if (out_file == NULL)
			fz_throw(ctx, FZ_ERROR_SYSTEM, "cannot create temporary file for .doc conversion");
		g_doc_out = out_file;

		memset(&opts, 0, sizeof opts);
		opts.iParagraphBreak = DEFAULT_SCREEN_WIDTH;
		opts.eConversionType = conversion_xml;
		opts.bHideHiddenText = TRUE;
		opts.bRemoveRemovedText = TRUE;
		opts.bUseLandscape = FALSE;
		opts.eEncoding = encoding_utf_8;
		opts.iPageHeight = INT_MAX;
		opts.iPageWidth = INT_MAX;
		opts.eImageLevel = level_no_images;
		vSetOptions(&opts);

		diag = pCreateDiagram("mupdf", "document.doc");
		if (diag == NULL)
			fz_throw(ctx, FZ_ERROR_ARGUMENT, "unable to initialise antiword");
		diag->pOutFile = out_file;

		if (!bWordDecryptor((FILE *)(void *)&g_doc_pos, (long)blen, diag))
		{
			vCloseFont();
			vFreeDocument();
			vMuPDFResetXML();
			diag = xfree(diag);
			diag = NULL;
			fz_throw(ctx, FZ_ERROR_FORMAT, "antiword failed to parse the document");
		}

		vDestroyDiagram(diag);
		diag = NULL;

		fflush(out_file);
		if (fseek(out_file, 0, SEEK_END) != 0)
			fz_throw(ctx, FZ_ERROR_SYSTEM, "cannot seek .doc conversion output");
		sz = ftell(out_file);
		if (sz <= 0)
			fz_throw(ctx, FZ_ERROR_FORMAT, "empty .doc conversion output");
		if (sz > MAX_DOC_DOCBOOK_BYTES)
			sz = MAX_DOC_DOCBOOK_BYTES;
		if (fseek(out_file, 0, SEEK_SET) != 0)
			fz_throw(ctx, FZ_ERROR_SYSTEM, "cannot rewind .doc conversion output");

		docbook = fz_malloc(ctx, (size_t)sz + 1);
		if (fread(docbook, 1, (size_t)sz, out_file) != (size_t)sz)
			fz_throw(ctx, FZ_ERROR_SYSTEM, "cannot read .doc conversion output");
		docbook[sz] = 0;

		html_buf = fz_new_buffer(ctx, 4096);
		out = fz_new_output_with_buffer(ctx, html_buf);

		d.ctx = ctx;
		d.out = out;
		d.heading = 0;
		d.emphasis = 0;
		d.footnote_depth = 0;
		d.ps_valid = 0;

		put_str(&d,
			"<html><head><meta charset=\"utf-8\"/><style>"
			"@page{margin:1.5em 2em}"
			"body{margin:0.5em 1em}"
			"p{margin:0.6em 0}"
			"h1,h2,h3,h4,h5,h6{margin:0.8em 0 0.4em}"
			"table{margin:0.5em 0;border-collapse:collapse}"
			"td,th{border:1px solid #999999;padding:0.15em 0.3em;vertical-align:top}"
			"</style></head><body>\n");

		docbook_to_html(&d, (const char *)docbook, (size_t)sz);

		/* Always close the document even when truncated. */
		fz_write_string(ctx, out, "</body></html>\n");

		fz_close_output(ctx, out);
		fz_drop_output(ctx, out);
		out = NULL;
	}
	fz_always(ctx)
	{
		if (out)
			fz_drop_output(ctx, out);
		if (diag)
			vDestroyDiagram(diag);
		if (out_file)
			fclose(out_file);
		fz_free(ctx, docbook);
		g_doc_data = NULL;
		g_doc_len = 0;
		g_doc_pos = 0;
		g_doc_out = NULL;
		antiword_unlock();
	}
	fz_catch(ctx)
	{
		fz_drop_buffer(ctx, html_buf);
		fz_rethrow(ctx);
	}

	return html_buf;
}

#else /* !MUPDF_ANTIWORD */

static fz_buffer *
doc_antiword_to_html(fz_context *ctx, fz_buffer *buf)
{
	(void)buf;
	fz_throw(ctx, FZ_ERROR_UNSUPPORTED, "Binary Word support not enabled");
	return NULL;
}

#endif /* MUPDF_ANTIWORD */

static const fz_htdoc_format fz_htdoc_doc =
{
	"Word document",
	NULL,
	0, 1,
	FZ_HTML_FLAVOR_DEFAULT
};

fz_document *
fz_open_doc_document_with_buffer(fz_context *ctx, fz_buffer *buf)
{
	fz_buffer *html = NULL;
	fz_buffer *convert = NULL;
	fz_document *doc = NULL;

	fz_var(html);
	fz_var(doc);

	fz_try(ctx)
	{
		html = doc_antiword_to_html(ctx, buf);

		/* fz_htdoc_open_document_with_buffer takes ownership of the
		 * buffer even on failure, so hand it over before calling. */
		convert = html;
		html = NULL;
		doc = fz_htdoc_open_document_with_buffer(ctx, NULL, convert, &fz_htdoc_doc);
	}
	fz_always(ctx)
	{
		fz_drop_buffer(ctx, html);
	}
	fz_catch(ctx)
	{
		fz_rethrow(ctx);
	}

	return doc;
}
