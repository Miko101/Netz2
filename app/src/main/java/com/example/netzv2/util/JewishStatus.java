package com.example.netzv2.util;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Compact "what is today" label: Yom Tov name, Rosh Chodesh, Omer day, fast,
 * Chol HaMoed, or empty if it's a regular weekday.
 */
public final class JewishStatus {

    private JewishStatus() {}

    public static String describe(Date date, Locale uiLocale, boolean inIsrael) {
        JewishCalendar jc = new JewishCalendar();
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        jc.setDate(c);
        jc.setInIsrael(inIsrael);

        boolean hebrew = isHebrew(uiLocale);
        com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter hdf =
                new com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter();
        hdf.setHebrewFormat(hebrew);
        hdf.setUseGershGershayim(hebrew);

        String yomTov = hdf.formatYomTov(jc);
        if (yomTov != null && !yomTov.isEmpty()) {
            int omer = jc.getDayOfOmer();
            if (omer != -1) {
                return yomTov + " · " + formatOmer(omer, hebrew, hdf);
            }
            return yomTov;
        }

        if (jc.isRoshChodesh()) {
            return hebrew ? "ראש חודש " + hdf.formatMonth(jc) : "Rosh Chodesh " + hdf.formatMonth(jc);
        }

        int omer = jc.getDayOfOmer();
        if (omer != -1) {
            return formatOmer(omer, hebrew, hdf);
        }

        if (jc.getDayOfWeek() == Calendar.SATURDAY) {
            return hebrew ? "שבת" : "Shabbat";
        }

        return "";
    }

    private static boolean isHebrew(Locale loc) {
        String lang = loc.getLanguage();
        return "iw".equals(lang) || "he".equals(lang);
    }

    private static String formatOmer(int omer, boolean hebrew,
                                     com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter hdf) {
        if (hebrew) {
            return hdf.formatHebrewNumber(omer) + " בעומר";
        }
        return "Day " + omer + " of the Omer";
    }
}
