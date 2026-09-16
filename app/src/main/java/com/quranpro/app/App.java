package com.quranpro.app;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.quranpro.app.data.Store;
import com.quranpro.app.util.LangHelper;

public class App extends Application {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static App inst;

    /**
     * Only used when the PackageManager lookup fails (practically never). Every screen
     * and every HTTP User-Agent reads the version through {@link #versionName()} — the
     * app used to hard-code "1.0"/"1.3"/"1.5" in nine different places, so the About
     * and Contact screens kept reporting a release that was years out of date.
     */
    private static final String FALLBACK_VERSION = "1.6";

    private static volatile String pkgVersionName;
    private static volatile long pkgVersionCode;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LangHelper.wrap(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        readPackageVersion();
        AppCompatDelegate.setDefaultNightMode(Store.themeMode(this));
    }

    @SuppressWarnings("deprecation") // PackageInfo.versionCode below API 28
    private void readPackageVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pi.versionName != null && !pi.versionName.isEmpty()) pkgVersionName = pi.versionName;
            pkgVersionCode = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception ignored) {
            // keep the fallbacks
        }
    }

    public static Context context() {
        return inst == null ? null : inst.getApplicationContext();
    }

    /** The real installed version, e.g. {@code "1.6"} or {@code "1.6-debug"}. */
    public static String versionName() {
        String v = pkgVersionName;
        return v == null || v.isEmpty() ? FALLBACK_VERSION : v;
    }

    /** The real installed versionCode (Android uses it to compare updates). */
    public static long versionCode() {
        return pkgVersionCode;
    }

    /** Single User-Agent for every HTTP request the app makes. */
    public static String userAgent() {
        return "QuranPro/" + versionName() + " (Android)";
    }

    public static void post(Runnable r) {
        MAIN.post(r);
    }

    public static void postDelayed(Runnable r, long ms) {
        MAIN.postDelayed(r, ms);
    }

    public static void removeCallbacks(Runnable r) {
        MAIN.removeCallbacks(r);
    }
}
