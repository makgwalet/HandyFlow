import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { EvidenceRequests } from "./EvidenceRequests"
import { actionsFor, needsAttention, sortRequests } from "./requests.logic"
import type { EvidenceRequestApi, EvidenceRequestView } from "./types"

const req = (o: Partial<EvidenceRequestView> = {}): EvidenceRequestView => ({
  id: "r1", reference: "ER-0001", engagementId: null, title: "Bank statements", description: null, priority: "MEDIUM", dueDate: null,
  assignedTo: null, assignedToName: null, requestedByKind: "AUDITOR", requestedByLabel: "a@firm.test", status: "OPEN", timesRejected: 0,
  overdue: false, dueSoon: false, cancelReason: null, createdAt: "2026-10-01T00:00:00Z", closedAt: null, rounds: [], comments: [], ...o,
})
const makeApi = (o: Partial<EvidenceRequestApi> = {}): EvidenceRequestApi => ({
  list: vi.fn().mockResolvedValue([]), detail: vi.fn(), create: vi.fn(), comment: vi.fn(), cancel: vi.fn(), download: vi.fn(), ...o,
})
afterEach(cleanup)
beforeEach(() => { vi.restoreAllMocks() })

describe("request rules", () => {
  it("each side only gets the actions that are theirs", () => {
    const open = req(), waiting = req({ status: "SUBMITTED" })
    expect(actionsFor(open, "business")).toMatchObject({ upload: true, send: true, notAvailable: true, approve: false, sendBack: false })
    expect(actionsFor(open, "auditor")).toMatchObject({ upload: false, send: false, approve: false })
    expect(actionsFor(waiting, "auditor")).toMatchObject({ approve: true, sendBack: true })
    expect(actionsFor(waiting, "business")).toMatchObject({ send: false, approve: false })
  })

  it("only the side that raised a request can cancel it, and finished ones cannot be touched", () => {
    expect(actionsFor(req({ requestedByKind: "AUDITOR" }), "business").cancel).toBe(false)
    expect(actionsFor(req({ requestedByKind: "AUDITOR" }), "auditor").cancel).toBe(true)
    const done = actionsFor(req({ status: "APPROVED" }), "auditor")
    expect(done).toMatchObject({ cancel: false, comment: false, approve: false })
  })

  it("it is the business's turn when open and the auditor's when answered", () => {
    expect(needsAttention(req(), "business")).toBe(true)
    expect(needsAttention(req({ status: "NOT_AVAILABLE" }), "auditor")).toBe(true)
    expect(needsAttention(req({ status: "SUBMITTED" }), "business")).toBe(false)
  })

  it("lists overdue first, then high priority, finished last", () => {
    const rs = [req({ id: "done", status: "APPROVED", overdue: false }), req({ id: "low", priority: "LOW" }), req({ id: "late", overdue: true, priority: "LOW" }), req({ id: "high", priority: "HIGH" })]
    expect(sortRequests(rs).map(r => r.id)).toEqual(["late", "high", "low", "done"])
  })
})

describe("EvidenceRequests screen", () => {
  it("shows only what needs the viewer by default, and everything on request", async () => {
    const api = makeApi({ list: vi.fn().mockResolvedValue([req(), req({ id: "r2", reference: "ER-0002", title: "Debtors", status: "SUBMITTED" })]) })
    render(<EvidenceRequests side="business" api={api} />)
    expect(await screen.findByText(/ER-0001/)).toBeTruthy()
    expect(screen.queryByText(/ER-0002/)).toBeNull()
    fireEvent.click(screen.getByText(/^All/))
    expect(screen.getByText(/ER-0002/)).toBeTruthy()
  })

  it("will not send a request without a title", async () => {
    const api = makeApi({ create: vi.fn().mockResolvedValue(req()) })
    render(<EvidenceRequests side="auditor" api={api} />)
    fireEvent.click(await screen.findByText("New request"))
    const send = screen.getByText("Send request") as HTMLButtonElement
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("What is needed"), { target: { value: "Debtors listing" } })
    fireEvent.change(screen.getByLabelText("Request priority"), { target: { value: "HIGH" } })
    fireEvent.click(send)
    await waitFor(() => expect(api.create).toHaveBeenCalledWith({ title: "Debtors listing", description: undefined, priority: "HIGH", dueDate: undefined, engagementId: undefined }))
  })

  it("asks the auditor for a real reason before sending an answer back", async () => {
    const waiting = req({ status: "SUBMITTED", rounds: [{ roundNo: 1, kind: "SUBMISSION", note: "Here", submittedAt: "2026-10-02T00:00:00Z", decision: null, decisionNote: null, decidedAt: null, files: [{ id: "f1", fileName: "march.pdf" }] }] })
    const api = makeApi({ list: vi.fn().mockResolvedValue([waiting]), detail: vi.fn().mockResolvedValue(waiting), reject: vi.fn().mockResolvedValue(req()), approve: vi.fn() })
    const prompt = vi.spyOn(window, "prompt").mockReturnValueOnce("bad").mockReturnValueOnce("Page 2 is missing from the statement")
    render(<EvidenceRequests side="auditor" api={api} />)
    fireEvent.click(await screen.findByText(/ER-0001/))
    expect(await screen.findByText("march.pdf")).toBeTruthy()
    fireEvent.click(screen.getByText("Send back"))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(api.reject).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Send back"))
    await waitFor(() => expect(api.reject).toHaveBeenCalledWith("r1", "Page 2 is missing from the statement"))
    expect(prompt).toHaveBeenCalledTimes(2)
  })

  it("lets the business mark an item not available only with a reason, and shows the history of earlier rounds", async () => {
    const open = req({ timesRejected: 1, rounds: [{ roundNo: 1, kind: "SUBMISSION", note: "First try", submittedAt: "2026-10-02T00:00:00Z", decision: "REJECTED", decisionNote: "Wrong month", decidedAt: "2026-10-03T00:00:00Z", files: [] }] })
    const api = makeApi({ list: vi.fn().mockResolvedValue([open]), detail: vi.fn().mockResolvedValue(open), notAvailable: vi.fn().mockResolvedValue(open) })
    vi.spyOn(window, "prompt").mockReturnValue("The bank closed the account in 2019")
    render(<EvidenceRequests side="business" api={api} />)
    fireEvent.click(await screen.findByText(/ER-0001/))
    expect(await screen.findByText(/Sent back: Wrong month/)).toBeTruthy()
    fireEvent.click(screen.getByText("Not available"))
    await waitFor(() => expect(api.notAvailable).toHaveBeenCalledWith("r1", "The bank closed the account in 2019"))
  })

  it("uploads a chosen file against the request and offers no approval to the business", async () => {
    const upload = vi.fn().mockResolvedValue(undefined)
    const api = makeApi({ list: vi.fn().mockResolvedValue([req()]), detail: vi.fn().mockResolvedValue(req()), upload })
    render(<EvidenceRequests side="business" api={api} />)
    fireEvent.click(await screen.findByText(/ER-0001/))
    const input = (await screen.findByLabelText("Attach a file")) as HTMLInputElement
    const file = new File(["x"], "march.pdf", { type: "application/pdf" })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => expect(upload).toHaveBeenCalledWith("r1", file))
    expect(screen.queryByText("Approve")).toBeNull()
  })
})
