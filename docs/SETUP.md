# Darji Parivar — backend and release setup

The app runs in **demo mode** (14 sample profiles, any 6-digit OTP works) until you add Supabase
keys. Everything below is a one-time setup.

## 1. Supabase

1. Create a project at <https://supabase.com> (region: Mumbai, `ap-south-1`, for Indian users).
2. Apply the schema in `supabase/migrations/20261112000000_init.sql`:
   - with the CLI: `supabase link --project-ref <ref>` then `supabase db push`, or
   - paste the file into **SQL Editor → New query → Run**.
   This creates every table with Row Level Security, the `send_interest` / `respond_interest` /
   `delete_account` functions, Realtime on `messages`, and the private `photos` storage bucket.
3. **Authentication → Providers**
   - **Phone**: enable it and connect an SMS provider (Twilio, MessageBird or Vonage; for India,
     Twilio Verify or MSG91 via a Supabase SMS hook). SMS OTP doesn't work without this.
   - **Email**: enabled by default. Under **Email Templates → Magic Link**, make sure the template
     includes `{{ .Token }}` so users get a 6-digit code instead of only a link.
   - Set OTP expiry to 5–10 minutes and turn on rate limits under **Auth → Rate Limits**.
4. **Project Settings → API**: copy the project URL and the `anon` public key into
   `local.properties` (this file is git-ignored; never commit keys):

   ```properties
   supabase.url=https://YOUR-REF.supabase.co
   supabase.anonKey=eyJhbGciOi...
   ```

5. Rebuild. The demo banner in Account disappears once the app is connected.

Moderation: reports land in `public.reports`. Read them in the Supabase dashboard (service role).
Mark genuine members with `update profiles set verified = true where display_id = 'DP12345';` —
users cannot set `verified` or `premium` themselves (a trigger blocks it).

## 2. Crash reporting (Sentry, optional)

Create a project at <https://sentry.io> (platform: Android) and add its DSN:

```properties
sentry.dsn=https://xxxx@oNNNN.ingest.sentry.io/NNNN
```

It only runs in release builds, with no personal data attached. The app uses `sentry-android-core`
(no native libraries) so it stays compatible with 16 KB page-size devices.

## 3. Push notifications (not done yet)

Push needs a Firebase project, which only you can create:

1. Create a Firebase project, add an Android app with the final application id, and download
   `google-services.json` into `app/`.
2. Then ask for the FCM step to be finished: add the `com.google.gms.google-services` plugin and
   `firebase-messaging`, store device tokens in a `push_tokens` table, and send notifications for
   new interests, accepted interests and new messages from a Supabase Edge Function triggered by
   database webhooks, with one notification channel per type and text in the user's language.

## 4. Release build

1. Choose the final application id (for example `com.darjiparivar.app`) and set it in
   `app/build.gradle` (`applicationId`). It can't change after the first Play Store upload.
2. Create an upload key once:

   ```bash
   keytool -genkeypair -v -keystore ~/keys/darjiparivar-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

3. Add it to `local.properties`:

   ```properties
   release.storeFile=/Users/you/keys/darjiparivar-upload.jks
   release.storePassword=...
   release.keyAlias=upload
   release.keyPassword=...
   ```

4. Build the bundle for Play: `./gradlew bundleRelease` →
   `app/build/outputs/bundle/release/app-release.aab`. R8 code and resource shrinking is on.

## 5. Tests

- Unit tests: `./gradlew testDebugUnitTest`
- End-to-end on a device or emulator (demo backend): `./gradlew connectedDebugAndroidTest`
  (turn off animations in Developer options first).
