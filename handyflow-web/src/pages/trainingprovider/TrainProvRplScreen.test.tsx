// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvRplPanel from "./TrainProvRplPanel"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const app = (over: object = {}) => ({
  id: "r1", rplNo: "RPL-00001", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerName: "T L", programmeTitle: "P", moduleId: null, moduleTitle: null, claim: "Ten years running stores",
  appliedOn: "2020-03-01", status: "OPEN", outcome: null, decisionBasis: null, assessorName: null, assessorId: null, legacyAssessorText: false, recordedForAssessor: null, decidedOn: null, singleReason: null, confirmedOn: null, withdrawReason: null,
  liveEvidence: 1, unreviewedEvidence: 0, acceptedEvidence: 1, stateLabel: "Open. Evidence is being gathered and reviewed", ...over,
})
const evidence = (over: object = {}) => ({ id: "v1", applicationId: "r1", kind: "DOCUMENT", description: "Reference letter from employer", sourceRef: null, receivedOn: "2020-03-02", status: "RECEIVED", withdrawReason: null, review: null, reviewNote: null, reviewedOn: null, ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let apps: unknown[] = []
let current = app()
let items: unknown[] = []
let eligible: unknown[] = []
const rplAssessor = (over: object = {}) => ({ practitionerId: "x1", practitionerNo: "PRC-00001", name: "Ms Assessor", role: "RPL_ASSESSOR", appointmentId: "ap1", verifiedOn: "2020-01-01", hasLogin: true, ...over })
beforeEach(() => {
  get.mockReset(); post.mockReset(); apps = [app()]; current = app(); eligible = [rplAssessor()]; items = [evidence({ review: "ACCEPTED", reviewNote: "Checked with the employer", reviewedOn: "2020-03-03" })]
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /\/eligible$/.test(url) ? eligible : /rpl-applications\/r1$/.test(url) ? { application: current, evidence: items } : /programmes\//.test(url) ? { versions: [] } : apps } }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

const open = (status = "IN_PROGRESS") => { wrap(<TrainProvRplPanel enrolmentId="e1" enrolmentStatus={status} startDate="2020-01-01" programmeId="p1" versionId="v1" />); fireEvent.click(screen.getByText("Recognition of prior learning")) }

describe("RPL panel", () => {
  it("loads nothing until opened and says an RPL decision is not a completion or certificate", async () => {
    wrap(<TrainProvRplPanel enrolmentId="e1" enrolmentStatus="IN_PROGRESS" startDate="2020-01-01" programmeId="p1" versionId="v1" />)
    expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Recognition of prior learning"))
    expect(await screen.findByText("RPL-00001")).toBeTruthy()
    expect(screen.getByText(/not a completion and not a certificate/)).toBeTruthy()
  })

  it("only offers a new application while the enrolment is open", async () => {
    open("CANCELLED")
    await screen.findByText("RPL-00001")
    expect(screen.queryByText("Add an RPL application")).toBeNull()
  })

  it("checks a new application before sending", async () => {
    apps = []
    open()
    fireEvent.click(await screen.findByText("Add an RPL application"))
    fireEvent.click(screen.getByText("Add application"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Prior learning claimed"), { target: { value: "Ten years running stores" } })
    fireEvent.change(screen.getByLabelText("Day of the application"), { target: { value: "2020-03-01" } })
    fireEvent.click(screen.getByText("Add application"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/rpl-applications", { enrolmentId: "e1", moduleId: null, claim: "Ten years running stores", appliedOn: "2020-03-01" }))
  })

  it("reviews evidence with a written note", async () => {
    items = [evidence()]
    current = app({ unreviewedEvidence: 1, acceptedEvidence: 0 })
    open()
    fireEvent.click(await screen.findByText("Evidence"))
    fireEvent.click(await screen.findByText("Review evidence"))
    fireEvent.click(screen.getByText("Save the review"))
    expect(await screen.findByText(/accepted or queried/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Review of the evidence"), { target: { value: "ACCEPTED" } })
    fireEvent.change(screen.getByLabelText("Review note"), { target: { value: "Checked with the employer" } })
    fireEvent.change(screen.getByLabelText("Day of the review"), { target: { value: "2020-03-03" } })
    fireEvent.click(screen.getByText("Save the review"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/rpl-evidence/v1/review", { review: "ACCEPTED", note: "Checked with the employer", reviewedOn: "2020-03-03" }))
  })

  it("will not record a decision while evidence is unreviewed", async () => {
    current = app({ unreviewedEvidence: 1, acceptedEvidence: 0 })
    apps = [current]
    open()
    fireEvent.click(await screen.findByText("Record decision"))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "NOT_RECOGNISED" } })
    fireEvent.change(screen.getByLabelText("Basis for the decision"), { target: { value: "Evidence did not match the outcomes" } })
    await screen.findByText(/Ms Assessor · PRC-00001/)
    fireEvent.change(screen.getByLabelText("RPL assessor"), { target: { value: "x1" } })
    fireEvent.click(screen.getByText("Save the decision"))
    expect(await screen.findByText(/not been reviewed yet/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("records a decision and needs a reason to confirm alone", async () => {
    open()
    fireEvent.click(await screen.findByText("Record decision"))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "RECOGNISED" } })
    fireEvent.change(screen.getByLabelText("Basis for the decision"), { target: { value: "Evidence matches the outcomes" } })
    await screen.findByText(/Ms Assessor · PRC-00001/)
    fireEvent.change(screen.getByLabelText("RPL assessor"), { target: { value: "x1" } })
    fireEvent.change(screen.getByLabelText("Day of the decision"), { target: { value: "2020-03-04" } })
    fireEvent.click(screen.getByLabelText(/Confirm without a second person/))
    fireEvent.click(screen.getByText("Save the decision"))
    expect(await screen.findByText(/without a second person, say why/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for confirming alone"), { target: { value: "Only one qualified assessor on site" } })
    fireEvent.click(screen.getByText("Save the decision"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/rpl-applications/r1/decide", expect.objectContaining({ outcome: "RECOGNISED", confirmAlone: true, singleReason: "Only one qualified assessor on site", assessorId: "x1" })))
  })

  it("asks how the decision reached us when the assessor has no login", async () => {
    eligible = [rplAssessor({ hasLogin: false })]
    open()
    fireEvent.click(await screen.findByText("Record decision"))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "RECOGNISED" } })
    fireEvent.change(screen.getByLabelText("Basis for the decision"), { target: { value: "Evidence matches the outcomes" } })
    await screen.findByText(/Ms Assessor · PRC-00001/)
    fireEvent.change(screen.getByLabelText("RPL assessor"), { target: { value: "x1" } })
    fireEvent.change(screen.getByLabelText("Day of the decision"), { target: { value: "2020-03-04" } })
    fireEvent.click(screen.getByText("Save the decision"))
    expect(await screen.findByText(/has no login, so the decision is recorded for them/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("How the assessor's decision reached you"), { target: { value: "Signed decision form handed over" } })
    fireEvent.click(screen.getByText("Save the decision"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/rpl-applications/r1/decide", expect.objectContaining({ assessorId: "x1", recordedForAssessor: "Signed decision form handed over" })))
  })

  it("says so when no one holds an RPL assessor appointment", async () => {
    eligible = []
    open()
    fireEvent.click(await screen.findByText("Record decision"))
    expect(await screen.findByText(/An ordinary assessor appointment does not cover RPL/)).toBeTruthy()
  })

  it("lets a second person confirm a waiting decision", async () => {
    current = app({ status: "DECIDED", outcome: "RECOGNISED", decisionBasis: "Evidence matches", assessorName: "Ms Assessor", assessorId: "x1", decidedOn: "2020-03-04", stateLabel: "Decided as recognised. Waiting for a second person to confirm" })
    apps = [current]
    open()
    fireEvent.click(await screen.findByText("Confirm decision"))
    fireEvent.change(screen.getByLabelText("Day confirmed"), { target: { value: "2020-03-05" } })
    fireEvent.click(screen.getByText("Confirm the decision"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/rpl-applications/r1/confirm", { confirmedOn: "2020-03-05" }))
  })

  it("needs a reason to withdraw and offers nothing on a withdrawn application", async () => {
    open()
    fireEvent.click(await screen.findByText("Withdraw application"))
    fireEvent.click(screen.getByText("Withdraw the application"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for withdrawing the application"), { target: { value: "Entered against the wrong learner" } })
    fireEvent.click(screen.getByText("Withdraw the application"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/rpl-applications/r1/withdraw", { reason: "Entered against the wrong learner" }))
    cleanup()
    apps = [app({ status: "WITHDRAWN", withdrawReason: "Entered against the wrong learner" })]
    open()
    await screen.findByText("RPL-00001")
    expect(screen.queryByText("Withdraw application")).toBeNull()
    expect(screen.queryByText("Record decision")).toBeNull()
  })
})
