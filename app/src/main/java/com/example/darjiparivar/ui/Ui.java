package com.example.darjiparivar.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Profile;
import com.example.darjiparivar.data.RepoException;
import com.example.darjiparivar.data.Text;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Ui {
    private Ui() {}

    /** Pads the root view so content sits clear of the status bar, nav bar and keyboard. */
    static void applyInsets(View root) {
        int left = root.getPaddingLeft(), top = root.getPaddingTop();
        int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Fills a view with the profile's colour and initials, with the given corner radius. */
    static void bindAvatar(TextView view, Profile p, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.color);
        bg.setCornerRadius(radiusDp * view.getResources().getDisplayMetrics().density);
        view.setBackground(bg);
        view.setText(p.initials());
    }

    /**
     * Loads the profile photo into {@code image} when there is one; otherwise hides it so the
     * initials avatar underneath shows through. A radius of -1 makes it a circle.
     */
    static void bindPhoto(ImageView image, String url, int radiusDp) {
        if (url == null || url.isEmpty()) {
            Glide.with(image).clear(image);
            image.setVisibility(View.GONE);
            return;
        }
        image.setVisibility(View.VISIBLE);
        if (radiusDp < 0) {
            Glide.with(image).load(url).circleCrop().into(image);
        } else if (radiusDp == 0) {
            Glide.with(image).load(url).centerCrop().into(image);
        } else {
            int px = Math.round(radiusDp * image.getResources().getDisplayMetrics().density);
            Glide.with(image).load(url).transform(new CenterCrop(), new RoundedCorners(px)).into(image);
        }
    }

    static String summary(Context c, Profile p) {
        return c.getString(R.string.years_short, p.age) + "  ·  " + p.height + "  ·  " + p.community();
    }

    static String status(Context c, Profile p) {
        return c.getString(p.online ? R.string.status_online : R.string.status_active);
    }

    static void bindShortlist(MaterialButton button, boolean shortlisted) {
        button.setText(shortlisted ? R.string.shortlisted : R.string.shortlist);
        button.setIconResource(shortlisted ? R.drawable.ic_check : R.drawable.ic_star);
    }

    static void bindInterest(MaterialButton button, boolean sent) {
        button.setText(sent ? R.string.interest_sent : R.string.send_interest);
        button.setEnabled(!sent);
    }

    static void snack(View view, String text, View anchor) {
        Snackbar bar = Snackbar.make(view, text, Snackbar.LENGTH_SHORT);
        if (anchor != null) bar.setAnchorView(anchor);
        bar.show();
    }

    /** A short, user-facing message for a data-layer failure. */
    static String error(Context c, RepoException e) {
        if (e == null) return c.getString(R.string.err_generic);
        switch (e.reason) {
            case NETWORK: return c.getString(R.string.err_network);
            case DAILY_LIMIT: return c.getString(R.string.err_daily_limit);
            case AUTH: return c.getString(R.string.err_session);
            case NOT_ALLOWED: return c.getString(R.string.err_not_allowed);
            default: return c.getString(R.string.err_generic);
        }
    }

    static String time(long millis) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(millis));
    }

    // ---- Dropdown choices ----

    /** The English and Gujarati versions of a string-array, so choices can be stored in English. */
    static String[] array(Context c, int arrayId, boolean gujarati) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        config.setLocale(new Locale(gujarati ? "gu" : "en"));
        return c.createConfigurationContext(config).getResources().getStringArray(arrayId);
    }

    /** Shows a stored English choice in the current language. */
    static String displayChoice(Context c, int arrayId, String english) {
        String[] en = array(c, arrayId, false);
        String[] shown = c.getResources().getStringArray(arrayId);
        for (int i = 0; i < en.length; i++) if (en[i].equals(english)) return shown[i];
        return english;
    }

    /** Maps a choice shown in the current language back to its stored English value. */
    static String englishChoice(Context c, int arrayId, String shownValue) {
        String[] en = array(c, arrayId, false);
        String[] shown = c.getResources().getStringArray(arrayId);
        for (int i = 0; i < shown.length; i++) if (shown[i].equals(shownValue)) return en[i];
        return shownValue;
    }

    /** The Gujarati version of a stored English choice, or null if it isn't one of the choices. */
    static String gujaratiChoice(Context c, int arrayId, String english) {
        String[] en = array(c, arrayId, false);
        String[] gu = array(c, arrayId, true);
        for (int i = 0; i < en.length; i++) if (en[i].equals(english)) return gu[i];
        return null;
    }

    // ---- Language ----

    static boolean isGujarati() {
        return Text.gujarati();
    }

    /** Switches the whole app's language; AppCompat recreates open activities. */
    static void setGujarati(boolean gujarati) {
        if (gujarati == isGujarati()) return;
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(gujarati ? "gu" : "en"));
    }
}
