import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditReports.api", () => ({ auditReportsApi: {
  templates: vi.fn(), list: vi.fn(), get: vi.fn(), create: vi.fn(), newVersion: vi.fn(), editSection: vi.fn(), addPerson: vi.fn(), removePerson: vi.fn(),
  startReview: vi.fn(), raiseItem: vi.fn(), respond: vi.fn(), closeReview: vi.fn(), reopen: vi.fn(), issue: vi.fn(), users: vi.fn(), engagements: vi.fn(),
} }))
import { auditReportsApi } from "../../api/auditReports.api"
import { ReportsSection } from "./ReportsSection"

const m = auditReportsApi as any
afterEach(cleanup)

const summary = (status: string, extra: any = {}) => ({ id: "r1", engagementId: "g1", engagementName: "Payroll", version: 1, title: "Payroll report", template: "STANDARD", status, createdAt: "2026-10-01T08:00:00Z", openItems: 0, ...extra })
const view = (status: string, extra: any = {}) => ({
  summary: summary(status), contentHash: null, manager: true, canEdit: status === "DRAFT" || status === "FACTUAL_REVIEW",
  sections: [{ position: 1, title: "Summary", body: "All good", autoFindings: false }], items: [], people: [], ...extra,
})

beforeEach(() => {
  vi.clearAllMocks()
  m.templates.mockResolvedValue([{ id: "STANDARD", label: "Standard audit report", sections: ["Summary"] }])
  m.engagements.mockResolvedValue([{ id: "g1", name: "Payroll" }])
  m.users.mockResolvedValue([{ id: "u1", name: "Bob" }])
  m.list.mockResolvedValue([summary("DRAFT")])
})

const open = async (v: any) => { m.get.mockResolvedValue(v); render(<ReportsSection />); fireEvent.click(await screen.findByText("Open")) }

describe("Reports", () => {
  it("lists reports and starts a new one from a template", async () => {
    m.create.mockResolvedValue(view("DRAFT"))
    m.get.mockResolvedValue(view("DRAFT"))
    render(<ReportsSection />)
    await screen.findByText(/Payroll report/)
    fireEvent.change(screen.getByLabelText("Engagement for the report"), { target: { value: "g1" } })
    fireEvent.click(screen.getByText("Start report"))
    await waitFor(() => expect(m.create).toHaveBeenCalledWith("g1", "STANDARD", ""))
  })

  it("a manager can save a section and start the factual-accuracy review", async () => {
    m.editSection.mockResolvedValue(view("DRAFT"))
    m.startReview.mockResolvedValue(view("FACTUAL_REVIEW"))
    await open(view("DRAFT"))
    fireEvent.change(await screen.findByLabelText("Section 1 text"), { target: { value: "Headcount is 41" } })
    fireEvent.click(screen.getByText("Save section"))
    await waitFor(() => expect(m.editSection).toHaveBeenCalledWith("r1", 1, "Summary", "Headcount is 41"))
    fireEvent.click(screen.getByText("Start factual-accuracy review"))
    await waitFor(() => expect(m.startReview).toHaveBeenCalledWith("r1"))
  })

  it("shows the server's reason when the review cannot start", async () => {
    m.startReview.mockRejectedValue({ response: { data: { message: "Name at least one reviewer before starting the factual-accuracy review" } } })
    await open(view("DRAFT"))
    fireEvent.click(await screen.findByText("Start factual-accuracy review"))
    expect((await screen.findByRole("alert")).textContent).toContain("Name at least one reviewer")
  })

  it("answering a comment needs 10 characters", async () => {
    m.respond.mockResolvedValue(view("FACTUAL_REVIEW"))
    await open(view("FACTUAL_REVIEW", { items: [{ id: "i1", sectionPosition: 1, comment: "Headcount is 41, not 14", raisedByName: "Bob", raisedAt: "2026-10-02T08:00:00Z", status: "OPEN" }] }))
    const accept = await screen.findByText("Accept and answer") as HTMLButtonElement
    expect(accept.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Answer to comment"), { target: { value: "Corrected in section 1" } })
    expect(accept.disabled).toBe(false)
    fireEvent.click(accept)
    await waitFor(() => expect(m.respond).toHaveBeenCalledWith("r1", "i1", "ACCEPTED", "Corrected in section 1"))
  })

  it("a reviewer sees the draft and can raise a comment but has no management buttons", async () => {
    m.raiseItem.mockResolvedValue(view("FACTUAL_REVIEW", { manager: false, canEdit: false }))
    await open(view("FACTUAL_REVIEW", { manager: false, canEdit: false }))
    await screen.findByText("All good")
    expect(screen.queryByText("Close review")).toBeNull()
    expect(screen.queryByText("Issue report")).toBeNull()
    fireEvent.change(screen.getByLabelText("New comment"), { target: { value: "The headcount is wrong" } })
    fireEvent.click(screen.getByText("Raise comment"))
    await waitFor(() => expect(m.raiseItem).toHaveBeenCalledWith("r1", null, "The headcount is wrong"))
  })

  it("an issued report offers a new version, and recipients and their reading counts are shown to managers", async () => {
    m.newVersion.mockResolvedValue(view("DRAFT"))
    await open(view("ISSUED", { contentHash: "abcdef1234567890", people: [{ id: "p1", kind: "RECIPIENT", userId: "u1", name: "Bob", addedAt: "2026-10-03T00:00:00Z", viewCount: 3 }] }))
    expect(await screen.findByText(/read 3 times/)).toBeTruthy()
    expect(screen.getByText(/fingerprint abcdef123456/)).toBeTruthy()
    fireEvent.click(screen.getByText("Start a new version"))
    await waitFor(() => expect(m.newVersion).toHaveBeenCalledWith("r1"))
  })

  it("a recipient sees the text only", async () => {
    await open(view("ISSUED", { manager: false, canEdit: false }))
    await screen.findByText("All good")
    expect(screen.queryByText("Start a new version")).toBeNull()
    expect(screen.queryByText("Reviewers and recipients")).toBeNull()
  })
})
