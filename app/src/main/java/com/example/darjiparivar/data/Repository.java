package com.example.darjiparivar.data;

import android.content.Context;

import com.example.darjiparivar.BuildConfig;
import com.example.darjiparivar.data.supabase.SupabaseRepository;

import java.io.Closeable;
import java.util.List;
import java.util.Set;

/**
 * Everything the app reads or writes. Calls are blocking and may hit the network, so they must
 * run off the main thread (the ViewModels do this).
 *
 * <p>{@link SupabaseRepository} is used when Supabase keys are configured in local.properties;
 * otherwise the app runs in demo mode on {@link FakeRepository}'s sample data.
 */
public interface Repository {

    interface MessageListener {
        void onMessage(Message message);
    }

    static Repository get(Context context) {
        return Holder.get(context.getApplicationContext());
    }

    /** True when running on local sample data instead of a real backend. */
    boolean isDemo();

    // ---- Session ----

    boolean isSignedIn();

    /**
     * Signs in with a Google ID token from Credential Manager. {@code nonce} is the raw nonce whose
     * SHA-256 was sent to Google. In demo mode the token may be null.
     */
    void signInWithGoogle(String idToken, String nonce, String email, String name) throws RepoException;

    /** Whether the signed-in user has finished profile setup. Cached locally after the first check. */
    boolean hasProfile() throws RepoException;

    ProfileDraft me() throws RepoException;

    void saveProfile(ProfileDraft draft) throws RepoException;

    int MAX_PHOTOS = 6;

    /** The user's own photos, main photo first. */
    List<Photo> myPhotos() throws RepoException;

    /** Adds a JPEG photo; the first photo becomes the main one. At most {@link #MAX_PHOTOS}. */
    void addPhoto(byte[] jpeg) throws RepoException;

    void deletePhoto(String photoId) throws RepoException;

    void setMainPhoto(String photoId) throws RepoException;

    /**
     * Sends a Google Play purchase to the server, which checks it with Google and turns on the
     * subscription. In demo mode the token may be null and the subscription is simulated.
     */
    void verifySubscription(String productId, String purchaseToken) throws RepoException;

    void signOut();

    /** Permanently deletes the user's account, profile, photos and messages. */
    void deleteAccount() throws RepoException;

    // ---- Discovery ----

    List<Profile> discover() throws RepoException;

    /** Last deck loaded successfully, for offline use. Never throws. */
    List<Profile> cachedDiscover();

    Profile profile(String id) throws RepoException;

    Relationship relationship(String id) throws RepoException;

    /** The member's contact email, or null while it's hidden (before both sides accept). */
    String contact(String id) throws RepoException;

    // ---- Actions ----

    /** @return true if the profile is now shortlisted. */
    boolean toggleShortlist(String id) throws RepoException;

    /** @throws RepoException with {@link RepoException.Reason#DAILY_LIMIT} past the daily limit. */
    void sendInterest(String id) throws RepoException;

    void respondToInterest(String senderId, boolean accept) throws RepoException;

    void block(String id) throws RepoException;

    void report(String id, String reason, String details) throws RepoException;

    // ---- Lists ----

    List<Profile> shortlisted() throws RepoException;

    List<Profile> sentInterests() throws RepoException;

    List<Profile> receivedInterests() throws RepoException;

    /** Ids of members the user is connected with (an interest was accepted). */
    Set<String> connectedIds() throws RepoException;

    List<Conversation> conversations() throws RepoException;

    // ---- Chat ----

    List<Message> messages(String partnerId) throws RepoException;

    Message sendMessage(String partnerId, String text) throws RepoException;

    /** Delivers new messages from the partner on the main thread until closed. */
    Closeable subscribe(String partnerId, MessageListener listener);

    final class Holder {
        private static Repository instance;

        private Holder() {}

        static synchronized Repository get(Context app) {
            if (instance == null) {
                boolean configured = !BuildConfig.SUPABASE_URL.isEmpty()
                        && !BuildConfig.SUPABASE_ANON_KEY.isEmpty();
                instance = configured
                        ? new SupabaseRepository(app, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
                        : new FakeRepository(app);
            }
            return instance;
        }

        /** For tests: swap in a specific repository. */
        public static synchronized void set(Repository repository) {
            instance = repository;
        }
    }
}
