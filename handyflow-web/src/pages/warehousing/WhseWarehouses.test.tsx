import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseWarehousesPanel, { type Warehouse } from "./WhseWarehousesPanel"
import WhseLocationsTab from "./WhseLocationsTab"
import { pickWarehouse, scope, scopeQuery, showSelector, warehouseProblem, type WarehouseLite } from "./warehouses"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: vi.fn() } }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const wh = (over: Partial<Warehouse> = {}): Warehouse => ({ id: "W1", code: "CPT", name: "Cape Town", address: null, active: true, isDefault: true, locations: 12, stockOnHand: 340, ...over })
const lite = (over: Partial<WarehouseLite> = {}): WarehouseLite => ({ id: "W1", code: "CPT", name: "Cape Town", active: true, isDefault: true, ...over })

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); post.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("warehouse helpers", () => {
  it("the selector shows only when more than one warehouse is switched on", () => {
    expect(showSelector([lite()])).toBe(false)
    expect(showSelector([lite(), lite({ id: "W2", code: "JHB", isDefault: false, active: false })])).toBe(false)
    expect(showSelector([lite(), lite({ id: "W2", code: "JHB", isDefault: false })])).toBe(true)
  })
  it("picks the one chosen, else the default, and nothing when there is no choice", () => {
    const two = [lite(), lite({ id: "W2", code: "JHB", isDefault: false })]
    expect(pickWarehouse(two, "W2")).toBe("W2")
    expect(pickWarehouse(two, "gone")).toBe("W1")
    expect(pickWarehouse(two, undefined)).toBe("W1")
    expect(pickWarehouse([lite()], "W1")).toBeUndefined()
    expect(pickWarehouse([], null)).toBeUndefined()
    expect(pickWarehouse([lite({ isDefault: false }), lite({ id: "W2", isDefault: false })], null)).toBe("W1")
  })
  it("codes and names are checked like the server does", () => {
    expect(warehouseProblem("cpt", "Cape Town", [])).toBeNull()
    expect(warehouseProblem("a b", "x", [])).toMatch(/code is 1 to 20/)
    expect(warehouseProblem("", "x", [])).toMatch(/code is 1 to 20/)
    expect(warehouseProblem("cpt", "x", [lite()])).toMatch(/already exists/)
    expect(warehouseProblem("jhb", " ", [])).toMatch(/Name/)
    expect(warehouseProblem("jhb", "x".repeat(121), [])).toMatch(/120/)
  })
  it("adds the warehouse to a request only when there is one to add", () => {
    expect(scope("W2")).toEqual({ params: { warehouseId: "W2" } })
    expect(scope(undefined)).toBeUndefined()
    expect(scopeQuery("W 2")).toBe("?warehouseId=W%202")
    expect(scopeQuery(undefined)).toBe("")
  })
})

describe("WhseWarehousesPanel", () => {
  it("lists warehouses with their size and default flag", async () => {
    get.mockResolvedValue({ data: [wh(), wh({ id: "W2", code: "JHB", name: "Johannesburg", isDefault: false, locations: 1, stockOnHand: 0 })] })
    wrap(<WhseWarehousesPanel />)
    expect(await screen.findByText("Cape Town")).toBeTruthy()
    expect(screen.getByText("Default")).toBeTruthy()
    expect(screen.getByText(/12 locations · 340 units on hand/)).toBeTruthy()
    expect(screen.getByText(/1 location · 0 units/)).toBeTruthy()
  })

  it("adds a warehouse with an upper-case code", async () => {
    get.mockResolvedValue({ data: [wh()] })
    wrap(<WhseWarehousesPanel />)
    await screen.findByText("Cape Town")
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "jhb" } })
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Johannesburg" } })
    fireEvent.click(screen.getByText("Add warehouse"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/warehouses", { code: "JHB", name: "Johannesburg", address: null }))
  })

  it("will not add a code that is already used", async () => {
    get.mockResolvedValue({ data: [wh()] })
    wrap(<WhseWarehousesPanel />)
    await screen.findByText("Cape Town")
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "cpt" } })
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Again" } })
    expect(screen.getByText(/already exists/)).toBeTruthy()
    expect((screen.getByText("Add warehouse") as HTMLButtonElement).disabled).toBe(true)
  })

  it("makes another warehouse the default, and the default has no switch-off", async () => {
    get.mockResolvedValue({ data: [wh(), wh({ id: "W2", code: "JHB", name: "Johannesburg", isDefault: false })] })
    wrap(<WhseWarehousesPanel />)
    await screen.findByText("Johannesburg")
    expect(screen.getAllByText("Switch off")).toHaveLength(1)
    fireEvent.click(screen.getByText("Make default"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/warehouses/W2/make-default"))
  })

  it("a switched-off warehouse can be switched back on", async () => {
    get.mockResolvedValue({ data: [wh(), wh({ id: "W2", code: "JHB", name: "Johannesburg", isDefault: false, active: false })] })
    wrap(<WhseWarehousesPanel />)
    fireEvent.click(await screen.findByText("Switch on"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/warehouses/W2/activate"))
  })
})

describe("Locations with several warehouses", () => {
  it("asks only for the chosen warehouse's locations, and changes when another is picked", async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith("/warehouses")) return { data: [lite(), lite({ id: "W2", code: "JHB", name: "Johannesburg", isDefault: false })] }
      return { data: [] }
    })
    wrap(<WhseLocationsTab />)
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/locations", { params: { warehouseId: "W1" } }))
    fireEvent.change(await screen.findByLabelText("Warehouse"), { target: { value: "W2" } })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/locations", { params: { warehouseId: "W2" } }))
  })

  it("with one warehouse there is no selector and the request is unchanged", async () => {
    get.mockImplementation(async (url: string) => (url.endsWith("/warehouses") ? { data: [lite()] } : { data: [] }))
    wrap(<WhseLocationsTab />)
    await screen.findByText("No locations set up yet.")
    expect(screen.queryByLabelText("Warehouse")).toBeNull()
    expect(get).toHaveBeenCalledWith("/api/v1/warehousing/locations", undefined)
  })
})
