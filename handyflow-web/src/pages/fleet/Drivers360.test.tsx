import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DriversTab from "./DriversTab"
import { daysToDate, driverDocumentCounts, expiryState, expiryWords, sortDrivers, rankMap, bandText, bandToneFor, bandTone, coverageNote, scoreText, settingsBody, settingsProblem, toForm, weightTotal } from "./driverScores.logic"

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
const drivers = [
  { id: "d1", firstName: "John", lastName: "Mokoena", phone: null, email: null, idNumber: null, licenseNumber: null, licenseCode: "EC", licenseExpiry: "2027-08-01", licenseExpiringSoon: false, prdpRequired: false, prdpNumber: null, prdpCategory: null, prdpExpiry: null, prdpExpiringSoon: false, status: "ACTIVE", notes: null, createdAt: "2026-01-01" },
  { id: "d2", firstName: "Thandi", lastName: "Nkosi", phone: null, email: null, idNumber: null, licenseNumber: null, licenseCode: "B", licenseExpiry: "2027-08-01", licenseExpiringSoon: false, prdpRequired: false, prdpNumber: null, prdpCategory: null, prdpExpiry: null, prdpExpiringSoon: false, status: "ACTIVE", notes: null, createdAt: "2026-01-01" },
  { id: "d3", firstName: "New", lastName: "Starter", phone: null, email: null, idNumber: null, licenseNumber: null, licenseCode: "B", licenseExpiry: null, licenseExpiringSoon: false, prdpRequired: false, prdpNumber: null, prdpCategory: null, prdpExpiry: null, prdpExpiringSoon: false, status: "ACTIVE", notes: null, createdAt: "2026-01-01" },
]
const profile = {
  driver: { firstName: "John", lastName: "Mokoena", licenseCode: "EC", prdpRequired: true, status: "ACTIVE" },
  compliance: [{ key: "LICENCE", label: "Driving licence", status: "VALID", daysRemaining: 300 }, { key: "PRDP", label: "PrDP", status: "EXPIRING_SOON", daysRemaining: 12 }],
  score: { overall: 92.3, band: "EXCELLENT", coverage: 100, reliable: true, periodFrom: "2026-07-22", periodTo: "2026-10-20", components: [{ key: "SAFETY", label: "Safety", weight: 35, effectiveWeight: 35, score: 95, status: "SCORED", detail: "0 harsh events" }, { key: "FUEL", label: "Fuel efficiency", weight: 10, effectiveWeight: 0, score: null, status: "NO_DATA", detail: "No fuel recorded" }] },
  activity: { tripCount: 40, completedTrips: 39, totalKm: 5200, businessKm: 5000, privateKm: 200, fuelLitres: 400, litresPer100Km: 9.5, inspectionsCompleted: 30, inspectionsFailed: 2, vehiclesDriven: ["ABC123MP"] },
  accidents: { count: 1, withInjuries: 0, withFatalities: 0, totalRepairCost: 12000, totalExcess: 5000, accidents: [{ id: "a1", referenceNumber: "ACC-2026-00021", occurredAt: "2026-09-01T08:00:00Z", status: "IN_REPAIR" }] },
}
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DriversTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: { data: settings } })
  get.mockImplementation((url: string) => url.includes("/profile") ? ok(profile) : url.includes("/driver-score-settings") ? ok(settings) : url.includes("/driver-scores") ? ok(board) : url.includes("/fleet/drivers") ? ok({ content: drivers }) : ok([]))
})
afterEach(cleanup)

