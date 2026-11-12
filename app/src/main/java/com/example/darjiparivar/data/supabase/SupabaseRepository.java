package com.example.darjiparivar.data.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.darjiparivar.data.Conversation;
import com.example.darjiparivar.data.Message;
import com.example.darjiparivar.data.Photo;
import com.example.darjiparivar.data.Profile;
import com.example.darjiparivar.data.ProfileDraft;
import com.example.darjiparivar.data.Relationship;
import com.example.darjiparivar.data.RepoException;
import com.example.darjiparivar.data.RepoException.Reason;
import com.example.darjiparivar.data.Repository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.darjiparivar.data.supabase.ProfileMapper.SELECT;
import static com.example.darjiparivar.data.supabase.ProfileMapper.str;

/**
 * Supabase-backed repository. Access rules are enforced on the server by Row Level Security
 * (see supabase/migrations); this class only shapes requests and maps rows.
 */
public class SupabaseRepository implements Repository {

    private static final String BUCKET = "photos";
    private static final Map<String, String> RETURN_ROWS = Collections.singletonMap("Prefer", "return=representation");
    private static final Map<String, String> UPSERT = Collections.singletonMap("Prefer", "resolution=merge-duplicates");

    private final SupabaseClient client;
    private final SharedPreferences prefs;
    private final Map<String, String> conversationIds = new HashMap<>();

    public SupabaseRepository(Context context, String url, String anonKey) {
        prefs = context.getSharedPreferences("darji_parivar_supabase", Context.MODE_PRIVATE);
        client = new SupabaseClient(url, anonKey, prefs);
    }

    @Override public boolean isDemo() { return false; }

    private String uid() throws RepoException {
        String id = client.userId();
        if (id == null) throw new RepoException(Reason.AUTH, "Not signed in");
        return id;
    }

    // ---- Session ----

    @Override
    public boolean isSignedIn() {
        return client.hasSession();
    }

    @Override
    public void signInWithGoogle(String idToken, String nonce, String email, String name) throws RepoException {
        if (idToken == null) throw new RepoException(Reason.AUTH, "Google sign-in is not configured");
        JsonObject body = new JsonObject();
        body.addProperty("provider", "google");
        body.addProperty("id_token", idToken);
        if (nonce != null) body.addProperty("nonce", nonce);
        client.storeSession(client.call("POST", "/auth/v1/token?grant_type=id_token", body, null));
        prefs.edit()
                .putString("contact", email == null ? "" : email)
                .putString("google_name", name == null ? "" : name)
                .remove("has_profile")
                .apply();
    }

    @Override
    public boolean hasProfile() throws RepoException {
        if (prefs.getBoolean("has_profile", false)) return true;
        boolean has = rows("/rest/v1/profiles?select=id&id=eq." + uid()).size() > 0;
        if (has) prefs.edit().putBoolean("has_profile", true).apply();
        return has;
    }

    @Override
    public ProfileDraft me() throws RepoException {
        JsonArray rows = rows("/rest/v1/profiles?select=*&id=eq." + uid());
        if (rows.size() > 0) ProfileMapper.mergeDetails(rows.get(0).getAsJsonObject(), details(uid()));
        ProfileDraft d = rows.size() > 0 ? ProfileMapper.toDraft(rows.get(0).getAsJsonObject()) : new ProfileDraft();
        if (rows.size() == 0) d.name = prefs.getString("google_name", "");
        if (rows.size() > 0) {
            String path = str(rows.get(0).getAsJsonObject(), "photo_path");
            if (!path.isEmpty()) d.photoUrl = signedUrls(Collections.singletonList(path)).get(path);
        }
        d.contact = prefs.getString("contact", "");
        return d;
    }

    @Override
    public void saveProfile(ProfileDraft draft) throws RepoException {
        String me = uid();
        client.call("POST", "/rest/v1/profiles?on_conflict=id", ProfileMapper.fromDraft(me, draft), UPSERT);
        client.call("POST", "/rest/v1/profile_details?on_conflict=profile_id",
                ProfileMapper.detailsFromDraft(me, draft), UPSERT);
        String contact = prefs.getString("contact", "");
        JsonObject c = new JsonObject();
        c.addProperty("profile_id", me);
        c.addProperty("email", contact);
        client.call("POST", "/rest/v1/profile_contacts?on_conflict=profile_id", c, UPSERT);
        JsonArray translations = new JsonArray();
        for (Map.Entry<String, String> e : draft.gu.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            JsonObject t = new JsonObject();
            t.addProperty("profile_id", me);
            t.addProperty("lang", "gu");
            t.addProperty("field", e.getKey());
            t.addProperty("value", e.getValue());
            translations.add(t);
        }
        if (translations.size() > 0) {
            client.call("POST", "/rest/v1/profile_translations?on_conflict=profile_id,lang,field",
                    translations, UPSERT);
        }
        prefs.edit().putBoolean("has_profile", true).apply();
    }

