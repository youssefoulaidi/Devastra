package com.quranpro.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.data.Models;
import com.quranpro.app.data.QuranMeta;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.DownloadHelper;
import com.quranpro.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** 114 surahs: search, play with current reciter, read, download, favorite, share. */
public class SurahsFragment extends Fragment {

    private SurahAdapter adapter;
    private List<Models.Surah> shown = new ArrayList<>();
    private TextView reciterName, empty, resumeTitle;
    private View resumeCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_surahs, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SurahAdapter();
        list.setAdapter(adapter);

        empty = v.findViewById(R.id.empty);
        reciterName = v.findViewById(R.id.reciter_name);
        resumeCard = v.findViewById(R.id.resume_card);
        resumeTitle = v.findViewById(R.id.resume_title);

        EditText search = v.findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        Button change = v.findViewById(R.id.btn_change_reciter);
        change.setOnClickListener(vv -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchTab(MainActivity.TAB_RECITERS);
            }
        });
        v.findViewById(R.id.reciter_card).setOnClickListener(vv -> change.performClick());

        v.findViewById(R.id.btn_resume).setOnClickListener(vv -> resumeLast());

        filter("");
        refreshHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHeader();
        DownloadHelper.addListener(dlListener);
        DownloadHelper.refreshStatuses(requireContext());
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        DownloadHelper.removeListener(dlListener);
        super.onPause();
    }

    private final Runnable dlListener = () -> {
        if (isAdded() && adapter != null) adapter.notifyDataSetChanged();
    };

    private void refreshHeader() {
        if (getContext() == null) return;
        Store.Current cur = Store.getCurrent(requireContext());
        reciterName.setText(cur == null ? "" : cur.reciterName + " • " + cur.moshafName);
        int last = Store.lastSurah(requireContext());
        if (last > 0) {
            resumeCard.setVisibility(View.VISIBLE);
            resumeTitle.setText(getString(R.string.read_title, Store.lastSurahName(requireContext()))
                    + " — " + Store.lastReciter(requireContext()));
        } else {
            resumeCard.setVisibility(View.GONE);
        }
    }

    private void filter(String q) {
        q = q.trim();
        shown = new ArrayList<>();
        String qn = latinDigits(q);
        for (Models.Surah s : QuranMeta.all()) {
            if (q.isEmpty() || s.ar.contains(q) || s.en.toLowerCase().contains(q.toLowerCase())
                    || String.valueOf(s.id).equals(qn)) {
                shown.add(s);
            }
        }
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private static String latinDigits(String q) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < q.length(); i++) {
            char ch = q.charAt(i);
            if (ch >= '٠' && ch <= '٩') sb.append((char) ('0' + ch - '٠'));
            else sb.append(ch);
        }
        return sb.toString().trim();
    }

    private void resumeLast() {
        if (getContext() == null) return;
        int last = Store.lastSurah(requireContext());
        Models.Moshaf m = Store.currentMoshaf(requireContext());
        Store.Current cur = Store.getCurrent(requireContext());
        if (last > 0 && m != null && cur != null && m.hasSurah(last)) {
            PlayerManager.playSurah(requireContext(), m, cur.reciterName, last);
            startActivity(new android.content.Intent(requireContext(), PlayerActivity.class));
        } else if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).switchTab(MainActivity.TAB_RECITERS);
        }
    }

    private void playSurah(Models.Surah s) {
        if (getContext() == null) return;
        Models.Moshaf m = Store.currentMoshaf(requireContext());
        Store.Current cur = Store.getCurrent(requireContext());
        if (m == null || cur == null) return;
        if (!m.hasSurah(s.id)) {
            Ui.toast(requireContext(), R.string.surah_not_in_moshaf);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchTab(MainActivity.TAB_RECITERS);
            }
            return;
        }
        PlayerManager.playSurah(requireContext(), m, cur.reciterName, s.id);
        startActivity(new android.content.Intent(requireContext(), PlayerActivity.class));
        refreshHeader();
    }

    private void showOptions(Models.Surah s) {
        if (getContext() == null) return;
        Store.Current cur0 = Store.getCurrent(requireContext());
        boolean offline = cur0 != null
                && DownloadHelper.isDownloaded(requireContext(), cur0.server, s.id);
        String[] items = {
                getString(R.string.opt_play),
                getString(R.string.opt_read),
                offline ? getString(R.string.opt_delete_download)
                        : getString(R.string.opt_download),
                getString(R.string.opt_fav),
                getString(R.string.opt_share),
                getString(R.string.opt_reciter)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.read_title, s.ar))
                .setItems(items, (d, which) -> {
                    if (which == 0) playSurah(s);
                    else if (which == 1) ReadActivity.open(requireContext(), s.id);
                    else if (which == 2) {
                        if (offline) {
                            DownloadHelper.delete(requireContext(),
                                    Store.favKey(cur0.server, s.id));
                            Ui.toast(requireContext(), R.string.deleted);
                            adapter.notifyDataSetChanged();
                        } else {
                            downloadSurah(s);
                        }
                    }
                    else if (which == 3) toggleFav(s);
                    else if (which == 4) shareSurah(s);
                    else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).switchTab(MainActivity.TAB_RECITERS);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void downloadSurah(Models.Surah s) {
        Store.Current cur = Store.getCurrent(requireContext());
        if (cur == null) return;
        Models.Moshaf m = Store.currentMoshaf(requireContext());
        if (m == null || !m.hasSurah(s.id)) {
            Ui.toast(requireContext(), R.string.surah_not_in_moshaf);
            return;
        }
        if (!com.quranpro.app.util.Net.online(requireContext())) {
            Ui.toast(requireContext(), R.string.error_network);
            return;
        }
        boolean started = DownloadHelper.enqueue(requireContext(), cur.reciterId,
                cur.reciterName, s.id, s.ar, cur.server, m.audioUrl(s.id));
        Ui.toast(requireContext(), started ? R.string.downloading : R.string.downloaded);
        adapter.notifyDataSetChanged();
    }

    private void toggleFav(Models.Surah s) {
        Store.Current cur = Store.getCurrent(requireContext());
        if (cur == null) return;
        String key = Store.favKey(cur.server, s.id);
        boolean was = Store.isFav(requireContext(), key);
        if (!was) {
            Store.Fav f = new Store.Fav();
            f.key = key;
            f.surahId = s.id;
            f.surahName = s.ar;
            f.reciterName = cur.reciterName;
            f.moshafName = cur.moshafName;
            f.server = cur.server;
            Store.toggleFav(requireContext(), f);
        } else {
            Store.removeFav(requireContext(), key);
        }
        Ui.toast(requireContext(), was ? R.string.fav_removed : R.string.fav_added);
    }

    private void shareSurah(Models.Surah s) {
        Store.Current cur = Store.getCurrent(requireContext());
        Models.Moshaf m = Store.currentMoshaf(requireContext());
        if (cur == null || m == null) return;
        String link = m.audioUrl(s.id);
        Ui.copyText(requireContext(), "quran", link);
        Ui.shareText(requireContext(),
                getString(R.string.read_title, s.ar) + " - " + cur.reciterName + "\n" + link);
    }

    // ---------------- adapter ----------------

    class SurahAdapter extends RecyclerView.Adapter<SurahAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            TextView num, nameAr, meta;
            View offlineDot;
            ImageButton btnPlay, btnMore;
            H(View v) {
                super(v);
                num = v.findViewById(R.id.num);
                nameAr = v.findViewById(R.id.name_ar);
                meta = v.findViewById(R.id.meta);
                offlineDot = v.findViewById(R.id.offline_dot);
                btnPlay = v.findViewById(R.id.btn_play);
                btnMore = v.findViewById(R.id.btn_more);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_surah, parent, false);
            return new H(v);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Models.Surah s = shown.get(position);
            h.num.setText(Ui.digits(s.id));
            h.nameAr.setText(getString(R.string.read_title, s.ar));
            String type = s.makki ? getString(R.string.makkia) : getString(R.string.madania);
            h.meta.setText(s.en + " • "
                    + getString(R.string.surah_meta, s.ayahs, type).replace(
                    String.valueOf(s.ayahs), Ui.digits(s.ayahs)));
            Store.Current cur = Store.getCurrent(h.itemView.getContext());
            boolean offline = cur != null && DownloadHelper.isDownloaded(
                    h.itemView.getContext(), cur.server, s.id);
            h.offlineDot.setVisibility(offline ? View.VISIBLE : View.GONE);
            h.btnPlay.setOnClickListener(v -> playSurah(s));
            h.btnMore.setOnClickListener(v -> showOptions(s));
            h.itemView.setOnClickListener(v -> playSurah(s));
            h.itemView.setOnLongClickListener(v -> {
                showOptions(s);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
