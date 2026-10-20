package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import com.example.deliveryapp.BuildConfig;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Branch;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.regex.Pattern;

/** Creates a customer account inside the app (needs the NEW POST /api/auth/register endpoint on the server). */
public class RegisterActivity extends BaseActivity {
    private TextInputEditText etFirst, etLast, etEmail, etPhone, etPhone2, etPassword, etConfirm, etPostal;
    /**
     * Set to true to require a Google-verified address when creating an account.
     * false = the customer just types their address (no Google Maps / Places needed to sign up).
     * (Default: true = Google-validated address.)
     */
    private static final boolean USE_GOOGLE_ADDRESS = !BuildConfig.MAPS_API_KEY.trim().isEmpty();

    private TextInputEditText etHouse, etStreet, etArea, etCity;
    private View manualAddress;
    private AddressPickerView address;
    private RadioButton rbUitzicht;
    private TextView tvMessage;
    private MaterialButton btnCreate;

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    @Override protected boolean requiresLogin() { return false; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        setupToolbar("Create account");
        findViewById(R.id.btnLoginLink).setOnClickListener(v -> { startActivity(new Intent(this, LoginActivity.class)); finish(); });

        etFirst = findViewById(R.id.etFirstName);
        etLast = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPhone2 = findViewById(R.id.etPhone2);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);
        etPostal = findViewById(R.id.etPostal);
        address = findViewById(R.id.registerAddress);
        etHouse = findViewById(R.id.etHouse);
        etStreet = findViewById(R.id.etStreet);
        etArea = findViewById(R.id.etArea);
        etCity = findViewById(R.id.etCity);
        manualAddress = findViewById(R.id.manualAddress);
        Ui.show(manualAddress, !USE_GOOGLE_ADDRESS);
        Ui.show(address, USE_GOOGLE_ADDRESS);
        rbUitzicht = findViewById(R.id.rbUitzicht);
        ((RadioButton) findViewById(R.id.rbKenridge)).setChecked(Prefs.get().getBranch() == Branch.KENRIDGE);
        rbUitzicht.setChecked(Prefs.get().getBranch() == Branch.UITZICHT);
        tvMessage = findViewById(R.id.tvMessage);
        btnCreate = findViewById(R.id.btnCreate);

