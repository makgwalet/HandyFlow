import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F2: whoever made a journal cannot post it; a reversal is a draft someone else posts.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "me" } }) }))
const get = vi.fn(), post = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: {
  get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: (...a: any[]) => del(...a) } }))
import JournalEntriesTab from "./JournalEntriesTab"

const j = (id: string, entryNumber: string, status: string, createdBy: string | null) => ({
  id, entryNumber, entryDate: "2026-10-01", description: "Desc " + entryNumber, reference: "", entryType: "MANUAL",
  status, totalDebit: 100, totalCredit: 100, balanced: true, lines: [], createdBy, createdAt: "2026-10-01T00:00:00Z" })
const journals = [j("1", "JE-1", "DRAFT", "me"), j("2", "JE-2", "DRAFT", "someone"), j("3", "JE-3", "POSTED", null)]

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <JournalEntriesTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE", "ACCOUNTING_ADMIN"])
  get.mockReset(); post.mockReset(); del.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.includes("journal-entries") ? { content: journals, totalElements: 3 } : [] } }))
  post.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("JournalEntriesTab review", () => {
  it("offers Post only on drafts someone else made", async () => {
    show()
    await screen.findByText("JE-1")
    expect(screen.getByText("Awaiting another person")).toBeTruthy()
    expect(screen.getAllByText("Post")).toHaveLength(1)
    fireEvent.click(screen.getByText("Post"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/journal-entries/2/post"))
  })

  it("hides Post and Reverse without ACCOUNTING_ADMIN", async () => {
    perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE"])
    show()
    await screen.findByText("JE-1")
    expect(screen.queryByText("Post")).toBeNull()
    expect(screen.queryByText("Reverse")).toBeNull()
    expect(screen.queryByText("Awaiting another person")).toBeNull()
  })

  it("drafts a reversal and says a different person must post it", async () => {
    show()
    await screen.findByText("JE-3")
    fireEvent.click(screen.getByText("Reverse"))
    fireEvent.click(screen.getByText("Draft reversal"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/journal-entries/3/reverse",
      expect.objectContaining({ reversalDate: expect.any(String) })))
    expect(await screen.findByText(/Reversal of JE-3 drafted/)).toBeTruthy()
  })

  it("discards a draft after confirmation", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    show()
    await screen.findByText("JE-2")
    fireEvent.click(screen.getByLabelText("Discard draft JE-2"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/accounting/journal-entries/2"))
  })
})
