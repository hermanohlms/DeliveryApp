package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
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

    private String editId;
    private Models.SpecialItemRequest initial;
    private Bundle restored;
    private Intent deferredResult;
    private boolean changed;
    private Models.Special special;
    private SpecialCustomization state;
    private LinearLayout slots, addons;
    private TextView description, quantity, hint, error;
    private MaterialButton add;
    private final Map<String, String> selectionLabels = new HashMap<>();
    private Models.SpecialComponent pendingComponent;
    private Models.SpecialAddon pendingAddon;
    private int pendingIndex;
    private boolean adding, quoteLoading;
    private String quoteKey, quoteError;
    private Double quotedTotal;
    private int quoteGeneration;

    private final ActivityResultLauncher<Intent> configure = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                if (state == null) deferredResult = result.getData();
                else applyConfigured(result.getData());
            });

    private void applyConfigured(Intent data) {
        Models.OrderItemRequest item = new Gson().fromJson(data.getStringExtra(EXTRA_CONFIGURED_REQUEST), Models.OrderItemRequest.class);
        if (item == null) return;
        if (pendingComponent != null) {
            state.select(pendingComponent, pendingIndex, item);
            selectionLabels.put(SpecialCustomization.key(pendingComponent.id, pendingIndex), data.getStringExtra(EXTRA_CONFIGURED_DETAIL));
        } else if (pendingAddon != null) {
            Models.SpecialAddonSelection old = state.addon(pendingAddon.id);
            state.setAddon(pendingAddon, old == null ? 1 : old.quantity, item);
            selectionLabels.put("addon:" + pendingAddon.id, data.getStringExtra(EXTRA_CONFIGURED_DETAIL));
        }
        pendingComponent = null; pendingAddon = null; deferredResult = null; render();
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_special_detail);
        Ui.configureAddBar(this);
        setupToolbar("Special");
        slots = findViewById(R.id.slots);
        addons = findViewById(R.id.addons);
        description = findViewById(R.id.tvDescription);
        quantity = findViewById(R.id.tvQty);
        hint = findViewById(R.id.tvHint);
        error = findViewById(R.id.tvError);
        add = findViewById(R.id.btnAdd);
        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            if (state == null) recreate();
            else { quoteKey = null; quoteError = null; Ui.show(error, false); render(); }
        });
        findViewById(R.id.btnMinus).setOnClickListener(v -> {
            if (state != null) { state.setQuantity(state.quantity() - 1); render(); }
        });
        findViewById(R.id.btnPlus).setOnClickListener(v -> {
            if (state != null) { state.setQuantity(state.quantity() + 1); render(); }
        });
        add.setOnClickListener(v -> addToCart());
        editId = getIntent().getStringExtra(CartEditor.EXTRA_LINE_ID);
        restored = savedInstanceState;
        Cart.Line line = editId == null ? null : Cart.get().find(editId);
        if (editId != null && line == null) {
            Ui.show(findViewById(R.id.progress), false);
            fail("This item is no longer in your cart. Go back to add it again."); return;
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("draft")) {
            initial = new Gson().fromJson(savedInstanceState.getString("draft"), Models.SpecialItemRequest.class);
            changed = savedInstanceState.getBoolean("changed");
            Bundle labels = savedInstanceState.getBundle("labels");
            if (labels != null) for (String key : labels.keySet()) selectionLabels.put(key, labels.getString(key));
        } else if (line != null) {
            initial = line.specialRequest.copyWithQuantity(line.quantity); selectionLabels.putAll(line.componentDetails);
        }
        int branchId = Prefs.get().getBranch().id;
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        Repo.get().specials(branchId, new Repo.Result<List<Models.Special>>() {
            @Override public void onSuccess(List<Models.Special> values) {
                if (dead()) return;
                for (Models.Special value : Models.nz(values)) if (value.id == id) special = value;
                Ui.show(findViewById(R.id.progress), false);
                if (special == null) { fail("This special is no longer available."); return; }
                state = new SpecialCustomization(special);
                if (initial != null) { changed |= state.restore(initial); initial = null; }
                if (restored != null) {
                    long componentId = restored.getLong("pendingComponent", -1);
                    long addonId = restored.getLong("pendingAddon", -1);
                    pendingIndex = restored.getInt("pendingIndex");
                    for (Models.SpecialComponent c : Models.nz(special.components)) if (c.id == componentId) pendingComponent = c;
                    for (Models.SpecialAddon a : Models.nz(special.addons)) if (a.id == addonId) pendingAddon = a;
                    restored = null;
                }
                if (deferredResult != null) applyConfigured(deferredResult);
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
        Ui.show(findViewById(R.id.btnRetry), editId == null || Cart.get().find(editId) != null);
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
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void render() {
        if (state == null) return;
        setupToolbar(special.name);
        description.setText(special.description == null ? "" : special.description);
        ((TextView)findViewById(R.id.tvBasePrice)).setText(Money.rand(special.price));
        TextView notice = findViewById(R.id.tvNotice);
        notice.setText("Some saved choices have changed. Review this special before saving."); Ui.show(notice, changed);
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
            slots.addView(Ui.sectionHint(this, "Required · Choose " + (component.quantity == 1 ? "one" : component.quantity)));
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
        if (Models.nz(special.addons).stream().anyMatch(a -> a.active)) {
            addons.addView(Ui.sectionTitle(this, "Extras")); addons.addView(Ui.sectionHint(this, "Optional"));
        }
        for (Models.SpecialAddon addon : Models.nz(special.addons)) {
            if (!addon.active) continue;
            Models.SpecialAddonSelection selected = state.addon(addon.id);
            CompoundButton check = Ui.choiceRow(this, addon.label, addon.price, false, true);
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
                    minus.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,48), Ui.dp(this,48)));
                    TextView count = new TextView(this);
                    count.setText("  " + selected.quantity + "  ");
                    MaterialButton plus = command("+");
                    plus.setContentDescription("Increase " + addon.label + " quantity");
                    plus.setEnabled(selected.quantity < addon.maxQuantity);
                    plus.setOnClickListener(v -> { state.setAddon(addon, selected.quantity + 1, selected.item); render(); });
                    plus.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,48), Ui.dp(this,48)));
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
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
        refreshQuote();
        updateBar();
        if (adding) { Ui.enableInputs(slots, false); Ui.enableInputs(addons, false); }
    }

    private void updateBar() {
        quantity.setText(String.valueOf(state.quantity()));
        findViewById(R.id.btnMinus).setEnabled(state.quantity() > 1 && !adding);
        findViewById(R.id.btnPlus).setEnabled(state.quantity() < 50 && !adding);
        String problem = state.problem() == null ? quoteError : state.problem();
        hint.setText(problem); Ui.show(hint, problem != null);
        double total = quotedTotal == null ? state.estimate() : quotedTotal;
        add.setText(adding ? "Checking price…" : quoteLoading ? "Updating total…"
                : (editId == null ? "Add to cart · " : "Update cart · ") + Money.rand(total));
        add.setEnabled(problem == null && !adding && !quoteLoading);
    }

    /** Nested toppings can cost extra too; keep the visible total in sync with the server quote. */
    private void refreshQuote() {
        if (state.problem() != null) {
            quoteGeneration++; quoteKey = null; quotedTotal = null; quoteError = null; quoteLoading = false; return;
        }
        Models.SpecialItemRequest request = state.request().copyWithQuantity(state.quantity());
        String key = new Gson().toJson(request);
        if (key.equals(quoteKey)) return;
        quoteKey = key; quotedTotal = null; quoteError = null; quoteLoading = true;
        int generation = ++quoteGeneration;
        Repo.get().specialQuote(Prefs.get().getBranch().id, request, new Repo.Result<Models.SpecialQuote>() {
            @Override public void onSuccess(Models.SpecialQuote quote) {
                if (dead() || generation != quoteGeneration) return;
                quoteLoading = false; quotedTotal = quote == null ? null : quote.lineTotal; Ui.show(findViewById(R.id.btnRetry), false); updateBar();
            }
            @Override public void onError(String message, int code) {
                if (dead() || generation != quoteGeneration) return;
                quoteLoading = false; quoteError = code == 0 ? null : message; Ui.show(findViewById(R.id.btnRetry), quoteError != null); updateBar();
            }
        });
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
        intent.putExtra(CartEditor.EXTRA_INITIAL_REQUEST, new Gson().toJson(item));
        String key = pendingComponent == null ? "addon:" + pendingAddon.id : SpecialCustomization.key(pendingComponent.id, pendingIndex);
        intent.putExtra(CartEditor.EXTRA_USE_DEFAULTS, !selectionLabels.containsKey(key) && Models.nz(item.customizations).isEmpty());
        if (item.pizzaId != null) {
            intent.putExtra(PizzaDetailActivity.EXTRA_ID, item.pizzaId);
            if (sizeCm != null) intent.putExtra(EXTRA_LOCKED_SIZE_CM, sizeCm);
        } else intent.putExtra(ItemDetailActivity.EXTRA_ID, item.menuItemId);
        configure.launch(intent);
    }

    private void addToCart() {
        if (state == null || state.problem() != null || adding || quoteLoading || quoteError != null) return;
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
        Cart.Line line = new Cart.Line(special.name, summary(), total / state.quantity(), state.quantity(), request);
        line.componentDetails.putAll(selectionLabels);
        if (!CartEditor.save(this, editId, line)) { adding = false; render(); return; }
        finish();
    }
    private String summary() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (Models.SpecialComponent component : Models.nz(special.components)) {
            for (int slot = 1; slot <= component.quantity; slot++) {
                Models.SpecialSelection choice = state.selection(component.id, slot);
                if (choice == null || choice.item == null) continue;
                int id = choice.item.pizzaId != null ? choice.item.pizzaId : choice.item.menuItemId;
                for (Models.SpecialOption option : Models.nz(component.options)) if (option.id == id) {
                    String detail = selectionLabels.get(SpecialCustomization.key(component.id, slot));
                    parts.add(option.name + (detail == null || detail.isBlank() ? "" : " (" + detail + ")")
                            + (choice.item.notes == null || choice.item.notes.isBlank() ? "" : " · Note: " + choice.item.notes));
                }
            }
        }
        for (Models.SpecialAddon addon : Models.nz(special.addons)) {
            Models.SpecialAddonSelection choice = state.addon(addon.id);
            if (choice != null) {
                String detail = selectionLabels.get("addon:" + addon.id);
                parts.add(choice.quantity + " × " + addon.label + (detail == null || detail.isBlank() ? "" : " (" + detail + ")")
                        + (choice.item.notes == null || choice.item.notes.isBlank() ? "" : " · Note: " + choice.item.notes));
            }
        }
        return parts.isEmpty() ? "Special bundle" : String.join("\n", parts);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (state != null) out.putString("draft", new Gson().toJson(state.request()));
        else if (initial != null) out.putString("draft", new Gson().toJson(initial));
        Bundle labels = new Bundle(); for (Map.Entry<String,String> e : selectionLabels.entrySet()) labels.putString(e.getKey(), e.getValue());
        out.putBundle("labels", labels); out.putBoolean("changed", changed);
        out.putLong("pendingComponent", pendingComponent == null ? -1 : pendingComponent.id);
        out.putLong("pendingAddon", pendingAddon == null ? -1 : pendingAddon.id); out.putInt("pendingIndex", pendingIndex);
        super.onSaveInstanceState(out);
    }

}
