// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvSamplingPanel from "./TrainProvSamplingPanel"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))
let me = "me"
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: me } }) }))

const version = (over: object = {}) => ({
  id: "v1", versionNo: 1, effectiveFrom: "2020-01-01", basis: "PERCENTAGE", percentage: 10, minCount: 2, maxCount: null, selectionMethod: "RANDOM", riskFactors: null,
  extraTriggerQueries: null, extraLookback: null, extraPercentage: null, rationale: "Agreed at the quality meeting", createdAt: "2020-01-01T08:00:00Z", ...over,
})
const rule = (over: object = {}) => ({
  id: "r1", ruleNo: "SMR-00001", name: "All practical assessments", scopeType: "ALL", programmeId: null, programmeTitle: null, cohortId: null, cohortName: null, assessmentType: null,
  status: "ACTIVE", retiredReason: null, current: version(), versions: [version()], ...over,
})
const row = (over: object = {}) => ({ id: "d1", drawNo: "SMD-00001", ruleNo: "SMR-00001", ruleName: "All practical assessments", versionNo: 1, drawnOn: "2020-03-01", decidedFrom: "2020-02-01", decidedTo: "2020-02-28", eligibleCount: 20, requiredCount: 2, selectedCount: 2, status: "OPEN", decision: null, ...over })
const item = (over: object = {}) => ({
  id: "i1", attemptId: "a1", attemptNo: "ATT-1", instrumentTitle: "Practical task", learnerName: "Thandi Nkosi", assessorId: "p1", assessorName: "Sam Dube", decision: "COMPETENT", riskScore: 2,
  riskFactors: "repeat attempt", selected: true, selectedReason: "RANDOM", targetNote: null, moderationNo: null, moderationOutcome: null, moderationResolved: false, ...over,
})
const draw = (over: object = {}) => ({
  id: "d1", drawNo: "SMD-00001", ruleId: "r1", ruleNo: "SMR-00001", ruleName: "All practical assessments", versionNo: 1,
  ruleSnapshot: "SMR-00001 All practical assessments, version 1: 10% of eligible records. This is the provider's own sampling rule, not a regulatory requirement.",
  drawnOn: "2020-03-01", decidedFrom: "2020-02-01", decidedTo: "2020-02-28", seed: 42, eligibleCount: 3, requiredCount: 2, selectedCount: 2, status: "OPEN", drawnBy: "other",
  decision: null, decisionNote: null, summary: null, voidReason: null, moderatedCount: 0,
  items: [item(), item({ id: "i2", attemptId: "a2", attemptNo: "ATT-2", learnerName: "Sipho Zulu" }), item({ id: "i3", attemptId: "a3", attemptNo: "ATT-3", learnerName: "Lerato M", selected: false, selectedReason: null })],
  actions: [], ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let rules: unknown[] = []
let draws: unknown[] = []
let detail: unknown = draw()
beforeEach(() => {
  get.mockReset(); post.mockReset(); me = "me"; rules = [rule()]; draws = [row()]; detail = draw()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /\/draws\/d1$/.test(url) ? detail : /\/draws$/.test(url) ? draws : /\/rules$/.test(url) ? rules : [] } }))
  post.mockResolvedValue({ data: { data: draw() } })
})
afterEach(cleanup)

describe("sampling panel", () => {
  it("says it is the provider's own rule and describes the live version", async () => {
    wrap(<TrainProvSamplingPanel />)
    expect(await screen.findByText(/not a regulatory requirement/)).toBeTruthy()
    expect(await screen.findByText(/10% of eligible results, at least 2, random/)).toBeTruthy()
    expect(screen.getByText(/SMD-00001 · All practical assessments/)).toBeTruthy()
  })

  it("shows nothing is sampled until a rule exists", async () => {
    rules = []; draws = []
    wrap(<TrainProvSamplingPanel />)
    expect(await screen.findByText(/Nothing is moderated by sampling until you write one/)).toBeTruthy()
  })

  it("refuses a rule without a reason before sending", async () => {
    rules = []
    wrap(<TrainProvSamplingPanel />)
    fireEvent.click(await screen.findByText("New rule"))
    fireEvent.change(screen.getAllByRole("textbox")[0], { target: { value: "Everything" } })
    fireEvent.change(screen.getByLabelText("Percentage"), { target: { value: "10" } })
    fireEvent.click(screen.getByText("Save rule"))
    expect(await screen.findByText(/Say why the rule is set this way/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("makes a draw for a chosen rule and window", async () => {
    wrap(<TrainProvSamplingPanel />)
    fireEvent.click(await screen.findByText("Make a draw"))
    fireEvent.change(await screen.findByLabelText("Rule"), { target: { value: "r1" } })
    fireEvent.change(screen.getByLabelText("Decisions made from"), { target: { value: "2020-02-01" } })
    fireEvent.change(screen.getByLabelText("to"), { target: { value: "2020-02-28" } })
    fireEvent.click(screen.getByText("Draw"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/\/draws$/), { ruleId: "r1", decidedFrom: "2020-02-01", decidedTo: "2020-02-28", targeted: [] }))
  })

  it("shows the snapshot and the picked results, and all of them on request", async () => {
    wrap(<TrainProvSamplingPanel />)
    fireEvent.click(await screen.findByText(/SMD-00001 · All practical assessments/))
    expect(await screen.findByText(/This is the provider's own sampling rule, not a regulatory requirement\./)).toBeTruthy()
    expect(screen.getByText(/Thandi Nkosi/)).toBeTruthy()
    expect(screen.queryByText(/Lerato M/)).toBeNull()
    fireEvent.click(screen.getByText("Show all 3"))
    expect(screen.getByText(/Lerato M/)).toBeTruthy()
  })

  it("will not close until every picked result is moderated", async () => {
    wrap(<TrainProvSamplingPanel />)
    fireEvent.click(await screen.findByText(/SMD-00001 · All practical assessments/))
    fireEvent.click(await screen.findByText("Close draw"))
    fireEvent.change(screen.getByLabelText("Reason for the decision"), { target: { value: "Everything looks right" } })
    fireEvent.click(screen.getByText("Close draw"))
    expect(await screen.findByText("2 picked result(s) have no moderation yet")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("asks for confirmation when the same person closes their own draw", async () => {
    detail = draw({ drawnBy: "me", moderatedCount: 2 })
    wrap(<TrainProvSamplingPanel />)
    fireEvent.click(await screen.findByText(/SMD-00001 · All practical assessments/))
    fireEvent.click(await screen.findByText("Close draw"))
    fireEvent.change(screen.getByLabelText("Reason for the decision"), { target: { value: "Everything looks right" } })
    fireEvent.click(screen.getByText("Close draw"))
    expect(await screen.findByText(/A second person should close this draw/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText(/I drew this sample and I am closing it myself/))
    fireEvent.click(screen.getByText("Close draw"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/draws\/d1\/close$/), { decision: "RESULTS_ACCEPTED", note: "Everything looks right", confirmAlone: true }))
  })

  it("keeps earlier versions visible", async () => {
    rules = [rule({ current: version({ id: "v2", versionNo: 2, percentage: 20 }), versions: [version({ id: "v2", versionNo: 2, percentage: 20 }), version()] })]
    wrap(<TrainProvSamplingPanel />)
    fireEvent.click(await screen.findByText("Versions (2)"))
    expect(await screen.findByText(/Version 1 from 2020-01-01: 10% of eligible results/)).toBeTruthy()
  })
})
