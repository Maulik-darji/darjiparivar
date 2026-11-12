package com.example.darjiparivar.ui;

import android.view.View;

import com.example.darjiparivar.R;
import com.google.android.material.chip.ChipGroup;

/** Wires up the English | ગુજરાતી chips from view_language_toggle.xml. */
final class LanguageToggle {
    private LanguageToggle() {}

    static void bind(View root) {
        ChipGroup group = root.findViewById(R.id.group_language);
        group.check(Ui.isGujarati() ? R.id.chip_gu : R.id.chip_en);
        group.setOnCheckedStateChangeListener((g, ids) -> {
            Haptics.tap(g);
            AppPrefs.setLanguageChosen(g.getContext());
            Ui.setGujarati(!ids.isEmpty() && ids.get(0) == R.id.chip_gu);
        });
    }
}
