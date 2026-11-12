import * as SQLite from "expo-sqlite";
import type { OutboxItem, OutboxStatus, OutboxStore } from "./outbox";

interface Row {
  id: string;
  guard_id: string;
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
  guardId: r.guard_id,
  kind: r.kind,
  payload: JSON.parse(r.payload) as Record<string, unknown>,
  recordedAt: r.recorded_at,
  status: r.status,
  attempts: r.attempts,
  lastError: r.last_error,
  createdAt: r.created_at,
});

/** The outbox kept in SQLite, so a queued scan or panic survives the app being closed or the phone restarting. */
export async function openSqliteOutbox(): Promise<OutboxStore> {
  const db = await SQLite.openDatabaseAsync("guard-outbox.db");
  await db.execAsync(`
    CREATE TABLE IF NOT EXISTS outbox (
      id TEXT PRIMARY KEY NOT NULL,
      guard_id TEXT NOT NULL,
      kind TEXT NOT NULL,
      payload TEXT NOT NULL,
      recorded_at TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',
      attempts INTEGER NOT NULL DEFAULT 0,
      last_error TEXT,
      created_at TEXT NOT NULL
    );
  `);

  return {
    async add(item) {
      await db.runAsync(
        "INSERT OR IGNORE INTO outbox (id, guard_id, kind, payload, recorded_at, status, attempts, last_error, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        [item.id, item.guardId, item.kind, JSON.stringify(item.payload), item.recordedAt, item.status, item.attempts, item.lastError, item.createdAt],
      );
    },
    async pending(guardId) {
      const rows = await db.getAllAsync<Row>("SELECT * FROM outbox WHERE status = 'PENDING' AND guard_id = ? ORDER BY created_at ASC", [guardId]);
      return rows.map(toItem);
    },
    async failed(guardId) {
      const rows = await db.getAllAsync<Row>("SELECT * FROM outbox WHERE status = 'FAILED' AND guard_id = ? ORDER BY created_at ASC", [guardId]);
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
}
