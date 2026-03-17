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
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        setContentView(R.layout.activity_main);

        initAdDomains();
        initViews();
        setupWebView();

        // Прячем подсказку через 5 секунд
        handler.postDelayed(() -> {
            if (hintText != null) hintText.setVisibility(View.GONE);
        }, 5000);

        if (isNetworkAvailable()) {
            webView.loadUrl(HOME_URL);
        } else {
            showNoInternetDialog();
        }
    }

    private void initAdDomains() {
        String[] domains = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "mc.yandex.ru", "an.yandex.ru", "ad.mail.ru",
            "adfox.ru", "pushme.host", "pushami.com",
            "marketgid.com", "topad.ng", "popunder.net",
            "popads.net", "juicyads.com", "trafficfactory.biz",
            "exoclick.com", "tsyndicate.com", "bidswitch.net",
            "clickadu.com", "propellerads.com", "adsterra.com",
            "hilltopads.net", "trafficjunky.com"
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
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"
        );

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        webView.setWebViewClient(new LordFilmWebViewClient());
        webView.setWebChromeClient(new LordFilmChromeClient());

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
    }

    // ================================================
    // WebViewClient
    // ================================================
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
            Log.d(TAG, "Loading: " + url);

            if (videoInterceptor.isVideoUrl(url)) {
                Log.d(TAG, "Video URL detected: " + url);
                showVideoDialog(url);
                return true;
            }

            if (isAdUrl(url)) {
                Log.d(TAG, "Blocked ad: " + url);
                return true;
            }

            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (isAdUrl(url)) {
                return new WebResourceResponse("text/plain", "utf-8", null);
            }

            if (videoInterceptor.isVideoUrl(url)) {
                Log.d(TAG, "Stream intercepted: " + url);
                handler.post(() -> {
                    if (detectedVideoUrls.add(url)) {
                        showVideoNotification(url);
                    }
                });
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Log.e(TAG, "Error: " + description + " URL: " + failingUrl);
            if (failingUrl != null && failingUrl.equals(HOME_URL)) {
                statusText.setText("Ошибка: " + description);
                statusText.setVisibility(View.VISIBLE);
            }
        }
    }

    // ================================================
    // WebChromeClient
    // ================================================
    private class LordFilmChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            if (newProgress == 100) progressBar.setVisibility(View.GONE);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
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

    // ================================================
    // JavaScript Interface
    // ================================================
    public class WebAppInterface {

        @JavascriptInterface
        public void onVideoFound(String url) {
            Log.d(TAG, "JS video: " + url);
            handler.post(() -> {
                if (url != null && !url.isEmpty() && detectedVideoUrls.add(url)) {
                    showVideoDialog(url);
                }
            });
        }

        @JavascriptInterface
        public void onVideoSourceFound(String url, String title) {
            Log.d(TAG, "JS video source: " + url + " title: " + title);
            handler.post(() -> {
                if (url != null && !url.isEmpty()) {
                    detectedVideoUrls.add(url);
                    showVideoDialog(url, title);
                }
            });
        }

        @JavascriptInterface
        public void onMultipleVideosFound(String urlsJson) {
            Log.d(TAG, "Multiple videos: " + urlsJson);
            handler.post(() -> {
                try {
                    String[] urls = urlsJson
                        .replace("[", "").replace("]", "")
                        .replace("\"", "").split(",");
                    List<String> videoList = new ArrayList<>();
                    for (String u : urls) {
                        String trimmed = u.trim();
                        if (!trimmed.isEmpty()) videoList.add(trimmed);
                    }
                    if (videoList.size() == 1) {
                        showVideoDialog(videoList.get(0));
                    } else if (videoList.size() > 1) {
                        showVideoSelectionDialog(videoList);
                    }
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
        public void log(String message) {
            Log.d(TAG, "JS: " + message);
        }
    }

    // ================================================
    // JavaScript инъекции
    // ================================================
    private void injectVideoInterceptor(WebView view) {
        String js =
            "(function() {" +
            "  if(window._vi) return; window._vi=true;" +
            "  var origCE = document.createElement.bind(document);" +
            "  document.createElement = function(tag) {" +
            "    var el = origCE(tag);" +
            "    if(tag.toLowerCase()==='video') {" +
            "      var oSA = el.setAttribute.bind(el);" +
            "      el.setAttribute = function(n, v) {" +
            "        if(n==='src' && v) AndroidBridge.onVideoFound(v);" +
            "        return oSA(n, v);" +
            "      };" +
            "      var desc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');" +
            "      if(desc) {" +
            "        Object.defineProperty(el, 'src', {" +
            "          set: function(v) { if(v) AndroidBridge.onVideoFound(v); desc.set.call(this, v); }," +
            "          get: function() { return desc.get.call(this); }" +
            "        });" +
            "      }" +
            "    }" +
            "    return el;" +
            "  };" +
            "  function scan() {" +
            "    document.querySelectorAll('video').forEach(function(v) {" +
            "      if(v.src) AndroidBridge.onVideoFound(v.src);" +
            "      if(v.currentSrc) AndroidBridge.onVideoFound(v.currentSrc);" +
            "      v.querySelectorAll('source').forEach(function(s) {" +
            "        if(s.src) AndroidBridge.onVideoFound(s.src);" +
            "      });" +
            "    });" +
            "  }" +
            "  var obs = new MutationObserver(function(muts) {" +
            "    muts.forEach(function(m) {" +
            "      m.addedNodes.forEach(function(n) {" +
            "        if(n.tagName==='VIDEO') {" +
            "          if(n.src) AndroidBridge.onVideoFound(n.src);" +
            "        }" +
            "        if(n.querySelectorAll) {" +
            "          n.querySelectorAll('video').forEach(function(v) {" +
            "            if(v.src) AndroidBridge.onVideoFound(v.src);" +
            "          });" +
            "        }" +
            "      });" +
            "    });" +
            "  });" +
            "  obs.observe(document.body, {childList:true, subtree:true});" +
            "  var oXHR = XMLHttpRequest.prototype.open;" +
            "  XMLHttpRequest.prototype.open = function(m, u) {" +
            "    if(u && (u.indexOf('.m3u8')!==-1 || u.indexOf('.mp4')!==-1)) {" +
            "      AndroidBridge.onVideoFound(u);" +
            "    }" +
            "    return oXHR.apply(this, arguments);" +
            "  };" +
            "  var oF = window.fetch;" +
            "  window.fetch = function(i) {" +
            "    var u = typeof i==='string' ? i : (i.url||'');" +
            "    if(u.indexOf('.m3u8')!==-1 || u.indexOf('.mp4')!==-1) {" +
            "      AndroidBridge.onVideoFound(u);" +
            "    }" +
            "    return oF.apply(this, arguments);" +
            "  };" +
            "  setTimeout(scan, 2000);" +
            "  setTimeout(scan, 5000);" +
            "  setTimeout(scan, 10000);" +
            "})();";

        view.evaluateJavascript(js, null);
    }

    private void injectAdBlocker(WebView view) {
        String js =
            "(function() {" +
            "  var sels = [" +
            "    '.b-sticky_panel','.b-overfloat','.adsbygoogle'," +
            "    '[id*=\"banner\"]:not(video)','[class*=\"banner\"]:not(video)'," +
            "    '[id*=\"advert\"]','[class*=\"advert\"]'," +
            "    '.popup','.overlay-ad','.push-notification'," +
            "    '[onclick*=\"window.open\"]'" +
            "  ];" +
            "  sels.forEach(function(s) {" +
            "    document.querySelectorAll(s).forEach(function(el) {" +
            "      el.style.display='none';" +
            "    });" +
            "  });" +
            "  window.open = function() { return null; };" +
            "  if(window.Notification) {" +
            "    window.Notification.requestPermission = function() {" +
            "      return Promise.resolve('denied');" +
            "    };" +
            "  }" +
            "})();";

        view.evaluateJavascript(js, null);
    }

    private void injectTVNavigation(WebView view) {
        String js =
            "(function() {" +
            "  var s = document.createElement('style');" +
            "  s.textContent = '" +
            "    a:focus,button:focus,input:focus,[tabindex]:focus {" +
            "      outline:3px solid #FFD700 !important;" +
            "      outline-offset:2px !important;" +
            "      box-shadow:0 0 15px rgba(255,215,0,0.5) !important;" +
            "    }" +
            "    ::-webkit-scrollbar{width:8px}" +
            "    ::-webkit-scrollbar-thumb{background:#FFD700;border-radius:4px}" +
            "  ';" +
            "  document.head.appendChild(s);" +
            "  document.querySelectorAll('a,.b-content__inline_item').forEach(function(el) {" +
            "    if(!el.hasAttribute('tabindex')) el.setAttribute('tabindex','0');" +
            "  });" +
            "})();";

        view.evaluateJavascript(js, null);
    }

    // ================================================
    // Диалоги
    // ================================================
    private void showVideoDialog(String url) {
        showVideoDialog(url, "");
    }

    private void showVideoDialog(String url, String title) {
        if (isFinishing()) return;
        String displayTitle = (title != null && !title.isEmpty()) ? title : "Видео найдено";
        String quality = videoInterceptor.detectQuality(url);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("\uD83C\uDFAC " + displayTitle)
            .setMessage("Качество: " + quality + "\n\nОткрыть во внешнем плеере?\n\n" + truncateUrl(url))
            .setPositiveButton("\u25B6 VLC Player", (d, w) ->
                VLCLauncher.launch(this, url, displayTitle))
            .setNeutralButton("\uD83D\uDCCB Выбор плеера", (d, w) ->
                VLCLauncher.launchWithChooser(this, url, displayTitle))
            .setNegativeButton("Отмена", null)
            .setCancelable(true)
            .show();
    }

    private void showVideoSelectionDialog(List<String> urls) {
        if (isFinishing() || urls.isEmpty()) return;
        String[] items = new String[urls.size()];
        for (int i = 0; i < urls.size(); i++) {
            items[i] = videoInterceptor.detectQuality(urls.get(i)) + " \u2014 " + truncateUrl(urls.get(i));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("\uD83C\uDFAC Выберите качество")
            .setItems(items, (d, which) ->
                VLCLauncher.launch(this, urls.get(which), ""))
            .setNegativeButton("Отмена", null)
            .setCancelable(true)
            .show();
    }

    private void showVideoNotification(String url) {
        Toast.makeText(this,
            "\uD83C\uDFAC Видео найдено! MENU \u2192 VLC",
            Toast.LENGTH_LONG).show();
    }

    private void showNoInternetDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Нет подключения")
            .setMessage("Проверьте подключение к интернету")
            .setPositiveButton("Повторить", (d, w) -> {
                if (isNetworkAvailable()) webView.loadUrl(HOME_URL);
                else showNoInternetDialog();
            })
            .setNegativeButton("Выход", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Выход")
            .setMessage("Выйти из приложения?")
            .setPositiveButton("Да", (d, w) -> finish())
            .setNegativeButton("Нет", null)
            .show();
    }

    // ================================================
    // Обработка клавиш пульта
    // ================================================
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (customView != null) {
                    ((LordFilmChromeClient) webView.getWebChromeClient()).onHideCustomView();
                    return true;
                }
                if (webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                showExitDialog();
                return true;

            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                showDetectedVideosMenu();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                scanAndPlayVideo();
                return true;

            case KeyEvent.KEYCODE_BOOKMARK:
            case KeyEvent.KEYCODE_GUIDE:
                webView.loadUrl(HOME_URL);
                return true;

            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (webView.canGoBack()) webView.goBack();
                return true;

            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (webView.canGoForward()) webView.goForward();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showDetectedVideosMenu() {
        if (detectedVideoUrls.isEmpty()) {
            Toast.makeText(this, "Видео пока не найдено. Сканирую...", Toast.LENGTH_SHORT).show();
            scanAndPlayVideo();
            return;
        }
        showVideoSelectionDialog(new ArrayList<>(detectedVideoUrls));
    }

    private void scanAndPlayVideo() {
        String js =
            "(function() {" +
            "  var urls = [];" +
            "  document.querySelectorAll('video').forEach(function(v) {" +
            "    if(v.src) urls.push(v.src);" +
            "    if(v.currentSrc) urls.push(v.currentSrc);" +
            "    v.querySelectorAll('source').forEach(function(s) {" +
            "      if(s.src) urls.push(s.src);" +
            "    });" +
            "  });" +
            "  document.querySelectorAll('iframe').forEach(function(f) {" +
            "    try {" +
            "      var fd = f.contentDocument || f.contentWindow.document;" +
            "      fd.querySelectorAll('video').forEach(function(v) {" +
            "        if(v.src) urls.push(v.src);" +
            "        if(v.currentSrc) urls.push(v.currentSrc);" +
            "      });" +
            "    } catch(e) {}" +
            "  });" +
            "  if(urls.length>0) AndroidBridge.onMultipleVideosFound(JSON.stringify(urls));" +
            "  else AndroidBridge.log('No videos found');" +
            "})();";

        webView.evaluateJavascript(js, null);
    }

    // ================================================
    // Утилиты
    // ================================================
    private boolean isAdUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String domain : adDomains) {
            if (lower.contains(domain)) return true;
        }
        return lower.contains("/ad/") || lower.contains("/ads/") ||
               lower.contains("popunder") || lower.contains("clickunder") ||
               lower.contains("popads");
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        return info != null && info.isConnected();
    }

    private String truncateUrl(String url) {
        if (url == null) return "";
        return url.length() > 80 ? url.substring(0, 80) + "..." : url;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
