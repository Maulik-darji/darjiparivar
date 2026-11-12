package com.example.darjiparivar.data.supabase;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.util.function.Consumer;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Listens for new rows in one conversation over Supabase Realtime (Phoenix channels over a
 * WebSocket). Reconnects with backoff if the connection drops.
 */
final class RealtimeChannel extends WebSocketListener implements Closeable {

    private final SupabaseClient client;
    private final String conversationId;
    private final Consumer<JsonObject> onInsert;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebSocket socket;
    private int ref;
    private long backoffMs = 1000;
    private volatile boolean closed;

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (closed || socket == null) return;
            send("phoenix", "heartbeat", new JsonObject());
            main.postDelayed(this, 25_000);
        }
    };

    RealtimeChannel(SupabaseClient client, String conversationId, Consumer<JsonObject> onInsert) {
        this.client = client;
        this.conversationId = conversationId;
        this.onInsert = onInsert;
        connect();
    }

    private void connect() {
        if (closed) return;
        String ws = client.url.replaceFirst("^http", "ws")
                + "/realtime/v1/websocket?apikey=" + client.anonKey + "&vsn=1.0.0";
        socket = client.http.newWebSocket(new Request.Builder().url(ws).build(), this);
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        backoffMs = 1000;
        String token;
        try {
            token = client.accessToken();
        } catch (Exception e) {
            token = null;
        }
        JsonObject change = new JsonObject();
        change.addProperty("event", "INSERT");
        change.addProperty("schema", "public");
        change.addProperty("table", "messages");
        change.addProperty("filter", "conversation_id=eq." + conversationId);
        JsonArray changes = new JsonArray();
        changes.add(change);
        JsonObject config = new JsonObject();
        config.add("postgres_changes", changes);
        JsonObject payload = new JsonObject();
        payload.add("config", config);
        if (token != null) payload.addProperty("access_token", token);
        send(topic(), "phx_join", payload);
        main.removeCallbacks(heartbeat);
        main.postDelayed(heartbeat, 25_000);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            if (!"postgres_changes".equals(msg.get("event").getAsString())) return;
            JsonObject record = msg.getAsJsonObject("payload").getAsJsonObject("data").getAsJsonObject("record");
            main.post(() -> { if (!closed) onInsert.accept(record); });
        } catch (RuntimeException ignored) {
            // Replies and system messages we don't need.
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        reconnect();
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        reconnect();
    }

    private void reconnect() {
        main.removeCallbacks(heartbeat);
        if (closed) return;
        main.postDelayed(this::connect, backoffMs);
        backoffMs = Math.min(backoffMs * 2, 30_000);
    }

    private String topic() {
        return "realtime:chat-" + conversationId;
    }

    private void send(String topic, String event, JsonObject payload) {
        JsonObject msg = new JsonObject();
        msg.addProperty("topic", topic);
        msg.addProperty("event", event);
        msg.add("payload", payload);
        msg.addProperty("ref", String.valueOf(++ref));
        if (socket != null) socket.send(msg.toString());
    }

    @Override
    public void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        if (socket != null) socket.close(1000, null);
    }
}
