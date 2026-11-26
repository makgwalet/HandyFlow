import { describe, it, expect } from "vitest"
import { bookBody, bookProblem, bookingState, bookingWarning, emptyBooking, overrideProblem, releaseProblem, type Booking } from "./resourceBookingRules"

const b = (over: Partial<Booking> = {}): Booking => ({
  id: "b1", slotId: "s1", resourceId: "r1", resourceNo: "RES-00001", resourceName: "Welding rig", resourceType: "EQUIPMENT", resourceStatus: "IN_SERVICE", quantity: 1, status: "BOOKED",
  clashNote: null, overrideReason: null, overrideAt: null, releasedReason: null, releasedAt: null, slotStatus: "SCHEDULED", clashesNow: false, clashNowNote: null, venueNote: null, createdAt: "", ...over,
})

describe("booking form", () => {
  it("checks the resource and the quantity", () => {
    expect(bookProblem(emptyBooking())).toBe("Choose the resource")
    expect(bookProblem({ ...emptyBooking(), resourceId: "r1", quantity: "0" })).toBe("Book at least one")
    expect(bookProblem({ ...emptyBooking(), resourceId: "r1", quantity: "1.5" })).toBe("Book at least one")
    expect(bookProblem({ ...emptyBooking(), resourceId: "r1", quantity: "2" })).toBeNull()
  })
  it("sends numbers and null for a blank approval", () => {
    expect(bookBody({ resourceId: "r1", quantity: "2", overrideReason: "  " })).toEqual({ resourceId: "r1", quantity: 2, overrideReason: null })
  })
  it("needs written reasons", () => {
    expect(overrideProblem("short")).toContain("at least 10")
    expect(overrideProblem("Rig is split between two groups")).toBeNull()
    expect(releaseProblem("short")).toContain("at least 10")
    expect(releaseProblem("The practical was moved")).toBeNull()
  })
})
describe("state and warnings", () => {
  it("names the state", () => {
    expect(bookingState(b())).toBe("Booked")
    expect(bookingState(b({ status: "RELEASED" }))).toBe("Released")
    expect(bookingState(b({ slotStatus: "CANCELLED" }))).toBe("Slot cancelled")
  })
  it("prompts but never calls it a finding", () => {
    expect(bookingWarning(b())).toBeNull()
    expect(bookingWarning(b({ clashesNow: true, clashNowNote: "Welding rig: 1 of 1 already booked" }))).toContain("a prompt to check, not a finding")
    expect(bookingWarning(b({ resourceStatus: "UNDER_REPAIR" }))).toContain("no longer in service")
    expect(bookingWarning(b({ status: "RELEASED", resourceStatus: "UNDER_REPAIR" }))).toBeNull()
  })
})
