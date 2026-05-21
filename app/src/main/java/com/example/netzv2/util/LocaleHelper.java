package com.example.netzv2.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import com.example.netzv2.data.Prefs;

import java.util.Locale;

public final class LocaleHelper {

    private LocaleHelper() {}

    public static Context wrap(Context base) {
        String lang = new Prefs(base).getLanguage();
        if (Prefs.LANG_SYSTEM.equals(lang)) {
            return base;
        }
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Resources res = base.getResources();
        Configuration cfg = new Configuration(res.getConfiguration());
        cfg.setLocale(locale);
        cfg.setLayoutDirection(locale);
        return base.createConfigurationContext(cfg);
    }

    public static Locale currentLocale(Context ctx) {
        Configuration cfg = ctx.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return cfg.getLocales().get(0);
        }
        return cfg.locale;
    }
}
