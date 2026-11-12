package com.example.darjiparivar.data;

public class Message {
    public final long id;
    public final String text;
    public final boolean mine;
    public final long time;

    public Message(long id, String text, boolean mine, long time) {
        this.id = id;
        this.text = text;
        this.mine = mine;
        this.time = time;
    }
}
