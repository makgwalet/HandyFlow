import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("../../api/auditRemediation.api", () => ({ auditRemediationApi: { board: vi.fn() } }))
import { auditRemediationApi } from "../../api/auditRemediation.api"
import { RemediationSection } from "./RemediationSection"

const m = auditRemediationApi as unknown as { board: ReturnType<typeof vi.fn> }
afterEach(cleanup)
beforeEach(() => vi.clearAllMocks())
const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RemediationSection today="2026-10-28" /></QueryClientProvider>)

const board = {
  summary: { findings: 3, open: 1, inProgress: 1, resolved: 0, closed: 1, openCritical: 1, openHigh: 1, openMedium: 0, openLow: 0, pastDue: 1, openWithoutAction: 1, actions: 2, actionsOpen: 1, actionsAwaiting: 0, actionsValidated: 1, actionsOverdue: 1, remediationPct: 50 },
  findings: [
    { id: "f1", engagementId: "e1", engagement: "Payroll review", area: "HR", title: "Missing approvals", description: "No second approver", recommendation: "Add one", managementResponse: null, severity: "CRITICAL", status: "OPEN", owner: "Sarah Johnson", dueDate: "2026-10-20", pastDue: true, repeat: true, actions: 2, actionsDone: 1, actionsAwaiting: 0, actionsOverdue: 1, remediationPct: 50, createdAt: null },
    { id: "f2", engagementId: "e1", engagement: "Payroll review", area: "HR", title: "Stale accounts", description: null, recommendation: null, managementResponse: null, severity: "HIGH", status: "IN_PROGRESS", owner: null, dueDate: null, pastDue: false, repeat: false, actions: 0, actionsDone: 0, actionsAwaiting: 0, actionsOverdue: 0, remediationPct: 0, createdAt: null },
    { id: "f3", engagementId: "e2", engagement: "VAT", area: "Tax", title: "Late returns", description: null, recommendation: null, managementResponse: null, severity: "LOW", status: "CLOSED", owner: null, dueDate: null, pastDue: false, repeat: false, actions: 0, actionsDone: 0, actionsAwaiting: 0, actionsOverdue: 0, remediationPct: 0, createdAt: null },
  ],
  actions: [
    { id: "a1", findingId: "f1", finding: "Missing approvals", severity: "CRITICAL", engagement: "Payroll review", description: "Add a second approver", owner: "Mike Brown", dueDate: "2026-10-24", status: "OPEN", overdue: true, daysOverdue: 4 },
    { id: "a2", findingId: "f1", finding: "Missing approvals", severity: "CRITICAL", engagement: "Payroll review", description: "Train approvers", owner: "Mike Brown", dueDate: "2026-09-01", status: "VALIDATED", overdue: false, daysOverdue: 0 },
  ],
}

describe("Findings & Remediation", () => {
  it("shows the summary and, by default, the open findings only", async () => {
    m.board.mockResolvedValue(board)
    mount()
    expect(await screen.findByText("Missing approvals")).toBeTruthy()
    expect(screen.getByText("Stale accounts")).toBeTruthy()
    expect(screen.queryByText("Late returns")).toBeNull()
    expect(screen.getByText("1 critical, 1 high")).toBeTruthy()
    expect(screen.getByText("8 days late")).toBeTruthy()
  })
  it("shows every finding when asked, and filters by text", async () => {
    m.board.mockResolvedValue(board)
    mount()
    await screen.findByText("Missing approvals")
    fireEvent.change(screen.getByLabelText("Finding status"), { target: { value: "all" } })
    expect(screen.getByText("Late returns")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Search findings"), { target: { value: "vat" } })
    expect(screen.queryByText("Missing approvals")).toBeNull()
    expect(screen.getByText("Late returns")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Search findings"), { target: { value: "zzz" } })
    expect(screen.getByText("No findings match these filters.")).toBeTruthy()
  })
  it("opens a finding with its actions and closes it again", async () => {
    m.board.mockResolvedValue(board)
    mount()
    fireEvent.click(await screen.findByLabelText("View Missing approvals"))
    const panel = screen.getByRole("dialog")
    expect(panel.textContent).toContain("No second approver")
    expect(panel.textContent).toContain("Add a second approver")
    expect(panel.textContent).toContain("Actions (2)")
    fireEvent.click(screen.getByLabelText("Close"))
    expect(screen.queryByRole("dialog")).toBeNull()
  })
  it("lists actions with the overdue one flagged", async () => {
    m.board.mockResolvedValue(board)
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: /Actions/ }))
    expect(screen.getByText("Add a second approver")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Action status"), { target: { value: "overdue" } })
    expect(screen.queryByText("Train approvers")).toBeNull()
  })
  it("says so when there is nothing, and when the load fails", async () => {
    m.board.mockResolvedValue({ ...board, findings: [], actions: [], summary: { ...board.summary, findings: 0 } })
    mount()
    expect(await screen.findByText("No findings have been raised yet.")).toBeTruthy()
    cleanup()
    m.board.mockRejectedValue(new Error("x"))
    mount()
    expect((await screen.findByRole("alert")).textContent).toContain("could not be loaded")
  })
})
