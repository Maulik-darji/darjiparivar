package com.example.darjiparivar.ui;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Light haptic feedback through the view system, which respects the device's own touch-feedback
 * setting and needs no VIBRATE permission. Can be turned off in Account.
 */
final class Haptics {
    private Haptics() {}

    /** A small tick, e.g. when a dragged card crosses the swipe threshold. */
    static void tick(View v) { perform(v, HapticFeedbackConstants.CLOCK_TICK); }

    /** A regular tap, e.g. switching tabs or toggling a setting. */
    static void tap(View v) { perform(v, HapticFeedbackConstants.VIRTUAL_KEY); }

    /** Something succeeded: interest sent, message sent, request accepted. */
    static void confirm(View v) {
        perform(v, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Something was refused or failed: pass, decline, validation error. */
    static void reject(View v) {
        perform(v, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
    }

    private static void perform(View v, int constant) {
        if (v != null && AppPrefs.hapticsEnabled(v.getContext())) v.performHapticFeedback(constant);
    }
}
