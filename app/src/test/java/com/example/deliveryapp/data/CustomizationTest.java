package com.example.deliveryapp.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class CustomizationTest {
    private Models.ToppingItem topping(int id, String name, Boolean removable) {
        Models.ToppingItem item = new Models.ToppingItem();
        item.toppingId = id;
        item.name = name;
        item.removable = removable;
        return item;
    }

    private Models.PizzaDetail pizza(int size, Models.ToppingItem... defaults) {
        Models.PizzaDetail detail = new Models.PizzaDetail();
        detail.pizzaId = 12;
        detail.name = "Test pizza";
        detail.defaultToppings = Arrays.asList(defaults);
        detail.allToppings = Arrays.asList(topping(1, "Cheese", false),
                topping(2, "Basil", true), topping(3, "Mushrooms", null));
        Models.SizePrice option = new Models.SizePrice();
        option.sizeCm = size;
        option.basePrice = 100;
        detail.availableSizes = List.of(option);
        detail.basePriceForSelectedSize = 100;
        Models.ToppingPrice extra = new Models.ToppingPrice();
        extra.toppingId = 3;
        extra.extraPrice = 12;
        detail.extraPrices = List.of(extra);
        detail.baseOptions = List.of();
        return detail;
    }

    @Test public void pizzaDefaultsStartCheckedAndOnlyRemovableDefaultsCanBeCleared() {
        PizzaCustomization state = new PizzaCustomization();
        Models.PizzaDetail detail = pizza(30, topping(1, "Cheese", false), topping(2, "Basil", true));
        state.apply(detail, 30);
        assertFalse(state.toggle(detail.defaultToppings.get(0)));
        assertTrue(state.toggle(detail.defaultToppings.get(1)));
        assertTrue(state.toggle(detail.allToppings.get(2)));

        assertEquals(Set.of(1, 3), Set.copyOf(state.quoteRequest().selectedToppingIds));
        assertEquals(112.0, state.estimate(), 0.001);
        Models.OrderItemRequest order = state.orderItem(12, null);
        assertEquals(2, order.customizations.size());
        assertTrue(order.customizations.stream().anyMatch(c -> c.id == 2
                && "removedPizzaIngredient".equals(c.type)));
        assertTrue(order.customizations.stream().anyMatch(c -> c.id == 3 && c.type == null));
    }

    @Test public void changingSizePreservesValidChoicesAndAddsNewDefaults() {
        PizzaCustomization state = new PizzaCustomization();
        Models.PizzaDetail first = pizza(30, topping(1, "Cheese", false), topping(2, "Basil", true));
        state.apply(first, 30);
        state.toggle(first.defaultToppings.get(1));
        state.toggle(first.allToppings.get(2));
        Models.PizzaDetail next = pizza(40, topping(1, "Cheese", false), topping(2, "Basil", true),
                topping(3, "Mushrooms", true));
        state.apply(next, 40);
        assertEquals(40, (int) state.sizeCm());
        assertTrue(state.isSelected(1));
        assertFalse(state.isSelected(2));
        assertTrue(state.isSelected(3));
        assertEquals(100.0, state.estimate(), 0.001);
        assertFalse(state.toggle(next.defaultToppings.get(0)));
        Models.OrderItemRequest order = state.orderItem(next.pizzaId, null);
        assertEquals(40, (int) order.sizeCm);
        assertTrue(order.customizations.stream().anyMatch(c -> c.id == 2
                && "removedPizzaIngredient".equals(c.type)));
        assertEquals(Set.of(1, 3),
                Set.copyOf(CheckoutCatalogValidator.pizzaQuote(next, order).selectedToppingIds));
    }

    @Test public void checkoutRejectsUnremovableDefaultAndBuildsQuoteFromSavedRequest() {
        Models.PizzaDetail detail = pizza(30, topping(1, "Cheese", false), topping(2, "Basil", true));
        Models.OrderItemRequest request = new Models.OrderItemRequest();
        request.pizzaId = 12;
        request.sizeCm = 30;
        request.customizations.add(new Models.Customization(2, 1, "removedPizzaIngredient"));
        request.customizations.add(new Models.Customization(3, 1, null));
        assertEquals(Set.of(1, 3), Set.copyOf(CheckoutCatalogValidator.pizzaQuote(detail, request).selectedToppingIds));
        request.customizations.add(new Models.Customization(1, 1, "removedPizzaIngredient"));
        assertThrows(IllegalArgumentException.class,
                () -> CheckoutCatalogValidator.pizzaQuote(detail, request));
    }

    private Models.MenuItem menu(int id, String name, String category) {
        Models.MenuItem item = new Models.MenuItem();
        item.menuItemId = id;
        item.name = name;
        item.categoryName = category;
        item.price = 90.0;
        return item;
    }

    @Test public void variantFamiliesAndConditionalRibOptionsMatchWebsiteRules() {
        Models.MenuItem burger = menu(1, "Cheese Burger", "Burgers");
        Models.MenuItem combo = menu(2, "Cheese Burger Combo", "Burgers");
        assertEquals(List.of(burger, combo), CatalogOptions.variants(combo, List.of(combo, burger)));
        assertEquals("Burger combo", CatalogOptions.variantLabel(combo));
        Models.MenuItem medium = menu(3, "Bolognaise Medium", "Pastas");
        Models.MenuItem large = menu(4, "Bolognaise Large", "Pastas");
        assertEquals(List.of(medium, large), CatalogOptions.variants(large, List.of(large, medium)));

        Models.MenuItem rib = menu(5, "Ribs", "Ribs");
        Models.ModifierGroup side = new Models.ModifierGroup();
        side.groupId = 2;
        side.name = "Choose your side";
        Models.ModifierOption chips = new Models.ModifierOption();
        chips.optionId = 10;
        chips.name = "Chips";
        side.options = List.of(chips);
        Models.ModifierGroup extras = new Models.ModifierGroup();
        extras.groupId = 4;
        extras.name = "Chip extras";
        rib.modifierGroups = List.of(side, extras);
        assertEquals(List.of(side), CatalogOptions.visibleGroups(rib, Set.of()));
        assertEquals(List.of(side, extras), CatalogOptions.visibleGroups(rib, Set.of(10)));
    }

    @Test public void menuCheckoutChecksRequiredOptionsAndCurrentPrices() {
        Models.MenuItem burger = menu(1, "Cheese Burger", "Burgers");
        Models.BurgerTopping protein = new Models.BurgerTopping();
        protein.toppingId = 4; protein.name = "Beef"; protein.componentType = "protein"; protein.price = 5.0;
        Models.BurgerTopping cheese = new Models.BurgerTopping();
        cheese.toppingId = 5; cheese.name = "Cheese"; cheese.defaultSelected = true; cheese.removable = false;
        burger.burgerToppings = List.of(protein, cheese);
        Models.OrderItemRequest request = new Models.OrderItemRequest();
        request.menuItemId = 1;
        request.customizations.add(new Models.Customization(4, 1, "burgerTopping"));
        request.customizations.add(new Models.Customization(5, 1, "burgerTopping"));
        assertEquals(95.0, CheckoutCatalogValidator.menuPrice(burger, List.of(burger), request), 0.001);
        request.customizations.remove(1);
        assertThrows(IllegalArgumentException.class,
                () -> CheckoutCatalogValidator.menuPrice(burger, List.of(burger), request));
    }

    @Test public void specialRequiresEverySlotAndKeepsNestedSelections() {
        Models.Special special = new Models.Special();
        special.id = 20;
        special.availableToday = true;
        special.price = 150;
        Models.SpecialComponent component = new Models.SpecialComponent();
        component.id = 21;
        component.label = "Pizza";
        component.quantity = 2;
        component.allowRepeats = false;
        Models.SpecialOption a = new Models.SpecialOption(); a.id = 1; a.name = "Margherita";
        Models.SpecialOption b = new Models.SpecialOption(); b.id = 2; b.name = "Regina";
        component.options = List.of(a, b);
        special.components = List.of(component);
        SpecialCustomization state = new SpecialCustomization(special);
        assertNotNull(state.problem());
        Models.OrderItemRequest first = new Models.OrderItemRequest(); first.pizzaId = 1; first.sizeCm = 30;
        Models.OrderItemRequest second = new Models.OrderItemRequest(); second.pizzaId = 2; second.sizeCm = 30;
        state.select(component, 1, first);
        state.select(component, 2, first);
        assertNotNull(state.problem());
        state.select(component, 2, second);
        assertNull(state.problem());
        assertEquals(2, state.request().selections.size());
        assertEquals(20, state.request().specialId);
    }

    @Test public void checkoutRefreshesCatalogAndAutomaticallyUpdatesEstimate() {
        Models.MenuItem current = menu(30, "Pasta", "Pastas");
        current.price = 125.0;
        Models.OrderItemRequest request = new Models.OrderItemRequest();
        request.menuItemId = 30;
        Cart.Line line = new Cart.Line("Pasta", "", 95.0, 2, request);
        AtomicBoolean fetched = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        CatalogClient client = new FakeCatalog() {
            @Override public void freshMenu(int branchId, Repo.Result<List<Models.MenuItem>> cb) {
                fetched.set(true);
                cb.onSuccess(List.of(current));
            }
        };
        new CheckoutCatalogValidator(client, 1, List.of(line)).run(new Repo.Result<Void>() {
            @Override public void onSuccess(Void ignored) { completed.set(true); }
            @Override public void onError(String message, int code) { fail(message); }
        });
        assertTrue(fetched.get());
        assertTrue(completed.get());
        assertEquals(125.0, line.unitPrice, 0.001);
    }

    @Test public void unavailablePizzaChoiceKeepsOldCartEstimate() {
        Models.OrderItemRequest request = new Models.OrderItemRequest();
        request.pizzaId = 12;
        request.sizeCm = 30;
        request.customizations.add(new Models.Customization(999, 1, null));
        Cart.Line line = new Cart.Line("Pizza", "extra", 130.0, 1, request);
        AtomicBoolean rejected = new AtomicBoolean();
        CatalogClient client = new FakeCatalog() {
            @Override public void freshPizzaDetail(int branchId, int pizzaId, Integer size,
                                                   Repo.Result<Models.PizzaDetail> cb) {
                cb.onSuccess(pizza(30, topping(1, "Cheese", false)));
            }
        };
        new CheckoutCatalogValidator(client, 1, List.of(line)).run(new Repo.Result<Void>() {
            @Override public void onSuccess(Void ignored) { fail("Unavailable topping was accepted"); }
            @Override public void onError(String message, int code) { rejected.set(true); }
        });
        assertTrue(rejected.get());
        assertEquals(130.0, line.unitPrice, 0.001);
    }

    private abstract static class FakeCatalog implements CatalogClient {
        @Override public void freshMenu(int branchId, Repo.Result<List<Models.MenuItem>> cb) {
            cb.onSuccess(List.of());
        }
        @Override public void freshSpecials(int branchId, Repo.Result<List<Models.Special>> cb) {
            cb.onSuccess(List.of());
        }
        @Override public void freshPizzaDetail(int branchId, int pizzaId, Integer size,
                                               Repo.Result<Models.PizzaDetail> cb) {
            cb.onError("Missing pizza", 404);
        }
        @Override public void quote(int branchId, int pizzaId, Models.QuoteRequest request,
                                    Repo.Result<Models.QuoteResponse> cb) {
            cb.onError("Missing quote", 404);
        }
        @Override public void specialQuote(int branchId, Models.SpecialItemRequest request,
                                           Repo.Result<Models.SpecialQuote> cb) {
            cb.onError("Missing quote", 404);
        }
    }
}
