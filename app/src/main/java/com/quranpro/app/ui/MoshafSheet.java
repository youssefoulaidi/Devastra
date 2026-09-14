package com.quranpro.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.quranpro.app.R;
import com.quranpro.app.data.Models;
import com.quranpro.app.util.Ui;

import java.util.List;

/** Bottom sheet: choose a moshaf/riwayah of the selected reciter. */
public class MoshafSheet extends BottomSheetDialogFragment {

    public interface OnPick {
        void onPick(Models.Reciter r, Models.Moshaf m);
    }

    public static void show(Fragment parent, Models.Reciter r) {
        MoshafSheet s = new MoshafSheet();
        Bundle b = new Bundle();
        b.putInt("rid", r.id);
        b.putString("rname", r.name);
        b.putString("mosafs", new Gson().toJson(r.mosafs));
        s.setArguments(b);
        s.show(parent.getChildFragmentManager(), "moshaf");
    }

    static class H extends RecyclerView.ViewHolder {
        TextView name, meta;
        H(View view) {
            super(view);
            name = view.findViewById(R.id.name);
            meta = view.findViewById(R.id.meta);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_moshaf, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        Bundle b = getArguments();
        final int rid = b == null ? 0 : b.getInt("rid");
        final String rname = b == null ? "" : b.getString("rname");
        String json = b == null ? "[]" : b.getString("mosafs");
        final List<Models.Moshaf> mosafs = new Gson().fromJson(json,
                new TypeToken<List<Models.Moshaf>>() {}.getType());

        ((TextView) v.findViewById(R.id.sheet_sub)).setText(rname);

        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(new RecyclerView.Adapter<H>() {
            @NonNull
            @Override
            public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new H(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_moshaf, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull H h, int position) {
                final Models.Moshaf m = mosafs.get(position);
                h.name.setText(m.name);
                int n = m.surahIds().size();
                h.meta.setText(Ui.digits(n) + " / " + Ui.digits(114));
                h.itemView.setOnClickListener(vv -> {
                    Fragment p = getParentFragment();
                    Models.Reciter r = new Models.Reciter();
                    r.id = rid;
                    r.name = rname;
                    r.mosafs = mosafs;
                    if (p instanceof OnPick) ((OnPick) p).onPick(r, m);
                    dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return mosafs == null ? 0 : mosafs.size();
            }
        });
    }
}
