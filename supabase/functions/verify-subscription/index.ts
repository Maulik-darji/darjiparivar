// Verifies a Google Play subscription purchase and turns on premium for the caller.
//
// Deploy:   supabase functions deploy verify-subscription
// Secrets:  supabase secrets set GOOGLE_SERVICE_ACCOUNT="$(cat play-service-account.json)"
//           supabase secrets set ANDROID_PACKAGE=com.example.darjiparivar
//
// The service account needs "View financial data" + "Manage orders and subscriptions"
// in Play Console → Users and permissions.

import { createClient } from "jsr:@supabase/supabase-js@2";

const PRODUCT_ID = "darji_monthly";
const ACTIVE = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"]);

Deno.serve(async (req) => {
  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const auth = req.headers.get("Authorization") ?? "";
    const asUser = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: auth } },
    });
    const { data: { user } } = await asUser.auth.getUser();
    if (!user) return json({ error: "not_allowed: sign in first" }, 401);

    const { productId, purchaseToken } = await req.json();
    if (productId !== PRODUCT_ID || typeof purchaseToken !== "string") {
      return json({ error: "not_allowed: unknown product" }, 400);
    }

    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    // A purchase token belongs to one account only.
    const { data: existing } = await admin.from("subscriptions")
      .select("user_id").eq("purchase_token", purchaseToken).maybeSingle();
    if (existing && existing.user_id !== user.id) {
      return json({ error: "not_allowed: purchase belongs to another account" }, 403);
    }

    const pkg = Deno.env.get("ANDROID_PACKAGE") ?? "com.example.darjiparivar";
    const token = await googleAccessToken(JSON.parse(Deno.env.get("GOOGLE_SERVICE_ACCOUNT")!));
    const res = await fetch(
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases/subscriptionsv2/tokens/${purchaseToken}`,
      { headers: { Authorization: `Bearer ${token}` } },
    );
    if (!res.ok) return json({ error: `server: Google Play returned ${res.status}` }, 502);
    const sub = await res.json();

    const line = (sub.lineItems ?? []).find((l: { productId: string }) => l.productId === PRODUCT_ID);
    const active = ACTIVE.has(sub.subscriptionState) && !!line;
    const expiresAt: string | null = line?.expiryTime ?? null;

    await admin.from("subscriptions").upsert({
      purchase_token: purchaseToken,
      user_id: user.id,
      product_id: PRODUCT_ID,
      state: sub.subscriptionState,
      expires_at: expiresAt,
      updated_at: new Date().toISOString(),
    });
    await admin.from("profiles")
      .update({ premium: active, premium_until: active ? expiresAt : null })
      .eq("id", user.id);

    return json({ active, expiresAt });
  } catch (e) {
    return json({ error: `server: ${e instanceof Error ? e.message : e}` }, 500);
  }
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** OAuth access token for the Play Developer API from a service-account key (RS256 JWT). */
async function googleAccessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const enc = (o: unknown) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc({ alg: "RS256", typ: "JWT" })}.${enc({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })}`;
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s/g, "");
  const key = await crypto.subtle.importKey(
    "pkcs8",
    Uint8Array.from(atob(pem), (c) => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)),
  );
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${b64url(sig)}`,
    }),
  });
  const data = await res.json();
  if (!data.access_token) throw new Error("could not get Google access token");
  return data.access_token;
}

function b64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
