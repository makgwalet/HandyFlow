import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: ["EARTHMOVING_READ"] } }) }))

import AssetsTab from "./AssetsTab"
import { searchAssets, sortAssets, pageOf } from "./shared/fleetTable"

afterEach(cleanup)
const mk = (n: number, over: Record<string, unknown> = {}) => ({ id: "a" + n, name: "Machine " + n, fleetNumber: "F" + String(n).padStart(2, "0"), assetType: "DOZER", make: "Cat", model: null, year: 2020,
  serialNumber: "SN" + n, registration: null, ownershipType: "OWN", hireSupplier: null, hireStartDate: null, hireEndDate: null, status: "AVAILABLE", currentSite: null, currentClient: null,
  dailyRate: n * 100, hourlyRate: null, currentHours: n * 10, lastServiceHours: 0, serviceIntervalHours: 250, dueForService: false, notes: null, createdAt: "", ...over })
let fleet: unknown[] = []
beforeEach(() => {
  vi.clearAllMocks()
  get.mockImplementation((url: string) => Promise.resolve(url.includes("/inspections/latest") ? { data: { data: [] } } : { data: { data: { content: fleet, last: true } } }))
})
const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AssetsTab /></QueryClientProvider>)

describe("fleet helpers", () => {
  const list = [mk(1, { name: "CAT D9", currentSite: "N1 Bridge" }), mk(2, { name: "Volvo Loader", make: "Volvo", dailyRate: null }), mk(10, { name: "Tipper" })]
  it("finds machines by every word typed, across several fields", () => {
    expect(searchAssets(list, "bridge cat").map(a => a.id)).toEqual(["a1"])
    expect(searchAssets(list, "volvo").map(a => a.id)).toEqual(["a2"])
    expect(searchAssets(list, "  ").length).toBe(3)
    expect(searchAssets(list, "nothing here")).toEqual([])
  })
  it("sorts numbers as numbers, text naturally, and keeps empty values last either way", () => {
    expect(sortAssets(list, "fleetNumber", "asc").map(a => a.id)).toEqual(["a1", "a2", "a10"])
    expect(sortAssets(list, "currentHours", "desc").map(a => a.id)).toEqual(["a10", "a2", "a1"])
    expect(sortAssets(list, "dailyRate", "asc").map(a => a.id)).toEqual(["a1", "a10", "a2"])
    expect(sortAssets(list, "dailyRate", "desc").map(a => a.id)).toEqual(["a10", "a1", "a2"])
  })
  it("pages and keeps the page inside the list", () => {
    const items = Array.from({ length: 60 }, (_, i) => i)
    expect(pageOf(items, 0)).toMatchObject({ pages: 3, from: 1, to: 25 })
    expect(pageOf(items, 2)).toMatchObject({ page: 2, from: 51, to: 60 })
    expect(pageOf(items, 9).page).toBe(2)
    expect(pageOf([], 0)).toMatchObject({ pages: 1, from: 0, to: 0 })
  })
})

describe("fleet list", () => {
  it("searches, and switches to a sortable table", async () => {
    fleet = [mk(1, { name: "CAT D9" }), mk(2, { name: "Volvo Loader" }), mk(3, { name: "Tipper" })]
    mount()
    await screen.findByText("CAT D9")
    fireEvent.change(screen.getByLabelText("Search fleet"), { target: { value: "volvo" } })
    expect(screen.queryByText("CAT D9")).toBeNull()
    expect(screen.getByText("Volvo Loader")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Search fleet"), { target: { value: "" } })
    fireEvent.click(screen.getByRole("button", { name: "Table" }))
    const table = await screen.findByLabelText("Fleet table")
    const names = () => within(table).getAllByRole("row").slice(1).map(r => within(r).getAllByRole("cell")[1].textContent)
    expect(names()).toEqual(["CAT D9", "Volvo Loader", "Tipper"])
    fireEvent.click(within(table).getByRole("button", { name: /Meter/ }))
    fireEvent.click(within(table).getByRole("button", { name: /Meter/ }))
    expect(names()).toEqual(["Tipper", "Volvo Loader", "CAT D9"])
  })
  it("shows 25 at a time and moves between pages", async () => {
    fleet = Array.from({ length: 30 }, (_, i) => mk(i + 1))
    mount()
    expect(await screen.findByText("Showing 1–25 of 30")).toBeTruthy()
    expect(screen.queryByText("Machine 30")).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Next" }))
    expect(await screen.findByText("Showing 26–30 of 30")).toBeTruthy()
    expect(screen.getByText("Machine 30")).toBeTruthy()
  })
  it("opens a machine from the table", async () => {
    fleet = [mk(1, { name: "CAT D9" })]
    mount()
    await screen.findByText("CAT D9")
    fireEvent.click(screen.getByRole("button", { name: "Table" }))
    fireEvent.click(await screen.findByLabelText("Open F01"))
    expect(await screen.findByLabelText("Machine detail")).toBeTruthy()
  })
})
