package com.example.deliveryapp.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The required selections and add-ons for one branch special. */
public final class SpecialCustomization {
    private final Models.Special special;
    private final Map<String, Models.SpecialSelection> selections = new HashMap<>();
    private final Map<Long, Models.SpecialAddonSelection> addons = new HashMap<>();
    private int quantity = 1;

    public SpecialCustomization(Models.Special special) { this.special = special; }

    public static String key(long componentId, int index) { return componentId + ":" + index; }

    public int quantity() { return quantity; }
    public void setQuantity(int value) { quantity = Math.max(1, Math.min(50, value)); }
    public Models.SpecialSelection selection(long componentId, int index) {
        return selections.get(key(componentId, index));
    }
    public Models.SpecialAddonSelection addon(long id) { return addons.get(id); }

    public void select(Models.SpecialComponent component, int index, Models.OrderItemRequest item) {
        if (item == null) selections.remove(key(component.id, index));
        else selections.put(key(component.id, index), new Models.SpecialSelection(component.id, index, item));
    }

    public void setAddon(Models.SpecialAddon addon, int quantity, Models.OrderItemRequest item) {
        if (quantity <= 0 || item == null) addons.remove(addon.id);
        else addons.put(addon.id, new Models.SpecialAddonSelection(addon.id,
                Math.min(quantity, Math.max(1, addon.maxQuantity)), item));
    }

    public String problem() {
        if (!special.availableToday) return "This special is not available today.";
        for (Models.SpecialComponent component : Models.nz(special.components)) {
            if ("INCLUDED".equalsIgnoreCase(component.selectionMode) && !component.allowCustomization) continue;
            Set<Integer> chosen = new HashSet<>();
            for (int index = 1; index <= component.quantity; index++) {
                Models.SpecialSelection choice = selection(component.id, index);
                if (choice == null || choice.item == null) return "Choose " + component.label + ".";
                int productId = productId(choice.item);
                if (!allowed(component, productId)) return "Choose an available " + component.label + ".";
                if (!component.allowRepeats && !chosen.add(productId)) {
                    return component.label + " does not allow repeats.";
                }
            }
        }
        return null;
    }

    private boolean allowed(Models.SpecialComponent component, int productId) {
        for (Models.SpecialOption option : Models.nz(component.options)) {
            if (option.id == productId) return true;
        }
        return false;
    }

    private int productId(Models.OrderItemRequest item) {
        return item.pizzaId != null ? item.pizzaId : item.menuItemId == null ? -1 : item.menuItemId;
    }

    public Models.SpecialItemRequest request() {
        Models.SpecialItemRequest result = new Models.SpecialItemRequest();
        result.specialId = special.id;
        result.quantity = quantity;
        for (Models.SpecialComponent component : Models.nz(special.components)) {
            for (int index = 1; index <= component.quantity; index++) {
                Models.SpecialSelection choice = selection(component.id, index);
                if (choice != null) result.selections.add(choice);
            }
        }
        for (Models.SpecialAddon addon : Models.nz(special.addons)) {
            Models.SpecialAddonSelection choice = addons.get(addon.id);
            if (choice != null) result.addons.add(choice);
        }
        return result;
    }

    public double estimate() {
        double unit = special.price;
        for (Models.SpecialAddon addon : Models.nz(special.addons)) {
            Models.SpecialAddonSelection choice = addons.get(addon.id);
            if (choice != null) unit += addon.price * choice.quantity;
        }
        return unit * quantity;
    }
}
