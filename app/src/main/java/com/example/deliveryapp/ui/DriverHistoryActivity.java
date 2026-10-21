package com.example.deliveryapp.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.AccountRole;
import com.example.deliveryapp.data.DriverModels;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.google.android.material.button.MaterialButton;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Current-shift history only. No shift or delivery mutations are performed here. */
public class DriverHistoryActivity extends BaseActivity {
    public static final String EXTRA_SHIFT_ID="driver_shift_id";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable() {
        @Override public void run() { if (visible) { refresh(); handler.postDelayed(this,10000); } }
    };
    private long shiftId,lastRefresh;
    private String username;
    private boolean visible,busy;
    private TextView shift,connection,error;
    private LinearLayout orders;
    private MaterialButton refresh;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        shiftId=getIntent().getLongExtra(EXTRA_SHIFT_ID,0);
        username=Prefs.get().getUsername();
        setContentView(R.layout.activity_driver_history); setupToolbar("Shift history");
        shift=findViewById(R.id.tvDriverHistoryShift); connection=findViewById(R.id.tvDriverHistoryConnection);
        error=findViewById(R.id.tvDriverHistoryError); orders=findViewById(R.id.driverHistoryOrders);
        refresh=findViewById(R.id.btnDriverHistoryRefresh); refresh.setOnClickListener(v -> refresh());
    }
    @Override protected void onStart() {
        super.onStart();
        if (dead()) return;
        if (!Prefs.get().isLoggedIn() || Prefs.get().getRole()!=AccountRole.DRIVER
                || !username.equals(Prefs.get().getUsername())) { SessionRouter.open(this); return; }
        if (shiftId<=0) { finish(); return; }
        visible=true; refresh(); handler.postDelayed(poll,10000);
    }
    @Override protected void onStop() {
        visible=false; handler.removeCallbacks(poll); super.onStop();
    }
    private boolean usable() {
        return visible && !dead() && Prefs.get().isLoggedIn() && Prefs.get().getRole()==AccountRole.DRIVER
                && username.equals(Prefs.get().getUsername());
    }
    private void refresh() {
        if (visible && (!Prefs.get().isLoggedIn() || Prefs.get().getRole()!=AccountRole.DRIVER
                || !username.equals(Prefs.get().getUsername()))) {
            if (!Prefs.get().isLoggedIn()) Repo.get().logout();
            SessionRouter.open(this); return;
        }
        if (!usable() || busy) return;
        busy=true; refresh.setEnabled(false); error.setVisibility(View.GONE);
        connection.setText("Refreshing history…");
        Repo.get().driverDashboard(new Repo.Result<DriverModels.Dashboard>() {
            @Override public void onSuccess(DriverModels.Dashboard dashboard) {
                busy=false;
                if (!usable()) return;
                refresh.setEnabled(true);
                if (dashboard==null || dashboard.profile==null) { failed("Your driver profile is unavailable. Contact your manager."); return; }
                orders.removeAllViews(); lastRefresh=System.currentTimeMillis();
                connection.setText("Last refreshed "+time(lastRefresh));
                if (dashboard.shift==null || dashboard.shift.id!=shiftId) {
                    shift.setText("Shift history unavailable");
                    failed("This shift is no longer available here. Go back to current deliveries."); return;
                }
                shift.setText("This shift · Started "+time(DriverModels.timestamp(dashboard.shift.clockedInAt))
                        +" · "+dashboard.deliveredCount()+" delivered");
                for (DriverModels.Delivery delivery:dashboard.completedDeliveries()) {
                    LinearLayout box=DriverOrderViews.addCard(DriverHistoryActivity.this,orders,delivery);
                    DriverOrderViews.addDetails(DriverHistoryActivity.this,box,delivery);
                    DriverOrderViews.text(DriverHistoryActivity.this,box,"Picked up: "+time(DriverModels.timestamp(delivery.pickedUpAt)),16,false);
                    DriverOrderViews.text(DriverHistoryActivity.this,box,"Delivered: "+time(DriverModels.timestamp(delivery.deliveredAt)),16,false);
                }
                if (orders.getChildCount()==0)
                    DriverOrderViews.text(DriverHistoryActivity.this,orders,"No completed deliveries this shift yet.",16,false);
            }
            @Override public void onError(String message,int code) {
                busy=false;
                if (!usable()) return;
                if (code==403) { Repo.get().logout(); SessionRouter.open(DriverHistoryActivity.this); return; }
                failed(message);
            }
        });
    }
    private void failed(String message) {
        refresh.setEnabled(true); error.setText(message); error.setVisibility(View.VISIBLE);
        connection.setText(lastRefresh==0?"History not refreshed yet":"Connection not confirmed · Last refreshed "+time(lastRefresh));
    }
    private static String time(long millis) {
        return millis<=0?"Not recorded":DateTimeFormatter.ofPattern("dd MMM, HH:mm",Locale.ENGLISH)
                .withZone(ZoneId.of("Africa/Johannesburg")).format(Instant.ofEpochMilli(millis));
    }
}
