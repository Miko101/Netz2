package com.example.netzv2.data;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class CustomNetzTable {

    public static final String CITY_BET_EL = "bet_el";

    public static final String[] CITY_KEYS = { CITY_BET_EL };

    private static final TimeZone ISRAEL_TZ = TimeZone.getTimeZone("Asia/Jerusalem");
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    public static Date getNetzTime(Context ctx, String city, Date day) {
        Map<String, String> table = loadTable(ctx, city);
        if (table == null) return null;

        String dateStr = ZmanimRepository.formatDate(day);
        String timeStr = table.get(dateStr);
        if (timeStr == null) return null;

        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            int second = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            Calendar cal = Calendar.getInstance(ISRAEL_TZ);
            cal.setTime(day);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, second);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTime();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static synchronized Map<String, String> loadTable(Context ctx, String city) {
        if (city == null) return null;
        Map<String, String> cached = CACHE.get(city);
        if (cached != null) return cached;

        Map<String, String> map = new HashMap<>();
        String asset = "netz_tables/" + city + ".csv";
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int comma = line.indexOf(',');
                if (comma <= 0 || comma == line.length() - 1) continue;
                map.put(line.substring(0, comma), line.substring(comma + 1).trim());
            }
        } catch (IOException e) {
            return null;
        }

        Map<String, String> immutable = Collections.unmodifiableMap(map);
        CACHE.put(city, immutable);
        return immutable;
    }
}
