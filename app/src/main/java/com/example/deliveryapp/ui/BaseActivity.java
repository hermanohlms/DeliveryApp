package com.example.deliveryapp.ui;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.deliveryapp.App;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Prefs;
import com.google.android.material.appbar.MaterialToolbar;

public abstract class BaseActivity extends AppCompatActivity {

    /** Screens that need a signed-in customer (everything except login / register). */
    protected boolean requiresLogin() { return true; }

    @Override
    protected void onStart() {
        super.onStart();
        if (!(this instanceof DriverActivity) && !(this instanceof DriverHistoryActivity) && !(this instanceof StaffAccessActivity)
                && !(this instanceof LoginActivity) && SessionRouter.redirectNonCustomer(this)) return;
        if (requiresLogin() && !Prefs.get().isLoggedIn()) {
            App.goToLogin();
            finish();
        }
    }

    /** True once the activity can no longer show results (avoid touching views from late callbacks). */
    protected boolean dead() { return isFinishing() || isDestroyed(); }

    protected MaterialToolbar setupToolbar(String title) {
        MaterialToolbar tb = findViewById(R.id.toolbar);
        if (title != null) tb.setTitle(title);
        tb.setNavigationOnClickListener((View v) -> finish());
        return tb;
    }
}
