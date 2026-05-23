package com.example.netzv2.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.netzv2.R;
import com.example.netzv2.data.CustomNetzTable;
import com.example.netzv2.data.Prefs;
import com.example.netzv2.databinding.ActivitySettingsBinding;
import com.example.netzv2.databinding.RowSettingBinding;
import com.example.netzv2.util.LocaleHelper;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private Prefs prefs;

    private RowSettingBinding rowLanguage;
    private RowSettingBinding rowLocation;
    private RowSettingBinding rowTfila;
    private RowSettingBinding rowCalc;
    private RowSettingBinding rowAbout;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_settings);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        prefs = new Prefs(this);

        rowLanguage = RowSettingBinding.bind(binding.rowLanguage.getRoot());
        rowLocation = RowSettingBinding.bind(binding.rowLocation.getRoot());
        rowTfila = RowSettingBinding.bind(binding.rowTfila.getRoot());
        rowCalc = RowSettingBinding.bind(binding.rowCalc.getRoot());
        rowAbout = RowSettingBinding.bind(binding.rowAbout.getRoot());

        rowLanguage.rowTitle.setText(R.string.settings_language);
        rowLocation.rowTitle.setText(R.string.settings_location_source);
        rowTfila.rowTitle.setText(R.string.title_tfila);
        rowCalc.rowTitle.setText(R.string.settings_calc_method);
        rowAbout.rowTitle.setText(R.string.action_about_calc);
        rowAbout.rowSubtitle.setVisibility(android.view.View.GONE);

        bindSubtitles();
        initInIsraelSwitch();

        rowLanguage.getRoot().setOnClickListener(v -> showLanguageDialog());
        rowLocation.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, LocationSettingsActivity.class)));
        rowTfila.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, TfilaSettingsActivity.class)));
        rowCalc.getRoot().setOnClickListener(v -> showCalcDialog());
        rowAbout.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
    }

    private void bindSubtitles() {
        rowLanguage.rowSubtitle.setText(languageLabel(prefs.getLanguage()));
        rowLocation.rowSubtitle.setText(locationLabel(prefs.getLocationSource()));
        rowTfila.rowSubtitle.setText(tfilaLabel());
        rowCalc.rowSubtitle.setText(calcLabel(prefs.getCalcMethod()));
    }

    private void initInIsraelSwitch() {
        binding.switchInIsrael.setChecked(prefs.getInIsrael());
        binding.rowInIsrael.setOnClickListener(v -> {
            boolean newVal = !binding.switchInIsrael.isChecked();
            binding.switchInIsrael.setChecked(newVal);
            prefs.setInIsrael(newVal);
        });
    }

    private String languageLabel(String key) {
        switch (key) {
            case Prefs.LANG_ENGLISH: return getString(R.string.lang_english);
            case Prefs.LANG_HEBREW: return getString(R.string.lang_hebrew);
            default: return getString(R.string.lang_system);
        }
    }

    private String locationLabel(String key) {
        if (Prefs.LOC_MANUAL.equals(key)) {
            String name = prefs.getManualName();
            if (!TextUtils.isEmpty(name)) {
                return getString(R.string.loc_source_manual) + " · " + name;
            }
            return getString(R.string.loc_source_manual);
        }
        return getString(R.string.loc_source_gps);
    }

    private String tfilaLabel() {
        return prefs.hasTfilaConfig() ? getString(R.string.settings_tfila_desc) : "";
    }

    private String calcLabel(String key) {
        switch (key) {
            case Prefs.CALC_LOCAL_NIREH: return getString(R.string.calc_local_nireh);
            case Prefs.CALC_HEBCAL_MISHOR: return getString(R.string.calc_hebcal_mishor);
            case Prefs.CALC_CUSTOMIZED:
                return getString(R.string.calc_customized) + " · " + customCityLabel(prefs.getCustomCity());
            default: return getString(R.string.calc_local_mishor);
        }
    }

    private String customCityLabel(String city) {
        if (CustomNetzTable.CITY_BET_EL.equals(city)) {
            return getString(R.string.custom_city_bet_el);
        }
        return city == null ? "" : city;
    }

    private void showLanguageDialog() {
        String[] keys = { Prefs.LANG_SYSTEM, Prefs.LANG_ENGLISH, Prefs.LANG_HEBREW };
        String[] labels = {
                getString(R.string.lang_system),
                getString(R.string.lang_english),
                getString(R.string.lang_hebrew)
        };
        int checked = indexOf(keys, prefs.getLanguage(), 0);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String prev = prefs.getLanguage();
                    String next = keys[which];
                    dialog.dismiss();
                    if (!next.equals(prev)) {
                        prefs.setLanguage(next);
                        relaunch();
                    } else {
                        bindSubtitles();
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pick up location changes from the sub-screen.
        bindSubtitles();
        binding.switchInIsrael.setChecked(prefs.getInIsrael());
    }

    private void showCalcDialog() {
        String[] keys = {
                Prefs.CALC_LOCAL_MISHOR,
                Prefs.CALC_LOCAL_NIREH,
                Prefs.CALC_HEBCAL_MISHOR,
                Prefs.CALC_CUSTOMIZED
        };
        String[] labels = {
                getString(R.string.calc_local_mishor),
                getString(R.string.calc_local_nireh),
                getString(R.string.calc_hebcal_mishor),
                getString(R.string.calc_customized)
        };
        int checked = indexOf(keys, prefs.getCalcMethod(), 0);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_calc_method)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String selected = keys[which];
                    dialog.dismiss();
                    if (Prefs.CALC_CUSTOMIZED.equals(selected)) {
                        showCustomCityDialog();
                    } else {
                        prefs.setCalcMethod(selected);
                        bindSubtitles();
                    }
                })
                .show();
    }

    private void showCustomCityDialog() {
        String[] cityKeys = CustomNetzTable.CITY_KEYS;
        String[] cityLabels = new String[cityKeys.length];
        for (int i = 0; i < cityKeys.length; i++) {
            cityLabels[i] = customCityLabel(cityKeys[i]);
        }
        String current = Prefs.CALC_CUSTOMIZED.equals(prefs.getCalcMethod())
                ? prefs.getCustomCity()
                : cityKeys[0];
        int checked = indexOf(cityKeys, current, 0);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_custom_city)
                .setSingleChoiceItems(cityLabels, checked, (dialog, which) -> {
                    prefs.setCustomCity(cityKeys[which]);
                    prefs.setCalcMethod(Prefs.CALC_CUSTOMIZED);
                    dialog.dismiss();
                    bindSubtitles();
                })
                .show();
    }

    private static int indexOf(String[] arr, String value, int fallback) {
        if (value == null) return fallback;
        for (int i = 0; i < arr.length; i++) {
            if (value.equals(arr[i])) return i;
        }
        return fallback;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void relaunch() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        finishAffinity();
        startActivity(intent);
    }
}
