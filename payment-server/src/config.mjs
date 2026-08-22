import path from 'node:path';

function integer(value, fallback, minimum, maximum) {
  const parsed = value === undefined || value === '' ? fallback : Number.parseInt(value, 10);
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`Invalid bounded integer configuration (${minimum}-${maximum})`);
  }
  return parsed;
}

export function loadConfig(environment = process.env) {
  const paypalEnvironment = environment.PAYPAL_ENV || 'sandbox';
  if (!['sandbox', 'live'].includes(paypalEnvironment)) {
    throw new Error('PAYPAL_ENV must be sandbox or live');
  }
  const publicBaseUrl = (environment.PUBLIC_BASE_URL || '').replace(/\/+$/, '');
  const appReturnUri = (environment.APP_RETURN_URI || 'betasafe://paypal').replace(/\/+$/, '');
  const currency = environment.PAYMENT_CURRENCY || 'EUR';
  if (!/^[A-Z]{3}$/.test(currency)) throw new Error('PAYMENT_CURRENCY must be ISO-4217');
  if (!publicBaseUrl) throw new Error('PUBLIC_BASE_URL is required');
  if (!appReturnUri.startsWith('betasafe://paypal')) {
    throw new Error('APP_RETURN_URI must use the BetaSafe PayPal return route');
  }
  const minCents = integer(environment.MIN_PAYMENT_CENTS, 1, 1, 50_000);
  const maxCents = integer(environment.MAX_PAYMENT_CENTS, 20_000, minCents, 200_000);
  return Object.freeze({
    paypalEnvironment,
    paypalBaseUrl: paypalEnvironment === 'live'
      ? 'https://api-m.paypal.com' : 'https://api-m.sandbox.paypal.com',
    clientId: environment.PAYPAL_CLIENT_ID || '',
    clientSecret: environment.PAYPAL_CLIENT_SECRET || '',
    webhookId: environment.PAYPAL_WEBHOOK_ID || '',
    publicBaseUrl,
    appReturnUri,
    currency,
    minCents,
    maxCents,
    port: integer(environment.PORT, 8787, 1, 65535),
    dataFile: path.resolve(environment.DATA_FILE || './data/orders.json')
  });
}
