package com.example.deliveryapp.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory cart for the currently selected branch. */
public final class Cart {
    private static final Cart INSTANCE = new Cart();

    public static Cart get() { return INSTANCE; }

    public static class Line {
        public final String id;
        public final String name;
        public String detail;      // readable options, e.g. "30cm · Thin base · + Bacon"
        public double unitPrice;   // estimate only; the server recalculates
        public int quantity;
        public final Models.OrderItemRequest request;
        public final Models.SpecialItemRequest specialRequest;

        public Line(String name, String detail, double unitPrice, int quantity, Models.OrderItemRequest request) {
            this.id = UUID.randomUUID().toString();
            this.name = name; this.detail = detail; this.unitPrice = unitPrice;
            this.quantity = quantity; this.request = request; this.specialRequest = null;
        }

        public java.util.Map<String, String> componentDetails = new java.util.HashMap<>();

        private Line(String id, Line other) {
            this.id = id; name = other.name; detail = other.detail; unitPrice = other.unitPrice;
            quantity = other.quantity; request = other.request; specialRequest = other.specialRequest;
            componentDetails = new java.util.HashMap<>(other.componentDetails);
        }

        public Line(String name, String detail, double unitPrice, int quantity, Models.SpecialItemRequest request) {
            this.id = UUID.randomUUID().toString();
            this.name = name; this.detail = detail; this.unitPrice = unitPrice;
            this.quantity = quantity; this.request = null; this.specialRequest = request;
        }
    }

    public Line find(String id) {
        for (Line line : lines) if (line.id.equals(id)) return line;
        return null;
    }

    /** Replace atomically at the same position; never turn a stale edit into a new item. */
    public boolean replace(String id, Line replacement) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id.equals(id)) {
                lines.set(i, new Line(id, replacement));
                notifyChanged();
                return true;
            }
        }
        return false;
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

    public void pricesUpdated() { notifyChanged(); }

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
