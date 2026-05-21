package com.example.netzv2.util;

import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.util.Date;
import java.util.Locale;

public final class HebrewDateFormatter {

    private HebrewDateFormatter() {}

    /** Day + month only, no year. Honors UI locale (Hebrew gematria vs. English). */
    public static String dayAndMonth(Date date, Locale uiLocale) {
        JewishDate jd = jewishDateFor(date);
        com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter hdf =
                new com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter();
        boolean hebrew = isHebrewLocale(uiLocale);
        hdf.setHebrewFormat(hebrew);
        hdf.setUseGershGershayim(hebrew);

        String month = hdf.formatMonth(jd);
        String day = hebrew
                ? hdf.formatHebrewNumber(jd.getJewishDayOfMonth())
                : String.valueOf(jd.getJewishDayOfMonth());
        return hebrew
                ? day + " ב" + month
                : day + " " + month;
    }

    private static JewishDate jewishDateFor(Date d) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(d);
        return new JewishDate(c);
    }

    static boolean isHebrewLocale(Locale loc) {
        String lang = loc.getLanguage();
        return "iw".equals(lang) || "he".equals(lang);
    }
}
