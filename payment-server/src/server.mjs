import { createApp } from './app.mjs';
import { loadConfig } from './config.mjs';
import { OrderStore } from './order-store.mjs';
import { PayPalClient } from './paypal-client.mjs';

const config = loadConfig();
const store = new OrderStore(config.dataFile);
await store.initialize();
const server = createApp({ config, paypal: new PayPalClient(config), store });
server.listen(config.port, '0.0.0.0', () => {
  process.stdout.write(`BetaSafe payment service listening on port ${config.port}\n`);
});
