package com.quranpro.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.quranpro.app.data.Store;

public class App extends Application {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Store.themeMode(this));
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
