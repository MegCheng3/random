package com.danmemo.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

/** Thin shell: starts the Termux launcher (port 28770) and shows it full screen. */
public class MainActivity extends Activity {
    static final String URL = "http://127.0.0.1:28770/";
    WebView web;
    ValueCallback<Uri[]> picker;
    final Handler ui = new Handler(Looper.getMainLooper());
    boolean started = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (picker != null) picker.onReceiveValue(null);
                picker = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(Intent.createChooser(i, "Choose file"), 7); }
                catch (Exception e) { picker = null; return false; }
                return true;
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String u, String ua, String cd, String mime, long len) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) {}
            }
        });
        boot();
    }

    void boot() {
        web.loadDataWithBaseURL(null, "<body style='background:#0B101E;color:#F2C46D;font-family:sans-serif;padding:32px'>"
                + "<h2>Starting server…</h2><p>If this stays here, open Termux once, run "
                + "<code>sh ~/danmemo/install.sh</code> and set <code>allow-external-apps=true</code> in "
                + "<code>~/.termux/termux.properties</code>.</p></body>", "text/html", "utf-8", null);
        new Thread(() -> {
            if (!alive()) {
                startTermux();
                for (int i = 0; i < 40 && !alive(); i++) sleep(500);
            }
            ui.post(() -> {
                if (alive2) web.loadUrl(URL);
                else Toast.makeText(this, "Launcher did not start. Check Termux setup.", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    volatile boolean alive2 = false;

    boolean alive() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(URL + "api/status").openConnection();
            c.setConnectTimeout(800); c.setReadTimeout(800);
            alive2 = c.getResponseCode() == 200;
        } catch (Exception e) { alive2 = false; }
        return alive2;
    }

    void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    void startTermux() {
        try {
            Intent i = new Intent("com.termux.RUN_COMMAND");
            i.setClassName("com.termux", "com.termux.app.RunCommandService");
            i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/home/danmemo/start.sh");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"--no-browser"});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home/danmemo");
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(i);
        } catch (Exception e) {
            ui.post(() -> Toast.makeText(this, "Cannot reach Termux: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == 7 && picker != null) {
            Uri[] r = null;
            if (res == RESULT_OK && d != null) {
                if (d.getData() != null) r = new Uri[]{d.getData()};
                else if (d.getClipData() != null) {
                    r = new Uri[d.getClipData().getItemCount()];
                    for (int i = 0; i < r.length; i++) r[i] = d.getClipData().getItemAt(i).getUri();
                }
            }
            picker.onReceiveValue(r);
            picker = null;
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
