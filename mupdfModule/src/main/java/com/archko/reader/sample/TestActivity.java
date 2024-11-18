package com.archko.reader.sample;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.R;
import com.artifex.mupdf.fitz.android.AndroidDrawDevice;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class TestActivity extends AppCompatActivity {

    private static final String TAG = TestActivity.class.getSimpleName();

    private final static int REQUEST_CODE = 42;
    public static final int PERMISSION_CODE = 42042;

    private String pdfFileName;
    private ImageView imageView;
    private RecyclerView recyclerView;
    private ImageAdapter imageAdapter;
    private Document document;
    ExecutorService executors = Executors.newSingleThreadExecutor();
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private int vWidth = 1080;
    private int vHeight = 1880;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StatusBarHelper.hideSystemUI(this);
        StatusBarHelper.setImmerseBarAppearance(getWindow(), true);

        setContentView(R.layout.activity_main);
        findViewById(R.id.btn).setOnClickListener(v -> launchPicker());
        imageView = findViewById(R.id.image);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        imageAdapter = new ImageAdapter(this);
        recyclerView.setAdapter(imageAdapter);
        ColorItemDecoration colorItemDecoration = new ColorItemDecoration(this);
        colorItemDecoration.setDividerHeight(2);
        recyclerView.addItemDecoration(colorItemDecoration);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            1);
                }
            }
        }

        if (null != getIntent() && getIntent().getData() != null) {
            onResult(RESULT_OK, getIntent());
        } else {
            afterViews();
        }
    }

    void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    void afterViews() {
    }

    public static final String readStringFromInputStream(InputStream in) {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int len;
            while ((len = in.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
        } finally {
            closeStream(bos);
        }
        return "";
    }

    public static void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
            }
        }
    }

    private void displayFromUri(Uri uri) {
        pdfFileName = IntentFile.getPath(this, uri);
        View view = findViewById(R.id.content);
        int width = view.getWidth();
        int height = view.getHeight();
        if (width < 0) {
            width = vWidth;
        }
        if (height < 0) {
            height = vHeight;
        }
        System.out.printf("width:%s, height:%s, name:%s%n", width, height, pdfFileName);
        String css = "@page{font-face: 'DroidSans', 'simsun', 'NotoSans-CJK-Regular' ,'MiSansVF', 'menlo' ! important;}";
        /*try {
            InputStream is = getAssets().open("mupdf.css");
            css = readStringFromInputStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        com.artifex.mupdf.fitz.Context.useDocumentCSS(false);
        com.artifex.mupdf.fitz.Context.setUserCSS(css);

        document = Document.openDocument(pdfFileName);
        document.layout(width, height, 36);
        int pageCount = document.countPages();
        /*int page = pageCount > 7 ? 7 : 0;
        Bitmap bitmap = renderBitmap(document, page);
        System.out.printf("decode:%s:%s%n", pageCount, bitmap);
        imageView.setImageBitmap(bitmap);*/
        System.out.printf("decode:%s:", pageCount);
        imageAdapter.notifyDataSetChanged();
    }

    public Bitmap renderBitmap(Document document, int index) {
        float scale = 1f;
        Page page = document.loadPage(index);

        //byte[] bytes = page.textAsHtml2("preserve-whitespace,inhibit-spaces,preserve-images");
        //String content = new String(bytes);
        //System.out.println("content:" + content);

        int width = (int) (page.getBounds().x1 - page.getBounds().x0);
        int height = (int) (page.getBounds().y1 - page.getBounds().y0);
        android.graphics.Rect cropBound = new Rect(0, 0, width, height);
        int pageW;
        int pageH;
        int patchX;
        int patchY;
        //如果页面的缩放为1,那么这时的pageW就是view的宽.
        pageW = (int) (cropBound.width() * scale);
        pageH = (int) (cropBound.height() * scale);

        patchX = (int) (cropBound.left * scale);
        patchY = (int) (cropBound.top * scale);
        Bitmap bitmap = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888);
        com.artifex.mupdf.fitz.Matrix ctm = new com.artifex.mupdf.fitz.Matrix(scale);
        AndroidDrawDevice dev = new AndroidDrawDevice(bitmap, patchX, patchY, 0, 0, pageW, pageH);

        try {
            page.run(dev, ctm, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        dev.close();
        dev.destroy();

        return bitmap;
    }

    public void onResult(int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            Uri uri = intent.getData();
            displayFromUri(uri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPicker();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            onResult(resultCode, data);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        System.out.println("data:" + intent.getDataString());
        if (null != intent && intent.getData() != null) {
            onResult(RESULT_OK, intent);
        }
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageHolder> {
        private LayoutInflater inflater;
        private Context context;

        public ImageAdapter(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView view = new ImageView(context);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
            if (null == lp) {
                lp = new RecyclerView.LayoutParams(1080, 1080);
            } else {
                lp.width = 1080;
                lp.height = 1080;
            }
            view.setImageDrawable(new ColorDrawable(Color.GREEN));
            return new ImageHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return document == null ? 0 : document.countPages();
        }
    }

    private class ImageHolder extends RecyclerView.ViewHolder {

        public ImageHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void bind(int position) {
            ImageView view = (ImageView) itemView;
            view.setTag(position);
            decode(position, view);
        }

        private void decode(int position, ImageView view) {
            executors.execute(() -> {
                Bitmap bitmap = renderBitmap(document, position);
                mHandler.post(() -> update(bitmap, view, position));
            });
        }

        private void update(Bitmap bitmap, ImageView view, int position) {
            int pos = (int) view.getTag();
            //System.out.println(String.format("pos:%s, position:%s, bitmap:%s", pos, position, bitmap));
            if (pos == position && bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                lp.width = width;
                lp.height = height;
                view.setImageBitmap(bitmap);
                return;
            }
            view.setImageBitmap(null);
        }
    }
}
