// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvDuplicatesPanel from "./TrainProvDuplicatesPanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
let me = "me"
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: me } }) }))

const learner = (over: object = {}) => ({ kind: "LEARNER", id: "l1", number: "LRN-1", name: "Thandi Nkosi", idNumberMasked: "***4567", dateOfBirth: null, email: null, phone: null, status: "ACTIVE", linkedLearnerId: null, ...over })
const pair = (over: object = {}) => ({ first: learner(), second: learner({ id: "l2", number: "LRN-2" }), strength: "STRONG", reasons: ["ID_NUMBER", "NAME"], conflicts: [], ...over })
const merge = (over: object = {}) => ({
  id: "m1", status: "PROPOSED", survivorId: "l1", survivorName: "Thandi Nkosi", survivorNo: "LRN-1", mergedId: "l2", mergedName: "Thandi Nkosi", mergedNo: "LRN-2",
  matchReasons: ["ID_NUMBER"], conflicts: [], conflictsAccepted: false, reason: "Same passport on both records", survivorSnapshot: "", mergedSnapshot: "", proposedBy: "me",
  proposedAt: "2020-03-01T08:00:00Z", decidedBy: null, decidedAt: null, decisionReason: null, movedEnrolments: null, movedApplications: null, movedDocuments: null, delegateNote: null, ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let pairs: unknown[] = []
let unmatched: unknown[] = []
let merges: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); me = "me"; pairs = [pair()]; unmatched = []; merges = [merge()]
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /duplicate-pairs/.test(url) ? pairs : /unmatched-delegates/.test(url) ? unmatched : merges } }))
  post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)
const show = (admin = true) => wrap(<TrainProvDuplicatesPanel canManage isAdmin={admin} onOpen={() => undefined} />)

describe("possible duplicates", () => {
  it("lists a pair with how strong the match is and what agreed", async () => {
    show()
    expect(await screen.findByText("Likely the same person")).toBeTruthy()
    expect(screen.getByText("same identity number, same name")).toBeTruthy()
  })

  it("proposes a merge, keeping the chosen record, only with a reason", async () => {
    show()
    fireEvent.click(await screen.findByText("Propose a merge"))
    fireEvent.change(screen.getByLabelText("Record to keep"), { target: { value: "l2" } })
    fireEvent.click(screen.getByText("Propose merge"))
    expect(await screen.findByText(/Say why these are the same person/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Merge reason"), { target: { value: "Same passport on both records" } })
    fireEvent.click(screen.getByText("Propose merge"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/learner-merges$/), { survivorId: "l2", mergedId: "l1", reason: "Same passport on both records", conflictsAccepted: false }))
  })

  it("will not propose records that disagree until the differences are ticked as checked", async () => {
    pairs = [pair({ strength: "POSSIBLE", conflicts: ["The dates of birth differ"] })]
    show()
    expect(await screen.findByText(/They disagree: The dates of birth differ/)).toBeTruthy()
    fireEvent.click(screen.getByText("Propose a merge"))
    fireEvent.change(screen.getByLabelText("Merge reason"), { target: { value: "Typo in one birth date" } })
    fireEvent.click(screen.getByText("Propose merge"))
    expect(await screen.findByText(/The records disagree/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
})

describe("delegates with no learner", () => {
  it("links a delegate to a matching learner with a note saying where it came from", async () => {
    unmatched = [{ delegate: learner({ kind: "DELEGATE", id: "d1", number: "DEL-1", name: "T Nkosi" }), clientId: "c1", clientName: "Acme", candidates: [{ person: learner(), strength: "STRONG", reasons: ["ID_NUMBER"], conflicts: [] }] }]
    show()
    fireEvent.click(screen.getByText("Delegates with no learner"))
    fireEvent.click(await screen.findByText("Link"))
    await waitFor(() => expect(put).toHaveBeenCalledWith(expect.stringMatching(/learners\/l1\/delegate$/), { delegateId: "d1", reason: "Linked from the unmatched delegates list" }))
  })

  it("does not offer to link a learner whose details disagree", async () => {
    unmatched = [{ delegate: learner({ kind: "DELEGATE", id: "d1", number: "DEL-1", name: "T Nkosi" }), clientId: "c1", clientName: null, candidates: [{ person: learner(), strength: "POSSIBLE", reasons: ["NAME"], conflicts: ["The identity numbers differ"] }] }]
    show()
    fireEvent.click(screen.getByText("Delegates with no learner"))
    expect(await screen.findByText(/they disagree: The identity numbers differ/)).toBeTruthy()
    expect(screen.queryByText("Link")).toBeNull()
  })
})

describe("merges", () => {
  it("makes the proposer say why they are deciding alone", async () => {
    show()
    fireEvent.click(screen.getByText("Merges"))
    fireEvent.click(await screen.findByText("Approve"))
    fireEvent.change(screen.getByLabelText("Decision reason"), { target: { value: "Checked both records" } })
    fireEvent.click(screen.getByText("Merge the learners"))
    expect(await screen.findByText(/second person needs to decide/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByLabelText(/I am deciding this alone/))
    fireEvent.change(screen.getByLabelText("Reason for deciding alone"), { target: { value: "Nobody else is in today" } })
    fireEvent.click(screen.getByText("Merge the learners"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/learner-merges\/m1\/approve$/), { reason: "Checked both records", confirmAlone: true, singleReason: "Nobody else is in today" }))
  })

  it("lets a second person approve with just a reason, and cannot cancel someone else's proposal", async () => {
    me = "someone-else"
    show()
    fireEvent.click(screen.getByText("Merges"))
    expect(await screen.findByText("Reject")).toBeTruthy()
    expect(screen.queryByText("Cancel my proposal")).toBeNull()
    fireEvent.click(screen.getByText("Approve"))
    fireEvent.change(screen.getByLabelText("Decision reason"), { target: { value: "Checked both records" } })
    fireEvent.click(screen.getByText("Merge the learners"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/m1\/approve$/), { reason: "Checked both records", confirmAlone: false, singleReason: null }))
  })

  it("shows what a finished merge moved", async () => {
    merges = [merge({ status: "COMPLETED", movedEnrolments: 2, movedApplications: 1, movedDocuments: 3, decisionReason: "Checked both records", decidedBy: "x" })]
    show()
    fireEvent.click(screen.getByText("Merges"))
    fireEvent.change(await screen.findByLabelText("Merge status"), { target: { value: "COMPLETED" } })
    expect(await screen.findByText(/Moved 2 enrolment\(s\), 1 application\(s\), 3 document\(s\)/)).toBeTruthy()
  })
})
