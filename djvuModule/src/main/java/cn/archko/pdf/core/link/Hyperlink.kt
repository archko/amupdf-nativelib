package cn.archko.pdf.core.link

import android.graphics.Rect
//import com.artifex.mupdf.fitz.Document
//import com.artifex.mupdf.fitz.Link
//import com.artifex.mupdf.fitz.Location
//import org.vudroid.core.Page

class Hyperlink {
    var linkType = LINKTYPE_PAGE
    var url: String? = null
    var page = 0
    var bbox: Rect? = null
    override fun toString(): String {
        return "Hyperlink{" +
                "linkType=" + linkType +
                ", page=" + page +
                ", bbox=" + bbox +
                ", url='" + url + '\'' +
                '}'
    }

    companion object {

        const val LINKTYPE_PAGE = 0
        const val LINKTYPE_URL = 1

        //documentview
        /*fun mapPointToPage(page: Page, atX: Float, atY: Float): Hyperlink? {
            if (null == page.links) {
                return null
            }
            for (hyper in page.links) {
                if (null != hyper.bbox && hyper.bbox!!.contains(atX.toInt(), atY.toInt())) {
                    return hyper
                }
            }
            return null
        }*/

        //controller
        /*fun mapPointToPage(
            doc: Document?,
            pdfPage: com.artifex.mupdf.fitz.Page,
            atX: Float,
            atY: Float
        ): Hyperlink? {
            if (null == doc) {
                return null
            }
            val links: Array<Link>? = pdfPage.links
            if (links.isNullOrEmpty()) {
                return null
            }
            for (link in links) {
                if (link.bounds.contains(atX, atY)) {
                    val hyper = Hyperlink()
                    val loc: Location = doc.resolveLink(link)
                    val page: Int = doc.pageNumberFromLocation(loc)
                    hyper.page = page
                    if (page >= 0) {
                        hyper.bbox = Rect(0, 0, 0, 0)
                        hyper.url = null
                        hyper.linkType = LINKTYPE_PAGE
                    } else {
                        hyper.bbox = null
                        hyper.url = link.uri
                        hyper.linkType = LINKTYPE_URL
                    }
                    return hyper
                }
            }
            return null
        }*/

    }
}