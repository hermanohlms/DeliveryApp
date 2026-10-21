package com.example.deliveryapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.util.Money;

import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.Holder> {
    public interface Listener { void edit(Cart.Line line); }
    private final Listener listener;
    public CartAdapter(Listener listener) { this.listener = listener; }
    private final List<Cart.Line> lines = new ArrayList<>();

    public void submit(List<Cart.Line> newLines) {
        lines.clear();
        lines.addAll(newLines);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart_line, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final Cart.Line line = lines.get(position);
        h.name.setText(line.name);
        h.price.setText(Money.rand(line.unitPrice * line.quantity));
        h.detail.setText(line.detail);
        Ui.show(h.detail, line.detail != null && !line.detail.isEmpty());
        String notes = CartEditor.notes(line);
        h.notes.setText(notes == null ? "" : "Kitchen note: " + notes); Ui.show(h.notes, notes != null && !notes.isBlank());
        h.edit.setOnClickListener(v -> listener.edit(line));
        h.edit.setContentDescription("Edit " + line.name);
        h.remove.setContentDescription("Remove " + line.name);
        h.minus.setContentDescription("Decrease " + line.name + " quantity");
        h.plus.setContentDescription("Increase " + line.name + " quantity");
        h.plus.setEnabled(line.quantity < 50);
        h.qty.setText(String.valueOf(line.quantity));
        h.minus.setOnClickListener(v -> Cart.get().setQuantity(line.id, line.quantity - 1));
        h.plus.setOnClickListener(v -> Cart.get().setQuantity(line.id, line.quantity + 1));
        h.remove.setOnClickListener(v -> Cart.get().remove(line.id));
    }

    @Override public int getItemCount() { return lines.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, price, detail, notes, qty;
        final View minus, plus, remove, edit;
        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            price = v.findViewById(R.id.tvLinePrice);
            detail = v.findViewById(R.id.tvDetail);
            notes = v.findViewById(R.id.tvNotes); edit = v.findViewById(R.id.btnEdit);
            qty = v.findViewById(R.id.tvQty);
            minus = v.findViewById(R.id.btnMinus);
            plus = v.findViewById(R.id.btnPlus);
            remove = v.findViewById(R.id.btnRemove);
        }
    }
}
