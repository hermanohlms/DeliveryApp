package com.example.deliveryapp.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Refreshes current catalog data before converting estimates into an order. */
public final class CheckoutCatalogValidator {
    private final CatalogClient repo;
    private final int branchId;
    private final List<Cart.Line> lines;
    private final List<Double> prices = new ArrayList<>();
    private List<Models.MenuItem> menu = List.of();
    private List<Models.Special> specials = List.of();
    private Repo.Result<Void> callback;

    public CheckoutCatalogValidator(CatalogClient repo, int branchId, List<Cart.Line> lines) {
        this.repo = repo;
        this.branchId = branchId;
        this.lines = lines;
    }

    public void run(Repo.Result<Void> result) {
        callback = result;
        boolean needsMenu = false, needsSpecials = false;
        for (Cart.Line line : lines) {
            needsMenu |= line.request != null && line.request.menuItemId != null;
            needsSpecials |= line.specialRequest != null;
        }
        final boolean refreshSpecials = needsSpecials;
        if (needsMenu) {
            repo.freshMenu(branchId, new Repo.Result<List<Models.MenuItem>>() {
                @Override public void onSuccess(List<Models.MenuItem> value) {
                    menu = Models.nz(value);
                    refreshSpecials(refreshSpecials);
                }
                @Override public void onError(String message, int code) { fail(message, code); }
            });
        } else refreshSpecials(refreshSpecials);
    }

    private void refreshSpecials(boolean needed) {
        if (!needed) { next(0); return; }
        repo.freshSpecials(branchId, new Repo.Result<List<Models.Special>>() {
            @Override public void onSuccess(List<Models.Special> value) {
                specials = Models.nz(value);
                next(0);
            }
            @Override public void onError(String message, int code) { fail(message, code); }
        });
    }

    private void next(int index) {
        if (index == lines.size()) {
            for (int i = 0; i < lines.size(); i++) lines.get(i).unitPrice = prices.get(i);
            Cart.get().pricesUpdated();
            callback.onSuccess(null);
            return;
        }
        Cart.Line line = lines.get(index);
        if (line.specialRequest != null) {
            Models.Special special = null;
            for (Models.Special value : specials) {
                if (value.id == line.specialRequest.specialId) special = value;
            }
            if (special == null || !special.availableToday) {
                fail(line.name + " is no longer available today.", 400);
                return;
            }
            Models.SpecialItemRequest request = line.specialRequest.copyWithQuantity(line.quantity);
            repo.specialQuote(branchId, request, new Repo.Result<Models.SpecialQuote>() {
                @Override public void onSuccess(Models.SpecialQuote quote) {
                    if (quote == null) { fail("The current special price is unavailable.", 0); return; }
                    prices.add(quote.lineTotal / line.quantity);
                    next(index + 1);
                }
                @Override public void onError(String message, int code) { fail(message, code); }
            });
        } else if (line.request != null && line.request.pizzaId != null) {
            Models.OrderItemRequest request = line.request;
            repo.freshPizzaDetail(branchId, request.pizzaId, request.sizeCm,
                    new Repo.Result<Models.PizzaDetail>() {
                        @Override public void onSuccess(Models.PizzaDetail detail) {
                            try {
                                Models.QuoteRequest quoteRequest = pizzaQuote(detail, request);
                                repo.quote(branchId, request.pizzaId, quoteRequest,
                                        new Repo.Result<Models.QuoteResponse>() {
                                            @Override public void onSuccess(Models.QuoteResponse quote) {
                                                if (quote == null) { fail("The current pizza price is unavailable.", 0); return; }
                                                prices.add(quote.total);
                                                next(index + 1);
                                            }
                                            @Override public void onError(String message, int code) { fail(message, code); }
                                        });
                            } catch (IllegalArgumentException ex) { fail(ex.getMessage(), 400); }
                        }
                        @Override public void onError(String message, int code) { fail(message, code); }
                    });
        } else if (line.request != null && line.request.menuItemId != null) {
            Models.MenuItem item = CatalogOptions.find(menu, line.request.menuItemId);
            if (item == null) { fail(line.name + " is no longer available.", 404); return; }
            try {
                prices.add(menuPrice(item, menu, line.request));
                next(index + 1);
            } catch (IllegalArgumentException ex) { fail(ex.getMessage(), 400); }
        } else fail("Your cart contains an invalid item.", 400);
    }

    private void fail(String message, int code) {
        callback.onError(message == null ? "Could not verify the current menu." : message, code);
    }

