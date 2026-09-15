package com.quranpro.app;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.quranpro.app.data.Store;
import com.quranpro.app.util.LangHelper;

public class App extends Application {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static App inst;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LangHelper.wrap(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        AppCompatDelegate.setDefaultNightMode(Store.themeMode(this));
    }

    public static Context context() {
        return inst == null ? null : inst.getApplicationContext();
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
