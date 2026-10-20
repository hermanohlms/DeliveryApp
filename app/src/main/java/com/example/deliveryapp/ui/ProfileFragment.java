package com.example.deliveryapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.deliveryapp.App;
import com.example.deliveryapp.R;
import com.example.deliveryapp.data.Models;
import com.example.deliveryapp.data.Prefs;
import com.example.deliveryapp.data.Repo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;

/** The customer's details. Syncs with GET/PUT /api/me when the server has it, otherwise saves on the phone. */
public class ProfileFragment extends Fragment {
    private TextInputEditText etFirst, etLast, etPhone, etPhone2, etComplex;
    private AddressPickerView address;
    private TextView tvMessage;
    private MaterialButton btnSave;
    private View progress;
    private Models.Profile loaded = new Models.Profile();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        etFirst = v.findViewById(R.id.etFirst);
        etLast = v.findViewById(R.id.etLast);
        etPhone = v.findViewById(R.id.etPhone);
        etPhone2 = v.findViewById(R.id.etPhone2);
        etComplex = v.findViewById(R.id.etComplex);
        address = v.findViewById(R.id.profileAddress);
        tvMessage = v.findViewById(R.id.tvMessage);
        btnSave = v.findViewById(R.id.btnSave);
        progress = v.findViewById(R.id.progress);
        ((TextView) v.findViewById(R.id.tvSignedIn)).setText(Prefs.get().getUsername());

        fill(Prefs.get().getProfile());          // what we already have on the phone
        loadFromServer();                        // then refresh from the server if it supports it

        btnSave.setOnClickListener(x -> save());
        v.findViewById(R.id.btnLogout).setOnClickListener(x -> {
            Repo.get().logout();
            App.goToLogin();
        });
    }

    private void fill(Models.Profile p) {
        loaded = p;
        etFirst.setText(p.firstName);
        etLast.setText(p.lastName);
        etPhone.setText(p.phone);
        etPhone2.setText(p.phone2);
        etComplex.setText(p.complexName != null ? p.complexName : p.address != null ? p.address.complexName : null);
        address.setSelection(p.address);
    }

    private void loadFromServer() {
        Ui.show(progress, true);
        Repo.get().me(new Repo.Result<Models.Profile>() {
            @Override public void onSuccess(Models.Profile p) {
                if (!isAdded() || getView() == null) return;
                Ui.show(progress, false);
                if (p != null) { Prefs.get().saveProfile(p); fill(p); }
            }
            @Override public void onError(String message, int code) {
                if (!isAdded() || getView() == null) return;
                Ui.show(progress, false);   // server doesn't have /api/me yet: keep the local copy, say nothing
            }
        });
    }

    private void message(String text, boolean error) {
        tvMessage.setText(text);
        tvMessage.setTextColor(error
                ? MaterialColors.getColor(tvMessage, com.google.android.material.R.attr.colorError)
                : getResources().getColor(R.color.steel_blue, requireContext().getTheme()));
        Ui.show(tvMessage, text != null);
    }

    private void save() {
        String first = Ui.text(etFirst), last = Ui.text(etLast), phone = Ui.text(etPhone), phone2 = Ui.text(etPhone2);
        if (first.isEmpty() || last.isEmpty()) { message("Enter your first and last name.", true); return; }
        if (!Ui.PHONE.matcher(phone).matches()) { message("Enter a valid phone number (7–20 digits).", true); return; }
        if (!phone2.isEmpty() && !Ui.PHONE.matcher(phone2).matches()) { message("The second phone number isn't valid.", true); return; }
        if (address.getSelection() != null && !address.isVerified()) {
            message("Pick your address from the suggestions.", true);
            return;
        }

        final Models.Profile p = new Models.Profile();
        p.firstName = first; p.lastName = last; p.phone = phone; p.phone2 = phone2.isEmpty() ? null : phone2;
        p.email = loaded.email != null ? loaded.email : Prefs.get().getUsername();
        p.preferredStore = loaded.preferredStore;
        p.complexName = Ui.text(etComplex).isEmpty() ? null : Ui.text(etComplex);
        p.address = address.getSelection();
        if (p.address != null) p.address.complexName = p.complexName;

        Ui.hideKeyboard(btnSave);
        btnSave.setEnabled(false);
        Repo.get().updateMe(p, new Repo.Result<Models.Profile>() {
            @Override public void onSuccess(Models.Profile saved) {
                if (!isAdded() || getView() == null) return;
                btnSave.setEnabled(true);
                Prefs.get().saveProfile(saved != null ? saved : p);
                loaded = saved != null ? saved : p;
                message("Saved ✓", false);
            }
            @Override public void onError(String m, int code) {
                if (!isAdded() || getView() == null) return;
                btnSave.setEnabled(true);
                if (Repo.isUnsupported(code) || code == 0) {
                    Prefs.get().saveProfile(p);   // keep it on the phone until the server supports profiles
                    loaded = p;
                    message("Saved on this phone. It will sync once the server is updated.", false);
                } else {
                    message(m, true);
                }
            }
        });
    }
}
