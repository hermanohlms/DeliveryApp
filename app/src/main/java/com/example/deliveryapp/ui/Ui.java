package com.example.deliveryapp.ui;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
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
        tv.setPadding(0, dp(c, 24), 0, dp(c, 6));
        tv.setTextSize(20);
        androidx.core.view.ViewCompat.setAccessibilityHeading(tv, true);
        return tv;
    }

    /** Stack the action below the stepper when narrow screens or large text need more room. */
    public static void configureAddBar(android.app.Activity activity) {
        android.content.res.Configuration config = activity.getResources().getConfiguration();
        if (config.screenWidthDp >= 360 && config.fontScale <= 1.15f) return;
        android.widget.LinearLayout row = activity.findViewById(R.id.quantityRow);
        View action = activity.findViewById(R.id.btnAdd);
        android.widget.LinearLayout bar = (android.widget.LinearLayout)row.getParent();
        row.removeView(action); row.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(activity, 8); action.setLayoutParams(lp); bar.addView(action);
    }

    public static void enableInputs(View view, boolean enabled) {
        if (view instanceof android.widget.Button || view instanceof android.widget.EditText) view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enableInputs(group.getChildAt(i), enabled);
        }
    }

    public static TextView sectionHint(Context c, String text) {
        TextView hint = new TextView(c);
        hint.setText(text); hint.setTextSize(13);
        hint.setTextColor(com.google.android.material.color.MaterialColors.getColor(hint, com.google.android.material.R.attr.colorOnSurfaceVariant));
        hint.setPadding(0, 0, 0, dp(c, 8));
        return hint;
    }

    /** A whole-row choice with a dedicated trailing price, separate from the label. */
    public static CompoundButton choiceRow(Context c, String label, Double price, boolean radio, boolean enabled) {
        return new PriceChoice(c, label, price != null && price > 0 ? "+" + Money.rand(price) : "", radio, enabled);
    }

    public static CompoundButton absoluteChoiceRow(Context c, String label, double price, boolean checked) {
        CompoundButton row = new PriceChoice(c, label, Money.rand(price), true, true); row.setChecked(checked); return row;
    }

    private static final class PriceChoice extends androidx.appcompat.widget.AppCompatCheckBox {
        private final android.graphics.Paint pricePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final String price;
        private final boolean radio;
        PriceChoice(Context c, String label, String amount, boolean radio, boolean enabled) {
            super(c);
            this.radio = radio;
            price = amount;
            pricePaint.setTextSize(14 * c.getResources().getDisplayMetrics().scaledDensity);
            pricePaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            pricePaint.setColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
            if (radio) setButtonDrawable(androidx.core.widget.CompoundButtonCompat.getButtonDrawable(
                    new androidx.appcompat.widget.AppCompatRadioButton(c)));
            androidx.core.widget.CompoundButtonCompat.setButtonTintList(this,
                    androidx.appcompat.content.res.AppCompatResources.getColorStateList(c, R.color.option_control));
            setText(label); setTextSize(16); setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
            setBackgroundResource(R.drawable.bg_option);
            setEnabled(enabled); setMinHeight(dp(c, 60)); setGravity(android.view.Gravity.CENTER_VERTICAL);
            setPadding(dp(c, 12), dp(c, 12), dp(c, 16) + (price.isEmpty() ? 0 : (int)pricePaint.measureText(price) + dp(c, 12)), dp(c, 12));
            setCompoundDrawablePadding(dp(c, 8));
            if (!price.isEmpty()) setContentDescription(label + ", " + price);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(c, 6); setLayoutParams(lp);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (!price.isEmpty()) {
                pricePaint.setAlpha(isEnabled() ? 255 : 150);
                float x = getWidth() - dp(getContext(), 16) - pricePaint.measureText(price);
                float y = (getHeight() - pricePaint.ascent() - pricePaint.descent()) / 2f;
                canvas.drawText(price, x, y, pricePaint);
            }
        }
        @Override public CharSequence getAccessibilityClassName() {
            return radio ? "android.widget.RadioButton" : "android.widget.CheckBox";
        }
    }
}
