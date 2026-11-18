import { ApiError } from "@/api/client";

/** Every write the app can queue. Add a kind here, give it a priority below and a sender in the mode's package. */
export type OutboxKind = "DISPATCH" | "COMPLETE" | "FAILED_ATTEMPT" | "EVIDENCE" | "DIP_READING" | "RECEIVE_FUEL"
  | "SHIFT_OPEN" | "SHIFT_CLOSE" | "STAY_BOOK" | "STAY_CHECK_IN" | "STAY_CHECK_OUT";
export type OutboxStatus = "PENDING" | "FAILED";

/** One write waiting to reach the server. `id` is also the clientRequestId, so a retry can never double-create. */
export interface OutboxItem {
  id: string;
  /** The signed-in user who made it. An item is only ever sent under its own owner's sign-in. */
  ownerId: string;
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
  /** One user's pending items, oldest first. */
  pending(ownerId: string): Promise<OutboxItem[]>;
  failed(ownerId: string): Promise<OutboxItem[]>;
  update(id: string, patch: Partial<Pick<OutboxItem, "status" | "attempts" | "lastError">>): Promise<void>;
  remove(id: string): Promise<void>;
}

export type Sender = (item: OutboxItem) => Promise<void>;

/** After this many retryable failures an item is parked as FAILED so the driver can see it and the office can be told. */
export const MAX_ATTEMPTS = 8;

export interface FlushResult {
  sent: number;
  failed: number;
  /** True when nothing more was tried: no signal, the sign-in expired, or the subscription is not active. Every item is kept. */
  stopped: boolean;
}

/**
 * A trip must start before it can be completed, and photos go last because they need the delivery on the server.
 * Within the same step the order of creation is kept.
 */
const PRIORITY: Record<OutboxKind, number> = { DISPATCH: 0, COMPLETE: 1, FAILED_ATTEMPT: 1, EVIDENCE: 2, DIP_READING: 1, RECEIVE_FUEL: 1,
  // A shift opens before it closes; a stay is booked, then checked in, then checked out.
  SHIFT_OPEN: 0, SHIFT_CLOSE: 1, STAY_BOOK: 0, STAY_CHECK_IN: 1, STAY_CHECK_OUT: 2 };

export function sortForSending(items: OutboxItem[]): OutboxItem[] {
  return [...items].sort((a, b) => PRIORITY[a.kind] - PRIORITY[b.kind] || a.createdAt.localeCompare(b.createdAt));
}

/** Retryable: the server may accept the same request later. A 409 or 400 is the server saying the request itself is wrong, so it is parked. */
export function isRetryable(e: ApiError): boolean {
  return e.status === 429 || e.status === 408 || e.status >= 500;
}

let inFlight: Promise<FlushResult> | null = null;

/**
 * Sends everything pending. Only one flush runs at a time; a second call waits for and returns the first.
 * No signal (0), an expired sign-in (401) or an inactive subscription (402) stops the run and keeps every item.
 * A request the server refuses for good (400, 403, 404, 409...) is parked as FAILED with the server's message, never retried silently.
 */
export function flush(store: OutboxStore, send: Sender, ownerId: string): Promise<FlushResult> {
  if (inFlight) return inFlight;
  inFlight = run(store, send, ownerId).finally(() => {
    inFlight = null;
  });
  return inFlight;
}

async function run(store: OutboxStore, send: Sender, ownerId: string): Promise<FlushResult> {
  const result: FlushResult = { sent: 0, failed: 0, stopped: false };
  for (const item of sortForSending(await store.pending(ownerId))) {
    try {
      await send(item);
      await store.remove(item.id);
      result.sent += 1;
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      if (e.isNetwork || e.isUnauthorized || e.isPaymentRequired) {
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

/** Test seam: a flush that crashed must not block the next one. */
export function resetFlushForTests(): void {
  inFlight = null;
}
