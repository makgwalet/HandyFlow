// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvDocumentPanel from "./TrainProvDocumentPanel"
import TrainProvDocumentsList from "./TrainProvDocumentsList"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => true }))

const doc = (over: object = {}) => ({
  id: "d1", documentNo: "DOC-00001", docType: "PARTICIPATION_STATEMENT", enrolmentId: "e1", enrolmentNo: "ENR-00001", learnerId: "l1", learnerName: "Test Learner", programmeTitle: "Safety skills",
  versionNo: 1, issuedOn: "2020-01-01", title: "Statement of participation", body: "This statement confirms participation only.", disclaimer: "Not a QCTO or SAQA certificate.", status: "ISSUED",
  voidReason: null, handedOverOn: null, handedOverTo: null, handedOverMethod: null, completionStillStands: true, ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let docs: unknown[] = []
let outcomes: unknown[] = []
beforeEach(() => {
  get.mockReset(); post.mockReset(); docs = []; outcomes = []
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("learner-documents") ? docs : outcomes } }))
  post.mockResolvedValue({ data: { data: doc() } })
})
afterEach(cleanup)

describe("document panel", () => {
  it("loads nothing until opened, then offers only a statement without a completed outcome", async () => {
    wrap(<TrainProvDocumentPanel enrolmentId="e1" />)
    expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Documents"))
    expect(await screen.findByText(/not a certificate or statement of results/)).toBeTruthy()
    expect(screen.getByText("Issue a participation statement")).toBeTruthy()
    expect(screen.queryByText("Issue a completion letter")).toBeNull()
    expect(screen.getByText(/once a completed outcome is recorded/)).toBeTruthy()
  })

  it("offers a letter when a completed outcome stands and issues it", async () => {
    outcomes = [{ id: "c1", status: "RECORDED", outcome: "COMPLETED" }]
    wrap(<TrainProvDocumentPanel enrolmentId="e1" />)
    fireEvent.click(screen.getByText("Documents"))
    fireEvent.click(await screen.findByText("Issue a completion letter"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learner-documents", { enrolmentId: "e1", docType: "COMPLETION_LETTER" }))
  })

  it("shows the disclaimer when a document is viewed", async () => {
    docs = [doc()]
    wrap(<TrainProvDocumentPanel enrolmentId="e1" />)
    fireEvent.click(screen.getByText("Documents"))
    fireEvent.click(await screen.findByText("View and print"))
    expect(await screen.findByText("Not a QCTO or SAQA certificate.")).toBeTruthy()
  })

  it("checks the hand-over before sending, then records it", async () => {
    docs = [doc()]
    wrap(<TrainProvDocumentPanel enrolmentId="e1" />)
    fireEvent.click(screen.getByText("Documents"))
    fireEvent.click(await screen.findByText("Record hand-over"))
    fireEvent.click(screen.getAllByText("Record hand-over")[1])
    expect(await screen.findByText(/who it was handed over to/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Handed over to"), { target: { value: "The learner" } })
    fireEvent.change(screen.getByLabelText("Hand-over method"), { target: { value: "IN_PERSON" } })
    fireEvent.click(screen.getAllByText("Record hand-over")[1])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learner-documents/d1/hand-over", expect.objectContaining({ handedOverTo: "The learner", method: "IN_PERSON" })))
  })

  it("needs a written reason to void", async () => {
    docs = [doc()]
    wrap(<TrainProvDocumentPanel enrolmentId="e1" />)
    fireEvent.click(screen.getByText("Documents"))
    fireEvent.click(await screen.findByText("Void this document"))
    fireEvent.click(screen.getByText("Void the document"))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason for voiding"), { target: { value: "Issued to the wrong enrolment" } })
    fireEvent.click(screen.getByText("Void the document"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/training-provider/learner-documents/d1/void", { reason: "Issued to the wrong enrolment" }))
  })

  it("warns when a letter's completion was withdrawn", async () => {
    docs = [doc({ docType: "COMPLETION_LETTER", completionStillStands: false })]
    wrap(<TrainProvDocumentPanel enrolmentId="e1" />)
    fireEvent.click(screen.getByText("Documents"))
    expect(await screen.findByText(/has been withdrawn/)).toBeTruthy()
  })
})

describe("documents list", () => {
  it("lists documents with the note", async () => {
    docs = [doc()]
    wrap(<TrainProvDocumentsList />)
    expect(await screen.findByText(/Test Learner · Safety skills/)).toBeTruthy()
    expect(screen.getByText(/not a certificate or statement of results/)).toBeTruthy()
  })
})
