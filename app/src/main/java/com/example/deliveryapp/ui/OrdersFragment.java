package com.example.deliveryapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.example.deliveryapp.util.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the customer's server order history, falling back to this phone's saved
 * orders when the network is unavailable.
 */
public class OrdersFragment extends Fragment {
    private SwipeRefreshLayout swipe;
    private OrdersAdapter adapter;
    private TextView tvNote, tvEmpty;
    private int generation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipe = view.findViewById(R.id.swipe);
        tvNote = view.findViewById(R.id.tvNote);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        adapter = new OrdersAdapter();
        RecyclerView rv = view.findViewById(R.id.rvOrders);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.terracotta);
        swipe.setOnRefreshListener(this::load);
        load();
    }

    /** Refresh whenever the tab becomes visible again (e.g. right after placing an order). */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) load();
    }

    private void load() {
        final int gen = ++generation;
        swipe.setRefreshing(true);
        Repo.get().myOrders(new Repo.Result<List<Models.OrderHistoryEntry>>() {
            @Override public void onSuccess(List<Models.OrderHistoryEntry> list) {
                if (gen != generation || !isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                showServer(Models.nz(list));
            }
            @Override public void onError(String message, int code) {
                if (gen != generation || !isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                if (code == 401) return;   // Repo already sent the user to the login screen
                showLocal(code == 0 ? "You're offline. Showing orders placed on this phone."
                        : "Showing orders placed on this phone. Live status will appear once the server is updated.");
            }
        });
    }

    private void showServer(List<Models.OrderHistoryEntry> list) {
        List<OrdersAdapter.OrderRow> rows = new ArrayList<>();
        for (Models.OrderHistoryEntry o : list) {
            OrdersAdapter.OrderRow r = new OrdersAdapter.OrderRow();
            r.id = o.id;
            r.status = o.status;
            r.meta = join(o.createdAt, o.branchName, capitalize(o.orderType));
            r.lines = String.join("\n", Models.nz(o.itemLines));
            r.total = o.total == null ? null : "Total " + Money.rand(o.total);
            r.trackable = isActive(o.status);
            rows.add(r);
        }
        tvNote.setVisibility(View.GONE);
        adapter.submit(rows);
        Ui.show(tvEmpty, rows.isEmpty());
    }

    private void showLocal(String note) {
        List<OrdersAdapter.OrderRow> rows = new ArrayList<>();
        for (Models.PastOrder p : Prefs.get().getHistory()) {
            OrdersAdapter.OrderRow r = new OrdersAdapter.OrderRow();
            r.id = p.id;
            r.status = p.status;
            r.meta = join(Money.dateTime(p.placedAtMillis), p.branchName, capitalize(p.orderType));
            r.lines = p.lines == null ? "" : String.join("\n", p.lines);
            r.total = "Estimated " + Money.rand(p.estimatedTotal);
            r.trackable = isActive(p.status);
            rows.add(r);
        }
        tvNote.setText(note);
        Ui.show(tvNote, true);
        adapter.submit(rows);
        Ui.show(tvEmpty, rows.isEmpty());
    }

    /** Not yet delivered / collected / cancelled. */
    private static boolean isActive(String status) {
        int step = TrackOrderActivity.step(status);
        return step >= 0 && step < 3;
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p);
        }
        return sb.toString();
    }
}
