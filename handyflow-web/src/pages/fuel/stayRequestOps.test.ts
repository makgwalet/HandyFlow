import { describe, it, expect } from "vitest"
import { contactText, onlyRequests, pendingCount, shortDay, stayText } from "./stayRequestOps"

describe("stayRequestOps", () => {
  it("reads the day from the text, not the viewer's time zone", () => {
    expect(shortDay("2026-11-14")).toBe("14 Nov")
    expect(shortDay("2026-01-01")).toBe("1 Jan")
    expect(shortDay("nonsense")).toBe("nonsense")
  })
  it("says nights in the singular and plural", () => {
    expect(stayText({ arrivalDate: "2026-11-14", departureDate: "2026-11-15", nights: 1 })).toBe("14 Nov to 15 Nov · 1 night")
    expect(stayText({ arrivalDate: "2026-11-14", departureDate: "2026-11-16", nights: 2 })).toBe("14 Nov to 16 Nov · 2 nights")
  })
  it("names the driver when given, else the portal user", () => {
    expect(contactText({ driverName: "Sipho", driverPhone: "0825550123", requestedByEmail: "a@b.co" })).toBe("Sipho · 0825550123")
    expect(contactText({ driverName: null, driverPhone: null, requestedByEmail: "a@b.co" })).toBe("a@b.co")
  })
  it("keeps only well-formed rows and counts the waiting ones", () => {
    const rows = onlyRequests([{ id: "1", vehicleRegistration: "GP 1", status: "PENDING" }, null, { id: 2 }, { id: "3", vehicleRegistration: "GP 3", status: "APPROVED" }])
    expect(rows).toHaveLength(2)
    expect(pendingCount(rows as any)).toBe(1)
    expect(onlyRequests({})).toEqual([])
  })
})
