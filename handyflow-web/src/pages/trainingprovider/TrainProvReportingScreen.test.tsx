// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvReportingTab from "./TrainProvReportingTab"
import TrainProvObligationsPanel from "./TrainProvObligationsPanel"
import TrainProvPeriodsPanel from "./TrainProvPeriodsPanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const obligation = (over: object = {}) => ({
  id: "o1", obligationNo: "OBL-00001", authorityId: "a1", authorityName: "Some authority", programmeId: null, programmeTitle: null, name: "Quarterly report", cadence: "QUARTERLY",
  dueDaysAfterEnd: 30, sourceNote: "Taken from the letter of 3 May", status: "ACTIVE", retiredReason: null, ...over,
})
const period = (over: object = {}) => ({
  id: "p1", periodNo: "RPT-00001", obligationId: "o1", obligationNo: "OBL-00001", obligationName: "Quarterly report", authorityName: "Some authority", programmeTitle: null,
  periodStart: "2020-01-01", periodEnd: "2020-03-31", dueOn: "2020-04-30", dueOverrideReason: null, status: "OPEN", state: "OVERDUE", submittedOn: null, submissionRef: null,
  acknowledgedOn: null, acknowledgementRef: null, waivedReason: null, voidReason: null, note: null, ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let obligations: unknown[] = []
let periods: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); obligations = [obligation()]; periods = [period()]
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("report-obligations") ? obligations : url.includes("report-periods") ? periods : [] } }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("reporting tab", () => {
  it("opens on periods and says nothing is built in and nothing shows compliance", async () => {
    wrap(<TrainProvReportingTab />)
    expect(await screen.findByText(/Quarterly report · /)).toBeTruthy()
    expect(screen.getByText(/Nothing is built in/)).toBeTruthy()
    expect(screen.getByText(/nothing here shows that you are compliant/)).toBeTruthy()
    expect(screen.getAllByText("Overdue").length).toBeGreaterThan(1)
  })
})

describe("obligations", () => {
  it("needs a source note before saving", async () => {
    wrap(<TrainProvObligationsPanel />)
    fireEvent.click(await screen.findByText("Add an obligation"))
    fireEvent.change(screen.getByLabelText("Obligation name"), { target: { value: "Annual report" } })
    fireEvent.change(screen.getByLabelText("Days after the period ends"), { target: { value: "30" } })
    fireEvent.click(screen.getByText("Add obligation"))
    expect((await screen.findAllByText(/Choose the authority/)).length).toBeGreaterThan(0)
    expect(post).not.toHaveBeenCalled()
  })

  it("shows the source note and needs a reason to retire", async () => {
    wrap(<TrainProvObligationsPanel />)
    expect(await screen.findByText(/Source: Taken from the letter of 3 May/)).toBeTruthy()
    fireEvent.click(screen.getByText("Retire"))
    fireEvent.click(screen.getByText("Retire obligation"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Reason for retiring the obligation"), { target: { value: "Authority no longer requires it" } })
    fireEvent.click(screen.getByText("Retire obligation"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/report-obligations/o1/retire", { reason: "Authority no longer requires it" }))
  })
})

describe("periods", () => {
  it("records a submission as a staff record", async () => {
    wrap(<TrainProvPeriodsPanel />)
    fireEvent.click(await screen.findByText("Record as submitted"))
    fireEvent.change(screen.getByLabelText("Reference"), { target: { value: "SUB-1" } })
    fireEvent.click(screen.getByText("Save as submitted"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/report-periods/p1/submit", expect.objectContaining({ submissionRef: "SUB-1", note: null })))
  })

  it("records an acknowledgement only on a submitted period and rejects an earlier day", async () => {
    periods = [period({ status: "SUBMITTED", state: "SUBMITTED", submittedOn: "2020-04-20", submissionRef: "SUB-1" })]
    wrap(<TrainProvPeriodsPanel />)
    expect(await screen.findByText(/Recorded by staff as submitted on 2020-04-20/)).toBeTruthy()
    expect(screen.queryByText("Record as submitted")).toBeNull()
    fireEvent.click(screen.getByText("Record acknowledgement"))
    fireEvent.change(screen.getByLabelText("Day acknowledged"), { target: { value: "2020-04-19" } })
    fireEvent.click(screen.getByText("Save acknowledgement"))
    expect(await screen.findByText(/before it was submitted/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("needs a reason to waive and to void", async () => {
    wrap(<TrainProvPeriodsPanel />)
    fireEvent.click(await screen.findByText("Waive"))
    fireEvent.click(screen.getByText("Waive the period"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for waiving"), { target: { value: "Authority exempted this window" } })
    fireEvent.click(screen.getByText("Waive the period"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/report-periods/p1/waive", { reason: "Authority exempted this window" }))
  })

  it("asks for a reason when the due day differs from the rule", async () => {
    wrap(<TrainProvPeriodsPanel />)
    fireEvent.click(await screen.findByText("Add a period"))
    fireEvent.change(screen.getByLabelText("Obligation"), { target: { value: "o1" } })
    fireEvent.change(screen.getByLabelText("Period start"), { target: { value: "2027-01-01" } })
    fireEvent.change(screen.getByLabelText("Period end"), { target: { value: "2027-03-31" } })
    expect(await screen.findByText(/falls due on 2027-04-30/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Due day"), { target: { value: "2027-05-31" } })
    fireEvent.click(screen.getByText("Add period"))
    expect(await screen.findByText(/differs from the obligation's rule/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("does not offer actions on a void record", async () => {
    periods = [period({ status: "VOID", state: "VOID", voidReason: "Submitted against the wrong period" })]
    wrap(<TrainProvPeriodsPanel />)
    expect(await screen.findByText(/Voided. Reason: Submitted against the wrong period/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Void" })).toBeNull()
  })
})
