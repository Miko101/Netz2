package com.example.netzv2.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationRepository {

    public static class Place {
        public final double lat;
        public final double lon;
        public final double elevation; // meters; 0 if unknown
        public final String displayName;
        @Nullable public final String countryCode;

        public Place(double lat, double lon, double elevation, String displayName, @Nullable String countryCode) {
            this.lat = lat;
            this.lon = lon;
            this.elevation = elevation;
            this.displayName = displayName == null ? "" : displayName;
            this.countryCode = countryCode;
        }
    }

    public interface PlaceCallback {
        void onSuccess(@NonNull Place place);
        void onError(@NonNull String message);
    }

    private final Context appCtx;
    private final FusedLocationProviderClient fused;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public LocationRepository(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.fused = LocationServices.getFusedLocationProviderClient(appCtx);
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request a fresh device location. Resolves name via Geocoder when available.
     */
    public void requestCurrent(@NonNull PlaceCallback cb) {
        if (!hasPermission()) {
            cb.onError("permission");
            return;
        }
        try {
            CurrentLocationRequest req = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(5 * 60 * 1000L)
                    .build();
            fused.getCurrentLocation(req, null)
                    .addOnSuccessListener(loc -> {
                        if (loc == null) {
                            cb.onError("no_location");
                            return;
                        }
                        resolveName(loc, cb);
                    })
                    .addOnFailureListener(e -> cb.onError(e.getMessage() == null ? "fail" : e.getMessage()));
        } catch (SecurityException e) {
            cb.onError("permission");
        }
    }

    private void resolveName(Location loc, PlaceCallback cb) {
        io.execute(() -> {
            Address address = getAddress(loc.getLatitude(), loc.getLongitude());
            String name = address != null ? formatAddress(address) : null;
            String countryCode = address != null ? address.getCountryCode() : null;
            double elev = loc.hasAltitude() ? loc.getAltitude() : 0.0;
            cb.onSuccess(new Place(loc.getLatitude(), loc.getLongitude(), elev, name, countryCode));
        });
    }

    @Nullable
    private Address getAddress(double lat, double lon) {
        if (!Geocoder.isPresent()) return null;
        try {
            Geocoder geo = new Geocoder(appCtx, Locale.getDefault());
            List<Address> list = geo.getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        } catch (IOException e) {
            return null;
        }
    }

    public static final String ERROR_NOT_FOUND = "not_found";
    public static final String ERROR_NETWORK = "network";
    public static final String ERROR_NO_GEOCODER = "no_geocoder";

    public void searchByName(@NonNull String query, @NonNull PlaceCallback cb) {
        io.execute(() -> {
            if (!Geocoder.isPresent()) {
                cb.onError(ERROR_NO_GEOCODER);
                return;
            }
            try {
                Geocoder geo = new Geocoder(appCtx, Locale.getDefault());
                List<Address> list = geo.getFromLocationName(query, 1);
                if (list == null || list.isEmpty()) {
                    cb.onError(ERROR_NOT_FOUND);
                    return;
                }
                Address a = list.get(0);
                cb.onSuccess(new Place(a.getLatitude(), a.getLongitude(), 0.0, formatAddress(a), a.getCountryCode()));
            } catch (IOException e) {
                cb.onError(ERROR_NETWORK);
            }
        });
    }

    private static String formatAddress(Address a) {
        if (a.getLocality() != null) {
            if (a.getCountryName() != null) return a.getLocality() + ", " + a.getCountryName();
            return a.getLocality();
        }
        if (a.getSubAdminArea() != null) return a.getSubAdminArea();
        if (a.getAdminArea() != null) return a.getAdminArea();
        if (a.getCountryName() != null) return a.getCountryName();
        return null;
    }
}
