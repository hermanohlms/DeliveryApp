package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.PizzaCustomization;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.example.deliveryapp.util.Money;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

public class PizzaDetailActivity extends BaseActivity {
    public static final String EXTRA_ID = "pizzaId";

    private static class Binding {
        final CompoundButton button;
        final BooleanSupplier checked;
        Binding(CompoundButton button, BooleanSupplier checked) { this.button = button; this.checked = checked; }
    }

    private String editId;
    private Models.OrderItemRequest initial;
    private boolean changed;
    private int pizzaId;
    private int branchId;
    private Integer lockedSizeCm;
    private Models.PizzaDetail detail;
    private final PizzaCustomization selection = new PizzaCustomization();
    private final List<Binding> bindings = new ArrayList<>();
    private int qty = 1;
    private boolean refreshing, adding;
    private int loadGeneration;

    private MaterialToolbar toolbar;
    private View content, progress, errorGroup, lblBase, lblDefaults, lblExtras;
    private TextView tvError, tvIncludes, tvQty;
    private ChipGroup sizes;
    private LinearLayout bases, defaultsBox, extrasBox;
    private TextInputEditText etNotes;
    private MaterialButton btnAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pizza_detail);
        Ui.configureAddBar(this);
        toolbar = setupToolbar("Pizza");
        pizzaId = getIntent().getIntExtra(EXTRA_ID, -1);
        branchId = Prefs.get().getBranch().id;
        editId = getIntent().getStringExtra(CartEditor.EXTRA_LINE_ID);
        if (getIntent().hasExtra(SpecialDetailActivity.EXTRA_LOCKED_SIZE_CM)) {
            lockedSizeCm = getIntent().getIntExtra(SpecialDetailActivity.EXTRA_LOCKED_SIZE_CM, 0);
        }

        content = findViewById(R.id.content);
        progress = findViewById(R.id.progress);
        errorGroup = findViewById(R.id.errorGroup);
        tvError = findViewById(R.id.tvError);
        tvIncludes = findViewById(R.id.tvIncludes);
        sizes = findViewById(R.id.sizes);
        lblBase = findViewById(R.id.lblBase);
        bases = findViewById(R.id.bases);
        lblDefaults = findViewById(R.id.lblDefaults);
        defaultsBox = findViewById(R.id.defaults);
        lblExtras = findViewById(R.id.lblExtras);
        extrasBox = findViewById(R.id.extras);
        etNotes = findViewById(R.id.etNotes);
        tvQty = findViewById(R.id.tvQty);
        btnAdd = findViewById(R.id.btnAdd);

        findViewById(R.id.btnMinus).setOnClickListener(v -> { if (qty > 1) { qty--; updateBar(); } });
        findViewById(R.id.btnPlus).setOnClickListener(v -> { if (qty < 50) { qty++; updateBar(); } });
        if (getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false)) {
            findViewById(R.id.btnMinus).setVisibility(View.GONE);
            findViewById(R.id.btnPlus).setVisibility(View.GONE);
        }
        findViewById(R.id.btnRetry).setOnClickListener(v -> load(null));
        btnAdd.setOnClickListener(v -> addToCart());

        btnAdd.setEnabled(false);
        Cart.Line line = editId == null ? null : Cart.get().find(editId);
        if (editId != null && line == null) {
            tvError.setText("This item is no longer in your cart. Go back to add it again.");
            Ui.show(progress, false); Ui.show(errorGroup, true); Ui.show(findViewById(R.id.btnRetry), false); return;
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("draft")) {
            initial = new Gson().fromJson(savedInstanceState.getString("draft"), Models.OrderItemRequest.class);
            qty = savedInstanceState.getInt("quantity", 1); changed = savedInstanceState.getBoolean("changed");
        } else if (line != null) { initial = line.request.copyWithQuantity(1); qty = line.quantity; }
        else if (!getIntent().getBooleanExtra(CartEditor.EXTRA_USE_DEFAULTS, false)) {
            initial = new Gson().fromJson(getIntent().getStringExtra(CartEditor.EXTRA_INITIAL_REQUEST), Models.OrderItemRequest.class);
        }
        if (initial != null) etNotes.setText(initial.notes);
        if (getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false)) tvQty.setVisibility(View.GONE);
        load(lockedSizeCm != null ? lockedSizeCm : initial == null ? null : initial.sizeCm);
    }

    // ------------------------------------------------------------------ loading
    private void load(Integer size) {
        final int generation = ++loadGeneration;
        if (detail == null) {
            Ui.show(progress, true); Ui.show(errorGroup, false); Ui.show(content, false);
        } else {
            refreshing = true;
            updateBar();
        }
        Repo.get().pizzaDetail(branchId, pizzaId, size, new Repo.Result<Models.PizzaDetail>() {
            @Override public void onSuccess(Models.PizzaDetail d) {
                if (dead() || generation != loadGeneration) return;
                refreshing = false;
                if (d == null) {
                    tvError.setText("That pizza is no longer available. Choose another from the menu.");
                    Ui.show(progress, false); Ui.show(content, false); Ui.show(errorGroup, true); btnAdd.setEnabled(false); return;
                }
                detail = d;
                Integer actualSize = size;
                if (size != null && Models.nz(d.availableSizes).stream().noneMatch(v -> v.sizeCm == size)) {
                    actualSize = null; changed = true;
                }
                selection.apply(d, actualSize);
                if (initial != null) { changed |= selection.restore(initial); initial = null; }
                render();
            }
            @Override public void onError(String message, int code) {
                if (dead() || generation != loadGeneration) return;
                refreshing = false;
                if (detail == null && size != null && (code == 400 || code == 404)) {
                    changed = true; load(null); return;
                }
                if (detail == null) {
                    Ui.show(progress, false);
                    tvError.setText(message);
                    Ui.show(errorGroup, true);
                } else {
                    Ui.toast(PizzaDetailActivity.this, message);
                    updateBar();
                }
            }
        });
    }

    // ------------------------------------------------------------------ rendering
    private void render() {
        final Models.PizzaDetail d = detail;
        toolbar.setTitle(d.name);
        ((TextView) findViewById(R.id.tvBasePrice)).setText(Money.rand(d.basePriceForSelectedSize));
        TextView notice = findViewById(R.id.tvNotice);
        notice.setText("Some saved choices have changed. Please review your options before saving."); Ui.show(notice, changed);

        Ui.show(tvIncludes, false);

        // sizes
        sizes.removeAllViews();
        for (final Models.SizePrice s : Models.nz(d.availableSizes)) {
            Chip chip = (Chip) LayoutInflater.from(this).inflate(R.layout.chip_filter, sizes, false);
            chip.setText(s.sizeCm + "cm · " + Money.rand(s.basePrice));
            chip.setChecked(selection.sizeCm() != null && selection.sizeCm() == s.sizeCm);
            chip.setEnabled(lockedSizeCm == null);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked && !refreshing && (selection.sizeCm() == null || selection.sizeCm() != s.sizeCm)) {
                    load(s.sizeCm);
                }
            });
            sizes.addView(chip);
        }

        bindings.clear();

        // bases (single choice)
        bases.removeAllViews();
        List<Models.PizzaBaseOption> baseList = Models.nz(d.baseOptions);
        Ui.show(lblBase, !baseList.isEmpty());
        Ui.show(bases, !baseList.isEmpty());
        if (!baseList.isEmpty()) {
            CompoundButton standard = Ui.choiceRow(this, "Standard base", 0.0, true, true);
            standard.setOnClickListener(v -> { selection.setBaseOptionId(null); sync(); });
            bindings.add(new Binding(standard, () -> selection.baseOptionId() == null));
            bases.addView(standard);
        }
        for (final Models.PizzaBaseOption b : baseList) {
            CompoundButton row = Ui.choiceRow(this, b.name, b.extraPrice, true, true);
            row.setOnClickListener(v -> { selection.setBaseOptionId(b.pizzaBaseOptionId); sync(); });
            bindings.add(new Binding(row, () -> selection.baseOptionId() != null && selection.baseOptionId() == b.pizzaBaseOptionId));
            bases.addView(row);
        }

        defaultsBox.removeAllViews();
        List<Models.ToppingItem> defaultList = Models.nz(d.defaultToppings);
        Ui.show(lblDefaults, !defaultList.isEmpty());
        Ui.show(defaultsBox, !defaultList.isEmpty());
        for (Models.ToppingItem t : defaultList) {
            boolean removable = Boolean.TRUE.equals(t.removable);
            CompoundButton row = Ui.choiceRow(this, t.name, null, false, removable);
            row.setOnClickListener(v -> { selection.toggle(t); sync(); });
            bindings.add(new Binding(row, () -> selection.isSelected(t.toppingId)));
            defaultsBox.addView(row);
        }

        extrasBox.removeAllViews();
        Set<Integer> defaultIds = new java.util.HashSet<>();
        for (Models.ToppingItem t : Models.nz(d.defaultToppings)) defaultIds.add(t.toppingId);
        List<Models.ToppingItem> extraList = new ArrayList<>();
        for (Models.ToppingItem t : Models.nz(d.allToppings)) if (!defaultIds.contains(t.toppingId)) extraList.add(t);
        Ui.show(lblExtras, !extraList.isEmpty());
        Ui.show(extrasBox, !extraList.isEmpty());
        Map<Long, List<Models.ToppingItem>> priceGroups = new TreeMap<>();
        for (Models.ToppingItem topping : extraList) {
            long cents = Math.max(0, Math.round(extraPrice(topping.toppingId) * 100));
            priceGroups.computeIfAbsent(cents, ignored -> new ArrayList<>()).add(topping);
        }
        for (Map.Entry<Long, List<Models.ToppingItem>> group : priceGroups.entrySet()) {
            TextView heading = new TextView(this);
            heading.setText(group.getKey() == 0 ? "Included" : Money.rand(group.getKey() / 100.0));
            heading.setTextAppearance(R.style.Text_Section);
            heading.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
            androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
            extrasBox.addView(heading);
            for (final Models.ToppingItem topping : group.getValue()) {
                CompoundButton row = Ui.choiceRow(this, topping.name, extraPrice(topping.toppingId), false, true);
                row.setOnClickListener(v -> { selection.toggle(topping); sync(); });
                bindings.add(new Binding(row, () -> selection.isSelected(topping.toppingId)));
                extrasBox.addView(row);
            }
        }

        Ui.show(progress, false);
        Ui.show(errorGroup, false);
        Ui.show(content, true);
        sync();
    }

    private void sync() {
        for (Binding b : bindings) b.button.setChecked(b.checked.getAsBoolean());
        updateBar();
    }

    private double extraPrice(int toppingId) {
        for (Models.ToppingPrice p : Models.nz(detail.extraPrices)) if (p.toppingId == toppingId) return p.extraPrice;
        return 0.0;
    }

    /** Local estimate; the exact price is confirmed with the backend's quote endpoint when adding to the cart. */
    private double unitPrice() {
        return selection.estimate();
    }

    private void updateBar() {
        tvQty.setText(String.valueOf(qty));
        String action = getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false) ? "Save choices"
                : editId == null ? "Add to cart" : "Update cart";
        btnAdd.setText(adding ? "Checking price…" : refreshing ? "Updating prices…" : action + " · " + Money.rand(unitPrice() * qty));
        findViewById(R.id.btnMinus).setEnabled(qty > 1 && !adding && !refreshing);
        findViewById(R.id.btnPlus).setEnabled(qty < 50 && !adding && !refreshing);
        etNotes.setEnabled(!adding);
        if (adding) Ui.enableInputs(content, false);
        btnAdd.setEnabled(detail != null && selection.sizeCm() != null && !refreshing && !adding);
    }

    // ------------------------------------------------------------------ add to cart
    private void addToCart() {
        if (detail == null || selection.sizeCm() == null || adding || refreshing) return;
        adding = true;
        updateBar();
        Models.QuoteRequest q = selection.quoteRequest();
        Repo.get().quote(branchId, pizzaId, q, new Repo.Result<Models.QuoteResponse>() {
            @Override public void onSuccess(Models.QuoteResponse r) {
                if (dead()) return;
                complete(r == null ? null : r.total);
            }
            @Override public void onError(String message, int code) {
                if (dead()) return;
                adding = false;
                if (code == 0) complete(null);
                else { Ui.toast(PizzaDetailActivity.this, message); render(); }
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (detail != null) out.putString("draft", new Gson().toJson(selection.orderItem(pizzaId, Ui.text(etNotes))));
        else if (initial != null) out.putString("draft", new Gson().toJson(initial));
        out.putInt("quantity", qty); out.putBoolean("changed", changed);
        super.onSaveInstanceState(out);
    }

    private void complete(Double quoted) {
        double price = quoted != null && quoted > 0 ? quoted : unitPrice();
        List<String> parts = new ArrayList<>();
        parts.add(selection.sizeCm() + "cm");
        for (Models.PizzaBaseOption b : Models.nz(detail.baseOptions)) {
            if (selection.baseOptionId() != null && selection.baseOptionId() == b.pizzaBaseOptionId) parts.add(b.name);
        }
        for (Models.ToppingItem t : Models.nz(detail.allToppings)) {
            if (selection.isSelected(t.toppingId) && Models.nz(detail.defaultToppings).stream()
                    .noneMatch(def -> def.toppingId == t.toppingId)) parts.add("+ " + t.name);
        }
        for (Models.ToppingItem t : Models.nz(detail.defaultToppings)) {
            if (!selection.isSelected(t.toppingId)) parts.add("no " + t.name);
        }

        String notes = Ui.text(etNotes);
        Models.OrderItemRequest req = selection.orderItem(pizzaId, notes.isEmpty() ? null : notes);

        if (getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false)) {
            Intent result = new Intent();
            result.putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_REQUEST, new Gson().toJson(req));
            result.putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_DETAIL, String.join(" · ", parts));
            result.putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_PRICE, price);
            setResult(RESULT_OK, result);
        } else {
            if (!CartEditor.save(this, editId, new Cart.Line(detail.name, String.join(" · ", parts), price, qty, req))) {
                adding = false; render(); return;
            }
        }
        finish();
    }
}
