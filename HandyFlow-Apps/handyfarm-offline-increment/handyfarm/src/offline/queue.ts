import { getDb } from './db';
import { apiClient, ApiError } from '../api/client';

export type PendingMutationRow = {
  id: string;
  method: 'POST' | 'PATCH' | 'PUT';
  url: string;
  body: string | null;
  entity_label: string;
  created_at: string;
  status: 'PENDING' | 'SYNCING' | 'FAILED';
  attempt_count: number;
  last_error: string | null;
};

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/** Queues a write for later replay. Called only when a request fails with a network error (see client.ts). */
export async function enqueueMutation(params: {
  method: 'POST' | 'PATCH' | 'PUT';
  url: string;
  body: unknown;
  entityLabel: string;
}): Promise<string> {
  const db = await getDb();
  const id = generateId();
  await db.runAsync(
    `INSERT INTO pending_mutations (id, method, url, body, entity_label, created_at, status, attempt_count)
     VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0)`,
    [id, params.method, params.url, JSON.stringify(params.body ?? null), params.entityLabel, new Date().toISOString()]
  );
  return id;
}

export async function listPendingMutations(): Promise<PendingMutationRow[]> {
  const db = await getDb();
  return db.getAllAsync<PendingMutationRow>(
    `SELECT * FROM pending_mutations WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC`
  );
}

export async function getPendingCount(): Promise<number> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ count: number }>(
    `SELECT COUNT(*) as count FROM pending_mutations WHERE status IN ('PENDING', 'FAILED')`
  );
  return row?.count ?? 0;
}

async function deleteMutation(id: string) {
  const db = await getDb();
  await db.runAsync(`DELETE FROM pending_mutations WHERE id = ?`, [id]);
}

async function markFailed(id: string, error: string) {
  const db = await getDb();
  await db.runAsync(
    `UPDATE pending_mutations SET status = 'FAILED', attempt_count = attempt_count + 1, last_error = ? WHERE id = ?`,
    [error, id]
  );
}

export interface SyncResult {
  synced: number;
  stillFailed: number;
  errors: { entityLabel: string; error: string }[];
}

/**
 * Replays every queued mutation in creation order. Continues past
 * individual failures (e.g. one bad record shouldn't block the rest of the
 * queue) but stops entirely on the first network-level failure, since that
 * almost certainly means the rest will fail too.
 */
export async function syncPendingMutations(): Promise<SyncResult> {
  const pending = await listPendingMutations();
  const result: SyncResult = { synced: 0, stillFailed: 0, errors: [] };

  for (const row of pending) {
    try {
      await apiClient.request({
        method: row.method,
        url: row.url,
        data: row.body ? JSON.parse(row.body) : undefined,
      });
      await deleteMutation(row.id);
      result.synced += 1;
    } catch (err) {
      const isNetworkError = !(err instanceof ApiError) || err.status === undefined;
      const message = err instanceof Error ? err.message : 'Unknown error';
      await markFailed(row.id, message);
      result.stillFailed += 1;
      result.errors.push({ entityLabel: row.entity_label, error: message });
      if (isNetworkError) break; // still offline — no point trying the rest right now
    }
  }

  return result;
}
