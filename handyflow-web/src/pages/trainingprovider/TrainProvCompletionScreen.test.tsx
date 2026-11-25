// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvCompletionPanel from "./TrainProvCompletionPanel"
import TrainProvCompletionsList from "./TrainProvCompletionsList"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const row = (state: string, over: object = {}) => ({ instrumentId: "i1", instrumentNo: "ASI-00001", instrumentTitle: "Safety theory test", requiresModeration: true, state, attempts: 1, ...over })
const readiness = (rows: unknown[], openGaps: number) => ({ enrolmentId: "e1", enrolmentNo: "ENR-00001", enrolmentStatus: "IN_PROGRESS", learnerName: "Test Learner", programmeTitle: "Safety skills", versionNo: 1, rows, openGaps, note: "n" })
const completion = (over: object = {}) => ({
  id: "c1", completionNo: "CMP-00001", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerId: "l1", learnerName: "Test Learner", programmeTitle: "Safety skills", outcome: "COMPLETED",
  outcomeOn: "2027-06-01", basis: "All required assessments were completed and moderated", openGaps: 0, gapsSnapshot: null, gapsOverrideReason: null, status: "RECORDED", withdrawnReason: null,
  recordedAt: "", ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let ready = readiness([row("COMPETENT")], 0)
let outcomes: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); ready = readiness([row("COMPETENT")], 0); outcomes = []
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("/readiness") ? ready : outcomes } }))
  post.mockResolvedValue({ data: { data: completion() } })
})
afterEach(cleanup)

const openPanel = async () => { wrap(<TrainProvCompletionPanel enrolmentId="e1" startDate="2026-01-01" />); fireEvent.click(screen.getByText("Readiness and outcome")); await screen.findByText(/Every approved instrument shows competent/) }

describe("completion panel", () => {
  it("loads nothing until it is opened", () => {
    wrap(<TrainProvCompletionPanel enrolmentId="e1" startDate="2026-01-01" />)
    expect(get).not.toHaveBeenCalled()
  })

  it("shows readiness as advice and records a completion with a basis", async () => {
    await openPanel()
    expect(screen.getByText(/It is advice/)).toBeTruthy()
    fireEvent.click(screen.getByText("Record an outcome"))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "COMPLETED" } })
    fireEvent.change(screen.getByLabelText("Basis for the outcome"), { target: { value: "short" } })
    fireEvent.click(screen.getByText("Record the outcome"))
    expect(await screen.findByText(/basis for the outcome/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Basis for the outcome"), { target: { value: "All required assessments were completed and moderated" } })
    fireEvent.click(screen.getByText("Record the outcome"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/completions", expect.objectContaining({ enrolmentId: "e1", outcome: "COMPLETED", overrideReason: null })))
  })

  it("asks for a written reason to complete with open items and keeps it", async () => {
    ready = readiness([row("COMPETENT"), row("NOT_YET_COMPETENT", { instrumentId: "i2", instrumentNo: "ASI-00002", instrumentTitle: "Practical task" })], 1)
    wrap(<TrainProvCompletionPanel enrolmentId="e1" startDate="2026-01-01" />)
    fireEvent.click(screen.getByText("Readiness and outcome"))
    await screen.findByText(/1 of 2 instruments is not yet competent/)
    fireEvent.click(screen.getByText("Record an outcome"))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "COMPLETED" } })
    fireEvent.change(screen.getByLabelText("Basis for the outcome"), { target: { value: "Programme lead confirmed the learner is ready" } })
    fireEvent.click(screen.getByText("Record the outcome"))
    expect(await screen.findByText(/To complete anyway/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for completing with items open"), { target: { value: "Practical task waived by the programme lead" } })
    fireEvent.click(screen.getByText("Record the outcome"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ overrideReason: "Practical task waived by the programme lead" })))
  })

  it("needs no reason for did not complete even with open items", async () => {
    ready = readiness([row("NO_ATTEMPT")], 1)
    wrap(<TrainProvCompletionPanel enrolmentId="e1" startDate="2026-01-01" />)
    fireEvent.click(screen.getByText("Readiness and outcome"))
    await screen.findByText(/No attempt yet/)
    fireEvent.click(screen.getByText("Record an outcome"))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "DID_NOT_COMPLETE" } })
    expect(screen.queryByLabelText("Reason for completing with items open")).toBeNull()
    fireEvent.change(screen.getByLabelText("Basis for the outcome"), { target: { value: "Learner left the programme in April" } })
    fireEvent.click(screen.getByText("Record the outcome"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ outcome: "DID_NOT_COMPLETE", overrideReason: null })))
  })

  it("hides the record button once an outcome stands and withdraws only with a reason", async () => {
    outcomes = [completion()]
    await openPanel()
    expect(await screen.findByText(/CMP-00001/)).toBeTruthy()
    expect(screen.queryByText("Record an outcome")).toBeNull()
    fireEvent.click(screen.getByText("Withdraw this outcome"))
    fireEvent.click(screen.getByText("Withdraw the outcome"))
    expect(await screen.findByText(/at least 10/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for withdrawing"), { target: { value: "Recorded against the wrong enrolment" } })
    fireEvent.click(screen.getByText("Withdraw the outcome"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/c1/withdraw"), { reason: "Recorded against the wrong enrolment" }))
  })
})

describe("completions list", () => {
  it("lists standing outcomes and says they are not certificates", async () => {
    outcomes = [completion({ outcome: "COMPLETED", openGaps: 2, gapsOverrideReason: "Task waived by the programme lead" })]
    wrap(<TrainProvCompletionsList />)
    expect(await screen.findByText(/Test Learner · Safety skills/)).toBeTruthy()
    expect(screen.getByText(/not a certificate/)).toBeTruthy()
    expect(screen.getByText(/2 open at the time/)).toBeTruthy()
  })
})
