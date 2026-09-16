package com.quranpro.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.util.ImageLoader;

import java.util.ArrayList;

/** In-app video player with up-next list. */
public class VideoPlayerActivity extends BaseActivity {

    public static void open(Context c, ArrayList<String> urls, ArrayList<String> titles,
                            ArrayList<String> thumbs, int index) {
        Intent i = new Intent(c, VideoPlayerActivity.class);
        i.putStringArrayListExtra("urls", urls);
        i.putStringArrayListExtra("titles", titles);
        i.putStringArrayListExtra("thumbs", thumbs);
        i.putExtra("index", index);
        c.startActivity(i);
    }

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView title, sub;
    private RecyclerView list;
    private ArrayList<String> urls = new ArrayList<>();
    private ArrayList<String> titles = new ArrayList<>();
    private ArrayList<String> thumbs = new ArrayList<>();
    private boolean userPaused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        playerView = findViewById(R.id.player_view);
        title = findViewById(R.id.title);
        sub = findViewById(R.id.sub);
        list = findViewById(R.id.list);

        urls = getIntent().getStringArrayListExtra("urls");
        titles = getIntent().getStringArrayListExtra("titles");
        thumbs = getIntent().getStringArrayListExtra("thumbs");
        int index = getIntent().getIntExtra("index", 0);
        if (urls == null) urls = new ArrayList<>();
        if (titles == null) titles = new ArrayList<>();
        if (thumbs == null) thumbs = new ArrayList<>();

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(App.userAgent())
                .setAllowCrossProtocolRedirects(true);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(
                        new DefaultDataSource.Factory(this, http)))
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@NonNull MediaItem mediaItem, int reason) {
                updateTitle();
            }
        });

        for (String u : urls) {
            player.addMediaItem(MediaItem.fromUri(Uri.parse(u)));
        }
        if (index < 0) index = 0;
        if (index >= urls.size()) index = 0;
        player.seekTo(index, 0);
        player.prepare();
        player.play();
        updateTitle();

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new UpNextAdapter());
    }

    private void updateTitle() {
        int i = player == null ? 0 : player.getCurrentMediaItemIndex();
        if (i >= 0 && i < titles.size()) title.setText(titles.get(i));
        sub.setText(getString(R.string.videos_title));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
            userPaused = false;
        } else {
            userPaused = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && !userPaused) player.play();
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
        title.setVisibility(land ? View.GONE : View.VISIBLE);
        sub.setVisibility(land ? View.GONE : View.VISIBLE);
        list.setVisibility(land ? View.GONE : View.VISIBLE);
    }

    class UpNextAdapter extends RecyclerView.Adapter<UpNextAdapter.H> {
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
            h.title.setText(position < titles.size() ? titles.get(position) : "");
            h.type.setVisibility(View.GONE);
            ImageLoader.load(h.thumb, position < thumbs.size() ? thumbs.get(position) : "");
            h.itemView.setOnClickListener(v -> {
                if (player != null) {
                    player.seekTo(position, 0);
                    player.prepare();
                    player.play();
                }
            });
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }
    }
}
