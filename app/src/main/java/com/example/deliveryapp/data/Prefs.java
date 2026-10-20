package com.example.deliveryapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
        sp.edit().putString("token", a.accessToken)
                .putString("username", a.username != null ? a.username : fallbackUsername)
                .putLong("expires", expires).apply();
    }

    public void clearSession() {
        sp.edit().remove("token").remove("username").remove("expires").apply();
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

    public String getUsername() { return sp.getString("username", ""); }

    // ------------------------------------------------------------ branch
    public Branch getBranch() { return Branch.fromId(sp.getInt("branch", 1)); }

    public void setBranch(Branch b) { sp.edit().putInt("branch", b.id).apply(); }

    // ------------------------------------------------------------ saved details (local copy of the profile)
    public Models.Profile getProfile() {
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
        sp.edit().putString("profile", gson.toJson(copy)).apply();
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
