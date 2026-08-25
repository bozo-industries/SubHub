# PayPal setup

SubHub's Wallet can use a direct PayPal merchant REST app for verified manual checkout and an
optional saved PayPal Wallet. Configure and test Sandbox completely before selecting Live. Sandbox
uses simulated accounts and money; Live uses a separate credential set and real transactions.

## 1. Prepare the PayPal accounts

1. Use a PayPal Business account for the merchant that will receive Live payments. PayPal's REST
   API guide says a Business account is required to go live and to test integrations outside the
   United States.
2. Sign in to the [PayPal Developer Dashboard](https://developer.paypal.com/dashboard/), open
   **Apps & Credentials**, and create or select a REST API app. Copy the Client ID and reveal/copy
   the Client Secret only when entering it in SubHub. Do not put either value in documentation,
   screenshots, chat, or source control.
3. Use the Dashboard's Sandbox/Live selector deliberately. PayPal issues different credentials for
   each environment. The official [REST API getting-started guide](https://developer.paypal.com/api/get-started/)
   also explains the default sandbox business (seller) and personal (buyer) accounts.

PayPal documents Sandbox as an isolated test system that does not touch real PayPal accounts. Use
the personal sandbox account to approve the payer flow and the business sandbox account to inspect
the simulated merchant side. See PayPal's [sandbox testing guide](https://developer.paypal.com/sandbox-testing/overview/).

## 2. Enable saved PayPal Wallets

The merchant app must be eligible and configured for saved payment methods. A successful Client ID
and secret connection alone does not grant this capability.

For the business account, PayPal's current [Save payment methods guide](https://developer.paypal.com/api/save-with-purchase/save-payment-methods/)
directs the merchant to **Account Settings > Payment Preferences > Save PayPal and Venmo payment
methods > Get Started**, submit the requested business-profile information, and wait for PayPal's
eligibility result.

For the REST app:

- In Sandbox, select the sandbox app, enable **Accept payments**, open its advanced options, and
  confirm **Vault** is enabled.
- In Live, select the Live app, open **Features > Payment capabilities**, enable **Save payment
  methods**, then open **Features > Payment methods** and enable the PayPal wallet option shown by
  PayPal. PayPal's current UI/documentation may label this option **PayPal and Venmo**.

That label does not make Venmo available in Germany. PayPal's official
[Venmo eligibility documentation](https://developer.paypal.com/venmo/) limits Venmo checkout to
US-based merchants and US-based consumers using USD, and the payment method is shown only when the
session is eligible. SubHub's German/EUR Wallet setup uses PayPal Wallet, not Venmo.

PayPal states that purchase-later PayPal Wallet tokenization requires approval/configuration for
billing agreements or reference transactions. If the Dashboard capability is absent, pending, or
denied, contact PayPal support or the account representative rather than repeatedly reconnecting
the same credentials. See PayPal's
[Payment Method Tokens guide](https://developer.paypal.com/platforms/checkout/save-payment-methods/purchase-later/payment-tokens-api/paypal/).

## 3. Connect SubHub

1. Unlock **Dom mode** and open **Settings > PayPal**.
2. Choose **Sandbox** or **Live**. Switching environments disconnects the existing credentials and
   saved payer wallet by design.
3. Paste the matching REST app Client ID and Client Secret, then select **Connect**. SubHub verifies
   the merchant credentials without creating a payment.
4. Select **Link Payer Wallet**. PayPal opens a payer-present approval page for a billing agreement.
   Sign in with a sandbox personal buyer when testing, or with the intended real payer only after
   deliberately switching to Live.
5. Finish the PayPal approval, return to SubHub, and wait for **Saved Wallet Ready** or the masked
   linked-payer status. Linking creates no charge. A pending or expired setup must be resumed or
   started again.
6. Enable automatic Wallet payment only after reviewing its confirmation. Auto-pay is available
   only while Hardcore Mode and an active service lock are active, and it pauses rather
   than opening an interactive checkout when PayPal requires the payer again.

## Troubleshooting

- **Merchant API not connected:** confirm the environment and credential pair come from the same
  REST app. Sandbox credentials cannot authenticate against Live, or vice versa.
- **Saved Wallet unavailable:** confirm the business-profile eligibility step and the REST app's
  Save payment methods/Vault capability. The REST app must be enabled for PayPal Wallet billing
  agreements/reference transactions.
- **Approval pending:** finish the PayPal-hosted payer flow, close its completion tab if necessary,
  return to SubHub, and use **Resume Wallet Approval**. Setup tokens expire.
- **Live behaves differently from Sandbox:** Live capability and payer authorization are separate.
  Re-enable/check the Live app and link the real payer again.
- **Auto-pay paused:** relink the payer wallet if PayPal reports payer action or a token problem.
  SubHub intentionally does not fall back to an unnoticed checkout page.

For implementation and security boundaries, see [PayPal wallet settlement](paypal-penance.md).
