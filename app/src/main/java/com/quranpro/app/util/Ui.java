package com.quranpro.app.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.quranpro.app.App;
import com.quranpro.app.data.Store;

import java.util.Locale;

/** Small UI helpers: digits, durations, share. */
public final class Ui {
    private Ui() {}

    private static final char[] AR_DIGITS = {'٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'};

    public static boolean isArabic() {
        Context c = App.context();
        if (c != null) {
            String pref = Store.lang(c);
            if ("ar".equals(pref)) return true;
            if ("en".equals(pref)) return false;
        }
        return "ar".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    public static String digits(Object o) {
        String s = String.valueOf(o);
        if (!isArabic()) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(AR_DIGITS[ch - '0']);
            else sb.append(ch);
        }
        return sb.toString();
    }

    public static String fmtTime(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        String out;
        if (h > 0) out = String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        else out = String.format(Locale.US, "%02d:%02d", m, sec);
        return digits(out);
    }

    public static String ayahEnd(int n) {
        return "﴿" + digits(n) + "﴾";
    }

    public static void toast(Context c, int res) {
        Toast.makeText(c.getApplicationContext(), res, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    public static void shareText(Context c, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        c.startActivity(Intent.createChooser(i, null));
    }

    public static void copyText(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    public static void openUrl(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            c.startActivity(i);
        } catch (Exception e) {
            toast(c, url);
        }
    }
}
