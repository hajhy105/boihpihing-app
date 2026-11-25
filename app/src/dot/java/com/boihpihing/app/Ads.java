package com.boihpihing.app;

import android.app.Activity;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** 광고 없는 빌드(개인 테스트용 도트·3D 앱)에서 쓰는 빈 구현. */
final class Ads {
    private Ads() {}
    static void attach(Activity a, FrameLayout root, WebView web) {}
    static void onResume() {}
    static void onPause() {}
    static void destroy() {}
}
