import { describe, it, expect } from "vitest"
import { actionLabel, canCancel, canComplete, canEdit, canGoLive, canPostpone, canPublish, eventBody, eventFormProblem, formFromEvent, postponeProblem, shiftEnd, toApiValue, toInputValue } from "./eventActions"

const base = formFromEvent({ title: "Summit", eventType: "CONFERENCE", startDatetime: "2026-12-01T09:00:00", endDatetime: "2026-12-01T17:00:00", isFree: true, isPrivate: false })

describe("which actions an event offers", () => {
  it("publishes only drafts and goes live only once published", () => {
    expect(canPublish("DRAFT")).toBe(true); expect(canPublish("PUBLISHED")).toBe(false)
    expect(canGoLive("PUBLISHED")).toBe(true); expect(canGoLive("SOLD_OUT")).toBe(true); expect(canGoLive("DRAFT")).toBe(false)
    expect(canComplete("LIVE")).toBe(true); expect(canComplete("PUBLISHED")).toBe(false)
  })
  it("edits and cancels until the event is completed or cancelled", () => {
    for (const s of ["DRAFT", "PUBLISHED", "SOLD_OUT", "LIVE"]) { expect(canEdit(s)).toBe(true); expect(canCancel(s)).toBe(true) }
    for (const s of ["COMPLETED", "CANCELLED"]) { expect(canEdit(s)).toBe(false); expect(canCancel(s)).toBe(false) }
  })
  it("postpones only before the event goes live", () => {
    expect(canPostpone("DRAFT")).toBe(true); expect(canPostpone("SOLD_OUT")).toBe(true)
    expect(canPostpone("LIVE")).toBe(false); expect(canPostpone("COMPLETED")).toBe(false)
  })
})

describe("date helpers", () => {
  it("round-trips between the API and datetime-local values", () => {
    expect(toInputValue("2026-12-01T09:00:00")).toBe("2026-12-01T09:00")
    expect(toInputValue("2026-12-01T09:00:00.123456")).toBe("2026-12-01T09:00")
    expect(toInputValue(null)).toBe("")
    expect(toApiValue("2026-12-01T09:00")).toBe("2026-12-01T09:00:00")
    expect(toApiValue("")).toBeNull()
  })
  it("moves the end with the start so the event keeps its length", () => {
    expect(shiftEnd("2026-12-01T09:00", "2026-12-01T17:00", "2026-12-08T10:30")).toBe("2026-12-08T18:30")
    expect(shiftEnd("", "2026-12-01T17:00", "2026-12-08T10:30")).toBe("2026-12-01T17:00")
  })
})

describe("event form checks", () => {
  it("accepts a complete form", () => expect(eventFormProblem(base)).toBeNull())
  it("needs a title, dates in order and a deadline before the start", () => {
    expect(eventFormProblem({ ...base, title: "  " })).toMatch(/title/)
    expect(eventFormProblem({ ...base, startDatetime: "" })).toMatch(/starts and ends/)
    expect(eventFormProblem({ ...base, endDatetime: base.startDatetime })).toMatch(/end after/)
    expect(eventFormProblem({ ...base, registrationDeadline: "2026-12-02T00:00" })).toMatch(/before the event starts/)
  })
  it("capacity is empty or a whole number above zero", () => {
    expect(eventFormProblem({ ...base, venueCapacity: "" })).toBeNull()
    expect(eventFormProblem({ ...base, venueCapacity: "250" })).toBeNull()
    for (const bad of ["0", "-5", "12.5", "lots"]) expect(eventFormProblem({ ...base, venueCapacity: bad })).toMatch(/capacity/)
  })
  it("builds the body with nulls for empty fields and a numeric capacity", () => {
    const b = eventBody({ ...base, title: " Summit ", venueCapacity: "300", venueName: "", registrationDeadline: "2026-11-30T12:00" })
    expect(b).toMatchObject({ title: "Summit", venueCapacity: 300, venueName: null, description: null, notes: null, startDatetime: "2026-12-01T09:00:00", registrationDeadline: "2026-11-30T12:00:00" })
    expect(eventBody(base).venueCapacity).toBeNull()
  })
})

describe("postpone checks", () => {
  const cur = { start: "2026-12-01T09:00", end: "2026-12-01T17:00" }
  it("needs new dates in order, a change, and a reason", () => {
    expect(postponeProblem("", "", "weather", cur)).toMatch(/new start/i)
    expect(postponeProblem("2026-12-08T09:00", "2026-12-08T08:00", "weather", cur)).toMatch(/end after/)
    expect(postponeProblem(cur.start, cur.end, "weather", cur)).toMatch(/same as the current/)
    expect(postponeProblem("2026-12-08T09:00", "2026-12-08T17:00", "abc", cur)).toMatch(/why/)
    expect(postponeProblem("2026-12-08T09:00", "2026-12-08T17:00", "venue flooded", cur)).toBeNull()
  })
})

it("shows audit actions as words", () => expect(actionLabel("EVENT_WENT_LIVE")).toBe("Event went live"))
