import { describe, it, expect } from "vitest"
import { canAcknowledge, canClose, canEscalate, canReopen, canResolve, contactBody, contactProblem, coverageGaps, higherSeverities, incidentBody, incidentProblem, minutesText, overdueText, sortIncidents, staffBody, staffProblem, telHref, type Incident } from "./peopleOps"

const inc = (o: Partial<Incident> = {}): Incident => ({ id: "i", reference: "INC-1", title: "t", description: null, category: "OTHER", severity: "LOW", status: "OPEN", location: null, reportedByName: null, assignedToName: null, escalationLevel: 0, acknowledgedAt: null, resolvedAt: null, resolution: null, createdAt: "2026-12-01T10:00:00", minutesOpen: 5, acknowledgementOverdue: false, resolutionOverdue: false, timeline: null, ...o })

describe("staff", () => {
  const ok = { fullName: "Sipho", kind: "STAFF", role: "STEWARD", shiftStart: "2026-12-01T08:00", shiftEnd: "2026-12-01T16:00" }
  it("validates the shift", () => {
    expect(staffProblem(ok)).toBeNull()
    expect(staffProblem({ ...ok, fullName: " " })).toMatch(/name/)
    expect(staffProblem({ ...ok, role: "" })).toMatch(/role/)
    expect(staffProblem({ ...ok, email: "nope" })).toMatch(/email/)
    expect(staffProblem({ ...ok, shiftEnd: ok.shiftStart })).toMatch(/after/)
  })
  it("sends optional fields as null and a null user", () => {
    const b = staffBody({ ...ok, phone: "", email: "", post: "", notes: "" })
    expect(b).toMatchObject({ userId: null, phone: null, email: null, post: null, notes: null })
  })
  it("finds the roles with people still to arrive", () => {
    expect(coverageGaps([{ role: "STEWARD", rostered: 4, confirmed: 3, arrived: 1, noShow: 0 }, { role: "SECURITY", rostered: 2, confirmed: 2, arrived: 2, noShow: 0 }])).toEqual([{ role: "STEWARD", missing: 3 }])
  })
})

describe("incidents", () => {
  it("only unresolved incidents can be handled", () => {
    expect(canResolve("OPEN")).toBe(true); expect(canResolve("RESOLVED")).toBe(false)
    expect(canClose("RESOLVED")).toBe(true); expect(canClose("OPEN")).toBe(false)
    expect(canReopen("CLOSED")).toBe(true); expect(canReopen("OPEN")).toBe(false)
  })
  it("acknowledge once, escalate to level 3 at most", () => {
    expect(canAcknowledge(inc())).toBe(true); expect(canAcknowledge(inc({ acknowledgedAt: "x" }))).toBe(false)
    expect(canEscalate(inc({ escalationLevel: 2 }))).toBe(true); expect(canEscalate(inc({ escalationLevel: 3 }))).toBe(false); expect(canEscalate(inc({ status: "RESOLVED" }))).toBe(false)
  })
  it("offers only higher severities", () => { expect(higherSeverities("MEDIUM")).toEqual(["HIGH", "CRITICAL"]); expect(higherSeverities("CRITICAL")).toEqual([]) })
  it("says what is overdue, and nothing once resolved", () => {
    expect(overdueText(inc({ acknowledgementOverdue: true }))).toBe("Nobody has taken this yet")
    expect(overdueText(inc({ resolutionOverdue: true }))).toBe("Past its resolve target")
    expect(overdueText(inc({ acknowledgementOverdue: true, resolutionOverdue: true }))).toMatch(/Nobody.*resolve/)
    expect(overdueText(inc({ status: "RESOLVED", resolutionOverdue: true }))).toBeNull()
  })
  it("formats minutes", () => { expect(minutesText(0)).toBe("less than a minute"); expect(minutesText(45)).toBe("45 min"); expect(minutesText(120)).toBe("2 h"); expect(minutesText(135)).toBe("2 h 15 min") })
  it("sorts unresolved first, then severity, then oldest", () => {
    const l = sortIncidents([inc({ id: "a", status: "RESOLVED", severity: "CRITICAL" }), inc({ id: "b", severity: "LOW" }), inc({ id: "c", severity: "HIGH" }), inc({ id: "d", severity: "HIGH", createdAt: "2026-12-01T09:00:00" })])
    expect(l.map(x => x.id)).toEqual(["d", "c", "b", "a"])
  })
  it("validates and builds a report", () => {
    expect(incidentProblem({ title: "", category: "MEDICAL", severity: "HIGH" })).toMatch(/what happened/)
    expect(incidentProblem({ title: "Fall", category: "", severity: "HIGH" })).toMatch(/category/)
    expect(incidentProblem({ title: "Fall", category: "MEDICAL", severity: "HIGH" })).toBeNull()
    expect(incidentBody({ title: " Fall ", category: "MEDICAL", severity: "HIGH", location: "", description: "" })).toMatchObject({ title: "Fall", location: null, description: null })
  })
})

describe("contacts", () => {
  it("validates and builds", () => {
    expect(contactProblem({ contactType: "", name: "x", phone: "1" })).toMatch(/type/)
    expect(contactProblem({ contactType: "FIRE", name: "x", phone: " " })).toMatch(/phone/)
    expect(contactProblem({ contactType: "FIRE", name: "x", phone: "10177" })).toBeNull()
    expect(contactBody({ contactType: "FIRE", name: " Fire ", phone: " 10177 ", organisation: "", alternatePhone: "", email: "", notes: "" })).toMatchObject({ name: "Fire", phone: "10177", email: null })
  })
  it("makes dialable links", () => { expect(telHref("+27 (82) 123-4567")).toBe("tel:+27821234567") })
})
