package com.example.deliveryapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.util.Money;

import java.util.ArrayList;
import java.util.List;

public class MenuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onPizza(int pizzaId);
        void onItem(int itemId);
        void onSpecial(long specialId);
    }

    public static final class Row {
        static final int HEADER = 0, PIZZA = 1, ITEM = 2, SPECIAL = 3;
        final int type;
        String header;
        Models.PizzaCard pizza;
        Models.MenuItem item;
        Models.Special special;
        private Row(int type) { this.type = type; }

        public static Row header(String text) { Row r = new Row(HEADER); r.header = text; return r; }
        public static Row pizza(Models.PizzaCard p) { Row r = new Row(PIZZA); r.pizza = p; return r; }
        public static Row item(Models.MenuItem m) { Row r = new Row(ITEM); r.item = m; return r; }
        public static Row special(Models.Special s) { Row r = new Row(SPECIAL); r.special = s; return r; }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public MenuAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @Override public int getItemCount() { return rows.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == Row.HEADER) return new HeaderHolder(inf.inflate(R.layout.item_header, parent, false));
        return new RowHolder(inf.inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText(row.header);
            return;
        }
        RowHolder h = (RowHolder) holder;
        if (row.type == Row.PIZZA) {
            final Models.PizzaCard p = row.pizza;
            h.name.setText(p.name);
            List<String> tops = Models.nz(p.defaultToppings);
            h.sub.setText(String.join(", ", tops));
            Ui.show(h.sub, !tops.isEmpty());
            Ui.show(h.tag, false);
            Ui.show(h.from, true);
            h.price.setText(Money.rand(p.basePrice));
            h.itemView.setOnClickListener(v -> listener.onPizza(p.pizzaId));
        } else if (row.type == Row.SPECIAL) {
            Models.Special s = row.special;
            h.name.setText(s.name);
            h.sub.setText(s.description == null ? "" : s.description);
            Ui.show(h.sub, s.description != null && !s.description.isEmpty());
            h.tag.setText(s.availableToday ? "Today" : "This week");
            Ui.show(h.tag, true);
            Ui.show(h.from, false);
            h.price.setText(Money.rand(s.price));
            h.itemView.setOnClickListener(v -> listener.onSpecial(s.id));
        } else {
            final Models.MenuItem m = row.item;
            h.name.setText(m.name);
            boolean hasDesc = m.description != null && !m.description.trim().isEmpty();
            h.sub.setText(hasDesc ? m.description : "");
            Ui.show(h.sub, hasDesc);
            String tag = m.is2l ? "2 L" : m.is300ml ? "300 ml" : null;
            h.tag.setText(tag == null ? "" : tag);
            Ui.show(h.tag, tag != null);
            Ui.show(h.from, false);
            h.price.setText(Money.rand(m.priceOrZero()));
            h.itemView.setOnClickListener(v -> listener.onItem(m.menuItemId));
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderHolder(View v) { super(v); title = (TextView) v; }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final TextView name, sub, tag, from, price;
        RowHolder(View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            sub = v.findViewById(R.id.tvSub);
            tag = v.findViewById(R.id.tvTag);
            from = v.findViewById(R.id.tvFrom);
            price = v.findViewById(R.id.tvPrice);
        }
    }
}
