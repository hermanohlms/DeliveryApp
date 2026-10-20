package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.CatalogOptions;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.example.deliveryapp.util.Money;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Burgers, pastas, drinks...: modifier groups (sides, sauces) and burger components (protein, toppings). */
public class ItemDetailActivity extends BaseActivity {
    public static final String EXTRA_ID = "itemId";

    private static class Binding {
        final CompoundButton button;
        final BooleanSupplier checked;
        Binding(CompoundButton button, BooleanSupplier checked) { this.button = button; this.checked = checked; }
    }

    private int itemId;
    private Models.MenuItem item;
    private List<Models.MenuItem> catalog;
    private final Map<Integer, Set<Integer>> modifiers = new HashMap<>();   // groupId -> chosen optionIds
    private final Set<Integer> toppings = new HashSet<>();                  // ticked burger components
    private final List<Binding> bindings = new ArrayList<>();
    private int qty = 1;

    private MaterialToolbar toolbar;
    private View content, progress, errorGroup;
    private TextView tvError, tvDesc, tvQty, tvHint;
    private LinearLayout sections, variants;
    private TextInputEditText etNotes;
    private MaterialButton btnAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);
        toolbar = setupToolbar("Item");
        itemId = getIntent().getIntExtra(EXTRA_ID, -1);

        content = findViewById(R.id.content);
        progress = findViewById(R.id.progress);
        errorGroup = findViewById(R.id.errorGroup);
        tvError = findViewById(R.id.tvError);
        tvDesc = findViewById(R.id.tvDesc);
        sections = findViewById(R.id.sections);
        variants = findViewById(R.id.variants);
        etNotes = findViewById(R.id.etNotes);
        tvQty = findViewById(R.id.tvQty);
        tvHint = findViewById(R.id.tvHint);
        btnAdd = findViewById(R.id.btnAdd);

        findViewById(R.id.btnMinus).setOnClickListener(v -> { if (qty > 1) { qty--; updateBar(); } });
        findViewById(R.id.btnPlus).setOnClickListener(v -> { if (qty < 50) { qty++; updateBar(); } });
        if (getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false)) {
            findViewById(R.id.btnMinus).setVisibility(View.GONE);
            findViewById(R.id.btnPlus).setVisibility(View.GONE);
        }
        findViewById(R.id.btnRetry).setOnClickListener(v -> load());
        btnAdd.setOnClickListener(v -> addToCart());
        btnAdd.setEnabled(false);
        load();
    }

    private void load() {
        Ui.show(progress, true); Ui.show(errorGroup, false); Ui.show(content, false);
        Repo.get().menu(Prefs.get().getBranch().id, false, new Repo.Result<List<Models.MenuItem>>() {
            @Override public void onSuccess(List<Models.MenuItem> items) {
                if (dead()) return;
                catalog = items;
                selectVariant(itemId);
            }
            @Override public void onError(String message, int code) {
                if (dead()) return;
                Ui.show(progress, false);
                tvError.setText(message);
                Ui.show(errorGroup, true);
            }
        });
    }

    private void selectVariant(int id) {
        Models.MenuItem next = CatalogOptions.find(catalog, id);
        if (next == null) {
            Ui.show(progress, false);
            tvError.setText("That item is no longer available.");
            Ui.show(errorGroup, true);
            return;
        }
        item = next;
        itemId = id;
        modifiers.clear();
        toppings.clear();
        for (Models.BurgerTopping t : CatalogOptions.toppings(item, catalog)) {
            if (t.defaultSelected) toppings.add(t.toppingId);
        }
        render();
    }

    private List<Models.ModifierGroup> visibleGroups() {
        Set<Integer> selected = new HashSet<>();
        for (Set<Integer> values : modifiers.values()) selected.addAll(values);
        return CatalogOptions.visibleGroups(item, selected);
    }

    // ------------------------------------------------------------------ rendering
    private void render() {
        toolbar.setTitle(item.name);
        boolean hasDesc = item.description != null && !item.description.trim().isEmpty();
        tvDesc.setText(hasDesc ? item.description : "");
        Ui.show(tvDesc, hasDesc);

        variants.removeAllViews();
        List<Models.MenuItem> family = CatalogOptions.variants(item, catalog);
        if (family.size() > 1 && !getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false)) {
            variants.addView(Ui.sectionTitle(this, "Choose an option"));
            for (Models.MenuItem variant : family) {
                CompoundButton row = Ui.choiceRow(this,
                        CatalogOptions.variantLabel(variant) + " · " + Money.rand(variant.priceOrZero()),
                        null, true, true);
                row.setOnClickListener(v -> selectVariant(variant.menuItemId));
                row.setChecked(variant.menuItemId == itemId);
                variants.addView(row);
            }
        }
        sections.removeAllViews();
        bindings.clear();

        final List<Models.BurgerTopping> all = CatalogOptions.toppings(item, catalog);
        List<Models.BurgerTopping> proteins = new ArrayList<>();
        List<Models.BurgerTopping> others = new ArrayList<>();
        for (Models.BurgerTopping t : all) { if (t.isProtein()) proteins.add(t); else others.add(t); }

        if (!proteins.isEmpty()) {
            sections.addView(Ui.sectionTitle(this, "Choose your protein"));
            for (final Models.BurgerTopping t : proteins) {
                CompoundButton row = Ui.choiceRow(this, t.name, t.defaultSelected ? null : t.price, true, true);
                row.setOnClickListener(v -> { toggleTopping(t, all); sync(); });
                bindings.add(new Binding(row, () -> toppings.contains(t.toppingId)));
                sections.addView(row);
            }
        }
        if (!others.isEmpty()) {
            sections.addView(Ui.sectionTitle(this, "Toppings"));
            for (final Models.BurgerTopping t : others) {
                boolean locked = t.defaultSelected && !t.isRemovable();
                CompoundButton row = Ui.choiceRow(this, t.name, t.defaultSelected ? null : t.price, false, !locked);
                row.setOnClickListener(v -> { toggleTopping(t, all); sync(); });
                bindings.add(new Binding(row, () -> toppings.contains(t.toppingId)));
                sections.addView(row);
            }
        }
        for (final Models.ModifierGroup g : visibleGroups()) {
            sections.addView(Ui.sectionTitle(this, g.name + (g.minRequired() > 0 ? " (required)" : "")));
            final boolean single = g.maxAllowed() == 1;
            for (final Models.ModifierOption o : Models.nz(g.options)) {
                CompoundButton row = Ui.choiceRow(this, o.name, o.price,
                        single && g.minRequired() > 0, true);
                row.setOnClickListener(v -> { toggleModifier(g, o.optionId); render(); });
                bindings.add(new Binding(row, () -> chosen(g).contains(o.optionId)));
                sections.addView(row);
            }
        }

        Ui.show(progress, false);
        Ui.show(errorGroup, false);
        Ui.show(content, true);
        sync();
    }

    private Set<Integer> chosen(Models.ModifierGroup g) {
        Set<Integer> s = modifiers.get(g.groupId);
        return s == null ? new HashSet<Integer>() : s;
    }

    private void sync() {
        for (Binding b : bindings) b.button.setChecked(b.checked.getAsBoolean());
        updateBar();
    }

    // ------------------------------------------------------------------ selection rules
    private void toggleModifier(Models.ModifierGroup g, int optionId) {
        Set<Integer> current = new HashSet<>(chosen(g));
        int max = g.maxAllowed();
        if (max == 1) {
            current.clear();
            if (!(g.minRequired() == 0 && chosen(g).contains(optionId))) current.add(optionId);
        } else if (current.contains(optionId)) {
            current.remove(optionId);
        } else if (max > 0 && current.size() >= max) {
            return;   // at the limit
        } else {
            current.add(optionId);
        }
        modifiers.put(g.groupId, current);
    }

    private void toggleTopping(Models.BurgerTopping t, List<Models.BurgerTopping> all) {
        if (t.isProtein()) {
            for (Models.BurgerTopping p : all) if (p.isProtein()) toppings.remove(p.toppingId);
            toppings.add(t.toppingId);
        } else if (toppings.contains(t.toppingId)) {
            if (!(t.defaultSelected && !t.isRemovable())) toppings.remove(t.toppingId);
        } else {
            toppings.add(t.toppingId);
        }
    }

    /** First reason the selection is incomplete, or null when it can be added. */
    private String problem() {
        if (item == null) return null;
        for (Models.ModifierGroup g : visibleGroups()) {
            int min = g.minRequired();
            if (chosen(g).size() < min) {
                return "Choose " + (min > 1 ? "at least " + min + " for " : "") + g.name;
            }
        }
        int proteins = 0, picked = 0;
        for (Models.BurgerTopping t : CatalogOptions.toppings(item, catalog)) {
            if (t.isProtein()) { proteins++; if (toppings.contains(t.toppingId)) picked++; }
        }
        if (proteins > 0 && picked != 1) return "Choose your protein";
        return null;
    }

    private double unitPrice() {
        if (item == null) return 0.0;
        double total = item.priceOrZero();
        for (Models.ModifierGroup g : visibleGroups()) {
            for (Models.ModifierOption o : Models.nz(g.options)) {
                if (chosen(g).contains(o.optionId) && o.price != null) total += o.price;
            }
        }
        // anything that isn't a default component costs extra
        for (Models.BurgerTopping t : CatalogOptions.toppings(item, catalog)) {
            if (toppings.contains(t.toppingId) && !t.defaultSelected) total += t.priceOrZero();
        }
        return total;
    }

    private void updateBar() {
        tvQty.setText(String.valueOf(qty));
        String problem = problem();
        tvHint.setText(problem == null ? "" : problem);
        Ui.show(tvHint, problem != null);
        btnAdd.setText("Add to cart · " + Money.rand(unitPrice() * qty));
        btnAdd.setEnabled(item != null && problem == null);
    }

    // ------------------------------------------------------------------ add to cart
    private void addToCart() {
        if (item == null || problem() != null) return;
        Models.OrderItemRequest req = new Models.OrderItemRequest();
        req.menuItemId = item.menuItemId;
        req.quantity = 1;
        List<String> names = new ArrayList<>();

        for (Models.ModifierGroup g : visibleGroups()) {
            for (Models.ModifierOption o : Models.nz(g.options)) {
                if (chosen(g).contains(o.optionId)) {
                    req.customizations.add(new Models.Customization(o.optionId, 1, "modifierOption"));
                    names.add(o.name);
                }
            }
        }
        for (Models.BurgerTopping t : CatalogOptions.toppings(item, catalog)) {
            boolean on = toppings.contains(t.toppingId);
            if (on) {
                // kept defaults + the protein -> burgerTopping; anything else is a paid extra
                String type = (t.defaultSelected || t.isProtein()) ? "burgerTopping" : "burgerExtra";
                req.customizations.add(new Models.Customization(t.toppingId, 1, type));
                if (t.isProtein()) names.add(t.name); else if (!t.defaultSelected) names.add("+ " + t.name);
            } else if (t.defaultSelected && !t.isProtein()) {
                names.add("no " + t.name);
            }
        }
        String notes = Ui.text(etNotes);
        req.notes = notes.isEmpty() ? null : notes;

        if (getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false)) {
            Intent result = new Intent();
            result.putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_REQUEST, new Gson().toJson(req));
            result.putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_DETAIL, String.join(" · ", names));
            result.putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_PRICE, unitPrice());
            setResult(RESULT_OK, result);
        } else {
            Cart.get().add(new Cart.Line(item.name, String.join(" · ", names), unitPrice(), qty, req));
            Ui.toast(this, "Added to cart");
        }
        finish();
    }
}
