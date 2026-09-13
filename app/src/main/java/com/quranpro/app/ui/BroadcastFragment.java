package com.quranpro.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Live TV (Makkah/Sunnah HLS) + Quran radio stations. */
public class BroadcastFragment extends Fragment {

    private final List<Models.LiveChannel> live = new ArrayList<>();
    private final List<Models.RadioStation> radios = new ArrayList<>();
    private List<Models.RadioStation> shown = new ArrayList<>();
    private LiveAdapter liveAdapter;
    private RadioAdapter radioAdapter;
    private ProgressBar progress;
    private Button retry;
    private String query = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_broadcast, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        RecyclerView liveList = v.findViewById(R.id.live_list);
        liveList.setLayoutManager(new LinearLayoutManager(getContext()));
        liveAdapter = new LiveAdapter();
        liveList.setAdapter(liveAdapter);

        RecyclerView radioList = v.findViewById(R.id.radio_list);
        radioList.setLayoutManager(new LinearLayoutManager(getContext()));
        radioAdapter = new RadioAdapter();
        radioList.setAdapter(radioAdapter);

        progress = v.findViewById(R.id.progress);
        retry = v.findViewById(R.id.btn_retry);
        retry.setOnClickListener(vv -> load());

        EditText search = v.findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        load();
    }

    private void load() {
        if (getContext() == null) return;
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        Api.fetchLive(requireContext(), new Api.Cb<List<Models.LiveChannel>>() {
            @Override public void ok(List<Models.LiveChannel> v) {
                live.clear();
                live.addAll(v);
                liveAdapter.notifyDataSetChanged();
            }
            @Override public void err(String m) {}
        });
        Api.fetchRadios(requireContext(), new Api.Cb<List<Models.RadioStation>>() {
            @Override public void ok(List<Models.RadioStation> v) {
                progress.setVisibility(View.GONE);
                radios.clear();
                radios.addAll(v);
                applyFilter();
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                retry.setVisibility(View.VISIBLE);
            }
        });
    }

    private void applyFilter() {
        shown = new ArrayList<>();
        for (Models.RadioStation r : radios) {
            if (query.isEmpty() || r.name.contains(query)) shown.add(r);
        }
        if (radioAdapter != null) radioAdapter.notifyDataSetChanged();
    }

    // ---------------- adapters ----------------

    class LiveAdapter extends RecyclerView.Adapter<LiveAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            TextView name;
            H(View v) {
                super(v);
                name = v.findViewById(R.id.name);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_live, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            final Models.LiveChannel c = live.get(position);
            h.name.setText(c.name);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(v.getContext(), LiveActivity.class);
                i.putExtra("name", c.name);
                i.putExtra("url", c.url);
                v.getContext().startActivity(i);
            });
        }

        @Override
        public int getItemCount() {
            return live.size();
        }
    }

    class RadioAdapter extends RecyclerView.Adapter<RadioAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            TextView name;
            ImageButton btnPlay;
            H(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                btnPlay = v.findViewById(R.id.btn_play);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_radio, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            final Models.RadioStation r = shown.get(position);
            h.name.setText(r.name);
            View.OnClickListener play = v -> {
                PlayerManager.playRadio(v.getContext(), r.name, r.url);
                v.getContext().startActivity(new Intent(v.getContext(), PlayerActivity.class));
            };
            h.btnPlay.setOnClickListener(play);
            h.itemView.setOnClickListener(play);
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
