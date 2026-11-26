// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvWorkplacePanel from "./TrainProvWorkplacePanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const placement = (over: object = {}) => ({
  id: "w1", placementNo: "WPL-00001", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerName: "T L", programmeTitle: "P", hostName: "Acme Stores", hostContact: null,
  mentorName: "Ms Mentor", mentorContact: null, agreementRef: null, startDate: "2020-03-01", endDate: null, status: "ACTIVE", startedOn: "2020-03-01", endedOn: null, cancelReason: null,
  nextStatuses: ["COMPLETED", "CANCELLED"], confirmedHours: 8, unconfirmedHours: 4,
  summary: "12 hours logged, 8 confirmed by the mentor and 4 not yet confirmed. This is a record of time spent. It is not a competency decision.", ...over,
})
const entry = (over: object = {}) => ({ id: "g1", placementId: "w1", logDate: "2020-03-02", hours: 8, activity: "Assisted with stock counts", status: "RECORDED", voidReason: null, confirmedOn: null, confirmedByName: null, ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let placements: unknown[] = []
let logs: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); placements = [placement()]; logs = [entry()]
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /workplace-placements\/w1$/.test(url) ? { placement: placement(), logs } : placements } }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

const open = async (status = "IN_PROGRESS") => { wrap(<TrainProvWorkplacePanel enrolmentId="e1" enrolmentStatus={status} startDate="2020-01-01" />); fireEvent.click(screen.getByText("Workplace")) }

describe("workplace panel", () => {
  it("loads nothing until opened and says hours are not a competency decision", async () => {
    wrap(<TrainProvWorkplacePanel enrolmentId="e1" enrolmentStatus="IN_PROGRESS" startDate="2020-01-01" />)
    expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Workplace"))
    expect(await screen.findByText(/Acme Stores/)).toBeTruthy()
    expect(screen.getByText(/not a competency decision\. No required number/)).toBeTruthy()
    expect(screen.getByText(/12 hours logged, 8 confirmed/)).toBeTruthy()
  })

  it("only offers a new placement while the enrolment is open", async () => {
    await open("ON_HOLD")
    await screen.findByText(/Acme Stores/)
    expect(screen.queryByText("Add a placement")).toBeNull()
    expect(screen.getByText(/enrolled or in progress/)).toBeTruthy()
  })

  it("checks a new placement before sending", async () => {
    placements = []
    await open()
    fireEvent.click(await screen.findByText("Add a placement"))
    fireEvent.click(screen.getByText("Add placement"))
    expect(await screen.findByText(/name of the host workplace/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Host workplace"), { target: { value: "Acme Stores" } })
    fireEvent.change(screen.getByLabelText("Mentor name"), { target: { value: "Ms Mentor" } })
    fireEvent.change(screen.getByLabelText("Planned start"), { target: { value: "2020-03-01" } })
    fireEvent.click(screen.getByText("Add placement"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/workplace-placements", expect.objectContaining({ enrolmentId: "e1", hostName: "Acme Stores", endDate: null })))
  })

  it("adds a logbook entry only with sensible hours", async () => {
    await open()
    fireEvent.click(await screen.findByText("Logbook"))
    await screen.findByText(/Assisted with stock counts/)
    fireEvent.click(screen.getByText("Add an entry"))
    fireEvent.change(screen.getByLabelText("Hours"), { target: { value: "25" } })
    fireEvent.change(screen.getByLabelText("What was done"), { target: { value: "Assisted with stock counts" } })
    fireEvent.click(screen.getByText("Save the entry"))
    expect(await screen.findByText(/more than 0 and no more than 24/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Hours"), { target: { value: "8" } })
    fireEvent.change(screen.getByLabelText("Entry day"), { target: { value: "2020-03-03" } })
    fireEvent.click(screen.getByText("Save the entry"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/workplace-placements/w1/logs", { logDate: "2020-03-03", hours: 8, activity: "Assisted with stock counts" }))
  })

  it("records a mentor confirmation by name", async () => {
    await open()
    fireEvent.click(await screen.findByText("Logbook"))
    fireEvent.click(await screen.findByText("Record mentor confirmation"))
    fireEvent.click(screen.getByText("Save confirmation"))
    expect(await screen.findByText(/name of the mentor/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Mentor name for confirmation"), { target: { value: "Ms Mentor" } })
    fireEvent.click(screen.getByText("Save confirmation"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/workplace-logs/g1/confirm", expect.objectContaining({ mentorName: "Ms Mentor" })))
  })

  it("needs a reason to void an entry and to cancel a placement", async () => {
    await open()
    fireEvent.click(await screen.findByText("Logbook"))
    fireEvent.click(await screen.findByText("Void entry"))
    fireEvent.click(screen.getByText("Void the entry"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for voiding the entry"), { target: { value: "Entered against the wrong day" } })
    fireEvent.click(screen.getByText("Void the entry"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/workplace-logs/g1/void", { reason: "Entered against the wrong day" }))
  })

  it("cancels a placement with a reason", async () => {
    await open()
    fireEvent.click(await screen.findByText("Cancel placement"))
    fireEvent.change(screen.getByLabelText("Reason for cancelling the placement"), { target: { value: "Host withdrew the offer" } })
    fireEvent.click(screen.getByText("Cancel the placement"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/workplace-placements/w1/cancel", { reason: "Host withdrew the offer" }))
  })

  it("offers no actions on a completed placement", async () => {
    placements = [placement({ status: "COMPLETED", endedOn: "2020-03-31", nextStatuses: [] })]
    await open()
    await screen.findByText(/Acme Stores/)
    expect(screen.queryByText("Complete placement")).toBeNull()
    expect(screen.queryByText("Cancel placement")).toBeNull()
    expect(screen.queryByText("Edit")).toBeNull()
  })
})
