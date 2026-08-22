import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';

export class OrderStore {
  constructor(filePath) {
    this.filePath = filePath;
    this.orders = new Map();
    this.writeChain = Promise.resolve();
  }

  async initialize() {
    try {
      const parsed = JSON.parse(await readFile(this.filePath, 'utf8'));
      for (const value of Array.isArray(parsed.orders) ? parsed.orders : []) {
        if (value && typeof value.orderId === 'string') this.orders.set(value.orderId, value);
      }
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  }

  get(orderId) {
    const value = this.orders.get(orderId);
    return value ? structuredClone(value) : null;
  }

  findBySettlement(settlementId) {
    for (const value of this.orders.values()) {
      if (value.settlementId === settlementId) return structuredClone(value);
    }
    return null;
  }

  async put(order) {
    this.orders.set(order.orderId, structuredClone(order));
    await this.persist();
    return this.get(order.orderId);
  }

  async update(orderId, changes) {
    const existing = this.orders.get(orderId);
    if (!existing) return null;
    const updated = { ...existing, ...changes, updatedAt: new Date().toISOString() };
    this.orders.set(orderId, updated);
    await this.persist();
    return structuredClone(updated);
  }

  persist() {
    this.writeChain = this.writeChain.then(async () => {
      await mkdir(path.dirname(this.filePath), { recursive: true });
      const temporary = `${this.filePath}.${process.pid}.tmp`;
      const body = `${JSON.stringify({ orders: [...this.orders.values()] }, null, 2)}\n`;
      await writeFile(temporary, body, { encoding: 'utf8', mode: 0o600 });
      await rename(temporary, this.filePath);
    });
    return this.writeChain;
  }
}
