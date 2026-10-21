package com.example.deliveryapp.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.*;
import com.example.deliveryapp.util.Money;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Foreground-only dispatch UI. Every mutation is confirmed by the server. */
public class DriverActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() { if (visible) { refresh(false); handler.postDelayed(this, 10000); } }
    };
    private boolean visible, busy, fresh, mayStart=true;
    private String sessionUsername;
    private long lastRefresh;
    private DriverModels.Dashboard dashboard;
    private TextView name, branch, shift, summary, connection, error;
    private MaterialButton claim, end, retry, history;
    private LinearLayout active;
    private String message = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        sessionUsername=Prefs.get().getUsername();
        setContentView(R.layout.activity_driver);
        name=findViewById(R.id.tvDriverName); branch=findViewById(R.id.tvDriverBranch);
        shift=findViewById(R.id.tvDriverShift); summary=findViewById(R.id.tvDriverSummary);
        connection=findViewById(R.id.tvDriverConnection); error=findViewById(R.id.tvDriverError);
        claim=findViewById(R.id.btnDriverClaim); end=findViewById(R.id.btnDriverEnd); retry=findViewById(R.id.btnDriverRetry);
        active=findViewById(R.id.driverActive); history=findViewById(R.id.btnDriverHistory);
        history.setOnClickListener(v -> {
            if (usable() && dashboard != null && dashboard.shift != null)
                startActivity(new Intent(this, DriverHistoryActivity.class)
                        .putExtra(DriverHistoryActivity.EXTRA_SHIFT_ID, dashboard.shift.id));
        });
        retry.setOnClickListener(v -> { mayStart=true; refresh(false); });
        claim.setOnClickListener(v -> {
            if (!canAct() || dashboard.hasUnfinished()) return;
            begin(); Repo.get().driverClaim(deliveryResult());
        });
        end.setOnClickListener(v -> confirmEnd());
        render();
    }
    @Override protected void onStart() {
        super.onStart();
        if (dead()) return;
        if (Prefs.get().getRole()!=AccountRole.DRIVER) { SessionRouter.open(this); return; }
        visible=true; fresh=false; refresh(false); handler.postDelayed(poll,10000);
    }
    @Override protected void onStop() {
        visible=false; handler.removeCallbacks(poll); super.onStop();
    }
    private boolean usable() { return visible && !dead() && Prefs.get().getRole()==AccountRole.DRIVER
            && sessionUsername.equals(Prefs.get().getUsername()); }
    private boolean canAct() {
        return usable() && !busy && fresh && dashboard!=null && dashboard.profile.active
                && dashboard.shift!=null && dashboard.shift.active();
    }
    private void begin() { busy=true; message=""; render(); }
    private void refresh(boolean keepMessage) {
        if (visible && !Prefs.get().isLoggedIn()) { signOut(); return; }
        if (!usable() || busy) return;
        busy=true; if (!keepMessage) message=""; render();
        Repo.get().driverDashboard(new Repo.Result<DriverModels.Dashboard>() {
            @Override public void onSuccess(DriverModels.Dashboard d) {
                busy=false;
                if (!usable()) return;
                if (d==null || d.profile==null) { failure("Your driver profile is unavailable. Contact your manager.",0,false); return; }
                dashboard=d; fresh=true; lastRefresh=System.currentTimeMillis();
                if (Prefs.get().driverEndPending() && (d.shift==null || !d.shift.active())) { signOut(); return; }
                if (Prefs.get().driverStartPending()) {
                    if (d.shift!=null) Prefs.get().driverStarted();
                    else if (d.profile.active && mayStart) { startShift(); return; }
                }
                render();
            }
            @Override public void onError(String m,int code) { failure(m,code,false); }
        });
    }
    private void startShift() {
        begin(); Repo.get().driverClockIn(new Repo.Result<DriverModels.Shift>() {
            @Override public void onSuccess(DriverModels.Shift s) {
                busy=false;
                if (s!=null && s.active() && sessionUsername.equals(Prefs.get().getUsername())) Prefs.get().driverStarted();
                if (usable()) refresh(false);
            }
            @Override public void onError(String m,int code) {
                // Do not automatically replay a failed mutation. Reconcile the server state first.
                busy=false;
                mayStart=false;
                if (code==409) Prefs.get().driverStarted();
                failure(m,code,true);
            }
        });
    }
    private Repo.Result<DriverModels.Dashboard> deliveryResult() {
        return new Repo.Result<DriverModels.Dashboard>() {
            @Override public void onSuccess(DriverModels.Dashboard d) {
                busy=false;
                if (!usable()) return;
                if (d==null || d.profile==null) { refresh(false); return; }
                dashboard=d; fresh=true; lastRefresh=System.currentTimeMillis(); render();
            }
            @Override public void onError(String m,int code) { failure(m,code,true); }
        };
    }
    private void failure(String m,int code,boolean reconcile) {
        busy=false; fresh=false;
        if (!usable()) return;
        if (code==403) {
            Repo.get().logout(); SessionRouter.open(this); return;
        }
        message=m; render();
        if (reconcile) refresh(true);
    }
    private void confirmEnd() {
        if (busy || !fresh || dashboard==null) return;
        if (dashboard.hasUnfinished()) { message="Complete assigned deliveries before ending your shift."; render(); return; }
        boolean onShift=dashboard.shift!=null && dashboard.shift.active();
        new MaterialAlertDialogBuilder(this).setTitle(onShift ? "End your shift?" : "Sign out?")
                .setMessage(onShift ? "Your shift will end after the server confirms clock-out." : "Your recorded shift is already closed.")
                .setNegativeButton("Keep working",null)
                .setPositiveButton(onShift ? "End shift & sign out" : "Sign out",(dialog,which) -> {
                    if (!usable() || busy || !fresh) return;
                    if (!onShift) { signOut(); return; }
                    Prefs.get().driverEnding(); begin();
                    Repo.get().driverClockOut(new Repo.Result<DriverModels.Shift>() {
                        @Override public void onSuccess(DriverModels.Shift s) {
                            busy=false;
                            if (!usable()) return;
                            if (s!=null && !s.active()) signOut(); else refresh(true);
                        }
                        @Override public void onError(String m,int code) { failure(m,code,true); }
                    });
                }).show();
    }
    private void signOut() { Repo.get().logout(); SessionRouter.open(this); }
    private void render() {
        if (name==null) return;
        retry.setEnabled(!busy);
        connection.setText((busy ? "Syncing… · " : fresh ? "Connected · " : "Connection not confirmed · ")
                + (lastRefresh==0 ? "Not refreshed yet" : "Last refreshed " + time(lastRefresh)));
        error.setText(message); error.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);
        claim.setEnabled(canAct() && !dashboard.hasUnfinished());
        end.setEnabled(!busy && fresh && dashboard!=null && !dashboard.hasUnfinished());
        history.setEnabled(!busy && dashboard!=null && dashboard.shift!=null);
        history.setText("View shift history ("+(dashboard==null?0:dashboard.deliveredCount())+")");
        active.removeAllViews();
        if (dashboard==null) return;
        DriverModels.Profile p=dashboard.profile;
        String fullName=(safe(p.firstName)+" "+safe(p.lastName)).trim();
        name.setText(fullName.isEmpty()?"My shift":fullName);
        String branchName=p.branchId==1 || p.branchId==2 ? Branch.fromId(p.branchId).displayName : "Branch #"+p.branchId;
        branch.setText(branchName+" · "+safe(p.email));
        DriverModels.Shift s=dashboard.shift;
        if (!p.active) shift.setText("Driver account inactive · contact your manager");
        else if (s==null) shift.setText(Prefs.get().driverStartPending() ? "Starting your shift…" : "Shift closed · sign in again to start a shift");
        else if (!s.active()) shift.setText("Awaiting manager cash-up");
        else {
            long minutes=Math.max(0,(System.currentTimeMillis()-DriverModels.timestamp(s.clockedInAt))/60000);
            shift.setText("On shift · Started "+time(DriverModels.timestamp(s.clockedInAt))+" · "+minutes/60+"h "+minutes%60+"m");
        }
        summary.setText("Delivered: "+dashboard.deliveredCount()+"\nOrder sales: "+Money.rand(dashboard.sales())
                +"\nEstimated pay: "+Money.rand(dashboard.estimatedPay(System.currentTimeMillis()))
                +"\nFinal pay is confirmed during manager cash-up.");
        end.setText(s!=null && s.active() ? "End shift & sign out" : "Sign out");
        for (DriverModels.Delivery d:dashboard.currentDeliveries()) card(d,active);
        if (active.getChildCount()==0) text(active,"No current delivery. Claim next when you are ready.",16,false);
    }
    private void card(DriverModels.Delivery d,LinearLayout parent) {
        DriverModels.Order o=d.order;
        LinearLayout box=DriverOrderViews.addCard(this,parent,d);
        if ("ASSIGNED".equals(d.state)) {
            MaterialButton pickup=button(box,"Mark picked up",()-> {
                if (!canAct() || !d.pickupAllowed()) return;
                new MaterialAlertDialogBuilder(this).setTitle("Confirm pickup")
                        .setMessage("Have you collected order #"+o.id+"?")
                        .setNegativeButton("Cancel",null).setPositiveButton("Mark picked up",(dialog,which) -> {
                            if (!canAct() || !d.pickupAllowed()) return;
                            begin(); Repo.get().driverPickup(o.id,deliveryResult());
                        }).show();
            }); pickup.setEnabled(canAct() && d.pickupAllowed());
            if (!d.pickupAllowed()) text(box,"This order is no longer available for pickup. Refresh or contact your manager.",14,false);
        } else if ("PICKED_UP".equals(d.state)) {
            MaterialButton delivered=button(box,"Mark delivered",()->new MaterialAlertDialogBuilder(this)
                    .setTitle("Confirm delivery").setMessage("Has order #"+o.id+" been handed to the customer?")
                    .setNegativeButton("Cancel",null).setPositiveButton("Mark delivered",(dialog,which)-> {
                        if (!canAct()) return;
                        begin(); Repo.get().driverDelivered(o.id,deliveryResult());
                    }).show()); delivered.setEnabled(canAct());
        }
        DriverOrderViews.addDetails(this,box,d);
        if (!safe(o.phone).isEmpty()) button(box,"Call "+o.phone,()->external(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",o.phone,null))));
        if (!o.address().isEmpty() || o.latitude!=null && o.longitude!=null) button(box,"Navigate",()-> {
            String target=o.latitude!=null && o.longitude!=null ? o.latitude+","+o.longitude : o.address();
            external(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(target))));
        });
    }
    private void text(LinearLayout box,String value,int size,boolean bold) {
        if (value==null || value.isEmpty()) return;
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextIsSelectable(true);
        if (bold) t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);
        t.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,6)); box.addView(t);
    }
    private MaterialButton button(LinearLayout box,String label,Runnable action) {
        MaterialButton b=new MaterialButton(this); b.setText(label); b.setMinHeight(Ui.dp(this,56));
        b.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); b.setOnClickListener(v->action.run()); box.addView(b); return b;
    }
    private void external(Intent intent) {
        try { startActivity(intent); } catch (ActivityNotFoundException e) { Ui.toast(this,"No app is available to open this action."); }
    }
    private static String safe(String text) { return text==null?"":text; }
    private static String time(long millis) {
        return DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT).withZone(ZoneId.of("Africa/Johannesburg")).format(Instant.ofEpochMilli(millis));
    }
}
