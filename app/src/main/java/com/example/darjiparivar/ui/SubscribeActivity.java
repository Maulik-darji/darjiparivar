package com.example.darjiparivar.ui;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.android.billingclient.api.Purchase;
import com.example.darjiparivar.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** ₹99/month subscription that unlocks other members' full profiles. */
public class SubscribeActivity extends AppCompatActivity implements Billing.Listener {

    public static class SubscribeViewModel extends BaseViewModel {
        public SubscribeViewModel(@NonNull Application app) { super(app); }
    }

    private SubscribeViewModel vm;
    private Billing billing;
    private MaterialButton subscribe;
    private View progress;
    private boolean unavailable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subscribe);
        Ui.applyInsets(findViewById(R.id.root));
        vm = new ViewModelProvider(this).get(SubscribeViewModel.class);
        subscribe = findViewById(R.id.btn_subscribe);
        progress = findViewById(R.id.progress);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        subscribe.setOnClickListener(v -> {
            Haptics.tap(v);
            if (vm.repo.isDemo()) demoPurchase();
            else if (billing.ready()) billing.launch(this);
            else Ui.snack(v, getString(R.string.billing_unavailable), null);
        });
        findViewById(R.id.btn_restore).setOnClickListener(v -> {
            Haptics.tap(v);
            restore(true);
        });

        billing = new Billing(this, this);
        if (!vm.repo.isDemo()) {
            billing.connect();
            restore(false);
        }
    }

    private void demoPurchase() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.demo_purchase_title)
                .setMessage(R.string.demo_purchase_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.demo_purchase_confirm, (d, w) -> verify(null, null))
                .show();
    }

    /** Re-links an existing Play subscription, e.g. after reinstalling or on a new phone. */
    private void restore(boolean userAsked) {
        if (vm.repo.isDemo()) {
            if (userAsked) Ui.snack(subscribe, getString(R.string.nothing_to_restore), null);
            return;
        }
        billing.restore(purchases -> {
            for (Purchase p : purchases) {
                if (p.getProducts().contains(Billing.PRODUCT_ID)
                        && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    verify(p, p.getPurchaseToken());
                    return;
                }
            }
            if (userAsked) Ui.snack(subscribe, getString(R.string.nothing_to_restore), null);
        });
    }

    private void verify(Purchase purchase, String token) {
        busy(true);
        vm.act(() -> { vm.repo.verifySubscription(Billing.PRODUCT_ID, token); return null; }, (r, error) -> {
            busy(false);
            if (error != null) {
                Haptics.reject(subscribe);
                Ui.snack(subscribe, getString(R.string.verify_failed), null);
                return;
            }
            if (purchase != null) billing.acknowledge(purchase);
            Haptics.confirm(subscribe);
            setResult(RESULT_OK);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.subscribed_title)
                    .setMessage(R.string.subscribed_message)
                    .setPositiveButton(R.string.continue_, (d, w) -> finish())
                    .setOnDismissListener(d -> finish())
                    .show();
        });
    }

    private void busy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        subscribe.setEnabled(!busy);
    }

    // ---- Billing.Listener ----

    @Override
    public void onPrice(String formattedPrice) {
        ((TextView) findViewById(R.id.price)).setText(formattedPrice);
        subscribe.setText(getString(R.string.subscribe_for, formattedPrice));
    }

    @Override
    public void onPurchased(Purchase purchase) {
        verify(purchase, purchase.getPurchaseToken());
    }

    @Override
    public void onUnavailable() {
        unavailable = true;
    }

    @Override
    public void onCancelled() {
        busy(false);
    }

    @Override
    public void onFailed() {
        busy(false);
        Haptics.reject(subscribe);
        Ui.snack(subscribe, getString(unavailable ? R.string.billing_unavailable : R.string.err_generic), null);
    }

    @Override
    protected void onDestroy() {
        billing.end();
        super.onDestroy();
    }
}
