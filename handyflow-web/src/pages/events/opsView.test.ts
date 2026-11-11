import { describe, it, expect } from "vitest"
import { canCancelSession, canCompleteSession, canDeleteSession, canEditSession, canStartSession, checklistBody, checklistProblem, clock, delayText, doneWord, groupByPhase, lateText, phaseText, progressText, sessionBody, sessionProblem, shiftProblem, templateText, typeText, type ChecklistItem } from "./opsView"

const item = (o: Partial<ChecklistItem>): ChecklistItem => ({ id: "i", phase: "SETUP", title: "t", description: null, assignedTo: null, dueAt: null, required: true, done: false, doneByName: null, doneAt: null, sortOrder: 0, overdue: false, ...o })

describe("sessions", () => {
  it("follows the server's state rules", () => {
    expect(canStartSession("SCHEDULED", "LIVE")).toBe(true); expect(canStartSession("SCHEDULED", "PUBLISHED")).toBe(false); expect(canStartSession("IN_PROGRESS", "LIVE")).toBe(false)
    expect(canCompleteSession("IN_PROGRESS")).toBe(true); expect(canCompleteSession("SCHEDULED")).toBe(false)
    expect(canEditSession("SCHEDULED")).toBe(true); expect(canEditSession("IN_PROGRESS")).toBe(true); expect(canEditSession("COMPLETED")).toBe(false); expect(canEditSession("CANCELLED")).toBe(false)
    expect(canCancelSession("SCHEDULED")).toBe(true); expect(canCancelSession("COMPLETED")).toBe(false)
    expect(canDeleteSession("SCHEDULED")).toBe(true); expect(canDeleteSession("CANCELLED")).toBe(true); expect(canDeleteSession("IN_PROGRESS")).toBe(false); expect(canDeleteSession("COMPLETED")).toBe(false)
  })
  it("checks the form and builds the body", () => {
    const ok = { title: "Keynote", sessionType: "KEYNOTE", startAt: "2026-12-01T09:00", endAt: "2026-12-01T10:00" }
    expect(sessionProblem(ok)).toBeNull()
    expect(sessionProblem({ ...ok, title: " " })).toMatch(/title/); expect(sessionProblem({ ...ok, sessionType: "" })).toMatch(/type/)
    expect(sessionProblem({ ...ok, endAt: ok.startAt })).toMatch(/end after/); expect(sessionProblem({ ...ok, startAt: "" })).toMatch(/starts and ends/)
    expect(sessionBody({ ...ok, room: " Hall A " })).toEqual({ title: "Keynote", description: null, sessionType: "KEYNOTE", room: "Hall A", speaker: null, startAt: "2026-12-01T09:00:00", endAt: "2026-12-01T10:00:00", notes: null })
  })
  it("checks the 'running late' minutes", () => {
    expect(shiftProblem("20")).toBeNull(); expect(shiftProblem("-15")).toBeNull(); expect(shiftProblem("240")).toBeNull(); expect(shiftProblem("-120")).toBeNull()
    for (const bad of ["", "0", "abc", "1.5", "241", "-121"]) expect(shiftProblem(bad)).not.toBeNull()
  })
  it("words lateness and delay", () => {
    expect(lateText(0)).toBe("On time"); expect(lateText(20)).toBe("Running 20 min late"); expect(lateText(-5)).toBe("Running 5 min early")
    expect(delayText(null)).toBe(""); expect(delayText(0)).toBe("started on time"); expect(delayText(7)).toBe("started 7 min late"); expect(delayText(-3)).toBe("started 3 min early")
    expect(clock("2026-12-01T09:30:00")).toBe("09:30"); expect(clock(null)).toBe(""); expect(typeText("AV_TECH")).toBe("Av tech")
  })
})

describe("checklist", () => {
  it("groups by phase in lifecycle order and drops empty phases", () => {
    const g = groupByPhase([item({ id: "a", phase: "EVENT_DAY", sortOrder: 2 }), item({ id: "b", phase: "PRE_EVENT" }), item({ id: "c", phase: "EVENT_DAY", sortOrder: 1 }), item({ id: "d", phase: "CONTINGENCY" })])
    expect(g.map(x => x.phase)).toEqual(["PRE_EVENT", "EVENT_DAY", "CONTINGENCY"]); expect(g[1].items.map(i => i.id)).toEqual(["c", "a"])
  })
  it("calls a reviewed contingency plan 'Reviewed', not 'Done'", () => {
    expect(doneWord("CONTINGENCY", true)).toBe("Reviewed"); expect(doneWord("CONTINGENCY", false)).toBe("Not reviewed"); expect(doneWord("SETUP", true)).toBe("Done"); expect(doneWord("SETUP", false)).toBe("To do")
    expect(phaseText("CONTINGENCY")).toBe("Contingency plans"); expect(phaseText("PRE_EVENT")).toBe("Pre event")
  })
  it("checks and builds items; required defaults to true", () => {
    expect(checklistProblem({ phase: "SETUP", title: "Test PA" })).toBeNull(); expect(checklistProblem({ phase: "X", title: "a" })).toMatch(/phase/); expect(checklistProblem({ phase: "SETUP", title: " " })).toMatch(/title/)
    expect(checklistBody({ phase: "SETUP", title: " Test PA " })).toEqual({ phase: "SETUP", title: "Test PA", description: null, assignedTo: null, dueAt: null, required: true, sortOrder: null })
    expect(checklistBody({ phase: "SETUP", title: "x", required: false, dueAt: "2026-12-01T08:00" })).toMatchObject({ required: false, dueAt: "2026-12-01T08:00:00" })
  })
  it("words progress and template results", () => {
    expect(progressText({ total: 0, done: 0, requiredOpen: 0 })).toBe("Nothing yet"); expect(progressText({ total: 5, done: 3, requiredOpen: 1 })).toBe("3 of 5 done, 1 required still open"); expect(progressText({ total: 2, done: 2, requiredOpen: 0 })).toBe("2 of 2 done")
    expect(templateText({ added: 0, skipped: 4 })).toMatch(/already there/); expect(templateText({ added: 1, skipped: 0 })).toBe("Added 1 item."); expect(templateText({ added: 6, skipped: 2 })).toBe("Added 6 items; 2 already existed.")
  })
})
