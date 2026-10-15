import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditMonitoring.api", () => ({ auditMonitoringApi: { list: vi.fn(), scan: vi.fn(), review: vi.fn(), findings: vi.fn() } }))
import { auditMonitoringApi } from "../../api/auditMonitoring.api"
import { MonitoringSection } from "./MonitoringSection"

const m = auditMonitoringApi as any
afterEach(cleanup)

const alert = (x: any = {}) => ({ id: "a1", rule: "DUPLICATE_BANK_PAYMENT", severity: "HIGH", subjectType: "BANK_TRANSACTION", subjectIds: ["t1", "t2"], summary: "2 payments of R8200.00 went out with the same reference", detail: "Reference: ACME INV 5", status: "NEW", firstSeen: "", lastSeen: "", ...x })
const data = (alerts: any[], x: any = {}) => ({ counts: { open: alerts.filter(a => a.status === "NEW" || a.status === "IN_REVIEW").length, high: 1, medium: 0, low: 0, inReview: 0, dismissed: 0, escalated: 0, ...x }, alerts, notice: "These are patterns that deserve a look, not findings." })

beforeEach(() => {
  vi.clearAllMocks()
  m.list.mockResolvedValue(data([alert(), alert({ id: "a2", status: "DISMISSED", summary: "Old one", disposition: "Split on purpose.", reviewedByName: "Rev" })]))
  m.findings.mockResolvedValue([{ id: "f1", title: "Payroll: late approvals" }])
})

describe("Monitoring", () => {
  it("shows the notice, the counts and only open alerts first", async () => {
    render(<MonitoringSection />)
    expect(await screen.findByText(/not findings/)).toBeTruthy()
    expect(screen.getByText(/2 payments of R8200.00/)).toBeTruthy()
    expect(screen.queryByText(/Old one/)).toBeNull()
    fireEvent.click(screen.getByText("Closed"))
    expect(screen.getByText(/Old one/)).toBeTruthy()
    expect(screen.getByText("Rev: Split on purpose.")).toBeTruthy()
    expect(screen.queryByText("Review")).toBeNull()
  })

  it("scans and reports what it found", async () => {
    m.scan.mockResolvedValue({ found: 3, newAlerts: 2, seenAgain: 1, byRule: {}, journalsChecked: 40, bankPaymentsChecked: 12, peopleChecked: 5 })
    render(<MonitoringSection />)
    fireEvent.click(await screen.findByText("Scan now"))
    expect(await screen.findByText(/2 new, 1 already known \(40 journals, 12 bank payments, 5 people checked\)/)).toBeTruthy()
    expect(m.list).toHaveBeenCalledTimes(2)
  })

  it("will not dismiss without a reason, then dismisses with one", async () => {
    m.review.mockResolvedValue(data([]))
    render(<MonitoringSection />)
    fireEvent.click(await screen.findByText("Review"))
    fireEvent.change(screen.getByLabelText("Decision"), { target: { value: "DISMISSED" } })
    const save = screen.getByText("Save") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Paid in two parts on purpose." } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(m.review).toHaveBeenCalledWith("a1", "DISMISSED", "Paid in two parts on purpose.", null))
  })

  it("taking an alert up needs no reason", async () => {
    m.review.mockResolvedValue(data([alert({ status: "IN_REVIEW" })]))
    render(<MonitoringSection />)
    fireEvent.click(await screen.findByText("Review"))
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(m.review).toHaveBeenCalledWith("a1", "IN_REVIEW", "", null))
  })

  it("escalating can link a finding", async () => {
    m.review.mockResolvedValue(data([]))
    render(<MonitoringSection />)
    fireEvent.click(await screen.findByText("Review"))
    fireEvent.change(screen.getByLabelText("Decision"), { target: { value: "ESCALATED" } })
    fireEvent.change(screen.getByLabelText("Finding"), { target: { value: "f1" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Needs the audit team to look." } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(m.review).toHaveBeenCalledWith("a1", "ESCALATED", "Needs the audit team to look.", "f1"))
  })

  it("shows the server's message, such as being one of the people the alert is about", async () => {
    m.review.mockRejectedValue({ response: { data: { message: "You are one of the people this alert is about, so someone else must review it" } } })
    render(<MonitoringSection />)
    fireEvent.click(await screen.findByText("Review"))
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText(/someone else must review it/)).toBeTruthy()
  })
})
