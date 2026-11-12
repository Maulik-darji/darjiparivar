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
import com.example.darjiparivar.data.Conversation;

import java.util.List;

public class InboxFragment extends Fragment {

    public static class InboxViewModel extends BaseViewModel {
        final MutableLiveData<Resource<List<Conversation>>> conversations = new MutableLiveData<>();

        public InboxViewModel(@NonNull Application app) { super(app); }

        void load() {
            load(conversations, repo::conversations);
        }
    }

    private InboxViewModel vm;
    private RowAdapter adapter;
    private StateView state;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_list, container, false);
        vm = new ViewModelProvider(this).get(InboxViewModel.class);
        ((TextView) v.findViewById(R.id.eyebrow)).setText(R.string.conversations);
        ((TextView) v.findViewById(R.id.title)).setText(R.string.nav_inbox);
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(true);
        adapter = new RowAdapter(RowAdapter.Mode.INBOX, (p, accept, button) -> { });
        list.setAdapter(adapter);
        state = new StateView(v.findViewById(R.id.state));
        vm.conversations.observe(getViewLifecycleOwner(), res -> {
            if (res.data != null) adapter.submit(res.data, null);
            if (res.data == null && res.status == Resource.Status.LOADING) state.loading();
            else if (res.status == Resource.Status.ERROR && res.data == null)
                state.error(Ui.error(requireContext(), res.error), vm::load);
            else if (res.data != null && res.data.isEmpty()) state.empty(getString(R.string.inbox_empty));
            else state.hide();
        });
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
}
