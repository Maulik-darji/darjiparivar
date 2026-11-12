package com.example.darjiparivar.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.darjiparivar.R;

/** Shows the privacy policy or the safety tips. */
public class PrivacyActivity extends AppCompatActivity {

    static final String EXTRA_DOC = "doc";
    static final String DOC_PRIVACY = "privacy";
    static final String DOC_SAFETY = "safety";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_privacy);
        Ui.applyInsets(findViewById(R.id.root));
        boolean safety = DOC_SAFETY.equals(getIntent().getStringExtra(EXTRA_DOC));
        ((TextView) findViewById(R.id.title)).setText(safety ? R.string.menu_help : R.string.menu_privacy_policy);
        ((TextView) findViewById(R.id.body)).setText(safety ? R.string.safety_body : R.string.privacy_body);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
