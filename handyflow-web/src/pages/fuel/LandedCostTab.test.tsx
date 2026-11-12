import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LandedCostTab from "./LandedCostTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const route = (o: any = {}) => ({ routeId: "r1", routeDate: "2026-11-10", label: "Morning run", tankerRegistration: "GP 12 AB", deliveredStops: 2, litres: 8000, revenue: 176000,
  productCost: 144000, transportCost: 2000, landedCost: 146000, productPerLitre: 18, transportPerLitre: 0.25, landedPerLitre: 18.25, revenuePerLitre: 22, marginPerLitre: 3.75, margin: 30000, complete: true, flags: [], ...o })
const roll = (o: any = {}) => ({ key: "k", label: "GP 12 AB", routes: 1, litres: 8000, revenue: 176000, productCost: 144000, transportCost: 2000, landedCost: 146000, landedPerLitre: 18.25, marginPerLitre: 3.75, margin: 30000, ...o })
const body = (o: any = {}) => ({ from: "2026-10-13", to: "2026-11-12", routesWithDeliveries: 1, routesLeftOut: 0, routes: [route()], total: roll({ label: "All" }), byTanker: [roll()], byDepot: [roll({ key: "d", label: "North depot" })], ...o })

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><LandedCostTab /></QueryClientProvider>)
}

describe("LandedCostTab", () => {
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: body() } }) })
  afterEach(cleanup)

  it("shows landed cost and margin per litre, by tanker, by depot and by route", async () => {
    mount()
    expect(await screen.findByText("Landed cost per litre")).toBeTruthy()
    expect(screen.getAllByText("R18.25/L").length).toBeGreaterThan(2)
    expect(screen.getByLabelText("By tanker")).toBeTruthy(); expect(screen.getByLabelText("By depot")).toBeTruthy(); expect(screen.getByLabelText("Routes")).toBeTruthy()
    expect(screen.getByText("North depot")).toBeTruthy(); expect(screen.getByText("Complete")).toBeTruthy()
    expect(get.mock.calls[0][0]).toMatch(/\/api\/v1\/fuel\/landed-cost\?from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}/)
  })
  it("a route with a missing cost shows a dash and why, and the page says it is left out", async () => {
    get.mockResolvedValue({ data: { data: body({ routesLeftOut: 1, routes: [route({ complete: false, landedPerLitre: null, marginPerLitre: null, landedCost: null, flags: ["NO_TRANSPORT_COST"] })], byTanker: [], byDepot: [],
      total: roll({ routes: 0, landedPerLitre: null, marginPerLitre: null }) }) } })
    mount()
    expect(await screen.findByText("No vehicle or driver cost recorded")).toBeTruthy()
    expect(screen.getByText(/1 of 1 route is left out/)).toBeTruthy()
    expect(screen.queryByLabelText("By tanker")).toBeNull()
    expect(screen.getAllByText("-").length).toBeGreaterThan(1)
  })
  it("changing the period asks again with an earlier start", async () => {
    mount()
    await screen.findByText("Landed cost per litre")
    const first = get.mock.calls[0][0]
    fireEvent.change(screen.getByLabelText("Period"), { target: { value: "90" } })
    await waitFor(() => expect(get.mock.calls.length).toBe(2))
    expect(get.mock.calls[1][0]).not.toBe(first)
  })
  it("shows an empty state and an error state", async () => {
    get.mockResolvedValue({ data: { data: body({ routes: [], routesWithDeliveries: 0 }) } })
    mount()
    expect(await screen.findByText(/No routes with deliveries/)).toBeTruthy()
    cleanup(); get.mockRejectedValue(new Error("boom")); mount()
    await waitFor(() => expect(screen.getAllByRole("alert").some(a => /Could not load landed cost/.test(a.textContent ?? ""))).toBe(true))
  })
})
