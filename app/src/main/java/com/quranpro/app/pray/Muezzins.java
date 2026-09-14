package com.quranpro.app.pray;

/**
 * Muezzin voices for the adhan — free audio collection hosted on
 * raw.githubusercontent.com (abodehq/Athan-MP3), with an archive.org mirror
 * for the first entry.
 */
public final class Muezzins {
    private Muezzins() {}

    public static class Voice {
        public final String ar;
        public final String url;
        public final String fallback;

        Voice(String ar, String url, String fallback) {
            this.ar = ar;
            this.url = url;
            this.fallback = fallback;
        }
    }

    private static final String GH =
            "https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds/";

    public static Voice[] all() {
        return new Voice[]{
                new Voice("مشاري راشد العفاسي",
                        GH + "Athan%20Mishary%20Alafasi.mp3",
                        "https://archive.org/download/AdhanMisharyRashid/Adhan%20Mishary%20Rashid.mp3"),
                new Voice("أذان المسجد الحرام — مكة",
                        GH + "Athan%20Makkah.mp3", null),
                new Voice("محمد رفعت",
                        GH + "Athan%20Mohammad%20Ref3at.mp3", null),
                new Voice("محمد صديق المنشاوي",
                        GH + "Athan%20Mohammad%20Almenshawy.mp3", null),
                new Voice("ناصر القطامي",
                        GH + "Athan%20Nasser%20Alqatami.mp3", null),
                new Voice("حمد الدغريري",
                        GH + "Athan%20Hamad%20Deghreri.mp3", null),
                new Voice("ماجد الحمثني",
                        GH + "Athan%20Majed%20Al-hamathani.mp3", null),
                new Voice("حمدان المالكي",
                        GH + "Athan%20Hamdan%20Almalki.mp3", null),
                new Voice("إبراهيم الأركاني",
                        GH + "Athan%20Ibrahim%20Al-Arkani.mp3", null),
                new Voice("منصور الزهراني",
                        GH + "Athan%20Mansoor%20Az-Zahrani.mp3", null),
        };
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
}
