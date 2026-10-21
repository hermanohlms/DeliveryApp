package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.*;
import com.example.deliveryapp.util.Money;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import java.util.List;

/** Sectioned menu customisation, used both for new items and editing existing cart lines. */
public class ItemDetailActivity extends BaseActivity {
    public static final String EXTRA_ID = "itemId";
    private int itemId, qty = 1;
    private String editId;
    private Models.MenuItem item;
    private List<Models.MenuItem> catalog;
    private MenuCustomization selection;
    private Models.OrderItemRequest initial;
    private MaterialToolbar toolbar;
    private TextView tvError, tvDesc, tvQty, tvHint, tvNotice;
    private LinearLayout sections, variants;
    private TextInputEditText etNotes;
    private MaterialButton btnAdd;
    private boolean changed;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_item_detail);
        Ui.configureAddBar(this);
        toolbar = setupToolbar("Customise your order");
        itemId = getIntent().getIntExtra(EXTRA_ID, -1);
        editId = getIntent().getStringExtra(CartEditor.EXTRA_LINE_ID);
        tvError = findViewById(R.id.tvError); tvDesc = findViewById(R.id.tvDesc);
        tvNotice = findViewById(R.id.tvNotice); tvQty = findViewById(R.id.tvQty); tvHint = findViewById(R.id.tvHint);
        sections = findViewById(R.id.sections); variants = findViewById(R.id.variants);
        etNotes = findViewById(R.id.etNotes); btnAdd = findViewById(R.id.btnAdd);
        Cart.Line line = editId == null ? null : Cart.get().find(editId);
        if (editId != null && line == null) { fail("This item is no longer in your cart. Go back to the menu to add it again."); return; }
        if (saved != null && saved.containsKey("draft")) {
            initial = new Gson().fromJson(saved.getString("draft"), Models.OrderItemRequest.class);
            qty = saved.getInt("quantity", 1); itemId = saved.getInt("itemId", itemId);
            changed = saved.getBoolean("changed");
        } else if (line != null) { initial = line.request.copyWithQuantity(1); qty = line.quantity; }
        else if (!getIntent().getBooleanExtra(CartEditor.EXTRA_USE_DEFAULTS, false)) {
            initial = new Gson().fromJson(getIntent().getStringExtra(CartEditor.EXTRA_INITIAL_REQUEST), Models.OrderItemRequest.class);
        }
        if (initial != null) etNotes.setText(initial.notes);
        findViewById(R.id.btnMinus).setOnClickListener(v -> { if (qty > 1) { qty--; updateBar(); } });
        findViewById(R.id.btnPlus).setOnClickListener(v -> { if (qty < 50) { qty++; updateBar(); } });
        if (forSpecial()) {
            findViewById(R.id.btnMinus).setVisibility(View.GONE);
            findViewById(R.id.btnPlus).setVisibility(View.GONE);
            tvQty.setVisibility(View.GONE);
        }
        findViewById(R.id.btnRetry).setOnClickListener(v -> load());
        btnAdd.setOnClickListener(v -> save()); btnAdd.setEnabled(false);
        load();
    }
    private boolean forSpecial() { return getIntent().getBooleanExtra(SpecialDetailActivity.EXTRA_CONFIGURE_FOR_SPECIAL, false); }
    private void fail(String message) {
        Ui.show(findViewById(R.id.progress), false); Ui.show(findViewById(R.id.content), false);
        tvError.setText(message); Ui.show(findViewById(R.id.errorGroup), true);
        btnAdd.setEnabled(false);
        Ui.show(findViewById(R.id.btnRetry), editId == null || Cart.get().find(editId) != null);
    }
    private void load() {
        Ui.show(findViewById(R.id.progress), true); Ui.show(findViewById(R.id.errorGroup), false);
        Repo.get().menu(Prefs.get().getBranch().id, editId != null, new Repo.Result<List<Models.MenuItem>>() {
            @Override public void onSuccess(List<Models.MenuItem> items) {
                if (dead()) return;
                catalog = items; selectVariant(itemId);
                if (selection != null && initial != null) {
                    changed |= selection.restore(initial); initial = null; render();
                }
            }
            @Override public void onError(String message, int code) { if (!dead()) fail(message); }
        });
    }
    private void selectVariant(int id) {
        Models.MenuItem next = CatalogOptions.find(catalog, id);
        if (next == null) { fail("That item is no longer available. Return to the menu to choose another."); return; }
        item = next; itemId = id; selection = new MenuCustomization(item, catalog); render();
    }
    private void render() {
        toolbar.setTitle(item.name);
        tvDesc.setText(item.description); Ui.show(tvDesc, item.description != null && !item.description.isBlank());
        tvNotice.setText("Some saved choices have changed. Please review your options before saving."); Ui.show(tvNotice, changed);
        ((TextView) findViewById(R.id.tvBasePrice)).setText(Money.rand(item.priceOrZero()));
        variants.removeAllViews();
        List<Models.MenuItem> family = CatalogOptions.variants(item, catalog);
        if (family.size() > 1 && !forSpecial()) {
            variants.addView(Ui.sectionTitle(this, "Choose an option"));
            variants.addView(Ui.sectionHint(this, "Required · Choose one"));
            for (Models.MenuItem variant : family) {
                CompoundButton row = Ui.absoluteChoiceRow(this, CatalogOptions.variantLabel(variant), variant.priceOrZero(), variant.menuItemId == itemId);
                row.setOnClickListener(v -> selectVariant(variant.menuItemId)); variants.addView(row);
            }
        }
        sections.removeAllViews();
        toppingSection("Choose your protein", "Required · Choose one", selection.proteins(), true);
        toppingSection("Default toppings", "Untick any topping you would like removed", selection.defaults(), false);
        toppingSection("Extras", "Optional · Make it your own", selection.extras(), false);
        for (Models.ModifierGroup group : selection.visibleGroups()) {
            sections.addView(Ui.sectionTitle(this, group.name));
            sections.addView(Ui.sectionHint(this, group.minRequired() > 0 ? "Required · Choose " + (group.maxAllowed() == 1 ? "one" : "at least " + group.minRequired()) : "Optional"));
            for (Models.ModifierOption option : Models.nz(group.options)) {
                CompoundButton row = Ui.choiceRow(this, option.name, option.price, group.maxAllowed() == 1, true);
                row.setChecked(selection.selected(group, option.optionId));
                row.setOnClickListener(v -> { selection.toggle(group, option.optionId); render(); }); sections.addView(row);
            }
        }
        Ui.show(findViewById(R.id.progress), false); Ui.show(findViewById(R.id.errorGroup), false);
        Ui.show(findViewById(R.id.content), true); updateBar();
    }
    private void toppingSection(String title, String hint, List<Models.BurgerTopping> values, boolean radio) {
        if (values.isEmpty()) return;
        sections.addView(Ui.sectionTitle(this, title)); sections.addView(Ui.sectionHint(this, hint));
        for (Models.BurgerTopping topping : values) {
            CompoundButton row = Ui.choiceRow(this, topping.name, topping.defaultSelected ? null : topping.price,
                    radio, !topping.defaultSelected || topping.isRemovable() || topping.isProtein());
            row.setChecked(selection.selected(topping.toppingId));
            row.setOnClickListener(v -> { selection.toggle(topping); render(); }); sections.addView(row);
        }
    }
    private void updateBar() {
        tvQty.setText(String.valueOf(qty));
        String problem = selection == null ? null : selection.problem();
        tvHint.setText(problem); Ui.show(tvHint, problem != null);
        String action = forSpecial() ? "Save choices" : editId == null ? "Add to cart" : "Update cart";
        btnAdd.setText(action + " · " + Money.rand(selection == null ? 0 : selection.unitPrice() * qty));
        btnAdd.setEnabled(selection != null && problem == null);
        findViewById(R.id.btnMinus).setEnabled(qty > 1); findViewById(R.id.btnPlus).setEnabled(qty < 50);
    }
    private void save() {
        if (selection == null || selection.problem() != null) return;
        Models.OrderItemRequest request = selection.request(Ui.text(etNotes));
        if (forSpecial()) {
            Intent result = new Intent().putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_REQUEST, new Gson().toJson(request))
                    .putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_DETAIL, selection.summary())
                    .putExtra(SpecialDetailActivity.EXTRA_CONFIGURED_PRICE, selection.unitPrice());
            setResult(RESULT_OK, result);
        } else if (!CartEditor.save(this, editId, new Cart.Line(item.name, selection.summary(), selection.unitPrice(), qty, request))) {
            fail("This item is no longer in your cart. Go back to the menu to add it again."); return;
        }
        finish();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (selection != null) out.putString("draft", new Gson().toJson(selection.request(Ui.text(etNotes))));
        out.putInt("quantity", qty); out.putInt("itemId", itemId); out.putBoolean("changed", changed);
        super.onSaveInstanceState(out);
    }
}
