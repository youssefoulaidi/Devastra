package com.quranpro.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.media3.session.MediaController;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.audio.PlayerService;
import com.quranpro.app.data.QuranMeta;
import com.quranpro.app.data.Models;
import com.quranpro.app.util.Ui;

public class MainActivity extends BaseActivity {

    public static final int TAB_SURAHS = 0;
    public static final int TAB_RECITERS = 1;
    public static final int TAB_VIDEO = 2;
    public static final int TAB_BROADCAST = 3;
    public static final int TAB_LIBRARY = 4;

    private DrawerLayout drawer;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;

    private View miniRoot;
    private TextView miniTitle, miniSub;
    private ImageButton miniPlay, miniPrev, miniNext;
    private ProgressBar miniProgress;
    private MediaController controller;

    private final Fragment[] tabs = new Fragment[5];
    private int current = -1;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateMini();
            App.postDelayed(this, 600);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));

        drawer = findViewById(R.id.drawer);
        bottomNav = findViewById(R.id.bottom_nav);

        miniRoot = findViewById(R.id.mini_root);
        miniTitle = findViewById(R.id.mini_title);
        miniSub = findViewById(R.id.mini_sub);
        miniPlay = findViewById(R.id.mini_play);
        miniPrev = findViewById(R.id.mini_prev);
        miniNext = findViewById(R.id.mini_next);
        miniProgress = findViewById(R.id.mini_progress);

        miniRoot.setOnClickListener(v -> {
            if (PlayerService.isRunning()) {
                startActivity(new Intent(this, PlayerActivity.class));
            }
        });
        miniPlay.setOnClickListener(v -> {
            if (controller == null) return;
            if (controller.isPlaying()) controller.pause();
            else controller.play();
            App.postDelayed(this::updateMini, 150);
        });
        miniPrev.setOnClickListener(v -> {
            if (controller != null && controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem();
        });
        miniNext.setOnClickListener(v -> {
            if (controller != null && controller.hasNextMediaItem()) controller.seekToNextMediaItem();
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_surahs) switchTab(TAB_SURAHS);
            else if (id == R.id.nav_reciters) switchTab(TAB_RECITERS);
            else if (id == R.id.nav_video) switchTab(TAB_VIDEO);
            else if (id == R.id.nav_broadcast) switchTab(TAB_BROADCAST);
            else if (id == R.id.nav_library) switchTab(TAB_LIBRARY);
            return true;
        });

        NavigationView nav = findViewById(R.id.nav_view);
        nav.setNavigationItemSelectedListener(this::onDrawerItem);

        switchTab(TAB_SURAHS);
        int startTab = getIntent().getIntExtra("tab", TAB_SURAHS);
        if (startTab != TAB_SURAHS) switchTab(startTab);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }

    }

    private boolean onDrawerItem(MenuItem item) {
        int id = item.getItemId();
        drawer.closeDrawer(GravityCompat.START);
        if (id == R.id.nav_surahs) switchTab(TAB_SURAHS);
        else if (id == R.id.nav_reciters) switchTab(TAB_RECITERS);
        else if (id == R.id.nav_video) switchTab(TAB_VIDEO);
        else if (id == R.id.nav_library) {
            LibraryFragment.goFavs = false;
            switchTab(TAB_LIBRARY);
        } else if (id == R.id.d_fav) {
            LibraryFragment.goFavs = true;
            switchTab(TAB_LIBRARY);
        } else if (id == R.id.d_live) startActivity(new Intent(this, LiveActivity.class));
        else if (id == R.id.d_radio) switchTab(TAB_BROADCAST);
        else if (id == R.id.d_tafsir) startActivity(new Intent(this, TafsirActivity.class));
        else if (id == R.id.d_read) pickSurahToRead();
        else if (id == R.id.d_settings) startActivity(new Intent(this, SettingsActivity.class));
        else if (id == R.id.d_share) Ui.shareText(this, getString(R.string.share_app_text));
        else if (id == R.id.d_rate) rateApp();
        else if (id == R.id.d_about) InfoActivity.open(this, InfoActivity.PAGE_ABOUT);
        else if (id == R.id.d_privacy) InfoActivity.open(this, InfoActivity.PAGE_PRIVACY);
        else if (id == R.id.d_sources) InfoActivity.open(this, InfoActivity.PAGE_SOURCES);
        else if (id == R.id.d_prayer_times) startActivity(new Intent(this, PrayerTimesActivity.class));
        else if (id == R.id.d_qibla) startActivity(new Intent(this, QiblaActivity.class));
        else if (id == R.id.d_contact) startActivity(new Intent(this, ContactActivity.class));
        else if (id == R.id.d_follow) startActivity(new Intent(this, FollowUsActivity.class));
        else if (id == R.id.d_support) startActivity(new Intent(this, SupportActivity.class));
        return true;
    }

    private void pickSurahToRead() {
        String[] names = new String[114];
        for (Models.Surah s : QuranMeta.all()) {
            names[s.id - 1] = s.id + " - " + getString(R.string.read_title, s.ar);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_read)
                .setItems(names, (d, which) -> ReadActivity.open(this, which + 1))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void rateApp() {
        try {
            Ui.openUrl(this, "market://details?id=" + getPackageName().replace(".debug", ""));
        } catch (Exception e) {
            Ui.openUrl(this, "https://play.google.com/store/apps/details?id="
                    + getPackageName().replace(".debug", ""));
        }
    }

    public void switchTab(int tab) {
        if (tab == current) return;
        current = tab;
        if (tabs[tab] == null) {
            if (tab == TAB_SURAHS) tabs[tab] = new SurahsFragment();
            else if (tab == TAB_RECITERS) tabs[tab] = new RecitersFragment();
            else if (tab == TAB_VIDEO) tabs[tab] = new VideosFragment();
            else if (tab == TAB_BROADCAST) tabs[tab] = new BroadcastFragment();
            else tabs[tab] = new LibraryFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, tabs[tab])
                .commitAllowingStateLoss();
        int title = R.string.tab_surahs;
        int menuId = R.id.nav_surahs;
        if (tab == TAB_RECITERS) {
            title = R.string.tab_reciters;
            menuId = R.id.nav_reciters;
        } else if (tab == TAB_VIDEO) {
            title = R.string.tab_video;
            menuId = R.id.nav_video;
        } else if (tab == TAB_BROADCAST) {
            title = R.string.tab_broadcast;
            menuId = R.id.nav_broadcast;
        } else if (tab == TAB_LIBRARY) {
            title = R.string.tab_library;
            menuId = R.id.nav_library;
        }
        toolbar.setTitle(title);
        bottomNav.setSelectedItemId(menuId);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (PlayerService.isRunning() && controller == null) {
            PlayerManager.controller(this, c -> {
                controller = c;
                updateMini();
            });
        }
        App.post(ticker);
    }

    @Override
    protected void onStop() {
        App.removeCallbacks(ticker);
        PlayerManager.release(controller);
        controller = null;
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PlayerService.isRunning() && controller == null) {
            PlayerManager.controller(this, c -> {
                controller = c;
                updateMini();
            });
        } else {
            updateMini();
        }
    }

    private void updateMini() {
        if (!PlayerService.isRunning() || controller == null
                || controller.getMediaItemCount() == 0) {
            miniRoot.setVisibility(View.GONE);
            return;
        }
        miniRoot.setVisibility(View.VISIBLE);
        int i = controller.getCurrentMediaItemIndex();
        if (i >= 0 && i < controller.getMediaItemCount()) {
            androidx.media3.common.MediaMetadata md =
                    controller.getMediaItemAt(i).mediaMetadata;
            miniTitle.setText(md.title == null ? "" : md.title.toString());
            miniSub.setText(md.artist == null ? "" : md.artist.toString());
        }
        miniPlay.setImageResource(controller.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
        long dur = controller.getDuration();
        long pos = controller.getCurrentPosition();
        if (dur > 0) miniProgress.setProgress((int) (1000 * pos / dur));
        else miniProgress.setProgress(0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
