package com.quranpro.app.util;

import java.io.File;
import java.io.RandomAccessFile;

/** Shared payload checks so we never treat an error page as audio. */
public final class AudioCheck {
    private AudioCheck() {}

    /** Cheap sniff test: ID3 tag or an MPEG audio frame sync. */
    public static boolean looksLikeAudio(File f) {
        if (f == null || !f.exists() || f.length() < 1024) return false;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] head = new byte[16];
            int n = raf.read(head);
            if (n < 4) return false;
            if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return true;
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) return true;
            // some servers prepend junk: scan the first block for a frame sync
            raf.seek(0);
            byte[] block = new byte[4096];
            int m = raf.read(block);
            for (int i = 0; i + 1 < m; i++) {
                if ((block[i] & 0xFF) == 0xFF && (block[i + 1] & 0xE0) == 0xE0) return true;
            }
            return false;
        } catch (Exception e) {
            return f.length() > 4096; // unreadable: trust the size
        }
    }

    /** 1024 → "1.0 KB", 5242880 → "5.0 MB". */
    public static String fmtSize(long bytes) {
        if (bytes <= 0) return "0";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024d);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024d * 1024d));
    }
}
