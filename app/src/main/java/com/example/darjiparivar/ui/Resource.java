package com.example.darjiparivar.ui;

import com.example.darjiparivar.data.RepoException;

/** A value being loaded: loading, loaded, or failed (keeping the previous data if there was any). */
final class Resource<T> {
    enum Status { LOADING, SUCCESS, ERROR }

    final Status status;
    final T data;
    final RepoException error;
    /** Data came from the offline cache because the network failed. */
    final boolean stale;

    private Resource(Status status, T data, RepoException error, boolean stale) {
        this.status = status;
        this.data = data;
        this.error = error;
        this.stale = stale;
    }

    static <T> Resource<T> loading(T previous) { return new Resource<>(Status.LOADING, previous, null, false); }
    static <T> Resource<T> success(T data) { return new Resource<>(Status.SUCCESS, data, null, false); }
    static <T> Resource<T> stale(T data, RepoException error) { return new Resource<>(Status.SUCCESS, data, error, true); }
    static <T> Resource<T> error(RepoException error, T previous) { return new Resource<>(Status.ERROR, previous, error, false); }
}
