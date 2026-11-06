package com.narezany.calcm3;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Обёртка вокруг single-file калькулятора из assets/index.html.
 *
 * Страница грузится через https-базу (а не file://), чтобы работали
 * localStorage, история и буфер обмена — иначе WebView считает источник
 * непрозрачным и часть API отваливается.
 */
public class MainActivity extends Activity {

    /** Тот же адрес, что подставляет страница в относительных вызовах. */
    private static final String BASE_URL = "https://calc.local/";

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.setBackgroundColor(0x00000000);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);

        // На Android 13+ системный шрифт рендерится как есть, без синтетики.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            web.getSettings().setAlgorithmicDarkeningAllowed(false);
        }

        web.loadDataWithBaseURL(BASE_URL, readAsset(), "text/html", "UTF-8", null);
    }

    private String readAsset() {
        try (InputStream in = getAssets().open("index.html");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "<html><body style='font-family:sans-serif;padding:24px'>"
                    + "Не удалось прочитать index.html из assets.</body></html>";
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack(); // страница сама закрывает диалог по popstate
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Мост для кнопок «В файл» и «PNG»: страница отдаёт файл в base64,
     * мы кладём его в общую папку «Загрузки».
     */
    private class Bridge {

        @JavascriptInterface
        public void save(String name, String mime, String base64) {
            final byte[] data;
            try {
                data = Base64.decode(base64, Base64.DEFAULT);
            } catch (Exception e) {
                toast("Не удалось разобрать файл");
                return;
            }
            String safe = sanitize(name);
            try {
                String where = saveToDownloads(safe, mime, data);
                toast(where == null ? "Не удалось сохранить" : "Сохранено: " + where);
            } catch (Exception e) {
                toast("Ошибка сохранения: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void toastMsg(String text) {
            toast(text);
        }
    }

    private String saveToDownloads(String name, String mime, byte[] data) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                return null;
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    return null;
                }
                out.write(data);
            }
            return "Загрузки/" + name;
        }

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File file = new File(dir, name);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        return "Загрузки/" + name;
    }

    private static String sanitize(String name) {
        String n = name == null ? "файл" : name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
        return n.isEmpty() ? "файл" : n;
    }

    private void toast(final String text) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show());
    }
}
