import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import FuelTab from "./FuelTab"
import { defaultPeriod, logUrl, periodProblem, summaryUrl } from "./fuelLog.logic"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const row = (i: number, o: object = {}) => ({ id: `f${i}`, vehicleId: i % 2 ? "v1" : "v2", registration: i % 2 ? "ABC123MP" : "XYZ999GP", filledAt: "2026-10-10", litres: 40, pricePerLitre: 22.5, totalCost: 900, odometerAtFillup: 1000 + i, station: "Engen N1", receiptRef: `R${i}`, fullTank: true, ...o })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><FuelTab /></QueryClientProvider>)
beforeEach(() => {
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => url.includes("/fuel-log/summary") ? ok({ fillups: 30, litres: 1200, spend: 27000, avgPricePerLitre: 22.5 })
    : url.includes("/fuel-log") ? ok({ content: [row(1), row(2)], totalElements: 30, totalPages: 2 })
    : url.includes("/vehicles") ? ok({ content: [{ id: "v1", registration: "ABC123MP", make: "Toyota", model: "Hilux", currentOdometer: 5000 }] }) : ok([]))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("fuel log rules", () => {
  it("defaults to the last 30 days and builds the requests", () => {
    expect(defaultPeriod(new Date(2026, 9, 21))).toEqual({ from: "2026-09-22", to: "2026-10-21" })
    expect(logUrl({ from: "2026-09-22", to: "2026-10-21", q: " shell ", vehicleId: "v1" }, 2)).toBe("/api/v1/fleet/fuel-log?from=2026-09-22&to=2026-10-21&q=shell&vehicleId=v1&page=2&size=25")
    expect(summaryUrl({ from: "a", to: "b", q: "" })).toBe("/api/v1/fleet/fuel-log/summary?from=a&to=b")
  })
  it("refuses backwards and over-long periods", () => {
    expect(periodProblem("2026-05-02", "2026-05-01")).toContain("after the end date"); expect(periodProblem("2025-01-01", "2026-10-01")).toContain("a year or less")
    expect(periodProblem("", "2026-10-01")).toBe("Choose both dates."); expect(periodProblem("2026-09-01", "2026-10-01")).toBeNull()
  })
})
describe("fleet-wide fuel log", () => {
  it("shows every vehicle's fill-ups with totals for the whole filter", async () => {
    show()
    expect((await screen.findByLabelText(/^ABC123MP 2026-10-10/)).textContent).toContain("Engen N1"); expect(screen.getByLabelText(/^XYZ999GP/)).toBeTruthy()
    expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 30")
    await waitFor(() => expect(screen.getByLabelText("Fill-ups").textContent).toContain("30")); expect(screen.getByLabelText("Spend").textContent).toMatch(/27\s000,00/)
  })
  it("asks the server for the next page and for the search", async () => {
    show(); await screen.findByLabelText(/^ABC123MP/)
    fireEvent.click(screen.getByRole("button", { name: "Next" }))
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("/fuel-log?") && String(c[0]).includes("page=1"))).toBe(true))
    fireEvent.change(screen.getByLabelText("Search fuel log"), { target: { value: "engen" } })
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("q=engen") && String(c[0]).includes("page=0"))).toBe(true), { timeout: 2000 })
  })
  it("says so when the period has no fill-ups", async () => {
    get.mockImplementation((url: string) => url.includes("/fuel-log/summary") ? ok({ fillups: 0, litres: 0, spend: 0, avgPricePerLitre: 0 }) : url.includes("/fuel-log") ? ok({ content: [], totalElements: 0, totalPages: 0 }) : ok({ content: [] }))
    show(); expect((await screen.findByRole("status")).textContent).toContain("No fill-ups in this period")
  })
  it("lets you log a fill-up for a vehicle chosen inside the form", async () => {
    show(); await screen.findByLabelText(/^ABC123MP/)
    fireEvent.click(screen.getByRole("button", { name: /Log Fill-Up/ }))
    const submit = () => screen.getAllByRole("button", { name: "Log Fill-Up" }).at(-1) as HTMLButtonElement
    fireEvent.change(screen.getByPlaceholderText("45.0"), { target: { value: "30" } }); expect(submit().disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Vehicle *"), { target: { value: "v1" } }); expect(submit().disabled).toBe(false); fireEvent.click(submit())
    await waitFor(() => expect(post).toHaveBeenCalled()); expect(post.mock.calls[0][0]).toBe("/api/v1/fleet/vehicles/v1/fuel")
  })
})
describe("one vehicle's log", () => {
  it("searches and pages that vehicle's fill-ups", async () => {
    const mine = Array.from({ length: 30 }, (_, i) => ({ ...row(i), receiptRef: `REC${String(i).padStart(2, "0")}`, filledAt: "2026-10-10" }))
    get.mockImplementation((url: string) => /vehicles\/v1\/fuel/.test(url) ? ok(mine) : url.includes("/fuel-log/summary") ? ok({ fillups: 0, litres: 0, spend: 0, avgPricePerLitre: 0 }) : url.includes("/fuel-log") ? ok({ content: [], totalElements: 0, totalPages: 0 }) : ok({ content: [{ id: "v1", registration: "ABC123MP", make: "Toyota", model: "Hilux", currentOdometer: 5000 }] }))
    show(); await screen.findByRole("option", { name: /ABC123MP/ }); fireEvent.change(screen.getByDisplayValue("All vehicles"), { target: { value: "v1" } })
    await waitFor(() => expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 30"))
    fireEvent.change(screen.getByLabelText("Search fill-ups"), { target: { value: "rec07" } }); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–1 of 1")
  })
})
