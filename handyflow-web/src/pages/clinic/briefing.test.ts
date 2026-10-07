import { describe, it, expect } from "vitest"
import { ago, bmi, fmtDay, recallText, startPlan } from "./briefing"

describe("ago", () => {
  it("speaks in the unit a clinician thinks in", () => {
    expect(ago(null)).toBeNull()
    expect(ago(0)).toBe("Today")
    expect(ago(1)).toBe("Yesterday")
    expect(ago(12)).toBe("12 days ago")
    expect(ago(146)).toBe("4 months ago")
    expect(ago(800)).toBe("2 years ago")
  })
})

describe("recallText", () => {
  it("tells overdue, due today, upcoming and none apart", () => {
    expect(recallText(null)).toBeNull()
    expect(recallText({ dueDate: "2026-10-01", overdueDays: 7, due: true })).toBe(`Due ${fmtDay("2026-10-01T12:00:00+02:00")} (7 days overdue)`)
    expect(recallText({ dueDate: "2026-10-07", overdueDays: 1, due: true })).toMatch(/1 day overdue/)
    expect(recallText({ dueDate: "2026-10-08", overdueDays: 0, due: true })).toBe("Due today")
    expect(recallText({ dueDate: "2026-10-20", overdueDays: 0, due: false })).toMatch(/^Due 20 Oct 2026$/)
  })
})

describe("bmi", () => {
  it("is null unless both numbers are usable", () => {
    expect(bmi(72, 170)).toBe(24.9)
    expect(bmi(null, 170)).toBeNull()
    expect(bmi(72, 0)).toBeNull()
  })
})

describe("startPlan", () => {
  const now = new Date("2026-10-08T08:00:00Z")   // 10:00 in Johannesburg
  const a = (id: string, status: string, at: string) => ({ id, status, scheduledAt: at })

  it("is a walk-in when nothing is booked today (other days and finished ones do not count)", () => {
    const p = startPlan([a("1", "SCHEDULED", "2026-10-09T08:00:00Z"), a("2", "COMPLETED", "2026-10-08T07:00:00Z"), a("3", "CANCELLED", "2026-10-08T07:30:00Z")], now)
    expect(p).toEqual({ kind: "walk-in", appt: null, steps: ["check_in", "start"] })
  })
  it("checks a scheduled appointment in before starting it, because the server will not start it directly", () => {
    const p = startPlan([a("1", "SCHEDULED", "2026-10-08T09:00:00Z")], now)
    expect(p.kind).toBe("start")
    expect(p.steps).toEqual(["check_in", "start"])
  })
  it("starts a patient who has already been checked in or triaged", () => {
    expect(startPlan([a("1", "CHECKED_IN", "2026-10-08T07:00:00Z")], now).steps).toEqual(["start"])
    expect(startPlan([a("1", "TRIAGED", "2026-10-08T07:00:00Z")], now).steps).toEqual(["start"])
  })
  it("resumes one already in progress without calling anything", () => {
    const p = startPlan([a("1", "SCHEDULED", "2026-10-08T06:00:00Z"), a("2", "IN_PROGRESS", "2026-10-08T07:00:00Z")], now)
    expect(p).toMatchObject({ kind: "resume", steps: [] })
    expect(p.appt?.id).toBe("2")
  })
  it("prefers the one furthest along, then the earliest", () => {
    const p = startPlan([a("1", "SCHEDULED", "2026-10-08T05:00:00Z"), a("2", "CONFIRMED", "2026-10-08T09:00:00Z"), a("3", "CONFIRMED", "2026-10-08T07:00:00Z")], now)
    expect(p.appt?.id).toBe("3")
  })
  it("reads 'today' on the clinic's calendar, not UTC", () => {
    // 23:30 SAST on 8 Oct is 21:30 UTC on 8 Oct; 00:30 SAST on 9 Oct is 22:30 UTC on 8 Oct - tomorrow for the clinic.
    expect(startPlan([a("1", "SCHEDULED", "2026-10-08T22:30:00Z")], now).kind).toBe("walk-in")
    expect(startPlan([a("1", "SCHEDULED", "2026-10-08T21:30:00Z")], now).kind).toBe("start")
  })
})
