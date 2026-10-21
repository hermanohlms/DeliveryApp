package com.example.deliveryapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Everything that survives app restarts: login token, chosen branch, saved details, order history. */
public final class Prefs {
    private static Prefs instance;

    public static synchronized Prefs init(Context context) {
        if (instance == null) instance = new Prefs(context.getApplicationContext());
        return instance;
    }

    public static Prefs get() { return instance; }

    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    private Prefs(Context c) { sp = c.getSharedPreferences("delivery_app", Context.MODE_PRIVATE); }

    // ------------------------------------------------------------ session
    public void saveSession(Models.AuthResponse a, String fallbackUsername) {
        long expires = a.expiresIn > 0 ? System.currentTimeMillis() + a.expiresIn * 1000L : 0L;
        String username = a.username != null ? a.username : fallbackUsername;
        String owner = sp.getString("profile_owner", null);
        if (owner == null) {
            Models.Profile old = readProfile();
            owner = old.email;
        }
        SharedPreferences.Editor edit = sp.edit();
        boolean sameAccount = owner != null && (owner.equalsIgnoreCase(username)
                || fallbackUsername != null && owner.equalsIgnoreCase(fallbackUsername));
        if (!sameAccount) {
            edit.remove("profile").remove("profile_owner");
        } else {
            edit.putString("profile_owner", username.toLowerCase(Locale.ROOT));
        }
        edit.putString("token", a.accessToken).putString("username", username)
                .putLong("expires", expires).putString("account_role", AccountRole.from(a.roles).name())
                .putBoolean("driver_start_pending", AccountRole.from(a.roles) == AccountRole.DRIVER)
                .putBoolean("driver_end_pending", false).apply();
    }

    public void clearSession() {
        sp.edit().remove("token").remove("username").remove("expires")
                .remove("profile").remove("profile_owner").remove("account_role")
                .remove("driver_start_pending").remove("driver_end_pending").apply();
    }

    /** null when logged out or the token has expired */
    public String getToken() {
        String t = sp.getString("token", null);
        long exp = sp.getLong("expires", 0L);
        if (t == null) return null;
        if (exp != 0L && System.currentTimeMillis() >= exp) return null;
        return t;
    }

    public boolean isLoggedIn() { return getToken() != null; }

    public AccountRole getRole() {
        if (!isLoggedIn()) return AccountRole.UNKNOWN;
        try { return AccountRole.valueOf(sp.getString("account_role", "UNKNOWN")); }
        catch (IllegalArgumentException e) { return AccountRole.UNKNOWN; }
    }

    public boolean driverStartPending() { return sp.getBoolean("driver_start_pending", false); }
    public void driverStarted() { sp.edit().putBoolean("driver_start_pending", false).apply(); }
    public boolean driverEndPending() { return sp.getBoolean("driver_end_pending", false); }
    public void driverEnding() { sp.edit().putBoolean("driver_end_pending", true).apply(); }

    public String getUsername() { return sp.getString("username", ""); }

    // ------------------------------------------------------------ branch
    public Branch getBranch() { return Branch.fromId(sp.getInt("branch", 1)); }

    public void setBranch(Branch b) { sp.edit().putInt("branch", b.id).apply(); }

    // ------------------------------------------------------------ saved details (local copy of the profile)
    public Models.Profile getProfile() {
        String owner = sp.getString("profile_owner", null);
        if (owner == null && isLoggedIn()) {
            Models.Profile legacy = readProfile();
            if (legacy.email != null && legacy.email.equalsIgnoreCase(getUsername())) {
                owner = getUsername();
                sp.edit().putString("profile_owner", owner.toLowerCase(Locale.ROOT)).apply();
            }
        }
        if (owner == null || !owner.equalsIgnoreCase(getUsername()) || !isLoggedIn()) return new Models.Profile();
        return readProfile();
    }

    private Models.Profile readProfile() {
        String json = sp.getString("profile", null);
        if (json != null) {
            try {
                Models.Profile p = gson.fromJson(json, Models.Profile.class);
                if (p != null) return p;
            } catch (Exception ignored) { }
        }
        return new Models.Profile();
    }

    public void saveProfile(Models.Profile p) {
        Models.Profile copy = gson.fromJson(gson.toJson(p), Models.Profile.class);
        copy.currentPassword = null;   // never store passwords
        copy.newPassword = null;
        String owner = isLoggedIn() ? getUsername() : copy.email;
        if (owner == null || owner.trim().isEmpty()) return;
        sp.edit().putString("profile", gson.toJson(copy))
                .putString("profile_owner", owner.toLowerCase(Locale.ROOT)).apply();
    }

    // ------------------------------------------------------------ order history (local fallback)
    private static final Type HISTORY = new TypeToken<List<Models.PastOrder>>() {}.getType();

    public List<Models.PastOrder> getHistory() {
        String json = sp.getString("history", null);
        if (json != null) {
            try {
                List<Models.PastOrder> list = gson.fromJson(json, HISTORY);
                if (list != null) return list;
            } catch (Exception ignored) { }
        }
        return new ArrayList<>();
    }

    public void addOrder(Models.PastOrder order) {
        List<Models.PastOrder> list = getHistory();
        list.add(0, order);
        if (list.size() > 30) list = new ArrayList<>(list.subList(0, 30));
        sp.edit().putString("history", gson.toJson(list)).apply();
    }
}