    private JsonArray photoRows(String ownerId) throws RepoException {
        return rows("/rest/v1/photos?select=id,path,is_primary&owner_id=eq." + ownerId
                + "&order=is_primary.desc,created_at.asc");
    }

    @Override
    public List<Photo> myPhotos() throws RepoException {
        JsonArray rows = photoRows(uid());
        List<String> paths = new ArrayList<>();
        for (JsonElement e : rows) paths.add(str(e.getAsJsonObject(), "path"));
        Map<String, String> urls = paths.isEmpty() ? Collections.emptyMap() : signedUrls(paths);
        List<Photo> out = new ArrayList<>();
        for (JsonElement e : rows) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Photo(str(o, "id"), urls.get(str(o, "path")), ProfileMapper.bool(o, "is_primary")));
        }
        return out;
    }

    @Override
    public void addPhoto(byte[] jpeg) throws RepoException {
        String me = uid();
        int existing = photoRows(me).size();
        if (existing >= MAX_PHOTOS) throw new RepoException(Reason.NOT_ALLOWED, "Photo limit reached");
        String path = me + "/" + UUID.randomUUID() + ".jpg";
        client.upload("/storage/v1/object/" + BUCKET + "/" + path, jpeg);
        JsonObject photo = new JsonObject();
        photo.addProperty("owner_id", me);
        photo.addProperty("path", path);
        photo.addProperty("is_primary", existing == 0);
        client.call("POST", "/rest/v1/photos", photo, null);
        if (existing == 0) setProfilePhoto(me, path);
    }

    @Override
    public void deletePhoto(String photoId) throws RepoException {
        String me = uid();
        JsonArray rows = photoRows(me);
        JsonObject target = null;
        for (JsonElement e : rows) if (photoId.equals(str(e.getAsJsonObject(), "id"))) target = e.getAsJsonObject();
        if (target == null) return;
        deleteObjects(Collections.singletonList(str(target, "path")));
        client.call("DELETE", "/rest/v1/photos?id=eq." + photoId, null, null);
        if (ProfileMapper.bool(target, "is_primary")) {
            JsonArray left = photoRows(me);
            if (left.size() > 0) setMainPhoto(str(left.get(0).getAsJsonObject(), "id"));
            else setProfilePhoto(me, null);
        }
    }

    @Override
    public void setMainPhoto(String photoId) throws RepoException {
        String me = uid();
        JsonObject off = new JsonObject();
        off.addProperty("is_primary", false);
        client.call("PATCH", "/rest/v1/photos?owner_id=eq." + me + "&is_primary=eq.true", off, null);
        JsonObject on = new JsonObject();
        on.addProperty("is_primary", true);
        JsonArray updated = client.call("PATCH", "/rest/v1/photos?id=eq." + photoId, on, RETURN_ROWS).getAsJsonArray();
        if (updated.size() > 0) setProfilePhoto(me, str(updated.get(0).getAsJsonObject(), "path"));
    }

    private void setProfilePhoto(String me, String path) throws RepoException {
        JsonObject patch = new JsonObject();
        if (path == null) patch.add("photo_path", com.google.gson.JsonNull.INSTANCE);
        else patch.addProperty("photo_path", path);
        client.call("PATCH", "/rest/v1/profiles?id=eq." + me, patch, null);
    }

    private void deleteObjects(List<String> paths) throws RepoException {
        if (paths.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray prefixes = new JsonArray();
        for (String p : paths) prefixes.add(p);
        body.add("prefixes", prefixes);
        try {
            client.call("DELETE", "/storage/v1/object/" + BUCKET, body, null);
        } catch (RepoException e) {
            if (e.reason != Reason.NOT_FOUND) throw e;
        }
    }

    private JsonObject details(String profileId) throws RepoException {
        JsonArray rows = rows("/rest/v1/profile_details?select=about,income,family&profile_id=eq." + profileId);
        return rows.size() > 0 ? rows.get(0).getAsJsonObject() : null;
    }

    @Override
    public void verifySubscription(String productId, String purchaseToken) throws RepoException {
        if (purchaseToken == null) throw new RepoException(Reason.NOT_ALLOWED, "No purchase to verify");
        JsonObject body = new JsonObject();
        body.addProperty("productId", productId);
        body.addProperty("purchaseToken", purchaseToken);
        // Edge Function: checks the token with Google Play and sets premium_until.
        client.call("POST", "/functions/v1/verify-subscription", body, null);
    }

    @Override
    public void signOut() {
        try {
            client.call("POST", "/auth/v1/logout", null, null);
        } catch (RepoException ignored) {
            // Clear the local session either way.
        }
        client.clearSession();
        prefs.edit().clear().apply();
        conversationIds.clear();
    }

    @Override
    public void deleteAccount() throws RepoException {
        List<String> paths = new ArrayList<>();
        for (JsonElement e : photoRows(uid())) paths.add(str(e.getAsJsonObject(), "path"));
        deleteObjects(paths);
        client.rpc("delete_account", null);
        client.clearSession();
        prefs.edit().clear().apply();
    }

    // ---- Discovery ----

    @Override
    public List<Profile> discover() throws RepoException {
        ProfileDraft me = me();
        String path = "/rest/v1/profiles?select=" + SELECT
                + "&id=neq." + uid()
                + "&gender=eq." + (me.female ? "M" : "F")
                + "&age=gte." + me.prefAgeMin + "&age=lte." + me.prefAgeMax
                + "&order=last_seen.desc&limit=60";
        JsonArray rows = rows(path);
        prefs.edit().putString("deck_cache", rows.toString()).apply();
        return toProfiles(rows, true);
    }

    @Override
    public List<Profile> cachedDiscover() {
        String json = prefs.getString("deck_cache", null);
        if (json == null) return new ArrayList<>();
        try {
            return toProfiles(JsonParser.parseString(json).getAsJsonArray(), false);
        } catch (RepoException | RuntimeException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public Profile profile(String id) throws RepoException {
        JsonArray rows = rows("/rest/v1/profiles?select=" + SELECT + "&id=eq." + id);
        if (rows.size() == 0) throw new RepoException(Reason.NOT_FOUND, "No profile " + id);
        // Returns nothing unless the viewer is subscribed (or it's their own profile).
        ProfileMapper.mergeDetails(rows.get(0).getAsJsonObject(), details(id));
        Profile p = toProfiles(rows, true).get(0);
        // Gallery: the photos policies decide which of these this viewer may see.
        List<String> paths = new ArrayList<>();
        for (JsonElement e : photoRows(id)) paths.add(str(e.getAsJsonObject(), "path"));
        Map<String, String> urls = paths.isEmpty() ? Collections.emptyMap() : signedUrls(paths);
        for (String path : paths) if (urls.get(path) != null) p.photoUrls.add(urls.get(path));
        if (p.photoUrls.isEmpty() && p.photoUrl != null) p.photoUrls.add(p.photoUrl);
        return p;
    }

    @Override
    public Relationship relationship(String id) throws RepoException {
        String me = uid();
        Relationship r = new Relationship();
        r.shortlisted = rows("/rest/v1/shortlists?select=profile_id&owner_id=eq." + me + "&profile_id=eq." + id).size() > 0;
        r.blocked = rows("/rest/v1/blocks?select=blocked_id&blocker_id=eq." + me + "&blocked_id=eq." + id).size() > 0;
        JsonArray interests = rows("/rest/v1/interests?select=sender_id,status&or=(and(sender_id.eq." + me
                + ",receiver_id.eq." + id + "),and(sender_id.eq." + id + ",receiver_id.eq." + me + "))");
        for (JsonElement e : interests) {
            JsonObject o = e.getAsJsonObject();
            boolean mine = me.equals(str(o, "sender_id"));
            String status = str(o, "status");
            if (mine) r.interestSent = true;
            if (!mine && "pending".equals(status)) r.interestReceived = true;
            if ("accepted".equals(status)) r.connected = true;
        }
        return r;
    }

    @Override
    public String contact(String id) throws RepoException {
        JsonArray rows = rows("/rest/v1/profile_contacts?select=phone,email&profile_id=eq." + id);
        if (rows.size() == 0) return null;
        JsonObject o = rows.get(0).getAsJsonObject();
        String phone = str(o, "phone");
        return phone.isEmpty() ? str(o, "email") : phone;
    }

    // ---- Actions ----

    @Override
    public boolean toggleShortlist(String id) throws RepoException {
        String me = uid();
        String filter = "/rest/v1/shortlists?owner_id=eq." + me + "&profile_id=eq." + id;
        if (rows(filter + "&select=profile_id").size() > 0) {
            client.call("DELETE", filter, null, null);
            return false;
        }
        JsonObject row = new JsonObject();
        row.addProperty("owner_id", me);
        row.addProperty("profile_id", id);
        client.call("POST", "/rest/v1/shortlists", row, null);
        return true;
    }

    @Override
    public void sendInterest(String id) throws RepoException {
        JsonObject args = new JsonObject();
        args.addProperty("target", id);
        client.rpc("send_interest", args);
    }

    @Override
    public void respondToInterest(String senderId, boolean accept) throws RepoException {
        JsonObject args = new JsonObject();
        args.addProperty("sender", senderId);
        args.addProperty("accept", accept);
        client.rpc("respond_interest", args);
    }

    @Override
    public void block(String id) throws RepoException {
        JsonObject row = new JsonObject();
        row.addProperty("blocker_id", uid());
        row.addProperty("blocked_id", id);
        client.call("POST", "/rest/v1/blocks?on_conflict=blocker_id,blocked_id", row, UPSERT);
    }

    @Override
    public void report(String id, String reason, String details) throws RepoException {
        JsonObject row = new JsonObject();
        row.addProperty("reporter_id", uid());
        row.addProperty("reported_id", id);
        row.addProperty("reason", reason);
        row.addProperty("details", details);
        client.call("POST", "/rest/v1/reports", row, null);
    }

    // ---- Lists ----

    @Override
    public List<Profile> shortlisted() throws RepoException {
        return embedded(rows("/rest/v1/shortlists?select=p:profiles(" + SELECT + ")&owner_id=eq." + uid()
                + "&order=created_at.desc"), "p");
    }

    @Override
    public List<Profile> sentInterests() throws RepoException {
        return embedded(rows("/rest/v1/interests?select=p:profiles!interests_receiver_id_fkey(" + SELECT
                + ")&sender_id=eq." + uid() + "&order=created_at.desc"), "p");
    }

    @Override
    public List<Profile> receivedInterests() throws RepoException {
        return embedded(rows("/rest/v1/interests?select=p:profiles!interests_sender_id_fkey(" + SELECT
                + ")&receiver_id=eq." + uid() + "&status=eq.pending&order=created_at.desc"), "p");
    }

    @Override
    public Set<String> connectedIds() throws RepoException {
        String me = uid();
        Set<String> out = new HashSet<>();
        for (JsonElement e : rows("/rest/v1/interests?select=sender_id,receiver_id&status=eq.accepted")) {
            JsonObject o = e.getAsJsonObject();
            String s = str(o, "sender_id");
            out.add(me.equals(s) ? str(o, "receiver_id") : s);
        }
        return out;
    }

    @Override
    public List<Conversation> conversations() throws RepoException {
        String me = uid();
        JsonArray rows = rows("/rest/v1/conversations?select=id,user_a,user_b,"
                + "a:profiles!conversations_user_a_fkey(" + SELECT + "),"
                + "b:profiles!conversations_user_b_fkey(" + SELECT + "),"
                + "messages(id,sender_id,body,created_at)"
                + "&messages.order=created_at.desc&messages.limit=1");
        List<JsonObject> partners = new ArrayList<>();
        for (JsonElement e : rows) {
            JsonObject o = e.getAsJsonObject();
            boolean iAmA = me.equals(str(o, "user_a"));
            JsonElement partner = o.get(iAmA ? "b" : "a");
            if (partner != null && partner.isJsonObject()) partners.add(partner.getAsJsonObject());
        }
        List<Profile> profiles = toProfiles(partners, true);
        List<Conversation> out = new ArrayList<>();
        int i = 0;
        for (JsonElement e : rows) {
            JsonObject o = e.getAsJsonObject();
            JsonElement partner = o.get(me.equals(str(o, "user_a")) ? "b" : "a");
            if (partner == null || !partner.isJsonObject()) continue;
            Profile p = profiles.get(i++);
            conversationIds.put(p.id, str(o, "id"));
            JsonArray msgs = o.getAsJsonArray("messages");
            Message last = msgs != null && msgs.size() > 0 ? toMessage(msgs.get(0).getAsJsonObject(), me) : null;
            out.add(new Conversation(p, last));
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastTime(), a.lastTime()));
        return out;
    }

    // ---- Chat ----

    private String conversationId(String partnerId) throws RepoException {
        String cached = conversationIds.get(partnerId);
        if (cached != null) return cached;
        String me = uid();
        JsonArray rows = rows("/rest/v1/conversations?select=id&or=(and(user_a.eq." + me + ",user_b.eq."
                + partnerId + "),and(user_a.eq." + partnerId + ",user_b.eq." + me + "))");
        if (rows.size() == 0) throw new RepoException(Reason.NOT_ALLOWED, "Not connected");
        String id = str(rows.get(0).getAsJsonObject(), "id");
        conversationIds.put(partnerId, id);
        return id;
    }

    @Override
    public List<Message> messages(String partnerId) throws RepoException {
        String me = uid();
        List<Message> out = new ArrayList<>();
        for (JsonElement e : rows("/rest/v1/messages?select=id,sender_id,body,created_at&conversation_id=eq."
                + conversationId(partnerId) + "&order=created_at.asc&limit=500")) {
            out.add(toMessage(e.getAsJsonObject(), me));
        }
        return out;
    }

    @Override
    public Message sendMessage(String partnerId, String text) throws RepoException {
        String me = uid();
        JsonObject row = new JsonObject();
        row.addProperty("conversation_id", conversationId(partnerId));
        row.addProperty("sender_id", me);
        row.addProperty("body", text);
        JsonArray created = client.call("POST", "/rest/v1/messages", row, RETURN_ROWS).getAsJsonArray();
        return toMessage(created.get(0).getAsJsonObject(), me);
    }

    @Override
    public Closeable subscribe(String partnerId, MessageListener listener) {
        String id = conversationIds.get(partnerId);
        String me = client.userId();
        if (id == null || me == null) return () -> { };
        return new RealtimeChannel(client, id, record -> {
            Message m = toMessage(record, me);
            if (!m.mine) listener.onMessage(m);
        });
    }

    // ---- Helpers ----

    private JsonArray rows(String path) throws RepoException {
        JsonElement e = client.call("GET", path, null, null);
        return e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private List<Profile> embedded(JsonArray rows, String key) throws RepoException {
        List<JsonObject> inner = new ArrayList<>();
        for (JsonElement e : rows) {
            JsonElement p = e.getAsJsonObject().get(key);
            if (p != null && p.isJsonObject()) inner.add(p.getAsJsonObject());
        }
        return toProfiles(inner, true);
    }

    private List<Profile> toProfiles(Iterable<JsonElement> rows, boolean signPhotos) throws RepoException {
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement e : rows) list.add(e.getAsJsonObject());
        return toProfiles(list, signPhotos);
    }

    private List<Profile> toProfiles(List<JsonObject> rows, boolean signPhotos) throws RepoException {
        long now = System.currentTimeMillis();
        List<String> paths = new ArrayList<>();
        for (JsonObject o : rows) if (!str(o, "photo_path").isEmpty()) paths.add(str(o, "photo_path"));
        // Signing only succeeds for photos the storage policies let this user see.
        Map<String, String> urls = signPhotos && !paths.isEmpty() ? signedUrls(paths) : Collections.emptyMap();
        List<Profile> out = new ArrayList<>();
        for (JsonObject o : rows) {
            Profile p = ProfileMapper.toProfile(o, now);
            p.photoUrl = urls.get(str(o, "photo_path"));
            out.add(p);
        }
        return out;
    }

    private Map<String, String> signedUrls(List<String> paths) throws RepoException {
        JsonObject body = new JsonObject();
        body.addProperty("expiresIn", 60 * 60);
        JsonArray arr = new JsonArray();
        for (String p : paths) arr.add(p);
        body.add("paths", arr);
        Map<String, String> out = new HashMap<>();
        JsonElement res;
        try {
            res = client.call("POST", "/storage/v1/object/sign/" + BUCKET, body, null);
        } catch (RepoException e) {
            if (e.reason == Reason.NETWORK) throw e;
            return out;
        }
        if (!res.isJsonArray()) return out;
        for (JsonElement e : res.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            String signed = str(o, "signedURL");
            if (!signed.isEmpty()) out.put(str(o, "path"), client.url + "/storage/v1" + signed);
        }
        return out;
    }

    private static Message toMessage(JsonObject o, String me) {
        return new Message(o.get("id").getAsLong(), str(o, "body"), me.equals(str(o, "sender_id")),
                ProfileMapper.parseTime(str(o, "created_at")));
    }
}
