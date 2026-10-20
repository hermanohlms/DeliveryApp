package com.example.deliveryapp.ui;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deliveryapp.R;

import java.util.ArrayList;
import java.util.List;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.Holder> {
    /** What one order card shows, whether it came from the server or from this phone. */
    public static class OrderRow {
        public long id;
        public String status;
        public String meta;
        public String lines;
        public String total;   // may be null
        /** still in progress, so the card opens the tracking screen */
        public boolean trackable;
    }

    private final List<OrderRow> rows = new ArrayList<>();

    public void submit(List<OrderRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        OrderRow r = rows.get(position);
        h.id.setText("Order #" + r.id);
        h.status.setText(r.status == null ? "" : r.status);
        h.meta.setText(r.meta);
        h.lines.setText(r.lines);
        Ui.show(h.lines, r.lines != null && !r.lines.isEmpty());
        h.total.setText(r.total == null ? "" : r.total);
        Ui.show(h.total, r.total != null);
        Ui.show(h.track, r.trackable);
        h.itemView.setOnClickListener(r.trackable ? v -> {
            Intent i = new Intent(v.getContext(), TrackOrderActivity.class);
            i.putExtra(TrackOrderActivity.EXTRA_ORDER_ID, r.id);
            v.getContext().startActivity(i);
        } : null);
        h.itemView.setClickable(r.trackable);
    }

    @Override public int getItemCount() { return rows.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView id, status, meta, lines, total, track;
        Holder(View v) {
            super(v);
            id = v.findViewById(R.id.tvOrderId);
            status = v.findViewById(R.id.tvStatus);
            meta = v.findViewById(R.id.tvMeta);
            lines = v.findViewById(R.id.tvLines);
            total = v.findViewById(R.id.tvTotal);
            track = v.findViewById(R.id.tvTrack);
        }
    }
}