    public static Models.QuoteRequest pizzaQuote(Models.PizzaDetail detail, Models.OrderItemRequest request) {
        if (detail == null || request == null || request.sizeCm == null) {
            throw new IllegalArgumentException("This pizza is no longer available. Edit your cart.");
        }
        boolean sizeFound = false;
        for (Models.SizePrice size : Models.nz(detail.availableSizes)) sizeFound |= size.sizeCm == request.sizeCm;
        if (!sizeFound) throw new IllegalArgumentException("The selected pizza size is no longer available.");
        if (request.pizzaBaseOptionId != null) {
            boolean baseFound = false;
            for (Models.PizzaBaseOption base : Models.nz(detail.baseOptions)) {
                baseFound |= base.pizzaBaseOptionId == request.pizzaBaseOptionId;
            }
            if (!baseFound) throw new IllegalArgumentException("The selected pizza base is no longer available.");
        }
        Set<Integer> allowed = new HashSet<>(), defaults = new HashSet<>(), selected = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Map<Integer, Boolean> removable = new HashMap<>();
        for (Models.ToppingItem topping : Models.nz(detail.allToppings)) allowed.add(topping.toppingId);
        for (Models.ToppingItem topping : Models.nz(detail.defaultToppings)) {
            defaults.add(topping.toppingId);
            selected.add(topping.toppingId);
            removable.put(topping.toppingId, Boolean.TRUE.equals(topping.removable));
        }
        for (Models.Customization customization : Models.nz(request.customizations)) {
            String kind = customization.type == null ? "extra" : customization.type.toLowerCase();
            if (customization.quantity != 1 || !seen.add(kind + ":" + customization.id)) {
                throw new IllegalArgumentException("A pizza topping quantity has changed. Edit your cart.");
            }
            if ("removedPizzaIngredient".equalsIgnoreCase(customization.type)) {
                if (!defaults.contains(customization.id) || !removable.get(customization.id)) {
                    throw new IllegalArgumentException("A pizza topping can no longer be removed. Edit your cart.");
                }
                selected.remove(customization.id);
            } else if (customization.type == null || customization.type.isEmpty()) {
                if (!allowed.contains(customization.id) || defaults.contains(customization.id)) {
                    throw new IllegalArgumentException("A pizza extra has changed. Edit your cart.");
                }
                selected.add(customization.id);
            } else throw new IllegalArgumentException("A pizza customization is no longer valid.");
        }
        Models.QuoteRequest quote = new Models.QuoteRequest();
        quote.sizeCm = request.sizeCm;
        quote.pizzaBaseOptionId = request.pizzaBaseOptionId;
        quote.selectedToppingIds = new ArrayList<>(selected);
        return quote;
    }

    public static double menuPrice(Models.MenuItem item, List<Models.MenuItem> catalog,
                                   Models.OrderItemRequest request) {
        List<Models.BurgerTopping> toppings = CatalogOptions.toppings(item, catalog);
        Map<Integer, Models.BurgerTopping> toppingById = new HashMap<>();
        for (Models.BurgerTopping topping : toppings) toppingById.put(topping.toppingId, topping);
        Map<Integer, Models.ModifierOption> modifierById = new HashMap<>();
        Map<Integer, Set<Integer>> selectedGroups = new HashMap<>();
        for (Models.ModifierGroup group : Models.nz(item.modifierGroups)) {
            for (Models.ModifierOption option : Models.nz(group.options)) {
                modifierById.put(option.optionId, option);
            }
        }
        Set<Integer> selectedDefaults = new HashSet<>(), selectedProteins = new HashSet<>();
        Set<String> seen = new HashSet<>();
        double total = item.priceOrZero();
        for (Models.Customization custom : Models.nz(request.customizations)) {
            if (custom.quantity != 1 || !seen.add(custom.type + ":" + custom.id)) {
                throw new IllegalArgumentException("A menu option quantity has changed. Edit your cart.");
            }
            if ("modifierOption".equalsIgnoreCase(custom.type)) {
                Models.ModifierOption option = modifierById.get(custom.id);
                if (option == null) throw new IllegalArgumentException("A menu option is no longer available.");
                total += option.price == null ? 0 : option.price;
                for (Models.ModifierGroup group : Models.nz(item.modifierGroups)) {
                    for (Models.ModifierOption candidate : Models.nz(group.options)) {
                        if (candidate.optionId == custom.id) selectedGroups.computeIfAbsent(group.groupId,
                                key -> new HashSet<>()).add(custom.id);
                    }
                }
            } else if ("burgerTopping".equalsIgnoreCase(custom.type)
                    || "burgerExtra".equalsIgnoreCase(custom.type)) {
                Models.BurgerTopping topping = toppingById.get(custom.id);
                if (topping == null) throw new IllegalArgumentException("A burger topping is no longer available.");
                if ("burgerExtra".equalsIgnoreCase(custom.type) && topping.defaultSelected) {
                    throw new IllegalArgumentException("A burger topping has changed. Edit your cart.");
                }
                if (topping.defaultSelected) selectedDefaults.add(topping.toppingId);
                if (topping.isProtein()) selectedProteins.add(topping.toppingId);
                if (!topping.defaultSelected) total += topping.priceOrZero();
            } else throw new IllegalArgumentException("A menu customization is no longer valid.");
        }
        int proteinCount = 0;
        for (Models.BurgerTopping topping : toppings) {
            if (topping.isProtein()) proteinCount++;
            if (topping.defaultSelected && !topping.isProtein() && !topping.isRemovable()
                    && !selectedDefaults.contains(topping.toppingId)) {
                throw new IllegalArgumentException("A required burger topping is missing. Edit your cart.");
            }
        }
        if (proteinCount > 0 && selectedProteins.size() != 1) {
            throw new IllegalArgumentException("Choose one burger protein.");
        }
        Set<Integer> allOptionIds = new HashSet<>();
        for (Set<Integer> values : selectedGroups.values()) allOptionIds.addAll(values);
        List<Models.ModifierGroup> visible = CatalogOptions.visibleGroups(item, allOptionIds);
        Set<Integer> visibleIds = new HashSet<>();
        for (Models.ModifierGroup group : visible) {
            visibleIds.add(group.groupId);
            int selected = selectedGroups.getOrDefault(group.groupId, Set.of()).size();
            if (selected < group.minRequired() || group.maxAllowed() > 0 && selected > group.maxAllowed()) {
                throw new IllegalArgumentException("The choices for " + group.name + " need updating.");
            }
        }
        for (Integer groupId : selectedGroups.keySet()) {
            if (!visibleIds.contains(groupId)) throw new IllegalArgumentException("A menu option is no longer applicable.");
        }
        return total;
    }
}
