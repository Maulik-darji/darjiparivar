package com.example.darjiparivar.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Conversation;
import com.example.darjiparivar.data.Profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Compact rows used by the Matches tabs and the Inbox. */
class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

    enum Mode { RECEIVED, SENT, SHORTLIST, INBOX }

    interface Listener {
        void onRespond(Profile profile, boolean accept, View button);
    }

    private final Mode mode;
    private final Listener listener;
    private List<Conversation> items = new ArrayList<>();
    private Set<String> connected = Collections.emptySet();

    RowAdapter(Mode mode, Listener listener) {
        this.mode = mode;
        this.listener = listener;
    }

    void submitProfiles(List<Profile> profiles, Set<String> connectedIds) {
        List<Conversation> list = new ArrayList<>();
        for (Profile p : profiles) list.add(new Conversation(p, null));
        submit(list, connectedIds);
    }

    /** Updates only the rows that changed, so refreshing a tab doesn't flash or jump. */
    void submit(List<Conversation> list, Set<String> connectedIds) {
        List<Conversation> old = items;
        Set<String> oldConnected = connected;
        Set<String> newConnected = connectedIds == null ? Collections.emptySet() : connectedIds;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return list.size(); }

            @Override public boolean areItemsTheSame(int o, int n) {
                return old.get(o).partner.id.equals(list.get(n).partner.id);
            }

            @Override public boolean areContentsTheSame(int o, int n) {
                Conversation a = old.get(o), b = list.get(n);
                String id = b.partner.id;
                return a.lastTime() == b.lastTime()
                        && oldConnected.contains(id) == newConnected.contains(id)
                        && a.partner.online == b.partner.online
                        && samePhoto(a.partner.photoUrl, b.partner.photoUrl);
            }
        });
        items = list;
        connected = newConnected;
        diff.dispatchUpdatesTo(this);
    }

    /** Signed photo URLs change on every fetch; compare without the token. */
    private static boolean samePhoto(String a, String b) {
        if (a == null || b == null) return a == b;
        return a.split("\\?")[0].equals(b.split("\\?")[0]);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Conversation item = items.get(position);
        Profile p = item.partner;
        Context c = h.itemView.getContext();

        Ui.bindAvatar(h.avatar, p, 16);
        Ui.bindPhoto(h.photo, p.photoUrl, 16);
        h.name.setText(p.name() + ", " + p.age);
        h.actions.setVisibility(View.GONE);
        h.line1.setText(p.profession() + " · " + p.city());

        switch (mode) {
            case RECEIVED:
                h.meta.setText(R.string.status_new);
                h.actions.setVisibility(View.VISIBLE);
                h.secondary.setText(R.string.decline);
                h.primary.setText(R.string.accept);
                h.secondary.setOnClickListener(v -> listener.onRespond(p, false, v));
                h.primary.setOnClickListener(v -> listener.onRespond(p, true, v));
                break;
            case SENT:
                h.meta.setText(connected.contains(p.id) ? R.string.status_accepted : R.string.status_pending);
                break;
            case SHORTLIST:
                h.meta.setText(p.online ? c.getString(R.string.status_online_short) : "");
                break;
            case INBOX:
                if (item.last == null) {
                    h.line1.setText(R.string.say_hello);
                    h.meta.setText("");
                } else {
                    h.line1.setText(item.last.mine ? c.getString(R.string.you_prefix, item.last.text) : item.last.text);
                    h.meta.setText(Ui.time(item.last.time));
                }
                break;
        }

        h.itemView.setOnClickListener(v -> {
            Haptics.tap(v);
            Intent intent;
            if (mode == Mode.INBOX) {
                intent = new Intent(c, ChatActivity.class).putExtra(ChatActivity.EXTRA_ID, p.id);
            } else {
                intent = new Intent(c, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_ID, p.id);
            }
            c.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView avatar, name, line1, meta;
        final ImageView photo;
        final View actions;
        final com.google.android.material.button.MaterialButton primary, secondary;

        Holder(View v) {
            super(v);
            avatar = v.findViewById(R.id.avatar);
            photo = v.findViewById(R.id.avatar_image);
            name = v.findViewById(R.id.name);
            line1 = v.findViewById(R.id.line1);
            meta = v.findViewById(R.id.meta);
            actions = v.findViewById(R.id.actions);
            primary = v.findViewById(R.id.btn_primary);
            secondary = v.findViewById(R.id.btn_secondary);
        }
    }
}
