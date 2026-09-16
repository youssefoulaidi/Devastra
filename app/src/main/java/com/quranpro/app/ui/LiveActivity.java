package com.quranpro.app.ui;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Live TV: Quran & Sunnah channels (HLS). */
public class LiveActivity extends BaseActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView title;
    private RecyclerView listV;
    private final List<Models.LiveChannel> channels = new ArrayList<>();
    private ChannelAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);

        playerView = findViewById(R.id.player_view);
        title = findViewById(R.id.title);
        RecyclerView listH = findViewById(R.id.list);
        listH.setVisibility(View.GONE);
        listV = findViewById(R.id.list_v);
        listV.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChannelAdapter();
        listV.setAdapter(adapter);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(App.userAgent())
                .setAllowCrossProtocolRedirects(true);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(
                        new DefaultDataSource.Factory(this, http)))
                .build();
        playerView.setPlayer(player);

        String name = getIntent().getStringExtra("name");
        String url = getIntent().getStringExtra("url");
        if (url != null && !url.isEmpty()) {
            play(name == null ? "" : name, url);
        }

        Api.fetchLive(this, new Api.Cb<List<Models.LiveChannel>>() {
            @Override public void ok(List<Models.LiveChannel> v) {
                channels.clear();
                channels.addAll(v);
                adapter.notifyDataSetChanged();
                if ((url == null || url.isEmpty()) && !channels.isEmpty()) {
                    play(channels.get(0).name, channels.get(0).url);
                }
            }
            @Override public void err(String m) {}
        });
    }

    private void play(String name, String url) {
        title.setText(name);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration cfg) {
        super.onConfigurationChanged(cfg);
        boolean land = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE;
        ViewGroup.LayoutParams lp = playerView.getLayoutParams();
        lp.height = land ? ViewGroup.LayoutParams.MATCH_PARENT
                : (int) (240 * getResources().getDisplayMetrics().density);
        playerView.setLayoutParams(lp);
        listV.setVisibility(land ? View.GONE : View.VISIBLE);
    }

    class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.H> {
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
            final Models.LiveChannel c = channels.get(position);
            h.name.setText(c.name);
            h.itemView.setOnClickListener(v -> play(c.name, c.url));
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }
    }
}
