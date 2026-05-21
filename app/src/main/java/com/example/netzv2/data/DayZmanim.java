package com.example.netzv2.data;

import com.example.netzv2.R;
import com.kosherjava.zmanim.ComplexZmanimCalendar;
import com.kosherjava.zmanim.util.GeoLocation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Computes the chronologically-ordered list of halachic times for a single day,
 * after Netz. Used by the ViewModel to display "next zman" countdowns once Netz
 * has passed.
 */
public final class DayZmanim {

    public static class Entry {
        public final int labelRes;
        public final Date time;
        public Entry(int labelRes, Date time) {
            this.labelRes = labelRes;
            this.time = time;
        }
    }

    private DayZmanim() {}

    public static List<Entry> forDay(double lat, double lon, double elevation,
                                     Date day, String calcMethod) {
        TimeZone tz = TimeZone.getDefault();
        boolean useElevation = Prefs.CALC_LOCAL_NIREH.equals(calcMethod);
        double elev = useElevation ? Math.max(0.0, elevation) : 0.0;

        GeoLocation gl = new GeoLocation("local", lat, lon, elev, tz);
        ComplexZmanimCalendar c = new ComplexZmanimCalendar(gl);

        Calendar cal = Calendar.getInstance(tz);
        cal.setTime(day);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        c.getCalendar().setTime(cal.getTime());

        List<Entry> out = new ArrayList<>();
        addIf(out, R.string.zman_sof_zman_shma, c.getSofZmanShmaGRA());
        addIf(out, R.string.zman_sof_zman_tefilla, c.getSofZmanTfilaGRA());
        addIf(out, R.string.zman_chatzot, c.getChatzos());
        addIf(out, R.string.zman_mincha_gedola, c.getMinchaGedola());
        addIf(out, R.string.zman_mincha_ketana, c.getMinchaKetana());
        addIf(out, R.string.zman_plag_hamincha, c.getPlagHamincha());
        addIf(out, R.string.zman_shkia, useElevation ? c.getSunset() : c.getSeaLevelSunset());
        addIf(out, R.string.zman_tzeit, c.getTzais());

        out.sort((a, b) -> a.time.compareTo(b.time));
        return out;
    }

    /** Returns the next entry strictly after `now`, or null if all have passed. */
    public static Entry next(List<Entry> entries, Date now) {
        for (Entry e : entries) {
            if (e.time.after(now)) return e;
        }
        return null;
    }

    private static void addIf(List<Entry> out, int label, Date t) {
        if (t != null) out.add(new Entry(label, t));
    }
}
