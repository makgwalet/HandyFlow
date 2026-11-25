// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvLearnersTab from "./TrainProvLearnersTab"

const get = vi.fn()
const post = vi.fn()
let admin = false
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (p: string[]) => (p.includes("TRAININGPROVIDER_ADMIN") && p.length === 1 ? admin : true) }))

const learner = (over: object = {}) => ({
  id: "l1", learnerNo: "LRN-00001", firstNames: "Test", surname: "Learner", idType: "PASSPORT", idNumber: "A1234567", idNumberMasked: "****4567", dateOfBirth: null,
  email: null, phone: null, address: null, province: null, clientId: null, clientName: null, delegateId: null, consentGiven: false, consentOn: null,
  consentNote: null, status: "ACTIVE", archivedReason: null, liveEnrolments: 1, ...over,
})
const enrolment = (over: object = {}) => ({
  id: "e1", enrolmentNo: "ENR-00001", learnerId: "l1", learnerName: "Test Learner", programmeId: "p1", programmeTitle: "Safety skills", programmeVersionNo: 1, siteId: "s1",
  siteName: "Main campus", applicationId: null, clientId: null, startDate: "2027-02-01", expectedEndDate: null, status: "ENROLLED", statusReason: null,
  trainingActivity: "ACCREDITED", coverOutcome: "EXPIRED", coverCheckedOn: "2026-11-25", coverOverrideReason: "Board approved in writing", nextStatuses: ["IN_PROGRESS", "ON_HOLD"],
  createdAt: "2026-11-25T10:00:00Z", ...over,
})
const application = (over: object = {}) => ({
  id: "a1", learnerId: "l1", learnerName: "Test Learner", programmeId: "p1", programmeTitle: "Safety skills", siteId: null, siteName: null, preferredStart: null, sourceNote: null,
  status: "UNDER_REVIEW", statusReason: null, entryRequirementsMet: null, entryCheckNote: null, entryOverrideReason: null, nextStatuses: ["ACCEPTED", "DECLINED"],
  createdAt: "2026-11-24T10:00:00Z", ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  admin = false; get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => {
    let data: unknown = []
    if (url.endsWith("/learners")) data = [learner()]
    else if (url.endsWith("/learners/l1")) data = { learner: learner({ idNumber: "A1234567" }), applications: [application()], enrolments: [enrolment()] }
    else if (url.endsWith("/applications")) data = [application()]
    else if (url.endsWith("/learner-enrolments")) data = [enrolment()]
    else if (url.endsWith("/programmes")) data = [{ id: "p1", code: "SP1", title: "Safety skills", trainingActivity: "ACCREDITED" }]
    return Promise.resolve({ data: { data } })
  })
})
afterEach(cleanup)

describe("learners screen", () => {
  it("lists learners with the identity number masked", async () => {
    wrap(<TrainProvLearnersTab />)
    expect(await screen.findByText("Test Learner")).toBeTruthy()
    expect(screen.getByText(/\*\*\*\*4567/)).toBeTruthy()
    expect(screen.queryByText(/A1234567/)).toBeNull()
  })
  it("states plainly when an accredited enrolment had no cover", async () => {
    wrap(<TrainProvLearnersTab />)
    fireEvent.click(await screen.findByText("Test Learner"))
    expect(await screen.findByText(/Accreditation expired when enrolled/)).toBeTruthy()
    expect(screen.getByText(/Enrolled by an administrator: Board approved in writing/)).toBeTruthy()
  })
  it("a person must record the entry check before accepting, and nothing is sent without it", async () => {
    wrap(<TrainProvLearnersTab />)
    fireEvent.click(await screen.findByText("Test Learner"))
    fireEvent.click(await screen.findByText("Change status"))
    fireEvent.click(screen.getByText("Record"))
    expect((await screen.findByRole("alert")).textContent).toContain("meets the entry requirements")
    expect(post).not.toHaveBeenCalled()
  })
  it("only an administrator can accept someone who does not meet entry", async () => {
    wrap(<TrainProvLearnersTab />)
    fireEvent.click(await screen.findByText("Test Learner"))
    fireEvent.click(await screen.findByText("Change status"))
    fireEvent.change(screen.getByLabelText("Entry requirements met"), { target: { value: "no" } })
    expect(screen.getByText(/Only an administrator can accept a learner/)).toBeTruthy()
    expect(screen.queryByLabelText("Entry override reason")).toBeNull()
  })
  it("a new learner needs a valid identity before anything is sent", async () => {
    wrap(<TrainProvLearnersTab />)
    fireEvent.click(await screen.findByText("Add a learner"))
    fireEvent.change(screen.getByLabelText("First names"), { target: { value: "New" } })
    fireEvent.change(screen.getByLabelText("Surname"), { target: { value: "Person" } })
    fireEvent.change(screen.getByLabelText("Identity number"), { target: { value: "1234567890123" } })
    fireEvent.click(screen.getByText("Add learner"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("check digit"))
    expect(post).not.toHaveBeenCalled()
  })
})
