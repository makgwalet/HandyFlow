// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvAttemptsPanel from "./TrainProvAttemptsPanel"
import TrainProvModerationQueue from "./TrainProvModerationQueue"
import TrainProvLearnerAttempts from "./TrainProvLearnerAttempts"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const cohort = {
  id: "c1", cohortNo: "COH-00001", name: "Feb intake", programmeId: "p1", programmeTitle: "Safety skills", programmeVersionNo: 1, siteId: "s1", siteName: "Campus",
  startDate: "2027-02-01", endDate: null, capacity: 20, notes: null, status: "RUNNING", statusReason: null, members: 1, facilitators: 1, nextStatuses: [],
}
const members = [{ id: "m1", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerId: "l1", learnerName: "Test Learner", enrolmentStatus: "ENROLLED", status: "ACTIVE", removedReason: null, addedAt: "" }]
const attempt = (over: object = {}) => ({
  id: "a1", attemptNo: "ATT-00001", instrumentId: "i1", instrumentNo: "ASI-00001", instrumentTitle: "Safety theory test", maxMark: 50, requiresModeration: true, memberId: "m1",
  cohortId: "c1", cohortName: "Feb intake", enrolmentId: "e1", learnerId: "l1", learnerName: "Test Learner", attemptSeq: 1, assessedOn: "2027-03-04", assessorId: "x1",
  assessorName: "Ann Assessor", mark: 30, notes: null, status: "RECORDED", voidReason: null, decision: null, history: [], ...over,
})
const instrument = { id: "i1", instrumentNo: "ASI-00001", programmeId: "p1", programmeTitle: "Safety skills", programmeVersionId: "v1", versionNo: 1, title: "Safety theory test", maxMark: 50, status: "APPROVED" }
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let current = attempt()
let moderation: unknown = null
beforeEach(() => {
  get.mockReset(); post.mockReset(); current = attempt(); moderation = null
  get.mockImplementation((url: string) => {
    let data: unknown = []
    if (url.endsWith("/assessment-attempts")) data = [current]
    else if (url.endsWith("/assessment-attempts/a1")) data = current
    else if (url.endsWith("/assessment-attempts/a1/moderation")) data = moderation
    else if (url.endsWith("/assessment-instruments")) data = [instrument]
    else if (url.endsWith("/assessment-instruments/i1")) data = instrument
    else if (url.endsWith("/eligible")) data = [{ practitionerId: "x2", practitionerNo: "PRC-00002", name: "Mo Moderator", role: "MODERATOR", appointmentId: "q", verifiedOn: "2027-01-01" }, { practitionerId: "x1", practitionerNo: "PRC-00001", name: "Ann Assessor", role: "MODERATOR", appointmentId: "r", verifiedOn: "2027-01-01" }]
    else if (url.endsWith("/assessment-moderations/pending")) data = [{ attemptId: "a1", attemptNo: "ATT-00001", instrumentTitle: "Safety theory test", cohortName: "Feb intake", learnerName: "Test Learner", assessorId: "x1", assessorName: "Ann Assessor", decision: "COMPETENT", decidedOn: "2027-03-05" }]
    else if (url.endsWith("/assessment-moderations")) data = [{ id: "mo1", moderationNo: "MOD-00001", attemptId: "a1", attemptNo: "ATT-00001", instrumentTitle: "Safety theory test", learnerName: "Test Learner", assessorName: "Ann", decision: "COMPETENT", moderatorId: "x2", moderatorName: "Mo Moderator", moderatedOn: "2027-03-06", outcome: "QUERIED", basis: "Evidence is thin", resolutionNote: null, resolvedAt: null }]
    else if (url.endsWith("/l1/cohorts")) data = { note: "n", rows: [{ memberId: "m1", cohortId: "c1", cohortNo: "COH-00001", cohortName: "Feb intake", programmeTitle: "P", cohortStatus: "RUNNING", memberStatus: "ACTIVE", removedReason: null, enrolmentNo: "ENR-00001", startDate: "2027-02-01", endDate: null, expected: 0, attended: 0, absent: 0, excused: 0, unrecorded: 0, ratePercent: null }] }
    return Promise.resolve({ data: { data } })
  })
  post.mockResolvedValue({ data: { data: attempt() } })
})
afterEach(cleanup)

