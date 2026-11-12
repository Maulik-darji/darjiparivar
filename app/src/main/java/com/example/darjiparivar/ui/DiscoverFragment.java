package com.example.darjiparivar.ui;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Profile;
import com.example.darjiparivar.data.RepoException;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Card-stack discovery: drag the top card right to send interest, left to pass.
 * The buttons below (and TalkBack actions) do the same with an animated swipe.
 */
public class DiscoverFragment extends Fragment {

    /** Activity-scoped so the deck and position survive switching tabs. */
    public static class DiscoverViewModel extends BaseViewModel {
        final MutableLiveData<Resource<List<Profile>>> deck = new MutableLiveData<>();
        int position;
        int filter;

        public DiscoverViewModel(@NonNull Application app) { super(app); }

        void load() {
            Resource<List<Profile>> current = deck.getValue();
            List<Profile> previous = current == null ? null : current.data;
            deck.setValue(Resource.loading(previous));
            run(repo::discover, (list, error) -> {
                if (error == null) {
                    deck.setValue(Resource.success(list));
                } else if (error.reason == RepoException.Reason.NETWORK && !repo.cachedDiscover().isEmpty()) {
                    deck.setValue(Resource.stale(repo.cachedDiscover(), error));
                } else {
                    deck.setValue(Resource.error(error, previous));
                }
            });
        }
    }

    private static final float BACK_SCALE = 0.94f;

