import http from 'node:http';
import { URL } from 'node:url';
import { PayPalError } from './paypal-client.mjs';

const MAX_BODY_BYTES = 32 * 1024;
const SETTLEMENT_PATTERN = /^[0-9a-f]{8}-[0-9a-f-]{27,36}$/i;
const ORDER_PATTERN = /^[A-Z0-9_-]{1,128}$/i;

export function createApp({ config, paypal, store }) {
  return http.createServer(async (request, response) => {
    response.setHeader('cache-control', 'no-store');
    response.setHeader('x-content-type-options', 'nosniff');
    try {
      const url = new URL(request.url, config.publicBaseUrl);
      if (request.method === 'GET' && url.pathname === '/health') {
        return json(response, 200, { ok: true, environment: config.paypalEnvironment });
      }
      if (request.method === 'POST' && url.pathname === '/api/v1/orders') {
        const input = await readJson(request);
        validateCreate(input, config);
        const existing = store.findBySettlement(input.settlementId);
        if (existing) return json(response, 200, publicOrder(existing));
        const paypalOrder = await paypal.createOrder(input);
        const approvalUrl = link(paypalOrder.links, 'payer-action')
          || link(paypalOrder.links, 'approve');
        if (!ORDER_PATTERN.test(paypalOrder.id || '') || !isPayPalUrl(approvalUrl)) {
          throw new PayPalError(502, 'PayPal did not return a usable approval link');
        }
        const now = new Date().toISOString();
        const order = await store.put({
          orderId: paypalOrder.id,
          settlementId: input.settlementId,
          amountCents: input.amountCents,
          currency: config.currency,
          status: paypalOrder.status || 'CREATED',
          approvalUrl,
          createdAt: now,
          updatedAt: now
        });
        return json(response, 201, publicOrder(order));
      }

      const orderMatch = url.pathname.match(/^\/api\/v1\/orders\/([A-Za-z0-9_-]{1,128})$/);
      if (request.method === 'GET' && orderMatch) {
        const order = store.get(orderMatch[1]);
        return order ? json(response, 200, publicOrder(order))
          : json(response, 404, { error: 'Order not found' });
      }

      const captureMatch = url.pathname.match(
        /^\/api\/v1\/orders\/([A-Za-z0-9_-]{1,128})\/capture$/);
      if (request.method === 'POST' && captureMatch) {
        await readJson(request);
        const order = store.get(captureMatch[1]);
        if (!order) return json(response, 404, { error: 'Order not found' });
        if (order.status === 'CANCELED') return json(response, 409, { error: 'Order canceled' });
        if (order.status === 'COMPLETED') return json(response, 200, publicOrder(order));
        const captured = await paypal.captureOrder(order.orderId);
        const verified = verifiedCapture(captured, order);
        if (!verified) throw new PayPalError(502, 'Captured order did not match the ledger');
        const updated = await store.update(order.orderId, { status: 'COMPLETED' });
        return json(response, 200, publicOrder(updated));
      }

      const cancelMatch = url.pathname.match(
        /^\/api\/v1\/orders\/([A-Za-z0-9_-]{1,128})\/cancel$/);
      if (request.method === 'POST' && cancelMatch) {
        await readJson(request);
        const order = store.get(cancelMatch[1]);
        if (!order) return json(response, 404, { error: 'Order not found' });
        if (order.status === 'COMPLETED') {
          return json(response, 409, { error: 'Completed payment cannot be canceled here' });
        }
        const updated = await store.update(order.orderId,
          { status: 'CANCELED', approvalUrl: '' });
        return json(response, 200, publicOrder(updated));
      }

      if (request.method === 'GET'
          && (url.pathname === '/paypal/return' || url.pathname === '/paypal/cancel')) {
        const orderId = url.searchParams.get('token') || '';
        const order = ORDER_PATTERN.test(orderId) ? store.get(orderId) : null;
        if (!order) return text(response, 404, 'Unknown BetaSafe order.');
        const canceled = url.pathname.endsWith('/cancel');
        if (canceled && order.status !== 'COMPLETED') {
          await store.update(orderId, { status: 'CANCELED', approvalUrl: '' });
        }
        const destination = `${config.appReturnUri}/${canceled ? 'cancel' : 'result'}`
          + `?orderId=${encodeURIComponent(orderId)}`;
        response.writeHead(302, { location: destination });
        return response.end();
      }

      if (request.method === 'POST' && url.pathname === '/api/v1/paypal/webhook') {
        const event = await readJson(request);
        if (!await paypal.verifyWebhook(request.headers, event)) {
          return json(response, 400, { error: 'Invalid webhook signature' });
        }
        if (event.event_type === 'PAYMENT.CAPTURE.COMPLETED') {
          const orderId = event.resource?.supplementary_data?.related_ids?.order_id;
          const order = ORDER_PATTERN.test(orderId || '') ? store.get(orderId) : null;
          if (order && verifiedWebhook(event.resource, order)) {
            await store.update(orderId, { status: 'COMPLETED' });
          }
        }
        return json(response, 200, { received: true });
      }
      return json(response, 404, { error: 'Not found' });
    } catch (error) {
      const status = error instanceof ClientError || error instanceof PayPalError
        ? error.status : 500;
      const message = status >= 500 && !(error instanceof PayPalError)
        ? 'Internal payment service error' : error.message;
      return json(response, status, { error: message });
    }
  });
}

