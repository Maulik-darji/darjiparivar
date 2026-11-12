package com.example.darjiparivar.data.supabase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.darjiparivar.data.Profile;
import com.example.darjiparivar.data.ProfileDraft;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

public class ProfileMapperTest {

    private static final String ROW = "{"
            + "\"id\":\"8d1c7a50-1111-4c3e-9a77-000000000001\","
            + "\"display_id\":\"DP12345\",\"name\":\"Riya Darji\",\"gender\":\"F\",\"age\":26,"
            + "\"height\":\"5'4\\\"\",\"city\":\"Ahmedabad\",\"state\":\"Gujarat\","
            + "\"community\":\"Parmar\",\"education\":\"MBA\",\"profession\":\"PM\","
            + "\"income\":null,\"family\":\"Joint family\",\"about\":\"Hello\","
            + "\"pref_age_min\":25,\"pref_age_max\":32,\"photo_visibility\":\"connections\","
            + "\"verified\":true,\"premium\":false,\"last_seen\":\"2026-11-12T10:00:00.123456+00:00\","
            + "\"profile_translations\":[{\"lang\":\"gu\",\"field\":\"city\",\"value\":\"અમદાવાદ\"}]}";

    private static JsonObject row() {
        return JsonParser.parseString(ROW).getAsJsonObject();
    }

    @Test
    public void mapsRowToProfile() {
        long lastSeen = ProfileMapper.parseTime("2026-11-12T10:00:00+00:00");
        Profile p = ProfileMapper.toProfile(row(), lastSeen + 60_000);
        assertEquals("8d1c7a50-1111-4c3e-9a77-000000000001", p.id);
        assertEquals("DP12345", p.displayId);
        assertTrue(p.female);
        assertEquals(26, p.age);
        assertEquals("5'4\"", p.height);
        assertTrue(p.verified);
        assertFalse(p.premium);
        assertTrue("seen a minute ago counts as online", p.online);
        assertEquals("", p.income()); // null column
    }

    @Test
    public void offlineAfterFiveMinutes() {
        long lastSeen = ProfileMapper.parseTime("2026-11-12T10:00:00+00:00");
        assertFalse(ProfileMapper.toProfile(row(), lastSeen + 6 * 60_000).online);
    }

    @Test
    public void parsesUtcTimestamps() {
        assertEquals(1794477600000L, ProfileMapper.parseTime("2026-11-12T10:00:00.5+00:00"));
        assertEquals(0, ProfileMapper.parseTime(null));
        assertEquals(0, ProfileMapper.parseTime("garbage"));
    }

    @Test
    public void premiumNeedsAFuturePremiumUntil() {
        long now = ProfileMapper.parseTime("2026-11-12T10:00:00+00:00");
        JsonObject r = row();
        r.addProperty("premium", true);
        r.addProperty("premium_until", "2026-12-12T10:00:00+00:00");
        assertTrue(ProfileMapper.toProfile(r, now).premium);
        r.addProperty("premium_until", "2026-11-01T10:00:00+00:00");
        assertFalse("expired subscription", ProfileMapper.toProfile(r, now).premium);
    }

    @Test
    public void premiumDetailsAreWrittenSeparately() {
        ProfileDraft d = ProfileMapper.toDraft(row());
        JsonObject profile = ProfileMapper.fromDraft("user-1", d);
        assertFalse(profile.has("about"));
        assertFalse(profile.has("income"));
        JsonObject details = ProfileMapper.detailsFromDraft("user-1", d);
        assertEquals("Hello", details.get("about").getAsString());
        assertEquals("user-1", details.get("profile_id").getAsString());
    }

    @Test
    public void draftRoundTrip() {
        ProfileDraft d = ProfileMapper.toDraft(row());
        assertEquals("Riya Darji", d.name);
        assertEquals(25, d.prefAgeMin);
        assertEquals(32, d.prefAgeMax);
        assertTrue(d.photoConnectionsOnly);

        JsonObject out = ProfileMapper.fromDraft("user-1", d);
        assertEquals("user-1", out.get("id").getAsString());
        assertEquals("F", out.get("gender").getAsString());
        assertEquals("connections", out.get("photo_visibility").getAsString());
        assertFalse("users can't set their own verified flag", out.has("verified"));
    }
}
