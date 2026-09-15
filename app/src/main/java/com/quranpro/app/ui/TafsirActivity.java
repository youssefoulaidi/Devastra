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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Audio tafsir: choose a tafsir, then a surah. */
public class TafsirActivity extends BaseActivity {

    private Spinner sp;
    private ProgressBar progress;
    private TextView empty;
    private final List<Models.TafsirInfo> tafasir = new ArrayList<>();
    private final List<Models.TafsirSura> suras = new ArrayList<>();
    private SuraAdapter adapter;
    private String currentTafsir = "";

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

        progress.setVisibility(View.VISIBLE);
        Api.fetchTafasir(this, new Api.Cb<List<Models.TafsirInfo>>() {
            @Override public void ok(List<Models.TafsirInfo> v) {
                progress.setVisibility(View.GONE);
                tafasir.clear();
                tafasir.addAll(v);
                List<String> names = new ArrayList<>();
                for (Models.TafsirInfo t : tafasir) names.add(t.name);
                ArrayAdapter<String> ad = new ArrayAdapter<>(TafsirActivity.this,
                        android.R.layout.simple_spinner_item, names);
                ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                sp.setAdapter(ad);
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> p, View vw, int pos, long id) {
                        loadSuras(tafasir.get(pos));
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
                if (!tafasir.isEmpty()) loadSuras(tafasir.get(0));
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                empty.setText(R.string.error_network);
            }
        });
    }

    private void loadSuras(Models.TafsirInfo t) {
        currentTafsir = t.name;
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        Api.fetchTafsirSuras(this, t.id, new Api.Cb<List<Models.TafsirSura>>() {
            @Override public void ok(List<Models.TafsirSura> v) {
                progress.setVisibility(View.GONE);
                suras.clear();
                suras.addAll(v);
                adapter.notifyDataSetChanged();
                empty.setVisibility(suras.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    class SuraAdapter extends RecyclerView.Adapter<SuraAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            TextView name;
            ImageButton btnPlay;
            H(View v) {
                super(v);
                name = v.findViewById(R.id.name);
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
            h.name.setText(s.name);
            View.OnClickListener play = v -> {
                PlayerManager.playTafsir(v.getContext(), currentTafsir,
                        s.suraId, s.name, s.url);
                v.getContext().startActivity(new Intent(v.getContext(), PlayerActivity.class));
            };
            h.btnPlay.setOnClickListener(play);
            h.itemView.setOnClickListener(play);
        }

        @Override
        public int getItemCount() {
            return suras.size();
        }
    }
}
