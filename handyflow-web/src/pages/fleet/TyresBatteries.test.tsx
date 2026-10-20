import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TyresBatteriesTab from "./TyresBatteriesTab"
import { batteryActions, batteryBody, batteryProblem, emptyBattery, emptyTyre, fitProblem, removeProblem, treadProblem, treadTone, tyreActions, tyreBody, tyreProblem, voltageProblem } from "./tyres.logic"

const get = vi.fn(), post = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const tyres = [
  { id: "t1", serialNumber: "TY-001", brand: "Bridgestone", size: "295/80R22.5", status: "IN_STOCK", vehicleId: null, registration: null, position: null, lastTreadDepthMm: 14, treadStatus: "OK", kmInService: 0, retreadCount: 0 },
  { id: "t2", serialNumber: "TY-002", brand: "Michelin", size: "295/80R22.5", status: "FITTED", vehicleId: "v1", registration: "ABC123MP", position: "FL", lastTreadDepthMm: 2.4, treadStatus: "REPLACE_SOON", kmInService: 41000, retreadCount: 1 },
]
const batteries = [
  { id: "b1", serialNumber: "BT-9", brand: "Exide", nominalVoltage: 12, capacityAh: 100, status: "INSTALLED", registration: "ABC123MP", position: "1", warrantyExpiry: "2027-01-01", inWarranty: true, lastVoltage: 11.8, lastTestResult: "WEAK", daysInService: 400 },
  { id: "b2", serialNumber: "BT-10", brand: "Willard", nominalVoltage: 12, capacityAh: 80, status: "IN_STOCK", registration: null, position: null, warrantyExpiry: null, inWarranty: false, lastVoltage: null, lastTestResult: null, daysInService: 0 },
]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TyresBatteriesTab /></QueryClientProvider>)
const set = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } })
  get.mockImplementation((url: string) => url.includes("/tyres") ? ok({ content: tyres }) : url.includes("/batteries") ? ok({ content: batteries }) : url.includes("/vehicles") ? ok({ content: [{ id: "v1", registration: "ABC123MP" }] }) : ok([]))
})
afterEach(cleanup)

describe("tyre and battery rules", () => {
  it("offers actions by status", () => {
    expect(tyreActions("IN_STOCK").map(a => a.action)).toEqual(["FIT", "SCRAP"]); expect(tyreActions("FITTED").map(a => a.action)).toEqual(["MEASURE", "REMOVE"])
    expect(tyreActions("AWAITING_RETREAD").map(a => a.action)).toEqual(["RETREAD", "SCRAP"]); expect(tyreActions("SCRAPPED")).toEqual([])
    expect(batteryActions("INSTALLED").map(a => a.action)).toEqual(["TEST", "REMOVE"]); expect(batteryActions("FAULTY").map(a => a.action)).toEqual(["SCRAP"])
    expect(["OK", "REPLACE_SOON", "REPLACE_NOW", "NO_READING"].map(treadTone)).toEqual(["ok", "warn", "bad", "none"])
  })
  it("checks the forms", () => {
    expect(tyreProblem(emptyTyre())).toMatch(/serial/); expect(tyreProblem({ ...emptyTyre(), serialNumber: "x", brand: "b" })).toMatch(/size/)
    expect(tyreProblem({ ...emptyTyre(), serialNumber: "x", brand: "b", size: "s", initialTreadDepthMm: "40" })).toMatch(/30 mm/)
    expect(tyreBody({ ...emptyTyre(), serialNumber: " x ", brand: "b", size: "s" })).toMatchObject({ serialNumber: "x", pattern: null, purchasePrice: null })
    expect(batteryProblem({ ...emptyBattery(), serialNumber: "x", brand: "b", warrantyMonths: "24" })).toMatch(/purchase date/)
    expect(batteryBody({ ...emptyBattery(), serialNumber: "x", brand: "b", nominalVoltage: "24" })).toMatchObject({ nominalVoltage: 24, model: null })
    expect(fitProblem("", "", true)).toMatch(/vehicle/); expect(fitProblem("v", "", true)).toMatch(/where/); expect(fitProblem("v", "", false)).toBeNull()
    expect([treadProblem("0"), treadProblem("12")]).toEqual([expect.stringMatching(/tread/), null]); expect(voltageProblem("40")).toMatch(/32/); expect(removeProblem("SCRAPPED", " ")).toMatch(/why/); expect(removeProblem("IN_STOCK", "")).toBeNull()
  })
})

describe("Tyres", () => {
  it("lists tyres with tread status and where they are", async () => {
    show(); await screen.findByText("TY-001")
    expect(screen.getByLabelText("TY-002").textContent).toContain("ABC123MP · FL"); expect(screen.getByLabelText("TY-002").textContent).toContain("2.4 mm · Replace soon"); expect(screen.getByLabelText("TY-002").textContent).toContain("1× retread")
    expect(screen.getByLabelText("TY-001").textContent).toContain("Store")
  })
  it("fits a stock tyre to a vehicle position", async () => {
    show(); fireEvent.click(await screen.findByLabelText("Fit TY-001")); await screen.findByText("ABC123MP", { selector: "option" })
    set("Vehicle", "v1"); set("Position", "fr"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/tyres/t1/fit", { vehicleId: "v1", position: "FR" }))
  })
  it("records a tread reading", async () => {
    show(); fireEvent.click(await screen.findByLabelText("Measure TY-002")); set("Tread depth (mm)", "2.1"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/tyres/t2/measurements", { treadDepthMm: 2.1 }))
  })
  it("needs a reason to scrap a removed tyre", async () => {
    show(); fireEvent.click(await screen.findByLabelText("Remove TY-002")); set("Outcome", "SCRAPPED")
    expect((screen.getByText("Confirm") as HTMLButtonElement).disabled).toBe(true); set("Scrap reason", "Sidewall cut"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/tyres/t2/remove", { outcome: "SCRAPPED", reason: "WORN", scrapReason: "Sidewall cut" }))
  })
  it("registers a tyre and shows a duplicate-serial refusal", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "A tyre with that serial number already exists" } } })
    show(); fireEvent.click(await screen.findByRole("button", { name: "Register a tyre" })); set("Serial number", "TY-001"); set("Brand", "Bridgestone"); set("Size", "295/80R22.5")
    fireEvent.click(screen.getByText("Register")); expect(await screen.findByText(/already exists/)).toBeTruthy()
  })
})

describe("Batteries", () => {
  it("lists batteries with test result and warranty", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Batteries" })); await screen.findByText("BT-9")
    expect(screen.getByLabelText("BT-9").textContent).toContain("weak · 11.8 V"); expect(screen.getByLabelText("BT-9").textContent).toContain("2027-01-01"); expect(screen.getByLabelText("BT-10").textContent).toContain("Never tested")
  })
  it("records a resting voltage test", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Batteries" })); fireEvent.click(await screen.findByLabelText("Test BT-9")); set("Resting voltage", "12.4"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/batteries/b1/tests", { voltage: 12.4 }))
  })
  it("installs a stock battery", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Batteries" })); fireEvent.click(await screen.findByLabelText("Install BT-10")); await screen.findByText("ABC123MP", { selector: "option" })
    set("Vehicle", "v1"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/batteries/b2/install", { vehicleId: "v1", position: null }))
  })
  it("read-only users see the lists without actions", async () => {
    perms = new Set(["FLEET_READ"]); show(); await screen.findByText("TY-001")
    expect(screen.queryByLabelText("Fit TY-001")).toBeNull(); expect(screen.queryByRole("button", { name: "Register a tyre" })).toBeNull()
  })
})
