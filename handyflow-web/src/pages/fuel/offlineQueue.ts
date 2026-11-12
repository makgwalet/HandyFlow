// C05: the driver phone's offline queue. Every action a driver takes on a load (start trip, record delivery,
// record location) is kept on the device with its own request id and sent in order when the signal returns.
// A repeat is harmless: completion and evidence carry a clientRequestId the server de-duplicates on.
// A network failure keeps everything and stops the run. A server refusal marks only that item FAILED, keeps it
// visible for Retry or Discard, and holds back later items for the same load so they never run out of order.
import type { QueueFile } from "./evidenceCapture"
export type QueueKind = "START" | "COMPLETE" | "EVIDENCE" | "FAILED"
export interface QueueItem {
  id: string; kind: QueueKind; deliveryId: string; path: string; body: Record<string, unknown> | null
  queuedAt: string; status: "PENDING" | "FAILED"; message: string | null; attempts: number; label: string
  /** A photo or signature kept with the item (a small data URL) and sent as an upload. */
  file?: QueueFile | null
}
type Store = Pick<Storage, "getItem" | "setItem">
const KEY = "fuel-driver-queue-v2"
const LEGACY_KEY = "fuel-driver-queue-v1"

export const isNetworkError = (e: any) => !e?.response

/** False when the phone would not keep it (storage full or blocked): the caller must tell the driver, never pretend it was saved. */
function write(q: QueueItem[], s: Store): boolean { try { s.setItem(KEY, JSON.stringify(q)); return true } catch { return false } }

/** Reads the queue, bringing over completions saved by the earlier version of the app. */
export function readQueue(s: Store = localStorage): QueueItem[] {
  let q: QueueItem[] = []
  try { const raw = s.getItem(KEY); if (raw) q = JSON.parse(raw) } catch { q = [] }
  try {
    const old = s.getItem(LEGACY_KEY)
    if (old) {
      const legacy = JSON.parse(old) as { deliveryId: string; clientRequestId: string; body: Record<string, unknown>; queuedAt: string }[]
      for (const l of legacy) q.push({ id: l.clientRequestId, kind: "COMPLETE", deliveryId: l.deliveryId, path: `/api/v1/fuel/driver/loads/${l.deliveryId}/complete`,
        body: l.body, queuedAt: l.queuedAt, status: "PENDING", message: null, attempts: 0, label: "Delivery" })
      write(q, s); (s as Storage).removeItem?.(LEGACY_KEY)
    }
  } catch { /* an unreadable legacy value is ignored */ }
  return Array.isArray(q) ? q : []
}

/** Adds an item. A completion replaces any earlier completion for the same load (the driver corrected it). Everything else is kept: a load can be started and fail more than once. */
export function enqueue(item: Omit<QueueItem, "queuedAt" | "status" | "message" | "attempts">, s: Store = localStorage): QueueItem | null {
  const full: QueueItem = { ...item, queuedAt: new Date().toISOString(), status: "PENDING", message: null, attempts: 0 }
  const q = readQueue(s).filter(x => !(item.kind === "COMPLETE" && x.kind === "COMPLETE" && x.deliveryId === item.deliveryId))
  return write([...q, full], s) ? full : null
}

export const remove = (id: string, s: Store = localStorage) => write(readQueue(s).filter(x => x.id !== id), s)
/** Puts a failed item back in line so the next run sends it again. */
export const retry = (id: string, s: Store = localStorage) => write(readQueue(s).map(x => x.id === id ? { ...x, status: "PENDING", message: null } : x), s)

export interface FlushResult { sent: QueueItem[]; failed: { item: QueueItem; message: string }[]; stoppedOffline: boolean }

/**
 * Sends pending items in the order they were made. `alreadyDone` lets the caller treat a refusal as success
 * (a start that was refused because the load is already on the road).
 */
export async function flushQueue(send: (i: QueueItem) => Promise<unknown>, s: Store = localStorage,
                                 alreadyDone?: (i: QueueItem) => Promise<boolean>): Promise<FlushResult> {
  const out: FlushResult = { sent: [], failed: [], stoppedOffline: false }
  const blocked = new Set<string>()
  for (const item of readQueue(s)) {
    if (item.status === "FAILED") { blocked.add(item.deliveryId); continue }
    if (blocked.has(item.deliveryId)) continue
    try { await send(item); remove(item.id, s); out.sent.push(item) }
    catch (e: any) {
      if (isNetworkError(e)) { out.stoppedOffline = true; break }
      let resolved = false
      try { resolved = alreadyDone ? await alreadyDone(item) : false } catch { resolved = false }
      if (resolved) { remove(item.id, s); out.sent.push(item); continue }
      const message = e?.response?.data?.message ?? "The server refused this."
      write(readQueue(s).map(x => x.id === item.id ? { ...x, status: "FAILED", message, attempts: x.attempts + 1 } : x), s)
      blocked.add(item.deliveryId); out.failed.push({ item, message })
    }
  }
  return out
}
