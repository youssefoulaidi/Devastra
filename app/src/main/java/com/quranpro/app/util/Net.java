package com.quranpro.app.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

/** Small connectivity helper — used to fail fast instead of hanging when offline. */
public final class Net {
    private Net() {}

    @SuppressWarnings("deprecation")
    public static boolean online(Context c) {
        if (c == null) return true;
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                if (n == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                return caps != null
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true; // never block playback because the check failed
        }
    }
}
