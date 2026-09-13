package com.quranpro.app.audio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.quranpro.app.data.Models;
import com.quranpro.app.data.QuranMeta;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.DownloadHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Entry point for playback + controller access from UI. */
public final class PlayerManager {
    private PlayerManager() {}

    public interface CCb {
        void onController(MediaController c);
    }

    public static void playTracks(Context ctx, List<Track> tracks, int index) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, PlayerService.class);
        i.setAction(PlayerService.ACT_PLAY);
        i.putExtra("tracks", new ArrayList<>(tracks));
        i.putExtra("index", index);
        ContextCompat.startForegroundService(app, i);
    }

    /** Play a surah with the given moshaf; queue continues to the end when autoplay is on. */
    public static void playSurah(Context ctx, Models.Moshaf moshaf, String reciterName,
                                 int surahId) {
        Context app = ctx.getApplicationContext();
        Models.Surah s = QuranMeta.byId(surahId);
        if (s == null || moshaf == null) return;
        String mName = moshaf.name == null ? "" : moshaf.name;

        List<Integer> ids;
        if (Store.autoplay(app)) {
            ids = new ArrayList<>();
            for (int id : moshaf.surahIds()) {
                if (id >= surahId) ids.add(id);
            }
            Collections.sort(ids);
            if (ids.isEmpty()) ids.add(surahId);
        } else {
            ids = new ArrayList<>();
            ids.add(surahId);
        }

        List<Track> tracks = new ArrayList<>(ids.size());
        for (int id : ids) {
            Models.Surah qs = QuranMeta.byId(id);
            String name = qs == null ? ("سورة " + id) : ("سورة " + qs.ar);
            String sub = reciterName + (mName.isEmpty() ? "" : " • " + mName);
            String offline = DownloadHelper.offlinePath(app, moshaf.server, id);
            tracks.add(new Track(Track.KIND_SURAH, id, name, sub,
                    moshaf.audioUrl(id), offline, moshaf.server,
                    Store.favKey(moshaf.server, id)));
        }
        Store.setLast(app, surahId, s.ar, reciterName, moshaf.server);
        playTracks(ctx, tracks, 0);
    }

    public static void playRadio(Context ctx, String name, String url) {
        List<Track> tracks = new ArrayList<>();
        tracks.add(new Track(Track.KIND_RADIO, 0, name,
                ctx.getString(com.quranpro.app.R.string.radio_playing),
                url, null, url, "radio|" + url));
        playTracks(ctx, tracks, 0);
    }

    public static void playTafsir(Context ctx, String tafsirName, int suraId,
                                  String suraName, String url) {
        Models.Surah qs = QuranMeta.byId(suraId);
        String title = qs == null ? suraName : ("تفسير سورة " + qs.ar);
        List<Track> tracks = new ArrayList<>();
        tracks.add(new Track(Track.KIND_TAFSIR, suraId, title, tafsirName,
                url, null, url, "tafsir|" + url));
        playTracks(ctx, tracks, 0);
    }

    public static void controller(Context ctx, CCb cb) {
        Context app = ctx.getApplicationContext();
        SessionToken token = new SessionToken(app,
                new ComponentName(app, PlayerService.class));
        ListenableFuture<MediaController> f =
                new MediaController.Builder(app, token).buildAsync();
        f.addListener(() -> {
            try {
                cb.onController(f.get());
            } catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(app));
    }

    public static void release(MediaController c) {
        if (c != null) {
            try {
                c.release();
            } catch (Exception ignored) {}
        }
    }

    public static void setRepeatOne(MediaController c, boolean one) {
        if (c != null) c.setRepeatMode(one
                ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public static boolean isRepeatOne(MediaController c) {
        return c != null && c.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }
}
