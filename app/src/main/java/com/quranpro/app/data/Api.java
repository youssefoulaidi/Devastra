package com.quranpro.app.data;

import android.content.Context;

import com.quranpro.app.App;
import com.quranpro.app.util.LangHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Free APIs used (no key required):
 *  - mp3quran.net API v3 : reciters, moshafs, videos, live tv, radios, tafsir, ayat timing
 *  - api.alquran.cloud v1: uthmani text
 *  - cdn.islamic.network : fallback surah audio (pattern urls)
 */
public final class Api {
    private Api() {}

    public interface Cb<T> {
        void ok(T v);
        void err(String m);
    }

    public static final String MP3 = "https://mp3quran.net/api/v3";
    public static final String QCLOUD = "https://api.alquran.cloud/v1";
    public static final String ADHAN_API = "https://api.aladhan.com/v1";

    private static final ExecutorService EX = Executors.newFixedThreadPool(4);
    private static final long HOUR = 3600_000L;
    private static final long DAY = 24 * HOUR;

    public static String lang(Context c) {
        return LangHelper.isArabic(c) ? "ar" : "eng";
    }

    // ---------- low level ----------

    private static File cacheFile(Context ctx, String url) {
        try {
            File dir = new File(ctx.getCacheDir(), "api");
            dir.mkdirs();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format(Locale.US, "%02x", b));
            return new File(dir, sb + ".json");
        } catch (Exception e) {
            return new File(ctx.getCacheDir(), "api_fallback.json");
        }
    }

    private static String readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return readAll(in);
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    private static void writeFile(File f, String s) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(s.getBytes(StandardCharsets.UTF_8));
            } finally {
                try { out.close(); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** GET with disk cache. Returns stale cache when offline. */
    public static String getSync(Context ctx, String url, long cacheMs) throws IOException {
        File cache = cacheFile(ctx, url);
        if (cacheMs > 0 && cache.exists()
                && System.currentTimeMillis() - cache.lastModified() < cacheMs) {
            return readFile(cache);
        }
        HttpURLConnection cn = null;
        try {
            cn = (HttpURLConnection) new URL(url).openConnection();
            cn.setConnectTimeout(20000);
            cn.setReadTimeout(30000);
            cn.setRequestProperty("User-Agent", "QuranPro/1.0 (Android)");
            cn.setRequestProperty("Accept", "application/json");
            cn.connect();
            int code = cn.getResponseCode();
            if (code < 200 || code >= 300) {
                if (cache.exists()) return readFile(cache);
                throw new IOException("HTTP " + code);
            }
            String s = readAll(cn.getInputStream());
            if (s.length() > 20) writeFile(cache, s);
            return s;
        } catch (IOException e) {
            if (cache.exists()) return readFile(cache);
            throw e;
        } finally {
            if (cn != null) cn.disconnect();
        }
    }

    // ---------- reciters ----------

    public static void fetchReciters(final Context ctx, final int rewayaId,
                                     final Cb<List<Models.Reciter>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String url = MP3 + "/reciters?language=" + lang(app);
                if (rewayaId > 0) url += "&rewaya=" + rewayaId;
                String s = getSync(app, url, DAY);
                List<Models.Reciter> out = parseReciters(s);
                if (out.isEmpty()) throw new IOException("empty");
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                final List<Models.Reciter> fb = fallbackReciters();
                App.post(() -> cb.ok(fb)); // graceful offline mode
            }
        });
    }

    private static List<Models.Reciter> parseReciters(String s) throws Exception {
        List<Models.Reciter> out = new ArrayList<>();
        JSONObject root = new JSONObject(s);
        JSONArray arr = root.optJSONArray("reciters");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Models.Reciter r = new Models.Reciter();
            r.id = o.optInt("id");
            r.name = o.optString("name", "").trim();
            r.letter = o.optString("letter", "").trim();
            JSONArray mos = o.optJSONArray("moshaf");
            if (mos != null) {
                for (int j = 0; j < mos.length(); j++) {
                    JSONObject m = mos.getJSONObject(j);
                    Models.Moshaf mh = new Models.Moshaf();
                    mh.id = m.optInt("id");
                    mh.name = m.optString("name", "").trim();
                    mh.server = m.optString("server", "").trim();
                    mh.total = m.optInt("surah_total");
                    mh.list = m.optString("surah_list", "").trim();
                    if (!mh.server.isEmpty()) r.mosafs.add(mh);
                }
            }
            if (!r.name.isEmpty() && !r.mosafs.isEmpty()) out.add(r);
        }
        return out;
    }

    /** Offline fallback: famous reciters served from cdn.islamic.network (pattern urls). */
    public static List<Models.Reciter> fallbackReciters() {
        String[][] fb = {
                {"-1", "مشاري راشد العفاسي", "ar.alafasy"},
                {"-2", "محمود خليل الحصري", "ar.husary"},
                {"-3", "محمد صديق المنشاوي", "ar.minshawi"},
                {"-4", "عبد الباسط عبد الصمد (مرتل)", "ar.abdulbasit"},
                {"-5", "عبد الباسط عبد الصمد (مجوّد)", "ar.abdulbasitmujawwad"},
                {"-6", "عبد الرحمن السديس", "ar.sudais"},
                {"-7", "سعود الشريم", "ar.shuraim"},
                {"-8", "أحمد بن علي العجمي", "ar.ajamy"},
                {"-9", "محمد أيوب", "ar.muhammadayyoub"},
                {"-10", "علي الحذيفي", "ar.hudhaify"},
                {"-11", "محمد جبريل", "ar.muhammadjibreel"},
                {"-12", "محمد صديق المنشاوي (مجوّد)", "ar.minshawimujawwad"},
        };
        StringBuilder all = new StringBuilder();
        for (int i = 1; i <= 114; i++) {
            if (i > 1) all.append(',');
            all.append(i);
        }
        List<Models.Reciter> out = new ArrayList<>();
        for (String[] f : fb) {
            Models.Reciter r = new Models.Reciter();
            r.id = Integer.parseInt(f[0]);
            r.name = f[1];
            r.letter = f[1].substring(0, 1);
            r.fallback = true;
            Models.Moshaf m = new Models.Moshaf();
            m.id = Integer.parseInt(f[0]);
            m.name = "حفص عن عاصم - مرتل";
            m.server = "cdn:" + f[2];
            m.total = 114;
            m.list = all.toString();
            r.mosafs.add(m);
            out.add(r);
        }
        return out;
    }

    // ---------- riwayat ----------

    public static void fetchRiwayat(final Context ctx, final Cb<List<Models.Riwaya>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, MP3 + "/riwayat?language=" + lang(app), 7 * DAY);
                List<Models.Riwaya> out = new ArrayList<>();
                JSONArray arr = new JSONObject(s).optJSONArray("riwayat");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Models.Riwaya r = new Models.Riwaya();
                        r.id = o.optInt("id");
                        r.name = o.optString("name", "").trim();
                        if (!r.name.isEmpty()) out.add(r);
                    }
                }
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    // ---------- videos ----------

    public static void fetchVideos(final Context ctx, final Cb<List<Models.VideoClip>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String lang = "ar".equals(lang(app)) ? "ar" : "eng";
                java.util.Map<Integer, String> types = new java.util.HashMap<>();
                try {
                    String t = getSync(app, MP3 + "/video_types?language=" + lang, 7 * DAY);
                    JSONArray arr = new JSONObject(t).optJSONArray("video_types");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            types.put(o.optInt("id"), o.optString("video_type", ""));
                        }
                    }
                } catch (Exception ignored) {}

                String s = getSync(app, MP3 + "/videos?language=" + lang, 6 * HOUR);
                List<Models.VideoClip> out = new ArrayList<>();
                JSONArray groups = new JSONObject(s).optJSONArray("videos");
                if (groups != null) {
                    for (int i = 0; i < groups.length(); i++) {
                        JSONObject g = groups.getJSONObject(i);
                        String reciter = g.optString("reciter_name", "").trim();
                        JSONArray arr = g.optJSONArray("videos");
                        if (arr == null) continue;
                        for (int j = 0; j < arr.length(); j++) {
                            JSONObject v = arr.getJSONObject(j);
                            Models.VideoClip c = new Models.VideoClip();
                            c.id = v.optInt("id");
                            c.url = v.optString("video_url", "").trim();
                            c.thumb = v.optString("video_thumb_url", "").trim();
                            int typeId = v.optInt("video_type");
                            c.typeName = types.containsKey(typeId) ? types.get(typeId) : "";
                            c.reciter = reciter;
                            if (!c.url.isEmpty()) out.add(c);
                        }
                    }
                }
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    // ---------- live tv ----------

    public static void fetchLive(final Context ctx, final Cb<List<Models.LiveChannel>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, MP3 + "/live-tv?language=" + lang(app), 6 * HOUR);
                List<Models.LiveChannel> out = new ArrayList<>();
                JSONArray arr = new JSONObject(s).optJSONArray("livetv");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Models.LiveChannel c = new Models.LiveChannel();
                        c.id = o.optInt("id");
                        c.name = o.optString("name", "").trim();
                        c.url = o.optString("url", "").trim();
                        if (!c.url.isEmpty()) out.add(c);
                    }
                }
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    // ---------- radios ----------

    public static void fetchRadios(final Context ctx, final Cb<List<Models.RadioStation>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, MP3 + "/radios?language=" + lang(app), DAY);
                List<Models.RadioStation> out = new ArrayList<>();
                JSONArray arr = new JSONObject(s).optJSONArray("radios");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Models.RadioStation r = new Models.RadioStation();
                        r.id = o.optInt("id");
                        r.name = o.optString("name", "").trim();
                        r.url = o.optString("url", "").trim();
                        if (!r.url.isEmpty()) out.add(r);
                    }
                }
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    // ---------- tafsir ----------

    public static void fetchTafasir(final Context ctx, final Cb<List<Models.TafsirInfo>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, MP3 + "/tafasir?language=ar", 7 * DAY);
                List<Models.TafsirInfo> out = new ArrayList<>();
                JSONArray arr = new JSONObject(s).optJSONArray("tafasir");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Models.TafsirInfo t = new Models.TafsirInfo();
                        t.id = o.optInt("id");
                        t.name = o.optString("name", "").trim();
                        if (!t.name.isEmpty()) out.add(t);
                    }
                }
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    public static void fetchTafsirSuras(final Context ctx, final int tafsirId,
                                        final Cb<List<Models.TafsirSura>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, MP3 + "/tafsir?tafsir=" + tafsirId + "&language=ar", 7 * DAY);
                List<Models.TafsirSura> out = new ArrayList<>();
                JSONObject root = new JSONObject(s);
                JSONObject taf = root.optJSONObject("tafasir");
                if (taf != null) {
                    JSONObject sora = taf.optJSONObject("sora");
                    if (sora != null) {
                        Iterator<String> keys = sora.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            JSONArray arr = sora.optJSONArray(k);
                            if (arr != null && arr.length() > 0) {
                                JSONObject o = arr.getJSONObject(0);
                                Models.TafsirSura t = new Models.TafsirSura();
                                t.suraId = o.optInt("sura_id", parseIntSafe(k));
                                t.name = o.optString("name", "").trim();
                                t.url = o.optString("url", "").trim();
                                if (!t.url.isEmpty()) out.add(t);
                            }
                        }
                    }
                }
                Collections.sort(out, new Comparator<Models.TafsirSura>() {
                    @Override public int compare(Models.TafsirSura a, Models.TafsirSura b) {
                        return a.suraId - b.suraId;
                    }
                });
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    // ---------- quran text ----------

    public static void fetchSurahText(final Context ctx, final int surah,
                                      final Cb<List<Models.Ayah>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, QCLOUD + "/surah/" + surah + "/quran-uthmani", 30 * DAY);
                List<Models.Ayah> out = new ArrayList<>();
                JSONObject data = new JSONObject(s).optJSONObject("data");
                JSONArray arr = data == null ? null : data.optJSONArray("ayahs");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Models.Ayah a = new Models.Ayah();
                        a.num = o.optInt("numberInSurah", i + 1);
                        a.text = o.optString("text", "").trim();
                        a.juz = o.optInt("juz");
                        a.page = o.optInt("page");
                        out.add(a);
                    }
                }
                // Strip bismillah prefix from first ayah (except Al-Fatiha & At-Tawba)
                if (surah != 1 && surah != 9 && !out.isEmpty()) {
                    String t = out.get(0).text;
                    int cut = t.indexOf("حِيمِ");
                    if (cut > 0 && cut < 120) {
                        out.get(0).text = t.substring(cut + "حِيمِ".length()).trim();
                    }
                }
                if (out.isEmpty()) throw new IOException("empty");
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    // ---------- ayat timing ----------

    public static void fetchTimingReads(final Context ctx, final Cb<List<Models.TimingRead>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, MP3 + "/ayat_timing/reads", 7 * DAY);
                List<Models.TimingRead> out = new ArrayList<>();
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Models.TimingRead r = new Models.TimingRead();
                    r.id = o.optInt("id");
                    r.name = o.optString("name", "");
                    r.folder = o.optString("folder_url", "");
                    out.add(r);
                }
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    public static void fetchTiming(final Context ctx, final int surah, final int read,
                                   final Cb<List<Models.Timing>> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String s = getSync(app, MP3 + "/ayat_timing?surah=" + surah + "&read=" + read, 30 * DAY);
                List<Models.Timing> out = new ArrayList<>();
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Models.Timing t = new Models.Timing();
                    t.ayah = o.optInt("ayah");
                    t.start = o.optLong("start_time");
                    t.end = o.optLong("end_time");
                    out.add(t);
                }
                App.post(() -> cb.ok(out));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    // ---------- prayer times (Aladhan — free, no key) ----------

    /** Fetch one day of timings; ddMmYyyy may be null for today. Raw JSON string cached. */
    public static void fetchPrayerTimes(final Context ctx, final double lat, final double lon,
                                        final int method, final String ddMmYyyy,
                                        final Cb<String> cb) {
        final Context app = ctx.getApplicationContext();
        EX.execute(() -> {
            try {
                String day = (ddMmYyyy == null || ddMmYyyy.isEmpty())
                        ? new java.text.SimpleDateFormat("dd-MM-yyyy", Locale.US)
                          .format(new java.util.Date())
                        : ddMmYyyy;
                String url = ADHAN_API + "/timings/" + day
                        + "?latitude=" + lat + "&longitude=" + lon
                        + "&method=" + method + "&iso8601=false";
                String s = getSync(app, url, HOUR);
                if (s == null || !s.contains("\"timings\"")) throw new IOException("bad payload");
                App.post(() -> cb.ok(s));
            } catch (Exception e) {
                App.post(() -> cb.err(e.getMessage()));
            }
        });
    }

    public static String normServer(String s) {
        if (s == null) return "";
        s = s.trim().toLowerCase(Locale.US);
        if (s.startsWith("http://")) s = "https://" + s.substring(7);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
