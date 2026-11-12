package com.example.darjiparivar.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.darjiparivar.R;
import com.google.gson.Gson;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo backend: 14 sample members, with the user's own data in SharedPreferences. Used when no
 * Supabase keys are configured, and in tests.
 */
public class FakeRepository implements Repository {

    static final int DAILY_INTEREST_LIMIT = 20;

    private static final String PREFS = "darji_parivar_demo";
    private static final String KEY_ME = "me";
    private static final String KEY_SIGNED_IN = "signed_in";
    private static final String KEY_SHORTLIST = "shortlist";
    private static final String KEY_SENT = "sent";
    private static final String KEY_ACCEPTED = "accepted";
    private static final String KEY_DECLINED = "declined";
    private static final String KEY_BLOCKED = "blocked";
    private static final String KEY_SENT_TIMES = "sent_times";
    private static final String KEY_PHOTOS = "my_photos";

    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Profile> profiles = new ArrayList<>();
    private final Map<String, List<Message>> chats = new HashMap<>();
    private final Map<String, MessageListener> listeners = new HashMap<>();
    private long nextMessageId = 1;
    private int replyIndex;

    public FakeRepository(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedProfiles();
    }

    @Override public boolean isDemo() { return true; }

    // ---- Session ----

    @Override
    public boolean isSignedIn() {
        return prefs.getBoolean(KEY_SIGNED_IN, false);
    }

    @Override
    public void signInWithGoogle(String idToken, String nonce, String email, String name) {
        // Demo mode trusts the Google account details as given (or signs in anonymously).
        prefs.edit()
                .putBoolean(KEY_SIGNED_IN, true)
                .putString("contact", email == null ? "" : email)
                .putString("google_name", name == null ? "" : name)
                .apply();
    }

    @Override
    public boolean hasProfile() {
        return prefs.contains(KEY_ME);
    }

    @Override
    public synchronized ProfileDraft me() {
        String json = prefs.getString(KEY_ME, null);
        ProfileDraft d = json == null ? new ProfileDraft() : gson.fromJson(json, ProfileDraft.class);
        if (json == null) d.name = prefs.getString("google_name", "");
        if (d.displayId.isEmpty()) d.displayId = "DP" + (50000 + Math.abs(d.name.hashCode()) % 50000);
        if (d.contact.isEmpty()) d.contact = prefs.getString("contact", "");
        if (d.subscribed && d.subscribedUntil < System.currentTimeMillis()) d.subscribed = false;
        d.photoUrl = null;
        for (Photo p : myPhotos()) if (p.primary) d.photoUrl = p.url;
        return d;
    }

    @Override
    public synchronized void saveProfile(ProfileDraft draft) {
        prefs.edit().putString(KEY_ME, gson.toJson(draft)).apply();
    }

    @Override
    public synchronized List<Photo> myPhotos() {
        Photo[] saved = gson.fromJson(prefs.getString(KEY_PHOTOS, "[]"), Photo[].class);
        List<Photo> out = new ArrayList<>();
        for (Photo p : saved) if (p.primary) out.add(p);
        for (Photo p : saved) if (!p.primary) out.add(p);
        return out;
    }

    @Override
    public synchronized void addPhoto(byte[] jpeg) throws RepoException {
        List<Photo> photos = myPhotos();
        if (photos.size() >= MAX_PHOTOS) {
            throw new RepoException(RepoException.Reason.NOT_ALLOWED, "Photo limit reached");
        }
        File file = new File(context.getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(jpeg);
        } catch (IOException e) {
            throw new RepoException(RepoException.Reason.SERVER, "Could not save photo", e);
        }
        photos.add(new Photo(file.getName(), Uri.fromFile(file).toString(), photos.isEmpty()));
        savePhotos(photos);
    }

