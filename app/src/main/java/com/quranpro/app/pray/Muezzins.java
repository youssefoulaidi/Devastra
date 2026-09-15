package com.quranpro.app.pray;

import android.content.Context;

import java.io.InputStream;

/**
 * Muezzin voices for the adhan — free audio collection hosted on
 * raw.githubusercontent.com (abodehq/Athan-MP3), with an archive.org mirror
 * for the first entry.
 *
 * <p>A curated set ships inside the APK ({@code assets/adhan}) so the adhan works with
 * no internet at all. Every other voice can be downloaded for offline use from the
 * muezzin settings screen; downloaded copies are kept in internal storage and never
 * expire.
 */
public final class Muezzins {
    private Muezzins() {}

    public static class Voice {
        public final String ar;
        public final String url;
        public final String fallback;
        /** File name inside assets/adhan, or null when it is not bundled. */
        public final String asset;

        Voice(String ar, String url, String fallback, String asset) {
            this.ar = ar;
            this.url = url;
            this.fallback = fallback;
            this.asset = asset;
        }
    }

    private static final String GH =
            "https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds/";

    public static Voice[] all() {
        return new Voice[]{
                new Voice("مشاري راشد العفاسي",
                        GH + "Athan%20Mishary%20Alafasi.mp3",
                        "https://archive.org/download/AdhanMisharyRashid/Adhan%20Mishary%20Rashid.mp3",
                        "athan_mishary.mp3"),
                new Voice("أذان المسجد الحرام — مكة",
                        GH + "Athan%20Makkah.mp3", null,
                        "athan_makkah.mp3"),
                new Voice("محمد رفعت",
                        GH + "Athan%20Mohammad%20Ref3at.mp3", null,
                        "athan_refaat.mp3"),
                new Voice("محمد صديق المنشاوي",
                        GH + "Athan%20Mohammad%20Almenshawy.mp3", null,
                        "athan_minshawi.mp3"),
                new Voice("ناصر القطامي",
                        GH + "Athan%20Nasser%20Alqatami.mp3", null, "athan_qatami.mp3"),
                new Voice("حمد الدغريري",
                        GH + "Athan%20Hamad%20Deghreri.mp3", null, null),
                new Voice("ماجد الحمثني",
                        GH + "Athan%20Majed%20Al-hamathani.mp3", null, null),
                new Voice("حمدان المالكي",
                        GH + "Athan%20Hamdan%20Almalki.mp3", null, null),
                new Voice("إبراهيم الأركاني",
                        GH + "Athan%20Ibrahim%20Al-Arkani.mp3", null, null),
                new Voice("منصور الزهراني",
                        GH + "Athan%20Mansoor%20Az-Zahrani.mp3", null, null),
                new Voice("عبد الباسط عبد الصمد",
                        GH + "Athan%20Abed%20Albase6.mp3", null,
                        "athan_abdulbasit.mp3"),
                new Voice("صهيب خطبة",
                        GH + "Athan%20Suhaib%20Khatba.mp3", null,
                        "athan_suhaib.mp3"),
                new Voice("أذان الفجر — مالك شعبان",
                        GH + "Athan%20Al-fajer%20-%20Malek%20chebae.mp3", null,
                        "athan_chebae.mp3"),
                new Voice("أحمد نوينع",
                        GH + "Athan%20Ahmad%20Nuyne3.mp3", null, "athan_ahmad.mp3"),
        };
    }

    public static int count() {
        return all().length;
    }

    /** -1 means "notification only, no audio". */
    public static String voiceLabel(int idx) {
        if (idx < 0) return "إشعار بدون صوت";
        Voice[] v = all();
        return (idx >= 0 && idx < v.length) ? v[idx].ar : v[0].ar;
    }

    public static String urlFor(int idx) {
        Voice[] v = all();
        return (idx >= 0 && idx < v.length) ? v[idx].url : v[0].url;
    }

    public static String fallbackFor(int idx) {
        Voice[] v = all();
        return (idx >= 0 && idx < v.length) ? v[idx].fallback : null;
    }

    /** File name inside assets/adhan, or null. */
    public static String assetName(int idx) {
        Voice[] v = all();
        return (idx >= 0 && idx < v.length) ? v[idx].asset : null;
    }

    private static volatile boolean[] bundledCache;

    private static boolean[] bundled(Context c) {
        boolean[] cache = bundledCache;
        if (cache != null) return cache;
        Voice[] all = all();
        boolean[] out = new boolean[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = fileExists(c, all[i].asset);
        }
        bundledCache = out;
        return out;
    }

    private static boolean fileExists(Context c, String asset) {
        if (c == null || asset == null || asset.trim().isEmpty()) return false;
        try (InputStream ignored = c.getAssets().open("adhan/" + asset)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Playable {@code file:///android_asset/…} source when the voice ships with the app. */
    public static String assetFor(Context c, int idx) {
        if (c == null) return null;
        String asset = assetName(idx);
        if (asset == null || asset.trim().isEmpty()) return null;
        String path = "adhan/" + asset;
        try (InputStream ignored = c.getAssets().open(path)) {
            return "file:///android_asset/" + path;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isBundled(Context c, int idx) {
        if (c == null || idx < 0) return false;
        boolean[] cache = bundled(c);
        return idx < cache.length && cache[idx];
    }
}
