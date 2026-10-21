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
import com.example.deliveryapp.util.Money;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MenuFragment extends Fragment {
    private static final String ALL = "All", PIZZAS = "Pizzas", SPECIALS = "Specials";

    private MenuAdapter adapter;
    private ChipGroup chips;
    private View progress, errorGroup, tvEmpty;
    private TextView tvError;

    private Models.PizzaCategorySplit pizzas = new Models.PizzaCategorySplit();
    private List<Models.MenuItem> items = new ArrayList<>();
    private List<Models.Special> specials = new ArrayList<>();
    private String selected = ALL;
    private String query = "";
    private int generation;
    private int done;
    private Models.PizzaCategorySplit pizzaResult;
    private List<Models.MenuItem> menuResult;
    private List<Models.Special> specialResult;
    private String pizzaError, menuError, specialError;
    private final Map<Integer, Models.PizzaDetail> guestPizzaDetails = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) { selected = savedInstanceState.getString("category", ALL); query = savedInstanceState.getString("query", ""); }
        ((TextView)view.findViewById(R.id.etSearch)).setText(query);
        chips = view.findViewById(R.id.chips);
        progress = view.findViewById(R.id.progress);
        errorGroup = view.findViewById(R.id.errorGroup);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvError = view.findViewById(R.id.tvError);
        Ui.show(view.findViewById(R.id.tvGuestNotice), !Prefs.get().isLoggedIn());
        view.findViewById(R.id.btnBackHome).setOnClickListener(v -> {
            Intent home = new Intent(requireContext(), LandingActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            requireActivity().finish();
        });

        adapter = new MenuAdapter(new MenuAdapter.Listener() {
            @Override public void onPizza(int pizzaId) {
                if (!Prefs.get().isLoggedIn()) { showGuestPizza(pizzaId); return; }
                Intent i = new Intent(requireContext(), PizzaDetailActivity.class);
                i.putExtra(PizzaDetailActivity.EXTRA_ID, pizzaId);
                startActivity(i);
            }
            @Override public void onItem(int itemId) {
                if (!Prefs.get().isLoggedIn()) { showGuestOrderPrompt(); return; }
                Intent i = new Intent(requireContext(), ItemDetailActivity.class);
                i.putExtra(ItemDetailActivity.EXTRA_ID, itemId);
                startActivity(i);
            }
            @Override public void onSpecial(long specialId) {
                if (!Prefs.get().isLoggedIn()) { showGuestOrderPrompt(); return; }
                Intent i = new Intent(requireContext(), SpecialDetailActivity.class);
                i.putExtra(SpecialDetailActivity.EXTRA_ID, specialId);
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

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        out.putString("category", selected); out.putString("query", query); super.onSaveInstanceState(out);
    }

    /** Called by MainActivity after the customer switches branch. */
    public void reload() {
        guestPizzaDetails.clear();
        if (getView() != null) load(true);
    }

    private void showGuestOrderPrompt() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ready to order?")
                .setMessage("Log in to customize items and add them to your cart.")
                .setPositiveButton("Log in", (d, w) -> startActivity(new Intent(requireContext(), LoginActivity.class)))
                .setNegativeButton("Keep browsing", null)
                .show();
    }

    private void showGuestPizza(int pizzaId) {
        Models.PizzaDetail cached = guestPizzaDetails.get(pizzaId);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pizza sizes and prices")
                .setMessage(cached == null ? "Loading sizes…" : sizePrices(cached))
                .setPositiveButton("Log in to order", (d, w) -> startActivity(new Intent(requireContext(), LoginActivity.class)))
                .setNegativeButton("Keep browsing", null)
                .show();
        if (cached != null) return;
        final int branchId = Prefs.get().getBranch().id;
        Repo.get().pizzaDetail(branchId, pizzaId, null, new Repo.Result<Models.PizzaDetail>() {
            @Override public void onSuccess(Models.PizzaDetail detail) {
                if (!isAdded() || getView() == null || branchId != Prefs.get().getBranch().id) return;
                if (detail != null) guestPizzaDetails.put(pizzaId, detail);
                if (dialog.isShowing()) dialog.setMessage(detail == null ? "Sizes are unavailable right now." : sizePrices(detail));
            }
            @Override public void onError(String message, int code) {
                if (dialog.isShowing() && isAdded()) dialog.setMessage("Sizes are unavailable right now. Please try again later.");
            }
        });
    }

    private String sizePrices(Models.PizzaDetail detail) {
        List<Models.SizePrice> sizes = Models.nz(detail.availableSizes);
        if (sizes.isEmpty()) return "Sizes are unavailable right now.";
        StringBuilder text = new StringBuilder();
        for (Models.SizePrice size : sizes) {
            if (text.length() > 0) text.append("\n");
            text.append(size.sizeCm).append(" cm · ").append(Money.rand(size.basePrice));
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ loading
    private void load(boolean force) {
        final int gen = ++generation;
        final int branchId = Prefs.get().getBranch().id;
        Ui.show(progress, true);
        Ui.show(errorGroup, false);
        Ui.show(tvEmpty, false);
        done = 0; pizzaResult = null; menuResult = null; specialResult = null;
        pizzaError = null; menuError = null; specialError = null;

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
        Repo.get().specials(branchId, new Repo.Result<List<Models.Special>>() {
            @Override public void onSuccess(List<Models.Special> value) {
                if (stale(gen)) return;
                specialResult = value;
                oneDone(gen);
            }
            @Override public void onError(String message, int code) {
                if (stale(gen)) return;
                specialError = message;
                oneDone(gen);
            }
        });
    }

    private boolean stale(int gen) { return gen != generation || !isAdded() || getView() == null; }

    private void oneDone(int gen) {
        if (++done < 3) return;
        Ui.show(progress, false);
        if (pizzaResult == null && menuResult == null && specialResult == null) {
            tvError.setText(pizzaError != null ? pizzaError : menuError != null ? menuError : specialError);
            Ui.show(errorGroup, true);
            adapter.submit(new ArrayList<MenuAdapter.Row>());
            return;
        }
        // Show whatever loaded; one missing section is not fatal.
        pizzas = pizzaResult != null ? pizzaResult : new Models.PizzaCategorySplit();
        items = menuResult != null ? menuResult : new ArrayList<Models.MenuItem>();
        specials = specialResult != null ? specialResult : new ArrayList<Models.Special>();
        buildChips();
        render();
    }

    // ------------------------------------------------------------------ chips + list
    private void buildChips() {
        List<String> names = new ArrayList<>();
        names.add(ALL);
        if (!Models.nz(pizzas.favouritePizzas).isEmpty() || !Models.nz(pizzas.supremePizzas).isEmpty()) names.add(PIZZAS);
        if (!specials.isEmpty()) names.add(SPECIALS);
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
        if (selected.equals(ALL) || selected.equals(SPECIALS)) {
            List<Models.Special> matches = new ArrayList<>();
            for (Models.Special special : specials) {
                if (has(special.name, query) || has(special.description, query)) matches.add(special);
            }
            if (!matches.isEmpty()) {
                rows.add(MenuAdapter.Row.header("Specials"));
                for (Models.Special special : matches) rows.add(MenuAdapter.Row.special(special));
            }
        }
        if (!selected.equals(PIZZAS) && !selected.equals(SPECIALS)) {
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
