package com.quranpro.app.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import com.quranpro.app.data.Store;

import java.util.Locale;

/** Resolves and applies the app language (auto / ar / en). */
public final class LangHelper {
    private LangHelper() {}

    public static Context wrap(Context base) {
        if (base == null) return null;
        String pref = Store.lang(base);
        Locale locale = resolveLocale(base, pref);
        Locale.setDefault(locale);

        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);
        cfg.setLayoutDirection(locale);
        try {
            base.getResources().updateConfiguration(cfg, base.getResources().getDisplayMetrics());
        } catch (Exception ignored) {}
        if ("auto".equals(pref)) return base;
        return base.createConfigurationContext(cfg);
    }

    public static Locale localeOf(Context c) {
        if (c == null) return Locale.getDefault();
        Configuration cfg = c.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= 24) {
            LocaleList locales = cfg.getLocales();
            if (locales != null && !locales.isEmpty()) return locales.get(0);
        }
        Locale locale = cfg.locale;
        return locale == null ? Locale.getDefault() : locale;
    }

    public static boolean isArabic(Context c) {
        return "ar".equalsIgnoreCase(localeOf(c).getLanguage());
    }

    private static Locale resolveLocale(Context base, String pref) {
        if ("ar".equals(pref)) return new Locale("ar");
        if ("en".equals(pref)) return Locale.ENGLISH;
        return localeOf(base);
    }
}
