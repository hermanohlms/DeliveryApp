package com.example.deliveryapp.data;

import org.junit.After;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class CartEditingTest {
    @After public void clearCart() { Cart.get().clear(); }
    private Models.BurgerTopping topping(int id, String name, boolean included, boolean protein, boolean removable, double price) {
        Models.BurgerTopping t = new Models.BurgerTopping(); t.toppingId = id; t.name = name;
        t.defaultSelected = included; t.componentType = protein ? "protein" : "topping";
        t.removable = removable; t.price = price; return t;
    }
    private Models.MenuItem burger() {
        Models.MenuItem b = new Models.MenuItem(); b.menuItemId = 1; b.name = "Cheese Burger"; b.categoryName = "Burgers"; b.price = 80.0;
        b.burgerToppings = List.of(topping(1,"Beef",true,true,false,0), topping(2,"Cheese",true,false,true,14),
                topping(3,"Lettuce",true,false,false,5), topping(4,"Bacon",false,false,true,19));
        return b;
    }
    @Test public void defaultsAndExtrasAreSeparateAndRemovingDefaultsDoesNotDiscount() {
        Models.MenuItem b = burger(); MenuCustomization state = new MenuCustomization(b, List.of(b));
        assertEquals(List.of(b.burgerToppings.get(0)), state.proteins());
        assertEquals(List.of(b.burgerToppings.get(1), b.burgerToppings.get(2)), state.defaults());
        assertEquals(List.of(b.burgerToppings.get(3)), state.extras());
        assertEquals(80, state.unitPrice(), .001);
        state.toggle(b.burgerToppings.get(1)); state.toggle(b.burgerToppings.get(2));
        assertFalse(state.selected(2)); assertTrue(state.selected(3)); assertEquals(80, state.unitPrice(), .001);
        state.toggle(b.burgerToppings.get(3));
        assertEquals(99, state.unitPrice(), .001);
        assertEquals(99, CheckoutCatalogValidator.menuPrice(b,List.of(b),state.request(null)), .001);
    }
    @Test public void duplicatedApiRowsKeepIncludedToppingsOutOfExtrasAndTotals() {
        Models.MenuItem b=burger(); java.util.List<Models.BurgerTopping> rows=new java.util.ArrayList<>(b.burgerToppings);
        rows.add(topping(2,"Cheese",false,false,true,14)); rows.add(topping(3,"Lettuce",false,false,true,5));
        b.burgerToppings=rows; MenuCustomization state=new MenuCustomization(b,List.of(b));
        assertEquals(4,state.allToppings().size());assertEquals(2,state.defaults().size());assertEquals(1,state.extras().size());
        assertEquals(80,state.unitPrice(),.001);assertEquals(80,CheckoutCatalogValidator.menuPrice(b,List.of(b),state.request(null)),.001);
        assertEquals(3,state.request(null).customizations.size());
    }
    @Test public void restoreKeepsRemovedDefaultsExtrasModifiersAndNotes() {
        Models.MenuItem b = burger();
        Models.ModifierOption o = new Models.ModifierOption(); o.optionId=20; o.name="Drink"; o.price=7.0;
        Models.ModifierGroup g = new Models.ModifierGroup(); g.groupId=10; g.options=List.of(o); g.maxSelect=1; g.required=true; g.name="Drink";
        b.modifierGroups=List.of(g);
        MenuCustomization state = new MenuCustomization(b,List.of(b));
        state.toggle(b.burgerToppings.get(1)); state.toggle(b.burgerToppings.get(3)); state.toggle(g,20);
        Models.OrderItemRequest request = state.request("No salt");
        MenuCustomization restored = new MenuCustomization(b,List.of(b));
        assertFalse(restored.restore(request)); assertFalse(restored.selected(2)); assertTrue(restored.selected(4));
        assertTrue(restored.selected(g,20)); assertEquals(106,restored.unitPrice(),.001); assertNull(restored.problem());
        assertEquals("No salt", restored.request(request.notes).notes);
        restored.toggle(b.burgerToppings.get(3));
        assertTrue(request.customizations.stream().anyMatch(c -> c.id==4));
    }
    @Test public void staleOptionsAreReportedAndRequiredChoicesStillBlockSaving() {
        Models.MenuItem b=burger(); MenuCustomization state=new MenuCustomization(b,List.of(b));
        Models.OrderItemRequest request=state.request(null); request.customizations.removeIf(c -> c.id==1);
        request.customizations.add(new Models.Customization(999,1,"burgerExtra"));
        assertTrue(state.restore(request)); assertFalse(state.selected(999)); assertEquals("Choose your protein",state.problem());
    }
    @Test public void aPreviouslyIncludedToppingIsNotSilentlyChargedAsAnExtra() {
        Models.MenuItem b=burger(); MenuCustomization old=new MenuCustomization(b,List.of(b));
        Models.OrderItemRequest request=old.request(null); b.burgerToppings.get(1).defaultSelected=false;
        MenuCustomization current=new MenuCustomization(b,List.of(b)); assertTrue(current.restore(request));
        assertFalse(current.selected(2)); assertEquals(80,current.unitPrice(),.001);
    }
    @Test public void comboCanReuseIndividualBurgerToppings() {
        Models.MenuItem b=burger(); Models.MenuItem combo=new Models.MenuItem(); combo.menuItemId=2;
        combo.name="Cheese Burger Combo"; combo.categoryName="Burgers"; combo.price=100.0;
        MenuCustomization state=new MenuCustomization(combo,List.of(combo,b));
        assertEquals(2,state.defaults().size()); assertEquals(1,state.extras().size()); assertEquals(100,state.unitPrice(),.001);
    }
    @Test public void replacementKeepsIdentityPositionAndRecalculatesTotalsWithoutDuplicates() {
        Cart cart=Cart.get(); cart.clear(); Models.OrderItemRequest r=new Models.OrderItemRequest(); r.menuItemId=1;
        Cart.Line first=new Cart.Line("Burger","",80,1,r); Cart.Line second=new Cart.Line("Drink","",20,1,r);
        cart.add(first); cart.add(second);
        Cart.Line edited=new Cart.Line("Burger combo","No cheese",99,2,r.copyWithQuantity(1));
        assertTrue(cart.replace(first.id,edited)); assertEquals(2,cart.lines().size());
        assertEquals(first.id,cart.lines().get(0).id); assertSame(second,cart.lines().get(1));
        assertEquals("Burger combo",cart.find(first.id).name); assertEquals(218,cart.total(),.001);
        assertEquals(3,cart.count());
    }
    @Test public void staleEditCannotResurrectARemovedCartLine() {
        Cart cart=Cart.get(); cart.clear(); Models.OrderItemRequest r=new Models.OrderItemRequest();
        Cart.Line line=new Cart.Line("Burger","",80,1,r); cart.add(line); cart.remove(line.id);
        assertFalse(cart.replace(line.id,line)); assertTrue(cart.isEmpty());
    }
    @Test public void requestSnapshotsAreIndependentForCancelAndRotation() {
        Models.OrderItemRequest original=new Models.OrderItemRequest(); original.menuItemId=1; original.notes="No salt";
        original.customizations.add(new Models.Customization(4,1,"burgerExtra"));
        Models.OrderItemRequest draft=original.copyWithQuantity(3);
        draft.customizations.get(0).id=99; draft.customizations.clear(); draft.notes="More salt";
        assertEquals(4,original.customizations.get(0).id); assertEquals("No salt",original.notes); assertEquals(1,original.quantity);
    }
    private Models.PizzaDetail pizza() {
        Models.PizzaDetail d=new Models.PizzaDetail(); d.pizzaId=10; d.basePriceForSelectedSize=100;
        Models.SizePrice size=new Models.SizePrice();size.sizeCm=30;size.basePrice=100; d.availableSizes=List.of(size);
        Models.ToppingItem cheese=new Models.ToppingItem(); cheese.toppingId=1; cheese.name="Cheese"; cheese.removable=false;
        Models.ToppingItem basil=new Models.ToppingItem(); basil.toppingId=2;basil.name="Basil";basil.removable=true;
        Models.ToppingItem extra=new Models.ToppingItem();extra.toppingId=3;extra.name="Mushroom";
        d.defaultToppings=List.of(cheese,basil); d.allToppings=List.of(cheese,basil,extra);
        Models.PizzaBaseOption base=new Models.PizzaBaseOption();base.pizzaBaseOptionId=20;base.extraPrice=36;d.baseOptions=List.of(base);
        Models.ToppingPrice price=new Models.ToppingPrice();price.toppingId=3;price.extraPrice=12;d.extraPrices=List.of(price);return d;
    }
    @Test public void pizzaEditRestoresSizeBaseRemovedDefaultsExtrasAndNotes() {
        Models.PizzaDetail d=pizza(); PizzaCustomization old=new PizzaCustomization();old.apply(d,30);
        old.toggle(d.defaultToppings.get(1));old.toggle(d.allToppings.get(2));old.setBaseOptionId(20);
        Models.OrderItemRequest request=old.orderItem(10,"Extra crispy");
        PizzaCustomization state=new PizzaCustomization();state.apply(d,request.sizeCm);assertFalse(state.restore(request));
        assertEquals(30,(int)state.sizeCm());assertEquals(20,(int)state.baseOptionId());assertFalse(state.isSelected(2));assertTrue(state.isSelected(3));
        assertEquals(148,state.estimate(),.001);assertEquals("Extra crispy",state.orderItem(10,request.notes).notes);
    }
    @Test public void pizzaRestoreReportsStaleChoicesAndKeepsLockedDefaults() {
        Models.PizzaDetail d=pizza();PizzaCustomization state=new PizzaCustomization();state.apply(d,30);
        Models.OrderItemRequest request=new Models.OrderItemRequest();request.pizzaBaseOptionId=999;
        request.customizations.add(new Models.Customization(1,1,"removedPizzaIngredient"));
        request.customizations.add(new Models.Customization(999,1,null));
        assertTrue(state.restore(request));assertTrue(state.isSelected(1));assertNull(state.baseOptionId());assertFalse(state.isSelected(999));
    }
    private Models.Special special() {
        Models.Special s=new Models.Special();s.id=50;s.availableToday=true;s.price=150;
        Models.SpecialComponent c=new Models.SpecialComponent();c.id=60;c.quantity=1;c.allowCustomization=true;c.label="Burger";
        Models.SpecialOption o=new Models.SpecialOption();o.id=1;o.name="Burger";c.options=List.of(o);s.components=List.of(c);
        Models.SpecialAddon a=new Models.SpecialAddon();a.id=70;a.productId=2;a.active=true;a.maxQuantity=3;a.price=10;s.addons=List.of(a);return s;
    }
    @Test public void specialEditRestoresNestedRequestsNotesAddonsAndQuantitiesIndependently() {
        Models.Special s=special();SpecialCustomization old=new SpecialCustomization(s);
        Models.OrderItemRequest item=new Models.OrderItemRequest();item.menuItemId=1;item.notes="No salt";
        item.customizations.add(new Models.Customization(4,1,"burgerExtra"));old.select(s.components.get(0),1,item);
        Models.OrderItemRequest addon=new Models.OrderItemRequest();addon.menuItemId=2;old.setAddon(s.addons.get(0),2,addon);old.setQuantity(3);
        Models.SpecialItemRequest request=old.request().copyWithQuantity(3);
        SpecialCustomization state=new SpecialCustomization(s);assertFalse(state.restore(request));assertNull(state.problem());
        assertEquals(3,state.quantity());assertEquals(2,state.addon(70).quantity);assertEquals(510,state.estimate(),.001);
        assertEquals("No salt",state.selection(60,1).item.notes);
        state.selection(60,1).item.customizations.clear();
        assertEquals(1,request.selections.get(0).item.customizations.size());
    }
    @Test public void missingSpecialOptionRequiresAReplacementSelection() {
        Models.Special s=special();SpecialCustomization old=new SpecialCustomization(s);
        Models.OrderItemRequest item=new Models.OrderItemRequest();item.menuItemId=999;
        Models.SpecialItemRequest request=new Models.SpecialItemRequest();request.selections.add(new Models.SpecialSelection(60,1,item));
        assertTrue(old.restore(request));assertNotNull(old.problem());assertTrue(old.request().selections.isEmpty());
    }
}
