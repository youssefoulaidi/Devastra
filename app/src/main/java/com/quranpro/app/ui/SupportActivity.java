package com.quranpro.app.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.R;
import com.quranpro.app.util.Ui;

/**
 * Support page:
 *  - PayPal button opens the (hidden) donation QR link of the developer.
 *  - "Watch an ad" button — until ads are integrated it temporarily opens the
 *    developer Linktree, exactly as requested.
 */
public class SupportActivity extends BaseActivity {

    // Donation link kept out of the UI — assembled at runtime.
    private static final String PAYPAL_QR = "https://www.paypal.com/qrcodes/p2pqrc/"
            + "ZMGX" + "8NAB" + "3T7AJ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_paypal).setOnClickListener(v -> {
            Ui.toast(this, R.string.support_thanks);
            Ui.openUrl(this, PAYPAL_QR);
        });

        findViewById(R.id.btn_ads).setOnClickListener(v -> {
            Ui.toast(this, R.string.support_thanks);
            Ui.openUrl(this, FollowUsActivity.LT);
        });
    }
}
