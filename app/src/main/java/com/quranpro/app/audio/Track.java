package com.quranpro.app.audio;

import java.io.Serializable;

/** A playable item: surah recitation, radio stream or tafsir audio. */
public class Track implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int KIND_SURAH = 0;
    public static final int KIND_RADIO = 1;
    public static final int KIND_TAFSIR = 2;

    public int kind;
    public int surahId;
    public String title;
    public String sub;
    public String url;
    public String filePath; // offline override
    public String server;
    public String key;

    public Track(int kind, int surahId, String title, String sub,
                 String url, String filePath, String server, String key) {
        this.kind = kind;
        this.surahId = surahId;
        this.title = title;
        this.sub = sub;
        this.url = url;
        this.filePath = filePath;
        this.server = server;
        this.key = key;
    }
}
