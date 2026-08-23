# PayPal wallet settlement

SubHub keeps its Wallet ledger on the device. Dom Settings selects Sandbox or Live and accepts the
matching PayPal Client ID and secret for this installation. Both values are encrypted at rest with
an Android Keystore AES-GCM key and are never compiled into the APK. An optional PayPal.Me or other
PayPal-hosted payment link remains available as a fallback.

The environment toggle is an authorization boundary. Changing Sandbox/Live or changing the Client
ID clears the old credentials and saved-wallet state and cancels any active checkout. The Orders
client only accepts the two compiled PayPal API hosts; there is no user-editable server origin.

For a payment, SubHub obtains an OAuth token, creates an Orders v2 order for the exact bounded EUR
settlement, and opens PayPal's approval page. On return it captures the order and accepts completion
only if the environment boundary, local settlement reference, PayPal order, currency, amount, and
capture status all match.

Before order creation, the official PayPal Android fraud-protection module collects Magnes risk
data without requesting location. Its transaction-scoped client metadata ID is attached to create
and capture as `PayPal-Client-Metadata-Id`. The collector uses the same Sandbox or Live environment
as the Orders request, and the ID is not retained as a reusable app identifier.

Create and capture use separate settlement-derived `PayPal-Request-Id` idempotency keys. Network
timeouts, HTTP 408/429, and 5xx responses receive one bounded retry; payer, validation, and other
4xx failures do not. Capture failures leave checkout pending so retry uses the same idempotency key.
Sanitized failures include PayPal's debug ID when provided.

## Saved wallet and eligibility

Dom mode can request a saved PayPal wallet. The next explicitly approved order includes
`store_in_vault=ON_SUCCESS`, `usage_type=MERCHANT`, and `customer_type=CONSUMER`. This order is also
the runtime capability probe: recognized vault/account capability errors mark the feature
unavailable rather than silently creating a non-vaulted order.

A `VAULTED` result becomes ready only when the response includes both `vault.id` and `customer.id`.
An `APPROVED` result without IDs remains pending because PayPal can finish vault creation
asynchronously. The app does not invent readiness in that state; a production service normally
learns the final token from `VAULT.PAYMENT-TOKEN.CREATED`. Vault and customer IDs are encrypted and
bound to the selected environment and Client ID.

Live vault eligibility is reviewed and enabled in PayPal's account/developer settings; it is not a
generic preflight API result. Switching to Live therefore requires Live credentials and a fresh
payer authorization even if Sandbox was already ready.

For payment-link fallback, PayPal.Me links receive the exact EUR amount using PayPal's documented
`paypal.me/name/10.00EUR` form. The payer returns to SubHub and marks only the local ledger paid; the
app does not represent that fallback as PayPal-side verification.

The two payment methods have different targets by PayPal design. Orders API funds go to the
merchant/payee associated with the connected API credentials. A PayPal.Me URL identifies its own
hosted-link recipient and cannot be supplied as the Orders API payee, so SubHub uses that link only
when API checkout is disconnected.

Android Keystore protects credentials and tokens at rest but cannot make a merchant secret
unextractable while the app process is running. An on-device client also cannot reliably receive a
public PayPal webhook while offline or unreachable; those are deliberate tradeoffs of this local
development architecture.
