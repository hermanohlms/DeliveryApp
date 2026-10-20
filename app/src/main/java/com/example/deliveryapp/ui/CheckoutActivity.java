package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.deliveryapp.BuildConfig;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Branch;
import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.example.deliveryapp.util.Money;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CheckoutActivity extends BaseActivity {
    /** After PayFast sends the customer back, wait up to this many checks (2 s apart) for the server's ITN. */
    private static final int PAYMENT_CHECKS = 8;
    private static final long PAYMENT_CHECK_DELAY_MS = 2000;

    private TextInputEditText etName, etPhone, etComplex, etGate;
    private AddressPickerView address;
    private View deliveryGroup;
    private Chip chipDelivery, chipPayFast, chipCash;
    private TextView tvMessage, tvPriceNote;
    private MaterialButton btnPlace;

    private String lastKey;
    private int lastHash;

    /** The order being placed (kept while the customer is on the PayFast page). */
    private static final class Pending {
        Models.OrderRequest req;
        Branch branch;
        List<Cart.Line> lines;
        double estimate;
        boolean delivery;
        Models.OrderResponse order;
    }
    private Pending pending;

    private final ActivityResultLauncher<Intent> payFast = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), res -> {
                if (pending == null || pending.order == null) { paymentNotDone(null); return; }
                Intent data = res.getData();
                boolean returned = data != null
                        && PayFastActivity.OUTCOME_RETURNED.equals(data.getStringExtra(PayFastActivity.EXTRA_OUTCOME));
                // even after "cancel", ask once: the customer may have paid just before leaving the page
                checkPayment(returned ? PAYMENT_CHECKS : 1, returned);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        setupToolbar("Checkout");

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etComplex = findViewById(R.id.etComplex);
        etGate = findViewById(R.id.etGate);
        address = findViewById(R.id.checkoutAddress);
        deliveryGroup = findViewById(R.id.deliveryGroup);
        chipDelivery = findViewById(R.id.chipDelivery);
        chipPayFast = findViewById(R.id.chipPayFast);
        chipCash = findViewById(R.id.chipCash);
        tvMessage = findViewById(R.id.tvMessage);
        tvPriceNote = findViewById(R.id.tvPriceNote);
        btnPlace = findViewById(R.id.btnPlace);

        ((Chip) findViewById(R.id.chipPickup)).setText("Pick up at " + Prefs.get().getBranch().displayName);
        chipDelivery.setOnCheckedChangeListener((b, checked) -> {
            Ui.show(deliveryGroup, checked);
            updateTotal();
        });
        chipPayFast.setOnCheckedChangeListener((b, checked) -> updateTotal());
        if (!BuildConfig.PAYFAST_ENABLED) {
            chipCash.setChecked(true);
            Ui.show(findViewById(R.id.paymentGroup), false);
        }

        // pre-fill from the saved profile
        Models.Profile p = Prefs.get().getProfile();
        etName.setText(p.fullName());
        etPhone.setText(p.phone);
        etComplex.setText(p.complexName != null ? p.complexName : p.address != null ? p.address.complexName : null);
        address.setSelection(p.address);

        buildSummary();
        updateTotal();
        btnPlace.setOnClickListener(v -> place());
    }

    private void buildSummary() {
        LinearLayout box = findViewById(R.id.summary);
        box.removeAllViews();
        for (Cart.Line l : Cart.get().lines()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            TextView name = new TextView(this);
            name.setText(l.quantity + "× " + l.name);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView price = new TextView(this);
            price.setText(Money.rand(l.unitPrice * l.quantity));
            row.addView(name);
            row.addView(price);
            box.addView(row);
        }
        LinearLayout total = new LinearLayout(this);
        total.setOrientation(LinearLayout.HORIZONTAL);
        total.setPadding(0, Ui.dp(this, 8), 0, 0);
        TextView label = new TextView(this);
        label.setText("Estimated total");
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = new TextView(this);
        value.setText(Money.rand(Cart.get().total()));
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
        total.addView(label);
        total.addView(value);
        box.addView(total);
    }

    private boolean payOnline() { return BuildConfig.PAYFAST_ENABLED && chipPayFast.isChecked(); }

    private void updateTotal() {
        chipCash.setText(chipDelivery.isChecked() ? "Cash on delivery" : "Pay at the counter");
        tvPriceNote.setText(payOnline()
                ? "PayFast shows the exact amount (including any delivery charge) before you pay."
                : "The restaurant confirms the final price (including any delivery charge) when it accepts your order.");
        btnPlace.setText((payOnline() ? "Pay with PayFast · " : "Place order · ") + Money.rand(Cart.get().total()));
    }

    private void message(String text) {
        tvMessage.setText(text);
        tvMessage.setTextColor(MaterialColors.getColor(tvMessage, com.google.android.material.R.attr.colorError));
        Ui.show(tvMessage, text != null);
    }

    private String validate(boolean delivery) {
        if (Cart.get().isEmpty()) return "Your cart is empty.";
        if (Ui.text(etName).isEmpty()) return "Enter your name.";
        if (!Ui.PHONE.matcher(Ui.text(etPhone)).matches()) return "Enter a valid phone number (7–20 digits).";
        if (delivery && !address.isVerified()) return "Pick your delivery address from the suggestions.";
        if (!Ui.GATE.matcher(Ui.text(etGate)).matches()) return "Gate code may only contain letters, numbers, spaces, # and *.";
        return null;
    }

    private void place() {
        final boolean delivery = chipDelivery.isChecked();
        final boolean online = payOnline();
        String problem = validate(delivery);
        if (problem != null) { message(problem); return; }
        message(null);
        Ui.hideKeyboard(btnPlace);

        final String complex = Ui.text(etComplex).isEmpty() ? null : Ui.text(etComplex);
        final String gate = Ui.text(etGate).isEmpty() ? null : Ui.text(etGate);

        pending = new Pending();
        pending.branch = Prefs.get().getBranch();
        pending.lines = Cart.get().lines();
        pending.estimate = Cart.get().total();
        pending.delivery = delivery;

        final Models.OrderRequest req = new Models.OrderRequest();
        req.customerName = Ui.text(etName);
        req.phone = Ui.text(etPhone);
        req.branchName = pending.branch.displayName;
        req.orderType = delivery ? "delivery" : "pickup";
        req.complexName = complex;
        req.gateAccessCode = gate;
        req.paymentMethod = online ? "payfast" : "cash";
        if (delivery) {
            req.deliveryAddress = address.getSelection().copy();
            req.deliveryAddress.complexName = complex;
        }
        req.items = new ArrayList<>();
        for (Cart.Line l : pending.lines) req.items.add(l.request.copyWithQuantity(l.quantity));
        pending.req = req;

        // Same request => same idempotency key, so retrying after a timeout (or a cancelled payment) can't create a duplicate order.
        int hash = new Gson().toJson(req).hashCode();
        if (lastKey == null || hash != lastHash) {
            lastHash = hash;
            lastKey = Repo.get().newIdempotencyKey();
        }

        btnPlace.setEnabled(false);
        if (online) startPayFast();
        else placeCashOrder();
    }

    private void placeCashOrder() {
        btnPlace.setText("Placing order…");
        Repo.get().placeOrder(lastKey, pending.req, new Repo.Result<Models.OrderResponse>() {
            @Override public void onSuccess(Models.OrderResponse r) {
                if (dead()) return;
                pending.order = r;
                finishOrder(null);
            }
            @Override public void onError(String m, int code) {
                if (dead()) return;
                paymentNotDone(m);
            }
        });
    }

    private void startPayFast() {
        btnPlace.setText("Opening PayFast…");
        Repo.get().payFastCheckout(lastKey, pending.req, new Repo.Result<Models.PayFastCheckoutResponse>() {
            @Override public void onSuccess(Models.PayFastCheckoutResponse r) {
                if (dead()) return;
                if (r == null || r.order == null) { paymentNotDone("Something went wrong starting the payment. Please try again."); return; }
                pending.order = r.order;
                if (r.payment == null || r.payment.processUrl == null) {   // demo mode: no PayFast page
                    checkPayment(1, true);
                    return;
                }
                Intent i = new Intent(CheckoutActivity.this, PayFastActivity.class);
                i.putExtra(PayFastActivity.EXTRA_SESSION, new Gson().toJson(r.payment));
                payFast.launch(i);
            }
            @Override public void onError(String m, int code) {
                if (dead()) return;
                paymentNotDone(m);
            }
        });
    }

    /** Asks the server whether PayFast's ITN has marked the order paid; the redirect alone proves nothing. */
    private void checkPayment(final int checksLeft, final boolean returned) {
        btnPlace.setEnabled(false);
        btnPlace.setText("Confirming payment…");
        Repo.get().paymentStatus(pending.order.id, new Repo.Result<Models.PaymentStatus>() {
            @Override public void onSuccess(Models.PaymentStatus s) {
                if (dead()) return;
                String status = s == null || s.status == null ? "PENDING" : s.status.toUpperCase(Locale.ROOT);
                boolean over = "FAILED".equals(status) || "CANCELLED".equals(status);
                if ("PAID".equals(status)) finishOrder(ConfirmationActivity.PAYMENT_PAID);
                else if (!over && checksLeft > 1) {
                    btnPlace.postDelayed(() -> { if (!dead()) checkPayment(checksLeft - 1, returned); }, PAYMENT_CHECK_DELAY_MS);
                }
                else if (!over && returned) finishOrder(ConfirmationActivity.PAYMENT_PENDING);
                else paymentNotDone("FAILED".equals(status)
                            ? "PayFast couldn't take the payment. Please try again or choose cash."
                            : "Payment wasn't completed. Tap Pay to try again.");
            }
            @Override public void onError(String m, int code) {
                if (dead()) return;
                // PayFast sent the customer back as paid; the ITN will settle it on the server
                if (returned) finishOrder(ConfirmationActivity.PAYMENT_PENDING);
                else paymentNotDone("Payment wasn't completed. Tap Pay to try again.");
            }
        });
    }

    /** Back on the checkout form; the idempotency key is kept so trying again reuses the same order. */
    private void paymentNotDone(String text) {
        btnPlace.setEnabled(true);
        updateTotal();
        message(text);
    }

    private void finishOrder(String payment) {
        Models.OrderResponse r = pending.order;
        Models.PastOrder past = new Models.PastOrder();
        past.id = r.id;
        past.branchName = pending.branch.displayName;
        past.orderType = pending.req.orderType;
        past.placedAtMillis = System.currentTimeMillis();
        past.estimatedTotal = pending.estimate;
        past.status = r.status != null ? r.status : "Pending";
        past.deliveryAddress = pending.req.deliveryAddress;
        past.lines = new ArrayList<>();
        for (Cart.Line l : pending.lines) {
            past.lines.add(l.quantity + "× " + l.name + (l.detail.isEmpty() ? "" : " (" + l.detail + ")"));
        }
        Prefs.get().addOrder(past);
        rememberDetails(pending.req, pending.delivery);
        Cart.get().clear();
        lastKey = null;

        Intent i = new Intent(CheckoutActivity.this, ConfirmationActivity.class);
        i.putExtra(ConfirmationActivity.EXTRA_ORDER_ID, r.id);
        i.putExtra(ConfirmationActivity.EXTRA_TYPE, pending.req.orderType);
        i.putExtra(ConfirmationActivity.EXTRA_BRANCH, pending.branch.displayName);
        i.putExtra(ConfirmationActivity.EXTRA_PAYMENT, payment);
        startActivity(i);
        finish();
    }

    /** Save name/phone/address so next checkout is pre-filled (kept on the phone). */
    private void rememberDetails(Models.OrderRequest req, boolean delivery) {
        Models.Profile p = Prefs.get().getProfile();
        if (p.firstName == null || p.firstName.isEmpty()) {
            String[] parts = req.customerName.split("\\s+", 2);
            p.firstName = parts[0];
            p.lastName = parts.length > 1 ? parts[1] : "";
        }
        p.phone = req.phone;
        p.complexName = req.complexName;
        if (delivery) p.address = req.deliveryAddress;
        Prefs.get().saveProfile(p);
    }
}
