package com.quranpro.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.quranpro.app.R;
import com.quranpro.app.util.Ui;

/**
 * Contact page — a form that composes an email for the team.
 * The team address is intentionally hidden in the code (not shown in the UI).
 */
public class ContactActivity extends BaseActivity {

    // Recipient stays hidden: assembled at runtime, never listed in the UI.
    private static final String MAILBOX = "youssef" + "virtual";
    private static final String DOMAIN = "@gmail" + ".com";

    private static String mail() {
        return MAILBOX + DOMAIN;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextInputEditText etName = findViewById(R.id.et_name);
        TextInputEditText etMail = findViewById(R.id.et_mail);
        TextInputEditText etSubject = findViewById(R.id.et_subject);
        TextInputEditText etMsg = findViewById(R.id.et_msg);
        Button send = findViewById(R.id.btn_send);

        send.setOnClickListener(v -> {
            String msg = text(etMsg);
            if (msg.isEmpty()) {
                Ui.toast(this, R.string.contact_err);
                return;
            }
            String name = text(etName);
            String reply = text(etMail);
            String subject = text(etSubject);
            if (subject.isEmpty()) {
                subject = getString(R.string.app_short) + " — "
                        + getString(R.string.contact_title);
            }
            StringBuilder body = new StringBuilder();
            body.append(msg).append("\n\n");
            body.append("———————\n");
            if (!name.isEmpty()) body.append("Name: ").append(name).append("\n");
            if (!reply.isEmpty()) body.append("Reply-to: ").append(reply).append("\n");
            body.append("— ").append(getString(R.string.app_short))
                    .append(" v").append(appVersion()).append(" (Android)");

            // Hide the real mailbox behind the client — user just presses "send".
            Uri uri = Uri.parse("mailto:" + Uri.encode(mail())
                    + "?subject=" + Uri.encode(subject)
                    + "&body=" + Uri.encode(body.toString()));
            Intent i = new Intent(Intent.ACTION_SENDTO, uri);
            try {
                startActivity(Intent.createChooser(i, getString(R.string.contact_send)));
                Ui.toast(this, R.string.contact_done);
            } catch (ActivityNotFoundException e) {
                // No mail client — offer copying instead.
                Ui.copyText(this, "contact", body.toString());
                Ui.toast(this, R.string.contact_no_mail);
            }
        });
    }

    private static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }
}