function validateCreate(input, config) {
  if (!input || !SETTLEMENT_PATTERN.test(input.settlementId || '')) {
    throw new ClientError(400, 'Invalid settlement ID');
  }
  if (!Number.isSafeInteger(input.amountCents)
      || input.amountCents < config.minCents || input.amountCents > config.maxCents) {
    throw new ClientError(400, 'Amount is outside merchant limits');
  }
  if (input.currency !== config.currency) throw new ClientError(400, 'Currency is not accepted');
}

function verifiedCapture(captured, order) {
  const purchase = captured.purchase_units?.[0];
  const capture = purchase?.payments?.captures?.[0];
  return captured.id === order.orderId && captured.status === 'COMPLETED'
    && purchase?.custom_id === order.settlementId
    && capture?.status === 'COMPLETED'
    && capture?.amount?.currency_code === order.currency
    && parseMoney(capture?.amount?.value) === order.amountCents;
}

function verifiedWebhook(resource, order) {
  return resource?.status === 'COMPLETED'
    && resource?.custom_id === order.settlementId
    && resource?.amount?.currency_code === order.currency
    && parseMoney(resource?.amount?.value) === order.amountCents;
}

function parseMoney(value) {
  if (!/^\d+\.\d{2}$/.test(value || '')) return -1;
  const [whole, fraction] = value.split('.');
  const cents = Number(whole) * 100 + Number(fraction);
  return Number.isSafeInteger(cents) ? cents : -1;
}

function publicOrder(order) {
  return {
    orderId: order.orderId,
    settlementId: order.settlementId,
    amountCents: order.amountCents,
    currency: order.currency,
    status: order.status,
    approvalUrl: order.approvalUrl || ''
  };
}

function link(links, relation) {
  return Array.isArray(links) ? links.find((item) => item.rel === relation)?.href : undefined;
}

function isPayPalUrl(value) {
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'https:'
      && (parsed.hostname === 'paypal.com' || parsed.hostname.endsWith('.paypal.com'));
  } catch {
    return false;
  }
}

async function readJson(request) {
  const chunks = [];
  let total = 0;
  for await (const chunk of request) {
    total += chunk.length;
    if (total > MAX_BODY_BYTES) throw new ClientError(413, 'Request body is too large');
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}'); }
  catch { throw new ClientError(400, 'Request body must be JSON'); }
}

function json(response, status, body) {
  const encoded = Buffer.from(JSON.stringify(body), 'utf8');
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': encoded.length
  });
  response.end(encoded);
}

function text(response, status, body) {
  const encoded = Buffer.from(body, 'utf8');
  response.writeHead(status, {
    'content-type': 'text/plain; charset=utf-8',
    'content-length': encoded.length
  });
  response.end(encoded);
}

class ClientError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
