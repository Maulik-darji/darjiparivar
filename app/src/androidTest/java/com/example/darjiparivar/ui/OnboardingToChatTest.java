package com.example.darjiparivar.ui;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.FakeRepository;
import com.example.darjiparivar.data.Repository;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import static org.hamcrest.Matchers.allOf;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end on the demo backend: pick a language, continue with Google, complete profile setup,
 * send an interest from the deck, accept a received interest, and send a chat message.
 */
@RunWith(AndroidJUnit4.class)
public class OnboardingToChatTest {

    @Before
    public void reset() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        c.getSharedPreferences("darji_parivar_demo", Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")));
        Repository.Holder.set(new FakeRepository(c));
    }

    @Test
    public void onboardingSwipeAcceptChat() {
        ActivityScenario.launch(LanguageActivity.class);

        // Language, then Continue with Google (the demo build signs in with a sample account).
        waitFor(withId(R.id.card_en)).perform(click());
        waitFor(withId(R.id.btn_google)).perform(click());

        // Step 1: basics.
        waitFor(withId(R.id.input_name)).perform(replaceText("Maulik Darji"));
        onView(withId(R.id.input_age)).perform(replaceText("28"));
        closeSoftKeyboard();
        pick(R.id.input_height, "5'8\"");
        onView(withId(R.id.input_city)).perform(replaceText("Ahmedabad"));
        onView(withId(R.id.input_state)).perform(replaceText("Gujarat"));
        closeSoftKeyboard();
        onView(withId(R.id.btn_next)).perform(click());

        // Step 2: career.
        waitFor(withId(R.id.input_education)).perform(replaceText("B.Tech"));
        onView(withId(R.id.input_profession)).perform(replaceText("Engineer"));
        closeSoftKeyboard();
        onView(withId(R.id.btn_next)).perform(click());

        // Step 3: family (surname/atak is required).
        waitFor(withId(R.id.input_community)).perform(replaceText("Parmar"));
        closeSoftKeyboard();
        onView(withId(R.id.btn_next)).perform(click());

        // Steps 4 and 5: preferences and photo can be left as they are.
        waitFor(withId(R.id.age_range));
        onView(withId(R.id.btn_next)).perform(click());
        waitFor(withId(R.id.btn_photo));
        onView(withId(R.id.btn_next)).perform(click());

        // Discover: send interest to the first card.
        waitFor(withId(R.id.btn_like)).perform(click());
        waitFor(withText("2 of 7"));

        // Matches: accept the first received interest.
        onView(withContentDescription("Matches")).perform(click());
        waitFor(withText("Pooja Parmar, 25"));
        onView(allOf(withId(R.id.list), isDisplayed())).perform(RecyclerViewActions.actionOnItemAtPosition(0,
                clickChild(R.id.btn_primary)));

        // Inbox: open the conversation and send a message.
        onView(withContentDescription("Inbox")).perform(click());
        waitFor(withText("Pooja Parmar, 25"));
        onView(allOf(withId(R.id.list), isDisplayed())).perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        waitFor(withId(R.id.input)).perform(replaceText("Namaste Pooja"));
        onView(allOf(withId(R.id.btn_send), isDisplayed())).perform(click());
        waitFor(withText("Namaste Pooja")).check(matches(isDisplayed()));
    }

    // ---- Helpers ----

    private static void pick(int dropdownId, String option) {
        onView(withId(dropdownId)).perform(click());
        onData(Matchers.<Object>equalTo(option)).inRoot(isPlatformPopup()).perform(click());
    }

    /** Waits up to 10 s for a view to be displayed (data loads on a background thread). */
    private static ViewInteraction waitFor(Matcher<View> matcher) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            try {
                // Tabs stay alive while hidden, so only match what's on screen.
                ViewInteraction v = onView(allOf(matcher, isDisplayed()));
                v.check(matches(isDisplayed()));
                return v;
            } catch (NoMatchingViewException | AssertionError e) {
                if (System.currentTimeMillis() > deadline) throw e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static ViewAction clickChild(int id) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isDisplayed(); }
            @Override public String getDescription() { return "click child " + id; }
            @Override public void perform(UiController ui, View view) { view.findViewById(id).performClick(); }
        };
    }
}
