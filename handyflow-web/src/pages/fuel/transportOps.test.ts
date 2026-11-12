import { describe, it, expect, beforeEach } from "vitest"
import { parseCapacities, validateTanker, autoSplit, validateAllocation, enqueue, fleetOptionText, readQueue, dequeue, flushQueue, isNetworkError, type Compartment } from "./transportOps"

const mem = () => { const m: Record<string, string> = {}; return { getItem: (k: string) => m[k] ?? null, setItem: (k: string, v: string) => { m[k] = v } } }
const comps: Compartment[] = [{ id: "c1", number: 1, capacityLitres: 10000 }, { id: "c2", number: 2, capacityLitres: 6000 }]

describe("transportOps", () => {
  it("parses compartment capacities", () => {
    expect(parseCapacities("10000, 6000").values).toEqual([10000, 6000])
    expect(parseCapacities("").error).toBeTruthy()
    expect(parseCapacities("10000 0").error).toBeTruthy()
    expect(parseCapacities("a").error).toBeTruthy()
    expect(parseCapacities(Array(13).fill("1").join(",")).error).toMatch(/12/)
    expect(validateTanker("", "100")).toMatch(/registration/)
    expect(validateTanker("GP 1", "100")).toBeNull()
  })
  it("auto-splits in compartment order and reports when it does not fit", () => {
    expect(autoSplit(12000, comps)).toEqual({ c1: "10000", c2: "2000" })
    expect(autoSplit(4000, comps)).toEqual({ c1: "4000" })
    expect(autoSplit(17000, comps)).toBeNull()
  })
  it("validates an allocation", () => {
    expect(validateAllocation("", "d", 100, {}, comps)).toMatch(/tanker/)
    expect(validateAllocation("t", "", 100, {}, comps)).toMatch(/driver/)
    expect(validateAllocation("t", "d", 12000, { c1: "10000", c2: "1000" }, comps)).toMatch(/add up/)
    expect(validateAllocation("t", "d", 12000, { c1: "5000", c2: "7000" }, comps)).toMatch(/holds 6000/)
    expect(validateAllocation("t", "d", 12000, { c1: "10000", c2: "2000" }, comps)).toBeNull()
  })
  describe("offline queue", () => {
    let s: ReturnType<typeof mem>
    beforeEach(() => { s = mem() })
    it("keeps the same request id on the queued item and replaces an older entry for the same delivery", () => {
      enqueue("d1", { litresDelivered: 100 }, "id-1", s)
      enqueue("d1", { litresDelivered: 120 }, "id-2", s)
      const q = readQueue(s)
      expect(q).toHaveLength(1)
      expect(q[0].body.clientRequestId).toBe("id-2")
      dequeue("d1", s); expect(readQueue(s)).toHaveLength(0)
    })
    it("survives corrupt storage", () => { s.setItem("fuel-driver-queue-v1", "{{"); expect(readQueue(s)).toEqual([]) })
    it("sends in order, stops at a network failure and keeps the rest", async () => {
      enqueue("d1", {}, "a", s); enqueue("d2", {}, "b", s); enqueue("d3", {}, "c", s)
      const calls: string[] = []
      const r = await flushQueue(async i => { calls.push(i.deliveryId); if (i.deliveryId === "d2") throw new Error("offline") }, s)
      expect(calls).toEqual(["d1", "d2"])
      expect(r.sent).toEqual(["d1"])
      expect(readQueue(s).map(x => x.deliveryId)).toEqual(["d2", "d3"])
    })
    it("drops an item the server refused, reports it, and carries on", async () => {
      enqueue("d1", {}, "a", s); enqueue("d2", {}, "b", s)
      const r = await flushQueue(async i => { if (i.deliveryId === "d1") throw { response: { data: { message: "Already delivered" } } } }, s)
      expect(r.refused).toEqual([{ deliveryId: "d1", message: "Already delivered" }])
      expect(r.sent).toEqual(["d2"])
      expect(readQueue(s)).toHaveLength(0)
    })
    it("tells network errors from server answers", () => { expect(isNetworkError(new Error("x"))).toBe(true); expect(isNetworkError({ response: { status: 409 } })).toBe(false) })
  })
})

describe("fleetOptionText (C03)", () => {
  it("shows a free vehicle by its label", () => {
    expect(fleetOptionText({ id: "1", registration: "CY 1", label: "CY 1 · Volvo FM", linkedTankerRegistration: null })).toBe("CY 1 · Volvo FM")
  })
  it("says when a vehicle already backs a tanker", () => {
    expect(fleetOptionText({ id: "1", registration: "CY 1", label: "CY 1 · Volvo FM", linkedTankerRegistration: "CY 1" })).toContain("already a tanker")
  })
})
