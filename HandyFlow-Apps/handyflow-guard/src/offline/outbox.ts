import { ApiError } from "@/api/client";

export type OutboxKind = "SCAN" | "INCIDENT" | "DURESS" | "EVIDENCE";
export type OutboxStatus = "PENDING" | "FAILED";

/** One write waiting to reach the server. `id` is also the clientRef, so a retry can never double-create. */
export interface OutboxItem {
  id: string;
  /** Who made it. On a shared tablet an item is only ever sent under its own guard's sign-in. */
  guardId: string;
  kind: OutboxKind;
  payload: Record<string, unknown>;
  recordedAt: string;
  status: OutboxStatus;
  attempts: number;
  lastError: string | null;
  createdAt: string;
}

export interface OutboxStore {
  add(item: OutboxItem): Promise<void>;
  /** One guard's pending items, oldest first. */
  pending(guardId: string): Promise<OutboxItem[]>;
  failed(guardId: string): Promise<OutboxItem[]>;
  update(id: string, patch: Partial<Pick<OutboxItem, "status" | "attempts" | "lastError">>): Promise<void>;
  remove(id: string): Promise<void>;
}

export type Sender = (item: OutboxItem) => Promise<void>;

/** After this many retryable failures an item is parked as FAILED so a supervisor can be told. */
export const MAX_ATTEMPTS = 8;

export interface FlushResult {
  sent: number;
  failed: number;
  /** True when nothing more was tried because the phone has no signal or the sign-in expired. */
  stopped: boolean;
}

/** Panic goes out before anything else, photos go last (they need their incident on the server first); the rest keep the order they were made in. */
const PRIORITY: Record<OutboxKind, number> = { DURESS: 0, INCIDENT: 1, SCAN: 2, EVIDENCE: 3 };

export function sortForSending(items: OutboxItem[]): OutboxItem[] {
  return [...items].sort(
    (a, b) => PRIORITY[a.kind] - PRIORITY[b.kind] || a.createdAt.localeCompare(b.createdAt),
  );
}

/** Retryable: the server may accept the same request later. 409 covers "already being processed". */
function isRetryable(e: ApiError): boolean {
  return e.status === 409 || e.status === 429 || e.status === 408 || e.status >= 500;
}

let inFlight: Promise<FlushResult> | null = null;

/**
 * Sends everything pending. Only one flush runs at a time; a second call waits for and returns the first.
 * No signal (status 0) or an expired sign-in (401) stops the run and keeps every item. A request the server
 * refuses for good (400, 403, 404...) is parked as FAILED with the server's message, never retried silently.
 */
export function flush(store: OutboxStore, send: Sender, guardId: string): Promise<FlushResult> {
  if (inFlight) return inFlight;
  inFlight = run(store, send, guardId).finally(() => {
    inFlight = null;
  });
  return inFlight;
}

async function run(store: OutboxStore, send: Sender, guardId: string): Promise<FlushResult> {
  const result: FlushResult = { sent: 0, failed: 0, stopped: false };
  for (const item of sortForSending(await store.pending(guardId))) {
    try {
      await send(item);
      await store.remove(item.id);
      result.sent += 1;
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      if (e.isNetwork || e.isUnauthorized) {
        result.stopped = true;
        return result;
      }
      const attempts = item.attempts + 1;
      if (isRetryable(e) && attempts < MAX_ATTEMPTS) {
        await store.update(item.id, { attempts, lastError: e.message });
      } else {
        await store.update(item.id, { status: "FAILED", attempts, lastError: e.message });
        result.failed += 1;
      }
    }
  }
  return result;
}
