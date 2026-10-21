package com.example.deliveryapp.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selection state shared by the pizza screen, cart payload and checkout validation. */
public final class PizzaCustomization {
    private final Set<Integer> selected = new HashSet<>();
    private final Set<Integer> defaults = new HashSet<>();
    private Models.PizzaDetail detail;
    private Integer sizeCm;
    private Integer baseOptionId;

    public void apply(Models.PizzaDetail next, Integer requestedSize) {
        Set<Integer> previousDefaults = new HashSet<>(defaults);
        Set<Integer> previousSelected = new HashSet<>(selected);
        boolean first = detail == null;
        detail = next;
        defaults.clear();
        Set<Integer> allowed = new HashSet<>();
        for (Models.ToppingItem item : Models.nz(next.allToppings)) allowed.add(item.toppingId);
        for (Models.ToppingItem item : Models.nz(next.defaultToppings)) defaults.add(item.toppingId);
        selected.clear();
        for (Integer id : previousSelected) if (allowed.contains(id)) selected.add(id);
        for (Models.ToppingItem item : Models.nz(next.defaultToppings)) {
            if (first || !previousDefaults.contains(item.toppingId) || !Boolean.TRUE.equals(item.removable)) {
                selected.add(item.toppingId);
            }
        }

        sizeCm = requestedSize;
        if (sizeCm == null) {
            for (Models.SizePrice size : Models.nz(next.availableSizes)) {
                if (Math.abs(size.basePrice - next.basePriceForSelectedSize) < 0.001) {
                    sizeCm = size.sizeCm;
                    break;
                }
            }
        }
        if (sizeCm == null && !Models.nz(next.availableSizes).isEmpty()) {
            sizeCm = next.availableSizes.get(0).sizeCm;
        }
        if (baseOptionId != null && Models.nz(next.baseOptions).stream()
                .noneMatch(option -> option.pizzaBaseOptionId == baseOptionId)) baseOptionId = null;
    }

    /** Restore saved choices against the current size's catalog; report removed choices. */
    public boolean restore(Models.OrderItemRequest request) {
        boolean changed = false;
        selected.clear(); selected.addAll(defaults);
        baseOptionId = request.pizzaBaseOptionId;
        if (baseOptionId != null && Models.nz(detail.baseOptions).stream()
                .noneMatch(o -> o.pizzaBaseOptionId == baseOptionId)) { baseOptionId = null; changed = true; }
        for (Models.Customization c : Models.nz(request.customizations)) {
            if (c.quantity != 1) { changed = true; continue; }
            if ("removedPizzaIngredient".equalsIgnoreCase(c.type)) {
                Models.ToppingItem t = Models.nz(detail.defaultToppings).stream().filter(v -> v.toppingId == c.id).findFirst().orElse(null);
                if (t != null && Boolean.TRUE.equals(t.removable)) selected.remove(c.id); else changed = true;
            } else if (c.type == null || c.type.isEmpty()) {
                boolean allowed = Models.nz(detail.allToppings).stream().anyMatch(t -> t.toppingId == c.id);
                if (allowed && !defaults.contains(c.id)) selected.add(c.id); else changed = true;
            } else changed = true;
        }
        return changed;
    }

    public boolean toggle(Models.ToppingItem item) {
        if (defaults.contains(item.toppingId) && selected.contains(item.toppingId)
                && !Boolean.TRUE.equals(item.removable)) return false;
        if (!selected.remove(item.toppingId)) selected.add(item.toppingId);
        return true;
    }

    public boolean isSelected(int id) { return selected.contains(id); }
    public Integer sizeCm() { return sizeCm; }
    public Integer baseOptionId() { return baseOptionId; }
    public void setBaseOptionId(Integer id) { baseOptionId = id; }

    public List<Integer> selectedToppingIds() { return new ArrayList<>(selected); }

    public Models.QuoteRequest quoteRequest() {
        Models.QuoteRequest request = new Models.QuoteRequest();
        request.sizeCm = sizeCm;
        request.pizzaBaseOptionId = baseOptionId;
        request.selectedToppingIds = selectedToppingIds();
        return request;
    }

    public Models.OrderItemRequest orderItem(int pizzaId, String notes) {
        Models.OrderItemRequest request = new Models.OrderItemRequest();
        request.pizzaId = pizzaId;
        request.sizeCm = sizeCm;
        request.pizzaBaseOptionId = baseOptionId;
        request.notes = notes;
        for (Integer id : selected) {
            if (!defaults.contains(id)) request.customizations.add(new Models.Customization(id, 1, null));
        }
        for (Integer id : defaults) {
            if (!selected.contains(id)) {
                request.customizations.add(new Models.Customization(id, 1, "removedPizzaIngredient"));
            }
        }
        return request;
    }

    public double estimate() {
        if (detail == null) return 0;
        double total = detail.basePriceForSelectedSize;
        for (Models.PizzaBaseOption option : Models.nz(detail.baseOptions)) {
            if (baseOptionId != null && option.pizzaBaseOptionId == baseOptionId) total += option.extraPrice;
        }
        for (Models.ToppingPrice price : Models.nz(detail.extraPrices)) {
            if (selected.contains(price.toppingId) && !defaults.contains(price.toppingId)) total += price.extraPrice;
        }
        return total;
    }
}
