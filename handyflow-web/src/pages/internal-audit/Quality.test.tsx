import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditQuality.api", () => ({ auditQualityApi: {
  list: vi.fn(), get: vi.fn(), request: vi.fn(), answer: vi.fn(), complete: vi.fn(), cancel: vi.fn(), metrics: vi.fn(), users: vi.fn(), engagements: vi.fn(),
} }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: any) => sel({ user: { userId: "rev1" } }) }))
import { auditQualityApi } from "../../api/auditQuality.api"
import { QualitySection } from "./QualitySection"

const m = auditQualityApi as any
afterEach(cleanup)

const metrics = { reviewsCompleted: 4, reviewsLive: 1, satisfactoryPct: 50, byConclusion: [], averageDaysToComplete: 3.5,
  mostFailedChecks: [{ code: "F4", question: "Have all workpapers been reviewed and signed off?", count: 2 }], issuedWithoutCompletedReview: 2,
  reviewNotesRaised: 10, reviewNotesCleared: 7, reviewNotesClearedPct: 70, findingsReopened: 1, findingsTotal: 8, findingsReopenedPct: 13 }
const summary = (extra: any = {}) => ({ id: "q1", engagementId: "g1", engagementName: "Payroll", reviewerName: "Rev Iewer", reviewerId: "rev1", requestedByName: "Lead", requestedAt: "2026-10-01T00:00:00Z", status: "IN_PROGRESS", answered: 1, total: 2, noAnswers: 0, ...extra })
const view = (extra: any = {}) => ({ summary: summary(extra.summary), checks: [
  { code: "P1", area: "PLANNING", question: "Are the objectives documented?", result: "YES" },
  { code: "F4", area: "FIELDWORK", question: "Have all workpapers been signed off?" }], ...extra })

beforeEach(() => {
  vi.clearAllMocks()
  m.metrics.mockResolvedValue(metrics)
  m.list.mockResolvedValue([summary()])
  m.engagements.mockResolvedValue([{ id: "g1", name: "Payroll" }])
  m.users.mockResolvedValue([{ id: "rev1", name: "Rev Iewer" }])
})

const open = async (v: any) => { m.get.mockResolvedValue(v); render(<QualitySection />); fireEvent.click(await screen.findByText("Open")) }

describe("Quality", () => {
  it("shows the quality figures, failed checks and the unreviewed issued reports", async () => {
    render(<QualitySection />)
    expect(await screen.findByText("Issued without a completed review")).toBeTruthy()
    expect(screen.getByText("F4: Have all workpapers been reviewed and signed off? — 2×")).toBeTruthy()
    expect(screen.getByText("70%")).toBeTruthy()
  })

  it("requests a review with the chosen reviewer", async () => {
    m.request.mockResolvedValue(view())
    m.get.mockResolvedValue(view())
    render(<QualitySection />)
    await screen.findByText("Request a quality review")
    fireEvent.change(screen.getByLabelText("Engagement to review"), { target: { value: "g1" } })
    fireEvent.change(screen.getByLabelText("Reviewer"), { target: { value: "rev1" } })
    fireEvent.click(screen.getByText("Request review"))
    await waitFor(() => expect(m.request).toHaveBeenCalledWith("g1", "rev1"))
  })

  it("the reviewer answers a 'No' only with an explanation", async () => {
    m.answer.mockResolvedValue(view())
    await open(view())
    const no = await screen.findByText("F4 No") as HTMLButtonElement
    expect(no.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Note for F4"), { target: { value: "Two workpapers have no sign-off" } })
    expect(no.disabled).toBe(false)
    fireEvent.click(no)
    await waitFor(() => expect(m.answer).toHaveBeenCalledWith("q1", "F4", "NO", "Two workpapers have no sign-off"))
  })

  it("someone who is not the reviewer sees the answers but no way to answer", async () => {
    await open(view({ summary: { reviewerId: "someone-else" } }))
    await screen.findByText(/Are the objectives documented/)
    expect(screen.queryByText("F4 No")).toBeNull()
    expect(screen.queryByText("Complete review")).toBeNull()
  })

  it("shows the server's reason when the review cannot be concluded", async () => {
    m.complete.mockRejectedValue({ response: { data: { message: "1 checklist item is still unanswered" } } })
    await open(view())
    fireEvent.change(await screen.findByLabelText("Conclusion note"), { target: { value: "Looks fine overall" } })
    fireEvent.click(screen.getByText("Complete review"))
    expect((await screen.findByRole("alert")).textContent).toContain("still unanswered")
  })
})
