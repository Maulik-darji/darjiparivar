package com.example.darjiparivar.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileTest {

    private static Profile profile(String name, String state) {
        // Same text in both languages so the result doesn't depend on the test JVM's locale.
        Text n = Text.of(name, name);
        Text s = Text.of(state, state);
        Text x = Text.of("", "");
        return new Profile("id", n, true, 26, "5'4\"", x, s, x, x, x, x, x, x,
                false, false, false, 0);
    }

    @Test
    public void latinNamesUseTwoInitials() {
        assertEquals("RD", profile("Riya Darji", "Gujarat").initials());
        assertEquals("R", profile("riya", "Gujarat").initials());
    }

    @Test
    public void gujaratiNamesUseFirstSyllable() {
        assertEquals("રિ", profile("રિયા દરજી", "Gujarat").initials());
        assertEquals("આ", profile("આન્યા ગોહિલ", "Gujarat").initials());
        // A conjunct (consonant + virama + consonant + vowel sign) stays together.
        assertEquals("સ્ને", profile("સ્નેહા રાઠોડ", "Gujarat").initials());
    }

    @Test
    public void firstName() {
        assertEquals("Riya", profile("Riya Darji", "Gujarat").firstName());
        assertEquals("Riya", profile("Riya", "Gujarat").firstName());
    }

    @Test
    public void abroadUsesEnglishState() {
        assertTrue(profile("A", "Canada").abroad());
        assertFalse(profile("A", "Gujarat").abroad());
    }
}