        // Google usually supplies the postal code; fill it in so the customer only fixes it if it's missing.
        address.setListener(sel -> {
            if (sel != null && sel.postalCode != null && Ui.text(etPostal).isEmpty()) etPostal.setText(sel.postalCode);
        });
        btnCreate.setOnClickListener(v -> submit());
    }

    private void message(String text) {
        tvMessage.setText(text);
        tvMessage.setTextColor(MaterialColors.getColor(tvMessage, com.google.android.material.R.attr.colorError));
        Ui.show(tvMessage, text != null);
    }

    private static boolean strongPassword(String p) {
        return p.length() >= 8 && UPPER.matcher(p).find() && LOWER.matcher(p).find()
                && DIGIT.matcher(p).find() && SPECIAL.matcher(p).find();
    }

    /** Returns the first problem with the form, or null when it's ready to send. */
    private String validate() {
        if (Ui.text(etFirst).isEmpty() || Ui.text(etLast).isEmpty()) return "Enter your first and last name.";
        String email = Ui.text(etEmail);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email address.";
        if (!Ui.PHONE.matcher(Ui.text(etPhone)).matches()) return "Enter a valid phone number (7–20 digits).";
        String p2 = Ui.text(etPhone2);
        if (!p2.isEmpty() && !Ui.PHONE.matcher(p2).matches()) return "The second phone number isn't valid.";
        String pw = etPassword.getText() == null ? "" : etPassword.getText().toString();
        String confirm = etConfirm.getText() == null ? "" : etConfirm.getText().toString();
        if (!strongPassword(pw)) {
            return "Password must be at least 8 characters and include uppercase, lowercase, number and a special character.";
        }
        if (!pw.equals(confirm)) return "The two passwords don't match.";
        if (USE_GOOGLE_ADDRESS) {
            if (!address.isVerified()) return "Pick your address from the suggestions.";
        } else {
            if (Ui.text(etStreet).isEmpty()) return "Enter your street.";
            if (Ui.text(etArea).isEmpty()) return "Enter your suburb.";
        }
        if (Ui.text(etPostal).isEmpty()) return "Enter your postal code.";
        return null;
    }

    private void submit() {
        String problem = validate();
        if (problem != null) { message(problem); return; }
        message(null);
        Ui.hideKeyboard(btnCreate);

        final String email = Ui.text(etEmail).toLowerCase(Locale.ROOT);
        final String password = etPassword.getText().toString();

        Models.AddressSelection a;
        if (USE_GOOGLE_ADDRESS) {
            a = address.getSelection().copy();
            if (a.area == null || a.area.trim().isEmpty()) a.area = a.city;   // backend requires an area
        } else {
            a = new Models.AddressSelection();   // typed by hand: no Google place id or coordinates
            a.houseNumber = Ui.text(etHouse).isEmpty() ? null : Ui.text(etHouse);
            a.street = Ui.text(etStreet);
            a.area = Ui.text(etArea);
            a.city = Ui.text(etCity).isEmpty() ? null : Ui.text(etCity);
            a.country = "South Africa";
            StringBuilder sb = new StringBuilder();
            for (String part : new String[]{a.houseNumber, a.street, a.area, a.city, Ui.text(etPostal)}) {
                if (part != null && !part.isEmpty()) { if (sb.length() > 0) sb.append(", "); sb.append(part); }
            }
            a.formattedAddress = sb.toString();
        }
        a.postalCode = Ui.text(etPostal);

        final Models.RegisterRequest req = new Models.RegisterRequest();
        req.firstName = Ui.text(etFirst);
        req.lastName = Ui.text(etLast);
        req.email = email;
        req.password = password;
        req.phone = Ui.text(etPhone);
        req.phone2 = Ui.text(etPhone2).isEmpty() ? null : Ui.text(etPhone2);
        req.preferredStore = (rbUitzicht.isChecked() ? Branch.UITZICHT : Branch.KENRIDGE).displayName;
        req.address = a;

        btnCreate.setEnabled(false);
        btnCreate.setText("Creating account…");
        Repo.get().register(req, new Repo.Result<Models.AuthResponse>() {
            @Override public void onSuccess(Models.AuthResponse auth) {
                if (dead()) return;
                Prefs.get().setBranch(Branch.fromName(req.preferredStore));
                if (auth != null && auth.accessToken != null) {
                    Prefs.get().saveSession(auth, email);
                    rememberProfile(req);
                    openLanding();
                } else {
                    // The server created the account but sent no token: sign in with the same details.
                    rememberProfile(req);
                    Repo.get().login(email, password, new Repo.Result<Models.AuthResponse>() {
                        @Override public void onSuccess(Models.AuthResponse a2) {
                            if (dead()) return;
                            Prefs.get().saveSession(a2, email);
                            openLanding();
                        }
                        @Override public void onError(String m, int code) {
                            if (dead()) return;
                            Ui.toast(RegisterActivity.this, "Account created. Please log in.");
                            finish();
                        }
                    });
                }
            }
            @Override public void onError(String m, int code) {
                if (dead()) return;
                btnCreate.setEnabled(true);
                btnCreate.setText("Create account");
                message(m);
            }
        });
    }

    private void rememberProfile(Models.RegisterRequest r) {
        Models.Profile p = new Models.Profile();
        p.firstName = r.firstName; p.lastName = r.lastName; p.email = r.email;
        p.phone = r.phone; p.phone2 = r.phone2; p.preferredStore = r.preferredStore; p.address = r.address;
        Prefs.get().saveProfile(p);
    }

    private void openLanding() {
        Intent i = new Intent(this, LandingActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
