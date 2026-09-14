package com.quranpro.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quranpro.app.R;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.Models;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** Big reciters list (mp3quran.net): search + riwayah filter + moshaf picker. */
public class RecitersFragment extends Fragment implements MoshafSheet.OnPick {

    private ReciterAdapter adapter;
    private final List<Models.Reciter> all = new ArrayList<>();
    private List<Models.Reciter> shown = new ArrayList<>();
    private final List<Models.Riwaya> riwayat = new ArrayList<>();

    private ProgressBar progress;
    private View error;
    private TextView empty;
    private EditText search;
    private Spinner sp;
    private String query = "";
    private int rewayaId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reciters, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ReciterAdapter();
        list.setAdapter(adapter);

        progress = v.findViewById(R.id.progress);
        error = v.findViewById(R.id.error);
        empty = v.findViewById(R.id.empty);
        search = v.findViewById(R.id.search);
        sp = v.findViewById(R.id.sp_riwaya);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        v.findViewById(R.id.btn_retry).setOnClickListener(vv -> load());
        loadRiwayat();
        load();
    }

    private void loadRiwayat() {
        if (getContext() == null) return;
        Api.fetchRiwayat(requireContext(), new Api.Cb<List<Models.Riwaya>>() {
            @Override public void ok(List<Models.Riwaya> v) {
                if (getContext() == null) return;
                riwayat.clear();
                riwayat.addAll(v);
                List<String> names = new ArrayList<>();
                names.add(getString(R.string.all_riwayat));
                for (Models.Riwaya r : riwayat) names.add(r.name);
                ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, names);
                ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                sp.setAdapter(ad);
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> p, View vw, int pos, long id) {
                        int nid = pos == 0 ? 0 : riwayat.get(pos - 1).id;
                        if (nid != rewayaId) {
                            rewayaId = nid;
                            load();
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
            }
            @Override public void err(String m) {}
        });
    }

    private void load() {
        if (getContext() == null) return;
        progress.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        Api.fetchReciters(requireContext(), rewayaId, new Api.Cb<List<Models.Reciter>>() {
            @Override public void ok(List<Models.Reciter> v) {
                progress.setVisibility(View.GONE);
                all.clear();
                all.addAll(v);
                applyFilter();
                if (!v.isEmpty() && v.get(0).fallback) {
                    Ui.toast(requireContext(), R.string.error_network);
                }
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                error.setVisibility(View.VISIBLE);
            }
        });
    }

    private void applyFilter() {
        shown = new ArrayList<>();
        for (Models.Reciter r : all) {
            if (query.isEmpty() || r.name.contains(query)) shown.add(r);
        }
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onPick(Models.Reciter r, Models.Moshaf m) {
        if (getContext() == null) return;
        Store.setCurrent(requireContext(), r.id, r.name, m.id, m.name, m.server, m.list);
        Ui.toast(requireContext(), r.name + " • " + m.name);
        if (adapter != null) adapter.notifyDataSetChanged();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).switchTab(MainActivity.TAB_SURAHS);
        }
    }

    // ---------------- adapter ----------------

    class ReciterAdapter extends RecyclerView.Adapter<ReciterAdapter.H> {
        class H extends RecyclerView.ViewHolder {
            TextView letter, name, meta;
            View check;
            H(View v) {
                super(v);
                letter = v.findViewById(R.id.letter);
                name = v.findViewById(R.id.name);
                meta = v.findViewById(R.id.meta);
                check = v.findViewById(R.id.check);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_reciter, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Models.Reciter r = shown.get(position);
            h.name.setText(r.name);
            String l = r.letter == null || r.letter.isEmpty() ? r.name.substring(0, 1) : r.letter;
            h.letter.setText(l);
            String count = getString(R.string.moshaf_count, r.mosafs.size())
                    .replace(String.valueOf(r.mosafs.size()), Ui.digits(r.mosafs.size()));
            String first = r.mosafs.isEmpty() ? "" : " • " + r.mosafs.get(0).name;
            h.meta.setText(count + first);
            Store.Current cur = Store.getCurrent(h.itemView.getContext());
            h.check.setVisibility(cur != null && cur.reciterId == r.id ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> MoshafSheet.show(RecitersFragment.this, r));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
