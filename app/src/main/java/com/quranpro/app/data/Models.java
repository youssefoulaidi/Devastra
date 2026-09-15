package com.quranpro.app.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Plain data models for mp3quran.net + AlQuran Cloud APIs. */
public final class Models {
    private Models() {}

    public static class Surah {
        public int id;
        public String ar;
        public String en;
        public int ayahs;
        public boolean makki;

        public Surah(int id, String ar, String en, int ayahs, boolean makki) {
            this.id = id;
            this.ar = ar;
            this.en = en;
            this.ayahs = ayahs;
            this.makki = makki;
        }
    }

    public static class Moshaf {
        public int id;
        public String name;
        public String server;
        public int total;
        public String list;
        private List<Integer> cache;

        public List<Integer> surahIds() {
            if (cache != null) return cache;
            cache = new ArrayList<>();
            if (list == null || list.isEmpty()) return cache;
            String[] parts = list.split(",");
            for (String p : parts) {
                try {
                    cache.add(Integer.parseInt(p.trim()));
                } catch (NumberFormatException ignored) {}
            }
            return cache;
        }

        public boolean hasSurah(int surahId) {
            return surahIds().contains(surahId);
        }

        /** Direct mp3 url for a surah. Supports mp3quran servers + cdn: fallback marker. */
        public String audioUrl(int surahId) {
            if (server != null && server.startsWith("cdn:")) {
                String edition = server.substring(4);
                return "https://cdn.islamic.network/quran/audio/128/" + edition + "/" + surahId + ".mp3";
            }
            String base = server == null ? "" : server.trim();
            if (!base.endsWith("/")) base += "/";
            return base + String.format(Locale.US, "%03d", surahId) + ".mp3";
        }

        public boolean isFallback() {
            return server != null && server.startsWith("cdn:");
        }
    }

    public static class Reciter {
        public int id;
        public String name;
        public String letter;
        public List<Moshaf> mosafs = new ArrayList<>();
        public boolean fallback;
    }

    public static class Riwaya {
        public int id;
        public String name;
    }

    public static class VideoClip {
        public int id;
        public String typeName;
        public String url;
        public String thumb;
        public String reciter;
    }

    public static class RadioStation {
        public int id;
        public String name;
        public String url;
    }

    public static class LiveChannel {
        public int id;
        public String name;
        public String url;
    }

    public static class TafsirInfo {
        public int id;
        public String name;
    }

    public static class TafsirSura {
        public int id;
        public int tafsirId;
        public int suraId;
        public String name;
        public String url;
        /** Stable ordering inside a surah (API entry id). */
        public int order;

        /** "سورة البقرة" — the surah part of the API title. */
        public String surahTitle() {
            if (name == null) return "";
            int cut = name.indexOf("الايات");
            if (cut < 0) cut = name.indexOf("الآيات");
            if (cut < 0) cut = name.indexOf("الايه");
            if (cut > 0) return name.substring(0, cut).trim();
            return name.trim();
        }

        /** "الآيات من 1 إلى 25" / "كاملة" — the range part of the API title. */
        public String rangeTitle() {
            if (name == null) return "";
            int cut = name.indexOf("الايات");
            if (cut < 0) cut = name.indexOf("الآيات");
            if (cut < 0) cut = name.indexOf("الايه");
            if (cut < 0) {
                return name.contains("كامل") ? "كاملة" : "";
            }
            String rest = name.substring(cut).trim();
            rest = rest.replace("الايات من", "الآيات من").replace("الي", "إلى");
            return rest;
        }
    }

    public static class Ayah {
        public int num;
        public String text;
        public int juz;
        public int page;
    }

    public static class Timing {
        public int ayah;
        public long start;
        public long end;
    }

    public static class TimingRead {
        public int id;
        public String name;
        public String folder;
    }
}
