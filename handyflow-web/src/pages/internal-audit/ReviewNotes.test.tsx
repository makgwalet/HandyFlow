import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReviewNotesPanel from "./ReviewNotesPanel"

vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { api.get.mockReset(); api.post.mockReset() })
const note = (o: any) => ({ id: "n1", workpaperId: "w1", note: "Sample size not explained", raisedBy: "r", raisedAt: "", status: "OPEN", response: null, timesReturned: 0, ...o })

describe("ReviewNotesPanel", () => {
  it("says sign-off is blocked while notes are not cleared", async () => {
    api.get.mockResolvedValue({ data: [note({}), note({ id: "n2", status: "CLEARED" })] })
    wrap(<ReviewNotesPanel workpaperId="w1" />)
    expect(await screen.findByText(/1 not cleared, so this workpaper cannot be signed off/)).toBeTruthy()
  })

  it("adds a note only when it is long enough", async () => {
    api.get.mockResolvedValue({ data: [] })
    api.post.mockResolvedValue({ data: note({}) })
    wrap(<ReviewNotesPanel workpaperId="w1" />)
    const btn = (await screen.findByText("Add note")) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Review note"), { target: { value: "Explain the sample size" } })
    fireEvent.click(btn)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/internal-audit/workpapers/w1/review-notes", { text: "Explain the sample size" }))
  })

  it("offers clear and send back on an answered note", async () => {
    api.get.mockResolvedValue({ data: [note({ status: "RESPONDED", response: "See section 3" })] })
    api.post.mockResolvedValue({ data: note({ status: "CLEARED" }) })
    wrap(<ReviewNotesPanel workpaperId="w1" />)
    fireEvent.click(await screen.findByText("Clear"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/internal-audit/review-notes/n1/clear", {}))
    expect(screen.getByText("Send back")).toBeTruthy()
  })

  it("shows the server's reason when clearing is refused", async () => {
    api.get.mockResolvedValue({ data: [note({ status: "RESPONDED", response: "See section 3" })] })
    api.post.mockRejectedValue({ response: { data: { message: "The person who answered a note cannot clear it" } } })
    wrap(<ReviewNotesPanel workpaperId="w1" />)
    fireEvent.click(await screen.findByText("Clear"))
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
