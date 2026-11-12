package com.example.darjiparivar.ui;

import android.view.View;
import android.widget.TextView;

import com.example.darjiparivar.R;

/** Controls the shared loading / error / empty block from view_state.xml. */
final class StateView {
    private final View root;
    private final View progress, icon, retry;
    private final TextView message;

    StateView(View root) {
        this.root = root;
        progress = root.findViewById(R.id.state_progress);
        icon = root.findViewById(R.id.state_icon);
        retry = root.findViewById(R.id.state_retry);
        message = root.findViewById(R.id.state_message);
    }

    void loading() {
        show(true, false, null, null);
    }

    void error(String text, Runnable onRetry) {
        show(false, true, text, onRetry);
    }

    void empty(String text) {
        show(false, true, text, null);
    }

    void hide() {
        root.setVisibility(View.GONE);
    }

    private void show(boolean busy, boolean showIcon, String text, Runnable onRetry) {
        root.setVisibility(View.VISIBLE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        icon.setVisibility(showIcon ? View.VISIBLE : View.GONE);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
        message.setText(text);
        retry.setVisibility(onRetry == null ? View.GONE : View.VISIBLE);
        retry.setOnClickListener(onRetry == null ? null : v -> {
            Haptics.tap(v);
            onRetry.run();
        });
    }
}
