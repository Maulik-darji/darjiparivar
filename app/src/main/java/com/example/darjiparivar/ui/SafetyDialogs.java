package com.example.darjiparivar.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Profile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Report and block flows, shared by the profile and chat screens. */
final class SafetyDialogs {
    private SafetyDialogs() {}

    static void show(Context c, View anchor, Profile p, BaseViewModel vm, Runnable onBlocked) {
        new MaterialAlertDialogBuilder(c)
                .setItems(new CharSequence[]{c.getString(R.string.report), c.getString(R.string.block)},
                        (d, which) -> {
                            if (which == 0) report(c, anchor, p, vm, onBlocked);
                            else block(c, anchor, p, vm, onBlocked);
                        })
                .show();
    }

    static void report(Context c, View anchor, Profile p, BaseViewModel vm, Runnable onBlocked) {
        View view = LayoutInflater.from(c).inflate(R.layout.dialog_report, null);
        RadioGroup reasons = view.findViewById(R.id.reasons);
        String[] shown = c.getResources().getStringArray(R.array.report_reasons);
        String[] english = Ui.array(c, R.array.report_reasons, false);
        for (int i = 0; i < shown.length; i++) {
            RadioButton b = new RadioButton(c);
            b.setId(View.generateViewId());
            b.setTag(english[i]);
            b.setText(shown[i]);
            b.setMinHeight(Math.round(48 * c.getResources().getDisplayMetrics().density));
            reasons.addView(b);
        }
        EditText details = view.findViewById(R.id.details);
        new MaterialAlertDialogBuilder(c)
                .setTitle(c.getString(R.string.report_title, p.firstName()))
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.report_send, (d, w) -> {
                    View checked = view.findViewById(reasons.getCheckedRadioButtonId());
                    String reason = checked == null ? english[english.length - 1] : (String) checked.getTag();
                    String text = details.getText().toString().trim();
                    vm.act(() -> { vm.repo.report(p.id, reason, text); return null; }, (r, error) -> {
                        if (error != null) {
                            Haptics.reject(anchor);
                            Ui.snack(anchor, Ui.error(c, error), null);
                            return;
                        }
                        Haptics.confirm(anchor);
                        new MaterialAlertDialogBuilder(c)
                                .setTitle(R.string.report_thanks)
                                .setMessage(c.getString(R.string.report_also_block, p.firstName()))
                                .setNegativeButton(R.string.not_now, null)
                                .setPositiveButton(R.string.block, (d2, w2) -> doBlock(c, anchor, p, vm, onBlocked))
                                .show();
                    });
                })
                .show();
    }

    static void block(Context c, View anchor, Profile p, BaseViewModel vm, Runnable onBlocked) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(c.getString(R.string.block_title, p.firstName()))
                .setMessage(R.string.block_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.block, (d, w) -> doBlock(c, anchor, p, vm, onBlocked))
                .show();
    }

    private static void doBlock(Context c, View anchor, Profile p, BaseViewModel vm, Runnable onBlocked) {
        vm.act(() -> { vm.repo.block(p.id); return null; }, (r, error) -> {
            if (error != null) {
                Haptics.reject(anchor);
                Ui.snack(anchor, Ui.error(c, error), null);
                return;
            }
            Haptics.confirm(anchor);
            onBlocked.run();
        });
    }
}
