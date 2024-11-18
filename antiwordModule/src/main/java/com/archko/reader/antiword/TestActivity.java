package com.archko.reader.antiword;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class TestActivity extends AppCompatActivity {

    private static final String TAG = TestActivity.class.getSimpleName();

    private final static int REQUEST_CODE = 42;
    public static final int PERMISSION_CODE = 42042;

    private String pdfFileName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StatusBarHelper.hideSystemUI(this);
        StatusBarHelper.setImmerseBarAppearance(getWindow(), true);

        setContentView(R.layout.activity_test);
        findViewById(R.id.btn).setOnClickListener(v -> launchPicker());

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
        }
    }

    void afterViews() {
    }

    private void displayFromUri(Uri uri) {
        pdfFileName = IntentFile.getPath(this, uri);
        System.out.println("name:" + pdfFileName);

        if (pdfFileName.endsWith("doc")) {
            LibAntiword.openDocFile(new File(pdfFileName));
        }
        if (pdfFileName.endsWith("docx")) {
            LibAntiword.convertDocxToHtml(new File(pdfFileName), this);
        }
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
}
