package com.quranpro.app.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.data.Store;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Default reciter (works offline via islamic.network CDN) on first launch.
        if (Store.getCurrent(this) == null) {
            StringBuilder all = new StringBuilder();
            for (int i = 1; i <= 114; i++) {
                if (i > 1) all.append(',');
                all.append(i);
            }
            Store.setCurrent(this, -1, "مشاري راشد العفاسي", -1,
                    "حفص عن عاصم - مرتل", "cdn:ar.alafasy", all.toString());
        }

        App.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 1400);
    }
}
