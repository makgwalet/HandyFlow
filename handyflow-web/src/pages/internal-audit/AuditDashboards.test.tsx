import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditDashboards.api", () => ({ auditDashboardsApi: { internal: vi.fn(), external: vi.fn() } }))
import { auditDashboardsApi } from "../../api/auditDashboards.api"
import { InternalAuditDashboard } from "./InternalAuditDashboard"
import { ExternalAuditDashboard } from "../settings/ExternalAuditDashboard"

const m = auditDashboardsApi as any
afterEach(cleanup)
beforeEach(() => vi.clearAllMocks())

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
