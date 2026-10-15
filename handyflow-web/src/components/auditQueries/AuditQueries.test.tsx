import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { AuditQueries } from "./AuditQueries"
import { actionsFor, needsAttention, sortQueries } from "./queries.logic"
import type { AuditQueryApi, AuditQueryView } from "./types"

const q = (o: Partial<AuditQueryView> = {}): AuditQueryView => ({
  id: "q1", kind: "QUERY", reference: "Q-0001", engagementId: null, title: "Round-sum journal", subjectType: null, subjectId: null, subjectLabel: null,
  dueDate: null, raisedByLabel: "a@firm.test", status: "OPEN", timesReopened: 0, overdue: false, linkedFindingId: null, linkedFindingTitle: null,
  createdAt: "2026-10-01T00:00:00Z", closedAt: null,
  messages: [{ id: "m1", authorKind: "AUDITOR", authorLabel: "a@firm.test", messageKind: "QUESTION", body: "Why a round sum?", createdAt: "2026-10-01T00:00:00Z" }], ...o,
})
const makeApi = (o: Partial<AuditQueryApi> = {}): AuditQueryApi => ({ list: vi.fn().mockResolvedValue([]), detail: vi.fn(), ...o })
afterEach(cleanup)
beforeEach(() => { vi.restoreAllMocks() })

describe("query rules", () => {
  it("only the business answers, only the auditor resolves", () => {
    expect(actionsFor(q(), "business").answer).toBe(true)
    expect(actionsFor(q(), "auditor")).toMatchObject({ answer: false, resolve: false, withdraw: true })
    const answered = q({ status: "ANSWERED" })
    expect(actionsFor(answered, "auditor")).toMatchObject({ resolve: true, followUp: true })
    expect(actionsFor(answered, "business").answer).toBe(false)
  })

  it("finished ones allow nothing", () => {
    expect(actionsFor(q({ status: "RESOLVED" }), "auditor")).toMatchObject({ resolve: false, followUp: false, withdraw: false })
    expect(actionsFor(q({ status: "WITHDRAWN" }), "business").answer).toBe(false)
  })

  it("turns are opposite on each side", () => {
    expect(needsAttention(q(), "business")).toBe(true)
    expect(needsAttention(q({ status: "ANSWERED" }), "auditor")).toBe(true)
    expect(needsAttention(q({ status: "ANSWERED" }), "business")).toBe(false)
  })

  it("sorts overdue first and finished last", () => {
    const list = [q({ id: "done", status: "RESOLVED" }), q({ id: "new", createdAt: "2026-10-09T00:00:00Z" }), q({ id: "late", overdue: true, createdAt: "2026-09-01T00:00:00Z" })]
    expect(sortQueries(list, "business").map(x => x.id)).toEqual(["late", "new", "done"])
  })
})

describe("AuditQueries screen", () => {
  it("lets the business answer only with a real answer", async () => {
    const api = makeApi({ list: vi.fn().mockResolvedValue([q()]), detail: vi.fn().mockResolvedValue(q()), answer: vi.fn().mockResolvedValue(q({ status: "ANSWERED" })), link: vi.fn(), findingOptions: vi.fn() })
    render(<AuditQueries side="business" api={api} />)
    fireEvent.click(await screen.findByText(/Q-0001/))
    const send = (await screen.findByText("Send answer")) as HTMLButtonElement
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Your answer"), { target: { value: "short" } })
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Your answer"), { target: { value: "It is the monthly retainer accrual" } })
    fireEvent.click(send)
    await waitFor(() => expect(api.answer).toHaveBeenCalledWith("q1", "It is the monthly retainer accrual"))
  })

  it("lets the auditor raise a note, but not without a title and a proper text", async () => {
    const api = makeApi({ raise: vi.fn().mockResolvedValue(q()) })
    render(<AuditQueries side="auditor" api={api} />)
    fireEvent.click(await screen.findByText("New query or note"))
    const send = screen.getByText("Send") as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Query type"), { target: { value: "REVIEW_NOTE" } })
    fireEvent.change(screen.getByLabelText("Query title"), { target: { value: "Cut-off" } })
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Query text"), { target: { value: "Year-end invoices look booked a week early" } })
    expect(send.disabled).toBe(false)
    fireEvent.click(send)
    await waitFor(() => expect(api.raise).toHaveBeenCalledWith({ kind: "REVIEW_NOTE", title: "Cut-off", text: "Year-end invoices look booked a week early", subjectType: undefined, subjectLabel: undefined, dueDate: undefined }))
  })

  it("asks the auditor for a reason before asking more", async () => {
    const answered = q({ status: "ANSWERED" })
    const api = makeApi({ list: vi.fn().mockResolvedValue([answered]), detail: vi.fn().mockResolvedValue(answered), followUp: vi.fn().mockResolvedValue(q()), resolve: vi.fn() })
    vi.spyOn(window, "prompt").mockReturnValueOnce("more").mockReturnValueOnce("Please show the retainer agreement")
    render(<AuditQueries side="auditor" api={api} />)
    fireEvent.click(await screen.findByText(/Q-0001/))
    fireEvent.click(await screen.findByText("Ask more"))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(api.followUp).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Ask more"))
    await waitFor(() => expect(api.followUp).toHaveBeenCalledWith("q1", "Please show the retainer agreement"))
  })

  it("lets the business link a finding by name and shows the link, and shows the auditor nothing about it", async () => {
    const linked = q({ linkedFindingId: "f1", linkedFindingTitle: "Weak journal approval" })
    const api = makeApi({ list: vi.fn().mockResolvedValue([q()]), detail: vi.fn().mockResolvedValue(q()), link: vi.fn().mockResolvedValue(linked), findingOptions: vi.fn().mockResolvedValue([{ id: "f1", title: "Weak journal approval (FY26)" }]) })
    render(<AuditQueries side="business" api={api} />)
    fireEvent.click(await screen.findByText(/Q-0001/))
    fireEvent.click(await screen.findByText("Link to an internal finding"))
    fireEvent.change(await screen.findByLabelText("Finding to link"), { target: { value: "f1" } })
    await waitFor(() => expect(api.link).toHaveBeenCalledWith("q1", "f1"))
    expect(await screen.findByText(/Linked to internal finding: Weak journal approval/)).toBeTruthy()
    cleanup()
    const auditorApi = makeApi({ list: vi.fn().mockResolvedValue([linked]), detail: vi.fn().mockResolvedValue(linked) })
    render(<AuditQueries side="auditor" api={auditorApi} />)
    fireEvent.click(await screen.findByText(/^All/))
    fireEvent.click(await screen.findByText(/Q-0001/))
    await screen.findByText("Why a round sum?")
    expect(screen.queryByText(/Linked to internal finding/)).toBeNull()
    expect(screen.queryByText("Link to an internal finding")).toBeNull()
  })
})
