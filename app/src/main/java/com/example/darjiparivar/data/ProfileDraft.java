package com.example.darjiparivar.data;

import java.util.HashMap;
import java.util.Map;

/** The signed-in user's own profile, as filled in during setup and edited later. */
public class ProfileDraft {
    public String displayId = "";
    public String name = "";
    public boolean female;
    public int age;
    public String height = "";
    public String city = "";
    public String state = "";
    public String community = "";
    public String education = "";
    public String profession = "";
    public String income = "";
    public String family = "";
    public String about = "";
    public int prefAgeMin = 18;
    public int prefAgeMax = 80;
    /** When true, only accepted connections can see the user's photo. */
    public boolean photoConnectionsOnly;
    public String photoUrl;
    /** Email of the Google account used to sign in; shown to others only after both accept. */
    public String contact = "";
    /**
     * Gujarati versions of fields picked from a list (income, family, …), keyed by column name.
     * The English value is stored in the field itself.
     */
    public Map<String, String> gu = new HashMap<>();
    /** Has an active ₹99/month subscription, which unlocks other members' full profiles. */
    public boolean subscribed;
    /** When the current subscription period ends (ms since epoch), or 0. */
    public long subscribedUntil;

    /** Rough completeness score (0–100) used for the profile-strength meter. */
    public int strength() {
        String[] fields = { name, height, city, state, community, education, profession, income,
                family, about };
        int filled = 0;
        for (String f : fields) if (f != null && !f.trim().isEmpty()) filled++;
        if (age > 0) filled++;
        if (photoUrl != null) filled += 3;
        return Math.min(100, Math.round(filled * 100f / (fields.length + 4)));
    }
}
