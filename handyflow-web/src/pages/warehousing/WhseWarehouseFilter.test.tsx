import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseDashboard from "./WhseDashboard"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string, cfg?: unknown) => get(u, cfg) } }))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WhseDashboard /></QueryClientProvider>)

const two = [{ id: "w1", code: "CPT", name: "Cape Town", active: true, isDefault: true }, { id: "w2", code: "JHB", name: "Johannesburg", active: true, isDefault: false }]
const serve = (warehouses: unknown[]) => get.mockImplementation(async (url: string) => {
  if (url.endsWith("/warehouses")) return { data: warehouses }
  if (url.endsWith("/clients/all") || url.endsWith("/locations") || url.endsWith("/stock-alerts")) return { data: [] }
  return { data: null }
})
const wasCalledWith = (suffix: string, warehouseId: string | undefined) =>
  get.mock.calls.some(([u, cfg]) => String(u).endsWith(suffix) && (cfg as { params?: { warehouseId?: string } } | undefined)?.params?.warehouseId === warehouseId)

beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("dashboard warehouse filter", () => {
  it("with two warehouses it defaults to the default one and the figures follow the choice", async () => {
    serve(two); wrap()
    await screen.findByLabelText("Warehouse")
    await waitFor(() => expect(wasCalledWith("/operations/summary", "w1")).toBe(true))
    expect(wasCalledWith("/tasks/summary", "w1")).toBe(true)
    fireEvent.change(screen.getByLabelText("Warehouse"), { target: { value: "w2" } })
    await waitFor(() => expect(wasCalledWith("/operations/summary", "w2")).toBe(true))
    expect(wasCalledWith("/locations", "w2")).toBe(true)
  })
  it("with one warehouse there is no selector and no warehouse parameter", async () => {
    serve([two[0]]); wrap()
    await waitFor(() => expect(get.mock.calls.some(([u]) => String(u).endsWith("/operations/summary"))).toBe(true))
    expect(screen.queryByLabelText("Warehouse")).toBeNull()
    expect(wasCalledWith("/operations/summary", undefined)).toBe(true)
  })
})
