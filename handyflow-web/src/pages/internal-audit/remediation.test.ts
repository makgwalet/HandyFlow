import { describe, it, expect } from "vitest"
import { actionStatus, dueText, filterActions, filterFindings, findingStatus, severityLabel, severityTone } from "./remediation.logic"
import type { RemediationAction, RemediationFinding } from "../../api/auditRemediation.api"

const f = (o: Partial<RemediationFinding>): RemediationFinding => ({ id: "f", engagementId: "e", engagement: "Payroll review", area: "HR", title: "Missing approvals", description: null, recommendation: null, managementResponse: null, severity: "HIGH", status: "OPEN", owner: "Sarah", dueDate: null, pastDue: false, repeat: false, actions: 1, actionsDone: 0, actionsAwaiting: 0, actionsOverdue: 0, remediationPct: 0, createdAt: null, ...o })
const a = (o: Partial<RemediationAction>): RemediationAction => ({ id: "a", findingId: "f", finding: "Missing approvals", severity: "HIGH", engagement: "Payroll review", description: "Add a second approver", owner: "Mike", dueDate: "2026-11-01", status: "OPEN", overdue: false, daysOverdue: 0, ...o })

describe("labels", () => {
  it("words statuses and severities", () => {
    expect(findingStatus("IN_PROGRESS").label).toBe("In progress")
    expect(findingStatus("ODD").label).toBe("ODD")
    expect(actionStatus("COMPLETED").label).toBe("Awaiting validation")
    expect(severityLabel("CRITICAL")).toBe("Critical")
    expect(severityLabel(null)).toBe("Unrated")
    expect(severityTone("critical")).toBe("warning")
    expect(severityTone(null)).toBe("muted")
  })
})

describe("filters", () => {
  const rows = [f({ id: "1" }), f({ id: "2", severity: "LOW", status: "CLOSED", title: "Stale accounts", engagement: "IT review" }), f({ id: "3", pastDue: true, actions: 0 }), f({ id: "4", severity: null, status: "RESOLVED" })]
  const all = { severity: "all", status: "all" as const, q: "" }
  it("narrows findings by severity, state and text", () => {
    expect(filterFindings(rows, all)).toHaveLength(4)
    expect(filterFindings(rows, { ...all, severity: "LOW" }).map(r => r.id)).toEqual(["2"])
    expect(filterFindings(rows, { ...all, severity: "UNRATED" }).map(r => r.id)).toEqual(["4"])
    expect(filterFindings(rows, { ...all, status: "open" }).map(r => r.id)).toEqual(["1", "3"])
    expect(filterFindings(rows, { ...all, status: "settled" }).map(r => r.id)).toEqual(["2", "4"])
    expect(filterFindings(rows, { ...all, status: "pastDue" }).map(r => r.id)).toEqual(["3"])
    expect(filterFindings(rows, { ...all, status: "noAction" }).map(r => r.id)).toEqual(["3"])
    expect(filterFindings(rows, { ...all, q: " IT " }).map(r => r.id)).toEqual(["2"])
  })
  it("narrows actions by state and text", () => {
    const acts = [a({ id: "1" }), a({ id: "2", overdue: true, daysOverdue: 4 }), a({ id: "3", status: "COMPLETED" }), a({ id: "4", status: "VALIDATED", description: "Reconcile monthly" })]
    const base = { status: "all" as const, q: "" }
    expect(filterActions(acts, base)).toHaveLength(4)
    expect(filterActions(acts, { ...base, status: "overdue" }).map(r => r.id)).toEqual(["2"])
    expect(filterActions(acts, { ...base, status: "open" }).map(r => r.id)).toEqual(["1", "2"])
    expect(filterActions(acts, { ...base, status: "awaiting" }).map(r => r.id)).toEqual(["3"])
    expect(filterActions(acts, { ...base, status: "validated" }).map(r => r.id)).toEqual(["4"])
    expect(filterActions(acts, { ...base, q: "reconcile" }).map(r => r.id)).toEqual(["4"])
  })
})

describe("due text", () => {
  it("says when", () => {
    expect(dueText(null, "2026-10-28")).toBe("")
    expect(dueText("2026-10-28", "2026-10-28")).toBe("Due today")
    expect(dueText("2026-10-29", "2026-10-28")).toBe("Due tomorrow")
    expect(dueText("2026-11-04", "2026-10-28")).toBe("Due in 7 days")
    expect(dueText("2026-10-27", "2026-10-28")).toBe("1 day late")
    expect(dueText("2026-10-20", "2026-10-28")).toBe("8 days late")
  })
})
