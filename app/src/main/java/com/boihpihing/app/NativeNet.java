package com.boihpihing.app;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 기상청 단기예보(공공데이터포털)를 앱 안에서 대신 요청한다.
 * - 공공데이터포털 API는 웹(CORS) 요청을 받지 않아서 앱(Java)에서 요청한다.
 * - 인증키는 BuildConfig.KMA_KEY(빌드 때 GitHub 비밀값 KMA_API_KEY에서 들어옴)에만 있고, 게임 HTML에는 넣지 않는다.
 * JS: NativeNet.hasKma(), NativeNet.kma(op, query, id) → 결과는 window.__kmaCb(id, 본문)
 */
final class NativeNet {
    private static final String BASE = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/";
    private final WebView web;

    NativeNet(WebView web) { this.web = web; }

    @JavascriptInterface
    public boolean hasKma() { return BuildConfig.KMA_KEY != null && !BuildConfig.KMA_KEY.isEmpty(); }

    @JavascriptInterface
    public void kma(String op, String query, String id) {
        if (!hasKma() || !("getUltraSrtNcst".equals(op) || "getUltraSrtFcst".equals(op) || "getVilageFcst".equals(op))) {
            reply(id, "{}");
            return;
        }
        String q = query == null ? "" : query.replaceAll("[^A-Za-z0-9=&_.-]", "");
        new Thread(() -> {
            String body = "{}";
            HttpURLConnection c = null;
            try {
                URL u = new URL(BASE + op + "?serviceKey=" + BuildConfig.KMA_KEY + "&dataType=JSON&" + q);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                body = sb.toString();
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            reply(id, body);
        }).start();
    }

    private void reply(String id, String body) {
        String js = "window.__kmaCb && window.__kmaCb(" + JSONObject.quote(id) + "," + JSONObject.quote(body) + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }
}
