package com.lordfilmtv.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String TAG = "LordFilmTV";
    private static final String HOME_URL = "https://nm.lordfilm133.ru/";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView hintText;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> detectedVideoUrls = new HashSet<>();
    private final VideoInterceptor videoInterceptor = new VideoInterceptor();
    private final Set<String> adDomains = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        setContentView(R.layout.activity_main);
        initAdDomains();
        initViews();
        setupWebView();

        handler.postDelayed(() -> {
            if (hintText != null) hintText.setVisibility(View.GONE);
        }, 6000);

        if (isNetworkAvailable()) {
            webView.loadUrl(HOME_URL);
        } else {
            showNoInternetDialog();
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void initAdDomains() {
        String[] domains = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "mc.yandex.ru", "an.yandex.ru",
            "ad.mail.ru", "adfox.ru", "pushme.host", "pushami.com",
            "marketgid.com", "popunder.net", "popads.net",
            "juicyads.com", "trafficfactory.biz", "exoclick.com",
            "tsyndicate.com", "bidswitch.net", "clickadu.com",
            "propellerads.com", "adsterra.com", "hilltopads.net"
        };
        for (String d : domains) adDomains.add(d);
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        hintText = findViewById(R.id.hintText);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        webView.setWebViewClient(new LordFilmWebViewClient());
        webView.setWebChromeClient(new LordFilmChromeClient());
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
    }

    private class LordFilmWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Загрузка...");
            detectedVideoUrls.clear();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            injectVideoInterceptor(view);
            injectAdBlocker(view);
            injectTVNavigation(view);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (videoInterceptor.isVideoUrl(url)) {
                showVideoDialog(url);
                return true;
            }
            if (isAdUrl(url)) return true;
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isAdUrl(url)) {
                return new WebResourceResponse("text/plain", "utf-8", null);
            }
            if (videoInterceptor.isVideoUrl(url)) {
                handler.post(() -> {
                    if (detectedVideoUrls.add(url)) {
                        showVideoNotification();
                    }
                });
            }
            return super.shouldInterceptRequest(view, request);
        }
    }

    private class LordFilmChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int p) {
            progressBar.setProgress(p);
            if (p == 100) progressBar.setVisibility(View.GONE);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) { callback.onCustomViewHidden(); return; }
            customView = view;
            customViewCallback = callback;
            fullscreenContainer.addView(view);
            fullscreenContainer.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            fullscreenContainer.setVisibility(View.GONE);
            fullscreenContainer.removeView(customView);
            customView = null;
            webView.setVisibility(View.VISIBLE);
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void onVideoFound(String url) {
            handler.post(() -> {
                if (url != null && !url.isEmpty() && detectedVideoUrls.add(url)) {
                    showVideoDialog(url);
                }
            });
        }

        @JavascriptInterface
        public void onMultipleVideosFound(String json) {
            handler.post(() -> {
                try {
                    String[] urls = json.replace("[","").replace("]","")
                            .replace("\"","").split(",");
                    List<String> list = new ArrayList<>();
                    for (String u : urls) {
                        String t = u.trim();
                        if (!t.isEmpty()) list.add(t);
                    }
                    if (list.size() == 1) showVideoDialog(list.get(0));
                    else if (list.size() > 1) showVideoSelectionDialog(list);
                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                }
            });
        }

        @JavascriptInterface
        public void playInVLC(String url) {
            handler.post(() -> VLCLauncher.launch(MainActivity.this, url, ""));
        }

        @JavascriptInterface
        public void log(String msg) {
            Log.d(TAG, "JS: " + msg);
        }
    }

    private void injectVideoInterceptor(WebView view) {
        String js =
            "(function(){" +
            "if(window._vi)return;window._vi=true;" +
            "function scan(){" +
            "document.querySelectorAll('video').forEach(function(v){" +
            "if(v.src)AndroidBridge.onVideoFound(v.src);" +
            "if(v.currentSrc)AndroidBridge.onVideoFound(v.currentSrc);" +
            "v.querySelectorAll('source').forEach(function(s){" +
            "if(s.src)AndroidBridge.onVideoFound(s.src);});});}" +
            "var obs=new MutationObserver(function(ml){ml.forEach(function(m){" +
            "m.addedNodes.forEach(function(n){" +
            "if(n.tagName==='VIDEO'){if(n.src)AndroidBridge.onVideoFound(n.src);}" +
            "if(n.querySelectorAll){n.querySelectorAll('video').forEach(function(v){" +
            "if(v.src)AndroidBridge.onVideoFound(v.src);});}});});});" +
            "obs.observe(document.body,{childList:true,subtree:true});" +
            "var oXHR=XMLHttpRequest.prototype.open;" +
            "XMLHttpRequest.prototype.open=function(m,u){" +
            "if(u&&(u.indexOf('.m3u8')!==-1||u.indexOf('.mp4')!==-1))" +
            "AndroidBridge.onVideoFound(u);" +
            "return oXHR.apply(this,arguments);};" +
            "var oF=window.fetch;window.fetch=function(i){" +
            "var u=typeof i==='string'?i:(i&&i.url||'');" +
            "if(u.indexOf('.m3u8')!==-1||u.indexOf('.mp4')!==-1)" +
            "AndroidBridge.onVideoFound(u);" +
            "return oF.apply(this,arguments);};" +
            "setTimeout(scan,2000);setTimeout(scan,5000);setTimeout(scan,10000);" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void injectAdBlocker(WebView view) {
        String js =
            "(function(){" +
            "var s=['.b-sticky_panel','.b-overfloat','.adsbygoogle'," +
            "'[id*=\"banner\"]:not(video)','[class*=\"advert\"]'," +
            "'.popup','.overlay-ad'];" +
            "s.forEach(function(x){document.querySelectorAll(x).forEach(" +
            "function(e){e.style.display='none';});});" +
            "window.open=function(){return null;};" +
            "if(window.Notification)window.Notification.requestPermission=" +
            "function(){return Promise.resolve('denied');};" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void injectTVNavigation(WebView view) {
        String js =
            "(function(){" +
            "var s=document.createElement('style');" +
            "s.textContent='a:focus,button:focus,[tabindex]:focus{" +
            "outline:3px solid #FFD700!important;" +
            "outline-offset:2px!important;" +
            "box-shadow:0 0 15px rgba(255,215,0,0.5)!important;}';" +
            "document.head.appendChild(s);" +
            "document.querySelectorAll('a,.b-content__inline_item').forEach(" +
            "function(e){if(!e.hasAttribute('tabindex'))e.setAttribute('tabindex','0');});" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void showVideoDialog(String url) {
        if (isFinishing()) return;
        String q = videoInterceptor.detectQuality(url);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Видео найдено")
            .setMessage("Качество: " + q + "\n\n" + trunc(url))
            .setPositiveButton("VLC", (d, w) -> VLCLauncher.launch(this, url, ""))
            .setNeutralButton("Выбор", (d, w) -> VLCLauncher.launchWithChooser(this, url, ""))
            .setNegativeButton("Отмена", null)
            .setCancelable(true).show();
    }

    private void showVideoSelectionDialog(List<String> urls) {
        if (isFinishing() || urls.isEmpty()) return;
        String[] items = new String[urls.size()];
        for (int i = 0; i < urls.size(); i++)
            items[i] = videoInterceptor.detectQuality(urls.get(i)) + " - " + trunc(urls.get(i));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Выберите видео")
            .setItems(items, (d, w) -> VLCLauncher.launch(this, urls.get(w), ""))
            .setNegativeButton("Отмена", null).show();
    }

    private void showVideoNotification() {
        Toast.makeText(this, "Видео найдено! MENU = VLC", Toast.LENGTH_LONG).show();
    }

    private void showNoInternetDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Нет интернета")
            .setMessage("Проверьте подключение")
            .setPositiveButton("Повтор", (d, w) -> {
                if (isNetworkAvailable()) webView.loadUrl(HOME_URL);
                else showNoInternetDialog();
            })
            .setNegativeButton("Выход", (d, w) -> finish())
            .setCancelable(false).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (customView != null) {
                    ((LordFilmChromeClient) webView.getWebChromeClient()).onHideCustomView();
                    return true;
                }
                if (webView.canGoBack()) { webView.goBack(); return true; }
                new AlertDialog.Builder(this).setTitle("Выход?")
                    .setPositiveButton("Да", (d, w) -> finish())
                    .setNegativeButton("Нет", null).show();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                if (detectedVideoUrls.isEmpty()) scanPage();
                else showVideoSelectionDialog(new ArrayList<>(detectedVideoUrls));
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                scanPage();
                return true;
            case KeyEvent.KEYCODE_BOOKMARK:
            case KeyEvent.KEYCODE_GUIDE:
                webView.loadUrl(HOME_URL);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void scanPage() {
        String js =
            "(function(){var u=[];document.querySelectorAll('video').forEach(function(v){" +
            "if(v.src)u.push(v.src);if(v.currentSrc)u.push(v.currentSrc);" +
            "v.querySelectorAll('source').forEach(function(s){if(s.src)u.push(s.src);});});" +
            "if(u.length>0)AndroidBridge.onMultipleVideosFound(JSON.stringify(u));" +
            "else AndroidBridge.log('No videos');})();";
        webView.evaluateJavascript(js, null);
    }

    private boolean isAdUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        for (String d : adDomains) if (l.contains(d)) return true;
        return l.contains("/ad/") || l.contains("/ads/") ||
               l.contains("popunder") || l.contains("popads");
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager c = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo i = c != null ? c.getActiveNetworkInfo() : null;
        return i != null && i.isConnected();
    }

    private String trunc(String u) {
        return u == null ? "" : (u.length() > 80 ? u.substring(0, 80) + "..." : u);
    }

    @Override protected void onResume() { super.onResume(); if(webView!=null) webView.onResume(); }
    @Override protected void onPause() { if(webView!=null) webView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if(webView!=null) webView.destroy(); super.onDestroy(); }
}
