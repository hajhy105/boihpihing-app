package com.boihpihing.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import android.webkit.JavascriptInterface;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

/** 게임(assets/index.html)을 전체 화면 WebView로 띄운다. 인터넷 없이도 동작한다. */
public class MainActivity extends Activity {
    private volatile int safeTop = 0, safeBottom = 0;

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // https://appassets.androidplatform.net/assets/... 주소로 앱 안의 파일을 제공 (기록 저장이 안정적으로 유지됨)
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);   // 꾹 누르기 중 복사 메뉴가 뜨지 않게
        webView.setHapticFeedbackEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("window.__setSafe&&__setSafe(" + safeTop + "," + safeBottom + ")", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                return !"appassets.androidplatform.net".equals(u.getHost());
            }
        });

        // 화면 = WebView(게임) 위에 광고 배너를 얹을 수 있는 틀. 스토어 빌드에서만 광고가 붙는다.
        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.addJavascriptInterface(new NativeNet(webView), "NativeNet");   // 기상청 날씨 요청
        webView.addJavascriptInterface(new NativeHaptic(this), "NativeHaptic");   // 세기 조절 진동
        // 카메라 구멍(컷아웃) 높이를 게임에 알려서 상단 버튼이 가려지지 않게 한다
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public String safe() { return safeTop + "," + safeBottom; }
        }, "AppSafe");
        if (Build.VERSION.SDK_INT >= 33) {   // Android 13+: 새 뒤로가기 방식(앱이 직접 처리)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets c = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            float d = getResources().getDisplayMetrics().density;
            safeTop = Math.round(c.top / d);
            safeBottom = Math.round(c.bottom / d);
            webView.evaluateJavascript("window.__setSafe&&__setSafe(" + safeTop + "," + safeBottom + ")", null);
            return insets;
        });
        Ads.attach(this, root, webView);
        hideSystemBars();
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.evaluateJavascript("window.appPause && window.appPause()", null);   // 앱이 가려지면 소리 멈춤
        webView.onPause();
        Ads.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        Ads.onResume();
        webView.evaluateJavascript("window.appResume && window.appResume()", null);
        hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        Ads.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    /** 상단 상태바와 하단 내비게이션 바를 숨긴다. 화면 가장자리에서 쓸어내리면 잠깐 보였다가 다시 숨는다. */
    private void hideSystemBars() {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());
    }

    // 뒤로가기: 게임 안에서는 열린 창 닫기 → 메뉴(홈)로. 메뉴에서는 두 번 눌러야 종료.
    private long lastBackAt = 0;
    private void handleBack() {
        webView.evaluateJavascript("(window.appBack && window.appBack()) ? '1' : '0'", value -> {
            if ("\"1\"".equals(value)) return;
            long now = System.currentTimeMillis();
            if (now - lastBackAt < 2000) { finish(); return; }
            lastBackAt = now;
            android.widget.Toast.makeText(this, "뒤로 버튼을 한 번 더 누르면 게임이 종료돼요", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBackPressed() { handleBack(); }   // Android 12 이하

}