    private final List<Profile> deck = new ArrayList<>();
    private DiscoverViewModel vm;
    private View front, back, empty, actions;
    private TextView count;
    private StateView state;
    private View likeStamp, passStamp;
    private float downX, downY;
    private boolean animating, pastThreshold;
    private final List<Integer> a11yActions = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_discover, container, false);
        vm = new ViewModelProvider(requireActivity()).get(DiscoverViewModel.class);

        TextView greeting = v.findViewById(R.id.greeting);
        count = v.findViewById(R.id.count);
        front = v.findViewById(R.id.card_front);
        back = v.findViewById(R.id.card_back);
        empty = v.findViewById(R.id.empty);
        actions = v.findViewById(R.id.actions);
        state = new StateView(v.findViewById(R.id.state));
        vm.run(() -> vm.repo.me().name, (name, e) -> {
            if (name != null && !name.isEmpty()) greeting.setText(getString(R.string.greeting, name.split(" ")[0]));
        });

        ChipGroup filters = v.findViewById(R.id.filters);
        filters.check(vm.filter == 0 ? R.id.chip_all : vm.filter);
        filters.setOnCheckedStateChangeListener((group, ids) -> {
            Haptics.tap(group);
            vm.filter = ids.isEmpty() ? R.id.chip_all : ids.get(0);
            vm.position = 0;
            applyFilter();
        });

        likeStamp = front.findViewById(R.id.label_like);
        passStamp = front.findViewById(R.id.label_pass);
        front.setOnTouchListener(this::onCardTouch);
        v.findViewById(R.id.btn_pass).setOnClickListener(b -> swipe(-1));
        v.findViewById(R.id.btn_like).setOnClickListener(b -> swipe(1));
        v.findViewById(R.id.btn_shortlist).setOnClickListener(this::toggleShortlist);
        v.findViewById(R.id.btn_restart).setOnClickListener(b -> {
            Haptics.tap(b);
            vm.position = 0;
            vm.load();
        });

        vm.deck.observe(getViewLifecycleOwner(), this::render);
        if (vm.deck.getValue() == null) vm.load();
        return v;
    }

    private void render(Resource<List<Profile>> res) {
        View root = getView();
        if (root == null) return;
        root.findViewById(R.id.offline).setVisibility(res.stale ? View.VISIBLE : View.GONE);
        if (res.data == null) {
            front.setVisibility(View.GONE);
            back.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            actions.setVisibility(View.INVISIBLE);
            if (res.status == Resource.Status.LOADING) state.loading();
            else state.error(Ui.error(requireContext(), res.error), vm::load);
            return;
        }
        state.hide();
        applyFilter();
    }

    private void applyFilter() {
        Resource<List<Profile>> res = vm.deck.getValue();
        if (res == null || res.data == null) return;
        deck.clear();
        int filter = vm.filter == 0 ? R.id.chip_all : vm.filter;
        for (Profile p : res.data) {
            if (filter == R.id.chip_verified && !p.verified) continue;
            if (filter == R.id.chip_online && !p.online) continue;
            if (filter == R.id.chip_premium && !p.premium) continue;
            if (filter == R.id.chip_nri && !p.abroad()) continue;
            deck.add(p);
        }
        showCards();
    }

    private Profile current() {
        return vm.position < deck.size() ? deck.get(vm.position) : null;
    }

    private void showCards() {
        int pos = vm.position;
        boolean done = pos >= deck.size();
        empty.setVisibility(done ? View.VISIBLE : View.GONE);
        actions.setVisibility(done ? View.INVISIBLE : View.VISIBLE);
        front.setVisibility(done ? View.GONE : View.VISIBLE);
        back.setVisibility(pos + 1 < deck.size() ? View.VISIBLE : View.GONE);
        count.setText(getString(R.string.deck_count, Math.min(pos + 1, deck.size()), deck.size()));

        resetCard(front);
        back.setScaleX(BACK_SCALE);
        back.setScaleY(BACK_SCALE);
        back.setTranslationY(dp(18));
        if (!done) bindCard(front, deck.get(pos));
        if (pos + 1 < deck.size()) bindCard(back, deck.get(pos + 1));
        bindAccessibility(done ? null : deck.get(pos));
    }

    private void bindCard(View card, Profile p) {
        Ui.bindAvatar(card.findViewById(R.id.photo), p, 0);
        Ui.bindPhoto(card.findViewById(R.id.photo_image), p.photoUrl, 0);
        card.findViewById(R.id.tag_verified).setVisibility(p.verified ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.tag_premium).setVisibility(p.premium ? View.VISIBLE : View.GONE);
        ((TextView) card.findViewById(R.id.status)).setText(Ui.status(requireContext(), p));
        ((TextView) card.findViewById(R.id.name)).setText(p.name() + ", " + p.age);
        ((TextView) card.findViewById(R.id.summary)).setText(p.height + "  ·  " + p.community());
        ((TextView) card.findViewById(R.id.profession)).setText(p.profession() + "  ·  " + p.education());
        ((TextView) card.findViewById(R.id.location)).setText(p.location());
        card.setContentDescription(getString(R.string.card_description, p.name(), p.age,
                p.profession(), p.city()));
    }

    /** TalkBack users get "Send interest" / "Pass" actions instead of having to swipe. */
    private void bindAccessibility(Profile p) {
        for (int id : a11yActions) ViewCompat.removeAccessibilityAction(front, id);
        a11yActions.clear();
        if (p == null) return;
        a11yActions.add(ViewCompat.addAccessibilityAction(front, getString(R.string.send_interest),
                (view, args) -> { swipe(1); return true; }));
        a11yActions.add(ViewCompat.addAccessibilityAction(front, getString(R.string.pass),
                (view, args) -> { swipe(-1); return true; }));
        a11yActions.add(ViewCompat.addAccessibilityAction(front, getString(R.string.shortlist),
                (view, args) -> { toggleShortlist(front); return true; }));
    }

    private void resetCard(View card) {
        card.animate().cancel();
        card.setTranslationX(0);
        card.setTranslationY(0);
        card.setRotation(0);
        card.setScaleX(1);
        card.setScaleY(1);
        card.findViewById(R.id.label_like).setAlpha(0);
        card.findViewById(R.id.label_pass).setAlpha(0);
    }

    // ---- Gestures ----

    private boolean onCardTouch(View v, MotionEvent e) {
        if (animating) return true;
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                pastThreshold = false;
                v.getParent().requestDisallowInterceptTouchEvent(true);
                // Move a cached bitmap of the card instead of redrawing its views every frame.
                front.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                back.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                return true;
            case MotionEvent.ACTION_MOVE:
                v.setTranslationX(dx);
                v.setTranslationY(dy * 0.25f);
                v.setRotation(dx / v.getWidth() * 18f);
                onDrag(dx);
                boolean past = Math.abs(dx) > threshold();
                if (past != pastThreshold) {
                    pastThreshold = past;
                    Haptics.tick(v);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(dx) > threshold()) {
                    swipe(dx > 0 ? 1 : -1);
                } else if (Math.abs(dx) < dp(8) && Math.abs(dy) < dp(8)) {
                    settle();
                    v.performClick();
                    openProfile();
                } else {
                    settle();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                settle();
                return true;
        }
        return false;
    }

    private float threshold() {
        return front.getWidth() * 0.28f;
    }

    /** Fades the stamps in and grows the back card as the front card is dragged. */
    private void onDrag(float dx) {
        float progress = Math.min(1f, Math.abs(dx) / threshold());
        likeStamp.setAlpha(dx > 0 ? progress : 0);
        passStamp.setAlpha(dx < 0 ? progress : 0);
        float scale = BACK_SCALE + (1 - BACK_SCALE) * progress;
        back.setScaleX(scale);
        back.setScaleY(scale);
        back.setTranslationY(dp(18) * (1 - progress));
    }

    private void settle() {
        front.animate().translationX(0).translationY(0).rotation(0)
                .setInterpolator(new OvershootInterpolator(1.2f)).setDuration(260)
                .withEndAction(this::dropLayers).start();
        likeStamp.animate().alpha(0).setDuration(150).start();
        passStamp.animate().alpha(0).setDuration(150).start();
        back.animate().scaleX(BACK_SCALE).scaleY(BACK_SCALE).translationY(dp(18))
                .setDuration(260).start();
    }

    /** Flings the front card off-screen: dir 1 = interested, -1 = pass. */
    private void swipe(int dir) {
        Profile p = current();
        if (p == null || animating) return;
        animating = true;
        if (dir > 0) Haptics.confirm(front); else Haptics.reject(front);
        onDrag(dir * threshold());
        float distance = front.getWidth() * 1.5f;
        back.animate().scaleX(1).scaleY(1).translationY(0).setDuration(280).start();
        front.animate()
                .translationX(dir * distance)
                .translationY(front.getTranslationY() + dp(40))
                .rotation(dir * 24f)
                .setInterpolator(new AccelerateInterpolator())
                .setDuration(280)
                .withLayer()
                .withEndAction(() -> {
                    animating = false;
                    dropLayers();
                    if (!isAdded()) return;
                    vm.position++;
                    showCards();
                    if (dir > 0) sendInterest(p);
                })
                .start();
    }

    private void sendInterest(Profile p) {
        int sentAt = vm.position - 1;
        vm.act(() -> { vm.repo.sendInterest(p.id); return null; }, (r, error) -> {
            if (!isAdded()) return;
            if (error == null) {
                message(getString(R.string.msg_interest_sent, p.firstName()));
                return;
            }
            Haptics.reject(front);
            message(Ui.error(requireContext(), error));
            // Put the card back so the user can try again later.
            if (vm.position == sentAt + 1) {
                vm.position = sentAt;
                showCards();
            }
        });
    }

    private void dropLayers() {
        front.setLayerType(View.LAYER_TYPE_NONE, null);
        back.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private void toggleShortlist(View button) {
        Profile p = current();
        if (p == null) return;
        Haptics.tap(button);
        front.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90)
                .withEndAction(() -> front.animate().scaleX(1).scaleY(1).setDuration(140).start())
                .start();
        vm.run(() -> vm.repo.toggleShortlist(p.id), (added, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                message(Ui.error(requireContext(), error));
                return;
            }
            message(getString(added ? R.string.msg_shortlist_added : R.string.msg_shortlist_removed, p.firstName()));
        });
    }

    private void openProfile() {
        Profile p = current();
        if (p == null) return;
        Haptics.tap(front);
        startActivity(new Intent(requireContext(), ProfileActivity.class).putExtra(ProfileActivity.EXTRA_ID, p.id));
    }

    private void message(String text) {
        ((MainActivity) requireActivity()).message(text);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
