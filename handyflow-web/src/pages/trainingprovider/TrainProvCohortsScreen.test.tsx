// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvCohortsTab from "./TrainProvCohortsTab"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const cohort = (over: object = {}) => ({
  id: "c1", cohortNo: "COH-00001", name: "Feb intake", programmeId: "p1", programmeTitle: "Safety skills", programmeVersionNo: 1, siteId: "s1", siteName: "Main campus",
  startDate: "2027-02-01", endDate: null, capacity: 20, notes: null, status: "PLANNED", statusReason: null, members: 1, facilitators: 1, nextStatuses: ["CANCELLED", "RUNNING"], ...over,
})
const slot = (over: object = {}) => ({
  id: "t1", cohortId: "c1", cohortName: "Feb intake", slotDate: "2027-02-10", startTime: "09:00:00", endTime: "11:00:00", title: "Module 1", moduleId: null, moduleTitle: null,
  venueId: null, venueName: "Room 1", practitionerId: "r1", facilitatorName: "Test Person", facilitatorStanding: "VERIFIED_IN_FORCE", facilitatorStandingNote: "Credential checked and in force",
  status: "SCHEDULED", cancelledReason: null, ...over,
})
const detail = () => ({
  cohort: cohort(),
  members: [{ id: "m1", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerId: "l1", learnerName: "Test Learner", enrolmentStatus: "ENROLLED", status: "ACTIVE", removedReason: null, addedAt: "2026-11-25T10:00:00Z" }],
  facilitators: [{ id: "f1", practitionerId: "r1", practitionerNo: "PRC-00001", name: "Test Person", appointmentId: "a1", standing: "VERIFIED_IN_FORCE", standingNote: "", status: "ACTIVE", removedReason: null }],
  history: [],
})
const register = (over: object = {}) => ({
  slotId: "t1", cohortId: "c1", cohortName: "Feb intake", title: "Module 1", slotDate: "2027-02-10", startTime: "09:00:00", endTime: "11:00:00", slotStatus: "SCHEDULED",
  canRecord: true, cannotRecordBecause: null,
  rows: [{ memberId: "m1", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerId: "l1", learnerName: "Test Learner", attendanceId: null, status: null, note: null, corrections: 0 }], ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => {
    let data: unknown = []
    if (url.endsWith("/cohorts")) data = [cohort()]
    else if (url.endsWith("/cohorts/c1")) data = detail()
    else if (url.endsWith("/cohorts/c1/timetable")) data = [slot()]
    else if (url.endsWith("/timetable/t1/attendance")) data = register()
    else if (url.endsWith("/attendance-summary")) data = { cohortId: "c1", note: "Counts of what was recorded. It is not a result.", rows: [{ memberId: "m1", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerId: "l1", learnerName: "Test Learner", expected: 2, attended: 1, absent: 0, excused: 0, unrecorded: 1, ratePercent: 100 }] }
    else if (url.endsWith("/timetable")) data = [slot()]
    return Promise.resolve({ data: { data } })
  })
  post.mockResolvedValue({ data: { data: register() } })
})
afterEach(cleanup)

describe("cohort screens", () => {
  it("lists cohorts and opens one", async () => {
    wrap(<TrainProvCohortsTab />)
    fireEvent.click(await screen.findByText("Feb intake"))
    expect(await screen.findByText("Facilitators")).toBeTruthy()
    expect(screen.getByText("Test Learner")).toBeTruthy()
  })

  it("asks for a reason before cancelling a cohort", async () => {
    wrap(<TrainProvCohortsTab />)
    fireEvent.click(await screen.findByText("Feb intake"))
    fireEvent.click(await screen.findByText("Cancel the cohort"))
    fireEvent.click(screen.getByText("Confirm"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for cancelling the cohort"), { target: { value: "Too few learners enrolled" } })
    fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][1]).toEqual({ toStatus: "CANCELLED", reason: "Too few learners enrolled" })
  })

  it("says finishing a cohort does not mean learners have completed", async () => {
    const running = cohort({ status: "RUNNING", nextStatuses: ["CANCELLED", "COMPLETED"] })
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("/cohorts") ? [running] : url.endsWith("/cohorts/c1") ? { ...detail(), cohort: running } : [] } }))
    wrap(<TrainProvCohortsTab />)
    fireEvent.click(await screen.findByText("Feb intake"))
    fireEvent.click(await screen.findByText("Mark as completed"))
    expect(screen.getByText(/does not say any learner has completed or is competent/)).toBeTruthy()
  })

  it("warns on the timetable when a facilitator is not in force on the slot date", async () => {
    get.mockImplementation((url: string) => {
      let data: unknown = []
      if (url.endsWith("/cohorts")) data = [cohort()]
      else if (url.endsWith("/cohorts/c1")) data = detail()
      else if (url.endsWith("/cohorts/c1/timetable")) data = [slot({ facilitatorStanding: "EXPIRED", facilitatorStandingNote: "Expired" })]
      return Promise.resolve({ data: { data } })
    })
    wrap(<TrainProvCohortsTab />)
    fireEvent.click(await screen.findByText("Feb intake"))
    fireEvent.click(await screen.findByText("Timetable"))
    expect((await screen.findByRole("alert")).textContent).toContain("not checked and in force on this date")
  })

  it("flags a scheduled slot whose listed needs are not fully booked, as a prompt to check", async () => {
    get.mockImplementation((url: string) => {
      let data: unknown = []
      if (url.endsWith("/cohorts")) data = [cohort()]
      else if (url.endsWith("/cohorts/c1")) data = detail()
      else if (url.endsWith("/cohorts/c1/timetable")) data = [slot()]
      else if (url.endsWith("/cohorts/c1/slot-resource-needs")) data = { cohortId: "c1", note: "A prompt to check.", slots: [{ slotId: "t1", notCovered: 2, rows: [] }] }
      return Promise.resolve({ data: { data } })
    })
    wrap(<TrainProvCohortsTab />)
    fireEvent.click(await screen.findByText("Feb intake"))
    fireEvent.click(await screen.findByText("Timetable"))
    expect((await screen.findByRole("status")).textContent).toBe("2 listed needs are not fully booked for this session. Check the bookings.")
  })

  it("takes a register: only marked learners are sent, and an excuse needs a reason", async () => {
    wrap(<TrainProvCohortsTab />)
    fireEvent.click(await screen.findByText("Feb intake"))
    fireEvent.click(await screen.findByText("Attendance"))
    fireEvent.click(await screen.findByText("Module 1"))
    fireEvent.click(await screen.findByText("Save the register"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least one")
    fireEvent.change(screen.getByLabelText("Attendance for Test Learner"), { target: { value: "EXCUSED" } })
    fireEvent.click(screen.getByText("Save the register"))
    expect((await screen.findByRole("alert")).textContent).toContain("excused")
    fireEvent.change(screen.getByLabelText("Note for Test Learner"), { target: { value: "Medical note sighted" } })
    fireEvent.click(screen.getByText("Save the register"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][0]).toContain("/timetable/t1/attendance")
    expect(post.mock.calls[0][1]).toEqual({ entries: [{ memberId: "m1", status: "EXCUSED", note: "Medical note sighted" }] })
  })

  it("shows the attendance counts with a note that a rate is not a result", async () => {
    wrap(<TrainProvCohortsTab />)
    fireEvent.click(await screen.findByText("Feb intake"))
    fireEvent.click(await screen.findByText("Attendance"))
    expect(await screen.findByText(/It is not a result and does not say a learner is competent/)).toBeTruthy()
    expect(await screen.findByText(/100% of recorded sessions/)).toBeTruthy()
  })
})
