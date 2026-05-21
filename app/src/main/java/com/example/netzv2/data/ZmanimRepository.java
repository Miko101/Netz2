package com.example.netzv2.data;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZmanimRepository {

    public interface Callback {
        void onResult(@NonNull Result result);
    }

    public static class Result {
        public final Date sunrise;
        public final boolean fromCache;
        public final String errorMessage;

        public Result(Date sunrise, boolean fromCache, String errorMessage) {
            this.sunrise = sunrise;
            this.fromCache = fromCache;
            this.errorMessage = errorMessage;
        }
    }

    private final HebcalApi api = new HebcalApi();
    private final Prefs prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public ZmanimRepository(Context ctx) {
        this.prefs = new Prefs(ctx);
    }

    public void fetch(double lat, double lon, double elevation, Date day, String method, Callback cb) {
        switch (method == null ? Prefs.CALC_LOCAL_MISHOR : method) {
            case Prefs.CALC_LOCAL_NIREH:
                io.execute(() -> {
                    try {
                        Date sr = LocalZmanimCalculator.sunriseVisible(lat, lon, elevation, day);
                        cb.onResult(new Result(sr, false, null));
                    } catch (RuntimeException e) {
                        cb.onResult(new Result(null, false, e.getMessage()));
                    }
                });
                break;

            case Prefs.CALC_HEBCAL_MISHOR:
                fetchFromHebcal(lat, lon, day, cb);
                break;

            case Prefs.CALC_LOCAL_MISHOR:
            default:
                io.execute(() -> {
                    try {
                        Date sr = LocalZmanimCalculator.sunriseSeaLevel(lat, lon, day);
                        cb.onResult(new Result(sr, false, null));
                    } catch (RuntimeException e) {
                        cb.onResult(new Result(null, false, e.getMessage()));
                    }
                });
                break;
        }
    }

    private void fetchFromHebcal(double lat, double lon, Date day, Callback cb) {
        String dateStr = formatDate(day);
        io.execute(() -> {
            String cached = prefs.getZmanimCache(dateStr, lat, lon);
            try {
                HebcalApi.ZmanimResult result = api.fetchSunrise(lat, lon, dateStr);
                prefs.putZmanimCache(dateStr, lat, lon, result.rawJson);
                cb.onResult(new Result(result.sunrise, false, null));
            } catch (IOException networkErr) {
                if (cached != null) {
                    try {
                        HebcalApi.ZmanimResult result = api.parse(cached);
                        cb.onResult(new Result(result.sunrise, true, null));
                        return;
                    } catch (IOException parseErr) {
                        cb.onResult(new Result(null, false, parseErr.getMessage()));
                        return;
                    }
                }
                cb.onResult(new Result(null, false, networkErr.getMessage()));
            }
        });
    }

    public static String formatDate(Date d) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return fmt.format(d);
    }
}
