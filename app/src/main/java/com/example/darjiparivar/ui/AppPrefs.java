package com.example.darjiparivar.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-level settings that survive sign-out (language picked, haptics). */
final class AppPrefs {
    private AppPrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
    }

    static boolean languageChosen(Context c) { return prefs(c).getBoolean("language_chosen", false); }
    static void setLanguageChosen(Context c) { prefs(c).edit().putBoolean("language_chosen", true).apply(); }

    static boolean hapticsEnabled(Context c) { return prefs(c).getBoolean("haptics", true); }
    static void setHapticsEnabled(Context c, boolean on) { prefs(c).edit().putBoolean("haptics", on).apply(); }
}
