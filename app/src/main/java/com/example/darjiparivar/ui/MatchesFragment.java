package com.example.darjiparivar.ui;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Profile;
import com.google.android.material.tabs.TabLayout;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MatchesFragment extends Fragment {

    static class Tab {
        int index;
        List<Profile> profiles;
        Set<String> connected;
    }

    public static class MatchesViewModel extends BaseViewModel {
        final MutableLiveData<Resource<Tab>> data = new MutableLiveData<>();
        int tab;

        public MatchesViewModel(@NonNull Application app) { super(app); }

        void load() {
            int index = tab;
            load(data, () -> {
                Tab t = new Tab();
                t.index = index;
                t.profiles = index == 0 ? repo.receivedInterests()
                        : index == 1 ? repo.sentInterests() : repo.shortlisted();
                t.connected = index == 1 ? repo.connectedIds() : Collections.emptySet();
                return t;
            });
        }
    }

    private static final RowAdapter.Mode[] MODES = {
            RowAdapter.Mode.RECEIVED, RowAdapter.Mode.SENT, RowAdapter.Mode.SHORTLIST };
    private static final int[] EMPTY = {
            R.string.empty_received, R.string.empty_sent, R.string.empty_shortlist };

    private MatchesViewModel vm;
    private RecyclerView list;
    private final RowAdapter[] adapters = new RowAdapter[MODES.length];
    private StateView state;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_list, container, false);
        vm = new ViewModelProvider(this).get(MatchesViewModel.class);
        ((TextView) v.findViewById(R.id.eyebrow)).setText(R.string.your_connections);
        ((TextView) v.findViewById(R.id.title)).setText(R.string.nav_matches);
        list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(true);
        for (int i = 0; i < MODES.length; i++) adapters[i] = new RowAdapter(MODES[i], this::respond);
        list.setAdapter(adapters[vm.tab]);
        state = new StateView(v.findViewById(R.id.state));

        TabLayout tabs = v.findViewById(R.id.tabs);
        tabs.setVisibility(View.VISIBLE);
        tabs.addTab(tabs.newTab().setText(R.string.tab_received));
        tabs.addTab(tabs.newTab().setText(R.string.tab_sent));
        tabs.addTab(tabs.newTab().setText(R.string.tab_shortlisted));
        TabLayout.Tab selected = tabs.getTabAt(vm.tab);
        if (selected != null) selected.select();
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                Haptics.tap(tabs);
                vm.tab = t.getPosition();
                list.setAdapter(adapters[vm.tab]);
                vm.load();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        vm.data.observe(getViewLifecycleOwner(), this::render);
        return v;
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

    private void render(Resource<Tab> res) {
        Tab t = res.data;
        if (t != null && t.index != vm.tab) t = null; // Result for a tab we've left.
        if (t == null) {
            if (res.status == Resource.Status.ERROR) state.error(Ui.error(requireContext(), res.error), vm::load);
            else state.loading();
            return;
        }
        adapters[t.index].submitProfiles(t.profiles, t.connected);
        if (res.status == Resource.Status.ERROR) {
            state.hide();
            message(Ui.error(requireContext(), res.error));
        } else if (t.profiles.isEmpty()) {
            state.empty(getString(EMPTY[t.index]));
        } else {
            state.hide();
        }
    }

    private void respond(Profile p, boolean accept, View button) {
        button.setEnabled(false);
        vm.act(() -> { vm.repo.respondToInterest(p.id, accept); return null; }, (r, error) -> {
            if (!isAdded()) return;
            button.setEnabled(true);
            if (error != null) {
                Haptics.reject(button);
                message(Ui.error(requireContext(), error));
                return;
            }
            if (accept) Haptics.confirm(button); else Haptics.reject(button);
            message(getString(accept ? R.string.msg_connected : R.string.msg_declined, p.firstName()));
            vm.load();
        });
    }

    private void message(String text) {
        ((MainActivity) requireActivity()).message(text);
    }
}
