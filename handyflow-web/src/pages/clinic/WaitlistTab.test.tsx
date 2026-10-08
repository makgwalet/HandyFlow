import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: (...a: any[]) => del(...a) } }))
import WaitlistTab from "./WaitlistTab"

const entry = { id: "w1", patientId: "p1", patientName: "Ada Lovelace", status: "WAITING", createdAt: "2026-10-01T08:00:00Z" }
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WaitlistTab /></QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset(); del.mockReset()
  get.mockImplementation(async (url: string) => url.includes("waitlist") ? { data: { data: [entry] } }
    : url.includes("practitioners") ? { data: { data: [] } } : { data: { data: { content: [{ id: "p2", fullName: "Bheki Nkosi" }] } } })
  del.mockResolvedValue({}); post.mockResolvedValue({})
})
afterEach(cleanup)

describe("WaitlistTab (cancellation list)", () => {
  it("is named for what it is for", async () => {
    show()
    expect(await screen.findByText(/waiting for a cancelled slot/)).toBeTruthy()
  })
  it("asks before removing someone", async () => {
    show()
    fireEvent.click(await screen.findByLabelText("Remove Ada Lovelace"))
    expect(del).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByRole("button", { name: "Remove" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/waitlist/w1"))
  })
  it("adds a patient chosen through the searchable picker", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Add to cancellation list/ }))
    fireEvent.change(screen.getAllByRole("combobox")[0], { target: { value: "bheki" } })
    fireEvent.mouseDown((await screen.findByText("Bheki Nkosi")).closest("[role=option]") as HTMLElement)
    fireEvent.click(screen.getByRole("button", { name: "Add" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/waitlist", expect.objectContaining({ patientId: "p2" })))
  })
})
