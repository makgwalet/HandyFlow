import { describe, it, expect, afterEach, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen, cleanup } from "@testing-library/react"
import VehicleSummary from "./VehicleSummary"
import { vehicleSummary } from "./vehicleSummary.logic"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => false }))
afterEach(cleanup)
const today = new Date(2026, 9, 21)
const base = { currentOdometer: 48000, lastServiceKm: 40000, serviceIntervalKm: 10000, licenceDiscExpiry: "2026-12-31", roadworthyExpiry: "2026-10-30", insuranceExpiry: "2026-10-01" }

describe("vehicle summary", () => {
  it("works out the kilometres left and each document's state", () => {
    const s = vehicleSummary(base, today)
    expect(s.nextServiceKm).toBe(2000); expect(s.docs.map(d => d.tone)).toEqual(["ok", "warn", "bad"])
    expect(s.docs[1].text).toBe("9 days left"); expect(s.docs[2].text).toBe("Expired 20 days ago")
  })
  it("goes negative when overdue and says so when nothing is known", () => {
    expect(vehicleSummary({ ...base, currentOdometer: 52000 }, today).nextServiceKm).toBe(-2000)
    const none = vehicleSummary({ currentOdometer: null, lastServiceKm: null, serviceIntervalKm: null, licenceDiscExpiry: null, roadworthyExpiry: null, insuranceExpiry: null }, today)
    expect(none.nextServiceKm).toBeNull(); expect(none.docs.every(d => d.text === "Not set" && d.tone === "none")).toBe(true)
  })
  it("shows the service meter, odometer and documents", () => {
    get.mockResolvedValue({ data: { data: [] } })
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><VehicleSummary vehicleId="v1" name="ABC123MP" vehicle={{ ...base, insuranceExpiry: null }} /></QueryClientProvider>)
    expect(screen.getByLabelText(/^Service:/)).toBeTruthy(); expect(screen.getByText(/Odometer 48.000 km/)).toBeTruthy()
    expect(screen.getByLabelText("Insurance: Not set")).toBeTruthy()
  })
})
