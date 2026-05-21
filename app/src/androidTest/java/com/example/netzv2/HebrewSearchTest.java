package com.example.netzv2;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.netzv2.data.Prefs;
import com.example.netzv2.ui.LocationSettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies Hebrew handling end-to-end in the manual-location flow.
 *
 * Tests:
 *  - The EditText accepts Hebrew (Unicode) input and exposes it back via getText().
 *  - LocationSettingsActivity preserves Hebrew strings round-trip (Prefs → label).
 *  - The Geocoder API call doesn't reject Hebrew (best-effort: network may fail on
 *    this emulator but the call must not throw before reaching the backend).
 */
@RunWith(AndroidJUnit4.class)
public class HebrewSearchTest {

    private static final String JERUSALEM_HE = "ירושלים";
    private static final String TEL_AVIV_HE = "תל אביב";
    private static final String BEIT_EL_HE = "בית אל";

    @Test
    public void editText_acceptsHebrewInput() {
        try (ActivityScenario<LocationSettingsActivity> scenario =
                     ActivityScenario.launch(LocationSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                // Make sure manual section is visible.
                new Prefs(activity).setLocationSource(Prefs.LOC_MANUAL);
            });
            // Espresso replaces text directly on the View, bypassing the IME — works for Unicode.
            onView(withId(R.id.edit_city)).perform(replaceText(JERUSALEM_HE));
            onView(withId(R.id.edit_city)).check(matches(withText(JERUSALEM_HE)));
        }
    }

    @Test
    public void prefs_roundTrip_hebrewName() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Prefs prefs = new Prefs(ctx);
        prefs.setLocationSource(Prefs.LOC_MANUAL);
        prefs.setManualLocation(31.78, 35.22, JERUSALEM_HE);
        assertEquals(JERUSALEM_HE, prefs.getManualName());
    }

    @Test
    public void geocoder_doesNotRejectHebrew() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Geocoder must be present", Geocoder.isPresent());
        Geocoder geo = new Geocoder(ctx, new Locale("iw"));

        for (String query : new String[]{JERUSALEM_HE, TEL_AVIV_HE, BEIT_EL_HE}) {
            try {
                List<Address> hits = geo.getFromLocationName(query, 1);
                if (hits != null && !hits.isEmpty()) {
                    Address a = hits.get(0);
                    System.out.println("'" + query + "' → " + a.getLatitude()
                            + ", " + a.getLongitude() + " (" + a.getCountryName() + ")");
                } else {
                    System.out.println("'" + query + "' returned no hits (backend reachable but no match)");
                }
            } catch (IOException e) {
                // Network/DNS issues on the test environment — not a Hebrew-support issue.
                System.out.println("'" + query + "' backend unreachable: " + e.getMessage());
            }
        }
    }
}
