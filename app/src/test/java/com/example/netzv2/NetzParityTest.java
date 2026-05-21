package com.example.netzv2;

import com.kosherjava.zmanim.AstronomicalCalendar;
import com.kosherjava.zmanim.util.GeoLocation;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class NetzParityTest {

    private static class City {
        final String name;
        final double lat;
        final double lon;
        final double elev;

        City(String name, double lat, double lon, double elev) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.elev = elev;
        }
    }

    @Test
    public void printNetzForCities() {
        TimeZone tz = TimeZone.getTimeZone("Asia/Jerusalem");

        // Approximate centers + ground elevations (m). For shul comparisons elevation
        // may differ; chaitables typically picks a representative point per locality.
        City[] cities = new City[]{
                new City("Jerusalem (ירושלים)", 31.7683, 35.2137, 754),
                new City("Tzfat (צפת)",        32.9646, 35.4951, 850),
                new City("Tel Aviv (תל אביב)",  32.0853, 34.7818,   5),
                new City("Beit El (בית אל)",   31.9421, 35.2369, 880),
        };

        String[] dates = {"2026-05-15", "2026-06-21", "2026-12-21"};

        SimpleDateFormat parseFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        parseFmt.setTimeZone(tz);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        timeFmt.setTimeZone(tz);

        System.out.println();
        System.out.println("======== Netz parity check (KosherJava AstronomicalCalendar) ========");
        for (String dateStr : dates) {
            try {
                Date day = parseFmt.parse(dateStr);
                System.out.println();
                System.out.println("Date: " + dateStr);
                System.out.printf("%-22s %-10s %-10s %-7s%n",
                        "City", "Mishor", "HaNireh", "Δ");
                for (City c : cities) {
                    GeoLocation gl = new GeoLocation(c.name, c.lat, c.lon, c.elev, tz);
                    AstronomicalCalendar ac = new AstronomicalCalendar(gl);
                    Calendar cal = Calendar.getInstance(tz);
                    cal.setTime(day);
                    cal.set(Calendar.HOUR_OF_DAY, 12);
                    ac.getCalendar().setTime(cal.getTime());

                    Date mishor = ac.getSeaLevelSunrise();
                    Date nireh  = ac.getSunrise();
                    long deltaSec = (mishor.getTime() - nireh.getTime()) / 1000;

                    System.out.printf("%-22s %-10s %-10s %+4ds  (elev=%.0fm)%n",
                            c.name,
                            timeFmt.format(mishor),
                            timeFmt.format(nireh),
                            deltaSec,
                            c.elev);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println();
    }
}
