package com.example.darjiparivar.data.supabase;

import android.content.SharedPreferences;

import com.example.darjiparivar.data.RepoException;
import com.example.darjiparivar.data.RepoException.Reason;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Minimal Supabase client over plain HTTP: Auth (Google ID token), PostgREST, RPC and Storage. Keeps the
 * session tokens in SharedPreferences and refreshes the access token when it is about to expire.
 */
final class SupabaseClient {

    static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    static final MediaType JPEG = MediaType.get("image/jpeg");

    final String url;
    final String anonKey;
    final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build();
    final Gson gson = new Gson();

    private final SharedPreferences prefs;

    SupabaseClient(String url, String anonKey, SharedPreferences prefs) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.anonKey = anonKey;
        this.prefs = prefs;
    }

    // ---- Session ----

    String userId() { return prefs.getString("sb_user", null); }

    boolean hasSession() { return prefs.getString("sb_refresh", null) != null; }

    void clearSession() {
        prefs.edit().remove("sb_access").remove("sb_refresh").remove("sb_expires").remove("sb_user").apply();
    }

    synchronized String accessToken() throws RepoException {
        String token = prefs.getString("sb_access", null);
        if (token == null) return null;
        if (System.currentTimeMillis() > prefs.getLong("sb_expires", 0) - 60_000) {
            refresh();
            token = prefs.getString("sb_access", null);
        }
        return token;
    }

    private void refresh() throws RepoException {
        String refreshToken = prefs.getString("sb_refresh", null);
        if (refreshToken == null) throw new RepoException(Reason.AUTH, "Not signed in");
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);
        try {
            storeSession(raw("POST", "/auth/v1/token?grant_type=refresh_token", body, null, false));
        } catch (RepoException e) {
            if (e.reason == Reason.AUTH || e.reason == Reason.NOT_ALLOWED) clearSession();
            throw e;
        }
    }

    void storeSession(JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        long expiresIn = o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600;
        prefs.edit()
                .putString("sb_access", o.get("access_token").getAsString())
                .putString("sb_refresh", o.get("refresh_token").getAsString())
                .putLong("sb_expires", System.currentTimeMillis() + expiresIn * 1000)
                .putString("sb_user", o.getAsJsonObject("user").get("id").getAsString())
                .apply();
    }

    // ---- Requests ----

    /** An authenticated JSON request. Returns JsonNull for empty bodies. */
    JsonElement call(String method, String path, Object body, Map<String, String> headers)
            throws RepoException {
        return raw(method, path, body, headers, true);
    }

    JsonElement rpc(String function, Object args) throws RepoException {
        return call("POST", "/rest/v1/rpc/" + function, args == null ? new JsonObject() : args, null);
    }

    JsonElement upload(String path, byte[] bytes) throws RepoException {
        Request.Builder rb = new Request.Builder().url(url + path)
                .header("x-upsert", "true")
                .post(RequestBody.create(bytes, JPEG));
        return execute(authorize(rb, true));
    }

    private JsonElement raw(String method, String path, Object body, Map<String, String> headers,
                            boolean withUser) throws RepoException {
        RequestBody rb = null;
        if (body != null) rb = RequestBody.create(body instanceof String ? (String) body : gson.toJson(body), JSON);
        else if (!method.equals("GET") && !method.equals("DELETE")) rb = RequestBody.create("", JSON);
        Request.Builder b = new Request.Builder().url(url + path).method(method, rb);
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) b.header(h.getKey(), h.getValue());
        return execute(authorize(b, withUser));
    }

    private Request.Builder authorize(Request.Builder b, boolean withUser) throws RepoException {
        String token = withUser ? accessToken() : null;
        b.header("apikey", anonKey);
        // New "sb_publishable_…" keys aren't JWTs, so they only go in the apikey header. The
        // Authorization header carries the user's session token, or the legacy anon JWT.
        if (token != null) b.header("Authorization", "Bearer " + token);
        else if (anonKey.startsWith("eyJ")) b.header("Authorization", "Bearer " + anonKey);
        return b;
    }

    private JsonElement execute(Request.Builder b) throws RepoException {
        try (Response response = http.newCall(b.build()).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful()) {
                return text.isEmpty() ? JsonNull.INSTANCE : JsonParser.parseString(text);
            }
            throw error(response.code(), text);
        } catch (IOException e) {
            throw new RepoException(Reason.NETWORK, "Network error", e);
        }
    }

    private static RepoException error(int status, String body) {
        String message = body;
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            for (String key : new String[]{"message", "msg", "error_description", "error"}) {
                if (o.has(key) && o.get(key).isJsonPrimitive()) { message = o.get(key).getAsString(); break; }
            }
        } catch (RuntimeException ignored) {
            // Not JSON; keep the raw body.
        }
        String lower = message == null ? "" : message.toLowerCase();
        Reason reason;
        if (lower.contains("daily_limit")) reason = Reason.DAILY_LIMIT;
        else if (status == 401) reason = Reason.AUTH;
        else if (status == 403 || lower.contains("not_allowed")) reason = Reason.NOT_ALLOWED;
        else if (status == 404) reason = Reason.NOT_FOUND;
        else reason = Reason.SERVER;
        return new RepoException(reason, "HTTP " + status + ": " + message);
    }
}
