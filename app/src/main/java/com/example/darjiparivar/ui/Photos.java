package com.example.darjiparivar.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.example.darjiparivar.data.RepoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Turns a picked image into an upload-ready square JPEG (max 1080 px). */
final class Photos {
    private Photos() {}

    static final int SIZE = 1080;

    static Bitmap square(Context c, Uri uri) throws RepoException {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap full;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                full = BitmapFactory.decodeStream(in, null, opts);
            }
            if (full == null) throw new IOException("Unreadable image");
            int size = Math.min(full.getWidth(), full.getHeight());
            Bitmap square = Bitmap.createBitmap(full, (full.getWidth() - size) / 2,
                    (full.getHeight() - size) / 2, size, size);
            return size > SIZE ? Bitmap.createScaledBitmap(square, SIZE, SIZE, true) : square;
        } catch (IOException | SecurityException e) {
            throw new RepoException(RepoException.Reason.SERVER, "Could not read photo", e);
        }
    }

    static byte[] jpeg(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        return out.toByteArray();
    }
}
