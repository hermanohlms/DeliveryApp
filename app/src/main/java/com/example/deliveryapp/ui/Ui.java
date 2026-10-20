package com.example.deliveryapp.ui;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deliveryapp.R;
import com.example.deliveryapp.util.Money;

import java.util.regex.Pattern;

/** Small view helpers shared by every screen. */
public final class Ui {
    private Ui() {}

    // Same rules the backend uses
    public static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{7,20}$");
    public static final Pattern GATE = Pattern.compile("^[A-Za-z0-9 #*]{0,64}$");

    public static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static void show(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static void toast(Context c, String message) {
        Toast.makeText(c, message, Toast.LENGTH_LONG).show();
    }

    public static void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    public static TextView sectionTitle(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextAppearance(R.style.Text_Section);
        int pad = dp(c, 16);
        tv.setPadding(0, pad, 0, dp(c, 4));
        return tv;
    }

    /** One selectable line: a radio button (single choice) or checkbox (multi choice) with an optional price. */
    public static CompoundButton choiceRow(Context c, String label, Double price, boolean radio, boolean enabled) {
        CompoundButton b = radio ? new RadioButton(c) : new CheckBox(c);
        String text = label;
        if (price != null && price > 0) text += "   (+" + Money.rand(price) + ")";
        b.setText(text);
        b.setEnabled(enabled);
        b.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        b.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        return b;
    }
}
