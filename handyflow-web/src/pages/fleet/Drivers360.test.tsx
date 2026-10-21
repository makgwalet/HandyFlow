import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DriversTab from "./DriversTab"
import { fetchAllPages } from "./apiShape"
import { componentTone, documentState, filterDrivers, paginate, daysToDate, driverDocumentCounts, driverVehicles, initials, periodText, ringFraction, expiryState, expiryWords, sortDrivers, rankMap, bandText, bandToneFor, bandTone, coverageNote, scoreText, settingsBody, settingsProblem, toForm, weightTotal } from "./driverScores.logic"

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
  driver: { firstName: "John", lastName: "Mokoena", licenseCode: "EC", prdpRequired: true, status: "ACTIVE", phone: "082 123 4567", email: "john@fleet.co.za", idNumber: "8001015009087", licenseNumber: "AB123456", licenseExpiry: "2027-08-01", prdpNumber: "PD-77", prdpCategory: "G", prdpExpiry: "2026-11-01", notes: "Prefers early starts" },
  compliance: [{ key: "LICENCE", label: "Driving licence", status: "VALID", daysRemaining: 300 }, { key: "PRDP", label: "PrDP", status: "EXPIRING_SOON", daysRemaining: 12 }],
  score: { overall: 92.3, band: "EXCELLENT", coverage: 100, reliable: true, periodFrom: "2026-07-22", periodTo: "2026-10-20", components: [{ key: "SAFETY", label: "Safety", weight: 35, effectiveWeight: 35, score: 95, status: "SCORED", detail: "0 harsh events" }, { key: "FUEL", label: "Fuel efficiency", weight: 10, effectiveWeight: 0, score: null, status: "NO_DATA", detail: "No fuel recorded" }] },
  activity: { tripCount: 40, completedTrips: 39, totalKm: 5200, businessKm: 5000, privateKm: 200, fuelLitres: 400, litresPer100Km: 9.5, inspectionsCompleted: 30, inspectionsFailed: 2, vehiclesDriven: ["ABC123MP"] },
  accidents: { count: 1, withInjuries: 0, withFatalities: 0, totalRepairCost: 12000, totalExcess: 5000, accidents: [{ id: "a1", referenceNumber: "ACC-2026-00021", occurredAt: "2026-09-01T08:00:00Z", status: "IN_REPAIR" }] },
}
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DriversTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: { data: settings } })
  get.mockImplementation((url: string) => url.includes("/profile") ? ok(profile) : url.includes("/driver-score-settings") ? ok(settings) : url.includes("/driver-scores") ? ok(board) : url.includes("/fleet/drivers") ? ok({ content: drivers }) : url.includes("/fleet/vehicles") ? ok({ content: [{ id: "v1", registration: "ABC123MP", make: "Toyota", model: "Hilux", assignedDriverId: "d1" }, { id: "v2", registration: "XYZ999GP", make: "Isuzu", model: "D-Max", assignedDriverId: null }] }) : ok([]))
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

