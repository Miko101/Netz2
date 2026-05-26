package com.example.netzv2.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.netzv2.R;
import com.example.netzv2.data.Prefs;
import com.example.netzv2.databinding.ActivityTfilaSettingsBinding;
import com.example.netzv2.databinding.RowTfilaInputBinding;
import com.example.netzv2.util.LocaleHelper;

import java.util.ArrayList;
import java.util.List;

public class TfilaSettingsActivity extends AppCompatActivity {

    private ActivityTfilaSettingsBinding binding;
    private Prefs prefs;
    private final RowTfilaInputBinding[] rows = new RowTfilaInputBinding[Prefs.TFILA_KEYS.length];

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTfilaSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_tfila);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        prefs = new Prefs(this);

        initRows();
        loadPrefs();

        binding.btnSave.setOnClickListener(v -> save());
    }

    private void initRows() {
        int[] labels = {
                R.string.milestone_hodu, R.string.milestone_hashem_melech,
                R.string.milestone_baruch_sheamar, R.string.milestone_vayivarech_david,
                R.string.milestone_yishtabach, R.string.milestone_kadosh,
                R.string.milestone_shema, R.string.milestone_emet,
                R.string.milestone_ezrat_avotenu, R.string.milestone_tehilot,
                R.string.milestone_amida
        };

        View[] viewHolders = {
                binding.rowHodu.getRoot(), binding.rowHashemMelech.getRoot(),
                binding.rowBaruchSheamar.getRoot(), binding.rowVayivarechDavid.getRoot(),
                binding.rowYishtabach.getRoot(), binding.rowKadosh.getRoot(),
                binding.rowShema.getRoot(), binding.rowEmet.getRoot(),
                binding.rowEzratAvotenu.getRoot(), binding.rowTehilot.getRoot(),
                binding.rowAmida.getRoot()
        };

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RowTfilaInputBinding.bind(viewHolders[i]);
            rows[i].milestoneLabel.setText(labels[i]);
        }
    }

    private void loadPrefs() {
        for (int i = 0; i < rows.length; i++) {
            String display = prefs.getTfilaOffsetDisplay(i);
            if (!TextUtils.isEmpty(display)) {
                rows[i].editOffset.setText(display);
            }
        }
    }

    private void save() {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            String s = rows[i].editOffset.getText().toString().trim();
            if (s.isEmpty()) {
                values.add(null);
                continue;
            }
            Integer seconds = Prefs.parseOffsetSeconds(s);
            if (seconds == null) {
                Toast.makeText(this, R.string.msg_error_invalid_offset, Toast.LENGTH_LONG).show();
                return;
            }
            values.add(seconds);
        }

        // Validation: chronological order (descending seconds).
        Integer last = null;
        for (Integer v : values) {
            if (v != null) {
                if (last != null && v > last) {
                    Toast.makeText(this, R.string.msg_error_chronological, Toast.LENGTH_LONG).show();
                    return;
                }
                last = v;
            }
        }

        for (int i = 0; i < values.size(); i++) {
            prefs.setTfilaOffsetSeconds(i, values.get(i));
        }

        finish();
    }
}
