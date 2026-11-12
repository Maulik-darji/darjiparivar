package com.example.darjiparivar.ui;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.lifecycle.ViewModelProvider;

import com.example.darjiparivar.BuildConfig;
import com.example.darjiparivar.R;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** Welcome screen with the only sign-in option: Continue with Google. */
public class WelcomeActivity extends AppCompatActivity {

    private static final String TAG = "WelcomeActivity";

    public static class SignInViewModel extends BaseViewModel {
        public SignInViewModel(@NonNull Application app) { super(app); }
    }

    private SignInViewModel vm;
    private View button, progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welcome);
        Ui.applyInsets(findViewById(R.id.root));
        LanguageToggle.bind(findViewById(R.id.root));
        vm = new ViewModelProvider(this).get(SignInViewModel.class);

        button = findViewById(R.id.btn_google);
        progress = findViewById(R.id.progress);
        boolean googleConfigured = !BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty();
        findViewById(R.id.demo_hint).setVisibility(vm.repo.isDemo() ? View.VISIBLE : View.GONE);
        button.setOnClickListener(v -> {
            Haptics.tap(v);
            // The offline demo always uses a sample account, even if a Google client id is set.
            if (vm.repo.isDemo()) signIn(null, null, "", "");
            else if (googleConfigured) requestGoogleCredential();
            else fail(getString(R.string.google_not_configured));
        });
    }

    /** Shows Google's account picker; the ID token is then exchanged for a session. */
    private void requestGoogleCredential() {
        String nonce = randomNonce();
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(sha256(nonce))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        busy(true);
        CredentialManager.create(this).getCredentialAsync(this, request, null,
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
                                signIn(google.getIdToken(), nonce, google.getId(),
                                        google.getDisplayName() == null ? "" : google.getDisplayName());
                                return;
                            } catch (RuntimeException e) {
                                Log.w(TAG, "Unreadable Google credential", e);
                            }
                        }
                        fail(getString(R.string.google_failed));
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            busy(false);
                            return;
                        }
                        Log.w(TAG, "Google sign-in failed", e);
                        fail(getString(R.string.google_failed));
                    }
                });
    }

    private void signIn(String idToken, String nonce, String email, String name) {
        busy(true);
        vm.run(() -> {
            vm.repo.signInWithGoogle(idToken, nonce, email, name);
            return vm.repo.hasProfile();
        }, (hasProfile, error) -> {
            if (error != null) {
                fail(Ui.error(this, error));
                return;
            }
            Haptics.confirm(button);
            Intent intent = new Intent(this, hasProfile ? MainActivity.class : ProfileSetupActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void fail(String message) {
        busy(false);
        Haptics.reject(button);
        Ui.snack(button, message, null);
    }

    private void busy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        button.setEnabled(!busy);
    }

    private static String randomNonce() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
