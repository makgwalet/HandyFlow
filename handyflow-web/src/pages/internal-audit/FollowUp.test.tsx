import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditFollowUp.api", () => ({ auditFollowUpApi: { list: vi.fn(), get: vi.fn(), start: vi.fn(), assess: vi.fn(), engagements: vi.fn() } }))
import { auditFollowUpApi } from "../../api/auditFollowUp.api"
import { FollowUpSection } from "./FollowUpSection"

const m = auditFollowUpApi as any
afterEach(cleanup)

const counts = (x: any = {}) => ({ total: 2, assessed: 1, implemented: 1, partial: 0, notImplemented: 0, noLongerApplicable: 0, implementedPct: 50, complete: false, ...x })
const summary = (x: any = {}) => ({ engagementId: "e2", engagementName: "Payroll 2026", priorEngagementId: "e1", priorEngagementName: "Payroll 2025", counts: counts(), ...x })
const view = (items: any[] = [
  { findingId: "f1", title: "Late approvals", severity: "HIGH", outcome: "IMPLEMENTED", note: "Checked the new step.", assessedByName: "Thandi" },
  { findingId: "f2", title: "Shared logins", severity: "MEDIUM" }]) => ({ summary: summary(), items })

beforeEach(() => {
  vi.clearAllMocks()
  m.list.mockResolvedValue([summary()])
  m.engagements.mockResolvedValue([{ id: "e1", name: "Payroll 2025" }, { id: "e2", name: "Payroll 2026" }, { id: "e3", name: "Stock take" }])
})

describe("Follow-ups", () => {
  it("lists follow-ups with percentage implemented", async () => {
    render(<FollowUpSection />)
    expect(await screen.findByText(/follows Payroll 2025 · 50% implemented · 1\/2 assessed/)).toBeTruthy()
  })

  it("does not offer an engagement that is already a follow-up", async () => {
    render(<FollowUpSection />)
    await screen.findByText("Start a follow-up")
    const opts = Array.from((screen.getByLabelText("Follow-up engagement") as HTMLSelectElement).options).map(o => o.text)
    expect(opts).not.toContain("Payroll 2026")
    expect(opts).toContain("Stock take")
  })

  it("starts a follow-up with both engagements", async () => {
    m.start.mockResolvedValue(view()); m.get.mockResolvedValue(view())
    render(<FollowUpSection />)
    await screen.findByText("Start a follow-up")
    fireEvent.change(screen.getByLabelText("Follow-up engagement"), { target: { value: "e3" } })
    fireEvent.change(screen.getByLabelText("Earlier engagement"), { target: { value: "e1" } })
    fireEvent.click(screen.getByText("Start follow-up"))
    await waitFor(() => expect(m.start).toHaveBeenCalledWith("e3", "e1"))
  })

  it("will not save an outcome until the note is long enough, then saves it", async () => {
    m.get.mockResolvedValue(view())
    m.assess.mockResolvedValue(view([{ findingId: "f2", title: "Shared logins", outcome: "NOT_IMPLEMENTED", note: "Still shared on the till." }]))
    render(<FollowUpSection />)
    fireEvent.click(await screen.findByText("Open"))
    await screen.findByText("Late approvals")
    fireEvent.click(screen.getAllByText("Assess")[0])
    const save = screen.getByText("Save outcome") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Outcome for Shared logins"), { target: { value: "NOT_IMPLEMENTED" } })
    fireEvent.change(screen.getByLabelText("What was checked for Shared logins"), { target: { value: "Still shared on the till." } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(m.assess).toHaveBeenCalledWith("e2", "f2", "NOT_IMPLEMENTED", "Still shared on the till."))
  })

  it("shows the server's message when saving fails", async () => {
    m.get.mockResolvedValue(view())
    m.assess.mockRejectedValue({ response: { data: { message: "Say what was checked" } } })
    render(<FollowUpSection />)
    fireEvent.click(await screen.findByText("Open"))
    await screen.findByText("Late approvals")
    fireEvent.click(screen.getByText("Revise"))
    fireEvent.click(screen.getByText("Save outcome"))
    expect(await screen.findByText("Say what was checked")).toBeTruthy()
  })
})
