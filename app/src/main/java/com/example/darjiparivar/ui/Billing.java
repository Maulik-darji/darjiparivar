package com.example.darjiparivar.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Google Play Billing for the monthly subscription ("darji_monthly", ₹99/month, set up in Play
 * Console). Purchases are verified by the server before anything unlocks.
 */
final class Billing implements PurchasesUpdatedListener {

    static final String PRODUCT_ID = "darji_monthly";

    interface Listener {
        /** Price from Play, e.g. "₹99.00", once the product has loaded. */
        void onPrice(String formattedPrice);

        void onPurchased(Purchase purchase);

        /** Billing isn't available (no Play account, product not set up yet, network...). */
        void onUnavailable();

        void onCancelled();

        void onFailed();
    }

    private final BillingClient client;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ProductDetails product;

    Billing(Context context, Listener listener) {
        this.listener = listener;
        client = BillingClient.newBuilder(context.getApplicationContext())
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) loadProduct();
                else main.post(listener::onUnavailable);
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Reconnected on the next connect().
            }
        });
    }

    private void loadProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()))
                .build();
        client.queryProductDetailsAsync(params, (result, list) -> main.post(() -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || list.isEmpty()
                    || list.get(0).getSubscriptionOfferDetails() == null
                    || list.get(0).getSubscriptionOfferDetails().isEmpty()) {
                listener.onUnavailable();
                return;
            }
            product = list.get(0);
            listener.onPrice(product.getSubscriptionOfferDetails().get(0)
                    .getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice());
        }));
    }

    boolean ready() {
        return product != null;
    }

    void launch(Activity activity) {
        if (product == null) return;
        String offerToken = product.getSubscriptionOfferDetails().get(0).getOfferToken();
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(product)
                                .setOfferToken(offerToken)
                                .build()))
                .build();
        client.launchBillingFlow(activity, params);
    }

    /** Active purchases already on this Google account, e.g. after reinstalling. */
    void restore(java.util.function.Consumer<List<Purchase>> onResult) {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build(),
                (result, purchases) -> main.post(() -> onResult.accept(
                        result.getResponseCode() == BillingClient.BillingResponseCode.OK
                                ? purchases : Collections.emptyList())));
    }

    /** Play refunds purchases that aren't acknowledged within 3 days. */
    void acknowledge(Purchase purchase) {
        if (purchase.isAcknowledged()) return;
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken()).build(), r -> { });
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        main.post(() -> {
            int code = result.getResponseCode();
            if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (Purchase p : purchases) {
                    if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) listener.onPurchased(p);
                }
            } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
                listener.onCancelled();
            } else {
                listener.onFailed();
            }
        });
    }

    void end() {
        client.endConnection();
    }
}
