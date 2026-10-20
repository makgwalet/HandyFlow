// src/pages/earthmoving/shared/offlineQueue.ts
//
// The phone's queue of field entries. An entry is saved here the moment it is typed, with its own submission id, and stays until the server has
// answered. The id never changes, so sending an entry twice (a retry after a dropped connection) cannot make two. The server re-checks everything;
// this file only remembers what was typed and when, and what the server said.
//
// States: PENDING (not yet sent), SYNCED (applied), REJECTED (refused, with the reason), CONFLICT (a newer reading exists; kept for review).
// Deployment, maintenance release and disposal approval are never queued: they need a fresh check against the live record.

export type FieldKind = "METER_READING" | "POSITION_READING"
export type QueueState = "PENDING" | "SYNCED" | "REJECTED" | "CONFLICT"

export interface QueuedEntry {
  submissionId: string
  kind: FieldKind
  assetId: string
  assetName: string
  capturedAt: string
  meterValue: number | null
  latitude: number | null
  longitude: number | null
  note: string | null
  state: QueueState
  reason: string | null
}

export interface FieldResult { submissionId: string; status: "SYNCED" | "REJECTED" | "CONFLICT"; reason: string | null }

type Store = Pick<Storage, "getItem" | "setItem">
export const QUEUE_KEY = "hf-em-field-queue"
/** Finished entries kept for the person to see; older ones are dropped. Pending entries are never dropped. */
export const KEEP_FINISHED = 30

export function newId(): string {
  const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto
  if (c?.randomUUID) return c.randomUUID()
  // Older phones: a version 4 id from random numbers.
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, ch => {
    const r = Math.floor(Math.random() * 16)
    return (ch === "x" ? r : (r & 0x3) | 0x8).toString(16)
  })
}

const isEntry = (e: unknown): e is QueuedEntry => {
  const x = e as Partial<QueuedEntry> | null
  return !!x && typeof x.submissionId === "string" && typeof x.assetId === "string" && typeof x.capturedAt === "string"
    && (x.kind === "METER_READING" || x.kind === "POSITION_READING") && ["PENDING", "SYNCED", "REJECTED", "CONFLICT"].includes(String(x.state))
}

export function loadQueue(store: Store = localStorage): QueuedEntry[] {
  try {
    const raw = store.getItem(QUEUE_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter(isEntry) : []
  } catch {
    return []
  }
}

/** Saves the queue; returns false if the phone would not keep it (storage full or blocked) so the screen can say so. */
export function saveQueue(entries: QueuedEntry[], store: Store = localStorage): boolean {
  try {
    const pending = entries.filter(e => e.state === "PENDING")
    const finished = entries.filter(e => e.state !== "PENDING").slice(-KEEP_FINISHED)
    store.setItem(QUEUE_KEY, JSON.stringify([...finished, ...pending].sort((a, b) => a.capturedAt.localeCompare(b.capturedAt))))
    return true
  } catch {
    return false
  }
}

export interface NewEntry {
  kind: FieldKind; assetId: string; assetName: string; meterValue?: number | null; latitude?: number | null; longitude?: number | null; note?: string | null
}

/** What is wrong with an entry before it is saved, or "" when it is fine. The server checks again. */
export function entryProblem(e: NewEntry): string {
  if (!e.assetId) return "Choose the machine."
  if (e.kind === "METER_READING") {
    if (e.meterValue == null || Number.isNaN(e.meterValue)) return "Type the meter reading."
    if (e.meterValue < 0) return "A meter reading cannot be negative."
  } else {
    if (e.latitude == null || e.longitude == null) return "A position needs both latitude and longitude."
    if (Math.abs(e.latitude) > 90 || Math.abs(e.longitude) > 180) return "Latitude is -90 to 90 and longitude -180 to 180."
  }
  return ""
}

export function makeEntry(e: NewEntry, now: Date = new Date()): QueuedEntry {
  return {
    submissionId: newId(), kind: e.kind, assetId: e.assetId, assetName: e.assetName, capturedAt: now.toISOString(),
    meterValue: e.kind === "METER_READING" ? e.meterValue ?? null : null,
    latitude: e.kind === "POSITION_READING" ? e.latitude ?? null : null,
    longitude: e.kind === "POSITION_READING" ? e.longitude ?? null : null,
    note: e.note?.trim() ? e.note.trim() : null, state: "PENDING", reason: null,
  }
}

/** Records what the server decided. Entries the server did not mention stay as they are. */
export function applyResults(entries: QueuedEntry[], results: FieldResult[]): QueuedEntry[] {
  const by = new Map(results.map(r => [r.submissionId, r]))
  return entries.map(e => {
    const r = by.get(e.submissionId)
    return r && e.state === "PENDING" ? { ...e, state: r.status, reason: r.reason } : e
  })
}

/** Removes an entry that has not been sent. Sent entries are a record and stay. */
export function discard(entries: QueuedEntry[], submissionId: string): QueuedEntry[] {
  return entries.filter(e => e.submissionId !== submissionId || e.state !== "PENDING")
}

export const pendingOf = (entries: QueuedEntry[]) => entries.filter(e => e.state === "PENDING")

/** True when the failure means "no connection / server down" (keep the entries and try later) rather than "the server said no". */
export function isNetworkFailure(e: unknown): boolean {
  const x = e as { response?: { status?: number }; code?: string } | null
  if (!x) return false
  if (!x.response) return true
  return x.response.status === 502 || x.response.status === 503 || x.response.status === 504
}

/**
 * Sends the pending entries, oldest first, in batches. Stops at the first batch that fails and leaves everything not answered pending, so nothing is lost
 * and nothing is sent as a different entry. Returns the updated queue and, if it stopped, why.
 */
export async function syncPending(
  entries: QueuedEntry[],
  send: (batch: QueuedEntry[]) => Promise<FieldResult[]>,
  batchSize = 50,
): Promise<{ entries: QueuedEntry[]; offline: boolean; error: unknown | null }> {
  let current = entries
  const pending = pendingOf(entries).sort((a, b) => a.capturedAt.localeCompare(b.capturedAt))
  for (let i = 0; i < pending.length; i += batchSize) {
    try {
      current = applyResults(current, await send(pending.slice(i, i + batchSize)))
    } catch (e) {
      return { entries: current, offline: isNetworkFailure(e), error: e }
    }
  }
  return { entries: current, offline: false, error: null }
}
