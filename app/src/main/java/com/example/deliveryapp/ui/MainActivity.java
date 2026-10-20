package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Branch;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Prefs;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Hosts the three main tabs (Menu, Orders, Profile) under a branch switcher and cart button. */
public class MainActivity extends BaseActivity {
    private static final String TAG_MENU = "menu", TAG_ORDERS = "orders", TAG_PROFILE = "profile";

    private TextView tvBranch, tvBadge;
    private BottomNavigationView bottomNav;
    private final Runnable cartListener = this::updateBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvBranch = findViewById(R.id.tvBranch);
        tvBadge = findViewById(R.id.tvBadge);
        bottomNav = findViewById(R.id.bottomNav);
        tvBranch.setText(Prefs.get().getBranch().displayName);

        findViewById(R.id.branchSelector).setOnClickListener(v -> showBranchMenu());
        findViewById(R.id.cartButton).setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_MENU) == null) {
            fm.beginTransaction()
                    .add(R.id.fragmentContainer, new MenuFragment(), TAG_MENU)
                    .add(R.id.fragmentContainer, new OrdersFragment(), TAG_ORDERS)
                    .add(R.id.fragmentContainer, new ProfileFragment(), TAG_PROFILE)
                    .commitNow();
        }
        selectTab(bottomNav.getSelectedItemId());
        bottomNav.setOnItemSelectedListener(item -> { selectTab(item.getItemId()); return true; });
    }

    private void selectTab(int itemId) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment menu = fm.findFragmentByTag(TAG_MENU);
        Fragment orders = fm.findFragmentByTag(TAG_ORDERS);
        Fragment profile = fm.findFragmentByTag(TAG_PROFILE);
        Fragment target = itemId == R.id.nav_orders ? orders : itemId == R.id.nav_profile ? profile : menu;
        FragmentTransaction tx = fm.beginTransaction();
        for (Fragment f : new Fragment[]{menu, orders, profile}) {
            if (f == null) continue;
            if (f == target) tx.show(f); else tx.hide(f);
        }
        tx.commit();
    }

    // ------------------------------------------------------------------ branch switcher
    private void showBranchMenu() {
        PopupMenu popup = new PopupMenu(this, findViewById(R.id.branchSelector));
        for (Branch b : Branch.values()) popup.getMenu().add(Menu.NONE, b.id, Menu.NONE, b.displayName);
        popup.setOnMenuItemClickListener(item -> {
            Branch target = Branch.fromId(item.getItemId());
            if (target == Prefs.get().getBranch()) return true;
            if (Cart.get().isEmpty()) {
                applyBranch(target);
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Switch to " + target.displayName + "?")
                        .setMessage("Prices and availability differ per branch, so your cart will be emptied.")
                        .setPositiveButton("Switch", (d, w) -> applyBranch(target))
                        .setNegativeButton("Keep my cart", null)
                        .show();
            }
            return true;
        });
        popup.show();
    }

    private void applyBranch(Branch b) {
        Prefs.get().setBranch(b);
        Cart.get().clear();
        tvBranch.setText(b.displayName);
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_MENU);
        if (f instanceof MenuFragment) ((MenuFragment) f).reload();
    }

    // ------------------------------------------------------------------ cart badge
    private void updateBadge() {
        int n = Cart.get().count();
        tvBadge.setText(n > 99 ? "99+" : String.valueOf(n));
        Ui.show(tvBadge, n > 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Cart.get().addListener(cartListener);
        updateBadge();
    }

    @Override
    protected void onStop() {
        Cart.get().removeListener(cartListener);
        super.onStop();
    }
}
