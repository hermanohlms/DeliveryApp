package com.example.deliveryapp;

import android.app.Application;
import android.content.Intent;

import com.example.deliveryapp.data.Cart;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.example.deliveryapp.ui.LandingActivity;
import com.google.android.libraries.places.api.Places;

public class App extends Application {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Prefs.init(this);
        Repo.init(this);
        if (!BuildConfig.MAPS_API_KEY.isEmpty() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), BuildConfig.MAPS_API_KEY);
        }
    }

    /** Sends the user back to the public landing screen and clears every other screen. */
    public static void goToLogin() {
        Cart.get().clear();
        Intent i = new Intent(instance, LandingActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        instance.startActivity(i);
    }
}
