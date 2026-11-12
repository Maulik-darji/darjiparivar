package com.example.darjiparivar.ui;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.example.darjiparivar.BuildConfig;
import com.example.darjiparivar.R;
import com.example.darjiparivar.data.ProfileDraft;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class AccountFragment extends Fragment {

    static class Summary {
        ProfileDraft me;
        int shortlisted, sent, connected;
    }

    public static class AccountViewModel extends BaseViewModel {
        final MutableLiveData<Resource<Summary>> summary = new MutableLiveData<>();

        public AccountViewModel(@NonNull Application app) { super(app); }

        void load() {
            load(summary, () -> {
                Summary s = new Summary();
                s.me = repo.me();
                s.shortlisted = repo.shortlisted().size();
                s.sent = repo.sentInterests().size();
                s.connected = repo.connectedIds().size();
                return s;
            });
        }
    }

    private AccountViewModel vm;
    private View root;
    private MaterialSwitch photoPrivate;
    private boolean binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_account, container, false);
        vm = new ViewModelProvider(this).get(AccountViewModel.class);
        root.findViewById(R.id.demo_banner).setVisibility(vm.repo.isDemo() ? View.VISIBLE : View.GONE);
        ((TextView) root.findViewById(R.id.version)).setText(getString(R.string.version,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        bindSwitch(R.id.row_gujarati, R.id.switch_gujarati, Ui.isGujarati(), (b, on) -> Ui.setGujarati(on));
        bindSwitch(R.id.row_haptics, R.id.switch_haptics, AppPrefs.hapticsEnabled(requireContext()), (b, on) -> {
            AppPrefs.setHapticsEnabled(requireContext(), on);
            Haptics.tap(b);
        });
        photoPrivate = bindSwitch(R.id.row_photo_private, R.id.switch_photo_private, false,
                (b, on) -> savePhotoPrivacy(on));

        LinearLayout menu = root.findViewById(R.id.menu);
        addRow(inflater, menu, R.string.menu_edit_profile, () -> edit(0));
        addRow(inflater, menu, R.string.menu_partner_prefs, () -> edit(3));
        addRow(inflater, menu, R.string.my_photos, () ->
                startActivity(new Intent(requireContext(), PhotosActivity.class)));
        addRow(inflater, menu, R.string.menu_help, () -> openDoc(PrivacyActivity.DOC_SAFETY));
        addRow(inflater, menu, R.string.menu_privacy_policy, () -> openDoc(PrivacyActivity.DOC_PRIVACY));

        root.findViewById(R.id.card_me).setOnClickListener(v -> edit(0));

        root.findViewById(R.id.btn_logout).setOnClickListener(v -> confirmLogout());
        root.findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());

        vm.summary.observe(getViewLifecycleOwner(), this::render);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) vm.load();
    }

    /** Tabs are kept alive and hidden; refresh when the user comes back to this one. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) vm.load();
    }

    private void render(Resource<Summary> res) {
        if (res.status == Resource.Status.ERROR) message(Ui.error(requireContext(), res.error));
        Summary s = res.data;
        if (s == null) return;
        ProfileDraft me = s.me;
        ((TextView) root.findViewById(R.id.avatar)).setText(initials(me.name));
        Ui.bindPhoto(root.findViewById(R.id.avatar_image), me.photoUrl, 20);
        ((TextView) root.findViewById(R.id.name)).setText(me.name);
        String profession = me.profession.isEmpty() ? "" : me.profession + " · ";
        ((TextView) root.findViewById(R.id.summary)).setText(
                getString(R.string.years_short, me.age) + " · " + profession + me.city);
        ((TextView) root.findViewById(R.id.user_id)).setText(getString(R.string.profile_id, me.displayId));
        int strength = me.strength();
        ((LinearProgressIndicator) root.findViewById(R.id.strength)).setProgressCompat(strength, true);
        ((TextView) root.findViewById(R.id.strength_value)).setText(strength + "%");
        ((TextView) root.findViewById(R.id.stat_shortlisted)).setText(String.valueOf(s.shortlisted));
        ((TextView) root.findViewById(R.id.stat_sent)).setText(String.valueOf(s.sent));
        ((TextView) root.findViewById(R.id.stat_connected)).setText(String.valueOf(s.connected));
        binding = true;
        photoPrivate.setChecked(me.photoConnectionsOnly);
        binding = false;
        bindSubscription(me);
    }

    private void bindSubscription(ProfileDraft me) {
        TextView title = root.findViewById(R.id.premium_title);
        com.google.android.material.button.MaterialButton button = root.findViewById(R.id.btn_upgrade);
        if (me.subscribed) {
            String until = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    .format(new java.util.Date(me.subscribedUntil));
            title.setText(getString(R.string.premium_active, until));
            button.setText(R.string.manage_subscription);
            button.setOnClickListener(v -> {
                Haptics.tap(v);
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        "https://play.google.com/store/account/subscriptions?sku=" + Billing.PRODUCT_ID
                                + "&package=" + requireContext().getPackageName())));
            });
        } else {
            title.setText(R.string.premium_title);
            button.setText(R.string.subscribe_default);
            button.setOnClickListener(v -> {
                Haptics.tap(v);
                startActivity(new Intent(requireContext(), SubscribeActivity.class));
            });
        }
    }

    private MaterialSwitch bindSwitch(int rowId, int switchId, boolean checked,
                                      CompoundButton.OnCheckedChangeListener onChange) {
        MaterialSwitch sw = root.findViewById(switchId);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            Haptics.tap(b);
            onChange.onCheckedChanged(b, on);
        });
        root.findViewById(rowId).setOnClickListener(v -> sw.toggle());
        return sw;
    }

    private void savePhotoPrivacy(boolean connectionsOnly) {
        vm.act(() -> {
            ProfileDraft me = vm.repo.me();
            me.photoConnectionsOnly = connectionsOnly;
            vm.repo.saveProfile(me);
            return null;
        }, (r, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                message(Ui.error(requireContext(), error));
                binding = true;
                photoPrivate.setChecked(!connectionsOnly);
                binding = false;
                return;
            }
            message(getString(connectionsOnly ? R.string.photo_private_on : R.string.photo_private_off));
        });
    }

    private void addRow(LayoutInflater inflater, LinearLayout menu, int label, Runnable action) {
        TextView row = (TextView) inflater.inflate(R.layout.item_menu, menu, false);
        row.setText(label);
        row.setOnClickListener(v -> {
            Haptics.tap(v);
            action.run();
        });
        menu.addView(row);
    }

    private void edit(int step) {
        startActivity(new Intent(requireContext(), ProfileSetupActivity.class)
                .putExtra(ProfileSetupActivity.EXTRA_EDIT, true)
                .putExtra(ProfileSetupActivity.EXTRA_STEP, step));
    }

    private void openDoc(String doc) {
        startActivity(new Intent(requireContext(), PrivacyActivity.class).putExtra(PrivacyActivity.EXTRA_DOC, doc));
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout, (d, w) ->
                        vm.act(() -> { vm.repo.signOut(); return null; }, (r, e) -> restart()))
                .show();
    }

    private void confirmDelete() {
        Haptics.reject(root);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_confirm, (d, w) ->
                        vm.act(() -> { vm.repo.deleteAccount(); return null; }, (r, error) -> {
                            if (error != null) {
                                if (isAdded()) message(Ui.error(requireContext(), error));
                                return;
                            }
                            restart();
                        }))
                .show();
    }

    private void restart() {
        if (!isAdded()) return;
        requireActivity().getViewModelStore().clear();
        Intent intent = new Intent(requireContext(), LanguageActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void message(String text) {
        ((MainActivity) requireActivity()).message(text);
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.toString();
    }
}
