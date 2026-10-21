package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.DemoData;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;


public class LoginActivity extends BaseActivity {
    private TextInputEditText etEmail, etPassword;
    private TextView tvMessage;
    private MaterialButton btnLogin;

    @Override protected boolean requiresLogin() { return false; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Prefs.get().isLoggedIn()) { openLanding(); return; }
        setContentView(R.layout.activity_login);
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent home = new Intent(this, LandingActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvMessage = findViewById(R.id.tvMessage);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> login());
        findViewById(R.id.btnForgot).setOnClickListener(v -> forgotPassword());
        View demoHint = findViewById(R.id.tvDemoHint);
        View demoButton = findViewById(R.id.btnDemo);
        Ui.show(demoHint, DemoData.ENABLED);
        Ui.show(demoButton, DemoData.ENABLED);
        demoButton.setOnClickListener(v -> {
            etEmail.setText(DemoData.EMAIL);
            etPassword.setText(DemoData.PASSWORD);
            login();
        });
        findViewById(R.id.btnRegister).setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void openLanding() {
        SessionRouter.open(this);
    }

    private void message(String text, boolean error) {
        tvMessage.setText(text);
        tvMessage.setTextColor(error
                ? com.google.android.material.color.MaterialColors.getColor(tvMessage, com.google.android.material.R.attr.colorError)
                : getResources().getColor(R.color.steel_blue, getTheme()));
        Ui.show(tvMessage, text != null);
    }

    private void login() {
        final String email = Ui.text(etEmail);
        final String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) { message("Enter your email and password.", true); return; }
        Ui.hideKeyboard(btnLogin);
        message(null, false);
        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in…");
        Repo.get().login(email, password, new Repo.Result<Models.AuthResponse>() {
            @Override public void onSuccess(Models.AuthResponse auth) {
                if (dead()) return;
                if (auth == null || com.example.deliveryapp.data.AccountRole.from(auth.roles)
                        == com.example.deliveryapp.data.AccountRole.UNKNOWN) {
                    reset();
                    message("Your account has no supported access role. Please contact your manager.", true);
                    return;
                }
                Prefs.get().saveSession(auth, email);
                openLanding();
            }
            @Override public void onError(String m, int code) {
                if (dead()) return;
                reset();
                message(m, true);
            }
        });
    }

    private void reset() {
        btnLogin.setEnabled(true);
        btnLogin.setText("Log in");
    }

    private void forgotPassword() {
        final EditText input = new EditText(this);
        input.setHint("Email");
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setText(Ui.text(etEmail));
        int pad = Ui.dp(this, 24);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset password")
                .setView(box)
                .setPositiveButton("Send link", (d, w) -> {
                    String email = input.getText().toString().trim();
                    if (email.isEmpty()) { message("Enter your email first.", true); return; }
                    Repo.get().forgotPassword(email, new Repo.Result<Models.MessageResponse>() {
                        @Override public void onSuccess(Models.MessageResponse r) {
                            if (dead()) return;
                            String m = r != null && r.message != null && !r.message.isEmpty()
                                    ? r.message : "If that email exists, a reset link has been sent.";
                            message(m, false);
                        }
                        @Override public void onError(String m, int code) {
                            if (dead()) return;
                            message(m, true);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
