package com.example.deliveryapp.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Models;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shows PayFast's own payment page. The fields were signed by the server (the passphrase never reaches the phone);
 * this screen only posts them and watches for PayFast sending the customer to the return / cancel URL.
 * Whether the order is actually paid is decided by the server's ITN, which CheckoutActivity asks about afterwards.
 */
public class PayFastActivity extends BaseActivity {
    public static final String EXTRA_SESSION = "session";
    public static final String EXTRA_OUTCOME = "outcome";
    public static final String OUTCOME_RETURNED = "returned";
    public static final String OUTCOME_CANCELLED = "cancelled";

    private WebView web;
    private View progress;
    private String returnUrl, cancelUrl;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payfast);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { askCancel(); }
        });
        setupToolbar("Secure payment").setNavigationOnClickListener(v -> askCancel());

        Models.PayFastSession s = new Gson().fromJson(getIntent().getStringExtra(EXTRA_SESSION), Models.PayFastSession.class);
        returnUrl = withoutQuery(s.returnUrl);
        cancelUrl = withoutQuery(s.cancelUrl);

        progress = findViewById(R.id.progress);
        web = findViewById(R.id.web);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);      // PayFast and bank 3-D Secure pages need it
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return intercept(r.getUrl());
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap favicon) {
                // form posts / redirects don't always go through shouldOverrideUrlLoading
                if (intercept(Uri.parse(url))) { v.stopLoading(); return; }
                Ui.show(progress, true);
            }
            @Override public void onPageFinished(WebView v, String url) {
                Ui.show(progress, false);
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.postUrl(s.processUrl, formBody(s.fields));
    }

    /** True when the URL must not load in the WebView (we either finished, or it isn't a web page). */
    private boolean intercept(Uri uri) {
        if (done) return true;
        String url = uri.toString();
        if (returnUrl != null && url.startsWith(returnUrl)) { finishWith(OUTCOME_RETURNED); return true; }
        if (cancelUrl != null && url.startsWith(cancelUrl)) { finishWith(OUTCOME_CANCELLED); return true; }
        String scheme = uri.getScheme();
        return !"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme);
    }

    private void finishWith(String outcome) {
        done = true;
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_OUTCOME, outcome));
        finish();
    }

    private void askCancel() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Leave payment?")
                .setMessage("Your order is only sent to the kitchen once payment goes through.")
                .setPositiveButton("Leave", (d, w) -> finishWith(OUTCOME_CANCELLED))
                .setNegativeButton("Stay", null)
                .show();
    }


    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    private static String withoutQuery(String url) {
        if (url == null || url.isEmpty()) return null;
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    private static byte[] formBody(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            }
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
