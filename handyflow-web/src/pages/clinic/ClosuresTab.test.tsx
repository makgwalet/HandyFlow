import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
let canAdmin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: (...a: any[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canAdmin }))
import ClosuresTab, { closureProblem } from "./ClosuresTab"

const ROW = { id: "c1", firstDay: "2026-12-25", lastDay: "2026-12-25", reason: "Christmas Day" }
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ClosuresTab /></QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); canAdmin = true
  get.mockResolvedValue({ data: { data: [ROW] } }); post.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("closureProblem", () => {
  it("needs both days, in order, and at most 60 days", () => {
    expect(closureProblem("", "2026-12-25")).toMatch(/Choose/)
    expect(closureProblem("2026-12-26", "2026-12-25")).toMatch(/cannot be before/)
    expect(closureProblem("2026-12-25", "2027-02-23")).toMatch(/60 days/)   // 61 days
    expect(closureProblem("2026-12-25", "2027-02-22")).toBeNull()           // exactly 60 days
    expect(closureProblem("2026-12-25", "2026-12-25")).toBeNull()
  })
})

describe("ClosuresTab", () => {
  it("lists upcoming closures", async () => {
    show()
    expect(await screen.findByText("Christmas Day")).toBeTruthy()
    expect(get.mock.calls[0][0]).toBe("/api/v1/clinic/closures")
  })

  it("adds a one-day closure when the last day is left blank", async () => {
    show(); await screen.findByText("Christmas Day")
    fireEvent.change(screen.getByLabelText("First day"), { target: { value: "2026-12-26" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: " Day of Goodwill " } })
    fireEvent.click(screen.getByText("Add closure"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toEqual({ firstDay: "2026-12-26", lastDay: "2026-12-26", reason: "Day of Goodwill" })
  })

  it("does not send a backwards closure", async () => {
    show(); await screen.findByText("Christmas Day")
    fireEvent.change(screen.getByLabelText("First day"), { target: { value: "2026-12-26" } })
    fireEvent.change(screen.getByLabelText("Last day"), { target: { value: "2026-12-25" } })
    fireEvent.click(screen.getByText("Add closure"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/cannot be before/)
    expect(post).not.toHaveBeenCalled()
  })

  it("cancels a closure", async () => {
    show(); await screen.findByText("Christmas Day")
    fireEvent.click(screen.getByText("Cancel"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/closures/c1"))
  })

  it("shows the server's reason when adding fails", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "A closure can be at most 60 days" } } })
    show(); await screen.findByText("Christmas Day")
    fireEvent.change(screen.getByLabelText("First day"), { target: { value: "2026-12-26" } })
    fireEvent.click(screen.getByText("Add closure"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/60 days/)
  })

  it("is read-only without admin permission", async () => {
    canAdmin = false
    show(); await screen.findByText("Christmas Day")
    expect(screen.queryByText("Add closure")).toBeNull()
    expect(screen.queryByText("Cancel")).toBeNull()
  })
})
