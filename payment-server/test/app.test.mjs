import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { createApp } from '../src/app.mjs';
import { OrderStore } from '../src/order-store.mjs';

const settlementId = '11111111-1111-4111-8111-111111111111';

async function fixture() {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'betasafe-paypal-test-'));
  const config = {
    paypalEnvironment: 'sandbox',
    publicBaseUrl: 'http://127.0.0.1',
    appReturnUri: 'betasafe://paypal',
    currency: 'EUR',
    minCents: 100,
    maxCents: 20_000
  };
  const calls = { create: 0, capture: 0, verify: 0 };
  const paypal = {
    async createOrder(input) {
      calls.create++;
      return {
        id: 'ORDER-ABC123', status: 'CREATED',
        links: [{ rel: 'payer-action', href: 'https://www.sandbox.paypal.com/checkoutnow?token=ORDER-ABC123' }],
        input
      };
    },
    async captureOrder(orderId) {
      calls.capture++;
      return {
        id: orderId,
        status: 'COMPLETED',
        purchase_units: [{
          custom_id: settlementId,
          payments: { captures: [{ status: 'COMPLETED', amount: { currency_code: 'EUR', value: '5.00' } }] }
        }]
      };
    },
    async verifyWebhook() { calls.verify++; return true; }
  };
  const store = new OrderStore(path.join(directory, 'orders.json'));
  await store.initialize();
  const server = createApp({ config, paypal, store });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  return {
    base: `http://127.0.0.1:${address.port}`,
    calls,
    store,
    async close() {
      await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
      await rm(directory, { recursive: true, force: true });
    }
  };
}

async function createOrder(testFixture) {
  return fetch(`${testFixture.base}/api/v1/orders`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ settlementId, amountCents: 500, currency: 'EUR' })
  });
}

test('creates an idempotent bounded order without exposing credentials', async () => {
  const current = await fixture();
  try {
    const first = await createOrder(current);
    assert.equal(first.status, 201);
    const body = await first.json();
    assert.deepEqual(Object.keys(body).sort(),
      ['amountCents', 'approvalUrl', 'currency', 'orderId', 'settlementId', 'status'].sort());
    assert.equal(body.amountCents, 500);
    assert.match(body.approvalUrl, /^https:\/\//);

    const second = await createOrder(current);
    assert.equal(second.status, 200);
    assert.equal(current.calls.create, 1);
  } finally {
    await current.close();
  }
});

test('rejects an amount outside merchant limits before calling PayPal', async () => {
  const current = await fixture();
  try {
    const response = await fetch(`${current.base}/api/v1/orders`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ settlementId, amountCents: 99, currency: 'EUR' })
    });
    assert.equal(response.status, 400);
    assert.equal(current.calls.create, 0);
  } finally {
    await current.close();
  }
});

test('return route does not capture until the Android client confirms', async () => {
  const current = await fixture();
  try {
    await createOrder(current);
    const returned = await fetch(`${current.base}/paypal/return?token=ORDER-ABC123`, {
      redirect: 'manual'
    });
    assert.equal(returned.status, 302);
    assert.equal(returned.headers.get('location'),
      'betasafe://paypal/result?orderId=ORDER-ABC123');
    assert.equal(current.calls.capture, 0);

    const captured = await fetch(`${current.base}/api/v1/orders/ORDER-ABC123/capture`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}'
    });
    assert.equal(captured.status, 200);
    assert.equal((await captured.json()).status, 'COMPLETED');
    assert.equal(current.calls.capture, 1);
  } finally {
    await current.close();
  }
});

test('canceled order cannot be captured', async () => {
  const current = await fixture();
  try {
    await createOrder(current);
    const canceled = await fetch(`${current.base}/api/v1/orders/ORDER-ABC123/cancel`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}'
    });
    assert.equal(canceled.status, 200);
    assert.equal((await canceled.json()).status, 'CANCELED');
    const captured = await fetch(`${current.base}/api/v1/orders/ORDER-ABC123/capture`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}'
    });
    assert.equal(captured.status, 409);
    assert.equal(current.calls.capture, 0);
  } finally {
    await current.close();
  }
});

test('verified completion webhook updates only an exact local order', async () => {
  const current = await fixture();
  try {
    await createOrder(current);
    const response = await fetch(`${current.base}/api/v1/paypal/webhook`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        event_type: 'PAYMENT.CAPTURE.COMPLETED',
        resource: {
          status: 'COMPLETED',
          custom_id: settlementId,
          amount: { currency_code: 'EUR', value: '5.00' },
          supplementary_data: { related_ids: { order_id: 'ORDER-ABC123' } }
        }
      })
    });
    assert.equal(response.status, 200);
    assert.equal(current.store.get('ORDER-ABC123').status, 'COMPLETED');
    assert.equal(current.calls.verify, 1);
  } finally {
    await current.close();
  }
});
