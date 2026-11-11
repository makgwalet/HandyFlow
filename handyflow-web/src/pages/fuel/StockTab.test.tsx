import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import StockTab from "./StockTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const line = (o: any = {}) => ({ tankId: "t1", tankName: "Diesel 1", fuelType: "DIESEL", depotId: null, physical: 5000, reserved: 1000, safetyStock: 500, availableToPromise: 3500, ledgerBalance: 5000, ledgerMatches: true, ...o })
let availability: any

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><StockTab /></QueryClientProvider>)
}

describe("StockTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); perms = []
    availability = { tanks: [line(), line({ tankId: "t2", tankName: "Diesel 2", physical: 1000, reserved: 0, safetyStock: 0, availableToPromise: 1000, ledgerBalance: 1000 })], depots: [] }
    post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.includes("/availability")) return { data: { data: availability } }
      if (u.includes("/ledger")) return { data: { data: { content: [{ id: "m1", tankId: "t1", movementType: "ADJUSTMENT", litresDelta: -120, balanceAfter: 4880, reason: "Stocktake loss", createdAt: "2026-11-11T10:00:00Z" }] } } }
      if (u.includes("/depots")) return { data: { data: [{ id: "d1", code: "WIT", name: "Witbank Depot", address: "N4", tankCount: 2 }] } }
      return { data: { data: { content: [] } } }
    })
  })
  afterEach(() => cleanup())

  it("shows physical, reserved, safety and available to promise per tank", async () => {
    mount()
    await waitFor(() => screen.getByText("Diesel 1"))
    const row = screen.getByText("Diesel 1").closest("tr")!
    expect(row.textContent).toContain("5")      // physical
    expect(row.textContent).toContain("Matches")
    expect(screen.getByText(/Available to promise = physical stock/)).toBeTruthy()
  })

  it("warns loudly when the ledger does not add up to a tank", async () => {
    availability.tanks[1] = line({ tankId: "t2", tankName: "Diesel 2", physical: 1000, ledgerBalance: 900, ledgerMatches: false })
    mount()
    expect((await screen.findByRole("alert")).textContent).toContain("Diesel 2")
  })

  it("hides Adjust without FUEL_STOCK_ADJUST and Reserve/Transfer without FUEL_MANAGE", async () => {
    mount(); await waitFor(() => screen.getByText("Diesel 1"))
    expect(screen.queryByText("Adjust")).toBeNull(); expect(screen.queryByText("Transfer between tanks")).toBeNull(); expect(screen.queryByText("Reserve fuel")).toBeNull()
  })

  it("adjust: validates on screen, then posts the signed litres with the reason", async () => {
    perms = ["FUEL_STOCK_ADJUST"]; mount(); await waitFor(() => screen.getByText("Diesel 1"))
    fireEvent.click(screen.getAllByText("Adjust")[0])
    const dlg = screen.getByRole("dialog")
    const go = within(dlg).getByRole("button", { name: "Post adjustment" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText("Litres (+/-)"), { target: { value: "-120" } })
    fireEvent.change(within(dlg).getByLabelText(/Reason/), { target: { value: "Stocktake loss" } })
    expect(go.disabled).toBe(false)
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/tanks/t1/adjust", { litres: -120, reason: "Stocktake loss" }))
  })

  it("reserve: refuses more than available to promise before calling the server", async () => {
    perms = ["FUEL_MANAGE"]; mount(); await waitFor(() => screen.getByText("Diesel 1"))
    fireEvent.click(screen.getAllByText("Reserve")[0])
    const dlg = screen.getByRole("dialog")
    fireEvent.change(within(dlg).getByLabelText("Litres"), { target: { value: "3501" } })
    expect(dlg.textContent).toContain("available to promise")
    expect((within(dlg).getByRole("button", { name: "Reserve" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText("Litres"), { target: { value: "3500" } })
    fireEvent.click(within(dlg).getByRole("button", { name: "Reserve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/reservations", { tankId: "t1", litres: 3500, note: null }))
  })

  it("transfer: posts both tanks, litres and reason; shows the server's refusal", async () => {
    perms = ["FUEL_MANAGE"]; mount(); await waitFor(() => screen.getByText("Diesel 1"))
    fireEvent.click(screen.getByText("Transfer between tanks"))
    const dlg = screen.getByRole("dialog")
    fireEvent.change(within(dlg).getByLabelText("From"), { target: { value: "t1" } })
    fireEvent.change(within(dlg).getByLabelText("To"), { target: { value: "t2" } })
    fireEvent.change(within(dlg).getByLabelText("Litres"), { target: { value: "100" } })
    fireEvent.change(within(dlg).getByLabelText(/Reason/), { target: { value: "Balance depots" } })
    post.mockRejectedValueOnce({ response: { data: { message: "Only 50L of Diesel 1 is free: 4950L is reserved for other customers or orders." } } })
    fireEvent.click(within(dlg).getByRole("button", { name: "Transfer" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/transfers", { fromTankId: "t1", toTankId: "t2", litres: 100, reason: "Balance depots" }))
    expect((await within(dlg).findByRole("alert")).textContent).toContain("is reserved for other customers")
  })

  it("lists ledger lines with friendly type names", async () => {
    mount(); await waitFor(() => screen.getByText("Diesel 1"))
    fireEvent.click(screen.getByRole("button", { name: "Ledger" }))
    expect(await screen.findByText("Adjustment")).toBeTruthy()
    expect(screen.getByText("Stocktake loss")).toBeTruthy()
  })

  it("depots: lists them and posts a new one", async () => {
    perms = ["FUEL_MANAGE"]; mount(); await waitFor(() => screen.getByText("Diesel 1"))
    fireEvent.click(screen.getByRole("button", { name: "Depots" }))
    expect(await screen.findByText("Witbank Depot")).toBeTruthy()
    fireEvent.click(screen.getByText("Add depot"))
    const dlg = screen.getByRole("dialog")
    fireEvent.change(within(dlg).getByLabelText("Name"), { target: { value: "Rustenburg" } })
    fireEvent.change(within(dlg).getByLabelText("Code"), { target: { value: "RUS 1" } })
    expect((within(dlg).getByRole("button", { name: "Save" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText("Code"), { target: { value: "RUS1" } })
    fireEvent.click(within(dlg).getByRole("button", { name: "Save" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/depots", { name: "Rustenburg", code: "RUS1", address: null }))
  })
})
