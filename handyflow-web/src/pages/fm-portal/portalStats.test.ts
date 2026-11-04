import { describe, it, expect } from "vitest"
import { activityFeed, donutArcs, filterRequests, nextInvoiceDue, owingInvoices, statusSlices } from "./portalStats"
import type { FmPortalInvoice, FmPortalQuote, FmPortalWo } from "../../api/fmPortal.api"

const wo = (n: number, status: string, over: Partial<FmPortalWo> = {}): FmPortalWo => ({ id: "w" + n, workOrderNumber: "WO-" + n, siteId: "s1", assetId: null, category: "CORRECTIVE", priority: "NORMAL", status, description: "Pump leaking", reportedBy: null, scheduledDate: null, completedAt: null, createdAt: "2026-11-01T08:00:00Z", declineMessage: null, resolutionDueAt: null, resolutionSla: null, ...over })
const inv = (n: number, over: Partial<FmPortalInvoice> = {}): FmPortalInvoice => ({ id: "i" + n, invoiceNumber: "INV-" + n, periodStart: "2026-10-01", periodEnd: "2026-10-31", issueDate: "2026-11-01", dueDate: "2026-12-01", subtotal: 100, vatAmount: 0, total: 100, amountPaid: 0, creditedAmount: 0, balance: 100, status: "SENT", ...over })

describe("statusSlices", () => {
  it("folds the server's statuses into four groups", () => {
    const { slices, total } = statusSlices({ REQUESTED: 2, ASSIGNED: 1, IN_PROGRESS: 3, ON_HOLD: 1, COMPLETED: 5, CANCELLED: 1, REJECTED: 1 })
    expect(Object.fromEntries(slices.map(s => [s.key, s.count]))).toEqual({ open: 3, progress: 4, completed: 5, cancelled: 2 })
    expect(total).toBe(14)
  })
  it("copes with nothing", () => expect(statusSlices(null).total).toBe(0))
})

describe("donutArcs", () => {
  it("shares the circle by count, with no arc for an empty group", () => {
    const { slices } = statusSlices({ REQUESTED: 1, COMPLETED: 3 })
    const arcs = donutArcs(slices, 40), circ = 2 * Math.PI * 40
    expect(arcs).toHaveLength(2)
    expect(arcs[0].dash).toBeCloseTo(circ / 4); expect(arcs[1].dash).toBeCloseTo(circ * 3 / 4)
    expect(arcs[1].offset).toBeCloseTo(-circ / 4)
    expect(donutArcs(statusSlices({}).slices, 40)).toEqual([])
  })
})

describe("filterRequests", () => {
  const rows = [wo(1, "REQUESTED"), wo(2, "COMPLETED", { siteId: "s2", description: "Door seal" }), wo(3, "IN_PROGRESS")]
  it("filters by site, status group and words", () => {
    expect(filterRequests(rows, { search: "", siteId: "s2", group: "" }).map(r => r.id)).toEqual(["w2"])
    expect(filterRequests(rows, { search: "", siteId: "", group: "progress" }).map(r => r.id)).toEqual(["w3"])
    expect(filterRequests(rows, { search: "DOOR", siteId: "", group: "" }).map(r => r.id)).toEqual(["w2"])
    expect(filterRequests(rows, { search: "wo-1", siteId: "", group: "" })).toHaveLength(1)
    expect(filterRequests(rows, { search: "", siteId: "", group: "" })).toHaveLength(3)
  })
})

describe("activityFeed", () => {
  it("lists newest first across requests, quotes and invoices", () => {
    const q: FmPortalQuote = { id: "q1", quoteNumber: "Q-1", siteId: null, title: "New compressor", description: null, status: "SENT", canRespond: true, validUntil: null, subtotal: 1, vatRate: 0, vatAmount: 0, total: 1, sentAt: "2026-11-03T09:00:00Z", decidedAt: null, decisionNote: null, lines: [] }
    const feed = activityFeed([wo(1, "COMPLETED", { completedAt: "2026-11-04T10:00:00Z" })], [q], [inv(1)])
    expect(feed.map(f => f.title)).toEqual(["WO-1 completed", "Q-1 sent to you", "INV-1 issued"])
  })
  it("keeps to the limit", () => expect(activityFeed(Array.from({ length: 9 }, (_, i) => wo(i, "REQUESTED")), [], [], 4)).toHaveLength(4))
})

describe("invoices owing", () => {
  it("ignores paid, draft, cancelled and credited, and picks the earliest due", () => {
    const list = [inv(1, { dueDate: "2026-12-10" }), inv(2, { dueDate: "2026-12-01" }), inv(3, { status: "PAID", balance: 0 }), inv(4, { status: "DRAFT" }), inv(5, { status: "CREDITED" })]
    expect(owingInvoices(list).map(i => i.id)).toEqual(["i2", "i1"])
    expect(nextInvoiceDue(list)?.id).toBe("i2")
    expect(nextInvoiceDue([])).toBeNull()
  })
})

import { mapsUrl, orderPhotos, serviceReport } from "./portalStats"
describe("map link, service report, photo order", () => {
  it("builds a search link from the address and none when empty", () => {
    expect(mapsUrl({ street: "1 Main Rd", city: "Pretoria", postalCode: "" })).toBe("https://www.google.com/maps/search/?api=1&query=1%20Main%20Rd%2C%20Pretoria")
    expect(mapsUrl({})).toBeNull()
    expect(mapsUrl(null)).toBeNull()
  })
  it("summarises work done, speed and SLA", () => {
    const wo = (o: Record<string, unknown>) => ({ id: "x", workOrderNumber: "W", siteId: "s", assetId: null, category: "PLUMBING", priority: "HIGH", status: "COMPLETED", description: "d", reportedBy: null, scheduledDate: null, completedAt: "2026-10-12T00:00:00Z", createdAt: "2026-10-10T00:00:00Z", declineMessage: null, resolutionDueAt: null, resolutionSla: "MET", ...o }) as never
    const r = serviceReport([wo({}), wo({ completedAt: "2026-10-14T00:00:00Z", resolutionSla: "BREACHED", category: "ELECTRICAL" }), wo({ status: "OPEN", completedAt: null, resolutionSla: "ON_TRACK" }), wo({ status: "CANCELLED" })], new Date("2026-11-04T00:00:00Z"))
    expect(r.total).toBe(3); expect(r.open).toBe(1); expect(r.completed).toBe(2); expect(r.completedLast90).toBe(2)
    expect(r.avgDaysToFix).toBe(3); expect(r.slaMetPct).toBe(50); expect(r.byCategory[0]).toEqual({ category: "PLUMBING", count: 2 })
  })
  it("gives null averages with nothing to average", () => {
    const r = serviceReport([], new Date()); expect(r.avgDaysToFix).toBeNull(); expect(r.slaMetPct).toBeNull(); expect(r.total).toBe(0)
  })
  it("orders photos before, general, after", () => {
    expect(orderPhotos([{ kind: "AFTER_PHOTO" }, { kind: "PHOTO" }, { kind: "BEFORE_PHOTO" }]).map(p => p.kind)).toEqual(["BEFORE_PHOTO", "PHOTO", "AFTER_PHOTO"])
  })
})
