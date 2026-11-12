package com.example.darjiparivar.ui;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Repository;

/**
 * Launcher. On first launch asks for a language; afterwards routes straight to the right screen
 * (welcome, profile setup, or the main app).
 */
public class LanguageActivity extends AppCompatActivity {

    public static class Router extends BaseViewModel {
        public Router(@NonNull Application app) { super(app); }
    }

    private StateView state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_language);
        Ui.applyInsets(findViewById(R.id.root));
        state = new StateView(findViewById(R.id.state));

        if (AppPrefs.languageChosen(this)) {
            route();
            return;
        }
        findViewById(R.id.picker).setVisibility(android.view.View.VISIBLE);
        findViewById(R.id.card_en).setOnClickListener(v -> pick(v, false));
        findViewById(R.id.card_gu).setOnClickListener(v -> pick(v, true));
    }

    private void pick(android.view.View v, boolean gujarati) {
        Haptics.confirm(v);
        AppPrefs.setLanguageChosen(this);
        if (gujarati != Ui.isGujarati()) {
            Ui.setGujarati(gujarati); // Recreates this activity, which then routes.
        } else {
            route();
        }
    }

    private void route() {
        findViewById(R.id.picker).setVisibility(android.view.View.GONE);
        Repository repo = Repository.get(this);
        if (!repo.isSignedIn()) {
            open(WelcomeActivity.class);
            return;
        }
        state.loading();
        Router router = new ViewModelProvider(this).get(Router.class);
        router.run(repo::hasProfile, (has, error) -> {
            if (error != null) {
                state.error(Ui.error(this, error), this::route);
                return;
            }
            open(has ? MainActivity.class : ProfileSetupActivity.class);
        });
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
