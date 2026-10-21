package com.example.deliveryapp.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.DriverModels;
import com.example.deliveryapp.util.Money;
import com.google.android.material.card.MaterialCardView;

/** Shared read-only order details; delivery actions belong only to the dashboard. */
final class DriverOrderViews {
    private DriverOrderViews() {}

    static LinearLayout addCard(Context context, LinearLayout parent, DriverModels.Delivery delivery) {
        DriverModels.Order order=delivery.order;
        MaterialCardView card=new MaterialCardView(context);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=Ui.dp(context,12); card.setLayoutParams(params);
        card.setRadius(Ui.dp(context,16)); card.setCardElevation(Ui.dp(context,1));
        card.setCardBackgroundColor(context.getColor(R.color.white)); card.setStrokeColor(context.getColor(R.color.outline));
        LinearLayout box=new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL);
        int pad=Ui.dp(context,16); box.setPadding(pad,pad,pad,pad); card.addView(box); parent.addView(card);
        text(context,box,"Order #"+order.id+" · "+safe(delivery.state).replace('_',' '),20,true);
        if (delivery.unfinished()) text(context,box,"Kitchen: "+("Completed".equals(order.status)?"Ready for pickup":safe(order.status)),16,false);
        text(context,box,order.customerName,18,true);
        return box;
    }

    static void addDetails(Context context, LinearLayout box, DriverModels.Delivery delivery) {
        DriverModels.Order order=delivery.order;
        text(context,box,order.address(),16,false);
        if (!safe(order.complexName).isEmpty()) text(context,box,"Unit / complex: "+order.complexName,16,false);
        if (!safe(order.gateAccessCode).isEmpty()) text(context,box,"Gate code: "+order.gateAccessCode,16,false);
        if (!safe(order.notes).isEmpty()) text(context,box,"Notes: "+order.notes,16,false);
        text(context,box,order.contents(),16,false);
        text(context,box,"Order total: "+Money.rand(delivery.total),16,true);
    }

    static void text(Context context,LinearLayout box,String value,int size,boolean bold) {
        if (value==null || value.isEmpty()) return;
        TextView text=new TextView(context); text.setText(value); text.setTextSize(size); text.setTextIsSelectable(true);
        if (bold) text.setTypeface(text.getTypeface(),android.graphics.Typeface.BOLD);
        text.setPadding(0,Ui.dp(context,6),0,Ui.dp(context,6)); box.addView(text);
    }
    private static String safe(String value) { return value==null?"":value; }
}
