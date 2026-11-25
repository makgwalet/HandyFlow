// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { CsPortalQueries, CsPortalRequests } from "./CsPortalContact"

const api = vi.hoisted(() => ({ queries: vi.fn(), query: vi.fn(), raise: vi.fn(), reply: vi.fn(), requests: vi.fn(), upload: vi.fn() }))
vi.mock("../../api/csPortal.api", () => ({ csPortalApi: api }))

const q = { id: "q1", subject: "Tax number", status: "ANSWERED", createdAt: "2027-03-01T08:00:00Z", lastMessageAt: "2027-03-02T08:00:00Z", messageCount: 2 }
const detail = { query: q, messages: [{ fromClient: true, author: "Naledi", body: "Where is my tax number?", createdAt: "2027-03-01T08:00:00Z" }, { fromClient: false, author: "The practice", body: "It is on the letter.", createdAt: "2027-03-02T08:00:00Z" }] }
const req = (over: object = {}) => ({ id: "r1", title: "Bank statements", description: null, documentType: null, dueDate: "2027-03-10", status: "OPEN", requestedAt: "2027-03-01T08:00:00Z", late: false, ...over })

afterEach(cleanup)
beforeEach(() => { vi.clearAllMocks(); api.queries.mockResolvedValue([q]); api.query.mockResolvedValue(detail); api.requests.mockResolvedValue([req()]) })

describe("portal queries", () => {
  it("shows the practice as one name and lets the client reply", async () => {
    api.reply.mockResolvedValue(detail)
    render(<CsPortalQueries clientId="c1" />)
    fireEvent.click(await screen.findByText("Tax number"))
    expect(await screen.findByText("It is on the letter.")).toBeTruthy()
    expect(document.body.textContent).toContain("The practice")
    fireEvent.change(screen.getByLabelText("Your reply"), { target: { value: "Thank you" } })
    fireEvent.click(screen.getByRole("button", { name: "Send reply" }))
    await waitFor(() => expect(api.reply).toHaveBeenCalledWith("q1", "Thank you"))
  })
  it("needs a subject and a message before asking", async () => {
    api.raise.mockResolvedValue(detail)
    render(<CsPortalQueries clientId="c1" />)
    fireEvent.click(await screen.findByRole("button", { name: "Ask a question" }))
    fireEvent.click(screen.getByRole("button", { name: "Send" }))
    expect(await screen.findByText("Give your question a subject")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Subject"), { target: { value: "VAT" } })
    fireEvent.change(screen.getByLabelText("Your question"), { target: { value: "When is it due?" } })
    fireEvent.click(screen.getByRole("button", { name: "Send" }))
    await waitFor(() => expect(api.raise).toHaveBeenCalledWith({ clientId: "c1", subject: "VAT", body: "When is it due?" }))
  })
  it("shows the server's reason when a question is refused", async () => {
    api.raise.mockRejectedValue({ response: { data: { message: "You already have 5 queries waiting" } } })
    render(<CsPortalQueries clientId="c1" />)
    fireEvent.click(await screen.findByRole("button", { name: "Ask a question" }))
    fireEvent.change(screen.getByLabelText("Subject"), { target: { value: "VAT" } })
    fireEvent.change(screen.getByLabelText("Your question"), { target: { value: "Hi" } })
    fireEvent.click(screen.getByRole("button", { name: "Send" }))
    expect(await screen.findByText("You already have 5 queries waiting")).toBeTruthy()
  })
})

describe("portal document requests", () => {
  it("uploads a good file", async () => {
    api.upload.mockResolvedValue(req({ status: "RECEIVED" }))
    render(<CsPortalRequests clientId="c1" />)
    const input = await screen.findByLabelText("File for Bank statements")
    const file = new File(["x"], "march.pdf", { type: "application/pdf" })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => expect(api.upload).toHaveBeenCalledWith("r1", file))
  })
  it("refuses a bad file before sending it", async () => {
    render(<CsPortalRequests clientId="c1" />)
    const input = await screen.findByLabelText("File for Bank statements")
    fireEvent.change(input, { target: { files: [new File(["x"], "virus.exe")] } })
    expect(await screen.findByText(/Use a PDF, JPG, PNG, XLSX, DOCX or CSV/)).toBeTruthy()
    expect(api.upload).not.toHaveBeenCalled()
  })
  it("says so when nothing is asked", async () => {
    api.requests.mockResolvedValue([])
    render(<CsPortalRequests clientId="c1" />)
    expect(await screen.findByText("Nothing has been asked of you.")).toBeTruthy()
  })
})
