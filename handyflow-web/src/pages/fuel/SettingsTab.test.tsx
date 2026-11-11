import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SettingsTab from "./SettingsTab"

const get = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const saved = { driverCostPerHour: 150, vehicleCostPerKm: 6.5, fixedCostPerDelivery: 40, overheadPctOfRevenue: 3, defaultTripHours: 2, poOverTolerancePct: 2, ratesConfigured: true }
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><SettingsTab /></QueryClientProvider>)
}

describe("SettingsTab", () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); perms = ["FUEL_MANAGE"]; get.mockResolvedValue({ data: { data: saved } }); put.mockResolvedValue({ data: { data: saved } }) })
  afterEach(cleanup)

  it("loads the saved values", async () => {
    mount()
    expect(((await screen.findByLabelText("Driver cost per hour (R)")) as HTMLInputElement).value).toBe("150")
    expect((screen.getByLabelText("Supplier delivery tolerance (%)") as HTMLInputElement).value).toBe("2")
  })

  it("saves the edited values as numbers", async () => {
    mount()
    fireEvent.change(await screen.findByLabelText("Driver cost per hour (R)"), { target: { value: "175.5" } })
    fireEvent.change(screen.getByLabelText("Supplier delivery tolerance (%)"), { target: { value: "5" } })
    fireEvent.click(screen.getByText("Save settings"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/fuel/settings")
    expect(put.mock.calls[0][1]).toMatchObject({ driverCostPerHour: 175.5, poOverTolerancePct: 5, vehicleCostPerKm: 6.5 })
    expect(await screen.findByText("Saved.")).toBeTruthy()
  })

  it("does not send an invalid value", async () => {
    mount()
    fireEvent.change(await screen.findByLabelText("Supplier delivery tolerance (%)"), { target: { value: "15" } })
    fireEvent.click(screen.getByText("Save settings"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/tolerance/)
    expect(put).not.toHaveBeenCalled()
  })

  it("shows the server's message when saving fails", async () => {
    put.mockRejectedValueOnce({ response: { data: { message: "Not allowed" } } })
    mount()
    await screen.findByLabelText("Driver cost per hour (R)")
    fireEvent.click(screen.getByText("Save settings"))
    expect((await screen.findByRole("alert")).textContent).toBe("Not allowed")
  })

  it("without FUEL_MANAGE the fields are read-only and there is no Save", async () => {
    perms = ["FUEL_READ"]
    mount()
    const input = (await screen.findByLabelText("Driver cost per hour (R)")) as HTMLInputElement
    expect(input.disabled).toBe(true)
    expect(screen.queryByText("Save settings")).toBeNull()
  })
})
