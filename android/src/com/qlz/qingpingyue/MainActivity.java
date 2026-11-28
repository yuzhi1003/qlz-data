package com.qlz.qingpingyue;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// build-tag: v1.7-notify-20261128
public class MainActivity extends Activity {

    private static final String LIVE = "https://yuzhi1003.github.io/qlz-data/index.html";
    private static final String LOCAL = "https://qlz.local/index.html";

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 1001;
    private boolean fellBack = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                String host = u.getHost() == null ? "" : u.getHost();
                if ("qlz.local".equals(host)) return false;
                if ("yuzhi1003.github.io".equals(host)) return false;
                if (scheme.startsWith("http") || scheme.startsWith("mailto") || scheme.startsWith("tel")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame() && !fellBack) {
                    fellBack = true;
                    v.loadUrl(LOCAL);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("qlz.local".equals(u.getHost())) {
                    String path = u.getPath() == null ? "/index.html" : u.getPath();
                    if (path.startsWith("/")) path = path.substring(1);
                    if (path.isEmpty()) path = "index.html";
                    try {
                        InputStream in = getAssets().open(path);
                        String mime = path.endsWith(".js") ? "application/javascript"
                                : path.endsWith(".css") ? "text/css"
                                : path.endsWith(".png") ? "image/png"
                                : path.endsWith(".webp") ? "image/webp"
                                : path.endsWith(".mp3") ? "audio/mpeg"
                                : "text/html";
                        return new WebResourceResponse(mime, "utf-8", in);
                    } catch (IOException e) {
                        return null;
                    }
                }
                return null;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                filePathCallback = cb;
                try {
                    Intent i = params.createIntent();
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(i, REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener(new android.webkit.DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { }
            }
        });

        web.addJavascriptInterface(new Bridge(), "QLZNative");
        ensureChannel();
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2002); } catch (Exception e) { }
        }
        web.loadUrl(LIVE + "?v=" + (System.currentTimeMillis()/60000));   // 加时间戳，绕过缓存，永远拿最新网页
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                if (data.getData() != null) out = new Uri[]{data.getData()};
                else if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    out = new Uri[n];
                    for (int i = 0; i < n; i++) out[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(out);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    private void handleBack() {
        if (web == null) { finish(); return; }
        web.evaluateJavascript(
            "(function(){try{return (typeof window.qlzBack==='function' && window.qlzBack())?'1':'0';}catch(err){return '0';}})()",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    boolean handled = value != null && value.indexOf('1') > -1;
                    if (handled) return;
                    if (web != null && web.canGoBack()) web.goBack();
                    else finish();
                }
            });
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) { handleBack(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override
    public void onBackPressed() { handleBack(); }

    /* ---------- 原生桥：通知 / 备份 ---------- */
    public class Bridge {
        @JavascriptInterface
        public void notifyMsg(String title, String body, int count) {
            pushNotify(title, body, count);
        }
        @JavascriptInterface
        public void saveBackup(String name, String content) {
            writeBackup(name, content);
        }
        @JavascriptInterface
        public String platform() { return "android"; }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel("qlz_msg", "清平乐消息", NotificationManager.IMPORTANCE_HIGH);
            ch.setShowBadge(true);
            ch.setDescription("群聊与私聊提醒");
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
    }

    private void pushNotify(final String title, final String body, final int count) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureChannel();
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (nm == null) return;
                    Intent it = new Intent(MainActivity.this, MainActivity.class);
                    it.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0, it,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Notification.Builder b = Build.VERSION.SDK_INT >= 26
                            ? new Notification.Builder(MainActivity.this, "qlz_msg")
                            : new Notification.Builder(MainActivity.this);
                    b.setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(title == null ? "清平乐" : title)
                            .setContentText(body == null ? "有新消息" : body)
                            .setAutoCancel(true)
                            .setContentIntent(pi)
                            .setNumber(count < 0 ? 0 : count)
                            .setShowWhen(true);
                    if (Build.VERSION.SDK_INT >= 21) b.setColor(0xFF35695B);
                    nm.notify(1001, b.build());
                } catch (Exception e) { }
            }
        });
    }

    private void writeBackup(final String name, final String content) {
        try {
            byte[] data = content.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download");
                Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IOException("无法创建文件");
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data); os.flush(); os.close();
                toast("备份已存到「下载」文件夹：" + name);
            } else {
                File dir = getExternalFilesDir(null);
                File f = new File(dir, name);
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(data); fo.flush(); fo.close();
                toast("备份已存到：" + f.getAbsolutePath());
            }
        } catch (Exception e) {
            toast("备份失败：" + e.getMessage());
        }
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show(); }
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
