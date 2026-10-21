import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import VehicleDetail from "./VehicleDetail"
import { derivedPayload, toVehicleForm, vehicleBody, vehicleProblem } from "./vehicleEdit.logic"
import { insuranceBody, insuranceProblem, toInsuranceForm } from "./insurance.logic"

const get = vi.fn(), put = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), put: (...a: Args) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const vehicle = { registration: "ABC123MP", make: "Isuzu", model: "NPR", year: 2021, vehicleType: "TRUCK", fuelType: "DIESEL", vin: "VIN1", engineNumber: null, gvmKg: 7500, tareKg: 3000, serviceIntervalKm: 15000, licenceDiscExpiry: "2027-01-31", notes: "Depot A" }
const show = (v = vehicle) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><VehicleDetail vehicleId="v1" identity={v} /></QueryClientProvider>)
const baseInsurance = { insured: true, insurer: "Santam", policyNumber: "P-77", policyType: "COMPREHENSIVE", policyStart: "2026-01-01", policyExpiry: "2026-12-31", daysToExpiry: 72, premium: 1200, excess: 5000, coverAmount: 300000, trackingRequired: true, trackingProvider: "Netstar", trackingDeviceNumber: "N-1", windscreenCover: true, broker: "Alexander Forbes" }

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); get.mockReset(); put.mockReset(); put.mockResolvedValue({ data: { data: {} } })
  get.mockImplementation((url: string) => url.endsWith("/insurance") ? ok(baseInsurance) : ok([]))
})
afterEach(cleanup)

describe("vehicle edit rules", () => {
  it("round-trips the form and sends every field, with blanks as null", () => {
    const f = toVehicleForm(vehicle)
    expect(f.engineNumber).toBe(""); expect(f.gvmKg).toBe("7500")
    expect(vehicleBody(f)).toMatchObject({ registration: "ABC123MP", year: 2021, engineNumber: null, gvmKg: 7500, serviceIntervalKm: 15000, licenceDiscExpiry: "2027-01-31", notes: "Depot A", valuedAt: null })
    expect(Object.keys(vehicleBody(f))).toHaveLength(32)
  })
  it("blocks bad values", () => {
    const f = toVehicleForm(vehicle)
    expect(vehicleProblem(f, 2026)).toBeNull()
    expect(vehicleProblem({ ...f, registration: " " })).toMatch(/Registration/)
    expect(vehicleProblem({ ...f, year: "1900" }, 2026)).toMatch(/year/)
    expect(vehicleProblem({ ...f, axleCount: "0" })).toMatch(/Axles/)
    expect(vehicleProblem({ ...f, purchasePrice: "-1" })).toMatch(/Purchase price/)
    expect(vehicleProblem({ ...f, gvmKg: "2000" })).toMatch(/Tare/)
    expect(vehicleProblem({ ...f, gcmKg: "1.5" })).toMatch(/whole number/)
  })
  it("works out payload from GVM minus tare when blank", () => {
    expect(derivedPayload(toVehicleForm(vehicle))).toBe(4500); expect(derivedPayload({ ...toVehicleForm(vehicle), payloadKg: "4000" })).toBeNull()
  })
})

describe("insurance rules", () => {
  it("needs an insurer and expiry when insured, and sane dates and amounts", () => {
    const f = toInsuranceForm(baseInsurance)
    expect(insuranceProblem(f)).toBeNull()
    expect(insuranceProblem({ ...f, insurer: "" })).toMatch(/Insurer/); expect(insuranceProblem({ ...f, policyExpiry: "" })).toMatch(/expiry/)
    expect(insuranceProblem({ ...f, policyExpiry: "2025-12-31" })).toMatch(/before it starts/); expect(insuranceProblem({ ...f, premium: "-5" })).toMatch(/Premium/)
    expect(insuranceProblem({ ...f, trackingProvider: "" })).toMatch(/tracking provider/)
    expect(insuranceProblem({ ...f, insured: false, insurer: "", policyExpiry: "" })).toBeNull()
  })
  it("drops tracking details when tracking is not required", () => {
    expect(insuranceBody({ ...toInsuranceForm(baseInsurance), trackingRequired: false })).toMatchObject({ trackingRequired: false, trackingProvider: null, trackingDeviceNumber: null })
    expect(toInsuranceForm(null)).toMatchObject({ insured: true, policyType: "COMPREHENSIVE", insurer: "" })
  })
})

describe("Vehicle edit screen", () => {
  it("edits identity and saves the whole record", async () => {
    show(); fireEvent.click(screen.getByText("Edit details"))
    fireEvent.change(screen.getByLabelText("Engine number"), { target: { value: "4HK1-998" } })
    expect(screen.getByText(/Payload will be saved as 4.500 kg/)).toBeTruthy()
    fireEvent.click(screen.getByText("Save changes"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1", expect.objectContaining({ engineNumber: "4HK1-998", vin: "VIN1", serviceIntervalKm: 15000, licenceDiscExpiry: "2027-01-31", registration: "ABC123MP" })))
    await waitFor(() => expect(screen.queryByLabelText("Edit vehicle")).toBeNull())
  })
  it("blocks save on a bad value and shows a server refusal", async () => {
    put.mockRejectedValue({ response: { data: { message: "Engine number is already used by another vehicle" } } })
    show(); fireEvent.click(screen.getByText("Edit details"))
    fireEvent.change(screen.getByLabelText("Tare (kg)"), { target: { value: "9000" } })
    expect((screen.getByText("Save changes") as HTMLButtonElement).disabled).toBe(true); expect(screen.getByText(/Tare cannot be more/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Tare (kg)"), { target: { value: "3000" } }); fireEvent.click(screen.getByText("Save changes"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already used/)
  })
  it("hides editing from people without FLEET_MANAGE", () => {
    perms = new Set(["FLEET_READ"]); show(); expect(screen.queryByText("Edit details")).toBeNull()
  })
  it("edits the insurance profile", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Insurance" })); fireEvent.click(await screen.findByText("Edit insurance"))
    fireEvent.change(await screen.findByLabelText("Policy expiry"), { target: { value: "2027-12-31" } }); fireEvent.change(screen.getByLabelText("Premium (R)"), { target: { value: "1500" } })
    fireEvent.click(screen.getByText("Save insurance"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/insurance", expect.objectContaining({ insured: true, insurer: "Santam", policyExpiry: "2027-12-31", premium: 1500, trackingProvider: "Netstar" })))
  })
  it("records insurance for a vehicle with none, and the read view shows the new fields", async () => {
    get.mockImplementation((url: string) => url.endsWith("/insurance") ? ok(null) : ok([]))
    show(); fireEvent.click(screen.getByRole("tab", { name: "Insurance" })); expect(await screen.findByText("No insurance recorded for this vehicle.")).toBeTruthy()
    fireEvent.click(screen.getByText("Record insurance")); fireEvent.click(await screen.findByLabelText("Vehicle is insured"))
    expect(screen.queryByLabelText("Insurer")).toBeNull(); expect((screen.getByText("Save insurance") as HTMLButtonElement).disabled).toBe(false)
  })
  it("shows broker, windscreen and tracking device on the read view", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Insurance" }))
    expect(await screen.findByText("Alexander Forbes")).toBeTruthy(); expect(screen.getByText("Covered")).toBeTruthy(); expect(screen.getByText(/Netstar · N-1/)).toBeTruthy()
  })
})
