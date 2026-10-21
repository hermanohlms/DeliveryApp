package com.example.deliveryapp.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Catalog presentation rules shared by item customization and checkout. */
public final class CatalogOptions {
    private CatalogOptions() {}

    public static Models.MenuItem find(List<Models.MenuItem> catalog, int id) {
        for (Models.MenuItem item : Models.nz(catalog)) if (item.menuItemId == id) return item;
        return null;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isCombo(Models.MenuItem item) {
        return lower(item.name).matches(".*\\bcombo\\b.*") || lower(item.categoryName).contains("combo");
    }

    private static String burgerKey(Models.MenuItem item) {
        String name = lower(item.name);
        if (!(name.contains("burger") || lower(item.categoryName).contains("burger"))) return "";
        return name.replaceFirst("\\s+combo$", "").replaceAll("\\s+", " ");
    }

    public static List<Models.MenuItem> variants(Models.MenuItem item, List<Models.MenuItem> catalog) {
        List<Models.MenuItem> result = new ArrayList<>();
        String burger = burgerKey(item);
        if (!burger.isEmpty()) {
            for (Models.MenuItem candidate : Models.nz(catalog)) {
                if (burger.equals(burgerKey(candidate))) result.add(candidate);
            }
            result.sort(Comparator.comparing(CatalogOptions::isCombo)
                    .thenComparingDouble(Models.MenuItem::priceOrZero));
            return result;
        }
        String pasta = pastaKey(item);
        if (!pasta.isEmpty()) {
            for (Models.MenuItem candidate : Models.nz(catalog)) {
                if (pasta.equals(pastaKey(candidate))) result.add(candidate);
            }
            result.sort(Comparator.comparingInt(candidate -> lower(candidate.name).endsWith(" medium") ? 0 : 1));
        }
        return result;
    }

    private static String pastaKey(Models.MenuItem item) {
        if (!lower(item.categoryName).contains("pasta")) return "";
        String name = lower(item.name);
        if (name.endsWith(" medium")) return name.substring(0, name.length() - 7);
        if (name.endsWith(" large")) return name.substring(0, name.length() - 6);
        return "";
    }

    public static String variantLabel(Models.MenuItem item) {
        if (!burgerKey(item).isEmpty()) return isCombo(item) ? "Burger combo" : "Individual burger";
        if (lower(item.name).endsWith(" medium")) return "Medium";
        if (lower(item.name).endsWith(" large")) return "Large";
        return item.name;
    }

    public static List<Models.BurgerTopping> toppings(Models.MenuItem item, List<Models.MenuItem> catalog) {
        if (!Models.nz(item.burgerToppings).isEmpty()) return uniqueToppings(item.burgerToppings);
        for (Models.MenuItem variant : variants(item, catalog)) {
            if (!isCombo(variant) && !Models.nz(variant.burgerToppings).isEmpty()) return uniqueToppings(variant.burgerToppings);
        }
        return List.of();
    }

    /** The API may list an included component again among extras. Included always wins. */
    private static List<Models.BurgerTopping> uniqueToppings(List<Models.BurgerTopping> values) {
        java.util.Map<Integer, Models.BurgerTopping> unique = new java.util.LinkedHashMap<>();
        for (Models.BurgerTopping topping : Models.nz(values)) {
            Models.BurgerTopping previous = unique.get(topping.toppingId);
            if (previous == null || topping.defaultSelected && !previous.defaultSelected) unique.put(topping.toppingId, topping);
        }
        return new ArrayList<>(unique.values());
    }

    public static List<Models.ModifierGroup> visibleGroups(Models.MenuItem item, Set<Integer> selectedOptionIds) {
        List<Models.ModifierGroup> result = new ArrayList<>();
        boolean rib = lower(item.name).contains("rib") || lower(item.categoryName).contains("rib");
        boolean chipSide = false;
        for (Models.ModifierGroup group : Models.nz(item.modifierGroups)) {
            if (!(group.groupId == 2 || lower(group.name).contains("choose your side"))) continue;
            for (Models.ModifierOption option : Models.nz(group.options)) {
                if (selectedOptionIds.contains(option.optionId)
                        && (lower(option.name).contains("chip") || option.menuItemId != null
                        && Set.of(601, 602, 603).contains(option.menuItemId))) chipSide = true;
            }
        }
        for (Models.ModifierGroup group : Models.nz(item.modifierGroups)) {
            boolean side = group.groupId == 2 || lower(group.name).contains("choose your side");
            boolean chipExtras = group.groupId == 4 || lower(group.name).contains("chip extras");
            if (isCombo(item) && side) continue;
            if (chipExtras && !(rib && chipSide)) continue;
            result.add(group);
        }
        return result;
    }
}
