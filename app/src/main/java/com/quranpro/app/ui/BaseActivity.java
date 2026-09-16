package com.quranpro.app.ui;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.quranpro.app.data.Store;
import com.quranpro.app.util.LangHelper;

/** Activity base that applies language + app color theme before inflation. */
public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Apply selected brand color theme before super so Material3 picks correct primary
        try {
            setTheme(Store.appColorRes(this));
        } catch (Exception ignored) {}
        super.onCreate(savedInstanceState);
    }
}
