package com.example.deliveryapp.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory cart for the currently selected branch. */
public final class Cart {
    private static final Cart INSTANCE = new Cart();

    public static Cart get() { return INSTANCE; }

    public static class Line {
        public final String id = UUID.randomUUID().toString();
        public final String name;
        public final String detail;      // readable options, e.g. "30cm · Thin base · + Bacon"
        public final double unitPrice;   // estimate only; the server recalculates
        public int quantity;
        public final Models.OrderItemRequest request;

        public Line(String name, String detail, double unitPrice, int quantity, Models.OrderItemRequest request) {
            this.name = name; this.detail = detail; this.unitPrice = unitPrice;
            this.quantity = quantity; this.request = request;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private Cart() {}

    public List<Line> lines() { return new ArrayList<>(lines); }

    public boolean isEmpty() { return lines.isEmpty(); }

    public void add(Line l) { lines.add(l); notifyChanged(); }

    public void setQuantity(String id, int quantity) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id.equals(id)) {
                if (quantity <= 0) lines.remove(i); else lines.get(i).quantity = Math.min(quantity, 50);
                break;
            }
        }
        notifyChanged();
    }

    public void remove(String id) { setQuantity(id, 0); }

    public void clear() { lines.clear(); notifyChanged(); }

    public int count() {
        int n = 0;
        for (Line l : lines) n += l.quantity;
        return n;
    }

    public double total() {
        double t = 0;
        for (Line l : lines) t += l.unitPrice * l.quantity;
        return t;
    }

    public void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }

    public void removeListener(Runnable r) { listeners.remove(r); }

    private void notifyChanged() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }
}