    @Override
    public synchronized void deletePhoto(String photoId) {
        List<Photo> kept = new ArrayList<>();
        boolean removedMain = false;
        for (Photo p : myPhotos()) {
            if (p.id.equals(photoId)) {
                removedMain = p.primary;
                //noinspection ResultOfMethodCallIgnored
                new File(context.getFilesDir(), p.id).delete();
            } else {
                kept.add(p);
            }
        }
        if (removedMain && !kept.isEmpty()) {
            Photo first = kept.get(0);
            kept.set(0, new Photo(first.id, first.url, true));
        }
        savePhotos(kept);
    }

    @Override
    public synchronized void setMainPhoto(String photoId) {
        List<Photo> out = new ArrayList<>();
        for (Photo p : myPhotos()) out.add(new Photo(p.id, p.url, p.id.equals(photoId)));
        savePhotos(out);
    }

    private void savePhotos(List<Photo> photos) {
        prefs.edit().putString(KEY_PHOTOS, gson.toJson(photos)).apply();
    }

    @Override
    public synchronized void verifySubscription(String productId, String purchaseToken) {
        ProfileDraft me = me();
        me.subscribed = true;
        me.subscribedUntil = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000;
        saveProfile(me);
    }

    @Override
    public void signOut() {
        prefs.edit().clear().apply();
        chats.clear();
    }

    @Override
    public void deleteAccount() {
        File[] files = context.getFilesDir().listFiles((dir, n) -> n.startsWith("avatar_"));
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
        signOut();
    }

    // ---- Discovery ----

    @Override
    public List<Profile> discover() {
        ProfileDraft me = me();
        Set<String> hidden = getSet(KEY_DECLINED);
        hidden.addAll(getSet(KEY_BLOCKED));
        List<Profile> out = new ArrayList<>();
        for (Profile p : profiles) {
            if (p.female == me.female || hidden.contains(p.id)) continue;
            if (p.age < me.prefAgeMin || p.age > me.prefAgeMax) continue;
            out.add(p);
        }
        return out;
    }

    @Override
    public List<Profile> cachedDiscover() {
        return discover();
    }

    @Override
    public Profile profile(String id) throws RepoException {
        for (Profile p : profiles) if (p.id.equals(id)) return p;
        throw new RepoException(RepoException.Reason.NOT_FOUND, "No profile " + id);
    }

    @Override
    public Relationship relationship(String id) {
        Relationship r = new Relationship();
        r.shortlisted = getSet(KEY_SHORTLIST).contains(id);
        r.interestSent = getSet(KEY_SENT).contains(id);
        r.connected = getSet(KEY_ACCEPTED).contains(id);
        r.blocked = getSet(KEY_BLOCKED).contains(id);
        for (Profile p : receivedInterests()) if (p.id.equals(id)) r.interestReceived = true;
        return r;
    }

    @Override
    public String contact(String id) {
        if (!getSet(KEY_ACCEPTED).contains(id)) return null;
        return "member." + id.toLowerCase() + "@example.com";
    }

    // ---- Actions ----

    @Override
    public synchronized boolean toggleShortlist(String id) {
        Set<String> set = getSet(KEY_SHORTLIST);
        boolean added = set.add(id);
        if (!added) set.remove(id);
        putSet(KEY_SHORTLIST, set);
        return added;
    }

    @Override
    public synchronized void sendInterest(String id) throws RepoException {
        Set<String> sent = getSet(KEY_SENT);
        if (sent.contains(id)) return;
        long dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
        List<Long> times = new ArrayList<>();
        for (String t : getSet(KEY_SENT_TIMES)) {
            long time = Long.parseLong(t.substring(0, t.indexOf(':')));
            if (time > dayAgo) times.add(time);
        }
        if (times.size() >= DAILY_INTEREST_LIMIT) {
            throw new RepoException(RepoException.Reason.DAILY_LIMIT, "Daily interest limit reached");
        }
        sent.add(id);
        putSet(KEY_SENT, sent);
        Set<String> stamps = getSet(KEY_SENT_TIMES);
        stamps.add(System.currentTimeMillis() + ":" + id);
        putSet(KEY_SENT_TIMES, stamps);
    }

