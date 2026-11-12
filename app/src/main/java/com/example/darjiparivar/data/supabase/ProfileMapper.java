package com.example.darjiparivar.data.supabase;

import com.example.darjiparivar.data.Profile;
import com.example.darjiparivar.data.ProfileDraft;
import com.example.darjiparivar.data.Text;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Converts between Supabase rows and app models. Pure Java so it can be unit-tested. */
public final class ProfileMapper {

    static final String SELECT = "*,profile_translations(lang,field,value)";
    static final long ONLINE_WINDOW_MS = 5 * 60 * 1000L;

    private static final int[] COLORS = {
            0xFF8C1C2B, 0xFF2F4858, 0xFFB8862F, 0xFF3F6E5A,
            0xFF5B4B8A, 0xFFB5543C, 0xFF1F3A5F, 0xFF2A2A2A };
    private static final String[] TRANSLATABLE = {
            "name", "city", "state", "community", "education", "profession", "income",
            "family", "about" };

    private ProfileMapper() {}

    public static Profile toProfile(JsonObject row, long now) {
        Map<String, String> gu = new HashMap<>();
        JsonElement tr = row.get("profile_translations");
        if (tr != null && tr.isJsonArray()) {
            for (JsonElement e : (JsonArray) tr) {
                JsonObject t = e.getAsJsonObject();
                if ("gu".equals(str(t, "lang"))) gu.put(str(t, "field"), str(t, "value"));
            }
        }
        Map<String, Text> text = new HashMap<>();
        for (String f : TRANSLATABLE) text.put(f, Text.of(str(row, f), gu.get(f)));

        String id = str(row, "id");
        long lastSeen = parseTime(str(row, "last_seen"));
        boolean premium = bool(row, "premium") && parseTime(str(row, "premium_until")) > now;
        Profile p = new Profile(id, text.get("name"), "F".equals(str(row, "gender")),
                integer(row, "age"), str(row, "height"), text.get("city"), text.get("state"),
                text.get("community"), text.get("education"), text.get("profession"),
                text.get("income"), text.get("family"), text.get("about"),
                bool(row, "verified"), premium, now - lastSeen < ONLINE_WINDOW_MS,
                COLORS[Math.abs(id.hashCode()) % COLORS.length]);
        String displayId = str(row, "display_id");
        if (!displayId.isEmpty()) p.displayId = displayId;
        return p;
    }

    public static ProfileDraft toDraft(JsonObject row) {
        ProfileDraft d = new ProfileDraft();
        d.displayId = str(row, "display_id");
        d.name = str(row, "name");
        d.female = "F".equals(str(row, "gender"));
        d.age = integer(row, "age");
        d.height = str(row, "height");
        d.city = str(row, "city");
        d.state = str(row, "state");
        d.community = str(row, "community");
        d.education = str(row, "education");
        d.profession = str(row, "profession");
        d.income = str(row, "income");
        d.family = str(row, "family");
        d.about = str(row, "about");
        d.prefAgeMin = row.has("pref_age_min") && !row.get("pref_age_min").isJsonNull() ? integer(row, "pref_age_min") : 18;
        d.prefAgeMax = row.has("pref_age_max") && !row.get("pref_age_max").isJsonNull() ? integer(row, "pref_age_max") : 80;
        d.photoConnectionsOnly = "connections".equals(str(row, "photo_visibility"));
        d.subscribedUntil = parseTime(str(row, "premium_until"));
        d.subscribed = d.subscribedUntil > System.currentTimeMillis();
        return d;
    }

    /** Columns written when the user saves their own profile. */
    public static JsonObject fromDraft(String userId, ProfileDraft d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", userId);
        o.addProperty("name", d.name.trim());
        o.addProperty("gender", d.female ? "F" : "M");
        o.addProperty("age", d.age);
        o.addProperty("height", d.height);
        o.addProperty("city", d.city.trim());
        o.addProperty("state", d.state.trim());
        o.addProperty("community", d.community);
        o.addProperty("education", d.education.trim());
        o.addProperty("profession", d.profession.trim());
        o.addProperty("pref_age_min", d.prefAgeMin);
        o.addProperty("pref_age_max", d.prefAgeMax);
        o.addProperty("photo_visibility", d.photoConnectionsOnly ? "connections" : "all");
        return o;
    }

    /** Premium-only columns, stored in profile_details so only subscribers can read them. */
    public static JsonObject detailsFromDraft(String userId, ProfileDraft d) {
        JsonObject o = new JsonObject();
        o.addProperty("profile_id", userId);
        o.addProperty("about", d.about.trim());
        o.addProperty("income", d.income);
        o.addProperty("family", d.family);
        return o;
    }

    /** Copies a profile_details row (if any) into a profile row before mapping it. */
    public static void mergeDetails(JsonObject row, JsonObject details) {
        if (details == null) return;
        for (String key : new String[]{"about", "income", "family"}) {
            if (details.has(key)) row.add(key, details.get(key));
        }
    }

    /** Parses Postgres timestamptz like "2026-11-12T10:15:30.123456+00:00" (always UTC from Supabase). */
    public static long parseTime(String value) {
        if (value == null || value.length() < 19) return 0;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return f.parse(value.substring(0, 19)).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    static int integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }
}
