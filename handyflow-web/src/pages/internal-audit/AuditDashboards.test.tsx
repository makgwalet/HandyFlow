import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditDashboards.api", () => ({ auditDashboardsApi: { internal: vi.fn(), external: vi.fn(), committee: vi.fn(), trends: vi.fn(), activity: vi.fn() } }))
import { auditDashboardsApi } from "../../api/auditDashboards.api"
import { InternalAuditDashboard } from "./InternalAuditDashboard"
import type { Engagement } from "./auditShared"
import { ExternalAuditDashboard } from "../settings/ExternalAuditDashboard"

const m = auditDashboardsApi as any
afterEach(cleanup)
beforeEach(() => { vi.clearAllMocks(); m.trends.mockResolvedValue({ since: null, daysAgo: null, changes: [] }); m.activity.mockResolvedValue([]) })

const internal = {
  plan: { year: 2026, status: "ACTIVE", total: 4, completed: 1, inProgress: 1, deferred: 1, planned: 1, completionPct: 33 },
  coverage: { universeSize: 4, planned: 2, audited: 1, plannedPct: 50, auditedPct: 25, notPlanned: ["Inventory", "Procurement"] },
  findingsBySeverity: [{ severity: "HIGH", open: 1, total: 2 }],
  openFindings: 3, findingsPastDue: 1, overdueActionCount: 2,
  overdueActions: [{ id: "a1", findingId: "f1", description: "Fix approvals", dueDate: "2026-10-01", daysOverdue: 14 }],
  repeatFindingCount: 1, repeatFindings: [{ id: "f2", title: "Segregation of duties", severity: "HIGH", status: "OPEN" }],
}

describe("Internal audit dashboard", () => {
  it("shows plan, coverage, findings, overdue actions and repeat findings", async () => {
    m.internal.mockResolvedValue(internal)
    render(<InternalAuditDashboard />)
    expect(await screen.findByText("33%")).toBeTruthy()
    expect(screen.getByText("1 of 3 audits (1 deferred)")).toBeTruthy()
    expect(screen.getByText("Fix approvals — due 2026-10-01 (14 days late)")).toBeTruthy()
    expect(screen.getByText("Segregation of duties (HIGH, open)")).toBeTruthy()
    expect(screen.getByText("Inventory, Procurement")).toBeTruthy()
  })
  it("handles having no plan yet", async () => {
    m.internal.mockResolvedValue({ ...internal, plan: { ...internal.plan, year: null, total: 0 }, findingsBySeverity: [], overdueActions: [], overdueActionCount: 0, repeatFindings: [], repeatFindingCount: 0 })
    render(<InternalAuditDashboard />)
    expect(await screen.findByText("No plan")).toBeTruthy()
    expect(screen.getByText("No findings yet.")).toBeTruthy()
  })
  it("shows the server's reason on failure", async () => {
    m.internal.mockRejectedValue({ response: { data: { message: "Module not enabled" } } })
    render(<InternalAuditDashboard />)
    expect((await screen.findByRole("alert")).textContent).toContain("Module not enabled")
  })
})

const external = {
  requests: { awaitingBusiness: 4, overdue: 2, dueSoon: 1, awaitingAuditor: 3, rejectedBefore: 0, approved: 5, notAvailable: 0 },
  queries: { awaitingBusiness: 2, overdue: 1, awaitingAuditor: 1, reopenedBefore: 0, resolved: 0 },
  reviewNotes: { unresolved: 6, overdue: 0 }, overdueTotal: 3,
  engagements: [{ engagementId: "g1", name: "FY2026 audit", status: "ACTIVE", openRequests: 4, overdueRequests: 2, openQueries: 2, unresolvedNotes: 6 }],
  overdueItems: [{ type: "EVIDENCE_REQUEST", reference: "ER-0003", title: "Bank statements", engagement: "FY2026 audit", dueDate: "2026-10-01", daysOverdue: 14 }],
}

describe("External audit dashboard", () => {
  it("shows the figures and the most overdue items", async () => {
    m.external.mockResolvedValue(external)
    render(<ExternalAuditDashboard engagements={[{ id: "g1", name: "FY2026 audit" }]} />)
    expect(await screen.findByText("Requests waiting on us")).toBeTruthy()
    expect(screen.getByText("ER-0003 Bank statements — FY2026 audit, 14 days late")).toBeTruthy()
    expect(screen.getByText("Waiting on the auditor").parentElement!.textContent).toContain("4")
  })
  it("asks for one engagement when chosen", async () => {
    m.external.mockResolvedValue(external)
    render(<ExternalAuditDashboard engagements={[{ id: "g1", name: "FY2026 audit" }]} />)
    await screen.findByText("Requests waiting on us")
    fireEvent.change(screen.getByLabelText("Dashboard engagement"), { target: { value: "g1" } })
    await waitFor(() => expect(m.external).toHaveBeenLastCalledWith("g1"))
  })
})

