import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SlaCalendarTab from "./SlaCalendarTab"
import ClientsTab from "./ClientsTab"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
vi.mock("../../store/toast.store", () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

describe("public holidays", () => {
  beforeEach(() => { cleanup(); get.mockReset(); post.mockReset() })
  it("adds only the holidays not already on the list", async () => {
    const year = new Date().getFullYear()
    get.mockResolvedValue({ data: { configured: true, startTime: "08:00", endTime: "17:00", workingDays: ["MON"], timeZone: "Africa/Johannesburg", holidays: [{ date: `${year}-12-25`, name: "Christmas Day" }, { date: `${year}-01-01`, name: "New Year's Day" }] } })
    post.mockResolvedValue({ data: {} })
    wrap(<SlaCalendarTab />)
    fireEvent.click(await screen.findByText(`Add ${year} public holidays`))
    await waitFor(() => expect(post.mock.calls.length).toBeGreaterThan(8))
    const sent = post.mock.calls.map(c => (c[1] as { date: string }).date)
    expect(sent).not.toContain(`${year}-12-25`)
    expect(sent).not.toContain(`${year}-01-01`)
    expect(sent).toContain(`${year}-06-16`)
    expect(post.mock.calls[0][0]).toBe("/api/v1/facilitiesmanagement/sla/calendar/holidays")
  })
})

describe("client search", () => {
  beforeEach(() => { cleanup(); get.mockReset() })
  it("sends what was typed to the server and starts again from the first page", async () => {
    get.mockResolvedValue({ data: { content: [], totalPages: 0, totalElements: 0 } })
    wrap(<ClientsTab />)
    fireEvent.change(await screen.findByLabelText("Search clients"), { target: { value: "  acme " } })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/facilitiesmanagement/clients", { params: { page: 0, size: 25, search: "acme" } }), { timeout: 2000 })
    expect(await screen.findByText(/No client matches "acme"/)).toBeTruthy()
  })
})
