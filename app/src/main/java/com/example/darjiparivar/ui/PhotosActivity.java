package com.example.darjiparivar.ui;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.darjiparivar.R;
import com.example.darjiparivar.data.Photo;
import com.example.darjiparivar.data.Repository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Add, remove and choose the main one of up to six profile photos. */
public class PhotosActivity extends AppCompatActivity {

    public static class PhotosViewModel extends BaseViewModel {
        final MutableLiveData<Resource<List<Photo>>> photos = new MutableLiveData<>();

        public PhotosViewModel(@NonNull Application app) { super(app); }

        void load() {
            load(photos, repo::myPhotos);
        }

        /** Crops and uploads each picked image in turn, then reloads. */
        void upload(List<Uri> uris, Done<Object> done) {
            act(() -> {
                for (Uri uri : uris) repo.addPhoto(Photos.jpeg(Photos.square(getApplication(), uri)));
                return null;
            }, (r, error) -> {
                load();
                done.done(r, error);
            });
        }
    }

    private PhotosViewModel vm;
    private View progress;
    private final Adapter adapter = new Adapter();
    private ActivityResultLauncher<PickVisualMediaRequest> pickOne;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMany;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_photos);
        Ui.applyInsets(findViewById(R.id.root));
        vm = new ViewModelProvider(this).get(PhotosViewModel.class);
        progress = findViewById(R.id.progress);

        pickOne = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) upload(java.util.Collections.singletonList(uri));
        });
        pickMany = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(Repository.MAX_PHOTOS), uris -> {
                    if (!uris.isEmpty()) upload(uris);
                });

        RecyclerView grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setHasFixedSize(true);
        grid.setAdapter(adapter);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        vm.photos.observe(this, res -> {
            progress.setVisibility(res.status == Resource.Status.LOADING ? View.VISIBLE : View.INVISIBLE);
            if (res.data != null) adapter.submit(res.data);
            if (res.status == Resource.Status.ERROR) Ui.snack(grid, Ui.error(this, res.error), null);
        });
        if (vm.photos.getValue() == null) vm.load();
    }

    private void pick() {
        int free = Repository.MAX_PHOTOS - adapter.photos.size();
        if (free <= 0) return;
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build();
        // The multi-picker needs a limit of at least 2.
        if (free == 1) pickOne.launch(request); else pickMany.launch(request);
    }

    private void upload(List<Uri> uris) {
        int free = Repository.MAX_PHOTOS - adapter.photos.size();
        List<Uri> batch = new ArrayList<>(uris.subList(0, Math.min(free, uris.size())));
        progress.setVisibility(View.VISIBLE);
        vm.upload(batch, (r, error) -> {
            View root = findViewById(R.id.root);
            if (error != null) {
                Haptics.reject(root);
                Ui.snack(root, Ui.error(this, error), null);
            } else {
                Haptics.confirm(root);
                Ui.snack(root, getResources().getQuantityString(R.plurals.photos_added, batch.size(), batch.size()), null);
            }
        });
    }

    private void options(Photo photo, View anchor) {
        List<CharSequence> items = new ArrayList<>();
        if (!photo.primary) items.add(getString(R.string.make_main_photo));
        items.add(getString(R.string.delete_photo));
        new MaterialAlertDialogBuilder(this)
                .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                    boolean delete = which == items.size() - 1;
                    progress.setVisibility(View.VISIBLE);
                    vm.act(() -> {
                        if (delete) vm.repo.deletePhoto(photo.id);
                        else vm.repo.setMainPhoto(photo.id);
                        return null;
                    }, (r, error) -> {
                        if (error != null) Ui.snack(anchor, Ui.error(this, error), null);
                        else Haptics.confirm(anchor);
                        vm.load();
                    });
                })
                .show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        List<Photo> photos = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged")
        void submit(List<Photo> list) {
            photos = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_slot, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            boolean filled = position < photos.size();
            h.add.setVisibility(filled ? View.GONE : View.VISIBLE);
            h.image.setVisibility(filled ? View.VISIBLE : View.GONE);
            h.mainTag.setVisibility(filled && photos.get(position).primary ? View.VISIBLE : View.GONE);
            if (filled) {
                Photo p = photos.get(position);
                Ui.bindPhoto(h.image, p.url, 16);
                h.itemView.setOnClickListener(v -> {
                    Haptics.tap(v);
                    options(p, v);
                });
            } else {
                Glide.with(h.image).clear(h.image);
                h.itemView.setOnClickListener(v -> {
                    Haptics.tap(v);
                    pick();
                });
            }
        }

        @Override
        public int getItemCount() {
            return Repository.MAX_PHOTOS;
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image, add;
            final View mainTag;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.image);
                add = v.findViewById(R.id.add);
                mainTag = v.findViewById(R.id.main_tag);
            }
        }
    }
}
