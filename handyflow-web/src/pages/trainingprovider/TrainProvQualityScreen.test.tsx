// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvQualityTab from "./TrainProvQualityTab"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const tpl = { id: "t1", templateNo: "AUT-00001", name: "Classroom check", scopeType: "GENERAL", status: "ACTIVE", retiredReason: null, latestVersionNo: 1, latestVersionId: "tv1", items: ["Register kept up to date", "Fire exits clear"], versionCount: 1 }
const summary = { id: "a1", auditNo: "AUD-00001", title: "Term one classroom audit", scopeType: "GENERAL", targetName: null, plannedOn: "2020-03-01", status: "IN_PROGRESS", items: 2, answered: 0, issues: 0, openActions: 0 }
const detail = (over: object = {}) => ({
  ...summary, leadUserId: "u1", startedOn: "2020-03-01", completedOn: null, completedAlone: false, completedAloneReason: null, summary: null, cancelledReason: null,
  templateName: "Classroom check", templateVersionNo: 1, note: "An audit records what the auditor saw, in their own judgement. It does not show that anyone or anything meets a standard.",
  items: [{ id: "i1", position: 1, question: "Register kept up to date", outcome: null, note: null, answeredAt: null }, { id: "i2", position: 2, question: "Fire exits clear", outcome: null, note: null, answeredAt: null }],
  actions: [], ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
let current = detail()

beforeEach(() => {
  get.mockReset(); post.mockReset(); current = detail()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /templates$/.test(url) ? [tpl] : /quality-audits\/a1$/.test(url) ? current : /quality-audits$/.test(url) ? [summary] : [] } }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("quality audits", () => {
  it("lists audits with the plain note that an audit shows nothing is compliant", async () => {
    wrap(<TrainProvQualityTab />)
    expect(await screen.findByText(/It does not show that anyone or anything meets a standard/)).toBeTruthy()
    expect(await screen.findByText("Term one classroom audit")).toBeTruthy()
    expect(screen.getByText(/0 of 2 answered/)).toBeTruthy()
  })

  it("shows checklists and will not save one with too few items", async () => {
    wrap(<TrainProvQualityTab />)
    fireEvent.click(await screen.findByText("Checklists"))
    expect(await screen.findByText("Classroom check")).toBeTruthy()
    fireEvent.click(screen.getByText("Add a checklist"))
    fireEvent.change(screen.getByLabelText("Checklist name"), { target: { value: "Site check" } })
    fireEvent.change(screen.getByLabelText("Checklist items"), { target: { value: "" } })
    fireEvent.click(screen.getByText("Save checklist"))
    expect((await screen.findAllByRole("alert")).length).toBeGreaterThan(0)
    expect(post).not.toHaveBeenCalled()
  })

  it("needs a note when an item is an issue", async () => {
    wrap(<TrainProvQualityTab />)
    fireEvent.click(await screen.findByText("Term one classroom audit"))
    fireEvent.click((await screen.findAllByText("Answer"))[0])
    fireEvent.change(screen.getByLabelText("Outcome for item 1"), { target: { value: "ISSUE" } })
    fireEvent.click(screen.getByText("Save answer"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Note for item 1"), { target: { value: "Register two days behind" } })
    fireEvent.click(screen.getByText("Save answer"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/items\/i1\/answer$/), { outcome: "ISSUE", note: "Register two days behind" }))
  })

  it("will not complete an audit while items are unanswered", async () => {
    wrap(<TrainProvQualityTab />)
    fireEvent.click(await screen.findByText("Term one classroom audit"))
    fireEvent.click(await screen.findByText("Complete the audit"))
    fireEvent.change(screen.getByLabelText("Audit summary"), { target: { value: "Everything was looked at" } })
    fireEvent.click(screen.getByText("Complete audit"))
    expect((await screen.findByRole("alert")).textContent).toContain("2 item(s) are not answered yet")
    expect(post).not.toHaveBeenCalled()
  })

  it("completes by the lead alone only with a written reason", async () => {
    current = detail({
      items: [{ id: "i1", position: 1, question: "Register kept up to date", outcome: "NO_ISSUE", note: null, answeredAt: "x" }, { id: "i2", position: 2, question: "Fire exits clear", outcome: "NOTE", note: "Boxes near door seen", answeredAt: "x" }],
    })
    wrap(<TrainProvQualityTab />)
    fireEvent.click(await screen.findByText("Term one classroom audit"))
    fireEvent.click(await screen.findByText("Complete the audit"))
    fireEvent.change(screen.getByLabelText("Audit summary"), { target: { value: "Looked at both items" } })
    fireEvent.click(screen.getByLabelText(/I led this audit and am completing it myself/))
    fireEvent.click(screen.getByText("Complete audit"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    fireEvent.change(screen.getByLabelText("Reason for completing alone"), { target: { value: "No second person on site" } })
    fireEvent.click(screen.getByText("Complete audit"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/a1\/complete$/), { summary: "Looked at both items", confirmAlone: true, aloneReason: "No second person on site" }))
  })

  it("will not accept an action due in the past", async () => {
    current = detail({ items: [{ id: "i1", position: 1, question: "Register kept up to date", outcome: "ISSUE", note: "Register two days behind", answeredAt: "x" }] })
    wrap(<TrainProvQualityTab />)
    fireEvent.click(await screen.findByText("Term one classroom audit"))
    fireEvent.click(await screen.findByText("Add corrective action"))
    fireEvent.change(screen.getByLabelText("Action description"), { target: { value: "Catch up the register" } })
    fireEvent.change(screen.getByLabelText("Action owner"), { target: { value: "Thandi" } })
    fireEvent.change(screen.getByLabelText("Action due date"), { target: { value: "2020-01-01" } })
    fireEvent.click(screen.getByText("Save action"))
    expect((await screen.findByRole("alert")).textContent).toBe("The due date cannot be in the past")
    expect(post).not.toHaveBeenCalled()
  })
})
