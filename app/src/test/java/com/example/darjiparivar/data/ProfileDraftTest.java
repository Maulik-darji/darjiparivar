package com.example.darjiparivar.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileDraftTest {

    @Test
    public void emptyDraftHasNoStrength() {
        assertEquals(0, new ProfileDraft().strength());
    }

    @Test
    public void completeDraftWithPhotoIsFull() {
        ProfileDraft d = new ProfileDraft();
        d.name = "Riya"; d.age = 26; d.height = "5'4\""; d.city = "Surat"; d.state = "Gujarat";
        d.community = "Parmar"; d.education = "MBA"; d.profession = "PM";
        d.income = "₹10 – 15 LPA"; d.family = "Joint family"; d.about = "Hi";
        d.photoUrl = "https://example.com/p.jpg";
        assertEquals(100, d.strength());
    }

    @Test
    public void photoCountsForMoreThanOneField() {
        ProfileDraft a = new ProfileDraft();
        a.name = "Riya";
        ProfileDraft b = new ProfileDraft();
        b.name = "Riya";
        b.photoUrl = "x";
        assertTrue(b.strength() - a.strength() > 10);
    }
}
