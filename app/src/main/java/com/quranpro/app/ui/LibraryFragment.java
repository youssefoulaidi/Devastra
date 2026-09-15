package com.quranpro.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.audio.Track;
import com.quranpro.app.data.Models;
import com.quranpro.app.data.QuranMeta;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.DownloadHelper;

import java.util.ArrayList;
import java.util.List;

/** Downloads (offline) + favorites. */
public class LibraryFragment extends Fragment {

    /** Set by drawer before opening to jump to favorites tab. */
    public static boolean goFavs;

    private TabLayout tabs;
    private RecyclerView list;
    private TextView empty;
    private LibAdapter adapter;
    private int mode; // 0 downloads, 1 favs
    private final Runnable dlListener = this::refreshSafe;

    private final List<Store.Dl> dlList = new ArrayList<>();
    private final List<Store.Fav> favList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        tabs = v.findViewById(R.id.tabs);
        list = v.findViewById(R.id.list);
        empty = v.findViewById(R.id.empty);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LibAdapter();
        list.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                mode = tab.getPosition();
                refresh();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (goFavs) {
            goFavs = false;
            mode = 1;
            TabLayout.Tab t = tabs.getTabAt(1);
            if (t != null) t.select();
        }
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        DownloadHelper.addListener(dlListener);
        DownloadHelper.refreshStatuses(requireContext());
        refresh();
    }

    @Override
    public void onPause() {
        DownloadHelper.removeListener(dlListener);
        super.onPause();
    }

    private void refreshSafe() {
        if (!isAdded()) return;
        refresh();
    }

    private void refresh() {
        if (getContext() == null) return;
        dlList.clear();
        dlList.addAll(Store.getDls(requireContext()));
        favList.clear();
        favList.addAll(Store.getFavs(requireContext()));
        adapter.notifyDataSetChanged();
        boolean isEmpty = (mode == 0 ? dlList.isEmpty() : favList.isEmpty());
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        empty.setText(isEmpty ? (mode == 0 ? getString(R.string.lib_empty_downloads)
                : getString(R.string.lib_empty_favs)) : "");
    }

    class LibAdapter extends RecyclerView.Adapter<LibAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            TextView title, sub;
            ImageButton btnPlay, btnDel;
            H(View v) {
                super(v);
                title = v.findViewById(R.id.title);
                sub = v.findViewById(R.id.sub);
                btnPlay = v.findViewById(R.id.btn_play);
                btnDel = v.findViewById(R.id.btn_del);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lib, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            if (mode == 0) {
                if (position >= dlList.size()) return;
                final Store.Dl d = dlList.get(position);
                Models.Surah s = QuranMeta.byId(d.surahId);
                h.title.setText(getString(R.string.read_title,
                        s == null ? d.surahName : s.ar));
                h.sub.setText(d.reciterName + (d.done
                        ? " • " + getString(R.string.offline_badge)
                        : " • " + getString(R.string.downloading)));
                View.OnClickListener play = v -> {
                    if (!d.done) return;
                    Models.Moshaf mm = new Models.Moshaf();
                    mm.server = d.server;
                    List<Track> tracks = new ArrayList<>();
                    tracks.add(new Track(Track.KIND_SURAH, d.surahId,
                            h.title.getText().toString(), d.reciterName,
                            mm.audioUrl(d.surahId), d.path, d.server, d.key));
                    PlayerManager.playTracks(v.getContext(), tracks, 0);
                    v.getContext().startActivity(new Intent(v.getContext(), PlayerActivity.class));
                };
                h.btnPlay.setOnClickListener(play);
                h.itemView.setOnClickListener(play);
                h.btnDel.setOnClickListener(v -> {
                    DownloadHelper.delete(v.getContext(), d.key);
                    refresh();
                });
            } else {
                if (position >= favList.size()) return;
                final Store.Fav f = favList.get(position);
                Models.Surah s = QuranMeta.byId(f.surahId);
                h.title.setText(getString(R.string.read_title,
                        s == null ? f.surahName : s.ar));
                h.sub.setText(f.reciterName + (f.moshafName == null || f.moshafName.isEmpty()
                        ? "" : " • " + f.moshafName));
                View.OnClickListener play = v -> {
                    Models.Moshaf m = new Models.Moshaf();
                    m.server = f.server;
                    m.name = f.moshafName;
                    m.list = String.valueOf(f.surahId);
                    PlayerManager.playSurah(v.getContext(), m, f.reciterName, f.surahId);
                    v.getContext().startActivity(new Intent(v.getContext(), PlayerActivity.class));
                };
                h.btnPlay.setOnClickListener(play);
                h.itemView.setOnClickListener(play);
                h.btnDel.setOnClickListener(v -> {
                    Store.removeFav(v.getContext(), f.key);
                    refresh();
                });
            }
        }

        @Override
        public int getItemCount() {
            return mode == 0 ? dlList.size() : favList.size();
        }
    }
}
