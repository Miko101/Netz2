package com.example.netzv2.data;

import com.kosherjava.zmanim.AstronomicalCalendar;
import com.kosherjava.zmanim.util.GeoLocation;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public final class LocalZmanimCalculator {

    private LocalZmanimCalculator() {}

    /**
     * Geometric / "Mishor" sunrise — sun at the horizon as if observed from sea level.
     * Equivalent to Hebcal's default "sunrise" field.
     */
    public static Date sunriseSeaLevel(double lat, double lon, Date day) {
        AstronomicalCalendar ac = buildCalendar(lat, lon, 0.0, day);
        return ac.getSeaLevelSunrise();
    }

    /**
     * "Netz HaNireh" — visible sunrise from the observer's elevation. Earlier than
     * sea-level Netz when the observer is at altitude.
     */
    public static Date sunriseVisible(double lat, double lon, double elevationMeters, Date day) {
        AstronomicalCalendar ac = buildCalendar(lat, lon, Math.max(0.0, elevationMeters), day);
        return ac.getSunrise();
    }

    private static AstronomicalCalendar buildCalendar(double lat, double lon, double elevation, Date day) {
        TimeZone tz = TimeZone.getDefault();
        GeoLocation gl = new GeoLocation("local", lat, lon, elevation, tz);
        AstronomicalCalendar ac = new AstronomicalCalendar(gl);
        Calendar c = Calendar.getInstance(tz);
        c.setTime(day);
        // Snap to start-of-day so the calculator uses the right day in the location's TZ.
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        ac.getCalendar().setTime(c.getTime());
        return ac;
    }
}
