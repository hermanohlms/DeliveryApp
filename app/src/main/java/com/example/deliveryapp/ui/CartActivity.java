package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.util.Money;

public class CartActivity extends BaseActivity {
    private CartAdapter adapter;
    private View footer, tvEmpty;
    private TextView tvTotal;
    private final Runnable listener = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);
        setupToolbar("Your cart · " + Prefs.get().getBranch().displayName);

        findViewById(R.id.btnBrowse).setOnClickListener(v -> {
            Intent menu = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(menu); finish();
        });
        footer = findViewById(R.id.footer);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvTotal = findViewById(R.id.tvTotal);
        adapter = new CartAdapter(line -> CartEditor.open(this, line));
        RecyclerView rv = findViewById(R.id.rvCart);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        findViewById(R.id.btnCheckout).setOnClickListener(v -> startActivity(new Intent(this, CheckoutActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Cart.get().addListener(listener);
        refresh();
    }

    @Override
    protected void onStop() {
        Cart.get().removeListener(listener);
        super.onStop();
    }

    private void refresh() {
        adapter.submit(Cart.get().lines());
        boolean empty = Cart.get().isEmpty();
        Ui.show(tvEmpty, empty);
        Ui.show(footer, !empty);
        tvTotal.setText(Money.rand(Cart.get().total()));
        ((TextView)findViewById(R.id.tvCartCount)).setText(Cart.get().count() + (Cart.get().count() == 1 ? " item" : " items") + " · Made your way");
    }
}
