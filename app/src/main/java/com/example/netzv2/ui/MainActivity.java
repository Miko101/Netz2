package com.example.netzv2.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.netzv2.R;
import com.example.netzv2.data.Prefs;
import com.example.netzv2.databinding.ActivityMainBinding;
import com.example.netzv2.databinding.RowInfoBinding;
import com.example.netzv2.util.HebrewDateFormatter;
import com.example.netzv2.util.JewishStatus;
import com.example.netzv2.util.LocaleHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;

    private RowInfoBinding rowDate;
    private RowInfoBinding rowStatus;
    private RowInfoBinding rowLocation;

    private ValueAnimator blinkAnimator;

    private final Handler drumHandler = new Handler(Looper.getMainLooper());
    private static final long DRUM_FRAME_MS = 33L; // ~30fps
    private java.util.List<MainViewModel.Milestone> cachedMilestones;
    private Date cachedNetz;
    private final Runnable drumTick = new Runnable() {
        @Override
        public void run() {
            updateDrumFrame();
            drumHandler.postDelayed(this, DRUM_FRAME_MS);
        }
    };

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    viewModel.requestGpsLocation();
                } else {
                    binding.manualCard.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rowDate = RowInfoBinding.bind(binding.rowDate.getRoot());
        rowStatus = RowInfoBinding.bind(binding.rowStatus.getRoot());
        rowLocation = RowInfoBinding.bind(binding.rowLocation.getRoot());

        rowDate.rowIcon.setImageResource(R.drawable.ic_calendar);
        rowStatus.rowIcon.setImageResource(R.drawable.ic_star);
        rowLocation.rowIcon.setImageResource(R.drawable.ic_place);

        rowDate.rowValue.setText(R.string.placeholder_dash);
        rowStatus.rowValue.setText(R.string.placeholder_dash);
        rowLocation.rowValue.setText(R.string.placeholder_dash);

        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        binding.btnRefresh.setOnClickListener(v -> viewModel.refresh());

        binding.btnGrant.setOnClickListener(v ->
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION));
        binding.btnUseGps.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                viewModel.requestGpsLocation();
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        });
        binding.btnSearch.setOnClickListener(v -> performManualSearch());
        binding.editCity.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performManualSearch();
                return true;
            }
            return false;
        });

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.getState().observe(this, this::renderState);
        viewModel.getLocationState().observe(this, this::renderLocation);
        viewModel.getNeedsLocationInput().observe(this, needsInput ->
                binding.manualCard.setVisibility(needsInput != null && needsInput ? View.VISIBLE : View.GONE));

        viewModel.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasLocationPermission() && !Prefs.LOC_MANUAL.equals(new Prefs(this).getLocationSource())) {
            viewModel.requestGpsLocation();
        }
        viewModel.refreshMilestones();
        drumHandler.removeCallbacks(drumTick);
        drumHandler.post(drumTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        drumHandler.removeCallbacks(drumTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBlink();
        drumHandler.removeCallbacks(drumTick);
    }

    private void performManualSearch() {
        String q = binding.editCity.getText() == null ? "" : binding.editCity.getText().toString().trim();
        if (TextUtils.isEmpty(q)) return;
        viewModel.searchManual(q);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void renderState(MainViewModel.State s) {
        if (s == null) return;
        binding.offlineIndicator.setVisibility(s.fromCache ? View.VISIBLE : View.GONE);

        renderJewishInfo(s);
        renderTfilaMilestones(s);

        switch (s.status) {
            case LOADING:
                stopBlink();
                binding.countdownLabel.setText(R.string.label_next_netz);
                binding.countdownValue.setText(R.string.loading);
                binding.countdownSubtitle.setVisibility(View.GONE);
                break;

            case READY:
                stopBlink();
                binding.countdownLabel.setText(R.string.label_next_netz);
                binding.countdownValue.setText(formatRemaining(s.remainingMs));
                binding.countdownSubtitle.setText(getString(R.string.subtitle_at, formatTime(s.netz)));
                binding.countdownSubtitle.setVisibility(View.VISIBLE);
                break;

            case BLINKING:
                binding.countdownLabel.setText(R.string.netz_passed_short);
                binding.countdownValue.setText(formatTime(s.netz));
                binding.countdownSubtitle.setVisibility(View.GONE);
                startBlink();
                break;

            case NEXT_ZMAN:
                stopBlink();
                binding.countdownLabel.setText(getString(R.string.label_until, getString(s.nextZmanLabelRes)));
                binding.countdownValue.setText(formatRemaining(s.remainingMs));
                binding.countdownSubtitle.setText(getString(R.string.subtitle_at, formatTime(s.nextZmanTime)));
                binding.countdownSubtitle.setVisibility(View.VISIBLE);
                break;

            case POST_DAY:
                stopBlink();
                binding.countdownLabel.setText(R.string.post_day_label);
                binding.countdownValue.setText(R.string.placeholder_dash);
                binding.countdownSubtitle.setVisibility(View.GONE);
                break;

            case ERROR:
                stopBlink();
                binding.countdownLabel.setText(R.string.label_next_netz);
                binding.countdownValue.setText(s.errorMessage == null
                        ? getString(R.string.msg_fetch_failed) : s.errorMessage);
                binding.countdownSubtitle.setVisibility(View.GONE);
                break;
        }
    }

    private void renderJewishInfo(MainViewModel.State s) {
        Locale loc = LocaleHelper.currentLocale(this);
        Prefs prefs = new Prefs(this);
        
        Date now = new Date();
        Date activeHalachicDate = now;
        
        if (s.todayTzeit != null && now.after(s.todayTzeit)) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            cal.add(Calendar.DAY_OF_YEAR, 1);
            activeHalachicDate = cal.getTime();
        }

        rowDate.rowValue.setText(HebrewDateFormatter.dayAndMonth(activeHalachicDate, loc));
        
        String status = JewishStatus.describe(activeHalachicDate, loc, prefs.getInIsrael());
        rowStatus.rowValue.setText(TextUtils.isEmpty(status) ? getString(R.string.placeholder_dash) : status);
    }

    private void renderTfilaMilestones(MainViewModel.State s) {
        if (binding.tfilaCard == null) return;

        long now = System.currentTimeMillis();
        boolean shouldShow = s.milestones != null && !s.milestones.isEmpty()
                && s.status != MainViewModel.Status.LOADING
                && s.status != MainViewModel.Status.ERROR
                && s.status != MainViewModel.Status.POST_DAY
                && (s.netz == null || now - s.netz.getTime() <= MainViewModel.TFILA_CARD_DURATION_MS);

        if (!shouldShow) {
            binding.tfilaCard.setVisibility(View.GONE);
            cachedMilestones = null;
            cachedNetz = null;
            return;
        }

        binding.tfilaCard.setVisibility(View.VISIBLE);
        cachedMilestones = s.milestones;
        cachedNetz = s.netz;
        updateDrumFrame();
    }

    private void updateDrumFrame() {
        if (binding == null || binding.tfilaCard.getVisibility() != View.VISIBLE) return;
        if (cachedMilestones == null || cachedMilestones.isEmpty() || cachedNetz == null) return;

        long now = System.currentTimeMillis();
        int idx = MainViewModel.findCurrentMilestoneIndex(cachedMilestones, now);
        float progress = MainViewModel.calculateProgress(cachedMilestones, idx, now, cachedNetz);

        TextView tvPrev = binding.tfilaCard.findViewById(R.id.tf_prev);
        TextView tvCurr = binding.tfilaCard.findViewById(R.id.tf_current);
        TextView tvNext = binding.tfilaCard.findViewById(R.id.tf_next);
        TextView tvAhead = binding.tfilaCard.findViewById(R.id.tf_lookahead);

        setMilestoneLabel(tvPrev, idx - 1, cachedMilestones);
        setMilestoneLabel(tvCurr, idx, cachedMilestones);
        setMilestoneLabel(tvNext, idx + 1, cachedMilestones);
        setMilestoneLabel(tvAhead, idx + 2, cachedMilestones);

        int pitch = 0;
        if (tvCurr != null && tvNext != null) {
            pitch = tvNext.getTop() - tvCurr.getTop();
        }
        if (pitch <= 0) {
            pitch = getResources().getDimensionPixelSize(R.dimen.tfila_slot_pitch);
        }
        float ty = -pitch * progress;
        if (tvPrev != null) tvPrev.setTranslationY(ty);
        if (tvCurr != null) tvCurr.setTranslationY(ty);
        if (tvNext != null) tvNext.setTranslationY(ty);
        if (tvAhead != null) tvAhead.setTranslationY(ty);

        if (tvPrev != null) tvPrev.setAlpha(0.4f * (1f - progress));
        if (tvCurr != null) tvCurr.setAlpha(1f - 0.6f * progress);
        if (tvNext != null) tvNext.setAlpha(0.4f + 0.6f * progress);
        if (tvAhead != null) tvAhead.setAlpha(0.4f * progress);
    }

    private void setMilestoneLabel(TextView tv, int index, java.util.List<MainViewModel.Milestone> milestones) {
        if (tv == null) return;
        if (index >= 0 && index < milestones.size()) {
            tv.setText(milestones.get(index).labelRes);
        } else {
            tv.setText("");
        }
        tv.setVisibility(View.VISIBLE);
    }

    private boolean isSameDay(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;
        Calendar c1 = Calendar.getInstance(); c1.setTime(d1);
        Calendar c2 = Calendar.getInstance(); c2.setTime(d2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private void renderLocation(MainViewModel.LocationState ls) {
        if (ls == null) {
            rowLocation.rowValue.setText(R.string.placeholder_dash);
            return;
        }
        if (!TextUtils.isEmpty(ls.displayName)) {
            rowLocation.rowValue.setText(ls.displayName);
        } else if (ls.lat != null && ls.lon != null) {
            rowLocation.rowValue.setText(String.format(Locale.getDefault(), "%.3f, %.3f", ls.lat, ls.lon));
        } else {
            rowLocation.rowValue.setText(R.string.placeholder_dash);
        }
    }

    private String formatTime(Date d) {
        if (d == null) return getString(R.string.placeholder_dash);
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a";
        return new SimpleDateFormat(pattern, LocaleHelper.currentLocale(this)).format(d);
    }

    private static String formatRemaining(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec);
    }

    private void startBlink() {
        if (blinkAnimator != null && blinkAnimator.isRunning()) return;
        binding.countdownValue.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        blinkAnimator = ObjectAnimator.ofFloat(binding.countdownValue, "alpha", 1f, 0.35f, 1f);
        blinkAnimator.setDuration(1400L);
        blinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        blinkAnimator.setInterpolator(new LinearInterpolator());
        blinkAnimator.start();
    }

    private void stopBlink() {
        if (blinkAnimator != null) {
            blinkAnimator.cancel();
            blinkAnimator = null;
        }
        binding.countdownValue.setAlpha(1f);
        binding.countdownValue.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }
}
