package com.example.darjiparivar.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextTest {

    @Test
    public void picksLanguage() {
        Text t = Text.of("Ahmedabad", "અમદાવાદ");
        assertEquals("Ahmedabad", t.get(false));
        assertEquals("અમદાવાદ", t.get(true));
    }

    @Test
    public void fallsBackToEnglishWhenTranslationMissing() {
        assertEquals("MBA", Text.of("MBA", null).get(true));
        assertEquals("MBA", Text.of("MBA", "").get(true));
    }
}
