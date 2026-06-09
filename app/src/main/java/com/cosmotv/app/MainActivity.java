package com.cosmotv.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private static final String APP_URL = "https://cosmo-tv.onrender.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen, no title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        // Keep screen on while the app is open
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Root layout — needed to swap in fullscreen video view
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(0xFF000000);
        setContentView(fullscreenContainer);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        fullscreenContainer.addView(webView);

        configureWebView();

        // Restore URL after rotation / re-create
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(APP_URL);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();

        // Core
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // Video / media
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        // Layout — TV is always landscape, no zoom
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        // D-pad / spatial navigation between links and buttons
        s.setNeedInitialFocus(true);

        // Hardware-accelerated rendering
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Fire Stick user-agent: TV browser UA so the site knows it's a TV
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/114.0.5735.196 Mobile Safari/537.36 " +
            "CosmoTV/1.0"
        );

        // Cookies — persist across app restarts
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        // No scrollbars on TV
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        // ---- WebViewClient — handles navigation & errors ----
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                // Stay inside the WebView for all cosmo-tv URLs; let the system
                // handle anything else (mailto:, etc.)
                String url = req.getUrl().toString();
                if (url.startsWith("https://cosmo-tv.onrender.com") ||
                    url.startsWith("http://cosmo-tv.onrender.com")) {
                    return false; // load in-app
                }
                return true; // block external links
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req,
                                        WebResourceError err) {
                // Only reload the main frame on error, not sub-resources
                if (req.isForMainFrame()) {
                    view.loadUrl(
                        "data:text/html,<html><body style='background:#000;color:#fff;" +
                        "font-family:sans-serif;display:flex;align-items:center;" +
                        "justify-content:center;height:100vh;margin:0;'>" +
                        "<div style='text-align:center'>" +
                        "<div style='font-size:48px'>📡</div>" +
                        "<p style='font-size:24px'>Connecting to Cosmo TV…</p>" +
                        "<p style='font-size:16px;opacity:.6'>Press ▶ to retry</p>" +
                        "</div></body></html>"
                    );
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Flush cookies to disk after each page load
                CookieManager.getInstance().flush();

                // Inject D-pad helper JS so arrow keys move focus between
                // clickable elements (buttons, links, inputs)
                view.evaluateJavascript(
                    "(function() {" +
                    "  if (window.__dpadReady) return;" +
                    "  window.__dpadReady = true;" +
                    "  document.addEventListener('keydown', function(e) {" +
                    "    var els = Array.from(document.querySelectorAll(" +
                    "      'a,button,input,select,[tabindex]:not([tabindex=\"-1\"])'" +
                    "    )).filter(function(el) {" +
                    "      var r = el.getBoundingClientRect();" +
                    "      return r.width > 0 && r.height > 0 &&" +
                    "             !el.disabled && el.offsetParent !== null;" +
                    "    });" +
                    "    var cur = document.activeElement;" +
                    "    var ci = els.indexOf(cur);" +
                    "    if (e.keyCode === 13 && cur && cur !== document.body) {" +
                    "      cur.click(); e.preventDefault(); return;" +
                    "    }" +
                    "    if (e.keyCode === 40 && ci < els.length - 1) {" +
                    "      els[ci + 1].focus(); e.preventDefault();" +
                    "    } else if (e.keyCode === 38 && ci > 0) {" +
                    "      els[ci - 1].focus(); e.preventDefault();" +
                    "    }" +
                    "  });" +
                    "})()",
                    null
                );
            }
        });

        // ---- WebChromeClient — handles fullscreen video ----
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // Video went fullscreen — hide WebView, show video layer
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ));
                webView.setVisibility(View.GONE);
                // Force landscape + keep screen on during playback
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onHideCustomView() {
                // Video exited fullscreen
                if (fullscreenView == null) return;
                webView.setVisibility(View.VISIBLE);
                fullscreenContainer.removeView(fullscreenView);
                fullscreenView = null;
                if (fullscreenCallback != null) {
                    fullscreenCallback.onCustomViewHidden();
                    fullscreenCallback = null;
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                // Reuse the same WebView for new-window requests (e.g. OAuth popups)
                WebView.WebViewTransport transport =
                    (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(webView);
                resultMsg.sendToTarget();
                return true;
            }
        });
    }

    // ---- Remote control key handling ----
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            // Back: go back in history or exit app
            case KeyEvent.KEYCODE_BACK:
                if (fullscreenView != null) {
                    // Exit fullscreen video first
                    webView.getSettings(); // no-op, just to access client
                    if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
                    return true;
                }
                if (webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                return super.onKeyDown(keyCode, event);

            // Play / Pause button on Fire Stick remote
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                webView.evaluateJavascript(
                    "(function(){" +
                    "  var v = document.querySelector('video');" +
                    "  if(v){if(v.paused)v.play();else v.pause();}" +
                    "})()", null
                );
                return true;

            // Fast-forward / rewind
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                webView.evaluateJavascript(
                    "var v=document.querySelector('video');if(v)v.currentTime+=10;", null
                );
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                webView.evaluateJavascript(
                    "var v=document.querySelector('video');if(v)v.currentTime-=10;", null
                );
                return true;

            // Menu / home goes back to root
            case KeyEvent.KEYCODE_MENU:
                webView.loadUrl(APP_URL);
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // ---- Lifecycle ----
    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        webView.pauseTimers();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.stopLoading();
        webView.destroy();
    }
}
