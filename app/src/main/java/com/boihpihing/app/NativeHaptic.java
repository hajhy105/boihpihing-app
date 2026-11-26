package com.boihpihing.app;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;

/** 게임에서 부르는 진동. 웹 진동(navigator.vibrate)보다 세기를 직접 정할 수 있다. */
public class NativeHaptic {
    private final Vibrator vib;

    NativeHaptic(Context ctx) {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager m = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = m != null ? m.getDefaultVibrator() : null;
        } else {
            v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        }
        vib = v;
    }

    /** pattern: JSON 배열 [진동ms, 쉼ms, 진동ms, ...], amp: 1~255 */
    @JavascriptInterface
    public void vibrate(String pattern, int amp) {
        if (vib == null || !vib.hasVibrator()) return;
        try {
            JSONArray a = new JSONArray(pattern);
            int n = Math.min(a.length(), 16);
            if (n == 0) return;
            long[] t = new long[n + 1];
            int[] amps = new int[n + 1];
            t[0] = 0; amps[0] = 0;                       // 바로 시작
            int level = Math.max(1, Math.min(255, amp));
            for (int i = 0; i < n; i++) {
                t[i + 1] = Math.max(0, Math.min(2000, a.getLong(i)));
                amps[i + 1] = (i % 2 == 0) ? level : 0;   // 짝수 칸 = 진동, 홀수 칸 = 쉼
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vib.cancel();
                if (vib.hasAmplitudeControl()) vib.vibrate(VibrationEffect.createWaveform(t, amps, -1));
                else vib.vibrate(VibrationEffect.createWaveform(t, -1));
            } else {
                vib.vibrate(t, -1);
            }
        } catch (Exception ignored) { }
    }
}
