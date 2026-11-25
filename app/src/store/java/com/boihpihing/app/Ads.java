package com.boihpihing.app;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 스토어 빌드 전용 광고(AdMob).
 * - 하단 배너: 메뉴·상점·도감 같은 화면에서만 보이고, 낚시 중에는 숨긴다(게임 쪽 JS가 setBanner로 요청).
 * - 보상형 광고: 플레이어가 버튼을 눌렀을 때만 보여 주고, 끝까지 보면 JS의 window.onAdReward(tag)를 부른다.
 * - 광고 단위 ID는 build.gradle의 buildConfigField(기본값: 구글 테스트 ID)에서 온다.
 */
final class Ads {
    private Ads() {}

    private static Activity act;
    private static WebView web;
    private static FrameLayout root;
    private static AdView banner;
    private static RewardedAd rewarded;
    private static boolean loadingRewarded;
    private static boolean wantBanner;
    private static ConsentInformation consent;
    private static final AtomicBoolean started = new AtomicBoolean(false);

    static void attach(Activity a, FrameLayout r, WebView w) {
        act = a; root = r; web = w;
        web.addJavascriptInterface(new Bridge(), "AndroidAds");
        // 동의(UMP): 유럽 등 필요한 지역에서는 동의 창을 먼저 띄우고, 광고 요청이 가능할 때만 SDK를 시작한다.
        consent = UserMessagingPlatform.getConsentInformation(a);
        consent.requestConsentInfoUpdate(a, new ConsentRequestParameters.Builder().build(),
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(a, err -> { if (consent.canRequestAds()) start(); }),
                err -> { if (consent.canRequestAds()) start(); });
        if (consent.canRequestAds()) start();
    }

    private static void start() {
        if (!started.compareAndSet(false, true)) return;
        new Thread(() -> MobileAds.initialize(act, status -> act.runOnUiThread(() -> {
            makeBanner();
            loadRewarded();
            notifyJs("window.onAdsReady && window.onAdsReady()");
        }))).start();
    }

    private static void makeBanner() {
        if (banner != null) return;
        banner = new AdView(act);
        banner.setAdUnitId(BuildConfig.AD_BANNER);
        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(act, widthDp));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(banner, lp);
        banner.setVisibility(wantBanner ? View.VISIBLE : View.GONE);
        banner.loadAd(new AdRequest.Builder().build());
    }

    private static void loadRewarded() {
        if (rewarded != null || loadingRewarded) return;
        loadingRewarded = true;
        RewardedAd.load(act, BuildConfig.AD_REWARDED, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull RewardedAd ad) { rewarded = ad; loadingRewarded = false; }
            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { rewarded = null; loadingRewarded = false; }
        });
    }

    private static void notifyJs(String js) {
        if (web != null) web.post(() -> web.evaluateJavascript(js, null));
    }

    static void onResume() { if (banner != null) banner.resume(); }
    static void onPause() { if (banner != null) banner.pause(); }
    static void destroy() { if (banner != null) { banner.destroy(); banner = null; } }

    /** 게임(JS)에서 부르는 함수들: window.AndroidAds.xxx() */
    static final class Bridge {
        @JavascriptInterface public boolean isAvailable() { return true; }
        @JavascriptInterface public boolean rewardedReady() { return rewarded != null; }

        @JavascriptInterface public void setBanner(boolean show) {
            act.runOnUiThread(() -> { wantBanner = show; if (banner != null) banner.setVisibility(show ? View.VISIBLE : View.GONE); });
        }

        @JavascriptInterface public void showRewarded(String tag) {
            act.runOnUiThread(() -> {
                if (rewarded == null) { loadRewarded(); notifyJs("window.onAdFail && window.onAdFail(" + q(tag) + ")"); return; }
                final boolean[] earned = {false};
                RewardedAd ad = rewarded; rewarded = null;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() {
                        notifyJs(earned[0] ? "window.onAdReward && window.onAdReward(" + q(tag) + ")" : "window.onAdFail && window.onAdFail(" + q(tag) + ")");
                        loadRewarded();
                    }
                    @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                        notifyJs("window.onAdFail && window.onAdFail(" + q(tag) + ")");
                        loadRewarded();
                    }
                });
                ad.show(act, item -> earned[0] = true);
            });
        }

        @JavascriptInterface public boolean privacyOptionsRequired() {
            return consent != null && consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        }

        @JavascriptInterface public void showPrivacyOptions() {
            act.runOnUiThread(() -> UserMessagingPlatform.showPrivacyOptionsForm(act, err -> {}));
        }

        private static String q(String s) { return "'" + (s == null ? "" : s.replace("\\", "").replace("'", "")) + "'"; }
    }
}
