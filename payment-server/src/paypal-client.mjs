import { randomUUID } from 'node:crypto';

export class PayPalClient {
  constructor(config, fetchImplementation = fetch) {
    this.config = config;
    this.fetch = fetchImplementation;
    this.cachedToken = null;
    this.tokenExpiresAt = 0;
  }

  async accessToken() {
    if (this.cachedToken && Date.now() < this.tokenExpiresAt - 30_000) return this.cachedToken;
    if (!this.config.clientId || !this.config.clientSecret) {
      throw new PayPalError(503, 'PayPal credentials are not configured');
    }
    const credentials = Buffer.from(
      `${this.config.clientId}:${this.config.clientSecret}`, 'utf8').toString('base64');
    const response = await this.fetch(`${this.config.paypalBaseUrl}/v1/oauth2/token`, {
      method: 'POST',
      headers: {
        authorization: `Basic ${credentials}`,
        'content-type': 'application/x-www-form-urlencoded'
      },
      body: 'grant_type=client_credentials'
    });
    const result = await safeJson(response);
    if (!response.ok || typeof result.access_token !== 'string') {
      throw new PayPalError(502, `PayPal authentication failed (${response.status})`);
    }
    this.cachedToken = result.access_token;
    this.tokenExpiresAt = Date.now() + Number(result.expires_in || 300) * 1000;
    return this.cachedToken;
  }

  async createOrder({ settlementId, amountCents, currency }) {
    const body = {
      intent: 'CAPTURE',
      purchase_units: [{
        custom_id: settlementId,
        description: 'BetaSafe penance ledger settlement',
        amount: { currency_code: currency, value: money(amountCents) }
      }],
      payment_source: {
        paypal: {
          experience_context: {
            user_action: 'PAY_NOW',
            return_url: `${this.config.publicBaseUrl}/paypal/return`,
            cancel_url: `${this.config.publicBaseUrl}/paypal/cancel`
          }
        }
      }
    };
    return this.api('/v2/checkout/orders', {
      method: 'POST', body, requestId: settlementId
    });
  }

  async captureOrder(orderId) {
    return this.api(`/v2/checkout/orders/${encodeURIComponent(orderId)}/capture`, {
      method: 'POST', body: {}, requestId: `${orderId}-capture`
    });
  }

  async verifyWebhook(headers, event) {
    if (!this.config.webhookId) throw new PayPalError(503, 'PayPal webhook is not configured');
    const body = {
      transmission_id: headers['paypal-transmission-id'],
      transmission_time: headers['paypal-transmission-time'],
      cert_url: headers['paypal-cert-url'],
      auth_algo: headers['paypal-auth-algo'],
      transmission_sig: headers['paypal-transmission-sig'],
      webhook_id: this.config.webhookId,
      webhook_event: event
    };
    const result = await this.api('/v1/notifications/verify-webhook-signature', {
      method: 'POST', body, requestId: randomUUID()
    });
    return result.verification_status === 'SUCCESS';
  }

  async api(path, { method, body, requestId }) {
    const token = await this.accessToken();
    const response = await this.fetch(`${this.config.paypalBaseUrl}${path}`, {
      method,
      headers: {
        authorization: `Bearer ${token}`,
        'content-type': 'application/json',
        accept: 'application/json',
        'paypal-request-id': requestId
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const result = await safeJson(response);
    if (!response.ok) throw new PayPalError(502, `PayPal request failed (${response.status})`);
    return result;
  }
}

function money(cents) {
  return (cents / 100).toFixed(2);
}

async function safeJson(response) {
  const text = await response.text();
  if (text.length > 256 * 1024) throw new PayPalError(502, 'PayPal response was too large');
  try { return text ? JSON.parse(text) : {}; }
  catch { throw new PayPalError(502, 'PayPal returned invalid JSON'); }
}

export class PayPalError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
