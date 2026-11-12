package com.example.darjiparivar.data;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/** A piece of profile content in English and Gujarati; {@link #get()} picks the app language. */
public final class Text {
    public final String en;
    public final String gu;

    private Text(String en, String gu) {
        this.en = en;
        this.gu = gu;
    }

    public static Text of(String en, String gu) {
        return new Text(en, gu);
    }

    public String get() {
        return get(gujarati());
    }

    public String get(boolean gujarati) {
        return gujarati && gu != null && !gu.isEmpty() ? gu : en;
    }

    /** True when the app language (chosen in-app, else the system's) is Gujarati. */
    public static boolean gujarati() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        Locale locale = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        return locale != null && "gu".equals(locale.getLanguage());
    }
}
