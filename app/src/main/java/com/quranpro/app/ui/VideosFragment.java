package com.quranpro.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quranpro.app.R;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.Models;
import com.quranpro.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** Visual recitations (mp4) from mp3quran.net. */
public class VideosFragment extends Fragment {

    private final List<Models.VideoClip> all = new ArrayList<>();
    private VideoAdapter adapter;
    private ProgressBar progress;
    private View error;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_videos, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VideoAdapter();
        list.setAdapter(adapter);
        progress = v.findViewById(R.id.progress);
        error = v.findViewById(R.id.error);
        empty = v.findViewById(R.id.empty);
        v.findViewById(R.id.btn_retry).setOnClickListener(vv -> load());
        load();
    }

    private void load() {
        if (getContext() == null) return;
        progress.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        Api.fetchVideos(requireContext(), new Api.Cb<List<Models.VideoClip>>() {
            @Override public void ok(List<Models.VideoClip> v) {
                progress.setVisibility(View.GONE);
                all.clear();
                all.addAll(v);
                adapter.notifyDataSetChanged();
                empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                error.setVisibility(View.VISIBLE);
            }
        });
    }

    class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView title, type;
            H(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                title = v.findViewById(R.id.title);
                type = v.findViewById(R.id.type);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_video, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            final Models.VideoClip c = all.get(position);
            h.title.setText(displayTitle(c));
            if (c.typeName != null && !c.typeName.isEmpty()) {
                h.type.setVisibility(View.VISIBLE);
                h.type.setText(c.typeName);
            } else {
                h.type.setVisibility(View.GONE);
            }
            ImageLoader.load(h.thumb, c.thumb);
            h.itemView.setOnClickListener(v -> {
                ArrayList<String> urls = new ArrayList<>();
                ArrayList<String> titles = new ArrayList<>();
                ArrayList<String> thumbs = new ArrayList<>();
                for (Models.VideoClip x : all) {
                    urls.add(x.url);
                    titles.add(displayTitle(x));
                    thumbs.add(x.thumb == null ? "" : x.thumb);
                }
                VideoPlayerActivity.open(v.getContext(), urls, titles, thumbs, position);
            });
        }

        @Override
        public int getItemCount() {
            return all.size();
        }
    }

    private static String displayTitle(Models.VideoClip c) {
        String t = c.reciter == null ? "" : c.reciter;
        if (c.typeName != null && !c.typeName.isEmpty()) t += " — " + c.typeName;
        return t;
    }
}
