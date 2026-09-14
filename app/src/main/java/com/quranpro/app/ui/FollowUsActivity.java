package com.quranpro.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.R;
import com.quranpro.app.util.Ui;

/** Follow-us page: Facebook / Instagram / Linktree / LinkedIn. */
public class FollowUsActivity extends AppCompatActivity {

    public static final String FB = "https://facebook.com/youssef.ysah";
    public static final String IG = "https://instagram.com/oulaidi.youssef";
    public static final String LT = "https://linktr.ee/youssef.oulaidi";
    public static final String LI = "https://www.linkedin.com/in/youssef-oulaidi/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        bind(R.id.row_facebook, FB);
        bind(R.id.row_instagram, IG);
        bind(R.id.row_linktree, LT);
        bind(R.id.row_linkedin, LI);
    }

    private void bind(int rowId, final String url) {
        View row = findViewById(rowId);
        View.OnClickListener open = v -> openUrl(url);
        row.setOnClickListener(open);
    }

    private void openUrl(String url) {
        try {
            // Prefer the native apps when installed, fall back to browser.
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(probe);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, R.string.follow_err);
        }
    }
}
