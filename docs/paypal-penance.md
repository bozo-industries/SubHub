# PayPal Money Rules integration

SubHub's Money Rules feature is an opt-in ledger with explicit PayPal Checkout. Detection never charges a payer. A new detector track may add a bounded local entry, and the payer later chooses **Review payment in PayPal**.

## Safety and trust boundary

- The Android app stores strike rules, capped ledger entries, mercy state, and settlement history locally.
- Every rule set has an amount per strike, daily cap, weekly cap, and false-positive mercy window.
- **Safety Release — Clear All Unpaid** is permanently available and never initiates payment.
- The app sends only a random settlement ID, exact amount, and `EUR` currency to the backend.
- The PayPal client ID/secret and webhook ID exist only in the backend environment.
- PayPal approval redirects to the backend, which returns control to Android without capturing.
- Android requests capture only if the matching local settlement is still active.
- Completion is accepted only when order ID, settlement ID, amount, currency, and capture status all match.
- A verified `PAYMENT.CAPTURE.COMPLETED` webhook provides an independent completion path.

This follows PayPal's documented server-side Orders v2 flow and OAuth requirement:

- [PayPal Orders v2 integration](https://developer.paypal.com/api/rest/integration/orders-api/)
- [PayPal REST authentication](https://developer.paypal.com/api/rest/authentication/)
- [PayPal webhook verification](https://developer.paypal.com/api/rest/webhooks/rest/)

## Sandbox configuration

1. In the PayPal Developer Dashboard, create or select a Sandbox REST application associated with the receiving business account.
2. Create a separate Sandbox personal payer. A merchant account should not attempt to pay itself.
3. Copy `payment-server/.env.example` to `payment-server/.env`.
4. Put the Sandbox client ID and secret in that untracked file. Never add them to Gradle, Android resources, source code, screenshots, or chat.
5. Give the backend a public HTTPS URL and set `PUBLIC_BASE_URL` to that origin. The same origin must expose `/paypal/return`, `/paypal/cancel`, and `/api/v1/paypal/webhook`.
6. Register the webhook URL in the PayPal app, subscribe to `PAYMENT.CAPTURE.COMPLETED`, and put its webhook ID in `.env`.
7. Start and test the backend:

   ```powershell
   Set-Location payment-server
   npm test
   npm start
   ```

8. Provision the public backend origin while building the APK; it is deployment configuration and is not shown as an editable field in the app:

   ```powershell
   $env:SUBHUB_PAYPAL_BACKEND_URL = 'https://payments.example.com'
   .\gradlew.bat assembleDebug
   Remove-Item Env:\SUBHUB_PAYPAL_BACKEND_URL
   ```

   The equivalent Gradle property is `-PpaypalBackendUrl=https://payments.example.com`. For emulator-only development, explicitly use `-PpaypalBackendUrl=http://10.0.2.2:8787`; debug builds accept only that emulator alias or loopback over HTTP. Any build without a configured backend keeps local Money Rules usable but disables checkout with an honest status message.

PayPal client secrets, webhook IDs, and OAuth tokens belong only in the backend environment. SubHub intentionally has no screen for entering them: an APK cannot keep merchant secrets confidential, cannot receive public PayPal webhooks, and should not impersonate a server running on the phone.

The server persists its order correlation file beneath `payment-server/data/`, which is ignored by the repository's general build/data policy. Production deployment should mount that path on encrypted persistent storage, run a single writer instance or replace it with a transactional database, terminate TLS at a trusted proxy, rate-limit public endpoints, and retain provider receipts according to the merchant's legal and tax requirements.

## Live rollout

Before switching `PAYPAL_ENV=live`, verify the business account's approved use case and merchant country, use a different live REST app and webhook, set a conservative `MAX_PAYMENT_CENTS`, deploy behind HTTPS, run one complete low-value payment/refund exercise, and verify the PayPal dashboard receipt against SubHub's local settlement ID. Do not reuse Sandbox credentials in production.