    @Override
    public synchronized void respondToInterest(String senderId, boolean accept) {
        String key = accept ? KEY_ACCEPTED : KEY_DECLINED;
        Set<String> set = getSet(key);
        set.add(senderId);
        putSet(key, set);
    }

    @Override
    public synchronized void block(String id) {
        Set<String> set = getSet(KEY_BLOCKED);
        set.add(id);
        putSet(KEY_BLOCKED, set);
        Set<String> accepted = getSet(KEY_ACCEPTED);
        accepted.remove(id);
        putSet(KEY_ACCEPTED, accepted);
    }

    @Override
    public void report(String id, String reason, String details) {
        // Demo mode: reports aren't sent anywhere.
    }

    // ---- Lists ----

    @Override public List<Profile> shortlisted() { return visible(getSet(KEY_SHORTLIST)); }
    @Override public List<Profile> sentInterests() { return visible(getSet(KEY_SENT)); }
    @Override public Set<String> connectedIds() { return getSet(KEY_ACCEPTED); }

    /** A few sample members have already expressed interest in the user. */
    @Override
    public List<Profile> receivedInterests() {
        Set<String> answered = getSet(KEY_ACCEPTED);
        answered.addAll(getSet(KEY_DECLINED));
        List<Profile> out = new ArrayList<>();
        List<Profile> pool = discover();
        for (int i = 1; i < pool.size() && out.size() < 3; i += 2) {
            Profile p = pool.get(i);
            if (!answered.contains(p.id)) out.add(p);
        }
        return out;
    }

