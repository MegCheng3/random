package com.danmemo.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.HttpURLConnection;
import java.net.URL;

/** Thin shell: starts the Termux launcher (port 28770) and shows it full screen. */
public class MainActivity extends Activity {
    static final String URL = "http://127.0.0.1:28770/";
    static final String PERM = "com.termux.permission.RUN_COMMAND";
    static final String HOME = "/data/data/com.termux/files/home/danmemo";

    WebView web;
    ValueCallback<Uri[]> picker;
    final Handler ui = new Handler(Looper.getMainLooper());
    boolean booting = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("dm://retry")) { boot(); return true; }
                if (url.startsWith("dm://termux")) { openTermux(); return true; }
                return false;
            }
        });
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
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) {
            try { requestPermissions(new String[]{PERM}, 9); return; } catch (Exception ignored) {}
        }
        boot();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        boot();
    }

    String esc(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    void page(String title, String body, boolean buttons) {
        String btn = "display:inline-block;margin:6px 10px 6px 0;padding:12px 18px;border-radius:10px;"
                + "background:#F2C46D;color:#0B101E;font-weight:bold;text-decoration:none";
        String h = "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<body style=\"background:#0B101E;color:#F2C46D;font-family:sans-serif;padding:28px\">"
                + "<h2>" + esc(title) + "</h2>"
                + "<p style=\"color:#E8E3D5;line-height:1.5\">" + esc(body) + "</p>";
        if (buttons) {
            h += "<p><a href='dm://retry' style=\"" + btn + "\">Try again</a>"
                    + "<a href='dm://termux' style=\"" + btn + "\">Open Termux</a></p>"
                    + "<p style=\"color:#9AA3B8;font-size:14px;line-height:1.5\">Manual start: open Termux and run "
                    + "<code>sh ~/danmemo/start.sh --no-browser</code>, then tap Try again.</p>";
        }
        h += "</body>";
        web.loadDataWithBaseURL(null, h, "text/html", "utf-8", null);
    }

    void boot() {
        if (booting) return;
        booting = true;
        page("Starting server…", "Please wait up to 30 seconds.", false);
        new Thread(() -> {
            String problem = null;
            if (!alive()) {
                problem = startTermux();
                for (int i = 0; i < 60 && !alive(); i++) sleep(500);
            }
            final boolean ok = alive();
            final String p = problem;
            ui.post(() -> {
                booting = false;
                if (ok) web.loadUrl(URL);
                else page("Launcher did not start",
                        p != null ? p : "The request reached Termux but the launcher did not come up. "
                                + "Check that install.sh finished, that ~/danmemo/start.sh exists, "
                                + "and that allow-external-apps=true is in ~/.termux/termux.properties "
                                + "(then fully close and reopen Termux once).", true);
            });
        }).start();
    }

    boolean alive() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(URL + "api/status").openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            boolean ok = c.getResponseCode() == 200;
            c.disconnect();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /** Returns null when the request was sent, else a readable reason. */
    String startTermux() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
        } catch (Exception e) {
            return "Termux is not installed on this device. Install Termux from F-Droid, then run install.sh in it.";
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) {
            return "Permission missing. Open Android Settings > Apps > DanMemoRe > Permissions and allow "
                    + "\"Run commands in Termux environment\" (it may be under Additional permissions).";
        }
        try {
            Intent i = new Intent("com.termux.RUN_COMMAND");
            i.setClassName("com.termux", "com.termux.app.RunCommandService");
            i.putExtra("com.termux.RUN_COMMAND_PATH", HOME + "/start.sh");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"--no-browser"});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME);
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            return null;
        } catch (SecurityException e) {
            return "Android or Termux refused the request: " + e.getMessage()
                    + ". Check allow-external-apps=true in ~/.termux/termux.properties and restart Termux.";
        } catch (Exception e) {
            return "Could not reach Termux: " + e;
        }
    }

    void openTermux() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (i != null) startActivity(i);
        } catch (Exception ignored) {}
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == 7 && picker != null) {
            Uri[] r = null;
            if (res == RESULT_OK && d != null) {
                if (d.getData() != null) {
                    r = new Uri[]{d.getData()};
                } else if (d.getClipData() != null) {
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
