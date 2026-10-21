import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import VehicleOverview from "./VehicleOverview"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const day = (back: number) => { const d = new Date(); d.setDate(d.getDate() - back); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><VehicleOverview vehicleId="v1" vehicle={{ registration: "ABC123MP", status: "ON_TRIP", assignedDriverName: "John Mokoena", currentOdometer: 86340, vin: "AHTKZ3CD6H123456" } as never} /></QueryClientProvider>)
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("VehicleOverview", () => {
  it("shows key information, 30 days of fuel and the specification", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("/fuel-analytics") ? { summary: { litresPer100Km: 8.7 } } : [{ filledAt: day(1), litres: 40, totalCost: 900 }, { filledAt: day(3), litres: 30, totalCost: 700 }, { filledAt: day(90), litres: 50, totalCost: 1200 }] } }))
    show()
    expect(screen.getByLabelText("Key information").textContent).toContain("John Mokoena"); expect(screen.getByLabelText("Key information").textContent).toContain("On Trip")
    await waitFor(() => expect(screen.getByLabelText("Total fuel").textContent).toContain("70")); expect(screen.getByLabelText("Fuel cost").textContent).toMatch(/R 1\s600/)
    expect(screen.getByLabelText("Avg consumption").textContent).toContain("8.7 L/100 km"); expect(screen.getByRole("img", { name: /2 fill-ups/ })).toBeTruthy()
    expect(screen.getByText("AHTKZ3CD6H123456")).toBeTruthy()
  })
  it("says so when there was no fuel, and when the figures cannot load", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("/fuel-analytics") ? { summary: { litresPer100Km: null } } : [] } })); show()
    expect((await screen.findByRole("status")).textContent).toContain("No fill-ups in the last 30 days")
    cleanup(); get.mockImplementation(() => Promise.reject(new Error("x"))); show(); expect((await screen.findByRole("alert")).textContent).toContain("Couldn't load the fuel figures")
  })
})
