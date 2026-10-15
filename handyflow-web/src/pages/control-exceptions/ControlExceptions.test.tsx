import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/controlExceptions.api", () => ({ controlExceptionsApi: {
  detailed: vi.fn(), rate: vi.fn(), assign: vi.fn(), respond: vi.fn(), resolve: vi.fn(), followUp: vi.fn(),
  assignableUsers: vi.fn(), evidence: vi.fn(), attach: vi.fn(),
} }))
import { controlExceptionsApi as api } from "../../api/controlExceptions.api"
import { ControlExceptionsPage } from "./ControlExceptionsPage"

const base: any = {
  id: "e1", sourceModule: "scm", controlType: "THREE_WAY_MATCH", relatedEntityType: "INVOICE", relatedEntityId: "i1",
  severity: "WARNING", description: "Invoice does not match", status: "OPEN", detectedAt: "2026-10-01T08:00:00Z",
  overdue: false, followUpDue: false, timesReopened: 0, evidenceCount: 0,
}
const m = api as any

afterEach(cleanup)
beforeEach(() => {
  vi.clearAllMocks()
  m.detailed.mockResolvedValue([base])
  m.assignableUsers.mockResolvedValue([{ id: "u1", name: "Bob" }])
  m.evidence.mockResolvedValue([])
})

const manage = async () => { render(<ControlExceptionsPage />); fireEvent.click(await screen.findByText("Manage")) }

describe("Control exceptions", () => {
  it("shows unrated, overdue and follow-up flags", async () => {
    m.detailed.mockResolvedValue([{ ...base, overdue: true, timesReopened: 2 }])
    render(<ControlExceptionsPage />)
    expect(await screen.findByText("Not rated")).toBeTruthy()
    expect(screen.getByText("Overdue")).toBeTruthy()
    expect(screen.getByText("Reopened 2×")).toBeTruthy()
  })

  it("cannot resolve until the risk is rated, and needs notes of 10+ characters", async () => {
    await manage()
    expect(screen.getByText("Rate the risk first.")).toBeTruthy()
    expect((screen.getByText("Mark Resolved") as HTMLButtonElement).disabled).toBe(true)
  })

  it("saves a rating only with a reason", async () => {
    m.rate.mockResolvedValue(base)
    await manage()
    const save = screen.getByText("Save rating") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for the rating"), { target: { value: "Large and repeated supplier" } })
    fireEvent.change(screen.getByLabelText("Risk rating"), { target: { value: "HIGH" } })
    fireEvent.click(save)
    await waitFor(() => expect(m.rate).toHaveBeenCalledWith("e1", "HIGH", "Large and repeated supplier"))
  })

  it("a high-risk exception needs a follow-up date before it can be resolved", async () => {
    m.detailed.mockResolvedValue([{ ...base, riskRating: "HIGH", responseText: "Renegotiating terms" }])
    m.resolve.mockResolvedValue(base)
    await manage()
    fireEvent.change(screen.getByLabelText("Resolution notes"), { target: { value: "Credit note received in full" } })
    const btn = screen.getByText("Mark Resolved") as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Follow-up date"), { target: { value: "2099-01-01" } })
    expect(btn.disabled).toBe(false)
    fireEvent.click(btn)
    await waitFor(() => expect(m.resolve).toHaveBeenCalledWith("e1", "Credit note received in full", "2099-01-01"))
  })

  it("shows the server's reason when resolving is refused", async () => {
    m.detailed.mockResolvedValue([{ ...base, riskRating: "LOW" }])
    m.resolve.mockRejectedValue({ response: { data: { message: "Attach at least one piece of evidence" } } })
    await manage()
    fireEvent.change(screen.getByLabelText("Resolution notes"), { target: { value: "Supplier credit received" } })
    fireEvent.click(screen.getByText("Mark Resolved"))
    expect((await screen.findByRole("alert")).textContent).toContain("Attach at least one piece of evidence")
  })

  it("records a follow-up when it is due", async () => {
    m.detailed.mockResolvedValue([{ ...base, status: "RESOLVED", riskRating: "HIGH", resolutionNotes: "Done", followUpDate: "2026-10-10", followUpDue: true }])
    m.followUp.mockResolvedValue(base)
    await manage()
    fireEvent.change(screen.getByLabelText("Follow-up outcome"), { target: { value: "RECURRED" } })
    fireEvent.change(screen.getByLabelText("Follow-up note"), { target: { value: "Same supplier mismatched again" } })
    fireEvent.click(screen.getByText("Record follow-up"))
    await waitFor(() => expect(m.followUp).toHaveBeenCalledWith("e1", "RECURRED", "Same supplier mismatched again"))
  })

  it("requests the chosen scope", async () => {
    render(<ControlExceptionsPage />)
    await screen.findByText("Manage")
    fireEvent.click(screen.getByRole("tab", { name: "Follow-ups" }))
    await waitFor(() => expect(m.detailed).toHaveBeenLastCalledWith("follow-ups"))
  })
})