    @Override
    public List<Conversation> conversations() {
        List<Conversation> out = new ArrayList<>();
        for (Profile p : visible(getSet(KEY_ACCEPTED))) {
            List<Message> list = messages(p.id);
            out.add(new Conversation(p, list.get(list.size() - 1)));
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastTime(), a.lastTime()));
        return out;
    }

    // ---- Chat ----

    @Override
    public synchronized List<Message> messages(String partnerId) {
        List<Message> list = chats.get(partnerId);
        if (list == null) {
            list = new ArrayList<>();
            list.add(new Message(nextMessageId++, context.getString(R.string.chat_greeting), false,
                    System.currentTimeMillis() - 60 * 60 * 1000L));
            chats.put(partnerId, list);
        }
        return new ArrayList<>(list);
    }

    @Override
    public synchronized Message sendMessage(String partnerId, String text) {
        messages(partnerId);
        Message mine = new Message(nextMessageId++, text, true, System.currentTimeMillis());
        chats.get(partnerId).add(mine);
        String[] replies = context.getResources().getStringArray(R.array.chat_replies);
        String reply = replies[replyIndex++ % replies.length];
        main.postDelayed(() -> {
            Message m;
            synchronized (this) {
                m = new Message(nextMessageId++, reply, false, System.currentTimeMillis());
                chats.get(partnerId).add(m);
            }
            MessageListener l = listeners.get(partnerId);
            if (l != null) l.onMessage(m);
        }, 1500);
        return mine;
    }

    @Override
    public Closeable subscribe(String partnerId, MessageListener listener) {
        listeners.put(partnerId, listener);
        return () -> listeners.remove(partnerId, listener);
    }

    // ---- Helpers ----

    private List<Profile> visible(Set<String> ids) {
        Set<String> blocked = getSet(KEY_BLOCKED);
        List<Profile> out = new ArrayList<>();
        for (Profile p : profiles) if (ids.contains(p.id) && !blocked.contains(p.id)) out.add(p);
        return out;
    }

    private Set<String> getSet(String key) {
        return new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
    }

    private void putSet(String key, Set<String> set) {
        prefs.edit().putStringSet(key, set).apply();
    }


    private void seedProfiles() {
        int maroon = 0xFF8C1C2B, slate = 0xFF2F4858, ochre = 0xFFB8862F, forest = 0xFF3F6E5A,
                plum = 0xFF5B4B8A, rust = 0xFFB5543C, navy = 0xFF1F3A5F, ink = 0xFF2A2A2A;

        // Shared values, so each translation is written once.
        Text ahmedabad = t("Ahmedabad", "અમદાવાદ"), surat = t("Surat", "સુરત"),
                vadodara = t("Vadodara", "વડોદરા"), mumbai = t("Mumbai", "મુંબઈ"),
                pune = t("Pune", "પુણે"), rajkot = t("Rajkot", "રાજકોટ"),
                london = t("London", "લંડન"), bengaluru = t("Bengaluru", "બેંગલુરુ"),
                toronto = t("Toronto", "ટોરોન્ટો");
        Text gujarat = t("Gujarat", "ગુજરાત"), maharashtra = t("Maharashtra", "મહારાષ્ટ્ર"),
                karnataka = t("Karnataka", "કર્ણાટક"), uk = t("United Kingdom", "યુનાઇટેડ કિંગડમ"),
                canada = t("Canada", "કેનેડા");
        // Atak (surname) is what members type as their community.
        Text darji = t("Darji", "દરજી"), parmar = t("Parmar", "પરમાર"), solanki = t("Solanki", "સોલંકી"),
                chauhan = t("Chauhan", "ચૌહાણ"), rathod = t("Rathod", "રાઠોડ"), tank = t("Tank", "ટાંક"),
                gohil = t("Gohil", "ગોહિલ");
        Text joint = t("Joint family", "સંયુક્ત કુટુંબ"),
                oneBrother = t("Nuclear · 1 brother", "વિભક્ત કુટુંબ · 1 ભાઈ"),
                oneSister = t("Nuclear · 1 sister", "વિભક્ત કુટુંબ · 1 બહેન"),
                twoSiblings = t("Nuclear · 2 siblings", "વિભક્ત કુટુંબ · 2 ભાઈ-બહેન"),
                onlyChild = t("Nuclear · only child", "વિભક્ત કુટુંબ · એકમાત્ર સંતાન");

        profiles.add(new Profile("DP10231", t("Riya Darji", "રિયા દરજી"), true, 26, "5'4\"",
                ahmedabad, gujarat, darji, t("MBA, IIM Indore", "MBA, IIM ઇન્દોર"),
                t("Product Manager", "પ્રોડક્ટ મેનેજર"), lpa(18, 24), oneBrother,
                t("Calm, curious, and close to my family. I enjoy classical music, weekend treks and "
                                + "cooking for friends. Looking for a kind partner who values both ambition and home.",
                        "શાંત, જિજ્ઞાસુ અને પરિવારની નજીક. મને શાસ્ત્રીય સંગીત, સપ્તાહાંતના ટ્રેક અને "
                                + "મિત્રો માટે રસોઈ કરવી ગમે છે. એવા દયાળુ જીવનસાથીની શોધ છે જે મહત્ત્વાકાંક્ષા અને ઘર બંનેને મહત્ત્વ આપે."),
                true, true, true, maroon));
        profiles.add(new Profile("DP10232", t("Pooja Parmar", "પૂજા પરમાર"), true, 25, "5'3\"",
                surat, gujarat, parmar, t("B.Des, NID", "B.Des, NID"),
                t("Fashion Designer", "ફેશન ડિઝાઇનર"), lpa(10, 14), joint,
                t("I design ethnic wear and dream of my own label one day. Love Garba, old Bollywood "
                                + "songs and long chai conversations.",
                        "હું પરંપરાગત વસ્ત્રો ડિઝાઇન કરું છું અને એક દિવસ મારું પોતાનું લેબલ શરૂ કરવાનું "
                                + "સપનું છે. ગરબા, જૂનાં બોલિવૂડ ગીતો અને ચા સાથે લાંબી વાતો ગમે છે."),
                true, false, false, ochre));
        profiles.add(new Profile("DP10233", t("Kavya Solanki", "કાવ્યા સોલંકી"), true, 27, "5'5\"",
                vadodara, gujarat, solanki, t("MBBS, MD", "MBBS, MD"),
                t("Doctor (Pediatrics)", "ડૉક્ટર (બાળરોગ)"), lpa(20, 30), oneSister,
                t("Pediatrician who laughs a lot. Weekends are for reading, badminton and family dinners.",
                        "ખૂબ હસતી બાળરોગ નિષ્ણાત. સપ્તાહાંત વાંચન, બેડમિન્ટન અને પરિવાર સાથે ભોજન માટે હોય છે."),
                true, true, false, forest));
        profiles.add(new Profile("DP10234", t("Nisha Chauhan", "નિશા ચૌહાણ"), true, 24, "5'2\"",
                mumbai, maharashtra, chauhan, t("B.Com, CA", "B.Com, CA"),
                t("Chartered Accountant", "ચાર્ટર્ડ એકાઉન્ટન્ટ"), lpa(12, 16), twoSiblings,
                t("Numbers by day, dance by evening. Grounded, honest and family oriented.",
                        "દિવસે આંકડા, સાંજે નૃત્ય. સરળ સ્વભાવ, પ્રામાણિક અને પરિવારલક્ષી."),
                false, false, true, plum));
        profiles.add(new Profile("DP10235", t("Sneha Rathod", "સ્નેહા રાઠોડ"), true, 28, "5'6\"",
                pune, maharashtra, rathod, t("M.Tech, COEP", "M.Tech, COEP"),
                t("Software Engineer", "સોફ્ટવેર એન્જિનિયર"), lpa(25, 32), oneBrother,
                t("Backend engineer, amateur photographer, and a big believer in equal partnership.",
                        "બેકએન્ડ એન્જિનિયર, શોખીન ફોટોગ્રાફર, અને સમાન ભાગીદારીમાં દૃઢ વિશ્વાસ રાખું છું."),
                true, false, false, slate));
        profiles.add(new Profile("DP10236", t("Megha Tank", "મેઘા ટાંક"), true, 26, "5'4\"",
                rajkot, gujarat, tank, t("M.A. English", "M.A. અંગ્રેજી"),
                t("Lecturer", "વ્યાખ્યાતા"), lpa(6, 8), joint,
                t("Teacher at heart. Love poetry, gardening and travelling to hill stations.",
                        "હૃદયથી શિક્ષિકા. કવિતા, બાગકામ અને હિલ સ્ટેશનોની મુસાફરી ગમે છે."),
                false, false, true, rust));
        profiles.add(new Profile("DP10237", t("Aanya Gohil", "આન્યા ગોહિલ"), true, 27, "5'5\"",
                london, uk, gohil, t("MSc, LSE", "MSc, LSE"),
                t("Data Analyst", "ડેટા એનાલિસ્ટ"), t("£55k", "£55 હજાર/વર્ષ"), onlyChild,
                t("Born in Ahmedabad, living in London. Rooted in culture, open in outlook.",
                        "અમદાવાદમાં જન્મ, લંડનમાં રહું છું. સંસ્કૃતિમાં મૂળ, વિચારોમાં ખુલ્લી."),
                true, true, false, navy));

        profiles.add(new Profile("DP20411", t("Arjun Darji", "અર્જુન દરજી"), false, 29, "5'10\"",
                ahmedabad, gujarat, darji, t("B.Tech, NIT Surat", "B.Tech, NIT સુરત"),
                t("Software Architect", "સોફ્ટવેર આર્કિટેક્ટ"), lpa(30, 40), oneSister,
                t("Builder of software and furniture. I value honesty, humour and a shared love for food.",
                        "સોફ્ટવેર અને ફર્નિચર બંને બનાવું છું. પ્રામાણિકતા, રમૂજ અને ભોજનના સહિયારા પ્રેમને મહત્ત્વ આપું છું."),
                true, true, true, navy));
        profiles.add(new Profile("DP20412", t("Karan Parmar", "કરણ પરમાર"), false, 30, "5'11\"",
                surat, gujarat, parmar, t("MBA, Finance", "MBA, ફાઇનાન્સ"),
                t("Business Owner (Textiles)", "વ્યવસાયી (કાપડ)"), lpa(25, 35), joint,
                t("Running our family textile business into its third generation. Cricket and road trips.",
                        "અમારો પારિવારિક કાપડનો વ્યવસાય ત્રીજી પેઢીમાં સંભાળું છું. ક્રિકેટ અને રોડ ટ્રિપ્સનો શોખ."),
                true, false, false, maroon));
        profiles.add(new Profile("DP20413", t("Rohan Solanki", "રોહન સોલંકી"), false, 28, "5'9\"",
                bengaluru, karnataka, solanki, t("M.S., IIT Bombay", "M.S., IIT બોમ્બે"),
                t("Data Scientist", "ડેટા સાયન્ટિસ્ટ"), lpa(35, 45), oneBrother,
                t("Curious mind, avid runner, and a decent cook. Looking for a best friend for life.",
                        "જિજ્ઞાસુ મન, ઉત્સાહી દોડવીર અને સારો રસોઈયો. જીવનભરના શ્રેષ્ઠ મિત્રની શોધમાં છું."),
                true, true, false, forest));
        profiles.add(new Profile("DP20414", t("Vivek Chauhan", "વિવેક ચૌહાણ"), false, 31, "6'0\"",
                mumbai, maharashtra, chauhan, t("CA", "CA"),
                t("Finance Manager", "ફાઇનાન્સ મેનેજર"), lpa(28, 34), twoSiblings,
                t("Calm and dependable. Love Rajasthani folk music and mountains.",
                        "શાંત અને ભરોસાપાત્ર. રાજસ્થાની લોકસંગીત અને પર્વતો ગમે છે."),
                false, false, true, slate));
        profiles.add(new Profile("DP20415", t("Harsh Rathod", "હર્ષ રાઠોડ"), false, 29, "5'10\"",
                pune, maharashtra, rathod, t("B.Arch", "B.Arch"),
                t("Architect", "આર્કિટેક્ટ"), lpa(14, 18), onlyChild,
                t("I design homes and sketch on weekends. Seeking someone warm and adventurous.",
                        "હું ઘરો ડિઝાઇન કરું છું અને સપ્તાહાંતે સ્કેચ બનાવું છું. હૂંફાળા અને સાહસિક જીવનસાથીની શોધ છે."),
                true, false, false, ochre));
        profiles.add(new Profile("DP20416", t("Dhruv Tank", "ધ્રુવ ટાંક"), false, 27, "5'8\"",
                vadodara, gujarat, tank, t("MBBS", "MBBS"),
                t("Doctor (Ortho)", "ડૉક્ટર (ઓર્થોપેડિક)"), lpa(18, 24), joint,
                t("Resident doctor, tabla player, and a family man in the making.",
                        "રેસિડેન્ટ ડૉક્ટર, તબલાવાદક અને ભવિષ્યનો પરિવારપ્રેમી."),
                false, false, true, plum));
        profiles.add(new Profile("DP20417", t("Nikhil Gohil", "નિખિલ ગોહિલ"), false, 32, "5'11\"",
                toronto, canada, gohil, t("M.Eng, Waterloo", "M.Eng, વોટરલૂ"),
                t("Engineering Manager", "એન્જિનિયરિંગ મેનેજર"), t("CA$160k", "CA$160 હજાર/વર્ષ"),
                oneSister,
                t("Settled in Toronto, visit India every year. Value family, fitness and kindness.",
                        "ટોરોન્ટોમાં સ્થાયી, દર વર્ષે ભારત આવું છું. પરિવાર, તંદુરસ્તી અને દયાને મહત્ત્વ આપું છું."),
                true, true, false, ink));
    }

    private static Text t(String en, String gu) {
        return Text.of(en, gu);
    }

    /** Annual income range in lakhs. */
    private static Text lpa(int from, int to) {
        return Text.of("₹" + from + " – " + to + " LPA", "₹" + from + " – " + to + " લાખ/વર્ષ");
    }
}
