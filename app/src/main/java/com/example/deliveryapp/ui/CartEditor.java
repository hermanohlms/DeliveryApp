package com.example.deliveryapp.ui;

import android.app.Activity;
import android.content.Intent;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Models;

/** Activity contract shared by menu, pizza and special editors. */
public final class CartEditor {
    public static final String EXTRA_LINE_ID = "cartLineId";
    public static final String EXTRA_INITIAL_REQUEST = "initialRequest";
    public static final String EXTRA_USE_DEFAULTS = "useDefaults";
    private CartEditor() {}

    public static void open(Activity activity, Cart.Line line) {
        Class<?> target = line.specialRequest != null ? SpecialDetailActivity.class
                : line.request.pizzaId != null ? PizzaDetailActivity.class : ItemDetailActivity.class;
        Intent intent = new Intent(activity, target).putExtra(EXTRA_LINE_ID, line.id);
        if (line.specialRequest != null) intent.putExtra(SpecialDetailActivity.EXTRA_ID, line.specialRequest.specialId);
        else if (line.request.pizzaId != null) intent.putExtra(PizzaDetailActivity.EXTRA_ID, line.request.pizzaId);
        else intent.putExtra(ItemDetailActivity.EXTRA_ID, line.request.menuItemId.intValue());
        activity.startActivity(intent);
    }
    public static boolean save(Activity activity, String id, Cart.Line line) {
        if (id == null) Cart.get().add(line);
        else if (!Cart.get().replace(id, line)) {
            Ui.toast(activity, "This item is no longer in your cart. Return to the menu to add it again.");
            return false;
        }
        return true;
    }
    public static String notes(Cart.Line line) {
        if (line.request != null) return line.request.notes;
        return null;
    }
}
