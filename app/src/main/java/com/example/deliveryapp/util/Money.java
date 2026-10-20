package com.example.deliveryapp.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Money {
    private Money() {}

    public static String rand(double v) { return "R" + String.format(Locale.US, "%.2f", v); }

    public static String dateTime(long millis) {
        return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
