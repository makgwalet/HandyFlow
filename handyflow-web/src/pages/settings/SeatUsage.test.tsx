import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SeatUsage, { seatMessage, type SeatSnapshot } from "./SeatUsage"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SeatUsage /></QueryClientProvider>)
const snap = (o: Partial<SeatSnapshot> = {}, level = "NONE", used = 3, limit: number | null = 5): SeatSnapshot => ({
  tier: "STARTER", limitsProvisional: false, activeUsers: 2, pendingInvitations: 1, seats: { used, limit, percent: limit ? Math.round(used * 100 / limit) : null, level }, ...o })

beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("seat usage", () => {
  it("words the warnings", () => {
    expect(seatMessage(snap({}, "NONE"))).toBeNull()
    expect(seatMessage(snap({}, "AT_LIMIT", 5))).toMatch(/Every seat is taken/)
    expect(seatMessage(snap({}, "CLOSE", 5 - 0))).toMatch(/Nearly/)
    expect(seatMessage(snap({}, "NO_LIMIT", 3, null))).toBeNull()
  })
  it("shows used, active and invited seats", async () => {
    get.mockResolvedValue({ data: { data: snap({ limitsProvisional: true }) } })
    wrap()
    await waitFor(() => expect(screen.getByTestId("seat-usage")).toBeTruthy())
    const t = screen.getByTestId("seat-usage").textContent!
    expect(t).toContain("3 of 5 seats in use")
    expect(t).toContain("1 invited")
    expect(t).toContain("provisional")
  })
  it("renders nothing without a tier", async () => {
    get.mockResolvedValue({ data: { data: snap({ tier: null }) } })
    const { container } = wrap()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})
