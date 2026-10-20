package com.example.deliveryapp.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Models;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AddressComponent;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.List;

/**
 * Address search backed by Google Places. The backend only accepts addresses that were picked from Google
 * (place id + coordinates), so free-typed addresses are never treated as valid.
 */
public class AddressPickerView extends LinearLayout {
    public interface Listener { void onAddressChanged(Models.AddressSelection selection); }

    private final TextInputEditText input;
    private final TextView status;
    private final LinearLayout suggestions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlacesClient client;
    private AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();
    private Models.AddressSelection selection;
    private boolean settingText;
    private Runnable pendingSearch;
    private Listener listener;
    private String errorText;

    public AddressPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_address_picker, this, true);
        input = findViewById(R.id.addrInput);
        status = findViewById(R.id.addrStatus);
        suggestions = findViewById(R.id.addrSuggestions);

        if (Places.isInitialized()) client = Places.createClient(context);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (settingText) return;
                boolean had = selection != null;
                selection = null;
                errorText = null;
                updateStatus();
                if (had && listener != null) listener.onAddressChanged(null);
                scheduleSearch(s.toString().trim());
            }
        });
        updateStatus();
    }

    public void setListener(Listener l) { this.listener = l; }

    public Models.AddressSelection getSelection() { return selection; }

    public boolean isVerified() { return selection != null && selection.isVerified(); }

    /** Pre-fill (e.g. from the saved profile) without starting a search. */
    public void setSelection(Models.AddressSelection s) {
        selection = s;
        settingText = true;
        input.setText(s == null || s.formattedAddress == null ? "" : s.formattedAddress);
        settingText = false;
        suggestions.removeAllViews();
        errorText = null;
        updateStatus();
    }

    public void setHint(String hint) {
        ((com.google.android.material.textfield.TextInputLayout) findViewById(R.id.addrLayout)).setHint(hint);
    }

    // ------------------------------------------------------------------ search
    private void scheduleSearch(final String query) {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        suggestions.removeAllViews();
        if (client == null || query.length() < 2) return;
        pendingSearch = () -> search(query);
        handler.postDelayed(pendingSearch, 300);
    }

    private void search(final String query) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query).setCountries("ZA").setSessionToken(token).build();
        client.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    String current = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!current.equals(query) || selection != null) return;   // stale result
                    showSuggestions(response.getAutocompletePredictions());
                })
                .addOnFailureListener(e -> {
                    errorText = "Address search failed. Check your connection and Maps key.";
                    updateStatus();
                });
    }

    private void showSuggestions(List<AutocompletePrediction> predictions) {
        suggestions.removeAllViews();
        for (final AutocompletePrediction p : predictions) {
            TextView row = new TextView(getContext());
            row.setText(p.getFullText(null));
            int pad = Ui.dp(getContext(), 12);
            row.setPadding(Ui.dp(getContext(), 8), pad, Ui.dp(getContext(), 8), pad);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setClickable(true);
            row.setOnClickListener((View v) -> choose(p));
            suggestions.addView(row);
        }
    }

    private void choose(AutocompletePrediction p) {
        suggestions.removeAllViews();
        Ui.hideKeyboard(input);
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.ADDRESS_COMPONENTS);
        FetchPlaceRequest request = FetchPlaceRequest.builder(p.getPlaceId(), fields).setSessionToken(token).build();
        client.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Models.AddressSelection result = toSelection(response.getPlace());
                    token = AutocompleteSessionToken.newInstance();   // a session ends when the place is fetched
                    selection = result;
                    settingText = true;
                    input.setText(result.formattedAddress == null ? "" : result.formattedAddress);
                    settingText = false;
                    errorText = result.isVerified() ? null
                            : "That address is missing a street or suburb. Pick a more specific one.";
                    updateStatus();
                    if (listener != null) listener.onAddressChanged(result);
                })
                .addOnFailureListener(e -> {
                    errorText = "Couldn't load that address. Try again.";
                    updateStatus();
                });
    }

    private static String part(Place place, String... types) {
        if (place.getAddressComponents() == null) return null;
        for (AddressComponent c : place.getAddressComponents().asList()) {
            for (String t : types) {
                if (c.getTypes().contains(t)) return c.getName();
            }
        }
        return null;
    }

    private static Models.AddressSelection toSelection(Place place) {
        Models.AddressSelection a = new Models.AddressSelection();
        a.googlePlaceId = place.getId();
        a.formattedAddress = place.getAddress();
        if (place.getLatLng() != null) {
            a.latitude = place.getLatLng().latitude;
            a.longitude = place.getLatLng().longitude;
        }
        a.houseNumber = part(place, "street_number");
        a.street = part(place, "route");
        a.area = part(place, "sublocality_level_1", "sublocality", "neighborhood");
        a.city = part(place, "locality", "administrative_area_level_2");
        a.postalCode = part(place, "postal_code");
        a.province = part(place, "administrative_area_level_1");
        a.country = part(place, "country");
        return a;
    }

    private void updateStatus() {
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        if (errorText != null) {
            status.setText(errorText);
            status.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError));
        } else if (isVerified()) {
            status.setText("Address confirmed ✓");
            status.setTextColor(getResources().getColor(R.color.steel_blue, null));
        } else if (client == null) {
            status.setText("Address search is off: add MAPS_API_KEY to local.properties.");
            status.setTextColor(outline);
        } else {
            status.setText("Start typing, then pick your address from the list.");
            status.setTextColor(outline);
        }
    }
}
