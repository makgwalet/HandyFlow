import { describe, it, expect, beforeEach } from "vitest"
import { enqueue, readQueue, remove, retry, flushQueue, isNetworkError } from "./offlineQueue"

const mem = () => { const m: Record<string, string> = {}; return { getItem: (k: string) => m[k] ?? null, setItem: (k: string, v: string) => { m[k] = v }, removeItem: (k: string) => { delete m[k] } } }
const item = (id: string, kind: "START" | "COMPLETE" | "EVIDENCE", deliveryId: string) => ({ id, kind, deliveryId, path: `/p/${deliveryId}/${kind}`, body: { clientRequestId: id }, label: kind })

describe("offlineQueue (C05)", () => {
  let s: ReturnType<typeof mem>
  beforeEach(() => { s = mem() })

  it("keeps each item's own request id and replaces an earlier completion for the same load, but not evidence", () => {
    enqueue(item("a", "COMPLETE", "d1"), s); enqueue(item("b", "COMPLETE", "d1"), s)
    enqueue(item("e1", "EVIDENCE", "d1"), s); enqueue(item("e2", "EVIDENCE", "d1"), s)
    expect(readQueue(s).map(x => x.id)).toEqual(["b", "e1", "e2"])
  })
  it("brings over completions saved by the earlier app version, once", () => {
    s.setItem("fuel-driver-queue-v1", JSON.stringify([{ deliveryId: "d9", clientRequestId: "old", body: { litresDelivered: 5, clientRequestId: "old" }, queuedAt: "2026-11-01T00:00:00Z" }]))
    const q = readQueue(s)
    expect(q).toHaveLength(1); expect(q[0]).toMatchObject({ id: "old", kind: "COMPLETE", path: "/api/v1/fuel/driver/loads/d9/complete", status: "PENDING" })
    expect(readQueue(s)).toHaveLength(1)
    expect(s.getItem("fuel-driver-queue-v1")).toBeNull()
  })
  it("survives corrupt storage", () => { s.setItem("fuel-driver-queue-v2", "{{"); expect(readQueue(s)).toEqual([]) })

  it("sends in order, stops at a network failure and keeps everything left", async () => {
    enqueue(item("a", "START", "d1"), s); enqueue(item("b", "COMPLETE", "d1"), s); enqueue(item("c", "START", "d2"), s)
    const calls: string[] = []
    const r = await flushQueue(async i => { calls.push(i.id); if (i.id === "b") throw new Error("offline") }, s)
    expect(calls).toEqual(["a", "b"]); expect(r.stoppedOffline).toBe(true)
    expect(r.sent.map(x => x.id)).toEqual(["a"])
    expect(readQueue(s).map(x => x.id)).toEqual(["b", "c"])
  })
  it("a refusal marks only that item FAILED, keeps it, and holds back later items for the same load", async () => {
    enqueue(item("a", "START", "d1"), s); enqueue(item("b", "COMPLETE", "d1"), s); enqueue(item("c", "START", "d2"), s)
    const calls: string[] = []
    const r = await flushQueue(async i => { calls.push(i.id); if (i.id === "a") throw { response: { data: { message: "Not yours" } } } }, s)
    expect(calls).toEqual(["a", "c"])
    expect(r.failed).toEqual([{ item: expect.objectContaining({ id: "a" }), message: "Not yours" }])
    const q = readQueue(s)
    expect(q.map(x => x.id)).toEqual(["a", "b"])
    expect(q[0]).toMatchObject({ status: "FAILED", message: "Not yours", attempts: 1 })
  })
  it("a failed item stays blocked on later runs until retried or discarded", async () => {
    enqueue(item("a", "START", "d1"), s); enqueue(item("b", "COMPLETE", "d1"), s)
    await flushQueue(async i => { if (i.id === "a") throw { response: { data: {} } } }, s)
    const calls: string[] = []
    await flushQueue(async i => { calls.push(i.id) }, s)
    expect(calls).toEqual([])
    retry("a", s)
    await flushQueue(async i => { calls.push(i.id) }, s)
    expect(calls).toEqual(["a", "b"]); expect(readQueue(s)).toEqual([])
  })
  it("discarding a failed item frees the ones behind it", async () => {
    enqueue(item("a", "START", "d1"), s); enqueue(item("b", "COMPLETE", "d1"), s)
    await flushQueue(async i => { if (i.id === "a") throw { response: { data: {} } } }, s)
    remove("a", s)
    const r = await flushQueue(async () => {}, s)
    expect(r.sent.map(x => x.id)).toEqual(["b"])
  })
  it("treats a refused start as done when the caller says the load has already moved on", async () => {
    enqueue(item("a", "START", "d1"), s)
    const r = await flushQueue(async () => { throw { response: { data: { message: "Already dispatched" } } } }, s, async () => true)
    expect(r.sent.map(x => x.id)).toEqual(["a"]); expect(r.failed).toEqual([]); expect(readQueue(s)).toEqual([])
  })
  it("tells network errors from server answers", () => { expect(isNetworkError(new Error("x"))).toBe(true); expect(isNetworkError({ response: { status: 409 } })).toBe(false) })
})
