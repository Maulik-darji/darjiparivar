package com.example.darjiparivar.ui;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Message;
import com.example.darjiparivar.data.Profile;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    static final String EXTRA_ID = "chat_id";

    public static class ChatViewModel extends BaseViewModel {
        final MutableLiveData<Resource<List<Message>>> messages = new MutableLiveData<>();
        final MutableLiveData<Profile> partner = new MutableLiveData<>();
        private String partnerId;
        private Closeable subscription;

        public ChatViewModel(@NonNull Application app) { super(app); }

        void start(String id) {
            if (partnerId != null) return;
            partnerId = id;
            reload();
        }

        void reload() {
            Resource<List<Message>> current = messages.getValue();
            List<Message> previous = current == null ? null : current.data;
            messages.setValue(Resource.loading(previous));
            run(() -> {
                Profile p = repo.profile(partnerId);
                partner.postValue(p);
                return repo.messages(partnerId);
            }, (list, error) -> {
                if (error != null) {
                    messages.setValue(Resource.error(error, previous));
                    return;
                }
                messages.setValue(Resource.success(list));
                // Subscribe after the first load so the conversation id is known.
                if (subscription == null) subscription = repo.subscribe(partnerId, this::add);
            });
        }

        void add(Message m) {
            Resource<List<Message>> current = messages.getValue();
            List<Message> list = new ArrayList<>(current == null || current.data == null ? new ArrayList<>() : current.data);
            for (Message existing : list) if (existing.id == m.id) return;
            list.add(m);
            messages.setValue(Resource.success(list));
        }

        void send(String text, Done<Message> done) {
            run(() -> repo.sendMessage(partnerId, text), (m, error) -> {
                if (m != null) add(m);
                done.done(m, error);
            });
        }

        @Override
        protected void onCleared() {
            if (subscription != null) {
                try {
                    subscription.close();
                } catch (IOException ignored) {
                    // Nothing to clean up.
                }
            }
        }
    }

    private ChatViewModel vm;
    private RecyclerView list;
    private final MessageAdapter adapter = new MessageAdapter();
    private StateView state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        Ui.applyInsets(findViewById(R.id.root));
        vm = new ViewModelProvider(this).get(ChatViewModel.class);
        state = new StateView(findViewById(R.id.state));

        list = findViewById(R.id.list);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        list.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.header).setOnClickListener(v -> startActivity(
                new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_ID, id)));
        findViewById(R.id.btn_more).setOnClickListener(v -> {
            Profile p = vm.partner.getValue();
            if (p != null) SafetyDialogs.show(this, v, p, vm, this::finish);
        });

        EditText input = findViewById(R.id.input);
        findViewById(R.id.btn_send).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            input.setText("");
            Haptics.confirm(v);
            vm.send(text, (m, error) -> {
                if (error == null) return;
                Haptics.reject(v);
                if (input.getText().length() == 0) input.setText(text);
                Ui.snack(v, getString(R.string.err_not_sent), null);
            });
        });

        vm.partner.observe(this, p -> {
            Ui.bindAvatar(findViewById(R.id.avatar), p, 22);
            Ui.bindPhoto(findViewById(R.id.avatar_image), p.photoUrl, -1);
            ((TextView) findViewById(R.id.name)).setText(p.name());
            ((TextView) findViewById(R.id.status)).setText(Ui.status(this, p));
        });
        vm.messages.observe(this, res -> {
            if (res.data != null) {
                state.hide();
                int before = adapter.items.size();
                adapter.items = res.data;
                if (res.data.size() == before + 1) {
                    adapter.notifyItemInserted(before);
                    list.smoothScrollToPosition(before);
                } else {
                    adapter.notifyDataSetChanged();
                    if (res.data.size() > before) list.scrollToPosition(res.data.size() - 1);
                }
            } else if (res.status == Resource.Status.LOADING) {
                state.loading();
            } else {
                state.error(Ui.error(this, res.error), vm::reload);
            }
        });
        vm.start(id);
    }

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        List<Message> items = new ArrayList<>();

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Message m = items.get(position);
            h.row.setGravity(m.mine ? Gravity.END : Gravity.START);
            h.text.setText(m.text);
            h.text.setBackgroundResource(m.mine ? R.drawable.bg_bubble_out : R.drawable.bg_bubble_in);
            h.text.setTextColor(getColor(m.mine ? R.color.white : R.color.ink));
            h.time.setText(Ui.time(m.time));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final LinearLayout row;
            final TextView text, time;

            Holder(View v) {
                super(v);
                row = v.findViewById(R.id.row);
                text = v.findViewById(R.id.text);
                time = v.findViewById(R.id.time);
            }
        }
    }
}
