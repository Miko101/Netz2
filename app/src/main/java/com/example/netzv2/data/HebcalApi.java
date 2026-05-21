package com.example.netzv2.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HebcalApi {

    public static class ZmanimResult {
        public final Date sunrise;
        public final String rawJson;
        public ZmanimResult(Date sunrise, String rawJson) {
            this.sunrise = sunrise;
            this.rawJson = rawJson;
        }
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    public ZmanimResult fetchSunrise(double lat, double lon, String yyyyMmDd) throws IOException {
        String url = "https://www.hebcal.com/zmanim?cfg=json"
                + "&latitude=" + lat
                + "&longitude=" + lon
                + "&date=" + yyyyMmDd;
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code());
            }
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Empty body");
            String json = body.string();
            return parse(json);
        }
    }

    public ZmanimResult parse(String json) throws IOException {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("times")) throw new IOException("Missing 'times'");
            JsonObject times = root.getAsJsonObject("times");
            if (!times.has("sunrise")) throw new IOException("Missing 'sunrise'");
            String iso = times.get("sunrise").getAsString();
            Date d = parseIso8601(iso);
            return new ZmanimResult(d, json);
        } catch (RuntimeException e) {
            throw new IOException("Bad JSON: " + e.getMessage(), e);
        }
    }

    static Date parseIso8601(String s) throws IOException {
        // Hebcal returns offsets like "2026-05-10T05:42:13+03:00" — Java's pattern X handles them.
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(p, Locale.US);
                if (p.endsWith("ss")) {
                    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                return fmt.parse(s);
            } catch (Exception ignored) {}
        }
        throw new IOException("Unparseable date: " + s);
    }
}
