package com.quranpro.app.ui;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.quranpro.app.util.LangHelper;

/** Activity base that applies the in-app language before any view inflation. */
public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangHelper.wrap(newBase));
    }
}
