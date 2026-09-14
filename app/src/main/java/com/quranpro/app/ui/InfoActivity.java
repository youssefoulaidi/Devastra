package com.quranpro.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.R;
import com.quranpro.app.util.Ui;

/** About / privacy / sources page. */
public class InfoActivity extends AppCompatActivity {

    public static final int PAGE_ABOUT = 0;
    public static final int PAGE_PRIVACY = 1;
    public static final int PAGE_SOURCES = 2;

    public static void open(Context c, int page) {
        Intent i = new Intent(c, InfoActivity.class);
        i.putExtra("page", page);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        int page = getIntent().getIntExtra("page", PAGE_ABOUT);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView body = findViewById(R.id.t_body);
        boolean ar = Ui.isArabic();
        if (page == PAGE_PRIVACY) {
            toolbar.setTitle(R.string.privacy_title);
            body.setText(ar ? privacyAr() : privacyEn());
        } else if (page == PAGE_SOURCES) {
            toolbar.setTitle(R.string.sources_title);
            body.setText(ar ? sourcesAr() : sourcesEn());
        } else {
            toolbar.setTitle(R.string.about_title);
            body.setText(ar ? aboutAr() : aboutEn());
        }

        findViewById(R.id.btn_mp3quran).setOnClickListener(v ->
                Ui.openUrl(this, "https://www.mp3quran.net"));
        findViewById(R.id.btn_alquran).setOnClickListener(v ->
                Ui.openUrl(this, "https://alquran.cloud"));
        findViewById(R.id.links).setVisibility(page == PAGE_ABOUT ? View.VISIBLE : View.GONE);

        // YSAH-DEV developer badge — About page only; opens the dev's profile.
        int vis = page == PAGE_ABOUT ? View.VISIBLE : View.GONE;
        findViewById(R.id.dev_card).setVisibility(vis);
        findViewById(R.id.dev_sub).setVisibility(vis);
        findViewById(R.id.dev_card).setOnClickListener(v ->
                Ui.openUrl(this, FollowUsActivity.LT));
    }

    private String aboutAr() {
        return "تطبيق QuranPro — القرآن الكريم صوتًا وصورةً وقراءةً.\n\n"
                + "• استمع إلى 114 سورة بأصوات أكثر من 150 قارئًا من مختلف الروايات.\n"
                + "• شاهد التلاوات المرئية والبث المباشر لقناتي القرآن والسنة.\n"
                + "• استمع إلى إذاعات القرآن الكريم والتفسير الصوتي.\n"
                + "• اقرأ نص المصحف بالرسم العثماني مع متابعة الآيات أثناء التلاوة.\n"
                + "• حمّل السور للاستماع دون إنترنت، وأضف مفضّلتك، واضبط مؤقت النوم.\n\n"
                + "جميع التلاوات والنصوص من مصادر مجانية موثوقة (mp3quran.net ومنصة AlQuran Cloud).";
    }

    private String aboutEn() {
        return "QuranPro — the Holy Quran in audio, video and text.\n\n"
                + "• Listen to 114 surahs with 150+ reciters across many riwayat.\n"
                + "• Watch visual recitations and live Quran & Sunnah TV.\n"
                + "• Quran radios and audio tafsir.\n"
                + "• Read the uthmani text with ayah-by-ayah follow.\n"
                + "• Offline downloads, favorites and sleep timer.\n\n"
                + "All recitations and texts come from free trusted sources "
                + "(mp3quran.net and the AlQuran Cloud platform).";
    }

    private String privacyAr() {
        return "سياسة الخصوصية\n\n"
                + "• التطبيق لا يجمع أي بيانات شخصية ولا يتطلب تسجيل الدخول.\n"
                + "• الاتصال بالإنترنت يُستخدم فقط لجلب التلاوات والنصوص من المصادر العامة.\n"
                + "• تُحفظ تفضيلاتك (القارئ، المفضلة، التحميلات) على جهازك فقط.\n"
                + "• لا توجد إعلانات ولا تتبّع ولا مشاركة لأي بيانات مع أطراف ثالثة.";
    }

    private String privacyEn() {
        return "Privacy policy\n\n"
                + "• The app collects no personal data and requires no account.\n"
                + "• Internet is used only to fetch recitations and texts from public sources.\n"
                + "• Your preferences (reciter, favorites, downloads) stay on your device.\n"
                + "• No ads, no tracking, no data shared with third parties.";
    }

    private String sourcesAr() {
        return "مصادر الصوت والصورة والنص (مجانية ودون مفتاح):\n\n"
                + "• mp3quran.net API v3 — القرّاء والمصاحف والفيديو والبث والإذاعات والتفسير وتوقيت الآيات.\n"
                + "• api.alquran.cloud — نص المصحف بالرسم العثماني.\n"
                + "• cdn.islamic.network — ملفات صوتية بديلة للسور.\n"
                + "• everyayah.com — مرجع ملفات التلاوة آيةً بآية.\n\n"
                + "جزى الله القائمين على هذه المنصات خير الجزاء.";
    }

    private String sourcesEn() {
        return "Audio, video & text sources (free, no key):\n\n"
                + "• mp3quran.net API v3 — reciters, masahif, videos, live TV, radios, tafsir, ayat timing.\n"
                + "• api.alquran.cloud — uthmani mushaf text.\n"
                + "• cdn.islamic.network — fallback surah audio files.\n"
                + "• everyayah.com — ayah-by-ayah recitation reference.";
    }
}
