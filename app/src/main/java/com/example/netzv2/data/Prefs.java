package com.example.netzv2.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

public class Prefs {

    public static final String LANG_SYSTEM = "system";
    public static final String LANG_ENGLISH = "en";
    public static final String LANG_HEBREW = "iw";

    public static final String LOC_GPS = "gps";
    public static final String LOC_MANUAL = "manual";

    public static final String CALC_LOCAL_MISHOR = "local_mishor";
    public static final String CALC_LOCAL_NIREH = "local_nireh";
    public static final String CALC_HEBCAL_MISHOR = "hebcal_mishor";
    public static final String CALC_CUSTOMIZED = "customized";

    private static final String FILE = "netz_prefs";
    private static final String K_LANG = "lang";
    private static final String K_LOC_SOURCE = "loc_source";
    private static final String K_MANUAL_LAT = "manual_lat";
    private static final String K_MANUAL_LON = "manual_lon";
    private static final String K_MANUAL_NAME = "manual_name";
    private static final String K_LAST_LAT = "last_lat";
    private static final String K_LAST_LON = "last_lon";
    private static final String K_LAST_NAME = "last_name";
    private static final String K_LAST_ELEVATION = "last_elevation";
    private static final String K_MANUAL_ELEVATION = "manual_elevation";
    private static final String K_CALC_METHOD = "calc_method";
    private static final String K_CUSTOM_CITY = "custom_city";
    private static final String K_IN_ISRAEL = "in_israel";
    private static final String K_COUNTDOWN_PRECISION = "countdown_precision";
    private static final String K_CACHE_PREFIX = "zmanim_";

    // Tfila Milestones
    public static final String[] TFILA_KEYS = {
            "tf_hodu", "tf_hashem_melech", "tf_baruch_sheamar", "tf_vayivarech_david",
            "tf_yishtabach", "tf_kadosh", "tf_shema", "tf_emet",
            "tf_ezrat_avotenu", "tf_tehilot", "tf_amida"
    };

    private static final int[] DEFAULT_OFFSETS = {
            20, 19, 18, 16, 14, 12, 10, 8, 6, 4, 0
    };

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String getLanguage() {
        return sp.getString(K_LANG, LANG_SYSTEM);
    }

    public void setLanguage(String lang) {
        sp.edit().putString(K_LANG, lang).apply();
    }

    public String getLocationSource() {
        return sp.getString(K_LOC_SOURCE, LOC_GPS);
    }

    public void setLocationSource(String source) {
        sp.edit().putString(K_LOC_SOURCE, source).apply();
    }

    public void setManualLocation(double lat, double lon, String displayName) {
        sp.edit()
                .putString(K_MANUAL_LAT, Double.toString(lat))
                .putString(K_MANUAL_LON, Double.toString(lon))
                .putString(K_MANUAL_NAME, displayName == null ? "" : displayName)
                .apply();
    }

    public Double getManualLat() { return readDouble(K_MANUAL_LAT); }
    public Double getManualLon() { return readDouble(K_MANUAL_LON); }
    public String getManualName() { return sp.getString(K_MANUAL_NAME, ""); }

    public void setManualElevation(double meters) {
        sp.edit().putString(K_MANUAL_ELEVATION, Double.toString(meters)).apply();
    }
    public double getManualElevation() {
        Double v = readDouble(K_MANUAL_ELEVATION);
        return v == null ? 0.0 : v;
    }

    public void setLastLocation(double lat, double lon, String displayName, double elevation) {
        sp.edit()
                .putString(K_LAST_LAT, Double.toString(lat))
                .putString(K_LAST_LON, Double.toString(lon))
                .putString(K_LAST_NAME, displayName == null ? "" : displayName)
                .putString(K_LAST_ELEVATION, Double.toString(elevation))
                .apply();
    }

    public Double getLastLat() { return readDouble(K_LAST_LAT); }
    public Double getLastLon() { return readDouble(K_LAST_LON); }
    public String getLastName() { return sp.getString(K_LAST_NAME, ""); }
    public double getLastElevation() {
        Double v = readDouble(K_LAST_ELEVATION);
        return v == null ? 0.0 : v;
    }

    public String getCalcMethod() {
        return sp.getString(K_CALC_METHOD, CALC_LOCAL_MISHOR);
    }
    public void setCalcMethod(String method) {
        sp.edit().putString(K_CALC_METHOD, method).apply();
    }

    public String getCustomCity() {
        return sp.getString(K_CUSTOM_CITY, "bet_el");
    }
    public void setCustomCity(String city) {
        sp.edit().putString(K_CUSTOM_CITY, city).apply();
    }

    public boolean getInIsrael() {
        return sp.getBoolean(K_IN_ISRAEL, false);
    }
    public void setInIsrael(boolean inIsrael) {
        sp.edit().putBoolean(K_IN_ISRAEL, inIsrael).apply();
    }

    public boolean getCountdownPrecision() {
        return sp.getBoolean(K_COUNTDOWN_PRECISION, false);
    }
    public void setCountdownPrecision(boolean enabled) {
        sp.edit().putBoolean(K_COUNTDOWN_PRECISION, enabled).apply();
    }

    // Tfila methods — offsets are stored as "MM:SS" strings and exposed as total seconds.
    public Integer getTfilaOffsetSeconds(int index) {
        if (!sp.contains(TFILA_KEYS[index])) {
            return DEFAULT_OFFSETS[index] * 60;
        }
        return parseOffsetSeconds(sp.getString(TFILA_KEYS[index], null));
    }

    public void setTfilaOffsetSeconds(int index, Integer seconds) {
        if (seconds == null) {
            // Store an empty string so the milestone is treated as "disabled"
            // rather than falling back to the default offset.
            sp.edit().putString(TFILA_KEYS[index], "").apply();
        } else {
            sp.edit().putString(TFILA_KEYS[index], formatOffset(seconds)).apply();
        }
    }

    public String getTfilaOffsetDisplay(int index) {
        if (!sp.contains(TFILA_KEYS[index])) return null;
        Integer s = parseOffsetSeconds(sp.getString(TFILA_KEYS[index], null));
        return s == null ? null : formatOffset(s);
    }

    public static Integer parseOffsetSeconds(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        String s = raw.trim();
        int colon = s.indexOf(':');
        try {
            if (colon < 0) {
                // Legacy: bare minutes
                int m = Integer.parseInt(s);
                return m < 0 ? null : m * 60;
            }
            String mStr = s.substring(0, colon).trim();
            String sStr = s.substring(colon + 1).trim();
            int m = mStr.isEmpty() ? 0 : Integer.parseInt(mStr);
            int sec = sStr.isEmpty() ? 0 : Integer.parseInt(sStr);
            if (m < 0 || sec < 0 || sec > 59) return null;
            return m * 60 + sec;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatOffset(int seconds) {
        if (seconds < 0) seconds = 0;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format(java.util.Locale.US, "%d:%02d", m, s);
    }

    public boolean hasTfilaConfig() {
        return true;
    }

    public void putZmanimCache(String date, double lat, double lon, String json) {
        sp.edit().putString(cacheKey(date, lat, lon), json).apply();
    }

    public String getZmanimCache(String date, double lat, double lon) {
        return sp.getString(cacheKey(date, lat, lon), null);
    }

    private static String cacheKey(String date, double lat, double lon) {
        return K_CACHE_PREFIX + date + "_" + round3(lat) + "_" + round3(lon);
    }

    private static String round3(double v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }

    private Double readDouble(String k) {
        String s = sp.getString(k, null);
        if (TextUtils.isEmpty(s)) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }
}
