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
                String url = req.getUrl().toString();
                // Allow cosmo-tv app pages
                if (url.startsWith("https://cosmo-tv.onrender.com") ||
                    url.startsWith("http://cosmo-tv.onrender.com")) {
                    return false;
                }
                // Allow embed player domains (needed for iframe video players)
                String[] allowedHosts = {
                    "vidsrc.to", "vidsrc.cc", "vidsrc.me",
                    "2embed.cc", "www.2embed.cc",
                    "player.autoembed.cc", "autoembed.cc",
                    "embed.su",
                };
                String host = req.getUrl().getHost();
                if (host != null) {
                    for (String allowed : allowedHosts) {
                        if (host.equals(allowed) || host.endsWith("." + allowed)) {
                            return false; // load in-app
                        }
                    }
                }
                // Block everything else (ads, tracking, external sites)
                return true;
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
                CookieManager.getInstance().flush();

                // Spatial D-pad navigation: finds the nearest focusable element
                // in the actual visual direction pressed (up/down/left/right)
                view.evaluateJavascript(
                    "(function() {" +
                    "  if (window.__dpadReady) return;" +
                    "  window.__dpadReady = true;" +

                    // Get all visible, non-disabled focusable elements
                    "  function getFocusable() {" +
                    "    return Array.from(document.querySelectorAll(" +
                    "      'a[href],button,input:not([type=hidden]),select,textarea,[tabindex]:not([tabindex=\"-1\"])'" +
                    "    )).filter(function(el) {" +
                    "      if (el.disabled || el.offsetParent === null) return false;" +
                    "      var r = el.getBoundingClientRect();" +
                    "      return r.width > 4 && r.height > 4 &&" +
                    "             r.top < window.innerHeight && r.bottom > 0 &&" +
                    "             r.left < window.innerWidth && r.right > 0;" +
                    "    });" +
                    "  }" +

                    // Find the best candidate in the given direction (keyCode)
                    "  function findNext(cur, dir) {" +
                    "    var cr = cur.getBoundingClientRect();" +
                    "    var cx = (cr.left + cr.right) / 2;" +
                    "    var cy = (cr.top  + cr.bottom) / 2;" +
                    "    var best = null, bestScore = Infinity;" +
                    "    getFocusable().forEach(function(el) {" +
                    "      if (el === cur) return;" +
                    "      var er = el.getBoundingClientRect();" +
                    "      var ex = (er.left + er.right) / 2;" +
                    "      var ey = (er.top  + er.bottom) / 2;" +
                    "      var dx = ex - cx, dy = ey - cy;" +
                    // Check element is in the correct half-plane for this direction
                    "      var ok = (dir===40 && dy>8) || (dir===38 && dy<-8) ||" +
                    "               (dir===39 && dx>8) || (dir===37 && dx<-8);" +
                    "      if (!ok) return;" +
                    // Score: primary-axis distance + 2.5× cross-axis penalty
                    "      var pri = (dir===40||dir===38) ? Math.abs(dy) : Math.abs(dx);" +
                    "      var sec = (dir===40||dir===38) ? Math.abs(dx) : Math.abs(dy);" +
                    "      var score = pri + sec * 2.5;" +
                    "      if (score < bestScore) { bestScore = score; best = el; }" +
                    "    });" +
                    "    return best;" +
                    "  }" +

                    "  document.addEventListener('keydown', function(e) {" +
                    "    var k = e.keyCode;" +
                    // Enter / OK button — click the focused element
                    "    if (k === 13) {" +
                    "      var f = document.activeElement;" +
                    "      if (f && f !== document.body) { f.click(); e.preventDefault(); }" +
                    "      return;" +
                    "    }" +
                    // Arrow keys — spatial navigation
                    "    if (k !== 37 && k !== 38 && k !== 39 && k !== 40) return;" +
                    "    var cur = document.activeElement;" +
                    // If nothing focused yet, focus first visible element
                    "    if (!cur || cur === document.body) {" +
                    "      var first = getFocusable()[0];" +
                    "      if (first) { first.focus(); e.preventDefault(); }" +
                    "      return;" +
                    "    }" +
                    "    var next = findNext(cur, k);" +
                    "    if (next) {" +
                    "      next.focus();" +
                    "      next.scrollIntoView({ block:'nearest', inline:'nearest' });" +
                    "      e.preventDefault();" +
                    "    }" +
                    "  }, true);" + // capture phase so we see keys before the page does

                    // Highlight focused element so user can see where they are
                    "  var style = document.createElement('style');" +
                    "  style.textContent = ':focus { outline: 3px solid rgba(255,255,255,0.85) !important;" +
                    "    outline-offset: 3px !important; border-radius: 6px !important; }';" +
                    "  document.head.appendChild(style);" +

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
