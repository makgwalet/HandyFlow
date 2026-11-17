import * as SQLite from "expo-sqlite";
import type { OutboxItem, OutboxStatus, OutboxStore } from "./outbox";

interface Row {
  id: string;
  owner_id: string;
  kind: OutboxItem["kind"];
  payload: string;
  recorded_at: string;
  status: OutboxStatus;
  attempts: number;
  last_error: string | null;
  created_at: string;
}

const toItem = (r: Row): OutboxItem => ({
  id: r.id,
  ownerId: r.owner_id,
  kind: r.kind,
  payload: JSON.parse(r.payload) as Record<string, unknown>,
  recordedAt: r.recorded_at,
  status: r.status,
  attempts: r.attempts,
  lastError: r.last_error,
  createdAt: r.created_at,
});

/** Small key/value store for things that are safe to keep unencrypted (saved load lists). Secrets never go here. */
export interface KeyValueStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}

export interface LocalDb { outbox: OutboxStore; kv: KeyValueStore }

/** The outbox and saved data kept in SQLite, so a queued delivery survives the app being closed or the phone restarting. */
export async function openLocalDb(): Promise<LocalDb> {
  const db = await SQLite.openDatabaseAsync("fuel-app.db");
  await db.execAsync(`
    CREATE TABLE IF NOT EXISTS outbox (
      id TEXT PRIMARY KEY NOT NULL,
      owner_id TEXT NOT NULL,
      kind TEXT NOT NULL,
      payload TEXT NOT NULL,
      recorded_at TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',
      attempts INTEGER NOT NULL DEFAULT 0,
      last_error TEXT,
      created_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
  `);

  const outbox: OutboxStore = {
    async add(item) {
      await db.runAsync(
        "INSERT OR IGNORE INTO outbox (id, owner_id, kind, payload, recorded_at, status, attempts, last_error, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        [item.id, item.ownerId, item.kind, JSON.stringify(item.payload), item.recordedAt, item.status, item.attempts, item.lastError, item.createdAt],
      );
    },
    async pending(ownerId) {
      const rows = await db.getAllAsync<Row>("SELECT * FROM outbox WHERE status = 'PENDING' AND owner_id = ? ORDER BY created_at ASC", [ownerId]);
      return rows.map(toItem);
    },
    async failed(ownerId) {
      const rows = await db.getAllAsync<Row>("SELECT * FROM outbox WHERE status = 'FAILED' AND owner_id = ? ORDER BY created_at ASC", [ownerId]);
      return rows.map(toItem);
    },
    async update(id, patch) {
      const sets: string[] = [];
      const args: (string | number | null)[] = [];
      if (patch.status !== undefined) { sets.push("status = ?"); args.push(patch.status); }
      if (patch.attempts !== undefined) { sets.push("attempts = ?"); args.push(patch.attempts); }
      if (patch.lastError !== undefined) { sets.push("last_error = ?"); args.push(patch.lastError); }
      if (sets.length === 0) return;
      await db.runAsync(`UPDATE outbox SET ${sets.join(", ")} WHERE id = ?`, [...args, id]);
    },
    async remove(id) {
      await db.runAsync("DELETE FROM outbox WHERE id = ?", [id]);
    },
  };

  const kv: KeyValueStore = {
    async get(key) {
      const row = await db.getFirstAsync<{ value: string }>("SELECT value FROM kv WHERE key = ?", [key]);
      return row?.value ?? null;
    },
    async set(key, value) {
      await db.runAsync("INSERT OR REPLACE INTO kv (key, value) VALUES (?, ?)", [key, value]);
    },
    async remove(key) {
      await db.runAsync("DELETE FROM kv WHERE key = ?", [key]);
    },
  };

  return { outbox, kv };
}
