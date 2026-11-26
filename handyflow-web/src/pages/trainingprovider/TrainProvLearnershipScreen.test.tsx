// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvLearnershipPanel from "./TrainProvLearnershipPanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const agreement = (over: object = {}) => ({
  id: "a1", agreementNo: "LSH-00001", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerName: "T L", programmeTitle: "P", clientId: null, employerName: "Acme Stores", employerContact: null,
  startDate: "2020-03-01", endDate: null, status: "DRAFT", learnerSignedOn: null, employerSignedOn: null, providerSignedOn: null, registrationRef: null, registrationRecordedOn: null,
  endedOn: null, endReason: null, cancelReason: null,
  summary: "Draft. Not yet recorded as signed. This is a record of the paper agreement. It does not confirm that the learnership is registered or approved.", ...over,
})
const signed = (over: object = {}) => agreement({ status: "SIGNED", learnerSignedOn: "2020-03-01", employerSignedOn: "2020-03-01", providerSignedOn: "2020-03-02", ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let rows: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); rows = [agreement()]
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /clients/.test(url) ? [] : rows } }))
  post.mockResolvedValue({ data: { data: {} } })
  put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

const open = (status = "IN_PROGRESS") => { wrap(<TrainProvLearnershipPanel enrolmentId="e1" enrolmentStatus={status} startDate="2020-01-01" />); fireEvent.click(screen.getByText("Learnership")) }

describe("learnership panel", () => {
  it("loads nothing until opened and never says the learnership is registered or approved", async () => {
    wrap(<TrainProvLearnershipPanel enrolmentId="e1" enrolmentStatus="IN_PROGRESS" startDate="2020-01-01" />)
    expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Learnership"))
    expect(await screen.findByText("Acme Stores")).toBeTruthy()
    expect(screen.getAllByText(/does not confirm that the learnership is registered or approved/).length).toBeGreaterThan(0)
  })

  it("adds an agreement only with an employer and start day", async () => {
    rows = []
    open()
    fireEvent.click(await screen.findByText("Add an agreement"))
    fireEvent.click(screen.getByText("Add agreement"))
    expect(await screen.findByText(/name of the employer/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Employer"), { target: { value: "Acme Stores" } })
    fireEvent.change(screen.getByLabelText("Planned start"), { target: { value: "2020-03-01" } })
    fireEvent.click(screen.getByText("Add agreement"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learnership-agreements", { enrolmentId: "e1", clientId: null, employerName: "Acme Stores", employerContact: null, startDate: "2020-03-01", endDate: null }))
  })

  it("offers no new agreement while one is live or the enrolment is closed", async () => {
    open()
    await screen.findByText("Acme Stores")
    expect(screen.queryByText("Add an agreement")).toBeNull()
    expect(screen.getByText(/already has a live agreement/)).toBeTruthy()
    cleanup()
    rows = []
    open("CANCELLED")
    await screen.findByText(/No learnership agreement yet/)
    expect(screen.queryByText("Add an agreement")).toBeNull()
  })

  it("needs all three signing days", async () => {
    open()
    fireEvent.click(await screen.findByText("Record signing"))
    fireEvent.change(screen.getByLabelText("Learner signed on"), { target: { value: "2020-03-01" } })
    fireEvent.click(screen.getByText("Save signing"))
    expect(await screen.findByText(/each of the learner, the employer and the provider/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Employer signed on"), { target: { value: "2020-03-01" } })
    fireEvent.change(screen.getByLabelText("Provider signed on"), { target: { value: "2020-03-02" } })
    fireEvent.click(screen.getByText("Save signing"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learnership-agreements/a1/sign", { learnerSignedOn: "2020-03-01", employerSignedOn: "2020-03-01", providerSignedOn: "2020-03-02" }))
  })

  it("records a registration reference as entered, once", async () => {
    rows = [signed()]
    open()
    fireEvent.click(await screen.findByText("Record registration reference"))
    expect(screen.getByText(/not checked against any register/)).toBeTruthy()
    fireEvent.click(screen.getByText("Save reference"))
    expect(await screen.findByText(/exactly as it appears/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Registration reference"), { target: { value: "REG/123" } })
    fireEvent.change(screen.getByLabelText("Day recorded"), { target: { value: "2020-03-05" } })
    fireEvent.click(screen.getByText("Save reference"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learnership-agreements/a1/registration", { reference: "REG/123", recordedOn: "2020-03-05" }))
  })

  it("hides cancelling and the reference button once a reference is recorded", async () => {
    rows = [signed({ registrationRef: "REG/123", registrationRecordedOn: "2020-03-05" })]
    open()
    await screen.findByText("Acme Stores")
    expect(screen.queryByText("Record registration reference")).toBeNull()
    expect(screen.queryByText("Cancel agreement")).toBeNull()
    expect(screen.getByText("End agreement")).toBeTruthy()
  })

  it("ends a signed agreement with a day and a reason", async () => {
    rows = [signed()]
    open()
    fireEvent.click(await screen.findByText("End agreement"))
    fireEvent.change(screen.getByLabelText("Day the agreement ended"), { target: { value: "2020-09-01" } })
    fireEvent.click(screen.getByText("Save as ended"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason the agreement ended"), { target: { value: "Learner completed the term" } })
    fireEvent.click(screen.getByText("Save as ended"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learnership-agreements/a1/end", { endedOn: "2020-09-01", reason: "Learner completed the term" }))
  })

  it("cancels a draft only with a reason", async () => {
    open()
    fireEvent.click(await screen.findByText("Cancel agreement"))
    fireEvent.click(screen.getByText("Cancel the agreement"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Reason for cancelling the agreement"), { target: { value: "Employer pulled out" } })
    fireEvent.click(screen.getByText("Cancel the agreement"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learnership-agreements/a1/cancel", { reason: "Employer pulled out" }))
  })
})
