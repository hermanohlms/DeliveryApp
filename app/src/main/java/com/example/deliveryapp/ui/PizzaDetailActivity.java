package com.example.deliveryapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.example.deliveryapp.util.Money;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

public class PizzaDetailActivity extends BaseActivity {
    public static final String EXTRA_ID = "pizzaId";

    private static class Binding {
        final CompoundButton button;
        final BooleanSupplier checked;
        Binding(CompoundButton button, BooleanSupplier checked) { this.button = button; this.checked = checked; }
    }

    private int pizzaId;
    private int branchId;
    private Models.PizzaDetail detail;
    private Integer sizeCm;
    private Integer baseOptionId;
    private final Set<Integer> extras = new HashSet<>();
    private final List<Binding> bindings = new ArrayList<>();
    private int qty = 1;
    private boolean refreshing, adding;

    private MaterialToolbar toolbar;
    private View content, progress, errorGroup, lblBase, lblExtras;
    private TextView tvError, tvIncludes, tvQty;
    private ChipGroup sizes;
    private LinearLayout bases, extrasBox;
    private TextInputEditText etNotes;
    private MaterialButton btnAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pizza_detail);
        toolbar = setupToolbar("Pizza");
        pizzaId = getIntent().getIntExtra(EXTRA_ID, -1);
        branchId = Prefs.get().getBranch().id;

        content = findViewById(R.id.content);
        progress = findViewById(R.id.progress);
        errorGroup = findViewById(R.id.errorGroup);
        tvError = findViewById(R.id.tvError);
        tvIncludes = findViewById(R.id.tvIncludes);
        sizes = findViewById(R.id.sizes);
        lblBase = findViewById(R.id.lblBase);
        bases = findViewById(R.id.bases);
        lblExtras = findViewById(R.id.lblExtras);
        extrasBox = findViewById(R.id.extras);
        etNotes = findViewById(R.id.etNotes);
        tvQty = findViewById(R.id.tvQty);
        btnAdd = findViewById(R.id.btnAdd);

        findViewById(R.id.btnMinus).setOnClickListener(v -> { if (qty > 1) { qty--; updateBar(); } });
        findViewById(R.id.btnPlus).setOnClickListener(v -> { if (qty < 50) { qty++; updateBar(); } });
        findViewById(R.id.btnRetry).setOnClickListener(v -> load(null));
        btnAdd.setOnClickListener(v -> addToCart());

        btnAdd.setEnabled(false);
        load(null);
    }

    // ------------------------------------------------------------------ loading
    private void load(Integer size) {
        if (detail == null) {
            Ui.show(progress, true); Ui.show(errorGroup, false); Ui.show(content, false);
        } else {
            refreshing = true;
            updateBar();
        }
        Repo.get().pizzaDetail(branchId, pizzaId, size, new Repo.Result<Models.PizzaDetail>() {
            @Override public void onSuccess(Models.PizzaDetail d) {
                if (dead()) return;
                refreshing = false;
                detail = d;
                chooseDefaults(d, size);
                render();
            }
            @Override public void onError(String message, int code) {
                if (dead()) return;
                refreshing = false;
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

    private void chooseDefaults(Models.PizzaDetail d, Integer requestedSize) {
        List<Models.SizePrice> list = Models.nz(d.availableSizes);
        Integer chosen = requestedSize;
        if (chosen == null) {
            for (Models.SizePrice s : list) {
                if (Math.abs(s.basePrice - d.basePriceForSelectedSize) < 0.001) { chosen = s.sizeCm; break; }
            }
        }
        if (chosen == null && !list.isEmpty()) chosen = list.get(0).sizeCm;
        sizeCm = chosen;

        // keep the chosen base if it still exists for this size, otherwise pick the cheapest
        boolean exists = false;
        Models.PizzaBaseOption cheapest = null;
        for (Models.PizzaBaseOption b : Models.nz(d.baseOptions)) {
            if (baseOptionId != null && b.pizzaBaseOptionId == baseOptionId) exists = true;
            if (cheapest == null || b.extraPrice < cheapest.extraPrice) cheapest = b;
        }
        if (!exists) baseOptionId = cheapest == null ? null : cheapest.pizzaBaseOptionId;
    }

    // ------------------------------------------------------------------ rendering
    private void render() {
        final Models.PizzaDetail d = detail;
        toolbar.setTitle(d.name);

        List<String> names = new ArrayList<>();
        for (Models.ToppingItem t : Models.nz(d.defaultToppings)) names.add(t.name);
        tvIncludes.setText(names.isEmpty() ? "" : "Comes with " + String.join(", ", names));
        Ui.show(tvIncludes, !names.isEmpty());

        // sizes
        sizes.removeAllViews();
        for (final Models.SizePrice s : Models.nz(d.availableSizes)) {
            Chip chip = (Chip) LayoutInflater.from(this).inflate(R.layout.chip_filter, sizes, false);
            chip.setText(s.sizeCm + "cm · " + Money.rand(s.basePrice));
            chip.setChecked(sizeCm != null && sizeCm == s.sizeCm);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked && !refreshing && (sizeCm == null || sizeCm != s.sizeCm)) {
                    sizeCm = s.sizeCm;
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
        for (final Models.PizzaBaseOption b : baseList) {
            CompoundButton row = Ui.choiceRow(this, b.name, b.extraPrice, true, true);
            row.setOnClickListener(v -> { baseOptionId = b.pizzaBaseOptionId; sync(); });
            bindings.add(new Binding(row, () -> baseOptionId != null && baseOptionId == b.pizzaBaseOptionId));
            bases.addView(row);
        }

        // extra toppings (anything that isn't already on the pizza)
        extrasBox.removeAllViews();
        Set<Integer> defaultIds = new HashSet<>();
        for (Models.ToppingItem t : Models.nz(d.defaultToppings)) defaultIds.add(t.toppingId);
        List<Models.ToppingItem> extraList = new ArrayList<>();
        for (Models.ToppingItem t : Models.nz(d.allToppings)) if (!defaultIds.contains(t.toppingId)) extraList.add(t);
        Ui.show(lblExtras, !extraList.isEmpty());
        Ui.show(extrasBox, !extraList.isEmpty());
        for (final Models.ToppingItem t : extraList) {
            CompoundButton row = Ui.choiceRow(this, t.name, extraPrice(t.toppingId), false, true);
            row.setOnClickListener(v -> {
                if (!extras.remove(t.toppingId)) extras.add(t.toppingId);
                sync();
            });
            bindings.add(new Binding(row, () -> extras.contains(t.toppingId)));
            extrasBox.addView(row);
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
        if (detail == null) return 0.0;
        double total = detail.basePriceForSelectedSize;
        for (Models.PizzaBaseOption b : Models.nz(detail.baseOptions)) {
            if (baseOptionId != null && b.pizzaBaseOptionId == baseOptionId) total += b.extraPrice;
        }
        for (int id : extras) total += extraPrice(id);
        return total;
    }

    private void updateBar() {
        tvQty.setText(String.valueOf(qty));
        btnAdd.setText(refreshing ? "Updating prices…" : "Add to cart · " + Money.rand(unitPrice() * qty));
        btnAdd.setEnabled(detail != null && sizeCm != null && !refreshing && !adding);
    }

    // ------------------------------------------------------------------ add to cart
    private void addToCart() {
        if (detail == null || sizeCm == null) return;
        adding = true;
        updateBar();
        Models.QuoteRequest q = new Models.QuoteRequest();
        q.sizeCm = sizeCm;
        q.selectedToppingIds = new ArrayList<>(extras);
        q.pizzaBaseOptionId = baseOptionId;
        Repo.get().quote(branchId, pizzaId, q, new Repo.Result<Models.QuoteResponse>() {
            @Override public void onSuccess(Models.QuoteResponse r) {
                if (dead()) return;
                complete(r == null ? null : r.total);
            }
            @Override public void onError(String message, int code) {
                if (dead()) return;
                complete(null);   // fall back to the local estimate
            }
        });
    }

    private void complete(Double quoted) {
        double price = quoted != null && quoted > 0 ? quoted : unitPrice();
        List<String> parts = new ArrayList<>();
        parts.add(sizeCm + "cm");
        for (Models.PizzaBaseOption b : Models.nz(detail.baseOptions)) {
            if (baseOptionId != null && b.pizzaBaseOptionId == baseOptionId) parts.add(b.name);
        }
        for (Models.ToppingItem t : Models.nz(detail.allToppings)) {
            if (extras.contains(t.toppingId)) parts.add("+ " + t.name);
        }

        Models.OrderItemRequest req = new Models.OrderItemRequest();
        req.pizzaId = pizzaId;
        req.sizeCm = sizeCm;
        req.pizzaBaseOptionId = baseOptionId;
        req.quantity = 1;
        for (int id : extras) req.customizations.add(new Models.Customization(id, 1, null));
        String notes = Ui.text(etNotes);
        req.notes = notes.isEmpty() ? null : notes;

        Cart.get().add(new Cart.Line(detail.name, String.join(" · ", parts), price, qty, req));
        Ui.toast(this, "Added to cart");
        finish();
    }
}
