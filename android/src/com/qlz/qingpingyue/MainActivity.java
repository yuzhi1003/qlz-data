package com.qlz.qingpingyue;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;

// build-tag: v1.4-rebuild-20261128
public class MainActivity extends Activity {

    private static final String LIVE = "https://yuzhi1003.github.io/qlz-data/";
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

        web.loadUrl(LIVE);
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
