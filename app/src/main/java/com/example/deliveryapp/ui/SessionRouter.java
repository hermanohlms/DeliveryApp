package com.example.deliveryapp.ui;

import android.app.Activity;
import android.content.Intent;
import com.example.deliveryapp.data.AccountRole;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;

/** A role change replaces the task so Back cannot expose a previous account's screens. */
public final class SessionRouter {
    private SessionRouter() {}

    public static void open(Activity activity) {
        AccountRole role = Prefs.get().getRole();
        if (Prefs.get().isLoggedIn() && role == AccountRole.UNKNOWN) Repo.get().logout();
        Class<?> target = role == AccountRole.DRIVER ? DriverActivity.class
                : role == AccountRole.STAFF ? StaffAccessActivity.class : LandingActivity.class;
        activity.startActivity(new Intent(activity, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
    }

    public static boolean redirectNonCustomer(Activity activity) {
        if (Prefs.get().getRole() == AccountRole.DRIVER || Prefs.get().getRole() == AccountRole.STAFF
                || Prefs.get().isLoggedIn() && Prefs.get().getRole() == AccountRole.UNKNOWN) {
            open(activity);
            return true;
        }
        return false;
    }
}
