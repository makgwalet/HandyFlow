import { describe, it, expect } from "vitest"
import { bookingProblem, clashMessage } from "./bookingRules"

const NOW = new Date("2026-10-07T10:00:00").getTime()
const ok = { patientId: "p", scheduledAt: "2026-10-07T11:00", durationMinutes: "30" }

describe("bookingProblem", () => {
  it("accepts a normal booking", () => expect(bookingProblem(ok, NOW)).toBeNull())
  it("needs a patient and a time", () => {
    expect(bookingProblem({ ...ok, patientId: "" }, NOW)).toMatch(/required/)
    expect(bookingProblem({ ...ok, scheduledAt: "" }, NOW)).toMatch(/required/)
  })
  it("accepts a walk-in a few minutes ago but not an hour ago", () => {
    expect(bookingProblem({ ...ok, scheduledAt: "2026-10-07T09:55" }, NOW)).toBeNull()
    expect(bookingProblem({ ...ok, scheduledAt: "2026-10-07T09:00" }, NOW)).toMatch(/past/)
  })
  it("keeps the length in range", () => {
    expect(bookingProblem({ ...ok, durationMinutes: "0" }, NOW)).toMatch(/between 5 and 480/)
    expect(bookingProblem({ ...ok, durationMinutes: "481" }, NOW)).toMatch(/between 5 and 480/)
    expect(bookingProblem({ ...ok, durationMinutes: "480" }, NOW)).toBeNull()
  })
})

describe("clashMessage", () => {
  it("returns the server's text for a 409", () =>
    expect(clashMessage({ response: { status: 409, data: { message: "Dr Lee already has an appointment" } } })).toBe("Dr Lee already has an appointment"))
  it("has a fallback text for a 409 with no message", () =>
    expect(clashMessage({ response: { status: 409 } })).toMatch(/overlapping/))
  it("is null for other errors", () => {
    expect(clashMessage({ response: { status: 400, data: { message: "x" } } })).toBeNull()
    expect(clashMessage(new Error("network"))).toBeNull()
    expect(clashMessage(undefined)).toBeNull()
  })
})
