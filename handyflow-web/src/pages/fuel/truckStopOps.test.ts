import { describe, it, expect } from "vitest"
import { validateReservation, isOpen, statusLabel, timeText, type Offering } from "./truckStopOps"

const offering = (o: Partial<Offering> = {}): Offering => ({ id: "o1", bookingsServiceId: "s1", serviceName: "Shower", kind: "SHOWER", depotId: null, active: true, durationMinutes: 30, price: 40, resources: [], serviceMissing: false, ...o })

describe("truckStopOps", () => {
  it("needs a service, a registration, a date and a slot", () => {
    expect(validateReservation(undefined, "", "2026-11-14", "09:00", "CY 1")).toMatch(/service/)
    expect(validateReservation(offering(), "", "2026-11-14", "09:00", " ")).toMatch(/registration/)
    expect(validateReservation(offering(), "", "", "09:00", "CY 1")).toMatch(/date/)
    expect(validateReservation(offering(), "", "2026-11-14", "", "CY 1")).toMatch(/slot/)
    expect(validateReservation(offering(), "", "2026-11-14", "09:00", "CY 1")).toBeNull()
  })
  it("a service with bays needs a bay chosen", () => {
    const o = offering({ resources: [{ id: "b1", name: "Bay 1" }] })
    expect(validateReservation(o, "", "2026-11-14", "09:00", "CY 1")).toMatch(/bay/)
    expect(validateReservation(o, "b1", "2026-11-14", "09:00", "CY 1")).toBeNull()
  })
  it("only live bookings can be cancelled or completed", () => {
    expect(isOpen("PENDING")).toBe(true); expect(isOpen("CONFIRMED")).toBe(true)
    expect(isOpen("CANCELLED")).toBe(false); expect(isOpen("COMPLETED")).toBe(false); expect(isOpen("UNKNOWN")).toBe(false)
  })
  it("labels statuses and trims times", () => {
    expect(statusLabel("NO_SHOW")).toBe("No-show"); expect(statusLabel("X")).toBe("X")
    expect(timeText("09:30:00")).toBe("09:30"); expect(timeText(null)).toBe("–")
  })
})
