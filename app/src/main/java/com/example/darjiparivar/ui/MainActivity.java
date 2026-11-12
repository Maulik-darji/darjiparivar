package com.example.darjiparivar.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.darjiparivar.R;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_TAB = "tab";
    private static final String[] TAGS = { "discover", "matches", "inbox", "account" };

    private FloatingNav nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Ui.applyInsets(findViewById(R.id.root));

        nav = findViewById(R.id.nav);
        nav.setBlurSource(findViewById(R.id.container), getColor(R.color.bg));
        nav.setListener(index -> {
            Haptics.tap(nav);
            show(index);
        });
        int tab = savedInstanceState == null ? 0 : savedInstanceState.getInt(KEY_TAB, 0);
        nav.select(tab, false);
        show(tab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, nav.getSelected());
    }

    /**
     * Tabs are created once and then just shown or hidden, so switching is instant and each
     * tab keeps its scroll position. Tabs refresh their data when they become visible.
     */
    private void show(int index) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (int i = 0; i < TAGS.length; i++) {
            Fragment f = fm.findFragmentByTag(TAGS[i]);
            if (i == index) {
                if (f == null) tx.add(R.id.container, create(i), TAGS[i]);
                else tx.show(f);
            } else if (f != null && !f.isHidden()) {
                tx.hide(f);
            }
        }
        tx.commitNow();
    }

    private static Fragment create(int index) {
        switch (index) {
            case 1: return new MatchesFragment();
            case 2: return new InboxFragment();
            case 3: return new AccountFragment();
            default: return new DiscoverFragment();
        }
    }

    void message(String text) {
        Ui.snack(findViewById(R.id.container), text, nav);
    }
}
