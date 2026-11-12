package com.example.darjiparivar.data;

/** A chat with another member, with its most recent message (null if none yet). */
public class Conversation {
    public final Profile partner;
    public final Message last;

    public Conversation(Profile partner, Message last) {
        this.partner = partner;
        this.last = last;
    }

    public long lastTime() {
        return last == null ? 0 : last.time;
    }
}
