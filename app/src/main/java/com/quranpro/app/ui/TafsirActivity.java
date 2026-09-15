package com.quranpro.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.Models;
import com.quranpro.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio tafsir: choose a tafsir, then a surah (or an ayah range of it) and listen.
 *
 * <p>Fixes the empty list: the API returns {@code tafsir.soar[]} while the parser looked
 * for {@code tafsir.sora} (object), so no surah was ever shown. Entries are now grouped
 * per surah with their ayah range, and are kept on disk so the section still opens
 * without internet after it has been loaded once.
 */
public class TafsirActivity extends BaseActivity {

    private Spinner sp;
    private ProgressBar progress;
    private TextView empty;
    private final List<Models.TafsirInfo> tafasir = new ArrayList<>();
    private final List<Models.TafsirSura> suras = new ArrayList<>();
    private SuraAdapter adapter;
    private String currentTafsir = "";
    private Models.TafsirInfo selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tafsir);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        sp = findViewById(R.id.sp_tafsir);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SuraAdapter();
        list.setAdapter(adapter);

        empty.setOnClickListener(v -> {
            if (selected != null) loadSuras(selected);
            else loadTafasir();
        });

        loadTafasir();
    }

    private void loadTafasir() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        Api.fetchTafasir(this, new Api.Cb<List<Models.TafsirInfo>>() {
            @Override public void ok(List<Models.TafsirInfo> v) {
                progress.setVisibility(View.GONE);
                tafasir.clear();
                tafasir.addAll(v);
                if (tafasir.isEmpty()) tafasir.addAll(Api.defaultTafasir());
                List<String> names = new ArrayList<>();
                for (Models.TafsirInfo t : tafasir) names.add(t.name);
                ArrayAdapter<String> ad = new ArrayAdapter<>(TafsirActivity.this,
                        android.R.layout.simple_spinner_item, names);
                ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                sp.setAdapter(ad);
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> p, View vw, int pos, long id) {
                        if (pos >= 0 && pos < tafasir.size()) loadSuras(tafasir.get(pos));
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
                if (!tafasir.isEmpty()) loadSuras(tafasir.get(0));
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                tafasir.clear();
                tafasir.addAll(Api.defaultTafasir());
                if (tafasir.isEmpty()) {
                    showEmpty(getString(R.string.error_network));
                } else {
                    loadSuras(tafasir.get(0));
                }
            }
        });
    }

    private void loadSuras(Models.TafsirInfo t) {
        selected = t;
        currentTafsir = t.name;
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        Api.fetchTafsirSuras(this, t.id, new Api.Cb<List<Models.TafsirSura>>() {
            @Override public void ok(List<Models.TafsirSura> v) {
                progress.setVisibility(View.GONE);
                suras.clear();
                suras.addAll(v);
                adapter.notifyDataSetChanged();
                if (suras.isEmpty()) {
                    showEmpty(getString(R.string.tafsir_no_items));
                } else {
                    empty.setVisibility(View.GONE);
                }
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                suras.clear();
                adapter.notifyDataSetChanged();
                showEmpty(getString(R.string.error_network)
                        + "\n" + getString(R.string.tafsir_retry));
            }
        });
    }

    private void showEmpty(String msg) {
        empty.setVisibility(View.VISIBLE);
        empty.setText(msg);
    }

    class SuraAdapter extends RecyclerView.Adapter<SuraAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            TextView name, sub;
            ImageButton btnPlay;
            H(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                sub = v.findViewById(R.id.sub);
                btnPlay = v.findViewById(R.id.btn_play);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tafsir_surah, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            final Models.TafsirSura s = suras.get(position);
            String title = s.surahTitle();
            String range = s.rangeTitle();
            h.name.setText(title.isEmpty() ? s.name : title);
            h.sub.setText(range);
            h.sub.setVisibility(range.isEmpty() ? View.GONE : View.VISIBLE);
            View.OnClickListener play = v -> {
                PlayerManager.playTafsir(v.getContext(), currentTafsir,
                        s.suraId, title, s.url);
                v.getContext().startActivity(new Intent(v.getContext(), PlayerActivity.class));
            };
            h.btnPlay.setOnClickListener(play);
            h.itemView.setOnClickListener(play);
            h.itemView.setOnLongClickListener(v -> {
                String url = s.url;
                Ui.copyText(v.getContext(), "tafsir", url);
                Ui.toast(v.getContext(), url);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return suras.size();
        }
    }
}