import { CommitteeReport } from "./CommitteeReport"
const committee = {
  asOf: "2026-10-15",
  plan: { year: 2026, status: "ACTIVE", committed: 3, completed: 1, deferred: 1, completionPct: 33, quarters: [{ quarter: 1, planned: 2, completed: 1 }] },
  reports: { approved: 2, awaitingSignOff: 1, closedWithoutApproval: 1, recentlyApproved: [{ engagement: "Payroll audit", approvedAt: "2026-10-01T08:00:00Z" }] },
  risk: { byLevel: [{ level: "HIGH", count: 2 }, { level: "UNASSESSED", count: 1 }], highRiskNotYetAudited: ["Treasury"] },
  findings: { openHighOrCritical: 2, pastDue: 1 },
  overdueHighRiskActionCount: 1,
  overdueHighRiskActions: [{ id: "a1", description: "Lock payment run", finding: "Open payment run", severity: "CRITICAL", owner: "Bob", dueDate: "2026-10-01", daysOverdue: 14 }],
  controls: { open: 5, unrated: 2, highOrCritical: 1, overdue: 1, followUpsDue: 0, reopenedEver: 3 },
}
describe("Audit committee report", () => {
  it("shows plan delivery, approved reports, overdue high-risk actions and the control position", async () => {
    m.committee = vi.fn().mockResolvedValue(committee)
    render(<CommitteeReport />)
    expect(await screen.findByText("Position as at 2026-10-15")).toBeTruthy()
    expect(screen.getByText("33%")).toBeTruthy()
    expect(screen.getByText("Lock payment run — Open payment run (CRITICAL), Bob; due 2026-10-01, 14 days late")).toBeTruthy()
    expect(screen.getByText("Payroll audit — 2026-10-01")).toBeTruthy()
    expect(screen.getByText("high: 2 · unassessed: 1")).toBeTruthy()
    expect(screen.getByText("High or critical risk, not yet audited this year: Treasury")).toBeTruthy()
    expect(screen.getByText("Not yet rated")).toBeTruthy()
  })
  it("says so when there is nothing to report", async () => {
    m.committee = vi.fn().mockResolvedValue({ ...committee, plan: { ...committee.plan, year: null, quarters: [] }, overdueHighRiskActionCount: 0, overdueHighRiskActions: [], reports: { ...committee.reports, recentlyApproved: [] } })
    render(<CommitteeReport />)
    expect(await screen.findByText("None overdue.")).toBeTruthy()
    expect(screen.getByText("No reports approved yet.")).toBeTruthy()
    expect(screen.getByText("No plan")).toBeTruthy()
  })
  it("shows the server's reason on failure", async () => {
    m.committee = vi.fn().mockRejectedValue({ response: { data: { message: "No access" } } })
    render(<CommitteeReport />)
    expect((await screen.findByRole("alert")).textContent).toContain("No access")
  })
})

describe("Internal audit dashboard: key dates and links", () => {
  it("lists upcoming engagement dates and goes to a section from the links", async () => {
    m.internal.mockResolvedValue(internal)
    const go = vi.fn()
    const eng = [{ id: "g1", name: "Payroll review", universeEntryName: "HR", status: "FIELDWORK", startDate: "2026-09-01", endDate: "2026-11-05" }] as unknown as Engagement[]
    render(<InternalAuditDashboard engagements={eng} onGo={go} today="2026-10-28" />)
    expect(await screen.findByText("Payroll review")).toBeTruthy()
    expect(screen.getByText("5 Nov 2026")).toBeTruthy()
    fireEvent.click(screen.getByText("Audit universe"))
    expect(go).toHaveBeenCalledWith("universe")
  })
  it("says so when nothing is coming up", async () => {
    m.internal.mockResolvedValue(internal)
    render(<InternalAuditDashboard engagements={[]} onGo={vi.fn()} today="2026-10-28" />)
    expect(await screen.findByText("No engagement starts or ends in the next 90 days.")).toBeTruthy()
  })
  it("sizes the severity bars from open findings and shows all four levels", async () => {
    m.internal.mockResolvedValue(internal)
    render(<InternalAuditDashboard />)
    expect((await screen.findByLabelText("High open findings")).getAttribute("aria-valuenow")).toBe("100")
    expect(screen.getByLabelText("Critical open findings").getAttribute("aria-valuenow")).toBe("0")
  })
})

describe("Internal audit dashboard: trends and activity", () => {
  it("shows an arrow under a tile when there is something to compare with", async () => {
    m.internal.mockResolvedValue(internal)
    m.trends.mockResolvedValue({ since: "2026-08-04", daysAgo: 85, changes: [
      { key: "openFindings", now: 3, before: 6, delta: -3 }, { key: "overdueActions", now: 2, before: 1, delta: 1 },
      { key: "engagements", now: 12, before: 9, delta: 3 }, { key: "highRiskAreas", now: 5, before: 5, delta: 0 }] })
    render(<InternalAuditDashboard />)
    expect(await screen.findByText("3 fewer vs 85 days ago")).toBeTruthy()
    expect(screen.getByText("1 more vs 85 days ago")).toBeTruthy()
    expect(screen.getByText("High-risk areas")).toBeTruthy()
  })
  it("says why there is no arrow yet", async () => {
    m.internal.mockResolvedValue(internal)
    render(<InternalAuditDashboard />)
    expect(await screen.findByText(/Trend arrows appear once/)).toBeTruthy()
  })
  it("lists recent activity with the thing it happened to", async () => {
    m.internal.mockResolvedValue(internal)
    m.activity.mockResolvedValue([{ actor: "Sarah Johnson", action: "FINDING_CREATED", itemType: "FINDING", itemId: "f1", subject: "Segregation of duties", at: new Date().toISOString() }])
    render(<InternalAuditDashboard />)
    expect(await screen.findByText("Finding raised")).toBeTruthy()
    expect(screen.getByText(": Segregation of duties")).toBeTruthy()
    expect(screen.getByText("Sarah Johnson")).toBeTruthy()
  })
  it("still shows the dashboard when trends and activity cannot be loaded", async () => {
    m.internal.mockResolvedValue(internal)
    m.trends.mockRejectedValue(new Error("x")); m.activity.mockRejectedValue(new Error("x"))
    render(<InternalAuditDashboard />)
    expect(await screen.findByText("33%")).toBeTruthy()
    expect(screen.queryByText("Recent activity")).toBeNull()
  })
})
