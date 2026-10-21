package com.example.deliveryapp.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.deliveryapp.App;
import com.example.deliveryapp.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** All network access. Callbacks are delivered on the main thread (Retrofit's Android default). */
public final class Repo implements CatalogClient {
    public interface Result<T> {
        void onSuccess(T value);
        void onError(String message, int code);   // code 0 = no connection
    }

    private static Repo instance;

    public static synchronized Repo init(Context context) {
        if (instance == null) instance = new Repo(context.getApplicationContext());
        return instance;
    }

    public static Repo get() { return instance; }

    private final Api api;
    private final MenuCacheDb catalogDb;
    private final Gson gson = new Gson();
    private final Map<Integer, List<Models.MenuItem>> menuCache = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Answers a call from the built-in demo data (see DemoData) after a short, realistic delay. */
    private <T> void demo(final Result<T> cb, final T value) {
        main.postDelayed(() -> cb.onSuccess(value), 250);
    }

    private Repo(Context context) {
        catalogDb = new MenuCacheDb(context);
        OkHttpClient.Builder http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request req = chain.request();
                    String token = Prefs.get().getToken();
                    if (token != null && !req.url().encodedPath().startsWith("/api/auth/")) {
                        req = req.newBuilder().header("Authorization", "Bearer " + token).build();
                    }
                    return chain.proceed(req);
                });
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);
            log.redactHeader("Authorization");
            http.addInterceptor(log);
        }
        api = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(http.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(Api.class);
    }

    // ------------------------------------------------------------------ helpers
    /** Codes that mean "this endpoint isn't on the server yet" for the optional (NEW) endpoints. */
    public static boolean isUnsupported(int code) {
        return code == 400 || code == 403 || code == 404 || code == 405 || code == 500 || code == 501;
    }

    public String newIdempotencyKey() { return UUID.randomUUID().toString(); }

    private <T> void run(Call<T> call, final String context, final Result<T> cb) {
        call.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> c, Response<T> r) {
                if (r.isSuccessful()) cb.onSuccess(r.body());
                else fail(r, context, cb);
            }
            @Override public void onFailure(Call<T> c, Throwable t) {
                cb.onError("Can't reach the server. Check your internet connection.", 0);
            }
        });
    }

    private void fail(Response<?> r, String context, Result<?> cb) {
        int code = r.code();
        String path = r.raw().request().url().encodedPath();
        boolean authCall = path.startsWith("/api/auth/");
        if (code == 401 && !authCall && !Prefs.get().getUsername().isEmpty()) {
            Prefs.get().clearSession();
            Cart.get().clear();
            App.goToLogin();
        }
        cb.onError(messageFor(code, serverMessage(r), authCall, context), code);
    }

    private String serverMessage(Response<?> r) {
        try {
            ResponseBody body = r.errorBody();
            if (body == null) return null;
            JsonElement el = JsonParser.parseString(body.string());
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                for (String key : new String[]{"message", "error"}) {
                    if (o.has(key) && o.get(key).isJsonPrimitive()) {
                        String s = o.get(key).getAsString();
                        if (s != null && !s.trim().isEmpty() && s.length() <= 200) return s.trim();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String messageFor(int code, String server, boolean authCall, String context) {
        if ("register".equals(context)) {
            if (code == 401 || code == 403 || code == 404 || code == 405 || code == 501) {
                return "Online sign-up isn't switched on yet. Please try again once the server has been updated.";
            }
            if (code == 409) return server != null ? server : "An account with that email or phone number already exists.";
            if (code == 400) return server != null ? server : "Please check your details and your address, then try again.";
        }
        if ("payfast".equals(context) && (code == 404 || code == 405 || code == 501)) {
            return "Card payment isn't switched on yet. Please choose cash for now.";
        }
        if ("tracking".equals(context) && (code == 404 || code == 405 || code == 501)) {
            return "Live tracking isn't switched on yet. Check My orders for the latest status.";
        }
        switch (code) {
            case 400: return server != null ? server : "The restaurant couldn't accept that. Please check your details and try again.";
            case 401: return authCall ? "Incorrect email or password." : "Your session expired. Please log in again.";
            case 403: return "This account isn't allowed to do that.";
            case 404: return "driver".equals(context) ? (server != null ? server : "No delivery is available right now.") : "That item is no longer available.";
            case 409: return server != null ? server : "That conflicts with an existing record.";
            case 429: return "Too many attempts. Please wait a minute and try again.";
            default: return "Something went wrong on the server (" + code + ").";
        }
    }

    // ------------------------------------------------------------------ auth
    public void login(String email, String password, Result<Models.AuthResponse> cb) {
        if (DemoData.isDemoLogin(email, password)) {
            Prefs.get().saveProfile(DemoData.profile());
            demo(cb, DemoData.auth());
            return;
        }
        run(api.login(new Models.LoginRequest(email.trim(), password)), "login", cb);
    }

    public void forgotPassword(String email, Result<Models.MessageResponse> cb) {
        run(api.forgotPassword(new Models.ForgotPasswordRequest(email.trim())), "forgot", cb);
    }

    /** NEW endpoint. Success value is the AuthResponse when the server sends one, otherwise null. */
    public void register(Models.RegisterRequest req, final Result<Models.AuthResponse> cb) {
        run(api.register(req), "register", new Result<ResponseBody>() {
            @Override public void onSuccess(ResponseBody body) {
                Models.AuthResponse auth = null;
                try {
                    if (body != null) {
                        String text = body.string();
                        if (text != null && !text.trim().isEmpty()) auth = gson.fromJson(text, Models.AuthResponse.class);
                    }
                } catch (Exception ignored) { }
                cb.onSuccess(auth);
            }
            @Override public void onError(String message, int code) { cb.onError(message, code); }
        });
    }

    public void logout() {
        Prefs.get().clearSession();
        Cart.get().clear();
    }

    // ------------------------------------------------------------------ menu
    public void driverDashboard(Result<DriverModels.Dashboard> cb) { run(api.driverDashboard(), "driver", cb); }
    public void driverClockIn(Result<DriverModels.Shift> cb) { run(api.driverClockIn(), "driver", cb); }
    public void driverClockOut(Result<DriverModels.Shift> cb) { run(api.driverClockOut(), "driver", cb); }
    public void driverClaim(Result<DriverModels.Dashboard> cb) { run(api.driverClaim(), "driver", cb); }
    public void driverPickup(long id, Result<DriverModels.Dashboard> cb) { run(api.driverPickup(id), "driver", cb); }
    public void driverDelivered(long id, Result<DriverModels.Dashboard> cb) { run(api.driverDelivered(id), "driver", cb); }

    public void pizzas(int branchId, Result<Models.PizzaCategorySplit> cb) {
        final String key = "pizzas:" + branchId;
        run(api.pizzas(branchId), "pizzas", new Result<Models.PizzaCategorySplit>() {
            @Override public void onSuccess(Models.PizzaCategorySplit value) {
                Models.PizzaCategorySplit result = value == null ? new Models.PizzaCategorySplit() : value;
                catalogDb.putJson(key, gson.toJson(result));
                cb.onSuccess(result);
            }
            @Override public void onError(String message, int code) {
                String cached = catalogDb.getJson(key);
                if (cached != null) {
                    try { cb.onSuccess(gson.fromJson(cached, Models.PizzaCategorySplit.class)); return; }
                    catch (Exception ignored) { }
                }
                cb.onError(message, code);
            }
        });
    }

    public void menu(final int branchId, boolean force, final Result<List<Models.MenuItem>> cb) {
        List<Models.MenuItem> cached = menuCache.get(branchId);
        if (cached == null) cached = catalogDb.getMenu(branchId, gson);
        if (DemoData.isActive()) { demo(cb, DemoData.menuItems()); return; }
        final List<Models.MenuItem> stale = cached;
        run(api.menuItems(branchId), "menu", new Result<List<Models.MenuItem>>() {
            @Override public void onSuccess(List<Models.MenuItem> value) {
                List<Models.MenuItem> list = Models.nz(value);
                menuCache.put(branchId, list);
                catalogDb.saveMenu(branchId, list, gson);
                cb.onSuccess(list);
            }
            @Override public void onError(String message, int code) {
                if (stale != null) cb.onSuccess(stale);
                else cb.onError(message, code);
            }
        });
    }

    public void menuItem(int branchId, final int itemId, final Result<Models.MenuItem> cb) {
        menu(branchId, false, new Result<List<Models.MenuItem>>() {
            @Override public void onSuccess(List<Models.MenuItem> list) {
                for (Models.MenuItem m : list) {
                    if (m.menuItemId == itemId) { cb.onSuccess(m); return; }
                }
                cb.onError("That item is no longer available.", 404);
            }
            @Override public void onError(String message, int code) { cb.onError(message, code); }
        });
    }

    public void pizzaDetail(int branchId, int pizzaId, Integer sizeCm, Result<Models.PizzaDetail> cb) {
        final String key = "pizza:" + branchId + ":" + pizzaId + ":" + (sizeCm == null ? 0 : sizeCm);
        run(api.pizzaDetail(branchId, pizzaId, sizeCm), "pizza", new Result<Models.PizzaDetail>() {
            @Override public void onSuccess(Models.PizzaDetail value) {
                if (value != null) catalogDb.putJson(key, gson.toJson(value));
                cb.onSuccess(value);
            }
            @Override public void onError(String message, int code) {
                String cached = catalogDb.getJson(key);
                if (cached != null) {
                    try { cb.onSuccess(gson.fromJson(cached, Models.PizzaDetail.class)); return; }
                    catch (Exception ignored) { }
                }
                cb.onError(message, code);
            }
        });
    }

    public void specials(int branchId, Result<List<Models.Special>> cb) {
        final String key = "specials:" + branchId;
        run(api.specials(branchId), "specials", new Result<List<Models.Special>>() {
            @Override public void onSuccess(List<Models.Special> value) {
                List<Models.Special> list = Models.nz(value);
                catalogDb.putJson(key, gson.toJson(list));
                cb.onSuccess(list);
            }
            @Override public void onError(String message, int code) {
                String cached = catalogDb.getJson(key);
                if (cached != null) {
                    try {
                        cb.onSuccess(gson.fromJson(cached, new TypeToken<List<Models.Special>>() {}.getType()));
                        return;
                    } catch (RuntimeException ignored) { }
                }
                cb.onError(message, code);
            }
        });
    }

    public void specialQuote(int branchId, Models.SpecialItemRequest request, Result<Models.SpecialQuote> cb) {
        run(api.specialQuote(branchId, request.specialId, request), "special quote", cb);
    }

    /** Checkout calls these without an offline fallback, then replaces stale cache entries. */
    public void freshMenu(int branchId, Result<List<Models.MenuItem>> cb) {
        run(api.menuItems(branchId), "menu", new Result<List<Models.MenuItem>>() {
            @Override public void onSuccess(List<Models.MenuItem> value) {
                List<Models.MenuItem> list = Models.nz(value);
                menuCache.put(branchId, list);
                catalogDb.saveMenu(branchId, list, gson);
                cb.onSuccess(list);
            }
            @Override public void onError(String message, int code) { cb.onError(message, code); }
        });
    }

    public void freshPizzaDetail(int branchId, int pizzaId, Integer sizeCm, Result<Models.PizzaDetail> cb) {
        run(api.pizzaDetail(branchId, pizzaId, sizeCm), "pizza", new Result<Models.PizzaDetail>() {
            @Override public void onSuccess(Models.PizzaDetail value) {
                if (value != null) catalogDb.putJson("pizza:" + branchId + ":" + pizzaId + ":" + sizeCm,
                        gson.toJson(value));
                cb.onSuccess(value);
            }
            @Override public void onError(String message, int code) { cb.onError(message, code); }
        });
    }

    public void freshSpecials(int branchId, Result<List<Models.Special>> cb) {
        run(api.specials(branchId), "specials", new Result<List<Models.Special>>() {
            @Override public void onSuccess(List<Models.Special> value) {
                List<Models.Special> list = Models.nz(value);
                catalogDb.putJson("specials:" + branchId, gson.toJson(list));
                cb.onSuccess(list);
            }
            @Override public void onError(String message, int code) { cb.onError(message, code); }
        });
    }

    public void quote(int branchId, int pizzaId, Models.QuoteRequest req, Result<Models.QuoteResponse> cb) {
        if (DemoData.isActive()) { demo(cb, DemoData.quote(pizzaId, req)); return; }
        run(api.quote(branchId, pizzaId, req), "quote", cb);
    }

    // ------------------------------------------------------------------ orders
    public void placeOrder(String key, Models.OrderRequest req, Result<Models.OrderResponse> cb) {
        if (DemoData.isActive()) { demo(cb, DemoData.placeOrder(req)); return; }
        run(api.placeOrder(key, req), "order", cb);
    }

    /** NEW endpoint. Same Idempotency-Key => same order, with a fresh payment for it (so "try again" can't double-order). */
    public void payFastCheckout(String key, Models.OrderRequest req, Result<Models.PayFastCheckoutResponse> cb) {
        if (DemoData.isActive()) { demo(cb, DemoData.payFastCheckout(req)); return; }
        run(api.payFastCheckout(key, req), "payfast", cb);
    }

    /** NEW endpoint. */
    public void paymentStatus(long orderId, Result<Models.PaymentStatus> cb) {
        if (DemoData.isActive()) { demo(cb, DemoData.paymentStatus()); return; }
        run(api.paymentStatus(orderId), "payment", cb);
    }

    /** NEW endpoint. */
    public void tracking(long orderId, Result<Models.Tracking> cb) {
        if (DemoData.isActive()) { demo(cb, DemoData.tracking(orderId)); return; }
        run(api.tracking(orderId), "tracking", cb);
    }

    /** NEW endpoint; callers fall back to the phone's own history when {@link #isUnsupported(int)}. */
    public void myOrders(Result<List<Models.OrderHistoryEntry>> cb) { if (DemoData.isActive()) { demo(cb, DemoData.myOrders()); return; } run(api.myOrders(), "orders", cb); }

    // ------------------------------------------------------------------ profile (NEW endpoints)
    public void me(Result<Models.Profile> cb) { if (DemoData.isActive()) { demo(cb, DemoData.profile()); return; } run(api.me(), "me", cb); }

    public void updateMe(Models.Profile p, Result<Models.Profile> cb) { if (DemoData.isActive()) { demo(cb, p); return; } run(api.updateMe(p), "me", cb); }
}