describe("attempts panel", () => {
  it("lists an undecided attempt as waiting, not as a result", async () => {
    wrap(<TrainProvAttemptsPanel cohort={cohort} members={members} />)
    expect(await screen.findByText(/Test Learner · Safety theory test/)).toBeTruthy()
    expect(screen.getByText("Waiting for a decision")).toBeTruthy()
    expect(screen.getByText(/30 out of 50/)).toBeTruthy()
  })

  it("records an attempt for the cohort's approved instrument and an eligible assessor", async () => {
    wrap(<TrainProvAttemptsPanel cohort={cohort} members={members} />)
    fireEvent.click(await screen.findByText("Record an attempt"))
    await waitFor(() => expect(screen.getAllByText(/ASI-00001/).length).toBeGreaterThan(0))
    fireEvent.change(screen.getByLabelText("Learner"), { target: { value: "m1" } })
    fireEvent.change(screen.getByLabelText("Instrument"), { target: { value: "i1" } })
    await screen.findByText(/Ann Assessor · PRC-00001/)
    fireEvent.change(screen.getByLabelText("Assessor"), { target: { value: "x1" } })
    fireEvent.change(screen.getByLabelText("Mark"), { target: { value: "60" } })
    fireEvent.click(screen.getByText("Record the attempt"))
    expect(await screen.findByText(/maximum of 50/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Mark"), { target: { value: "32" } })
    fireEvent.click(screen.getByText("Record the attempt"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/assessment-attempts"), expect.objectContaining({ instrumentId: "i1", memberId: "m1", assessorId: "x1", mark: 32 })))
  })

  it("does not offer to record attempts for a planned cohort", async () => {
    wrap(<TrainProvAttemptsPanel cohort={{ ...cohort, status: "PLANNED" }} members={members} />)
    await screen.findByText(/Test Learner · Safety theory test/)
    expect(screen.queryByText("Record an attempt")).toBeNull()
  })
})

describe("attempt detail", () => {
  const open = async () => { wrap(<TrainProvAttemptsPanel cohort={cohort} members={members} />); fireEvent.click(await screen.findByText(/Test Learner · Safety theory test/)) }

  it("keeps the mark and the decision separate and refuses a decision without a basis", async () => {
    await open()
    expect(await screen.findByText(/Not recorded yet/)).toBeTruthy()
    expect(screen.getAllByText(/recorded separately/).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByText("Record the decision"))
    fireEvent.change(screen.getByLabelText("Decision"), { target: { value: "COMPETENT" } })
    fireEvent.change(screen.getByLabelText("Basis for the decision"), { target: { value: "short" } })
    const buttons = screen.getAllByText("Record the decision")
    fireEvent.click(buttons[buttons.length - 1])
    expect(await screen.findByText(/basis for the decision/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("records a decision with its basis", async () => {
    await open()
    fireEvent.click(await screen.findByText("Record the decision"))
    fireEvent.change(screen.getByLabelText("Decision"), { target: { value: "NOT_YET_COMPETENT" } })
    fireEvent.change(screen.getByLabelText("Basis for the decision"), { target: { value: "Missed two critical steps in the practical" } })
    const buttons = screen.getAllByText("Record the decision")
    fireEvent.click(buttons[buttons.length - 1])
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/a1/decision"), { decision: "NOT_YET_COMPETENT", basis: "Missed two critical steps in the practical" }))
  })

  it("offers no mark correction once a decision exists, but offers moderation without the assessor", async () => {
    current = attempt({ decision: { decision: "COMPETENT", basis: "Met every criterion", decidedBy: null, decidedOn: "2027-03-05" } })
    await open()
    expect((await screen.findAllByText("Competent")).length).toBeGreaterThan(0)
    expect(screen.queryByText("Correct the mark")).toBeNull()
    expect(screen.queryByText("Record the decision")).toBeNull()
    fireEvent.click(await screen.findByText("Record moderation"))
    await screen.findByText(/Mo Moderator · PRC-00002/)
    expect(screen.queryByText(/Ann Assessor · PRC-00001/, { selector: "option" })).toBeNull()
  })

  it("lets a queried moderation be answered in writing", async () => {
    current = attempt({ decision: { decision: "COMPETENT", basis: "Met every criterion", decidedBy: null, decidedOn: "2027-03-05" } })
    moderation = { id: "mo1", moderationNo: "MOD-00001", attemptId: "a1", attemptNo: "ATT-00001", instrumentTitle: "x", learnerName: "y", assessorName: "z", decision: "COMPETENT", moderatorId: "x2", moderatorName: "Mo Moderator", moderatedOn: "2027-03-06", outcome: "QUERIED", basis: "Evidence is thin", resolutionNote: null, resolvedAt: null }
    await open()
    fireEvent.click(await screen.findByText("Answer the query"))
    fireEvent.change(screen.getByLabelText("Answer to the query"), { target: { value: "short" } })
    fireEvent.click(screen.getByText("Record the answer"))
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Answer to the query"), { target: { value: "Attempt voided and the learner reassessed" } })
    fireEvent.click(screen.getByText("Record the answer"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/mo1/resolve"), { note: "Attempt voided and the learner reassessed" }))
  })
})

describe("moderation queue and learner view", () => {
  it("shows open queries and decisions waiting for moderation", async () => {
    wrap(<TrainProvModerationQueue />)
    expect(await screen.findByText("Query open")).toBeTruthy()
    expect(screen.getByText("Decisions waiting for moderation")).toBeTruthy()
    expect(screen.getByText("Competent")).toBeTruthy()
  })

  it("shows the learner's own attempts only", async () => {
    current = attempt()
    wrap(<TrainProvLearnerAttempts learnerId="l1" />)
    expect(await screen.findByText("Safety theory test")).toBeTruthy()
    expect(screen.getByText("Waiting for a decision")).toBeTruthy()
    cleanup()
    wrap(<TrainProvLearnerAttempts learnerId="someone-else" />)
    await waitFor(() => expect(screen.queryByText("Safety theory test")).toBeNull())
  })
})
