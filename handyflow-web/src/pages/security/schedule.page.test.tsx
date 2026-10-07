import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { mondayOf } from "./schedule.logic"

const monday = mondayOf(new Date())
const at = (dayOffset: number, hhmm: string) => new Date(`${new Date(`${monday}T00:00:00Z`).getTime() + dayOffset * 86400000 > 0 ? new Date(new Date(`${monday}T00:00:00Z`).getTime() + dayOffset * 86400000).toISOString().slice(0, 10) : monday}T${hhmm}:00+02:00`).toISOString()
const guards = [
  { id: "g1", firstName: "Nomsa", lastName: "Dlamini", status: "ACTIVE", psiraExpiryDate: "2030-01-01" },
  { id: "g2", firstName: "Bafana", lastName: "Khumalo", status: "ACTIVE", psiraExpiryDate: "2030-01-01" },
]
const shifts = [
  { id: "a", siteId: "s1", guardId: "g1", startAt: at(0, "06:00"), endAt: at(0, "18:00"), status: "SCHEDULED" },
  { id: "b", siteId: "s1", guardId: "g1", startAt: at(0, "17:00"), endAt: at(0, "20:00"), status: "SCHEDULED" },
]
const post = vi.fn(() => Promise.resolve({ data: {} }))
vi.mock("../../api/client", () => ({
  apiClient: {
    get: vi.fn((url: string) => Promise.resolve({ data: { data: url.includes("/shifts/range") ? shifts : url.includes("/guards") ? { content: guards } : { content: [{ id: "s1", name: "Centurion Mall" }] } } })),
    post: (...a: any[]) => (post as any)(...a),
  },
}))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
import SchedulerTab from "./SchedulerTab"

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter><SchedulerTab /></MemoryRouter>
  </QueryClientProvider>)
afterEach(() => { cleanup(); post.mockClear() })

describe("SchedulerTab", () => {
  it("shows guards, their shifts and the overlap on the later shift", async () => {
    show()
    expect(await screen.findByText("Nomsa Dlamini")).toBeTruthy()
    expect(screen.getByText("Bafana Khumalo")).toBeTruthy()
    expect(await screen.findByText("Overlaps another shift for this guard")).toBeTruthy()
    expect(screen.getAllByText("Centurion Mall").length).toBeGreaterThanOrEqual(2) // two shifts, plus the site filter option
    expect(screen.getByText("Shifts with a conflict").previousSibling?.textContent).toBe("1")
  })
  it("adds a shift through the server and shows its refusal", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Guard Bafana Khumalo already has a shift that overlaps" } } })
    show()
    await screen.findByText("Bafana Khumalo")
    fireEvent.click(screen.getAllByLabelText(/Add shift for Bafana Khumalo/)[0])
    fireEvent.click(screen.getByRole("button", { name: "Add shift" }))
    expect((await screen.findByRole("alert")).textContent).toContain("already has a shift that overlaps")
    expect(post).toHaveBeenCalledWith("/api/v1/security/shifts", expect.objectContaining({ guardId: "g2", siteId: "s1" }))
  })
  it("refuses a blank time before calling the server", async () => {
    show()
    await screen.findByText("Bafana Khumalo")
    fireEvent.click(screen.getAllByLabelText(/Add shift for Bafana Khumalo/)[0])
    fireEvent.change(screen.getByLabelText("Start"), { target: { value: "" } })
    fireEvent.click(screen.getByRole("button", { name: "Add shift" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("start and an end"))
    expect(post).not.toHaveBeenCalled()
  })
})
