package com.example.deliveryapp.data;

import java.util.*;

/** Editable burger/menu choices, independent of Android views and the live cart. */
public final class MenuCustomization {
    private final Models.MenuItem item;
    private final List<Models.MenuItem> catalog;
    private final Set<Integer> toppings = new HashSet<>();
    private final Map<Integer, Set<Integer>> modifiers = new HashMap<>();

    public MenuCustomization(Models.MenuItem item, List<Models.MenuItem> catalog) {
        this.item = item; this.catalog = catalog;
        for (Models.BurgerTopping t : allToppings()) if (t.defaultSelected) toppings.add(t.toppingId);
    }
    public List<Models.BurgerTopping> allToppings() { return CatalogOptions.toppings(item, catalog); }
    public List<Models.BurgerTopping> proteins() { return group(true, false); }
    public List<Models.BurgerTopping> defaults() { return group(false, true); }
    public List<Models.BurgerTopping> extras() { return group(false, false); }
    private List<Models.BurgerTopping> group(boolean protein, boolean defaults) {
        List<Models.BurgerTopping> result = new ArrayList<>();
        for (Models.BurgerTopping t : allToppings()) {
            if (protein ? t.isProtein() : !t.isProtein() && t.defaultSelected == defaults) result.add(t);
        }
        return result;
    }
    public boolean selected(int id) { return toppings.contains(id); }
    public boolean selected(Models.ModifierGroup g, int id) { return modifiers.getOrDefault(g.groupId, Set.of()).contains(id); }
    public List<Models.ModifierGroup> visibleGroups() {
        Set<Integer> selected = new HashSet<>();
        for (Set<Integer> group : modifiers.values()) selected.addAll(group);
        return CatalogOptions.visibleGroups(item, selected);
    }
    public void toggle(Models.BurgerTopping t) {
        if (t.isProtein()) {
            for (Models.BurgerTopping p : proteins()) toppings.remove(p.toppingId);
            toppings.add(t.toppingId);
        } else if (selected(t.toppingId)) {
            if (!t.defaultSelected || t.isRemovable()) toppings.remove(t.toppingId);
        } else toppings.add(t.toppingId);
    }
    public void toggle(Models.ModifierGroup g, int id) {
        Set<Integer> chosen = modifiers.computeIfAbsent(g.groupId, key -> new HashSet<>());
        if (g.maxAllowed() == 1) {
            boolean was = chosen.contains(id);
            chosen.clear();
            if (!was || g.minRequired() > 0) chosen.add(id);
        } else if (!chosen.remove(id) && (g.maxAllowed() == 0 || chosen.size() < g.maxAllowed())) chosen.add(id);
        Set<Integer> visible = new HashSet<>();
        for (Models.ModifierGroup group : visibleGroups()) visible.add(group.groupId);
        modifiers.keySet().retainAll(visible);
    }
    /** Restore a saved request. Missing/changed options are dropped, and reported for review. */
    public boolean restore(Models.OrderItemRequest request) {
        if (request == null) return false;
        boolean changed = false;
        toppings.clear(); modifiers.clear();
        for (Models.Customization c : Models.nz(request.customizations)) {
            boolean found = false;
            if ("modifierOption".equalsIgnoreCase(c.type)) {
                for (Models.ModifierGroup g : Models.nz(item.modifierGroups)) {
                    for (Models.ModifierOption o : Models.nz(g.options)) if (o.optionId == c.id) {
                        Set<Integer> chosen = modifiers.computeIfAbsent(g.groupId, key -> new HashSet<>());
                        if (c.quantity == 1 && (g.maxAllowed() == 0 || chosen.size() < g.maxAllowed())) {
                            chosen.add(c.id); found = true;
                        }
                    }
                }
            } else if ("burgerTopping".equalsIgnoreCase(c.type) || "burgerExtra".equalsIgnoreCase(c.type)) {
                for (Models.BurgerTopping t : allToppings()) if (t.toppingId == c.id && c.quantity == 1) {
                    if ("burgerExtra".equalsIgnoreCase(c.type) && t.defaultSelected) break;
                    if ("burgerTopping".equalsIgnoreCase(c.type) && !t.defaultSelected && !t.isProtein()) break;
                    toppings.add(c.id); found = true;
                }
            }
            changed |= !found;
        }
        for (Models.BurgerTopping t : defaults()) if (!t.isRemovable() && !selected(t.toppingId)) {
            toppings.add(t.toppingId); changed = true;
        }
        Set<Integer> visible = new HashSet<>();
        for (Models.ModifierGroup g : visibleGroups()) visible.add(g.groupId);
        changed |= modifiers.keySet().removeIf(id -> !visible.contains(id));
        return changed;
    }
    public String problem() {
        for (Models.ModifierGroup g : visibleGroups()) {
            if (modifiers.getOrDefault(g.groupId, Set.of()).size() < g.minRequired()) return g.name != null && g.name.toLowerCase(Locale.ROOT).startsWith("choose ") ? g.name : "Choose " + g.name;
        }
        if (!proteins().isEmpty() && proteins().stream().filter(t -> selected(t.toppingId)).count() != 1) return "Choose your protein";
        return null;
    }
    public double unitPrice() {
        double price = item.priceOrZero();
        for (Models.BurgerTopping t : allToppings()) if (selected(t.toppingId) && !t.defaultSelected) price += t.priceOrZero();
        for (Models.ModifierGroup g : visibleGroups()) for (Models.ModifierOption o : Models.nz(g.options)) {
            if (selected(g, o.optionId) && o.price != null) price += o.price;
        }
        return price;
    }
    public Models.OrderItemRequest request(String notes) {
        Models.OrderItemRequest request = new Models.OrderItemRequest();
        request.menuItemId = item.menuItemId; request.notes = notes == null || notes.isBlank() ? null : notes;
        for (Models.ModifierGroup g : visibleGroups()) for (Models.ModifierOption o : Models.nz(g.options)) {
            if (selected(g, o.optionId)) request.customizations.add(new Models.Customization(o.optionId, 1, "modifierOption"));
        }
        for (Models.BurgerTopping t : allToppings()) if (selected(t.toppingId)) {
            request.customizations.add(new Models.Customization(t.toppingId, 1,
                    t.defaultSelected || t.isProtein() ? "burgerTopping" : "burgerExtra"));
        }
        return request;
    }
    public String summary() {
        List<String> names = new ArrayList<>();
        for (Models.BurgerTopping t : allToppings()) {
            if (selected(t.toppingId) && t.isProtein()) names.add(t.name);
            else if (selected(t.toppingId) && !t.defaultSelected) names.add("+ " + t.name);
            else if (!selected(t.toppingId) && t.defaultSelected && !t.isProtein()) names.add("No " + t.name);
        }
        for (Models.ModifierGroup g : visibleGroups()) for (Models.ModifierOption o : Models.nz(g.options)) if (selected(g, o.optionId)) names.add(o.name);
        return String.join(" · ", names);
    }
}
