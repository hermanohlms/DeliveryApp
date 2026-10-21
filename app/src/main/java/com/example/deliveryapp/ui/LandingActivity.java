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

/** Native Pete’s Pizza landing screen with branch and session-aware actions. */
public class LandingActivity extends BaseActivity {
    private boolean branchChoiceOpen;
    @Override protected boolean requiresLogin() { return false; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (SessionRouter.redirectNonCustomer(this)) return;
        setContentView(R.layout.activity_landing);
        branchChoiceOpen = state != null && state.getBoolean("branch_choice_open");
        findViewById(R.id.btnViewMenu).setOnClickListener(v -> {
            branchChoiceOpen = true;
            findViewById(R.id.branchChoice).setVisibility(View.VISIBLE);
        });
        findViewById(R.id.btnKenridge).setOnClickListener(v -> selectBranch(Branch.KENRIDGE));
        findViewById(R.id.btnUitzicht).setOnClickListener(v -> selectBranch(Branch.UITZICHT));
        findViewById(R.id.btnKenridgeMap).setOnClickListener(v -> openMap("Petes Pizza Kenridge"));
        findViewById(R.id.btnUitzichtMap).setOnClickListener(v -> openMap("Petes Pizza Uitzicht"));
        findViewById(R.id.btnLogin).setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.btnRegister).setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.btnOrder).setOnClickListener(v -> openOrdering());
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Repo.get().logout();
            branchChoiceOpen = false;
            updateAccountActions();
        });
        updateAccountActions();
    }

    @Override protected void onResume() {
        super.onResume();
        if (dead() || SessionRouter.redirectNonCustomer(this)) return;
        updateAccountActions();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("branch_choice_open", branchChoiceOpen);
        super.onSaveInstanceState(out);
    }

    private void selectBranch(Branch branch) {
        Prefs.get().setBranch(branch);
        openOrdering();
    }

    private void updateAccountActions() {
        boolean signedIn = Prefs.get().isLoggedIn();
        findViewById(R.id.guestActions).setVisibility(signedIn ? View.GONE : View.VISIBLE);
        findViewById(R.id.memberActions).setVisibility(signedIn ? View.VISIBLE : View.GONE);
        findViewById(R.id.branchChoice).setVisibility(signedIn || branchChoiceOpen ? View.VISIBLE : View.GONE);
        TextView welcome = findViewById(R.id.tvWelcome);
        welcome.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (signedIn) {
            Models.Profile profile = Prefs.get().getProfile();
            String name = profile.firstName == null ? "" : profile.firstName.trim();
            welcome.setText(name.isEmpty() ? "Welcome back!" : "Welcome back, " + name + "!");
        }
    }

    private void openMap(String query) {
        Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
        try { startActivity(map); }
        catch (ActivityNotFoundException e) { Ui.toast(this, "No map app is available on this device."); }
    }

    private void openOrdering() {
        startActivity(new Intent(this, MainActivity.class));
        if (Prefs.get().isLoggedIn()) finish();
    }
}
