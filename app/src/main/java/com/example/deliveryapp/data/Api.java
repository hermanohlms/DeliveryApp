package com.example.deliveryapp.data;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Mirrors the Spring Boot controllers. Endpoints marked NEW do not exist on the backend yet. */
public interface Api {
    @GET("api/driver") Call<DriverModels.Dashboard> driverDashboard();
    @POST("api/driver/clock-in") Call<DriverModels.Shift> driverClockIn();
    @POST("api/driver/clock-out") Call<DriverModels.Shift> driverClockOut();
    @POST("api/driver/claim") Call<DriverModels.Dashboard> driverClaim();
    @POST("api/driver/orders/{id}/pickup") Call<DriverModels.Dashboard> driverPickup(@Path("id") long id);
    @POST("api/driver/orders/{id}/delivered") Call<DriverModels.Dashboard> driverDelivered(@Path("id") long id);
    @POST("api/auth/login")
    Call<Models.AuthResponse> login(@Body Models.LoginRequest body);

    @POST("api/auth/forgot-password")
    Call<Models.MessageResponse> forgotPassword(@Body Models.ForgotPasswordRequest body);

    /** NEW: create a customer account. Expected: 201 + AuthResponse (or empty body). */
    @POST("api/auth/register")
    Call<ResponseBody> register(@Body Models.RegisterRequest body);

    @GET("api/branches/{branchId}/pizzas")
    Call<Models.PizzaCategorySplit> pizzas(@Path("branchId") int branchId);

    @GET("api/branches/{branchId}/pizzas/{pizzaId}")
    Call<Models.PizzaDetail> pizzaDetail(@Path("branchId") int branchId, @Path("pizzaId") int pizzaId,
                                         @Query("sizeCm") Integer sizeCm);

    @POST("api/branches/{branchId}/pizzas/{pizzaId}/quote")
    Call<Models.QuoteResponse> quote(@Path("branchId") int branchId, @Path("pizzaId") int pizzaId,
                                     @Body Models.QuoteRequest body);

    @GET("api/branches/{branchId}/menu-items")
    Call<List<Models.MenuItem>> menuItems(@Path("branchId") int branchId);

    @GET("api/branches/{branchId}/specials/week")
    Call<List<Models.Special>> specials(@Path("branchId") int branchId);

    @POST("api/branches/{branchId}/specials/{specialId}/quote")
    Call<Models.SpecialQuote> specialQuote(@Path("branchId") int branchId,
                                          @Path("specialId") long specialId,
                                          @Body Models.SpecialItemRequest body);

    @POST("api/orders")
    Call<Models.OrderResponse> placeOrder(@Header("Idempotency-Key") String key, @Body Models.OrderRequest body);

    /** NEW: create the order and a signed PayFast payment for it in one call (nothing is created if this is missing). */
    @POST("api/orders/payfast")
    Call<Models.PayFastCheckoutResponse> payFastCheckout(@Header("Idempotency-Key") String key, @Body Models.OrderRequest body);

    /** NEW: whether PayFast has confirmed payment for the customer's order (via ITN). */
    @GET("api/orders/{orderId}/payment")
    Call<Models.PaymentStatus> paymentStatus(@Path("orderId") long orderId);

    /** NEW: live tracking for one of the customer's orders (status, ETA, driver position). */
    @GET("api/orders/{orderId}/tracking")
    Call<Models.Tracking> tracking(@Path("orderId") long orderId);

    /** NEW: the signed-in customer's own orders, newest first. */
    @GET("api/orders/mine")
    Call<List<Models.OrderHistoryEntry>> myOrders();

    /** NEW: the signed-in customer's profile. */
    @GET("api/me")
    Call<Models.Profile> me();

    /** NEW: update the signed-in customer's profile. */
    @PUT("api/me")
    Call<Models.Profile> updateMe(@Body Models.Profile body);
}
