package com.example.deliveryapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.deliveryapp.R;

public class ConfirmationActivity extends BaseActivity {
    public static final String EXTRA_ORDER_ID = "orderId";
    public static final String EXTRA_TYPE = "orderType";
    public static final String EXTRA_BRANCH = "branch";
    /** PAYMENT_PAID, PAYMENT_PENDING, or absent for cash orders */
    public static final String EXTRA_STATUS = "orderStatus";
    public static final String EXTRA_PAYMENT = "payment";
    public static final String PAYMENT_PAID = "paid";
    public static final String PAYMENT_PENDING = "pending";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmation);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { backToMenu(); }
        });

        long id = getIntent().getLongExtra(EXTRA_ORDER_ID, 0);
        String type = getIntent().getStringExtra(EXTRA_TYPE);
        String branch = getIntent().getStringExtra(EXTRA_BRANCH);
        String payment = getIntent().getStringExtra(EXTRA_PAYMENT);

        ((TextView) findViewById(R.id.tvOrderId)).setText("Order #" + id);
        ((TextView) findViewById(R.id.tvOrderMeta)).setText((branch == null ? "Restaurant" : branch) + " · " + ("pickup".equals(type) ? "Pickup" : "Delivery"));
        String status = getIntent().getStringExtra(EXTRA_STATUS);
        ((TextView) findViewById(R.id.tvOrderStatus)).setText(PAYMENT_PENDING.equals(payment) ? "Awaiting payment confirmation" : status == null ? "Order received" : status.replace('_', ' '));
        if (PAYMENT_PENDING.equals(payment)) ((TextView)findViewById(R.id.tvHeading)).setText("Your order is almost there");
        String info = (branch == null ? "The branch" : branch) + " has received your "
                + ("pickup".equals(type) ? "pickup" : "delivery") + " order and will start on it shortly.";
        if (PAYMENT_PAID.equals(payment)) info += "\nPayment received – thank you!";
        else if (PAYMENT_PENDING.equals(payment)) {
            info = "We're waiting for PayFast to confirm your payment. Your order goes to "
                    + (branch == null ? "the branch" : branch) + " as soon as it does – check My orders for its status.";
        }
        ((TextView) findViewById(R.id.tvInfo)).setText(info);
        findViewById(R.id.btnDone).setOnClickListener(v -> backToMenu());
        findViewById(R.id.btnTrack).setOnClickListener(v -> {
            Intent i = new Intent(this, TrackOrderActivity.class);
            i.putExtra(TrackOrderActivity.EXTRA_ORDER_ID, id);
            startActivity(i);
        });
    }


    private void backToMenu() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
