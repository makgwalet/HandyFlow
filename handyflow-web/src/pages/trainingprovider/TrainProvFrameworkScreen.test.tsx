// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvFrameworkPanel from "./TrainProvFrameworkPanel"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

const row = (over: object = {}) => ({
  id: "a1", title: "Skills development accreditation", accreditationNumber: "ACC-1", status: "APPROVED", effectiveStatus: "APPROVED", currency: "CURRENT", currencyNote: null,
  endedOn: null, supersededById: null, effectiveFrom: null, approvalDate: "2020-03-01", expiryDate: "2030-03-01", reviewDueOn: null, lastVerifiedOn: "2020-03-02",
  sourceType: null, sourceReference: null, sourceVersionDate: null, sourceLocation: null, liveScopes: 2, sites: 1, verifiedEvidence: 1, pendingEvidence: 0, flags: [], ...over,
})
const auth = (name: string, type: string, rank: number, rows: unknown[]) => ({ id: name, name, authorityType: type, rank, status: "ACTIVE", liveObligations: 1, accreditations: rows })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let framework: unknown[] = []
let evidence: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset()
  framework = [auth("QCTO", "QCTO", 0, [row(), row({ id: "a2", title: "Old registration", currency: "HISTORICAL", currencyNote: "Replaced by the new registration", endedOn: "2020-01-01" })]), auth("SAQA", "SAQA", 2, [])]
  evidence = []
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: /evidence$/.test(url) ? evidence : framework } }))
  post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("authority overview", () => {
  it("lists authorities with current and historical records kept apart and what was recorded", async () => {
    wrap(<TrainProvFrameworkPanel canManage />)
    expect(await screen.findByText("Skills development accreditation")).toBeTruthy()
    expect(screen.getByText("QCTO")).toBeTruthy()
    expect(screen.getByText("SAQA")).toBeTruthy()
    expect(screen.getByText("Historical")).toBeTruthy()
    expect(screen.getByText(/Historical: Replaced by the new registration \(stopped applying 2020-01-01\)/)).toBeTruthy()
    expect(screen.getByText("Nothing recorded for this authority.")).toBeTruthy()
  })

  it("shows prompts, not findings", async () => {
    framework = [auth("QCTO", "QCTO", 0, [row({ flags: ["NO_VERIFIED_EVIDENCE", "REVIEW_OVERDUE"], verifiedEvidence: 0 })])]
    wrap(<TrainProvFrameworkPanel canManage />)
    expect(await screen.findByText("No verified evidence on file")).toBeTruthy()
    expect(screen.getByText("Review overdue")).toBeTruthy()
  })

  it("needs the end date before saving a record as historical", async () => {
    wrap(<TrainProvFrameworkPanel canManage />)
    fireEvent.click((await screen.findAllByText("Current, transitional or historical"))[0])
    fireEvent.change(screen.getByLabelText("Currency"), { target: { value: "HISTORICAL" } })
    fireEvent.change(screen.getByLabelText("Currency note"), { target: { value: "Replaced by the new registration" } })
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText("Enter the date this stopped applying")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Ended on"), { target: { value: "2021-01-01" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/accreditations\/a1\/currency$/), { currency: "HISTORICAL", note: "Replaced by the new registration", endedOn: "2021-01-01", supersededById: null }))
  })

  it("will not save part of a source", async () => {
    wrap(<TrainProvFrameworkPanel canManage />)
    fireEvent.click((await screen.findAllByText("Dates and source"))[0])
    fireEvent.change(screen.getByLabelText("Source reference"), { target: { value: "Letter 22/3" } })
    fireEvent.click(screen.getByText("Save dates and source"))
    expect(await screen.findByText("Say what kind of document the source is")).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Source type"), { target: { value: "LETTER" } })
    fireEvent.click(screen.getByText("Save dates and source"))
    await waitFor(() => expect(put).toHaveBeenCalledWith(expect.stringMatching(/accreditations\/a1\/framework$/), expect.objectContaining({ sourceType: "LETTER", sourceReference: "Letter 22/3" })))
  })

  it("records new evidence as waiting to be checked and verifies it separately", async () => {
    evidence = [{ id: "e1", accreditationId: "a1", kind: "LETTER", description: "Approval letter", reference: null, documentDate: null, receivedOn: "2020-03-02", status: "PENDING", addedBy: "x", decidedBy: null, decisionNote: null }]
    wrap(<TrainProvFrameworkPanel canManage />)
    fireEvent.click((await screen.findAllByText("Evidence"))[0])
    expect(await screen.findByText("Waiting to be checked")).toBeTruthy()
    fireEvent.click(screen.getByText("Verify"))
    fireEvent.click(screen.getAllByText("Verify")[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/evidence\/e1\/decide$/), { toStatus: "VERIFIED", note: null }))
  })

  it("needs a reason to reject evidence", async () => {
    evidence = [{ id: "e1", accreditationId: "a1", kind: "LETTER", description: "Approval letter", reference: null, documentDate: null, receivedOn: "2020-03-02", status: "PENDING", addedBy: "x", decidedBy: null, decisionNote: null }]
    wrap(<TrainProvFrameworkPanel canManage />)
    fireEvent.click((await screen.findAllByText("Evidence"))[0])
    fireEvent.click(await screen.findByText("Reject"))
    fireEvent.click(screen.getAllByText("Reject")[0])
    expect(await screen.findByText(/Say why, in at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
})
