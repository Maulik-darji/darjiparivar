package com.example.darjiparivar.data;

/** A failure from the data layer, with a reason the UI can turn into a message. */
public class RepoException extends Exception {

    public enum Reason { NETWORK, AUTH, DAILY_LIMIT, NOT_ALLOWED, NOT_FOUND, SERVER }

    public final Reason reason;

    public RepoException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RepoException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
