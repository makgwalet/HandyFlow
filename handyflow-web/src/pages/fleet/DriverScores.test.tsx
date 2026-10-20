import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DriverScoresTab from "./DriverScoresTab"
import { bandTone, coverageNote, scoreText, settingsBody, settingsProblem, toForm, weightTotal } from "./driverScores.logic"

const get = vi.fn(), put = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), put: (...a: Args) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const settings = { weightSafety: 35, weightInspection: 20, weightAccidents: 15, weightFuel: 10, weightTrips: 10, weightCompliance: 10, lookbackDays: 90, excellentThreshold: 90, goodThreshold: 75, fairThreshold: 60, usingDefaults: true }
const board = [
  { driverId: "d1", driverName: "John Mokoena", overall: 92.3, band: "EXCELLENT", coverage: 100, reliable: true, tripCount: 40 },
  { driverId: "d2", driverName: "Thandi Nkosi", overall: 61, band: "FAIR", coverage: 30, reliable: false, tripCount: 3 },
  { driverId: "d3", driverName: "New Starter", overall: null, band: null, coverage: 0, reliable: false, tripCount: 0 },
]
const profile = {
  driver: { firstName: "John", lastName: "Mokoena", licenseCode: "EC", prdpRequired: true, status: "ACTIVE" },
  compliance: [{ key: "LICENCE", label: "Driving licence", status: "VALID", daysRemaining: 300 }, { key: "PRDP", label: "PrDP", status: "EXPIRING_SOON", daysRemaining: 12 }],
  score: { overall: 92.3, band: "EXCELLENT", coverage: 100, reliable: true, periodFrom: "2026-07-22", periodTo: "2026-10-20", components: [{ key: "SAFETY", label: "Safety", weight: 35, effectiveWeight: 35, score: 95, status: "SCORED", detail: "0 harsh events" }, { key: "FUEL", label: "Fuel efficiency", weight: 10, effectiveWeight: 0, score: null, status: "NO_DATA", detail: "No fuel recorded" }] },
  activity: { tripCount: 40, completedTrips: 39, totalKm: 5200, businessKm: 5000, privateKm: 200, fuelLitres: 400, litresPer100Km: 9.5, inspectionsCompleted: 30, inspectionsFailed: 2, vehiclesDriven: ["ABC123MP"] },
  accidents: { count: 1, withInjuries: 0, withFatalities: 0, totalRepairCost: 12000, totalExcess: 5000, accidents: [{ id: "a1", referenceNumber: "ACC-2026-00021", occurredAt: "2026-09-01T08:00:00Z", status: "IN_REPAIR" }] },
}
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DriverScoresTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: { data: settings } })
  get.mockImplementation((url: string) => url.includes("/profile") ? ok(profile) : url.includes("/driver-score-settings") ? ok(settings) : url.includes("/driver-scores") ? ok(board) : ok([]))
})
afterEach(cleanup)

describe("score rules", () => {
  it("words scores, bands and coverage", () => {
    expect(scoreText(92.34)).toBe("92.3%"); expect(scoreText(null)).toBe("No data")
    expect(["EXCELLENT", "GOOD", "FAIR", "POOR", null].map(bandTone)).toEqual(["ok", "ok", "warn", "bad", "none"])
    expect(coverageNote(true, 100)).toBeNull(); expect(coverageNote(false, 29.6)).toMatch(/30%/)
  })
  it("settings: weights must total 100 and thresholds must go down", () => {
    const f = toForm(settings)
    expect(weightTotal(f)).toBe(100); expect(settingsProblem(f)).toBeNull()
    expect(settingsProblem({ ...f, weightFuel: "20" })).toMatch(/add up to 110/)
    expect(settingsProblem({ ...f, weightSafety: "-5" })).toMatch(/Safety/)
    expect(settingsProblem({ ...f, lookbackDays: "3" })).toMatch(/7 and 730/)
    expect(settingsProblem({ ...f, goodThreshold: "95" })).toMatch(/go down/)
    expect(settingsBody(f)).toMatchObject({ weightSafety: 35, lookbackDays: 90, fairThreshold: 60 })
  })
})

describe("DriverScoresTab", () => {
  it("ranks drivers and marks low-coverage scores as indicative", async () => {
    show(); await screen.findByText("John Mokoena")
    expect(screen.getByLabelText("Thandi Nkosi").textContent).toContain("61.0%"); expect(screen.getByLabelText("Thandi Nkosi").textContent).toContain("*")
    expect(screen.getByLabelText("New Starter").textContent).toContain("No data"); expect(screen.getByText(/Indicative only/)).toBeTruthy()
  })
  it("opens a driver's 360 profile", async () => {
    show(); await screen.findByText("John Mokoena"); fireEvent.click(within("John Mokoena"))
    expect((await screen.findByLabelText("Overall score")).textContent).toContain("92.3%")
    expect(screen.getByLabelText("PrDP").textContent).toContain("12 days left"); expect(screen.getByLabelText("Fuel efficiency").textContent).toContain("No data")
    expect(screen.getByText(/1 accident /)).toBeTruthy(); expect(screen.getByText(/ACC-2026-00021/)).toBeTruthy(); expect(screen.getByText(/9.5 L\/100 km/)).toBeTruthy()
  })
  it("lets an admin change the weights, with the total shown and bad totals blocked", async () => {
    show(); fireEvent.click(await screen.findByText("Score settings")); await screen.findByText("Total 100% of 100%")
    fireEvent.change(screen.getByLabelText("Fuel efficiency weight"), { target: { value: "20" } })
    expect((screen.getByText("Save settings") as HTMLButtonElement).disabled).toBe(true); expect(screen.getByText("Total 110% of 100%")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Fuel efficiency weight"), { target: { value: "5" } }); fireEvent.change(screen.getByLabelText("Safety weight"), { target: { value: "40" } })
    fireEvent.click(screen.getByText("Save settings"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/driver-score-settings", expect.objectContaining({ weightSafety: 40, weightFuel: 5 })))
  })
  it("hides saving from people without FLEET_ADMIN", async () => {
    perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show(); fireEvent.click(await screen.findByText("Score settings")); await screen.findByText(/Total 100%/)
    expect(screen.queryByText("Save settings")).toBeNull(); expect((screen.getByLabelText("Safety weight") as HTMLInputElement).disabled).toBe(true)
  })
})

function within(name: string) { return Array.from(screen.getByLabelText(name).querySelectorAll("button")).find(b => b.textContent === "Profile") as HTMLElement }