describe("profile page rules", () => {
  it("colours a component by its score", () => { expect([null, 95, 80, 79, 60, 59, 0].map(componentTone)).toEqual(["none", "ok", "ok", "warn", "warn", "bad", "bad"]) })
  it("makes initials, a period, and a ring fraction", () => {
    expect(initials(" john", "mokoena")).toBe("JM"); expect(initials("", "x")).toBe("X")
    expect(periodText("2026-07-22", "2026-10-20")).toBe("22 Jul – 20 Oct 2026"); expect(periodText("2025-12-01", "2026-02-28")).toBe("1 Dec 2025 – 28 Feb 2026")
    expect([null, -5, 50, 140].map(ringFraction)).toEqual([0, 0, 0.5, 1]); expect(ringFraction(92.3)).toBeCloseTo(0.923, 5)
  })
  it("finds a driver's own vehicles only", () => { expect(driverVehicles([{ id: 1, assignedDriverId: "a" }, { id: 2, assignedDriverId: "b" }, { id: 3 }], "a")).toEqual([{ id: 1, assignedDriverId: "a" }]) })
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
    expect(screen.getByLabelText("Assigned vehicles").textContent).toContain("ABC123MP · Toyota Hilux"); expect(screen.getByLabelText("Assigned vehicles").textContent).not.toContain("XYZ999GP")
    const who = screen.getByLabelText("Driver details").textContent ?? ""; expect(who).toContain("082 123 4567"); expect(who).toContain("john@fleet.co.za"); expect(who).toContain("Prefers early starts"); expect(who).toContain("JM")
  })
  it("shows the driver's photo box and opens the documents and medical tabs", async () => {
    const docs = [{ id: "m1", type: "MEDICAL_CERTIFICATE", title: null, documentNumber: null, issueDate: null, expiryDate: "2026-11-10", version: 1, fileName: "med.pdf", expired: false, daysToExpiry: 20 }]
    get.mockImplementation((url: string) => url.includes("/drivers/d1/documents") ? ok(url.includes("type=PHOTO") ? [] : docs) : url.includes("/profile") ? ok(profile) : url.includes("/driver-scores") ? ok(board) : url.includes("/fleet/drivers") ? ok({ content: drivers }) : ok([]))
    show(); await screen.findByText("John Mokoena"); fireEvent.click(within("John Mokoena")); await screen.findByLabelText("Overall score")
    expect(screen.getByRole("button", { name: "Add photo" })).toBeTruthy()
    fireEvent.click(screen.getByRole("tab", { name: "Medical & training" })); expect((await screen.findByLabelText("Medical certificate")).textContent).toContain("20 days left")
    fireEvent.click(screen.getByRole("tab", { name: "Documents" })); expect((await screen.findByRole("status")).textContent).toContain("No documents on file yet")
  })
  it("keeps accidents, activity and licence details on their own tabs", async () => {
    show(); await screen.findByText("John Mokoena"); fireEvent.click(within("John Mokoena")); await screen.findByLabelText("Overall score")
    fireEvent.click(screen.getByRole("tab", { name: "Incidents" })); expect(screen.getByText(/1 accident /)).toBeTruthy(); expect(screen.getByText(/ACC-2026-00021/)).toBeTruthy()
    fireEvent.click(screen.getByRole("tab", { name: "Performance" })); expect(screen.getByText("39 of 40")).toBeTruthy(); expect(screen.getAllByText(/9.5 L\/100 km/).length).toBeGreaterThan(0); expect(screen.getByText("0 harsh events")).toBeTruthy()
    fireEvent.click(screen.getByRole("tab", { name: "Compliance" })); expect(screen.getByText("AB123456")).toBeTruthy(); expect(screen.getByText("PD-77")).toBeTruthy()
    fireEvent.click(screen.getByRole("tab", { name: "Overview" })); expect(screen.getByLabelText("PrDP")).toBeTruthy()
  })
  it("shows the profile as its own page and goes back to the list", async () => {
    show(); await screen.findByText("Thandi Nkosi"); fireEvent.click(within("John Mokoena")); await screen.findByLabelText("Overall score")
    expect(screen.queryByText("Thandi Nkosi")).toBeNull(); fireEvent.click(screen.getByText("← Back to drivers"))
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy(); expect(screen.queryByLabelText("Overall score")).toBeNull()
  })
  it("edits the driver from the profile", async () => {
    show(); await screen.findByText("John Mokoena"); fireEvent.click(within("John Mokoena")); await screen.findByLabelText("Overall score")
    fireEvent.click(screen.getByText("Edit driver")); expect(await screen.findByText("Edit Driver")).toBeTruthy()
  })
  it("says so when the profile cannot be loaded", async () => {
    get.mockImplementation((url: string) => url.includes("/profile") ? Promise.reject(new Error("x")) : url.includes("/driver-scores") ? ok(board) : url.includes("/fleet/drivers") ? ok({ content: drivers }) : ok([]))
    show(); await screen.findByText("John Mokoena"); fireEvent.click(within("John Mokoena")); expect((await screen.findByRole("alert")).textContent).toMatch(/Couldn't load this driver/)
  })
  it("sorts by score with rank badges, and a score the data cannot support gets none", async () => {
    show(); await screen.findByText("John Mokoena"); expect(screen.queryAllByLabelText("Rank").length).toBe(0)
    fireEvent.change(screen.getByLabelText("Sort drivers"), { target: { value: "score-desc" } })
    expect(screen.getAllByLabelText(/^(John Mokoena|Thandi Nkosi|New Starter)$/).map(e => e.getAttribute("aria-label"))).toEqual(["John Mokoena", "Thandi Nkosi", "New Starter"])
    expect(screen.getByLabelText("John Mokoena").querySelector('[aria-label="Rank"]')?.textContent).toBe("#1"); expect(screen.getByLabelText("Thandi Nkosi").querySelector('[aria-label="Rank"]')).toBeNull()
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

describe("a long driver register", () => {
  const on = (n: number) => { const d = new Date(); d.setDate(d.getDate() + n); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
  const mk = (i: number, o: object = {}) => ({ ...drivers[0], id: `x${i}`, firstName: `Driver${String(i).padStart(3, "0")}`, lastName: "Test", phone: `0821000${String(i).padStart(3, "0")}`, licenseNumber: `LIC${i}`, licenseExpiry: on(300), status: "ACTIVE", ...o })
  const many = Array.from({ length: 60 }, (_, i) => mk(i + 1))
  const serve = (list: unknown[], pages = 1) => get.mockImplementation((url: string) => {
    if (url.includes("/fleet/drivers")) { const page = Number(/page=(\d+)/.exec(url)?.[1] ?? 0), size = Number(/size=(\d+)/.exec(url)?.[1] ?? 200); return ok({ content: list.slice(page * size, (page + 1) * size), totalPages: pages }) }
    return url.includes("/driver-scores") ? ok([]) : ok([])
  })
  it("rules: documents, search and paging", () => {
    const base = { ...drivers[0], prdpRequired: false, prdpExpiry: null }
    expect([on(-1), on(5), on(40)].map(e => documentState({ ...base, licenseExpiry: e }))).toEqual(["EXPIRED", "EXPIRING", "OK"])
    expect(documentState({ ...base, licenseExpiry: on(300), prdpRequired: true, prdpExpiry: on(-3) })).toBe("EXPIRED"); expect(documentState({ ...base, licenseExpiry: on(300), prdpExpiry: on(-3) })).toBe("OK")
    const list = [mk(1), mk(2, { licenseExpiry: on(-5) }), mk(3, { status: "INACTIVE" })]
    expect(filterDrivers(list, { search: "driver002", status: "ALL", docs: "ALL" }).map(d => d.id)).toEqual(["x2"]); expect(filterDrivers(list, { search: "test 0821000003", status: "ALL", docs: "ALL" }).map(d => d.id)).toEqual(["x3"])
    expect(filterDrivers(list, { search: "", status: "ACTIVE", docs: "ALL" }).length).toBe(2); expect(filterDrivers(list, { search: "", status: "ALL", docs: "EXPIRED" }).map(d => d.id)).toEqual(["x2"])
    expect(paginate(many, 0)).toMatchObject({ pages: 3, from: 1, to: 25, total: 60 }); expect(paginate(many, 2)).toMatchObject({ from: 51, to: 60 }); expect(paginate(many, 9).page).toBe(2); expect(paginate([], 0)).toMatchObject({ pages: 1, from: 0, to: 0, rows: [] })
  })
  it("reads every page of a paged list, and says when it stopped early", async () => {
    const pages = [[1, 2], [3, 4], [5]]
    const g = (u: string) => Promise.resolve({ data: { data: { content: pages[Number(/page=(\d+)/.exec(u)![1])] ?? [], totalPages: 3 } } })
    expect(await fetchAllPages<number>(g, "/x?y=1", 2)).toEqual({ items: [1, 2, 3, 4, 5], truncated: false })
    expect(await fetchAllPages<number>(g, "/x", 2, 2)).toEqual({ items: [1, 2, 3, 4], truncated: true })
    expect(await fetchAllPages<number>(() => Promise.resolve({ data: { data: [7, 8] } }), "/x")).toEqual({ items: [7, 8], truncated: false })
  })
  it("shows 25 at a time and moves between pages", async () => {
    serve(many); show(); await screen.findByText("Driver001 Test")
    expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 60"); expect(screen.queryByText("Driver026 Test")).toBeNull()
    fireEvent.click(screen.getByText("Next")); expect(await screen.findByText("Driver026 Test")).toBeTruthy(); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 26–50 of 60")
    fireEvent.click(screen.getByText("Next")); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 51–60 of 60"); expect((screen.getByText("Next") as HTMLButtonElement).disabled).toBe(true)
  })
  it("searches the whole register and goes back to the first page", async () => {
    serve(many); show(); await screen.findByText("Driver001 Test"); fireEvent.click(screen.getByText("Next")); await screen.findByText("Driver026 Test")
    fireEvent.change(screen.getByLabelText("Search drivers"), { target: { value: "driver057" } })
    expect(await screen.findByText("Driver057 Test")).toBeTruthy(); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–1 of 1"); expect(screen.queryByText("Next")).toBeNull()
    fireEvent.change(screen.getByLabelText("Search drivers"), { target: { value: "nobody" } }); expect(await screen.findByText("No drivers match")).toBeTruthy()
  })
  it("reads past the first 200 drivers", async () => {
    const lots = Array.from({ length: 230 }, (_, i) => mk(i + 1)); serve(lots, 2); show()
    await screen.findByText("Driver001 Test"); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 230"); expect(screen.getByLabelText("Total drivers").textContent).toMatch(/^230/)
  })
  it("clicking the expired tile lists just those drivers, and clicking it again clears it", async () => {
    serve([mk(1), mk(2, { licenseExpiry: on(-5) }), mk(3, { licenseExpiry: on(5) })]); show(); await screen.findByText("Driver001 Test")
    fireEvent.click(screen.getByLabelText("Active drivers with an expired document")); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–1 of 1"); expect(screen.getByText("Driver002 Test")).toBeTruthy(); expect(screen.queryByText("Driver001 Test")).toBeNull()
    fireEvent.click(screen.getByLabelText("Active drivers with an expired document")); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–3 of 3")
    fireEvent.change(screen.getByLabelText("Documents"), { target: { value: "EXPIRING" } }); expect(screen.getByText("Driver003 Test")).toBeTruthy(); expect(screen.queryByText("Driver002 Test")).toBeNull()
  })
  it("keeps a driver's rank among all drivers when the list is searched", async () => {
    serve([mk(1), mk(2)]); get.mockImplementation((url: string) => url.includes("/driver-scores") ? ok([{ driverId: "x1", driverName: "a", overall: 70, band: "FAIR", coverage: 100, reliable: true, tripCount: 9 }, { driverId: "x2", driverName: "b", overall: 90, band: "EXCELLENT", coverage: 100, reliable: true, tripCount: 9 }]) : url.includes("/fleet/drivers") ? ok({ content: [mk(1), mk(2)] }) : ok([]))
    show(); await screen.findByText("Driver001 Test"); fireEvent.change(screen.getByLabelText("Sort drivers"), { target: { value: "score-desc" } })
    fireEvent.change(screen.getByLabelText("Search drivers"), { target: { value: "driver001" } })
    expect(screen.getByLabelText("Driver001 Test").querySelector('[aria-label="Rank"]')?.textContent).toBe("#2")
  })
})
