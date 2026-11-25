// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ClientRequestsTab from "./ClientRequestsTab"

const get = vi.fn()
const post = vi.fn()
let manage = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => manage, usePermission: () => manage }))

const query = (over: object = {}) => ({ id: "q1", clientId: "c1", clientName: "Acme", subject: "VAT question", status: "OPEN", raisedByName: "Pat", createdAt: "2027-03-01T08:00:00Z", lastMessageAt: "2027-03-01T08:00:00Z", messageCount: 1, ...over })
const request = (over: object = {}) => ({ id: "r1", clientId: "c1", clientName: "Acme", title: "Bank statements", description: null, documentType: null, dueDate: "2027-03-10", status: "OPEN", requestedAt: "2027-03-01T08:00:00Z", closedAt: null, closeNote: null, late: false, ...over })
const thread = { query: query(), messages: [{ authorType: "CLIENT", authorId: "u", authorName: "Pat", body: "Is this VAT due?", createdAt: "2027-03-01T08:00:00Z" }] }

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientRequestsTab clientId="c1" /></QueryClientProvider>)
}

describe("ClientRequestsTab", () => {
  afterEach(cleanup)
  beforeEach(() => {
    manage = true
    get.mockReset(); post.mockReset()
    get.mockImplementation((url: string) => {
      if (url.endsWith("/clients/c1/portal-queries")) return Promise.resolve({ data: { data: [query()] } })
      if (url.endsWith("/clients/c1/document-requests")) return Promise.resolve({ data: { data: [request()] } })
      return Promise.resolve({ data: { data: thread } })
    })
    post.mockResolvedValue({ data: { data: thread } })
  })

  it("lists the client's queries and what we asked for", async () => {
    renderIt()
    expect(await screen.findByText("VAT question")).toBeTruthy()
    expect(screen.getByText("Bank statements")).toBeTruthy()
    expect(document.body.textContent).toContain("Waiting for us")
  })

  it("opens a thread and sends a reply", async () => {
    renderIt()
    fireEvent.click(await screen.findByText("VAT question"))
    expect(await screen.findByText("Is this VAT due?")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Your reply"), { target: { value: "Yes, by the 25th." } })
    fireEvent.click(screen.getByRole("button", { name: "Send reply" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/portal-queries/q1/messages", { body: "Yes, by the 25th." }))
  })

  it("will not send an empty reply", async () => {
    renderIt()
    fireEvent.click(await screen.findByText("VAT question"))
    await screen.findByLabelText("Your reply")
    fireEvent.click(screen.getByRole("button", { name: "Send reply" }))
    expect(await screen.findByText("Write a message first")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("asks for a document", async () => {
    renderIt()
    fireEvent.click(await screen.findByRole("button", { name: "Ask for a document" }))
    fireEvent.click(screen.getByRole("button", { name: "Ask" }))
    expect(await screen.findByText("Say what you are asking for")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("What do you need?"), { target: { value: "Payslips" } })
    fireEvent.click(screen.getByRole("button", { name: "Ask" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/clients/c1/document-requests", { title: "Payslips", description: null, dueDate: null }))
  })

  it("needs a real reason to close a request", async () => {
    renderIt()
    fireEvent.click(await screen.findByRole("button", { name: "Close" }))
    fireEvent.change(screen.getByLabelText("Why is this done?"), { target: { value: "ok" } })
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }))
    expect(await screen.findByText(/at least 10 characters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Why is this done?"), { target: { value: "Checked and filed" } })
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/document-requests/r1/close", { note: "Checked and filed" }))
  })

  it("read-only users cannot reply, ask or close", async () => {
    manage = false
    renderIt()
    fireEvent.click(await screen.findByText("VAT question"))
    await screen.findByText("Is this VAT due?")
    expect(screen.queryByLabelText("Your reply")).toBeNull()
    expect(screen.queryByRole("button", { name: "Ask for a document" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Close" })).toBeNull()
  })
})
