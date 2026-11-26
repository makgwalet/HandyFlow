// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvFundingPanel from "./TrainProvFundingPanel"
import TrainProvFundersList from "./TrainProvFundersList"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const funder = (over: object = {}) => ({ id: "f1", funderNo: "FND-00001", name: "Acme Stores", funderType: "EMPLOYER", clientId: null, clientName: null, contactName: null, contactEmail: null, contactPhone: null, note: null, status: "ACTIVE", retireReason: null, activeLinks: 0, ...over })
const link = (over: object = {}) => ({ id: "l1", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerName: "T L", funderId: "f1", funderNo: "FND-00001", funderName: "Acme Stores", funderType: "EMPLOYER", fundedFrom: "2020-03-01", agreementRef: "AGR-1", funderReference: null, note: null, status: "ACTIVE", endedOn: null, endReason: null, ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let funders: unknown[] = []
let links: unknown[] = []
let summary = ""
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); funders = [funder()]; links = [link()]; summary = "Recorded as funded by Acme Stores. This records who is paying. It holds no amounts and does not mean any funding was approved or paid."
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /\/funding$/.test(url) ? { links, summary } : /\/funders$/.test(url) ? funders : /clients/.test(url) ? [] : [] } }))
  post.mockResolvedValue({ data: { data: {} } })
  put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

const openPanel = (status = "IN_PROGRESS") => { wrap(<TrainProvFundingPanel enrolmentId="e1" enrolmentStatus={status} startDate="2020-01-01" />); fireEvent.click(screen.getByText("Funding")) }

describe("funding panel", () => {
  it("loads nothing until opened and says a link is not approval or payment", async () => {
    wrap(<TrainProvFundingPanel enrolmentId="e1" enrolmentStatus="IN_PROGRESS" startDate="2020-01-01" />)
    expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Funding"))
    expect(await screen.findByText(/Recorded as funded by Acme Stores/)).toBeTruthy()
    expect(screen.getAllByText(/does not mean any funding was approved or paid/).length).toBeGreaterThan(0)
  })

  it("links a funder only after one is chosen and not twice", async () => {
    links = []
    summary = "No funder is recorded for this enrolment."
    openPanel()
    fireEvent.click(await screen.findByText("Link a funder"))
    fireEvent.click(screen.getByText("Link the funder"))
    expect(await screen.findByText("Choose the funder")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    await screen.findByText(/Acme Stores \(Employer\)/)
    fireEvent.change(screen.getByLabelText("Funder"), { target: { value: "f1" } })
    fireEvent.change(screen.getByLabelText("Funding starts"), { target: { value: "2020-03-01" } })
    fireEvent.click(screen.getByText("Link the funder"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/enrolment-funding", { enrolmentId: "e1", funderId: "f1", fundedFrom: "2020-03-01", agreementRef: null, funderReference: null, note: null }))
  })

  it("blocks a second link to the same live funder", async () => {
    openPanel()
    await screen.findByText("AGR-1", { exact: false })
    fireEvent.click(screen.getByText("Link a funder"))
    await screen.findByText(/Acme Stores \(Employer\)/)
    fireEvent.change(screen.getByLabelText("Funder"), { target: { value: "f1" } })
    fireEvent.click(screen.getByText("Link the funder"))
    expect(await screen.findByText(/already linked to that funder/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("ends a link with a day and a reason", async () => {
    openPanel()
    fireEvent.click(await screen.findByText("End funding"))
    fireEvent.change(screen.getByLabelText("Day the funding ended"), { target: { value: "2020-04-01" } })
    fireEvent.click(screen.getByText("Save as ended"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason the funding ended"), { target: { value: "Employer withdrew sponsorship" } })
    fireEvent.click(screen.getByText("Save as ended"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/enrolment-funding/l1/end", { endedOn: "2020-04-01", reason: "Employer withdrew sponsorship" }))
  })

  it("offers no new link on a cancelled enrolment or action on an ended link", async () => {
    links = [link({ status: "ENDED", endedOn: "2020-04-01", endReason: "Sponsor stopped" })]
    openPanel("CANCELLED")
    await screen.findByText(/Sponsor stopped/)
    expect(screen.queryByText("Link a funder")).toBeNull()
    expect(screen.queryByText("End funding")).toBeNull()
  })
})

describe("funders list", () => {
  it("adds a funder only with a kind and a name", async () => {
    funders = []
    wrap(<TrainProvFundersList />)
    fireEvent.click(await screen.findByText("Add a funder"))
    fireEvent.click(screen.getByText("Add funder"))
    expect(await screen.findByText(/kind of funder/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Kind of funder"), { target: { value: "SETA" } })
    fireEvent.change(screen.getByLabelText("Funder name"), { target: { value: "Some SETA" } })
    fireEvent.click(screen.getByText("Add funder"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/funders", expect.objectContaining({ name: "Some SETA", funderType: "SETA", clientId: null })))
  })

  it("retires a funder only with a reason", async () => {
    wrap(<TrainProvFundersList />)
    fireEvent.click(await screen.findByText("Retire funder"))
    fireEvent.click(screen.getByText("Retire the funder"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for retiring the funder"), { target: { value: "Stopped sponsoring learners" } })
    fireEvent.click(screen.getByText("Retire the funder"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/funders/f1/retire", { reason: "Stopped sponsoring learners" }))
  })

  it("offers nothing on a retired funder", async () => {
    funders = [funder({ status: "RETIRED", retireReason: "Stopped sponsoring learners" })]
    wrap(<TrainProvFundersList />)
    await screen.findByText(/Stopped sponsoring learners/)
    expect(screen.queryByText("Retire funder")).toBeNull()
    expect(screen.queryByText("Edit")).toBeNull()
  })
})
