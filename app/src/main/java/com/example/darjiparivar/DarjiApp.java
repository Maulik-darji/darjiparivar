package com.example.darjiparivar;

import android.app.Application;

import io.sentry.android.core.SentryAndroid;

public class DarjiApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Crash reporting only in release builds that have a DSN in local.properties.
        if (!BuildConfig.DEBUG && !BuildConfig.SENTRY_DSN.isEmpty()) {
            SentryAndroid.init(this, options -> {
                options.setDsn(BuildConfig.SENTRY_DSN);
                options.setSendDefaultPii(false);
                options.setAttachScreenshot(false);
            });
        }
    }
}
