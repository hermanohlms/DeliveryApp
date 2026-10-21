package com.example.deliveryapp.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.deliveryapp.BuildConfig;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Repo;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;

/**
 * Live view of one order: map (restaurant, customer, driver), status steps, ETA and driver details.
 * Everything comes from GET /api/orders/{id}/tracking, re-read every few seconds while the screen is open.
 * The driver app (separate) is what keeps the driver's position on the server up to date.
 */
public class TrackOrderActivity extends BaseActivity {
    public static final String EXTRA_ORDER_ID = "orderId";

    private static final long REFRESH_MS = 10_000;

    private long orderId;
    private GoogleMap map;
    private boolean mapLoaded, cameraFitted;
    private Marker branchMarker, destinationMarker, driverMarker;
    private Models.Tracking last;

    private TextView tvStatus, tvEta, tvDriverName, tvDriverVehicle, tvDriverUpdated, tvAddressTitle, tvAddress, tvMessage;
    private LinearLayout steps, driverGroup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::load;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_order);
        orderId = getIntent().getLongExtra(EXTRA_ORDER_ID, 0);
        setupToolbar("Order #" + orderId);

        tvStatus = findViewById(R.id.tvStatus);
        tvEta = findViewById(R.id.tvEta);
        steps = findViewById(R.id.steps);
        driverGroup = findViewById(R.id.driverGroup);
        tvDriverName = findViewById(R.id.tvDriverName);
        tvDriverVehicle = findViewById(R.id.tvDriverVehicle);
        tvDriverUpdated = findViewById(R.id.tvDriverUpdated);
        tvAddressTitle = findViewById(R.id.tvAddressTitle);
        tvAddress = findViewById(R.id.tvAddress);
        tvMessage = findViewById(R.id.tvMessage);
        findViewById(R.id.btnCall).setOnClickListener(v -> callDriver());
        tvStatus.setText("Loading…");

        if (BuildConfig.MAPS_API_KEY.isEmpty()) {
            mapNote("The live map is unavailable. You can still follow your order below.");
        } else {
            SupportMapFragment f = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            f.getMapAsync(m -> {
                map = m;
                map.getUiSettings().setMapToolbarEnabled(false);
                map.setOnMapLoadedCallback(() -> { mapLoaded = true; if (last != null) showOnMap(last); });
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(refresh);
    }

    private void load() {
        handler.removeCallbacks(refresh);
        Repo.get().tracking(orderId, new Repo.Result<Models.Tracking>() {
            @Override public void onSuccess(Models.Tracking t) {
                if (dead() || t == null) return;
                last = t;
                message(null);
                show(t);
                if (resumed && step(t.status) < STEP_DONE) handler.postDelayed(refresh, REFRESH_MS);
            }
            @Override public void onError(String m, int code) {
                if (dead()) return;
                message(m);
                if (last == null) {
                    tvStatus.setText("Tracking unavailable");
                    if (map != null) mapNote("The map appears here once the restaurant has tracking switched on.");
                }
                if (resumed && code != 401 && !Repo.isUnsupported(code)) handler.postDelayed(refresh, REFRESH_MS);
            }
        });
    }

    // ------------------------------------------------------------------ status steps
    private static final int STEP_CANCELLED = -1, STEP_RECEIVED = 0, STEP_PREPARING = 1, STEP_ON_WAY = 2, STEP_DONE = 3;

    /** Maps the server's status text onto the four steps (accepts a few common spellings). */
    static int step(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        switch (s) {
            case "CANCELLED": case "CANCELED": case "REJECTED": return STEP_CANCELLED;
            case "ACCEPTED": case "PREPARING": case "COOKING": case "IN_PROGRESS": return STEP_PREPARING;
            case "READY": case "READY_FOR_COLLECTION": case "READY_FOR_PICKUP":
            case "OUT_FOR_DELIVERY": case "ON_THE_WAY": case "DISPATCHED": case "PICKED_UP": return STEP_ON_WAY;
            case "DELIVERED": case "COLLECTED": case "COMPLETED": case "COMPLETE": return STEP_DONE;
            default: return STEP_RECEIVED;   // PENDING, AWAITING_PAYMENT, RECEIVED, unknown
        }
    }

    private void show(Models.Tracking t) {
        boolean pickup = "pickup".equals(t.orderType);
        int step = step(t.status);
        ((TextView)findViewById(R.id.tvOrderMeta)).setText("Order #" + orderId + " · "
                + (t.branch != null && t.branch.name != null ? t.branch.name + " · " : "") + (pickup ? "Pickup" : "Delivery"));
        tvStatus.setText(t.status == null ? "Order received" : pretty(t.status));

        String eta = null;
        if (t.etaMinutes != null && step >= 0 && step < STEP_DONE) {
            eta = (pickup ? "Ready in about " : "Arriving in about ") + t.etaMinutes + " min";
        }
        tvEta.setText(eta);
        Ui.show(tvEta, eta != null);

        String[] labels = pickup
                ? new String[]{"Order received", "Preparing", "Ready for collection", "Collected"}
                : new String[]{"Order received", "Preparing", "On the way", "Delivered"};
        buildSteps(labels, step);

        Models.Driver d = t.driver;
        boolean showDriver = !pickup && d != null && step >= 0 && step < STEP_DONE;
        Ui.show(driverGroup, showDriver);
        if (showDriver) {
            tvDriverName.setText(d.name == null ? "Driver assigned" : d.name);
            tvDriverVehicle.setText(d.vehicle);
            Ui.show(tvDriverVehicle, d.vehicle != null && !d.vehicle.isEmpty());
            tvDriverUpdated.setText(d.locationUpdatedAt == null ? "Waiting for the driver's location…"
                    : "Location updated " + d.locationUpdatedAt);
            Ui.show(findViewById(R.id.btnCall), d.phone != null && !d.phone.isEmpty());
        }

        Models.MapPoint where = pickup ? t.branch : t.destination;
        tvAddressTitle.setText(pickup ? "Collect from" : "Delivering to");
        String address = where == null ? null
                : where.formattedAddress != null ? where.formattedAddress : where.name;
        tvAddress.setText(address);
        Ui.show(tvAddressTitle, address != null);
        Ui.show(tvAddress, address != null);

        if (map != null && mapLoaded) showOnMap(t);
    }

    private void buildSteps(String[] labels, int current) {
        steps.removeAllViews();
        if (current == STEP_CANCELLED) {
            TextView tv = new TextView(this);
            tv.setText("This order was cancelled.");
            tv.setTextColor(MaterialColors.getColor(tv, com.google.android.material.R.attr.colorError));
            steps.addView(tv);
            return;
        }
        int done = ContextCompat.getColor(this, R.color.steel_blue);
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            tv.setText((i <= current ? "●  " : "○  ") + labels[i]);
            if (i <= current) tv.setTextColor(done);
            else tv.setTextColor(MaterialColors.getColor(tv, com.google.android.material.R.attr.colorOutline));
            if (i == current) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            steps.addView(tv);
        }
    }

    // ------------------------------------------------------------------ map
    private void showOnMap(Models.Tracking t) {
        Ui.show(findViewById(R.id.tvMapNote), false);
        boolean pickup = "pickup".equals(t.orderType);
        boolean active = step(t.status) >= 0 && step(t.status) < STEP_DONE;

        branchMarker = place(branchMarker, t.branch, t.branch != null && t.branch.name != null ? t.branch.name : "Restaurant",
                BitmapDescriptorFactory.HUE_AZURE);
        destinationMarker = place(destinationMarker, pickup ? null : t.destination, "Your address",
                BitmapDescriptorFactory.HUE_RED);

        Models.Driver d = t.driver;
        LatLng driverAt = !pickup && active && d != null && d.hasLocation() ? new LatLng(d.latitude, d.longitude) : null;
        if (driverAt == null) {
            if (driverMarker != null) { driverMarker.remove(); driverMarker = null; }
        } else if (driverMarker == null) {
            driverMarker = map.addMarker(new MarkerOptions().position(driverAt)
                    .title(d.name == null ? "Driver" : d.name).zIndex(1f)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        } else {
            driverMarker.setPosition(driverAt);
        }

        // Frame everything once; after that only the driver moves, so the customer can pan/zoom freely.
        if (!cameraFitted) {
            LatLngBounds.Builder b = new LatLngBounds.Builder();
            int n = 0;
            for (Marker m : new Marker[]{branchMarker, destinationMarker, driverMarker}) {
                if (m != null) { b.include(m.getPosition()); n++; }
            }
            if (n == 1) map.moveCamera(CameraUpdateFactory.newLatLngZoom(b.build().getCenter(), 15f));
            else if (n > 1) map.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), Ui.dp(this, 56)));
            cameraFitted = n > 0;
        }
    }

    /** Adds, moves or removes a fixed pin. */
    private Marker place(Marker marker, Models.MapPoint p, String title, float hue) {
        if (p == null || !p.hasLocation()) {
            if (marker != null) marker.remove();
            return null;
        }
        LatLng at = new LatLng(p.latitude, p.longitude);
        if (marker != null) { marker.setPosition(at); return marker; }
        return map.addMarker(new MarkerOptions().position(at).title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(hue)));
    }

    private void mapNote(String text) {
        TextView note = findViewById(R.id.tvMapNote);
        note.setText(text);
        Ui.show(note, true);
    }

    // ------------------------------------------------------------------ misc
    private void callDriver() {
        if (last == null || last.driver == null || last.driver.phone == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + last.driver.phone)));
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "No phone app found. The driver's number is " + last.driver.phone);
        }
    }

    private void message(String text) {
        tvMessage.setText(text);
        tvMessage.setTextColor(MaterialColors.getColor(tvMessage, com.google.android.material.R.attr.colorError));
        Ui.show(tvMessage, text != null);
    }

    /** "OUT_FOR_DELIVERY" → "Out for delivery" */
    private static String pretty(String status) {
        String s = status.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
