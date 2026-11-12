package com.example.darjiparivar.ui;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Profile;
import com.example.darjiparivar.data.Relationship;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    static final String EXTRA_ID = "profile_id";

    static class Details {
        Profile profile;
        Relationship relationship;
        String contact;
        /** Viewer has the subscription that unlocks full profiles. */
        boolean subscribed;
    }

    public static class ProfileViewModel extends BaseViewModel {
        final MutableLiveData<Resource<Details>> details = new MutableLiveData<>();
        String id;

        public ProfileViewModel(@NonNull Application app) { super(app); }

        void load() {
            String profileId = id;
            load(details, () -> {
                Details d = new Details();
                d.profile = repo.profile(profileId);
                d.relationship = repo.relationship(profileId);
                d.subscribed = repo.me().subscribed;
                d.contact = d.relationship.connected ? repo.contact(profileId) : null;
                return d;
            });
        }
    }

    private ProfileViewModel vm;
    private StateView state;
    private boolean detailsBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        Ui.applyInsets(findViewById(R.id.root));
        vm = new ViewModelProvider(this).get(ProfileViewModel.class);
        vm.id = id;
        state = new StateView(findViewById(R.id.state));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_more).setOnClickListener(v -> {
            Resource<Details> res = vm.details.getValue();
            if (res != null && res.data != null) SafetyDialogs.show(this, v, res.data.profile, vm, this::finish);
        });
        vm.details.observe(this, this::render);
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.load();
    }

    private void render(Resource<Details> res) {
        if (res.data == null) {
            findViewById(R.id.bottom_bar).setVisibility(View.GONE);
            if (res.status == Resource.Status.LOADING) state.loading();
            else state.error(Ui.error(this, res.error), vm::load);
            return;
        }
        state.hide();
        findViewById(R.id.content).setVisibility(View.VISIBLE);
        findViewById(R.id.bottom_bar).setVisibility(View.VISIBLE);
        Profile p = res.data.profile;
        if (!detailsBound) {
            bindProfile(p);
            detailsBound = true;
        }
        bindLock(res.data);
        bindContact(res.data);
        bindActions(res.data);
    }

    private void bindProfile(Profile p) {
        Ui.bindAvatar(findViewById(R.id.photo), p, 0);
        bindGallery(p);
        ((TextView) findViewById(R.id.profile_id)).setText(getString(R.string.profile_id, p.displayId));
        TextView status = findViewById(R.id.status);
        status.setText(Ui.status(this, p));
        status.setTextColor(getColor(p.online ? R.color.success : R.color.ink_muted));
        ((TextView) findViewById(R.id.name)).setText(p.name());
        ((TextView) findViewById(R.id.summary)).setText(Ui.summary(this, p));
        ((TextView) findViewById(R.id.about)).setText(p.about());
        findViewById(R.id.tag_verified).setVisibility(p.verified ? View.VISIBLE : View.GONE);
        findViewById(R.id.tag_premium).setVisibility(p.premium ? View.VISIBLE : View.GONE);

        LinearLayout details = findViewById(R.id.details);
        details.removeAllViews();
        addDetail(details, R.string.key_age, getString(R.string.years, p.age));
        addDetail(details, R.string.key_height, p.height);
        addDetail(details, R.string.key_community, p.community());
        addDetail(details, R.string.key_lives_in, p.location());
        addDetail(details, R.string.key_education, p.education());
        addDetail(details, R.string.key_profession, p.profession());
        addDetail(details, R.string.key_income, p.income());
        addDetail(details, R.string.key_family, p.family());
    }

    /** Horizontal, snapping photo pager with a "2 / 5" counter. */
    private void bindGallery(Profile p) {
        RecyclerView gallery = findViewById(R.id.gallery);
        TextView count = findViewById(R.id.photo_count);
        List<String> urls = p.photoUrls;
        boolean has = !urls.isEmpty();
        gallery.setVisibility(has ? View.VISIBLE : View.GONE);
        count.setVisibility(urls.size() > 1 ? View.VISIBLE : View.GONE);
        if (!has) return;
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        gallery.setLayoutManager(lm);
        gallery.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery_photo, parent, false);
                return new RecyclerView.ViewHolder(v) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Glide.with(h.itemView).load(urls.get(position)).centerCrop().into((ImageView) h.itemView);
            }

            @Override
            public int getItemCount() {
                return urls.size();
            }
        });
        new PagerSnapHelper().attachToRecyclerView(gallery);
        count.setText(getString(R.string.photo_count, 1, urls.size()));
        gallery.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                int pos = lm.findFirstCompletelyVisibleItemPosition();
                if (pos >= 0) {
                    count.setText(getString(R.string.photo_count, pos + 1, urls.size()));
                    Haptics.tick(rv);
                }
            }
        });
    }

    /** Free members see the card details only; the rest needs the ₹99/month subscription. */
    private void bindLock(Details d) {
        findViewById(R.id.locked).setVisibility(d.subscribed ? View.GONE : View.VISIBLE);
        findViewById(R.id.premium_sections).setVisibility(d.subscribed ? View.VISIBLE : View.GONE);
        // Without a subscription only the main photo shows (the server hides the rest too).
        ((RecyclerView) findViewById(R.id.gallery)).suppressLayout(!d.subscribed);
        if (!d.subscribed) findViewById(R.id.photo_count).setVisibility(View.GONE);
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            Haptics.tap(v);
            startActivity(new Intent(this, SubscribeActivity.class));
        });
    }

    private void bindContact(Details d) {
        TextView contact = findViewById(R.id.contact);
        boolean visible = d.contact != null && !d.contact.isEmpty();
        contact.setText(visible ? d.contact : getString(R.string.contact_hidden));
        contact.setTextColor(getColor(visible ? R.color.ink : R.color.ink_muted));
        contact.setCompoundDrawablesRelativeWithIntrinsicBounds(visible ? 0 : R.drawable.ic_lock, 0, 0, 0);
        contact.setTextIsSelectable(visible);
    }

    private void bindActions(Details d) {
        Profile p = d.profile;
        Relationship r = d.relationship;
        MaterialButton shortlist = findViewById(R.id.btn_shortlist);
        MaterialButton interest = findViewById(R.id.btn_interest);
        View bar = findViewById(R.id.bottom_bar);

        Ui.bindShortlist(shortlist, r.shortlisted);
        shortlist.setOnClickListener(v -> {
            Haptics.tap(v);
            vm.run(() -> vm.repo.toggleShortlist(p.id), (added, error) -> {
                if (error != null) {
                    Ui.snack(v, Ui.error(this, error), bar);
                    return;
                }
                r.shortlisted = added;
                Ui.bindShortlist(shortlist, added);
                Ui.snack(v, getString(added ? R.string.msg_shortlist_added : R.string.msg_shortlist_removed,
                        p.firstName()), bar);
            });
        });

        interest.setEnabled(true);
        if (r.connected) {
            interest.setText(R.string.message);
            interest.setOnClickListener(v -> {
                Haptics.tap(v);
                startActivity(new Intent(this, ChatActivity.class).putExtra(ChatActivity.EXTRA_ID, p.id));
            });
        } else if (r.interestReceived) {
            interest.setText(R.string.accept_interest);
            interest.setOnClickListener(v -> vm.act(() -> { vm.repo.respondToInterest(p.id, true); return null; },
                    (x, error) -> {
                        if (error != null) {
                            Haptics.reject(v);
                            Ui.snack(v, Ui.error(this, error), bar);
                            return;
                        }
                        Haptics.confirm(v);
                        Ui.snack(v, getString(R.string.msg_connected, p.firstName()), bar);
                        vm.load();
                    }));
        } else {
            Ui.bindInterest(interest, r.interestSent);
            interest.setOnClickListener(v -> {
                interest.setEnabled(false);
                vm.act(() -> { vm.repo.sendInterest(p.id); return null; }, (x, error) -> {
                    if (error != null) {
                        interest.setEnabled(true);
                        Haptics.reject(v);
                        Ui.snack(v, Ui.error(this, error), bar);
                        return;
                    }
                    Haptics.confirm(v);
                    r.interestSent = true;
                    Ui.bindInterest(interest, true);
                    Ui.snack(v, getString(R.string.msg_interest_sent, p.firstName()), bar);
                });
            });
        }
    }

    private void addDetail(LinearLayout parent, int key, String value) {
        if (value == null || value.isEmpty()) return;
        View row = getLayoutInflater().inflate(R.layout.item_detail, parent, false);
        ((TextView) row.findViewById(R.id.key)).setText(key);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        parent.addView(row);
    }
}
