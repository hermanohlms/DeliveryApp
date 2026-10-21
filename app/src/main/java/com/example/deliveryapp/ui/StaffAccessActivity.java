package com.example.deliveryapp.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.deliveryapp.data.AccountRole;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.google.android.material.button.MaterialButton;

public class StaffAccessActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24); box.setPadding(pad, pad, pad, pad);
        TextView message = new TextView(this);
        message.setText("Manager and admin accounts use the staff web app."); message.setTextSize(22); box.addView(message);
        MaterialButton web = new MaterialButton(this); web.setText("Open staff web app"); box.addView(web);
        web.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://crowdcam.co.za/admin/login"))); }
            catch (android.content.ActivityNotFoundException e) { Ui.toast(this, "No browser is available."); }
        });
        MaterialButton logout = new MaterialButton(this); logout.setText("Sign out"); box.addView(logout);
        logout.setOnClickListener(v -> { Repo.get().logout(); SessionRouter.open(this); });
        setContentView(box);
    }
    @Override protected void onStart() {
        super.onStart();
        if (!dead() && Prefs.get().getRole() != AccountRole.STAFF) SessionRouter.open(this);
    }
}
