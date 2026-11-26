// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { LearnerPortalHomePage } from "./LearnerPortalHomePage"
import { LearnerPortalAcceptInvitePage } from "./LearnerPortalAcceptInvitePage"
import TrainProvEmployerLearners from "./TrainProvEmployerLearners"
import TrainProvLearnerPortalAccess from "../trainingprovider/TrainProvLearnerPortalAccess"

const portalGet = vi.fn()
const portalPost = vi.fn()
const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/trainProvPortal.client", () => ({ trainProvPortalClient: { get: (...a: unknown[]) => portalGet(...a), post: (...a: unknown[]) => portalPost(...a) } }))
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const enrolment = (over: object = {}) => ({
  enrolmentNo: "ENR-00001", programmeCode: "HC1", programmeTitle: "Health care assistant", status: "IN_PROGRESS", startDate: "2020-03-01", expectedEndDate: "2020-09-01", cohortName: "March 2020",
  attendance: { recorded: 5, present: 3, late: 1, absent: 1, excused: 0 }, outcome: "No outcome recorded yet", outcomeOn: null, ...over,
})
const wrap = (ui: React.ReactNode, path = "/") => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter></QueryClientProvider>)

beforeEach(() => { portalGet.mockReset(); portalPost.mockReset(); get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("learner portal home", () => {
  it("shows the learner's own enrolments with attendance as counts and no competence wording", async () => {
    portalGet.mockResolvedValue({ data: { learnerNo: "LRN-00001", fullName: "Thandi Nkosi", note: "This shows what the training provider has recorded.", enrolments: [enrolment()], upcoming: [{ cohortName: "March 2020", title: "Module 2", date: "2099-03-10", startTime: "08:00:00", endTime: "12:00:00" }] } })
    wrap(<LearnerPortalHomePage />)
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy()
    expect(screen.getByText("5 sessions recorded: 3 present, 1 late, 1 absent, 0 excused")).toBeTruthy()
    expect(screen.getByText("No outcome recorded yet")).toBeTruthy()
    expect(screen.getByText(/08:00 to 12:00 · Module 2 · March 2020/)).toBeTruthy()
    expect(screen.queryByText(/competent/i)).toBeNull()
    expect(portalGet).toHaveBeenCalledWith("/api/v1/training-provider/portal/learner/me")
  })

  it("says plainly when nothing could be loaded", async () => {
    portalGet.mockRejectedValue(new Error("no"))
    wrap(<LearnerPortalHomePage />)
    expect((await screen.findByRole("alert")).textContent).toContain("Could not load your records")
  })
})

describe("learner accept invite", () => {
  const renderAccept = (path: string) => wrap(<Routes><Route path="/training-provider/portal/learner/accept-invite" element={<LearnerPortalAcceptInvitePage />} /></Routes>, path)
  it("needs the token from the email", () => {
    renderAccept("/training-provider/portal/learner/accept-invite")
    expect(screen.getByText(/missing its token/)).toBeTruthy()
  })
  it("will not accept two different passwords", async () => {
    renderAccept("/training-provider/portal/learner/accept-invite?token=abc")
    fireEvent.change(screen.getByLabelText("Your full name"), { target: { value: "Thandi Nkosi" } })
    fireEvent.change(screen.getByLabelText(/^Password/), { target: { value: "longenough1" } })
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "different11" } })
    fireEvent.click(screen.getByText("Accept invite and sign in"))
    expect((await screen.findByRole("alert")).textContent).toBe("The two passwords are not the same")
    expect(portalPost).not.toHaveBeenCalled()
  })
})

describe("employer programme learners", () => {
  it("lists consented learners and explains the ones that are not shown", async () => {
    portalGet.mockResolvedValue({ data: { note: "This shows what the training provider has recorded.", hiddenForConsent: 2, rows: [{ learnerNo: "LRN-00001", fullName: "Thandi Nkosi", enrolment: enrolment() }] } })
    wrap(<TrainProvEmployerLearners />)
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy()
    expect(screen.getByText(/2 enrolments are not shown because the learner's consent has not been recorded/)).toBeTruthy()
    expect(screen.getByText("Health care assistant (HC1)")).toBeTruthy()
  })
})

describe("staff learner portal access", () => {
  const grant = (over: object = {}) => ({ id: "g1", email: "t@example.co.za", status: "ACCEPTED", reason: "Learner asked to see her timetable", acceptedAt: "2020-03-02T08:00:00Z", endedReason: null, createdAt: "2020-03-01T08:00:00Z", ...over })
  const open = async (grants: unknown[] = [], props: object = {}) => {
    get.mockResolvedValue({ data: { data: grants } })
    post.mockResolvedValue({ data: { data: grant() } })
    wrap(<TrainProvLearnerPortalAccess learnerId="l1" defaultEmail="t@example.co.za" canManage archived={false} {...props} />)
    fireEvent.click(screen.getByText("Learner portal access"))
  }

  it("needs a written reason to invite", async () => {
    await open([])
    fireEvent.click(await screen.findByText("Invite the learner"))
    fireEvent.change(screen.getByLabelText("Reason for access"), { target: { value: "short" } })
    fireEvent.click(screen.getByText("Send invite"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for access"), { target: { value: "Learner asked to see her timetable" } })
    fireEvent.click(screen.getByText("Send invite"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/learners\/l1\/portal-access$/), { email: "t@example.co.za", reason: "Learner asked to see her timetable" }))
  })

  it("does not offer a second invite while access is live, and ending needs a reason", async () => {
    await open([grant()])
    expect(await screen.findByText("Has access")).toBeTruthy()
    expect(screen.queryByText("Invite the learner")).toBeNull()
    fireEvent.click(screen.getByText("End access"))
    fireEvent.click(screen.getByText("End access"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    fireEvent.change(screen.getByLabelText("Reason for ending access"), { target: { value: "Learner left the programme" } })
    fireEvent.click(screen.getByText("End access"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/g1\/end$/), { reason: "Learner left the programme" }))
  })

  it("keeps ended access in the list and allows a new invite", async () => {
    await open([grant({ status: "ENDED", endedReason: "Learner left the programme" })])
    expect(await screen.findByText(/Ended: Learner left the programme/)).toBeTruthy()
    expect(screen.getByText("Invite the learner")).toBeTruthy()
  })

  it("offers nothing to change without permission", async () => {
    await open([grant()], { canManage: false })
    expect(await screen.findByText("Has access")).toBeTruthy()
    expect(screen.queryByText("End access")).toBeNull()
    expect(screen.queryByText("Invite the learner")).toBeNull()
  })
})
