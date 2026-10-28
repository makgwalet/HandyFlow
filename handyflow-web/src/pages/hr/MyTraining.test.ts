import { describe, it, expect } from "vitest"
import { canDownload, expiryNote, orderBookings, type MyBooking } from "./myTraining.logic"

const b = (over: Partial<MyBooking>): MyBooking => ({ id: "b", courseTitle: "First Aid", startDate: "2026-11-10", endDate: "2026-11-10", venue: null, status: "ENROLLED", statusLabel: "Booked", score: null, passed: null, ...over })

describe("certificate wording", () => {
  it("tells the person how long is left", () => {
    expect(expiryNote({ state: "VALID", expiryDate: "2027-06-30", daysLeft: 200 })).toBe("Valid until 2027-06-30.")
    expect(expiryNote({ state: "EXPIRING", expiryDate: "2026-11-20", daysLeft: 12 })).toBe("Expires in 12 days (2026-11-20).")
    expect(expiryNote({ state: "EXPIRING", expiryDate: "2026-11-09", daysLeft: 1 })).toBe("Expires tomorrow (2026-11-09).")
    expect(expiryNote({ state: "EXPIRING", expiryDate: "2026-11-08", daysLeft: 0 })).toBe("Expires today (2026-11-08).")
  })
  it("handles lapsed, withdrawn and undated certificates", () => {
    expect(expiryNote({ state: "EXPIRED", expiryDate: "2026-09-01", daysLeft: -57 })).toMatch(/Expired on 2026-09-01.*refresher/)
    expect(expiryNote({ state: "REVOKED", expiryDate: "2027-01-01", daysLeft: null })).toBe("This certificate was withdrawn.")
    expect(expiryNote({ state: "VALID", expiryDate: null, daysLeft: null })).toBe("Does not expire.")
  })
  it("offers no PDF for a withdrawn certificate", () => {
    expect(canDownload({ state: "REVOKED" })).toBe(false)
    expect(canDownload({ state: "EXPIRED" })).toBe(true)
  })
})

describe("booking order", () => {
  it("puts coming sessions first, soonest first, then the past, latest first", () => {
    const rows = [
      b({ id: "old", startDate: "2026-01-10", status: "COMPLETED" }),
      b({ id: "later", startDate: "2026-12-01" }),
      b({ id: "soon", startDate: "2026-11-05" }),
      b({ id: "older", startDate: "2025-05-01", status: "ATTENDED" }),
    ]
    expect(orderBookings(rows, "2026-11-01").map(r => r.id)).toEqual(["soon", "later", "old", "older"])
  })
  it("does not treat a cancelled future session as coming", () => {
    expect(orderBookings([b({ id: "c", status: "CANCELLED", startDate: "2026-12-01" }), b({ id: "e", startDate: "2026-11-05" })], "2026-11-01").map(r => r.id)).toEqual(["e", "c"])
  })
})
