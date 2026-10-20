package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.example.deliveryapp.data.SpecialCustomization;
import com.example.deliveryapp.util.Money;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpecialDetailActivity extends BaseActivity {
    public static final String EXTRA_ID = "specialId";
    public static final String EXTRA_CONFIGURE_FOR_SPECIAL = "configureForSpecial";
    public static final String EXTRA_LOCKED_SIZE_CM = "lockedSizeCm";
    public static final String EXTRA_CONFIGURED_REQUEST = "configuredRequest";
    public static final String EXTRA_CONFIGURED_DETAIL = "configuredDetail";
    public static final String EXTRA_CONFIGURED_PRICE = "configuredPrice";

    private Models.Special special;
    private SpecialCustomization state;
    private LinearLayout slots, addons;
    private TextView description, quantity, hint, error;
    private MaterialButton add;
    private final Map<String, String> selectionLabels = new HashMap<>();
    private Models.SpecialComponent pendingComponent;
    private Models.SpecialAddon pendingAddon;
    private int pendingIndex;
    private boolean adding;

    private final ActivityResultLauncher<Intent> configure = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null || state == null) return;
                Intent data = result.getData();
                Models.OrderItemRequest item = new Gson().fromJson(
                        data.getStringExtra(EXTRA_CONFIGURED_REQUEST), Models.OrderItemRequest.class);
                if (item == null) return;
                if (pendingComponent != null) {
                    state.select(pendingComponent, pendingIndex, item);
                    selectionLabels.put(SpecialCustomization.key(pendingComponent.id, pendingIndex),
                            data.getStringExtra(EXTRA_CONFIGURED_DETAIL));
                } else if (pendingAddon != null) {
                    Models.SpecialAddonSelection old = state.addon(pendingAddon.id);
                    state.setAddon(pendingAddon, old == null ? 1 : old.quantity, item);
                }
                pendingComponent = null;
                pendingAddon = null;
                render();
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_special_detail);
        setupToolbar("Special");
        slots = findViewById(R.id.slots);
        addons = findViewById(R.id.addons);
        description = findViewById(R.id.tvDescription);
        quantity = findViewById(R.id.tvQuantity);
        hint = findViewById(R.id.tvHint);
        error = findViewById(R.id.tvError);
        add = findViewById(R.id.btnAdd);
        findViewById(R.id.btnMinus).setOnClickListener(v -> {
            if (state != null) { state.setQuantity(state.quantity() - 1); render(); }
        });
        findViewById(R.id.btnPlus).setOnClickListener(v -> {
            if (state != null) { state.setQuantity(state.quantity() + 1); render(); }
        });
        add.setOnClickListener(v -> addToCart());
        int branchId = Prefs.get().getBranch().id;
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        Repo.get().specials(branchId, new Repo.Result<List<Models.Special>>() {
            @Override public void onSuccess(List<Models.Special> values) {
                if (dead()) return;
                for (Models.Special value : Models.nz(values)) if (value.id == id) special = value;
                Ui.show(findViewById(R.id.progress), false);
                if (special == null) { fail("This special is no longer available."); return; }
                state = new SpecialCustomization(special);
                render();
            }
            @Override public void onError(String message, int code) {
                if (dead()) return;
                Ui.show(findViewById(R.id.progress), false);
                fail(message);
            }
        });
    }

    private void fail(String message) {
        error.setText(message);
        Ui.show(error, true);
        add.setEnabled(false);
    }

    private Models.OrderItemRequest plain(String type, int id, Integer sizeCm) {
        Models.OrderItemRequest request = new Models.OrderItemRequest();
        if ("PIZZA".equalsIgnoreCase(type)) {
            request.pizzaId = id;
            request.sizeCm = sizeCm;
        } else request.menuItemId = id;
        return request;
    }

    private MaterialButton command(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void render() {
        if (state == null) return;
        setupToolbar(special.name);
        description.setText(special.description == null ? "" : special.description);
        slots.removeAllViews();
        addons.removeAllViews();
        for (Models.SpecialComponent component : Models.nz(special.components)) {
            slots.addView(Ui.sectionTitle(this, component.label
                    + (component.sizeCm == null ? "" : " · " + component.sizeCm + "cm")));
            if ("INCLUDED".equalsIgnoreCase(component.selectionMode) && !component.allowCustomization) {
                TextView included = new TextView(this);
                included.setText("Included: " + component.label);
                slots.addView(included);
                continue;
            }
            for (int index = 1; index <= component.quantity; index++) {
                final int slot = index;
                if (Models.nz(component.options).size() == 1 && state.selection(component.id, slot) == null) {
                    Models.SpecialOption only = component.options.get(0);
                    state.select(component, slot, plain(component.productType, only.id, component.sizeCm));
                }
                Models.SpecialSelection selected = state.selection(component.id, slot);
                String selectedName = "Choose " + component.label;
                for (Models.SpecialOption option : Models.nz(component.options)) {
                    if (selected != null && (selected.item.pizzaId != null ? selected.item.pizzaId
                            : selected.item.menuItemId) == option.id) selectedName = option.name;
                }
                MaterialButton choice = command(component.quantity > 1 ? slot + ". " + selectedName : selectedName);
                choice.setOnClickListener(v -> choose(component, slot));
                slots.addView(choice);
                if (selected != null && component.allowCustomization) {
                    MaterialButton edit = command("Customize " + selectedName);
                    edit.setOnClickListener(v -> configure(component, slot, selected.item));
                    slots.addView(edit);
                }
                String detail = selectionLabels.get(SpecialCustomization.key(component.id, slot));
                if (detail != null && !detail.isEmpty()) {
                    TextView summary = new TextView(this);
                    summary.setText(detail);
                    slots.addView(summary);
                }
            }
        }
        for (Models.SpecialAddon addon : Models.nz(special.addons)) {
            if (!addon.active) continue;
            addons.addView(Ui.sectionTitle(this, addon.label + " · " + Money.rand(addon.price)));
            Models.SpecialAddonSelection selected = state.addon(addon.id);
            CheckBox check = new CheckBox(this);
            check.setText("Add " + addon.productName);
            check.setChecked(selected != null);
            check.setOnCheckedChangeListener((button, checked) -> {
                state.setAddon(addon, checked ? 1 : 0,
                        checked ? plain(addon.productType, addon.productId, addon.sizeCm) : null);
                render();
            });
            addons.addView(check);
            if (selected != null) {
                if (addon.maxQuantity > 1) {
                    LinearLayout row = new LinearLayout(this);
                    MaterialButton minus = command("−");
                    minus.setContentDescription("Decrease " + addon.label + " quantity");
                    minus.setOnClickListener(v -> {
                        if (selected.quantity <= 1) state.setAddon(addon, 0, null);
                        else state.setAddon(addon, selected.quantity - 1, selected.item);
                        render();
                    });
                    TextView count = new TextView(this);
                    count.setText("  " + selected.quantity + "  ");
                    MaterialButton plus = command("+");
                    plus.setContentDescription("Increase " + addon.label + " quantity");
                    plus.setEnabled(selected.quantity < addon.maxQuantity);
                    plus.setOnClickListener(v -> { state.setAddon(addon, selected.quantity + 1, selected.item); render(); });
                    row.addView(minus); row.addView(count); row.addView(plus);
                    addons.addView(row);
                }
                if (addon.allowCustomization) {
                    MaterialButton edit = command("Customize " + addon.label);
                    edit.setOnClickListener(v -> configure(addon, selected.item));
                    addons.addView(edit);
                }
            }
        }
        quantity.setText("Quantity: " + state.quantity());
        String problem = state.problem();
        hint.setText(problem == null ? "Estimated total. Final price confirmed at checkout." : problem);
        add.setText("Add to cart · " + Money.rand(state.estimate()));
        add.setEnabled(problem == null && !adding);
    }

    private void choose(Models.SpecialComponent component, int index) {
        List<Models.SpecialOption> options = Models.nz(component.options);
        if (options.isEmpty()) return;
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).name;
        new AlertDialog.Builder(this).setTitle(component.label).setItems(labels, (dialog, which) -> {
            Models.SpecialOption option = options.get(which);
            state.select(component, index, plain(component.productType, option.id, component.sizeCm));
            selectionLabels.remove(SpecialCustomization.key(component.id, index));
            render();
        }).show();
    }

    private void configure(Models.SpecialComponent component, int index, Models.OrderItemRequest item) {
        pendingComponent = component;
        pendingAddon = null;
        pendingIndex = index;
        launch(item, component.sizeCm);
    }

    private void configure(Models.SpecialAddon addon, Models.OrderItemRequest item) {
        pendingAddon = addon;
        pendingComponent = null;
        launch(item, addon.sizeCm);
    }

    private void launch(Models.OrderItemRequest item, Integer sizeCm) {
        Intent intent = new Intent(this, item.pizzaId != null ? PizzaDetailActivity.class : ItemDetailActivity.class);
        intent.putExtra(EXTRA_CONFIGURE_FOR_SPECIAL, true);
        if (item.pizzaId != null) {
            intent.putExtra(PizzaDetailActivity.EXTRA_ID, item.pizzaId);
            if (sizeCm != null) intent.putExtra(EXTRA_LOCKED_SIZE_CM, sizeCm);
        } else intent.putExtra(ItemDetailActivity.EXTRA_ID, item.menuItemId);
        configure.launch(intent);
    }

    private void addToCart() {
        if (state == null || state.problem() != null) return;
        adding = true;
        render();
        Models.SpecialItemRequest request = state.request();
        Repo.get().specialQuote(Prefs.get().getBranch().id, request, new Repo.Result<Models.SpecialQuote>() {
            @Override public void onSuccess(Models.SpecialQuote quote) {
                if (dead()) return;
                double total = quote == null ? state.estimate() : quote.lineTotal;
                complete(request, total);
            }
            @Override public void onError(String message, int code) {
                if (dead()) return;
                if (code == 0) complete(request, state.estimate());
                else { adding = false; fail(message); render(); }
            }
        });
    }

    private void complete(Models.SpecialItemRequest request, double total) {
        Cart.get().add(new Cart.Line(special.name, "Special bundle", total / state.quantity(),
                state.quantity(), request));
        Ui.toast(this, "Added to cart");
        finish();
    }
}
