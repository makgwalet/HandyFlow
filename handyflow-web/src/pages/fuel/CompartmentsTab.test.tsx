import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import CompartmentsTab from "./CompartmentsTab"
import { layOut, problems } from "./compartmentOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const rating = (n: number, o: any = {}) => ({ compartmentId: "c" + n, number: n, capacityLitres: 10000, fuelTypes: [], lastCargo: null, lastCargoAt: null, lastCleanedAt: null, ...o })
const load = (compartmentId: string, litres: number, o: any = {}) => ({ deliveryId: "d" + compartmentId + litres, status: "SCHEDULED", scheduledAt: "2026-11-18T08:00:00Z", customerName: "Gold Mine Ltd", fuelType: "DIESEL", compartmentId, litres, ...o })
const tankers = [{ id: "t1", registration: "AB 12 CD GP", name: null, active: true, totalCapacity: 20000, compartments: [], openLoads: 0 }, { id: "t2", registration: "EF 34 GH GP", name: "Blue", active: true, totalCapacity: 10000, compartments: [], openLoads: 0 }]

function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><CompartmentsTab /></QueryClientProvider>) }

describe("compartmentOps", () => {
  it("fills each compartment and flags over-capacity, mixed fuels and unrated fuels", () => {
    const cells = layOut([rating(2, { fuelTypes: ["PETROL"] }), rating(1)], [load("c1", 12000), load("c1", 500, { fuelType: "PETROL" }), load("c2", 4000)])
    expect(cells.map(c => c.rating.number)).toEqual([1, 2])
    expect(cells[0].over).toBe(true); expect(cells[0].mixed).toBe(true); expect(cells[0].percent).toBe(100)
    expect(cells[1].notRated).toEqual(["DIESEL"]); expect(cells[1].percent).toBe(40)
    expect(problems(cells)).toEqual(["Compartment 1 is planned above its capacity.", "Compartment 1 has more than one fuel planned in it on the same day.", "Compartment 2 is not rated for DIESEL."])
  })
  it("treats an unrestricted compartment as able to carry anything", () => {
    expect(layOut([rating(1)], [load("c1", 100)])[0].notRated).toEqual([])
  })
})

describe("CompartmentsTab", () => {
  beforeEach(() => {
    get.mockReset()
    get.mockImplementation((u: string) => u.endsWith("/tankers") ? ok(tankers)
      : u.endsWith("/compartment-ratings") ? ok([rating(1, { lastCargo: "PETROL", lastCargoAt: "2026-11-10T08:00:00Z", fuelTypes: ["DIESEL"] }), rating(2)])
      : u.endsWith("/compartment-loads") ? ok([load("c1", 6000)]) : ok(null))
  })
  afterEach(cleanup)

  it("shows the first tanker's compartments with what is planned, what they may carry and what they last carried", async () => {
    mount()
    const c1 = await screen.findByRole("group", { name: "Compartment 1" })
    expect(c1.textContent).toContain("6 000")
    expect(c1.textContent).toContain("Rated for: DIESEL")
    expect(c1.textContent).toContain("Last carried: PETROL")
    expect(screen.getByRole("group", { name: "Compartment 2" }).textContent).toContain("Empty")
    expect(screen.queryByRole("status", { name: "Problems with the plan" })).toBeNull()
  })

  it("asks for the chosen tanker and day", async () => {
    mount()
    await screen.findByRole("group", { name: "Compartment 1" })
    fireEvent.change(screen.getByLabelText("Tanker"), { target: { value: "t2" } })
    fireEvent.change(screen.getByLabelText("Day"), { target: { value: "2026-11-20" } })
    await screen.findByRole("group", { name: "Compartment 1" })
    expect(get.mock.calls.some(c => String(c[0]).includes("/tankers/t2/compartment-loads") && c[1]?.params?.date === "2026-11-20")).toBe(true)
  })

  it("lists problems with the plan in words", async () => {
    get.mockImplementation((u: string) => u.endsWith("/tankers") ? ok(tankers) : u.endsWith("/compartment-ratings") ? ok([rating(1, { fuelTypes: ["PETROL"] })]) : ok([load("c1", 6000)]))
    mount()
    expect(await screen.findByText("Compartment 1 is not rated for DIESEL.")).toBeTruthy()
  })

  it("says so when there are no tankers or the load fails", async () => {
    get.mockImplementation((u: string) => u.endsWith("/tankers") ? ok([]) : ok([]))
    mount()
    expect(await screen.findByText("No tankers yet")).toBeTruthy()
    cleanup(); get.mockImplementation((u: string) => u.endsWith("/tankers") ? ok(tankers) : Promise.reject(new Error("x"))); mount()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