describe("score rules", () => {
  it("words scores, bands and coverage", () => {
    expect(scoreText(92.34)).toBe("92.3%"); expect(scoreText(null)).toBe("No data")
    expect(["EXCELLENT", "GOOD", "FAIR", "POOR", null].map(bandTone)).toEqual(["ok", "ok", "warn", "bad", "none"])
    expect(coverageNote(true, 100)).toBeNull(); expect(coverageNote(false, 29.6)).toMatch(/30%/)
  })
  it("a thin score is Not rated, never a verdict", () => {
    expect(bandText("EXCELLENT", false)).toBe("Not rated"); expect(bandText("POOR", false)).toBe("Not rated")
    expect(bandText("GOOD", true)).toBe("Good"); expect(bandText(null, true)).toBe("—")
    expect(bandToneFor("POOR", false)).toBe("none"); expect(bandToneFor("POOR", true)).toBe("bad")
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

describe("document expiry", () => {
  const today = new Date(2026, 9, 20, 22, 40)   // late evening: the day must not roll over
  const on = (n: number) => { const d = new Date(2026, 9, 20 + n); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
  it("counts whole calendar days, whatever the time of day", () => {
    expect([-99, -1, 0, 1, 7, 29, 30].map(n => daysToDate(on(n), today))).toEqual([-99, -1, 0, 1, 7, 29, 30])
  })
  it("never calls an expired document expiring", () => {
    expect([-99, -1, 0, 1, 7, 29, 30].map(n => expiryState(on(n), today))).toEqual(["EXPIRED", "EXPIRED", "EXPIRING", "EXPIRING", "EXPIRING", "EXPIRING", "VALID"]); expect(expiryState(null, today)).toBe("NOT_SET")
    expect([-99, -1, 0, 1, 7].map(n => expiryWords(on(n), today))).toEqual(["Expired 99 days ago", "Expired 1 day ago", "Expires today", "1 day left", "7 days left"]); expect(expiryWords(null, today)).toBe("Not set")
  })
  it("counts active drivers once, expired before expiring, and only requires a PrDP where one is required", () => {
    const d = (o: object) => ({ status: "ACTIVE", licenseExpiry: on(200), prdpRequired: false, prdpExpiry: null, ...o })
    expect(driverDocumentCounts([d({ prdpRequired: true, prdpExpiry: on(-99) }), d({ licenseExpiry: on(10), prdpRequired: true, prdpExpiry: on(-5) }), d({ licenseExpiry: on(10) }), d({ prdpExpiry: on(-99) }), d({ status: "INACTIVE", licenseExpiry: on(-1) }), d({})], today)).toEqual({ expired: 2, expiring: 1 })
  })
})

describe("ranking", () => {
  const ds = [{ id: "a", firstName: "Zed", lastName: "Ames" }, { id: "b", firstName: "Bo", lastName: "Bell" }, { id: "c", firstName: "Cy", lastName: "Cole" }, { id: "d", firstName: "Di", lastName: "Dunn" }, { id: "e", firstName: "Al", lastName: "Eve" }]
  const sc = new Map([["a", { overall: 70, reliable: true }], ["b", { overall: 90, reliable: true }], ["c", { overall: 95, reliable: false }], ["d", { overall: null, reliable: false }], ["e", { overall: 90, reliable: true }]])
  const ids = (x: { id: string }[]) => x.map(d => d.id).join("")
  it("best first puts rated drivers ahead of indicative and no-data ones, ties by name", () => { expect(ids(sortDrivers(ds, "score-desc", sc))).toBe("beacd") })
  it("lowest first starts with the worst rated driver, unrated still last", () => { expect(ids(sortDrivers(ds, "score-asc", sc))).toBe("abecd") })
  it("name order ignores scores, and a missing score row counts as no data", () => { expect(ids(sortDrivers(ds, "name", sc))).toBe("abcde"); expect(ids(sortDrivers(ds, "score-desc", new Map()))).toBe("abcde") })
  it("only reliable scores get a rank", () => { const r = rankMap(sortDrivers(ds, "score-desc", sc), sc); expect([...r.entries()]).toEqual([["b", 1], ["e", 2], ["a", 3]]) })
})

describe("Drivers with Driver 360", () => {
  it("shows each driver's score on the register and marks low-coverage scores as indicative", async () => {
    show(); await screen.findByText("John Mokoena")
    const score = (n: string) => screen.getByLabelText(n).querySelector('[aria-label="Score"]')?.textContent
    expect(score("John Mokoena")).toBe("92.3% · Excellent"); expect(score("Thandi Nkosi")).toBe("61.0%* · Not rated")
    expect(score("New Starter")).toBe("No data · —"); expect(screen.getByText(/Indicative only/)).toBeTruthy()
  })
  it("opens a driver's 360 profile", async () => {
    show(); await screen.findByText("John Mokoena"); fireEvent.click(within("John Mokoena"))
    expect((await screen.findByLabelText("Overall score")).textContent).toContain("92.3%")
    expect(screen.getByLabelText("PrDP").textContent).toContain("12 days left"); expect(screen.getByLabelText("Fuel efficiency").textContent).toContain("No data")
    expect(screen.getByText(/1 accident /)).toBeTruthy(); expect(screen.getByText(/ACC-2026-00021/)).toBeTruthy(); expect(screen.getByText(/9.5 L\/100 km/)).toBeTruthy()
  })
  it("sorts by score with rank badges, and a score the data cannot support gets none", async () => {
    show(); await screen.findByText("John Mokoena"); expect(screen.queryAllByLabelText("Rank").length).toBe(0)
    fireEvent.change(screen.getByLabelText("Sort drivers"), { target: { value: "score-desc" } })
    expect(screen.getAllByLabelText(/^(John Mokoena|Thandi Nkosi|New Starter)$/).map(e => e.getAttribute("aria-label"))).toEqual(["John Mokoena", "Thandi Nkosi", "New Starter"])
    expect(screen.getByLabelText("John Mokoena").querySelector('[aria-label="Rank"]')?.textContent).toBe("#1"); expect(screen.getByLabelText("Thandi Nkosi").querySelector('[aria-label="Rank"]')).toBeNull()
  })
  it("opens the profile under the driver's own card, and closes it again", async () => {
    show(); await screen.findByText("Thandi Nkosi"); fireEvent.click(within("John Mokoena")); const o = await screen.findByLabelText("Overall score")
    expect(screen.getByLabelText("John Mokoena").contains(o)).toBe(true); expect(screen.getByLabelText("Thandi Nkosi").contains(o)).toBe(false)
    fireEvent.click(screen.getByText("Hide 360")); expect(screen.queryByLabelText("Overall score")).toBeNull()
  })
  it("a driver whose PrDP expired is counted as expired, never as expiring", async () => {
    const day = (n: number) => { const x = new Date(); x.setDate(x.getDate() + n); return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, "0")}-${String(x.getDate()).padStart(2, "0")}` }
    get.mockImplementation((url: string) => url.includes("/driver-scores") ? ok(board) : url.includes("/fleet/drivers") ? ok({ content: [{ ...drivers[0], prdpRequired: true, prdpCategory: "G", prdpExpiry: day(-99), prdpExpiringSoon: true }] }) : ok([]))
    show(); await screen.findByText("John Mokoena")
    expect(screen.getByLabelText("Active drivers with an expired document").textContent).toMatch(/^1/); expect(screen.getByLabelText("Active drivers with a document expiring in 30 days").textContent).toMatch(/^0/)
    expect(screen.getByLabelText("John Mokoena").textContent).toContain("Expired 99 days ago"); expect(screen.getByLabelText("John Mokoena").textContent).not.toMatch(/days left/)
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

function within(name: string) { return Array.from(screen.getByLabelText(name).querySelectorAll("button")).find(b => b.textContent === "360 view") as HTMLElement }
