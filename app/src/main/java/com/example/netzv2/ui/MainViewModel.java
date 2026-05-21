package com.example.netzv2.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.netzv2.R;
import com.example.netzv2.data.DayZmanim;
import com.example.netzv2.data.LocationRepository;
import com.example.netzv2.data.Prefs;
import com.example.netzv2.data.ZmanimRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class MainViewModel extends AndroidViewModel {

    /** How long after Netz we keep the time on screen, blinking, before switching to next-zman. */
    public static final long BLINK_DURATION_MS = 3 * 60 * 1000L;
    /** How long after Netz we keep the tfila card visible. */
    public static final long TFILA_CARD_DURATION_MS = 2 * 60 * 1000L;

    public enum Status { LOADING, READY, BLINKING, NEXT_ZMAN, POST_DAY, ERROR }

    public static class Milestone {
        public final int labelRes;
        public final Date time;
        public Milestone(int labelRes, Date time) {
            this.labelRes = labelRes;
            this.time = time;
        }
    }

    public static class State {
        public final Status status;
        public final Date netz;
        public final long remainingMs;
        public final boolean fromCache;
        public final String errorMessage;
        public final int nextZmanLabelRes;
        public final Date nextZmanTime;
        public final Date activeDay;
        public final Date todayTzeit;
        public final List<Milestone> milestones;
        public final int currentMilestoneIndex;
        public final float milestoneProgress; // 0.0 to 1.0 within current segment

        public State(Status status, Date netz, long remainingMs, boolean fromCache,
                     String errorMessage, int nextZmanLabelRes, Date nextZmanTime,
                     Date activeDay, Date todayTzeit,
                     List<Milestone> milestones, int currentMilestoneIndex, float milestoneProgress) {
            this.status = status;
            this.netz = netz;
            this.remainingMs = remainingMs;
            this.fromCache = fromCache;
            this.errorMessage = errorMessage;
            this.nextZmanLabelRes = nextZmanLabelRes;
            this.nextZmanTime = nextZmanTime;
            this.activeDay = activeDay;
            this.todayTzeit = todayTzeit;
            this.milestones = milestones != null ? milestones : new ArrayList<>();
            this.currentMilestoneIndex = currentMilestoneIndex;
            this.milestoneProgress = milestoneProgress;
        }

        static State loading() { return new State(Status.LOADING, null, -1, false, null, 0, null, new Date(), null, new ArrayList<>(), -1, 0f); }
        static State error(String msg) { return new State(Status.ERROR, null, -1, false, msg, 0, null, new Date(), null, new ArrayList<>(), -1, 0f); }
    }

    public static class LocationState {
        public final Double lat;
        public final Double lon;
        public final Double elevation;
        public final String displayName;
        public final boolean isManual;

        public LocationState(Double lat, Double lon, Double elevation, String displayName, boolean isManual) {
            this.lat = lat;
            this.lon = lon;
            this.elevation = elevation;
            this.displayName = displayName;
            this.isManual = isManual;
        }
    }

    private final ZmanimRepository zmanim;
    private final LocationRepository location;
    private final Prefs prefs;

    private final MutableLiveData<State> state = new MutableLiveData<>(State.loading());
    private final MutableLiveData<LocationState> locationState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> needsLocationInput = new MutableLiveData<>(false);

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private String lastFetchedDayKey;
    private Date lastFetchedDayDate;
    private String lastFetchedMethod;
    private Double lastLat, lastLon;
    private Double lastElevation;

    private long blinkStartedAt = 0L;
    @Nullable
    private List<DayZmanim.Entry> dayZmanimCache;

    private Date cachedTodayTzeit;
    private String cachedTzeitDayKey;

    private static final int[] MILESTONE_LABELS = {
            R.string.milestone_hodu, R.string.milestone_baruch_sheamar,
            R.string.milestone_vayivarech_david, R.string.milestone_yishtabach,
            R.string.milestone_kadosh, R.string.milestone_shema,
            R.string.milestone_emet, R.string.milestone_ezrat_avotenu,
            R.string.milestone_tehilot, R.string.milestone_amida
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            recomputeFromTick();
            ticker.postDelayed(this, 1000L);
        }
    };

    public MainViewModel(@NonNull Application app) {
        super(app);
        this.zmanim = new ZmanimRepository(app);
        this.location = new LocationRepository(app);
        this.prefs = new Prefs(app);
        ticker.post(tick);
    }

    public LiveData<State> getState() { return state; }
    public LiveData<LocationState> getLocationState() { return locationState; }
    public LiveData<Boolean> getNeedsLocationInput() { return needsLocationInput; }

    public void start() {
        String source = prefs.getLocationSource();
        if (Prefs.LOC_MANUAL.equals(source) && prefs.getManualLat() != null && prefs.getManualLon() != null) {
            useManualLocation(prefs.getManualLat(), prefs.getManualLon(),
                    prefs.getManualElevation(), prefs.getManualName());
            return;
        }
        if (location.hasPermission()) {
            requestGpsLocation();
        } else if (prefs.getLastLat() != null && prefs.getLastLon() != null) {
            applyLocation(prefs.getLastLat(), prefs.getLastLon(),
                    prefs.getLastElevation(), prefs.getLastName(), false);
            needsLocationInput.postValue(true);
        } else {
            needsLocationInput.postValue(true);
            state.postValue(State.loading());
        }
    }

    public void requestGpsLocation() {
        if (!location.hasPermission()) {
            needsLocationInput.postValue(true);
            return;
        }
        state.postValue(State.loading());
        location.requestCurrent(new LocationRepository.PlaceCallback() {
            @Override public void onSuccess(@NonNull LocationRepository.Place place) {
                prefs.setLocationSource(Prefs.LOC_GPS);
                prefs.setLastLocation(place.lat, place.lon, place.displayName, place.elevation);
                updateInIsrael(place.countryCode);
                applyLocation(place.lat, place.lon, place.elevation, place.displayName, false);
                needsLocationInput.postValue(false);
            }
            @Override public void onError(@NonNull String message) {
                needsLocationInput.postValue(true);
                state.postValue(State.error(message));
            }
        });
    }

    public void searchManual(@NonNull String query) {
        state.postValue(State.loading());
        location.searchByName(query, new LocationRepository.PlaceCallback() {
            @Override public void onSuccess(@NonNull LocationRepository.Place place) {
                prefs.setLocationSource(Prefs.LOC_MANUAL);
                prefs.setManualLocation(place.lat, place.lon, place.displayName);
                prefs.setManualElevation(place.elevation);
                updateInIsrael(place.countryCode);
                applyLocation(place.lat, place.lon, place.elevation, place.displayName, true);
                needsLocationInput.postValue(false);
            }
            @Override public void onError(@NonNull String message) {
                state.postValue(State.error(message));
            }
        });
    }

    private void updateInIsrael(String countryCode) {
        if (countryCode != null) {
            prefs.setInIsrael("IL".equalsIgnoreCase(countryCode));
        }
    }

    public void useManualLocation(double lat, double lon, double elevation, String name) {
        prefs.setLocationSource(Prefs.LOC_MANUAL);
        prefs.setManualLocation(lat, lon, name);
        prefs.setManualElevation(elevation);
        applyLocation(lat, lon, elevation, name, true);
        needsLocationInput.postValue(false);
    }

    public void refresh() {
        lastFetchedDayKey = null;
        lastFetchedMethod = null;
        dayZmanimCache = null;
        blinkStartedAt = 0L;
        cachedTzeitDayKey = null;
        if (lastLat != null && lastLon != null) {
            fetchForDate(startOfToday(), lastLat, lastLon, lastElevation == null ? 0.0 : lastElevation);
        } else {
            start();
        }
    }

    private void applyLocation(double lat, double lon, double elevation, String name, boolean manual) {
        lastLat = lat;
        lastLon = lon;
        lastElevation = elevation;
        locationState.postValue(new LocationState(lat, lon, elevation, name, manual));
        lastFetchedDayKey = null;
        lastFetchedMethod = null;
        dayZmanimCache = null;
        blinkStartedAt = 0L;
        cachedTzeitDayKey = null;
        fetchForDate(startOfToday(), lat, lon, elevation);
    }

    private void fetchForDate(Date day, double lat, double lon, double elevation) {
        String dayKey = ZmanimRepository.formatDate(day);
        String method = prefs.getCalcMethod();
        if (dayKey.equals(lastFetchedDayKey) && method.equals(lastFetchedMethod)) return;
        lastFetchedDayKey = dayKey;
        lastFetchedDayDate = day;
        lastFetchedMethod = method;
        dayZmanimCache = null;
        state.postValue(State.loading());
        zmanim.fetch(lat, lon, elevation, day, method, result -> {
            if (result.sunrise == null) {
                state.postValue(State.error(result.errorMessage));
                return;
            }
            renderForNetz(day, result.sunrise, result.fromCache);
        });
    }

    private void renderForNetz(Date day, Date netz, boolean fromCache) {
        long now = System.currentTimeMillis();
        long remaining = netz.getTime() - now;
        Date tzeit = getTodayTzeit();

        List<Milestone> milestones = computeMilestones(netz);
        int milestoneIndex = findCurrentMilestoneIndex(milestones, now);
        float progress = calculateProgress(milestones, milestoneIndex, now, netz);

        if (remaining > 0) {
            blinkStartedAt = 0L;
            state.postValue(new State(Status.READY, netz, remaining, fromCache, null, 0, null, day, tzeit, milestones, milestoneIndex, progress));
            return;
        }
        long sincePassed = -remaining;
        if (sincePassed < BLINK_DURATION_MS) {
            if (blinkStartedAt == 0L) blinkStartedAt = netz.getTime();
            state.postValue(new State(Status.BLINKING, netz, 0, fromCache, null, 0, null, day, tzeit, milestones, milestoneIndex, 1f));
        } else {
            postNextZmanOrRollover(day, netz, fromCache, milestones, milestoneIndex);
        }
    }

    private void postNextZmanOrRollover(Date day, Date netz, boolean fromCache, List<Milestone> milestones, int milestoneIndex) {
        // Tfila card disappears 2 minutes after Netz
        long now = System.currentTimeMillis();
        if (now - netz.getTime() > TFILA_CARD_DURATION_MS) {
            milestones = new ArrayList<>();
            milestoneIndex = -1;
        }

        List<DayZmanim.Entry> entries = ensureDayZmanimFor(day);
        Date nowD = new Date();
        DayZmanim.Entry next = entries == null ? null : DayZmanim.next(entries, nowD);
        Date tzeit = getTodayTzeit();

        if (next != null) {
            long rem = next.time.getTime() - nowD.getTime();
            state.postValue(new State(Status.NEXT_ZMAN, netz, rem, fromCache, null,
                    next.labelRes, next.time, day, tzeit, milestones, milestoneIndex, 0f));
        } else {
            if (isSameDay(day, nowD)) {
                fetchForDate(addDays(day, 1), lastLat, lastLon, lastElevation == null ? 0.0 : lastElevation);
            } else {
                state.postValue(new State(Status.POST_DAY, netz, 0, fromCache, null, 0, null, day, tzeit, milestones, milestoneIndex, 0f));
            }
        }
    }

    private List<Milestone> computeMilestones(Date netz) {
        List<Milestone> list = new ArrayList<>();
        if (netz == null) return list;
        for (int i = 0; i < Prefs.TFILA_KEYS.length; i++) {
            Integer offset = prefs.getTfilaOffset(i);
            if (offset != null) {
                Date time = new Date(netz.getTime() - (offset * 60 * 1000L));
                list.add(new Milestone(MILESTONE_LABELS[i], time));
            }
        }
        return list;
    }

    private int findCurrentMilestoneIndex(List<Milestone> milestones, long now) {
        if (milestones == null || milestones.isEmpty()) return -1;
        int index = -1;
        for (int i = 0; i < milestones.size(); i++) {
            if (now >= milestones.get(i).time.getTime()) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private float calculateProgress(List<Milestone> milestones, int idx, long now, Date netz) {
        if (milestones == null || milestones.isEmpty()) return 0f;
        Date start;
        Date end;

        if (idx == -1) { // Before first milestone
            start = new Date(netz.getTime() - (4 * 60 * 60 * 1000L)); // Arbitrary 4h before Netz
            end = milestones.get(0).time;
        } else if (idx < milestones.size() - 1) { // Between milestones
            start = milestones.get(idx).time;
            end = milestones.get(idx + 1).time;
        } else { // After last milestone (Amida)
            start = milestones.get(idx).time;
            end = netz;
        }

        long duration = end.getTime() - start.getTime();
        if (duration <= 0) return 1f;
        float p = (float)(now - start.getTime()) / duration;
        return Math.max(0f, Math.min(1f, p));
    }

    @Nullable
    private List<DayZmanim.Entry> ensureDayZmanimFor(Date day) {
        if (dayZmanimCache != null && isSameDay(day, lastFetchedDayDate)) return dayZmanimCache;
        if (lastLat == null || lastLon == null) return null;
        dayZmanimCache = DayZmanim.forDay(lastLat, lastLon,
                lastElevation == null ? 0.0 : lastElevation,
                day, prefs.getCalcMethod());
        lastFetchedDayDate = day;
        return dayZmanimCache;
    }

    private Date getTodayTzeit() {
        if (lastLat == null || lastLon == null) return null;
        String dayKey = ZmanimRepository.formatDate(new Date());
        if (dayKey.equals(cachedTzeitDayKey) && cachedTodayTzeit != null) return cachedTodayTzeit;

        List<DayZmanim.Entry> todayEntries = DayZmanim.forDay(lastLat, lastLon,
                lastElevation == null ? 0.0 : lastElevation,
                startOfToday(), prefs.getCalcMethod());
        for (DayZmanim.Entry e : todayEntries) {
            if (e.labelRes == R.string.zman_tzeit) {
                cachedTodayTzeit = e.time;
                cachedTzeitDayKey = dayKey;
                return cachedTodayTzeit;
            }
        }
        return null;
    }

    private void recomputeFromTick() {
        State s = state.getValue();
        if (s == null) return;

        // Method changed → refetch.
        String currentMethod = prefs.getCalcMethod();
        if (lastLat != null && lastLon != null && lastFetchedMethod != null
                && !currentMethod.equals(lastFetchedMethod)) {
            fetchForDate(lastFetchedDayDate != null ? lastFetchedDayDate : startOfToday(),
                    lastLat, lastLon, lastElevation == null ? 0.0 : lastElevation);
            return;
        }

        // Real-world day rolled over → reset to today.
        long now = System.currentTimeMillis();
        String realDayKey = ZmanimRepository.formatDate(new Date(now));
        if (lastFetchedDayKey != null && !realDayKey.equals(lastFetchedDayKey)
                && !isSameDay(lastFetchedDayDate, addDays(startOfToday(), 1))) {
            fetchForDate(startOfToday(), lastLat, lastLon, lastElevation == null ? 0.0 : lastElevation);
            return;
        }

        Date currentTzeit = getTodayTzeit();
        List<Milestone> currentMilestones = (s.milestones != null && !s.milestones.isEmpty()) ? s.milestones : computeMilestones(s.netz);
        int milestoneIndex = findCurrentMilestoneIndex(currentMilestones, now);
        float progress = calculateProgress(currentMilestones, milestoneIndex, now, s.netz);

        switch (s.status) {
            case READY:
                if (s.netz != null) {
                    long remaining = s.netz.getTime() - now;
                    if (remaining <= 0) {
                        blinkStartedAt = s.netz.getTime();
                        state.postValue(new State(Status.BLINKING, s.netz, 0, s.fromCache, null, 0, null, s.activeDay, currentTzeit, currentMilestones, milestoneIndex, 1f));
                    } else {
                        state.postValue(new State(Status.READY, s.netz, remaining, s.fromCache, null, 0, null, s.activeDay, currentTzeit, currentMilestones, milestoneIndex, progress));
                    }
                }
                break;
            case BLINKING:
                if (s.netz != null && now - s.netz.getTime() >= BLINK_DURATION_MS) {
                    postNextZmanOrRollover(s.activeDay, s.netz, s.fromCache, currentMilestones, milestoneIndex);
                } else {
                    state.postValue(new State(Status.BLINKING, s.netz, 0, s.fromCache, null, 0, null, s.activeDay, currentTzeit, currentMilestones, milestoneIndex, 1f));
                }
                break;
            case NEXT_ZMAN:
                if (s.nextZmanTime != null) {
                    long remaining = s.nextZmanTime.getTime() - now;
                    if (remaining <= 0) {
                        postNextZmanOrRollover(s.activeDay, s.netz, s.fromCache, currentMilestones, milestoneIndex);
                    } else {
                        state.postValue(new State(Status.NEXT_ZMAN, s.netz, remaining, s.fromCache, null,
                                s.nextZmanLabelRes, s.nextZmanTime, s.activeDay, currentTzeit, currentMilestones, milestoneIndex, 0f));
                    }
                }
                break;
            default:
                break;
        }
    }

    private static Date startOfToday() {
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static Date addDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DATE, days);
        return c.getTime();
    }

    private static boolean isSameDay(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;
        Calendar c1 = Calendar.getInstance(); c1.setTime(d1);
        Calendar c2 = Calendar.getInstance(); c2.setTime(d2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ticker.removeCallbacks(tick);
    }
}
