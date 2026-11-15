import { ApiError } from "@/api/client";

export type OutboxKind = "SCAN" | "INCIDENT" | "DURESS" | "ACK" | "GATE_ARRIVAL" | "GATE_EXIT" | "GATE_PHOTO" | "EVIDENCE";
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
const PRIORITY: Record<OutboxKind, number> = {
  DURESS: 0, INCIDENT: 1, SCAN: 2, ACK: 3,
  // An arrival must reach the server before the departure or photos that name it.
  GATE_ARRIVAL: 4, GATE_EXIT: 5, GATE_PHOTO: 6, EVIDENCE: 7,
};

export function sortForSending(items: OutboxItem[]): OutboxItem[] {
  return [...items].sort(
    (a, b) => PRIORITY[a.kind] - PRIORITY[b.kind] || a.createdAt.localeCompare(b.createdAt),
  );
}

/**
 * Retryable: the server may accept the same request later. 409 covers "already being processed" for the kinds that
 * have a ledger. For an acknowledgement a 409 means the order was replaced by a newer version, which no retry can fix.
 */
function isRetryable(e: ApiError, kind: OutboxKind): boolean {
  if (kind === "ACK" && e.status === 409) return false;
  return e.status === 409 || e.status === 429 || e.status === 408 || e.status >= 500;
}

/** The server's words when a departure is repeated or someone else already closed the visit: the visitor is out, so the job is done. */
const ALREADY_DEPARTED = "already logged as departed";
function isAlreadyDone(item: OutboxItem, e: ApiError): boolean {
  return item.kind === "GATE_EXIT" && e.status === 409 && e.message.toLowerCase().includes(ALREADY_DEPARTED);
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
      if (isAlreadyDone(item, e)) {
        await store.remove(item.id);
        result.sent += 1;
        continue;
      }
      const attempts = item.attempts + 1;
      if (isRetryable(e, item.kind) && attempts < MAX_ATTEMPTS) {
        await store.update(item.id, { attempts, lastError: e.message });
      } else {
        await store.update(item.id, { status: "FAILED", attempts, lastError: e.message });
        result.failed += 1;
      }
    }
  }
  return result;
}
