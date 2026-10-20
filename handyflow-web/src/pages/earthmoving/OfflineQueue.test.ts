import { describe, it, expect } from "vitest"
import {
  loadQueue, saveQueue, makeEntry, entryProblem, applyResults, discard, syncPending, isNetworkFailure, newId, QUEUE_KEY, KEEP_FINISHED,
  type QueuedEntry,
} from "./shared/offlineQueue"

const memory = () => {
  const m = new Map<string, string>()
  return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => { m.set(k, v) }, raw: m }
}
const meter = (over: Partial<QueuedEntry> = {}, at = "2026-10-20T08:00:00.000Z"): QueuedEntry =>
  ({ ...makeEntry({ kind: "METER_READING", assetId: "a1", assetName: "D9", meterValue: 100 }, new Date(at)), ...over })

describe("offline queue", () => {
  it("gives every entry its own id, kept for life", () => {
    const a = meter(), b = meter()
    expect(a.submissionId).not.toBe(b.submissionId)
    expect(a.submissionId).toMatch(/^[0-9a-f-]{36}$/)
    expect(newId()).toMatch(/^[0-9a-f-]{36}$/)
    expect(a.state).toBe("PENDING")
    expect(a.capturedAt).toBe("2026-10-20T08:00:00.000Z")
  })

  it("checks an entry before saving it", () => {
    expect(entryProblem({ kind: "METER_READING", assetId: "", assetName: "" })).toMatch(/machine/)
    expect(entryProblem({ kind: "METER_READING", assetId: "a1", assetName: "", meterValue: null })).toMatch(/meter reading/)
    expect(entryProblem({ kind: "METER_READING", assetId: "a1", assetName: "", meterValue: -1 })).toMatch(/negative/)
    expect(entryProblem({ kind: "POSITION_READING", assetId: "a1", assetName: "", latitude: -26 })).toMatch(/both/)
    expect(entryProblem({ kind: "POSITION_READING", assetId: "a1", assetName: "", latitude: 91, longitude: 0 })).toMatch(/-90 to 90/)
    expect(entryProblem({ kind: "POSITION_READING", assetId: "a1", assetName: "", latitude: -26.2, longitude: 28 })).toBe("")
    expect(entryProblem({ kind: "METER_READING", assetId: "a1", assetName: "", meterValue: 0 })).toBe("")
  })

  it("a meter entry carries no position and a position entry no meter", () => {
    const p = makeEntry({ kind: "POSITION_READING", assetId: "a1", assetName: "D9", meterValue: 5, latitude: 1, longitude: 2, note: "  gate " })
    expect(p.meterValue).toBeNull()
    expect(p.note).toBe("gate")
    const m = makeEntry({ kind: "METER_READING", assetId: "a1", assetName: "D9", meterValue: 5, latitude: 1, longitude: 2 })
    expect(m.latitude).toBeNull()
  })

  it("survives a reload, and ignores damaged storage", () => {
    const s = memory()
    expect(saveQueue([meter()], s)).toBe(true)
    expect(loadQueue(s)).toHaveLength(1)
    s.raw.set(QUEUE_KEY, "{not json")
    expect(loadQueue(s)).toEqual([])
    s.raw.set(QUEUE_KEY, JSON.stringify([{ nonsense: true }, meter()]))
    expect(loadQueue(s)).toHaveLength(1)
  })

  it("says so when the phone will not keep the queue", () => {
    const full = { getItem: () => null, setItem: () => { throw new Error("quota") } }
    expect(saveQueue([meter()], full)).toBe(false)
    expect(loadQueue({ getItem: () => { throw new Error("blocked") }, setItem: () => {} })).toEqual([])
  })

  it("never drops a pending entry, and keeps only the latest finished ones", () => {
    const s = memory()
    const finished = Array.from({ length: KEEP_FINISHED + 5 }, (_, i) => meter({ state: "SYNCED" }, `2026-10-01T08:${String(i).padStart(2, "0")}:00.000Z`))
    const pending = meter({}, "2026-10-02T08:00:00.000Z")
    saveQueue([...finished, pending], s)
    const back = loadQueue(s)
    expect(back).toHaveLength(KEEP_FINISHED + 1)
    expect(back.some(e => e.submissionId === pending.submissionId)).toBe(true)
  })

  it("records what the server decided and leaves the rest pending", () => {
    const a = meter(), b = meter(), c = meter()
    const out = applyResults([a, b, c], [
      { submissionId: a.submissionId, status: "SYNCED", reason: null },
      { submissionId: b.submissionId, status: "CONFLICT", reason: "A newer reading was recorded" },
    ])
    expect(out.map(e => e.state)).toEqual(["SYNCED", "CONFLICT", "PENDING"])
    expect(out[1].reason).toMatch(/newer/)
  })

  it("only a pending entry can be discarded", () => {
    const a = meter(), b = meter({ state: "SYNCED" })
    expect(discard([a, b], a.submissionId)).toEqual([b])
    expect(discard([a, b], b.submissionId)).toEqual([a, b])
  })

  it("sends pending entries oldest first, in batches, and keeps the same ids", async () => {
    const e1 = meter({}, "2026-10-20T09:00:00.000Z"), e2 = meter({}, "2026-10-20T07:00:00.000Z"), e3 = meter({}, "2026-10-20T08:00:00.000Z")
    const batches: string[][] = []
    const r = await syncPending([e1, e2, e3], async batch => {
      batches.push(batch.map(x => x.submissionId))
      return batch.map(x => ({ submissionId: x.submissionId, status: "SYNCED" as const, reason: null }))
    }, 2)
    expect(batches).toEqual([[e2.submissionId, e3.submissionId], [e1.submissionId]])
    expect(r.entries.every(e => e.state === "SYNCED")).toBe(true)
    expect(r.error).toBeNull()
  })

  it("a dropped connection keeps everything pending, for the retry to send again with the same ids", async () => {
    const a = meter(), b = meter()
    const r = await syncPending([a, b], async () => { throw Object.assign(new Error("Network Error"), { code: "ERR_NETWORK" }) })
    expect(r.offline).toBe(true)
    expect(r.entries.map(e => e.state)).toEqual(["PENDING", "PENDING"])
    expect(r.entries.map(e => e.submissionId)).toEqual([a.submissionId, b.submissionId])
  })

  it("keeps the answered batch when a later batch fails", async () => {
    const entries = [meter({}, "2026-10-20T07:00:00.000Z"), meter({}, "2026-10-20T08:00:00.000Z")]
    let calls = 0
    const r = await syncPending(entries, async batch => {
      if (++calls === 2) throw { response: { status: 403 } }
      return batch.map(x => ({ submissionId: x.submissionId, status: "SYNCED" as const, reason: null }))
    }, 1)
    expect(r.entries.map(e => e.state)).toEqual(["SYNCED", "PENDING"])
    expect(r.offline).toBe(false)   // the server said no; not a connection problem
  })

  it("tells a lost connection from a server refusal", () => {
    expect(isNetworkFailure(new Error("Network Error"))).toBe(true)
    expect(isNetworkFailure({ response: { status: 503 } })).toBe(true)
    expect(isNetworkFailure({ response: { status: 403 } })).toBe(false)
    expect(isNetworkFailure({ response: { status: 400 } })).toBe(false)
  })
})
