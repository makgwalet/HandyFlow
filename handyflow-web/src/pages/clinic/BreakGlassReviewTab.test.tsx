import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import BreakGlassReviewTab from "./BreakGlassReviewTab"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
afterEach(() => { cleanup(); get.mockReset(); post.mockReset() })

const row = { id: "s1", patientId: "p", patientName: "Thandi Nkosi", userId: "u", userName: "Dr Dlamini", reason: "Emergency admission", startedAt: "2026-10-13T08:00:00Z", expiresAt: "2026-10-13T09:00:00Z", itemsViewed: 2, documents: 1 }
const ui = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><BreakGlassReviewTab /></QueryClientProvider>)

describe("BreakGlassReviewTab", () => {
  it("lists what is waiting with who, why and what was done", async () => {
    get.mockResolvedValue({ data: { data: [row] } }); ui()
    expect(await screen.findByText(/Emergency admission/)).toBeTruthy()
    expect(screen.getByText("1 break-glass access is waiting for review.")).toBeTruthy()
    expect(screen.getByText(/2 viewed · 1 document printed or exported/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/clinic/break-glass/sessions?unacknowledged=true&limit=100")
  })
  it("records a review with the note", async () => {
    get.mockResolvedValue({ data: { data: [row] } }); post.mockResolvedValue({ data: {} }); ui()
    fireEvent.click(await screen.findByRole("button", { name: "Review" }))
    fireEvent.change(screen.getByLabelText("Review note"), { target: { value: "Checked with the ward" } })
    fireEvent.click(screen.getByRole("button", { name: "Mark as reviewed" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/break-glass/sessions/s1/acknowledge", { note: "Checked with the ward" }))
  })
  it("shows a reviewed session with who checked it and offers no review button", async () => {
    get.mockResolvedValue({ data: { data: [{ ...row, acknowledgedAt: "2026-10-13T10:00:00Z", acknowledgedNote: "All in order" }] } }); ui()
    fireEvent.click(await screen.findByLabelText(/Only those not yet reviewed/))
    expect(await screen.findByText(/All in order/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Review" })).toBeNull()
  })
  it("says so when the list cannot be loaded", async () => {
    get.mockRejectedValue(new Error("403")); ui()
    expect((await screen.findByRole("alert")).textContent).toMatch(/could not be loaded/)
  })
})
