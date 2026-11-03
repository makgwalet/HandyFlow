import * as SQLite from 'expo-sqlite';

/**
 * The offline sync queue's local database. See
 * docs/agriculture/12-OFFLINE-ARCHITECTURE.md for the design this
 * implements and what it deliberately does NOT cover yet (conflict
 * resolution, attachments, schema migrations beyond this first table).
 */
const DB_NAME = 'handyflow_agriculture.db';

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

export function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync(DB_NAME).then(async (db) => {
      await db.execAsync(`
        PRAGMA journal_mode = WAL;
        CREATE TABLE IF NOT EXISTS pending_mutations (
          id TEXT PRIMARY KEY NOT NULL,
          method TEXT NOT NULL,
          url TEXT NOT NULL,
          body TEXT,
          entity_label TEXT NOT NULL,
          created_at TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'PENDING',
          attempt_count INTEGER NOT NULL DEFAULT 0,
          last_error TEXT
        );
      `);
      return db;
    });
  }
  return dbPromise;
}
