package com.example.deliveryapp.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Branch;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.google.android.material.card.MaterialCardView;

/** Native Pete’s Pizza landing screen with branch and session-aware actions. */
public class LandingActivity extends BaseActivity {
    @Override protected boolean requiresLogin() { return false; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_landing);
        MaterialCardView kenridge = findViewById(R.id.cardKenridge);
        MaterialCardView uitzicht = findViewById(R.id.cardUitzicht);
        kenridge.setOnClickListener(v -> selectBranch(Branch.KENRIDGE, kenridge, uitzicht));
        uitzicht.setOnClickListener(v -> selectBranch(Branch.UITZICHT, kenridge, uitzicht));
        findViewById(R.id.btnKenridgeMap).setOnClickListener(v -> openMap("Petes Pizza Kenridge"));
        findViewById(R.id.btnUitzichtMap).setOnClickListener(v -> openMap("Petes Pizza Uitzicht"));
        findViewById(R.id.btnLogin).setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.btnRegister).setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.btnOrder).setOnClickListener(v -> openOrdering());
        findViewById(R.id.btnLogout).setOnClickListener(v -> { Repo.get().logout(); updateAccountActions(); });
        updateAccountActions();
        updateBranchSelection(kenridge, uitzicht);
    }

    private void selectBranch(Branch branch, MaterialCardView kenridge, MaterialCardView uitzicht) {
        Prefs.get().setBranch(branch);
        updateBranchSelection(kenridge, uitzicht);
    }

    private void updateBranchSelection(MaterialCardView kenridge, MaterialCardView uitzicht) {
        boolean selected = Prefs.get().getBranch() == Branch.KENRIDGE;
        kenridge.setStrokeWidth(Ui.dp(this, selected ? 2 : 1));
        uitzicht.setStrokeWidth(Ui.dp(this, selected ? 1 : 2));
        kenridge.setStrokeColor(getColor(selected ? R.color.terracotta : R.color.outline));
        uitzicht.setStrokeColor(getColor(selected ? R.color.outline : R.color.terracotta));
    }

    private void updateAccountActions() {
        boolean signedIn = Prefs.get().isLoggedIn();
        findViewById(R.id.guestActions).setVisibility(signedIn ? View.GONE : View.VISIBLE);
        findViewById(R.id.memberActions).setVisibility(signedIn ? View.VISIBLE : View.GONE);
        Models.Profile profile = Prefs.get().getProfile();
        String name = profile.firstName == null || profile.firstName.trim().isEmpty()
                ? Prefs.get().getUsername() : profile.firstName.trim();
        ((TextView) findViewById(R.id.tvWelcome)).setText("Welcome back, " + name);
    }

    private void openMap(String query) {
        Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
        try { startActivity(map); }
        catch (ActivityNotFoundException e) { Ui.toast(this, "No map app is available on this device."); }
    }

    private void openOrdering() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
