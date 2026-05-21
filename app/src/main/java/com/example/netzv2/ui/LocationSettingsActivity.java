package com.example.netzv2.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.netzv2.R;
import com.example.netzv2.data.LocationRepository;
import com.example.netzv2.data.Prefs;
import com.example.netzv2.databinding.ActivityLocationSettingsBinding;
import com.example.netzv2.util.LocaleHelper;

public class LocationSettingsActivity extends AppCompatActivity {

    private ActivityLocationSettingsBinding binding;
    private Prefs prefs;
    private LocationRepository location;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings_location_source);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        prefs = new Prefs(this);
        location = new LocationRepository(this);

        boolean manual = Prefs.LOC_MANUAL.equals(prefs.getLocationSource());
        binding.radioGps.setChecked(!manual);
        binding.radioManual.setChecked(manual);
        binding.manualSection.setVisibility(manual ? View.VISIBLE : View.GONE);
        updateStatus();

        binding.groupSource.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_manual) {
                prefs.setLocationSource(Prefs.LOC_MANUAL);
                binding.manualSection.setVisibility(View.VISIBLE);
            } else {
                prefs.setLocationSource(Prefs.LOC_GPS);
                binding.manualSection.setVisibility(View.GONE);
            }
            updateStatus();
        });

        binding.btnSearch.setOnClickListener(v -> performSearch());
        binding.editCity.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String q = binding.editCity.getText() == null ? "" : binding.editCity.getText().toString().trim();
        if (TextUtils.isEmpty(q)) return;
        binding.textStatus.setText(R.string.msg_searching);
        location.searchByName(q, new LocationRepository.PlaceCallback() {
            @Override public void onSuccess(@NonNull LocationRepository.Place place) {
                runOnUiThread(() -> {
                    prefs.setLocationSource(Prefs.LOC_MANUAL);
                    prefs.setManualLocation(place.lat, place.lon, place.displayName);
                    prefs.setManualElevation(place.elevation);
                    if (place.countryCode != null) {
                        prefs.setInIsrael("IL".equalsIgnoreCase(place.countryCode));
                    }
                    binding.editCity.setText("");
                    updateStatus();
                });
            }

            @Override public void onError(@NonNull String message) {
                runOnUiThread(() -> {
                    int msg;
                    switch (message) {
                        case LocationRepository.ERROR_NETWORK:
                            msg = R.string.msg_geocoder_network; break;
                        case LocationRepository.ERROR_NO_GEOCODER:
                            msg = R.string.msg_geocoder_unavailable; break;
                        default:
                            msg = R.string.msg_location_not_found; break;
                    }
                    binding.textStatus.setText(msg);
                });
            }
        });
    }

    private void updateStatus() {
        if (!Prefs.LOC_MANUAL.equals(prefs.getLocationSource())) {
            binding.textStatus.setText("");
            return;
        }
        String name = prefs.getManualName();
        if (TextUtils.isEmpty(name)) {
            binding.textStatus.setText(R.string.settings_manual_none);
        } else {
            binding.textStatus.setText(getString(R.string.settings_manual_current, name));
        }
    }
}
