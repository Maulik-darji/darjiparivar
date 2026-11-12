package com.example.darjiparivar.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.darjiparivar.data.RepoException;
import com.example.darjiparivar.data.Repository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs repository calls off the main thread and reports back on it. */
abstract class BaseViewModel extends AndroidViewModel {

    interface Task<T> {
        T run() throws RepoException;
    }

    interface Done<T> {
        void done(T result, RepoException error);
    }

    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    protected final Repository repo;

    BaseViewModel(@NonNull Application app) {
        super(app);
        repo = Repository.get(app);
    }

    /** Loads into a LiveData, keeping the previous value visible while loading or after an error. */
    protected <T> void load(MutableLiveData<Resource<T>> live, Task<T> task) {
        Resource<T> current = live.getValue();
        T previous = current == null ? null : current.data;
        live.setValue(Resource.loading(previous));
        run(task, (result, error) -> live.setValue(error == null ? Resource.success(result) : Resource.error(error, previous)));
    }

    /** Runs a task in the background and calls {@code done} on the main thread. */
    <T> void run(Task<T> task, Done<T> done) {
        IO.execute(() -> {
            try {
                T result = task.run();
                MAIN.post(() -> done.done(result, null));
            } catch (RepoException e) {
                MAIN.post(() -> done.done(null, e));
            } catch (RuntimeException e) {
                RepoException wrapped = new RepoException(RepoException.Reason.SERVER, String.valueOf(e.getMessage()), e);
                MAIN.post(() -> done.done(null, wrapped));
            }
        });
    }

    /** Runs an action whose result doesn't matter. */
    void act(Task<?> task, Done<Object> done) {
        run(() -> task.run(), (r, e) -> done.done(r, e));
    }
}
