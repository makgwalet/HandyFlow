import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EquipmentTab from "./EquipmentTab"
import { isMachine } from "./equipment.logic"

const get = vi.fn(), patch = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), patch: (...a: Args) => patch(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const row = (registration: string, engineHours: number | null, operatingRatePerHour: number | null, vehicleType = "BAKKIE") => ({ id: registration, registration, make: "Toyota", model: "Hilux", vehicleType, status: "AVAILABLE", engineHours, operatingRatePerHour })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><EquipmentTab /></QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { get.mockReset(); patch.mockReset() })

describe("equipment", () => {
  it("counts a vehicle as a machine only once it has hours or a rate", () => {
    expect(isMachine({ engineHours: 0, operatingRatePerHour: null })).toBe(false); expect(isMachine({ engineHours: null, operatingRatePerHour: null })).toBe(false)
    expect(isMachine({ engineHours: 1250.5, operatingRatePerHour: null })).toBe(true); expect(isMachine({ engineHours: 0, operatingRatePerHour: 85 })).toBe(true); expect(isMachine({ engineHours: 0, operatingRatePerHour: 0 })).toBe(true)
  })
  it("lists machines by default and keeps road vehicles out until asked", async () => {
    get.mockImplementation(() => ok([row("TRACTOR1", 4200, 310, "OTHER"), row("POLO1", 0, null, "SEDAN"), row("HILUX1", 0, null)])); show()
    expect(await screen.findByLabelText("TRACTOR1")).toBeTruthy(); expect(screen.queryByLabelText("POLO1")).toBeNull(); expect(screen.queryByLabelText("HILUX1")).toBeNull()
    expect(screen.getByRole("button", { name: "Machines (1)" }).getAttribute("aria-pressed")).toBe("true")
    fireEvent.click(screen.getByRole("button", { name: "All vehicles (3)" }))
    expect(screen.getByLabelText("POLO1").textContent).toContain("not set up as a machine"); expect(screen.getByLabelText("TRACTOR1").textContent).not.toContain("not set up")
  })
  it("says what to do when nothing is set up, and the button shows every vehicle", async () => {
    get.mockImplementation(() => ok([row("POLO1", 0, null, "SEDAN"), row("HILUX1", 0, null)])); show()
    expect((await screen.findByRole("status")).textContent).toContain("No machines set up yet"); expect(screen.queryByLabelText("POLO1")).toBeNull()
    fireEvent.click(screen.getByText("Show all vehicles to set one up")); expect(screen.getByLabelText("POLO1")).toBeTruthy(); expect(screen.getByLabelText("HILUX1")).toBeTruthy()
  })
  it("still saves hours for a vehicle set up from the full list", async () => {
    get.mockImplementation(() => ok([row("HILUX1", 0, null)])); patch.mockResolvedValue({ data: { data: {} } }); show()
    fireEvent.click(await screen.findByText("Show all vehicles to set one up")); fireEvent.change(screen.getByLabelText("Engine hours for HILUX1"), { target: { value: "120" } }); fireEvent.click(screen.getByLabelText("Save HILUX1"))
    await vi.waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/fleet/vehicles/HILUX1/equipment", { engineHours: 120, operatingRatePerHour: null }))
  })
})
