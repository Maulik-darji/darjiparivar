package com.example.darjiparivar.data;

/** One of the signed-in user's own photos. */
public class Photo {
    public final String id;
    public final String url;
    public final boolean primary;

    public Photo(String id, String url, boolean primary) {
        this.id = id;
        this.url = url;
        this.primary = primary;
    }
}
