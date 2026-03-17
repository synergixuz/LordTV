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
import java.util.LinkedHashSet;
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

    // Разделяем: плейлисты (ценные) и сегменты (для определения базового URL)
    private final LinkedHashSet<String> playlistUrls = new LinkedHashSet<>();
    private final LinkedHashSet<String> cdnBaseUrls = new LinkedHashSet<>();
    private final Set<String> seenSegmentDomains = new HashSet<>();
    private final Set<String> notifiedUrls = new HashSet<>();

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

        // Десктопный User-Agent — важно для lordfilm
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

    // ============================================================
    // WebViewClient — перехват ВСЕХ запросов
    // ============================================================
    private class LordFilmWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Загрузка...");
            // Очищаем при переходе на новую страницу
            playlistUrls.clear();
            cdnBaseUrls.clear();
            seenSegmentDomains.clear();
            notifiedUrls.clear();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            injectAllScripts(view);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isAdUrl(url)) return true;
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Блокируем рекламу
            if (isAdUrl(url)) {
                return new WebResourceResponse("text/plain", "utf-8", null);
            }

            // === КЛЮЧЕВОЕ: перехватываем ВСЕ видео-запросы ===
            processInterceptedUrl(url);

            return super.shouldInterceptRequest(view, request);
        }
    }

    /**
     * Анализирует перехваченный URL на наличие видео-контента
     */
    private void processInterceptedUrl(String url) {
        if (url == null || url.isEmpty()) return;

        // 1. Прямые видео-файлы (.m3u8, .mp4 и т.д.)
        if (videoInterceptor.isVideoUrl(url)) {
            Log.d(TAG, "VIDEO URL: " + url);
            handler.post(() -> addPlaylistUrl(url));
            return;
        }

        // 2. CDN-потоки (включая зашифрованные как interkh.com)
        if (videoInterceptor.isCdnVideoStream(url)) {
            Log.d(TAG, "CDN STREAM: " + url);

            if (videoInterceptor.isHlsPlaylist(url)) {
                // Это плейлист — самое ценное
                handler.post(() -> addPlaylistUrl(url));
            } else if (videoInterceptor.isVideoSegment(url)) {
                // Это сегмент — пробуем извлечь базовый URL
                handler.post(() -> processSegmentUrl(url));
            } else {
                // Неизвестный тип CDN запроса — сохраняем
                handler.post(() -> addPlaylistUrl(url));
            }
            return;
        }

        // 3. iframe-плееры (kodik, hdvb и т.д.)
        if (videoInterceptor.isPlayerIframe(url)) {
            Log.d(TAG, "PLAYER IFRAME: " + url);
        }
    }

    /**
     * Добавляет URL плейлиста/видео и уведомляет пользователя
     */
    private void addPlaylistUrl(String url) {
        if (playlistUrls.add(url)) {
            Log.d(TAG, "Added playlist: " + url + " (total: " + playlistUrls.size() + ")");
            if (notifiedUrls.add(url)) {
                Toast.makeText(this,
                        "\uD83C\uDFAC Видео #" + playlistUrls.size() + " найдено! MENU → VLC",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Обрабатывает URL сегмента — извлекает базовый путь для плейлиста
     */
    private void processSegmentUrl(String segmentUrl) {
        // Извлекаем домен
        try {
            String domain = segmentUrl.split("/")[2];
            if (seenSegmentDomains.add(domain)) {
                Log.d(TAG, "New CDN domain: " + domain);

                // Пробуем угадать URL плейлиста
                String guessedPlaylist = videoInterceptor.guessPlaylistUrl(segmentUrl);
                if (guessedPlaylist != null) {
                    cdnBaseUrls.add(guessedPlaylist);
                    Log.d(TAG, "Guessed playlist: " + guessedPlaylist);
                }

                // Также сохраняем базовый путь CDN
                int pathEnd = segmentUrl.lastIndexOf('/');
                if (pathEnd > 0) {
                    String basePath = segmentUrl.substring(0, pathEnd + 1);
                    cdnBaseUrls.add(basePath);
                }

                if (notifiedUrls.isEmpty()) {
                    Toast.makeText(this,
                            "\uD83C\uDFAC CDN обнаружен: " + domain + " | MENU → VLC",
                            Toast.LENGTH_SHORT).show();
                    notifiedUrls.add(domain);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing segment URL", e);
        }
    }

    // ============================================================
    // WebChromeClient
    // ============================================================
    private class LordFilmChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int p) {
            progressBar.setProgress(p);
            if (p == 100) progressBar.setVisibility(View.GONE);
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

    // ============================================================
    // JavaScript Interface
    // ============================================================
    public class WebAppInterface {

        @JavascriptInterface
        public void onVideoFound(String url) {
            Log.d(TAG, "JS onVideoFound: " + url);
            handler.post(() -> addPlaylistUrl(url));
        }

        @JavascriptInterface
        public void onHlsFound(String url) {
            Log.d(TAG, "JS onHlsFound: " + url);
            handler.post(() -> addPlaylistUrl(url));
        }

        @JavascriptInterface
        public void onIframePlayerFound(String url) {
            Log.d(TAG, "JS iframe player: " + url);
            handler.post(() -> {
                // Если нашли iframe плеера — запоминаем
                if (url != null && !url.isEmpty()) {
                    addPlaylistUrl(url);
                }
            });
        }

        @JavascriptInterface
        public void onMultipleVideosFound(String json) {
            handler.post(() -> {
                try {
                    String[] urls = json.replace("[", "").replace("]", "")
                            .replace("\"", "").split(",");
                    for (String u : urls) {
                        String t = u.trim();
                        if (!t.isEmpty()) addPlaylistUrl(t);
                    }
                    showVideoMenu();
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

    // ============================================================
    // JavaScript инъекции — РАСШИРЕННЫЕ
    // ============================================================
    private void injectAllScripts(WebView view) {
        injectVideoInterceptor(view);
        injectIframeScanner(view);
        injectAdBlocker(view);
        injectTVNavigation(view);

        // Повторное сканирование через задержку (плееры загружаются позже)
        handler.postDelayed(() -> {
            if (view != null) {
                injectDeepVideoScan(view);
            }
        }, 3000);
        handler.postDelayed(() -> {
            if (view != null) {
                injectDeepVideoScan(view);
            }
        }, 7000);
        handler.postDelayed(() -> {
            if (view != null) {
                injectDeepVideoScan(view);
            }
        }, 12000);
    }

    private void injectVideoInterceptor(WebView view) {
        String js =
                "(function(){" +
                        "if(window._vi)return;window._vi=true;" +

                        // Перехват XHR
                        "var oXHR=XMLHttpRequest.prototype.open;" +
                        "XMLHttpRequest.prototype.open=function(m,u){" +
                        "  try{" +
                        "    if(u){" +
                        "      var ul=u.toLowerCase();" +
                        "      if(ul.indexOf('.m3u8')!==-1||ul.indexOf('.mp4')!==-1||" +
                        "         ul.indexOf('.mpd')!==-1||ul.indexOf('playlist')!==-1||" +
                        "         ul.indexOf('manifest')!==-1||ul.indexOf('/hls/')!==-1||" +
                        "         ul.indexOf('interkh.com')!==-1||ul.indexOf('/stream/')!==-1){" +
                        "        AndroidBridge.onVideoFound(u);" +
                        "      }" +
                        "    }" +
                        "  }catch(e){}" +
                        "  return oXHR.apply(this,arguments);" +
                        "};" +

                        // Перехват fetch
                        "var oF=window.fetch;" +
                        "window.fetch=function(i){" +
                        "  try{" +
                        "    var u=typeof i==='string'?i:(i&&i.url||'');" +
                        "    var ul=u.toLowerCase();" +
                        "    if(ul.indexOf('.m3u8')!==-1||ul.indexOf('.mp4')!==-1||" +
                        "       ul.indexOf('interkh.com')!==-1||ul.indexOf('/hls/')!==-1){" +
                        "      AndroidBridge.onVideoFound(u);" +
                        "    }" +
                        "  }catch(e){}" +
                        "  return oF.apply(this,arguments);" +
                        "};" +

                        // Перехват createElement для video элементов
                        "var origCE=document.createElement.bind(document);" +
                        "document.createElement=function(tag){" +
                        "  var el=origCE(tag);" +
                        "  if(tag.toLowerCase()==='video'){" +
                        "    var oSA=el.setAttribute.bind(el);" +
                        "    el.setAttribute=function(n,v){" +
                        "      if(n==='src'&&v)AndroidBridge.onVideoFound(v);" +
                        "      return oSA(n,v);" +
                        "    };" +
                        "    try{" +
                        "      var desc=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src');" +
                        "      if(desc){" +
                        "        Object.defineProperty(el,'src',{" +
                        "          set:function(v){if(v)AndroidBridge.onVideoFound(v);desc.set.call(this,v);}," +
                        "          get:function(){return desc.get.call(this);}" +
                        "        });" +
                        "      }" +
                        "    }catch(e){}" +
                        "  }" +
                        "  return el;" +
                        "};" +

                        // MutationObserver для динамически добавленных элементов
                        "var obs=new MutationObserver(function(ml){ml.forEach(function(m){" +
                        "  m.addedNodes.forEach(function(n){" +
                        "    try{" +
                        "      if(n.tagName==='VIDEO'){" +
                        "        if(n.src)AndroidBridge.onVideoFound(n.src);" +
                        "        if(n.currentSrc)AndroidBridge.onVideoFound(n.currentSrc);" +
                        "      }" +
                        "      if(n.tagName==='SOURCE'&&n.src)AndroidBridge.onVideoFound(n.src);" +
                        "      if(n.tagName==='IFRAME'&&n.src)AndroidBridge.onIframePlayerFound(n.src);" +
                        "      if(n.querySelectorAll){" +
                        "        n.querySelectorAll('video,source,iframe').forEach(function(el){" +
                        "          if(el.src)AndroidBridge.onVideoFound(el.src);" +
                        "        });" +
                        "      }" +
                        "    }catch(e){}" +
                        "  });" +
                        "});});" +
                        "obs.observe(document.body||document.documentElement,{childList:true,subtree:true});" +

                        // Перехват Hls.js (если используется)
                        "if(window.Hls){" +
                        "  var origHls=window.Hls;" +
                        "  var origLoad=origHls.prototype.loadSource;" +
                        "  if(origLoad){" +
                        "    origHls.prototype.loadSource=function(u){" +
                        "      AndroidBridge.onHlsFound(u);" +
                        "      return origLoad.call(this,u);" +
                        "    };" +
                        "  }" +
                        "}" +

                        // Начальное сканирование
                        "function scan(){" +
                        "  document.querySelectorAll('video').forEach(function(v){" +
                        "    if(v.src)AndroidBridge.onVideoFound(v.src);" +
                        "    if(v.currentSrc)AndroidBridge.onVideoFound(v.currentSrc);" +
                        "    v.querySelectorAll('source').forEach(function(s){" +
                        "      if(s.src)AndroidBridge.onVideoFound(s.src);" +
                        "    });" +
                        "  });" +
                        "}" +
                        "scan();" +
                        "setTimeout(scan,2000);" +
                        "setTimeout(scan,5000);" +
                        "})();";

        view.evaluateJavascript(js, null);
    }

    /**
     * Сканирование iframe — плееры lordfilm часто в iframe
     */
    private void injectIframeScanner(WebView view) {
        String js =
                "(function(){" +
                        // Находим все iframe
                        "document.querySelectorAll('iframe').forEach(function(f){" +
                        "  var src=f.src||f.getAttribute('data-src')||'';" +
                        "  if(src){" +
                        "    AndroidBridge.onIframePlayerFound(src);" +
                        "    AndroidBridge.log('iframe: '+src);" +
                        "  }" +
                        "  try{" +
                        "    var fd=f.contentDocument||f.contentWindow.document;" +
                        "    if(fd){" +
                        "      fd.querySelectorAll('video').forEach(function(v){" +
                        "        if(v.src)AndroidBridge.onVideoFound(v.src);" +
                        "        if(v.currentSrc)AndroidBridge.onVideoFound(v.currentSrc);" +
                        "      });" +
                        "    }" +
                        "  }catch(e){" +
                        "    AndroidBridge.log('cross-origin iframe: '+src);" +
                        "  }" +
                        "});" +

                        // Ищем data-атрибуты с video URL
                        "document.querySelectorAll('[data-src],[data-url],[data-video]').forEach(function(el){" +
                        "  var ds=el.getAttribute('data-src')||el.getAttribute('data-url')||el.getAttribute('data-video')||'';" +
                        "  if(ds&&(ds.indexOf('.m3u8')!==-1||ds.indexOf('.mp4')!==-1||ds.indexOf('player')!==-1)){" +
                        "    AndroidBridge.onVideoFound(ds);" +
                        "  }" +
                        "});" +

                        // Ищем в скриптах ссылки на .m3u8/.mp4
                        "document.querySelectorAll('script').forEach(function(sc){" +
                        "  try{" +
                        "    var txt=sc.textContent||'';" +
                        "    var m3u=txt.match(/https?:\\/\\/[^\"'\\s]+\\.m3u8[^\"'\\s]*/gi);" +
                        "    var mp4=txt.match(/https?:\\/\\/[^\"'\\s]+\\.mp4[^\"'\\s]*/gi);" +
                        "    if(m3u)m3u.forEach(function(u){AndroidBridge.onHlsFound(u);});" +
                        "    if(mp4)mp4.forEach(function(u){AndroidBridge.onVideoFound(u);});" +
                        "  }catch(e){}" +
                        "});" +
                        "})();";

        view.evaluateJavascript(js, null);
    }

    /**
     * Глубокое сканирование — запускается с задержкой
     */
    private void injectDeepVideoScan(WebView view) {
        String js =
                "(function(){" +
                        "AndroidBridge.log('Deep scan started');" +

                        // Повторный скан видео
                        "document.querySelectorAll('video').forEach(function(v){" +
                        "  if(v.src)AndroidBridge.onVideoFound(v.src);" +
                        "  if(v.currentSrc)AndroidBridge.onVideoFound(v.currentSrc);" +
                        "  v.querySelectorAll('source').forEach(function(s){" +
                        "    if(s.src)AndroidBridge.onVideoFound(s.src);" +
                        "  });" +
                        "});" +

                        // Ищем Hls.js instances
                        "if(window.Hls&&window.Hls.DefaultConfig){" +
                        "  AndroidBridge.log('Hls.js detected');" +
                        "}" +

                        // Ищем активные MediaSource
                        "if(window.MediaSource){" +
                        "  AndroidBridge.log('MediaSource API available');" +
                        "}" +

                        // Повторный скан iframe
                        "document.querySelectorAll('iframe').forEach(function(f){" +
                        "  var src=f.src||f.getAttribute('data-src')||'';" +
                        "  if(src)AndroidBridge.onIframePlayerFound(src);" +
                        "  try{" +
                        "    var fd=f.contentDocument||f.contentWindow.document;" +
                        "    if(fd){" +
                        "      fd.querySelectorAll('video,source').forEach(function(el){" +
                        "        if(el.src)AndroidBridge.onVideoFound(el.src);" +
                        "        if(el.currentSrc)AndroidBridge.onVideoFound(el.currentSrc);" +
                        "      });" +
                        "      fd.querySelectorAll('script').forEach(function(sc){" +
                        "        try{" +
                        "          var t=sc.textContent||'';" +
                        "          var m=t.match(/https?:\\/\\/[^\"'\\s]+\\.m3u8[^\"'\\s]*/gi);" +
                        "          if(m)m.forEach(function(u){AndroidBridge.onHlsFound(u);});" +
                        "        }catch(e){}" +
                        "      });" +
                        "    }" +
                        "  }catch(e){}" +
                        "});" +

                        // Ищем URL в window свойствах
                        "try{" +
                        "  for(var k in window){" +
                        "    try{" +
                        "      var v=window[k];" +
                        "      if(typeof v==='string'&&v.length>20&&v.length<500){" +
                        "        if(v.indexOf('.m3u8')!==-1||v.indexOf('.mp4')!==-1){" +
                        "          AndroidBridge.onVideoFound(v);" +
                        "        }" +
                        "      }" +
                        "    }catch(e){}" +
                        "  }" +
                        "}catch(e){}" +

                        "AndroidBridge.log('Deep scan done. Videos: '+document.querySelectorAll('video').length);" +
                        "})();";

        view.evaluateJavascript(js, null);
    }

    private void injectAdBlocker(WebView view) {
        String js =
                "(function(){" +
                        "var s=['.b-sticky_panel','.b-overfloat','.adsbygoogle'," +
                        "'[id*=\"banner\"]:not(video)','[class*=\"advert\"]'," +
                        "'.popup','.overlay-ad','[class*=\"push\"]'];" +
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
                        "s.textContent='" +
                        "a:focus,button:focus,[tabindex]:focus{" +
                        "  outline:3px solid #FFD700!important;" +
                        "  outline-offset:2px!important;" +
                        "  box-shadow:0 0 15px rgba(255,215,0,0.5)!important;" +
                        "}" +
                        "';" +
                        "document.head.appendChild(s);" +
                        "document.querySelectorAll('a,.b-content__inline_item').forEach(" +
                        "function(e){if(!e.hasAttribute('tabindex'))e.setAttribute('tabindex','0');});" +
                        "})();";
        view.evaluateJavascript(js, null);
    }

    // ============================================================
    // Меню видео
    // ============================================================
    private void showVideoMenu() {
        List<String> allUrls = new ArrayList<>();

        // Сначала плейлисты (самые ценные)
        allUrls.addAll(playlistUrls);
        // Потом CDN базовые пути
        allUrls.addAll(cdnBaseUrls);

        if (allUrls.isEmpty()) {
            Toast.makeText(this, "Видео не найдено. Попробуйте нажать Play на фильме",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Убираем дубли
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(allUrls));

        if (unique.size() == 1) {
            showVideoDialog(unique.get(0));
        } else {
            showVideoSelectionDialog(unique);
        }
    }

    private void showVideoDialog(String url) {
        if (isFinishing()) return;
        String q = videoInterceptor.detectQuality(url);
        String type = videoInterceptor.isHlsPlaylist(url) ? "HLS Playlist" :
                videoInterceptor.isCdnVideoStream(url) ? "CDN Stream" : "Video";

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("\uD83C\uDFAC " + type)
                .setMessage("Качество: " + q + "\nТип: " + type +
                        "\n\n" + trunc(url, 120))
                .setPositiveButton("\u25B6 VLC", (d, w) -> VLCLauncher.launch(this, url, ""))
                .setNeutralButton("\uD83D\uDCCB Выбор", (d, w) -> VLCLauncher.launchWithChooser(this, url, ""))
                .setNegativeButton("Отмена", null)
                .setCancelable(true).show();
    }

    private void showVideoSelectionDialog(List<String> urls) {
        if (isFinishing() || urls.isEmpty()) return;
        String[] items = new String[urls.size()];
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String q = videoInterceptor.detectQuality(url);
            String type = videoInterceptor.isHlsPlaylist(url) ? " [HLS]" :
                    videoInterceptor.isCdnVideoStream(url) ? " [CDN]" : "";
            items[i] = q + type + " — " + trunc(url, 60);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("\uD83C\uDFAC Найдено видео: " + urls.size())
                .setItems(items, (d, w) -> VLCLauncher.launch(this, urls.get(w), ""))
                .setNegativeButton("Отмена", null).show();
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

    // ============================================================
    // Кнопки пульта
    // ============================================================
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
                new AlertDialog.Builder(this).setTitle("Выход?")
                        .setPositiveButton("Да", (d, w) -> finish())
                        .setNegativeButton("Нет", null).show();
                return true;

            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                showVideoMenu();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // Запускаем глубокий скан + показ меню через 2 сек
                injectDeepVideoScan(webView);
                handler.postDelayed(this::showVideoMenu, 2000);
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

    // ============================================================
    // Утилиты
    // ============================================================
    private boolean isAdUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        for (String d : adDomains)
            if (l.contains(d)) return true;
        return l.contains("/ad/") || l.contains("/ads/") ||
                l.contains("popunder") || l.contains("popads") ||
                l.contains("clickunder");
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager c = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo i = c != null ? c.getActiveNetworkInfo() : null;
        return i != null && i.isConnected();
    }

    private String trunc(String u, int max) {
        return u == null ? "" : (u.length() > max ? u.substring(0, max) + "..." : u);
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
