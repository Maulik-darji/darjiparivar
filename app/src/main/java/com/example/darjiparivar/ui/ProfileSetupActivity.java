package com.example.darjiparivar.ui;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.ProfileDraft;
import com.example.darjiparivar.data.RepoException;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * Five-step profile setup (basics, career, family, partner preferences, photo). Also used to
 * edit the profile later: pass {@link #EXTRA_EDIT}, and optionally {@link #EXTRA_STEP}.
 */
public class ProfileSetupActivity extends AppCompatActivity {

    static final String EXTRA_EDIT = "edit";
    static final String EXTRA_STEP = "step";
    static final int STEPS = 5;

    public static class SetupViewModel extends BaseViewModel {
        ProfileDraft draft;
        int step;
        byte[] photo;
        Bitmap preview;

        public SetupViewModel(@NonNull Application app) { super(app); }

        byte[] processPhoto(Uri uri) throws RepoException {
            preview = Photos.square(getApplication(), uri);
            return Photos.jpeg(preview);
        }
    }

    private SetupViewModel vm;
    private boolean editing;
    private ViewFlipper flipper;
    private MaterialButton next, prev;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                vm.run(() -> vm.processPhoto(uri), (bytes, error) -> {
                    if (error != null) {
                        Ui.snack(flipper, getString(R.string.err_photo), null);
                        return;
                    }
                    vm.photo = bytes;
                    showPhoto();
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);
        Ui.applyInsets(findViewById(R.id.root));
        LanguageToggle.bind(findViewById(R.id.root));
        vm = new ViewModelProvider(this).get(SetupViewModel.class);
        editing = getIntent().getBooleanExtra(EXTRA_EDIT, false);

        flipper = findViewById(R.id.flipper);
        next = findViewById(R.id.btn_next);
        prev = findViewById(R.id.btn_prev);
        setUpDropdowns();

        RangeSlider range = findViewById(R.id.age_range);
        range.addOnChangeListener((s, value, fromUser) -> {
            bindAgeLabel();
            if (fromUser) Haptics.tick(s);
        });
        findViewById(R.id.btn_photo).setOnClickListener(v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        findViewById(R.id.btn_close).setOnClickListener(v -> back());
        prev.setOnClickListener(v -> back());
        next.setOnClickListener(this::advance);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });

        if (vm.draft == null) {
            next.setEnabled(false);
            vm.run(() -> vm.repo.hasProfile() ? vm.repo.me() : new ProfileDraft(), (draft, error) -> {
                next.setEnabled(true);
                vm.draft = draft != null ? draft : new ProfileDraft();
                if (draft == null && vm.repo.isSignedIn()) vm.draft.contact = "";
                vm.step = editing ? getIntent().getIntExtra(EXTRA_STEP, 0) : 0;
                fillViews();
                showStep();
            });
        } else {
            showStep();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // After a language switch the restored dropdown text is in the old language.
        if (vm.draft != null) fillDropdowns();
        showPhoto();
    }

    // ---- Steps ----

    private void showStep() {
        flipper.setDisplayedChild(vm.step);
        ((TextView) findViewById(R.id.step_label)).setText(getString(R.string.step_of, vm.step + 1, STEPS));
        LinearProgressIndicator bar = findViewById(R.id.step_progress);
        bar.setProgressCompat(vm.step + 1, true);
        prev.setVisibility(vm.step == 0 ? View.GONE : View.VISIBLE);
        next.setText(vm.step == STEPS - 1 ? (editing ? R.string.save : R.string.find_matches) : R.string.continue_);
        findViewById(R.id.scroll).scrollTo(0, 0);
        bindAgeLabel();
    }

    private void back() {
        if (vm.step == 0 || editing) {
            finish();
            return;
        }
        vm.step--;
        showStep();
    }

    private void advance(View v) {
        if (vm.draft == null) return;
        if (!collect(vm.step)) {
            Haptics.reject(v);
            return;
        }
        Haptics.tap(v);
        if (vm.step < STEPS - 1 && !editing) {
            vm.step++;
            showStep();
            return;
        }
        if (editing && vm.step < STEPS - 1) {
            // When editing, validate every step before saving.
            for (int i = 0; i < STEPS; i++) {
                if (!collect(i)) {
                    vm.step = i;
                    showStep();
                    return;
                }
            }
        }
        save(v);
    }

    private void save(View v) {
        next.setEnabled(false);
        ProfileDraft draft = vm.draft;
        byte[] photo = vm.photo;
        vm.act(() -> {
            vm.repo.saveProfile(draft);
            if (photo != null) vm.repo.addPhoto(photo);
            return null;
        }, (r, error) -> {
            next.setEnabled(true);
            if (error != null) {
                Haptics.reject(v);
                Ui.snack(v, Ui.error(this, error), null);
                return;
            }
            Haptics.confirm(v);
            if (editing) {
                finish();
            } else {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
    }

    // ---- Form <-> draft ----

    /** Validates one step and copies it into the draft. */
    private boolean collect(int step) {
        ProfileDraft d = vm.draft;
        boolean ok = true;
        switch (step) {
            case 0:
                d.female = ((ChipGroup) findViewById(R.id.group_gender)).getCheckedChipId() == R.id.chip_groom;
                String name = text(R.id.input_name);
                ok &= check(R.id.til_name, name.matches("[\\p{L}\\p{M} .'-]{2,60}"), R.string.err_name);
                d.name = name;
                String age = text(R.id.input_age);
                int ageValue = age.isEmpty() ? 0 : Integer.parseInt(age);
                ok &= check(R.id.til_age, ageValue >= 18 && ageValue <= 80, R.string.err_age);
                d.age = ageValue;
                ok &= check(R.id.til_height, !d.height.isEmpty(), R.string.err_required);
                d.city = text(R.id.input_city);
                ok &= check(R.id.til_city, d.city.length() >= 2, R.string.err_city);
                d.state = text(R.id.input_state);
                ok &= check(R.id.til_state, d.state.length() >= 2, R.string.err_required);
                break;
            case 1:
                d.education = text(R.id.input_education);
                ok &= check(R.id.til_education, d.education.length() >= 2, R.string.err_required);
                d.profession = text(R.id.input_profession);
                ok &= check(R.id.til_profession, d.profession.length() >= 2, R.string.err_required);
                break;
            case 2:
                d.community = text(R.id.input_community);
                ok &= check(R.id.til_community, d.community.matches("[\\p{L}\\p{M} .'-]{2,40}"), R.string.err_community);
                d.about = text(R.id.input_about);
                break;
            case 3:
                List<Float> values = ((RangeSlider) findViewById(R.id.age_range)).getValues();
                d.prefAgeMin = Math.round(values.get(0));
                d.prefAgeMax = Math.round(values.get(1));
                break;
            case 4:
                d.photoConnectionsOnly = ((MaterialSwitch) findViewById(R.id.switch_photo_private)).isChecked();
                break;
        }
        return ok;
    }

    private boolean check(int tilId, boolean valid, int error) {
        ((TextInputLayout) findViewById(tilId)).setError(valid ? null : getString(error));
        return valid;
    }

    private String text(int id) {
        return ((EditText) findViewById(id)).getText().toString().trim();
    }

    private void fillViews() {
        ProfileDraft d = vm.draft;
        ((ChipGroup) findViewById(R.id.group_gender)).check(d.female ? R.id.chip_groom : R.id.chip_bride);
        ((EditText) findViewById(R.id.input_name)).setText(d.name);
        ((EditText) findViewById(R.id.input_age)).setText(d.age > 0 ? String.valueOf(d.age) : "");
        ((EditText) findViewById(R.id.input_city)).setText(d.city);
        ((EditText) findViewById(R.id.input_state)).setText(d.state);
        ((EditText) findViewById(R.id.input_education)).setText(d.education);
        ((EditText) findViewById(R.id.input_profession)).setText(d.profession);
        ((EditText) findViewById(R.id.input_community)).setText(d.community);
        ((EditText) findViewById(R.id.input_about)).setText(d.about);
        ((RangeSlider) findViewById(R.id.age_range)).setValues((float) d.prefAgeMin, (float) d.prefAgeMax);
        ((MaterialSwitch) findViewById(R.id.switch_photo_private)).setChecked(d.photoConnectionsOnly);
        fillDropdowns();
        showPhoto();
    }

    private void setUpDropdowns() {
        dropdown(R.id.input_height, R.array.heights, "height");
        dropdown(R.id.input_income, R.array.incomes, "income");
        dropdown(R.id.input_family, R.array.families, "family");
    }

    private void dropdown(int inputId, int arrayId, String field) {
        MaterialAutoCompleteTextView view = findViewById(inputId);
        view.setSimpleItems(getResources().getStringArray(arrayId));
        view.setOnItemClickListener((parent, v, position, id) -> {
            Haptics.tap(view);
            String english = Ui.array(this, arrayId, false)[position];
            setField(field, english);
            String gu = Ui.array(this, arrayId, true)[position];
            if (!gu.equals(english)) vm.draft.gu.put(field, gu);
            else vm.draft.gu.remove(field);
            ((TextInputLayout) view.getParent().getParent()).setError(null);
        });
    }

    private void fillDropdowns() {
        ProfileDraft d = vm.draft;
        show(R.id.input_height, R.array.heights, d.height);
        show(R.id.input_income, R.array.incomes, d.income);
        show(R.id.input_family, R.array.families, d.family);
    }

    private void show(int inputId, int arrayId, String english) {
        MaterialAutoCompleteTextView view = findViewById(inputId);
        view.setSimpleItems(getResources().getStringArray(arrayId));
        view.setText(english == null || english.isEmpty() ? "" : Ui.displayChoice(this, arrayId, english), false);
    }

    private void setField(String field, String value) {
        ProfileDraft d = vm.draft;
        switch (field) {
            case "height": d.height = value; break;
            case "income": d.income = value; break;
            case "family": d.family = value; break;
        }
    }

    private void bindAgeLabel() {
        List<Float> values = ((RangeSlider) findViewById(R.id.age_range)).getValues();
        ((TextView) findViewById(R.id.age_range_label)).setText(getString(R.string.pref_age_value,
                Math.round(values.get(0)), Math.round(values.get(1))));
    }

    private void showPhoto() {
        if (vm.draft == null) return;
        TextView initials = findViewById(R.id.photo_initials);
        String name = vm.draft.name.isEmpty() ? text(R.id.input_name) : vm.draft.name;
        initials.setText(name.isEmpty() ? "" : name.substring(0, 1).toUpperCase());
        ImageView preview = findViewById(R.id.photo_preview);
        if (vm.preview != null) {
            preview.setVisibility(View.VISIBLE);
            preview.setImageBitmap(vm.preview);
            preview.setClipToOutline(true);
            preview.setBackgroundResource(R.drawable.bg_round);
        } else {
            Ui.bindPhoto(preview, vm.draft.photoUrl, 20);
        }
    }
}
