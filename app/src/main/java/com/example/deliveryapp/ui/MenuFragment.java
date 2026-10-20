package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MenuFragment extends Fragment {
    private static final String ALL = "All", PIZZAS = "Pizzas";

    private MenuAdapter adapter;
    private ChipGroup chips;
    private View progress, errorGroup, tvEmpty;
    private TextView tvError;

    private Models.PizzaCategorySplit pizzas = new Models.PizzaCategorySplit();
    private List<Models.MenuItem> items = new ArrayList<>();
    private String selected = ALL;
    private String query = "";
    private int generation;
    private int done;
    private Models.PizzaCategorySplit pizzaResult;
    private List<Models.MenuItem> menuResult;
    private String pizzaError, menuError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        chips = view.findViewById(R.id.chips);
        progress = view.findViewById(R.id.progress);
        errorGroup = view.findViewById(R.id.errorGroup);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvError = view.findViewById(R.id.tvError);

        adapter = new MenuAdapter(new MenuAdapter.Listener() {
            @Override public void onPizza(int pizzaId) {
                Intent i = new Intent(requireContext(), PizzaDetailActivity.class);
                i.putExtra(PizzaDetailActivity.EXTRA_ID, pizzaId);
                startActivity(i);
            }
            @Override public void onItem(int itemId) {
                Intent i = new Intent(requireContext(), ItemDetailActivity.class);
                i.putExtra(ItemDetailActivity.EXTRA_ID, itemId);
                startActivity(i);
            }
        });
        RecyclerView rv = view.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        ((TextView) view.findViewById(R.id.etSearch)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString().trim(); render(); }
        });
        view.findViewById(R.id.btnRetry).setOnClickListener(v -> load(true));

        load(false);
    }

    /** Called by MainActivity after the customer switches branch. */
    public void reload() {
        if (getView() != null) load(true);
    }

    // ------------------------------------------------------------------ loading
    private void load(boolean force) {
        final int gen = ++generation;
        final int branchId = Prefs.get().getBranch().id;
        Ui.show(progress, true);
        Ui.show(errorGroup, false);
        Ui.show(tvEmpty, false);
        done = 0; pizzaResult = null; menuResult = null; pizzaError = null; menuError = null;

        Repo.get().pizzas(branchId, new Repo.Result<Models.PizzaCategorySplit>() {
            @Override public void onSuccess(Models.PizzaCategorySplit v) {
                if (stale(gen)) return;
                pizzaResult = v == null ? new Models.PizzaCategorySplit() : v;
                oneDone(gen);
            }
            @Override public void onError(String message, int code) {
                if (stale(gen)) return;
                pizzaError = message;
                oneDone(gen);
            }
        });
        Repo.get().menu(branchId, force, new Repo.Result<List<Models.MenuItem>>() {
            @Override public void onSuccess(List<Models.MenuItem> v) {
                if (stale(gen)) return;
                menuResult = v;
                oneDone(gen);
            }
            @Override public void onError(String message, int code) {
                if (stale(gen)) return;
                menuError = message;
                oneDone(gen);
            }
        });
    }

    private boolean stale(int gen) { return gen != generation || !isAdded() || getView() == null; }

    private void oneDone(int gen) {
        if (++done < 2) return;
        Ui.show(progress, false);
        if (pizzaResult == null && menuResult == null) {
            tvError.setText(pizzaError != null ? pizzaError : menuError);
            Ui.show(errorGroup, true);
            adapter.submit(new ArrayList<MenuAdapter.Row>());
            return;
        }
        // Show whatever loaded; one missing section is not fatal.
        pizzas = pizzaResult != null ? pizzaResult : new Models.PizzaCategorySplit();
        items = menuResult != null ? menuResult : new ArrayList<Models.MenuItem>();
        buildChips();
        render();
    }

    // ------------------------------------------------------------------ chips + list
    private void buildChips() {
        List<String> names = new ArrayList<>();
        names.add(ALL);
        if (!Models.nz(pizzas.favouritePizzas).isEmpty() || !Models.nz(pizzas.supremePizzas).isEmpty()) names.add(PIZZAS);
        Set<String> cats = new LinkedHashSet<>();
        for (Models.MenuItem m : items) if (m.categoryName != null) cats.add(m.categoryName);
        names.addAll(cats);
        if (!names.contains(selected)) selected = ALL;

        chips.removeAllViews();
        for (final String name : names) {
            Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.chip_filter, chips, false);
            chip.setText(name);
            chip.setChecked(name.equals(selected));
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) { selected = name; render(); }
            });
            chips.addView(chip);
        }
    }

    private static boolean has(String text, String q) {
        return q.isEmpty() || (text != null && text.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)));
    }

    private void render() {
        if (adapter == null) return;
        List<MenuAdapter.Row> rows = new ArrayList<>();

        if (selected.equals(ALL) || selected.equals(PIZZAS)) {
            addPizzas(rows, "Favourite pizzas", Models.nz(pizzas.favouritePizzas));
            addPizzas(rows, "Supreme pizzas", Models.nz(pizzas.supremePizzas));
        }
        if (!selected.equals(PIZZAS)) {
            Map<String, List<Models.MenuItem>> grouped = new LinkedHashMap<>();
            for (Models.MenuItem m : items) {
                boolean inCategory = selected.equals(ALL) || selected.equals(m.categoryName);
                if (!inCategory) continue;
                if (!(has(m.name, query) || has(m.description, query))) continue;
                String cat = m.categoryName == null ? "Other" : m.categoryName;
                if (!grouped.containsKey(cat)) grouped.put(cat, new ArrayList<Models.MenuItem>());
                grouped.get(cat).add(m);
            }
            for (Map.Entry<String, List<Models.MenuItem>> e : grouped.entrySet()) {
                rows.add(MenuAdapter.Row.header(e.getKey()));
                for (Models.MenuItem m : e.getValue()) rows.add(MenuAdapter.Row.item(m));
            }
        }
        adapter.submit(rows);
        Ui.show(tvEmpty, rows.isEmpty());
    }

    private void addPizzas(List<MenuAdapter.Row> rows, String title, List<Models.PizzaCard> list) {
        List<Models.PizzaCard> match = new ArrayList<>();
        for (Models.PizzaCard p : list) if (has(p.name, query)) match.add(p);
        if (match.isEmpty()) return;
        rows.add(MenuAdapter.Row.header(title));
        for (Models.PizzaCard p : match) rows.add(MenuAdapter.Row.pizza(p));
    }
}
