import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrackingSetupTab from "./TrackingSetupTab"
import { agoText, deviceBody, deviceProblem, deviceState, emptyDevice, keyNameProblem, keyStatusText, settingsBody, settingsProblem, speedLimitWarning, toDeviceForm, toSettingsForm, untracked } from "./trackingSetup.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), put: (...a: Args) => put(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const NOW = new Date("2026-10-20T12:00:00Z")
const ago = (mins: number) => new Date(NOW.getTime() - mins * 60000).toISOString()
const devices = [
  { id: "d1", provider: "CARTRACK", externalId: "CT-100", vehicleId: "v1", registration: "ABC123MP", name: "Hilux unit", active: true, lastSeenAt: new Date(Date.now() - 5 * 60000).toISOString() },
  { id: "d2", provider: "MIX", externalId: "MX-7", vehicleId: "v2", registration: "XYZ999GP", name: null, active: true, lastSeenAt: null },
]
const vehicles = [{ id: "v1", registration: "ABC123MP", make: "Toyota", model: "Hilux", status: "AVAILABLE" }, { id: "v2", registration: "XYZ999GP", status: "AVAILABLE" }, { id: "v3", registration: "NEW555NW", status: "AVAILABLE" }, { id: "v4", registration: "OLD1", status: "RETIRED" }]
const settings = { defaultSpeedLimitKph: 120, speedingToleranceKph: 5, speedingCooldownMinutes: 10, alertOverspeedKph: 20, idlingThresholdMinutes: 10, offlineAfterMinutes: 30, usingDefaults: true }
const keys = [
  { id: "k1", name: "Cartrack production", keyPrefix: "tk_ab12", createdAt: "2026-09-01T08:00:00Z", lastUsedAt: new Date(Date.now() - 3 * 60000).toISOString(), revokedAt: null, active: true },
  { id: "k2", name: "Old provider", keyPrefix: "tk_zz99", createdAt: "2026-01-01T08:00:00Z", lastUsedAt: null, revokedAt: "2026-02-01T08:00:00Z", active: false },
]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TrackingSetupTab /></QueryClientProvider>)
const set = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); for (const m of [get, post, put, del]) m.mockReset()
  put.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: { data: null } })
  post.mockImplementation((url: string) => url.includes("ingest-keys") ? ok({ key: { ...keys[0], id: "k3", name: "Teltonika", keyPrefix: "tk_new1" }, apiKey: "tk_new1_SECRETVALUE" }) : ok({}))
  get.mockImplementation((url: string) => url.includes("/telematics/devices") ? ok(devices) : url.includes("/telematics/settings") ? ok(settings) : url.includes("/ingest-keys") ? ok(keys) : url.includes("/vehicles") ? ok({ content: vehicles }) : ok([]))
})
afterEach(cleanup)

describe("tracking rules", () => {
  it("checks a device", () => {
    const f = { ...emptyDevice(), externalId: "CT-1", vehicleId: "v1" }
    expect(deviceProblem(f)).toBeNull(); expect(deviceProblem({ ...f, externalId: " " })).toMatch(/unit id/); expect(deviceProblem({ ...f, vehicleId: "" })).toMatch(/vehicle/); expect(deviceProblem({ ...f, name: "x".repeat(151) })).toMatch(/name/)
    expect(deviceBody({ ...f, provider: " ", name: " Truck " })).toEqual({ externalId: "CT-1", provider: null, vehicleId: "v1", name: "Truck", active: true })
    expect(toDeviceForm(devices[1])).toMatchObject({ id: "d2", name: "", provider: "MIX" })
  })
  it("tells a working device from a silent, new or switched-off one", () => {
    const d = (active: boolean, lastSeenAt: string | null) => ({ active, lastSeenAt })
    expect([deviceState(d(false, ago(1)), 30, NOW), deviceState(d(true, null), 30, NOW), deviceState(d(true, ago(10)), 30, NOW), deviceState(d(true, ago(90)), 30, NOW)]).toEqual(["off", "never", "ok", "silent"])
  })
  it("words time since the last report", () => { expect([null, ago(0), ago(1), ago(45), ago(120), ago(60 * 24 * 3)].map(x => agoText(x, NOW))).toEqual(["Never", "Just now", "1 minute ago", "45 minutes ago", "2 hours ago", "3 days ago"]) })
  it("lists vehicles that would never appear on the map", () => { expect(untracked(vehicles, devices).map(v => v.registration)).toEqual(["NEW555NW"]); expect(untracked(vehicles, devices.map(d => ({ ...d, active: false }))).map(v => v.id)).toEqual(["v1", "v2", "v3"]) })
  it("checks the rules against the server's ranges and warns about 120 km/h", () => {
    const f = toSettingsForm(settings)
    expect(settingsProblem(f)).toBeNull(); expect(settingsProblem({ ...f, defaultSpeedLimitKph: "10" })).toMatch(/20 to 200/); expect(settingsProblem({ ...f, speedingToleranceKph: "31" })).toMatch(/0 to 30/)
    expect(settingsProblem({ ...f, offlineAfterMinutes: "" })).toMatch(/Offline after/); expect(settingsProblem({ ...f, idlingThresholdMinutes: "2.5" })).toMatch(/whole number/)
    expect(speedLimitWarning(f)).toMatch(/120/); expect(speedLimitWarning({ ...f, defaultSpeedLimitKph: "100" })).toBeNull()
    expect(settingsBody({ ...f, defaultSpeedLimitKph: "100" })).toMatchObject({ defaultSpeedLimitKph: 100, offlineAfterMinutes: 30 })
  })
  it("checks key names and words their status", () => {
    expect(keyNameProblem(" ")).toMatch(/Name/); expect(keyNameProblem("Cartrack")).toBeNull()
    expect(keyStatusText({ active: true, lastUsedAt: null, revokedAt: null }, NOW)).toBe("Never used"); expect(keyStatusText({ active: true, lastUsedAt: ago(3), revokedAt: null }, NOW)).toBe("Last used 3 minutes ago")
    expect(keyStatusText({ active: false, lastUsedAt: null, revokedAt: "2026-02-01T08:00:00Z" }, NOW)).toMatch(/^Revoked/)
  })
})

describe("Devices", () => {
  it("lists devices with their state and flags untracked vehicles", async () => {
    show(); expect((await screen.findByLabelText("CT-100")).textContent).toContain("Reporting"); expect(screen.getByLabelText("MX-7").textContent).toContain("Never reported")
    expect(screen.getByLabelText("Untracked vehicles").textContent).toContain("NEW555NW"); expect(screen.getByLabelText("Untracked vehicles").textContent).not.toContain("OLD1")
  })
  it("registers a device against a vehicle", async () => {
    show(); fireEvent.click(await screen.findByText("Register device")); expect((screen.getByText("Save device") as HTMLButtonElement).disabled).toBe(true)
    set("Unit id", " TK-55 "); set("Provider", "teltonika"); await screen.findByRole("option", { name: /NEW555NW/ }); set("Vehicle", "v3"); fireEvent.click(screen.getByText("Save device"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/telematics/devices", { externalId: "TK-55", provider: "teltonika", vehicleId: "v3", name: null, active: true }))
  })
  it("moves a device to another vehicle, switches it off, and shows a server refusal", async () => {
    show(); fireEvent.click(await screen.findByLabelText("Edit CT-100")); await screen.findByRole("option", { name: /NEW555NW/ }); set("Vehicle", "v3"); fireEvent.click(screen.getByText("Save device"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/telematics/devices/d1", expect.objectContaining({ vehicleId: "v3", externalId: "CT-100" })))
    fireEvent.click(await screen.findByLabelText("Switch off MX-7")); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/telematics/devices/d2", expect.objectContaining({ active: false })))
    post.mockRejectedValue({ response: { data: { message: "A device with id CT-100 is already registered" } } })
    fireEvent.click(screen.getByText("Register device")); set("Unit id", "CT-100"); await screen.findByRole("option", { name: /NEW555NW/ }); set("Vehicle", "v3"); fireEvent.click(screen.getByText("Save device"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already registered/)
  })
  it("read-only users see devices but cannot change them", async () => {
    perms = new Set(["FLEET_READ"]); show(); await screen.findByLabelText("CT-100"); expect(screen.queryByText("Register device")).toBeNull(); expect(screen.queryByLabelText("Edit CT-100")).toBeNull()
  })
})

describe("Rules", () => {
  it("shows the defaults with the 120 km/h warning, and saves changes", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Rules" })); expect((await screen.findByLabelText("Default speed limit") as HTMLInputElement).value).toBe("120"); expect(screen.getByRole("note").textContent).toMatch(/120/)
    set("Default speed limit", "100"); expect(screen.queryByRole("note")).toBeNull(); fireEvent.click(screen.getByText("Save rules"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/telematics/settings", expect.objectContaining({ defaultSpeedLimitKph: 100, offlineAfterMinutes: 30 })))
  })
  it("blocks a value out of range, and locks the fields without FLEET_ADMIN", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Rules" })); await screen.findByLabelText("Speeding tolerance"); set("Speeding tolerance", "50")
    expect((screen.getByText("Save rules") as HTMLButtonElement).disabled).toBe(true); expect(screen.getByText(/0 to 30/)).toBeTruthy()
    cleanup(); perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show(); fireEvent.click(screen.getByRole("tab", { name: "Rules" }))
    expect((await screen.findByLabelText("Speeding tolerance") as HTMLInputElement).disabled).toBe(true); expect(screen.queryByText("Save rules")).toBeNull()
  })
})

describe("Ingest keys", () => {
  it("is for administrators only", () => { perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show(); expect(screen.queryByRole("tab", { name: "Ingest keys" })).toBeNull() })
  it("lists keys by prefix only, never the secret", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Ingest keys" })); expect((await screen.findByLabelText("Cartrack production")).textContent).toContain("tk_ab12…")
    expect(screen.getByLabelText("Cartrack production").textContent).toContain("Last used 3 minutes ago"); expect(screen.getByLabelText("Old provider").textContent).toMatch(/Revoked/); expect(screen.queryByLabelText("Revoke Old provider")).toBeNull()
  })
  it("creates a key and shows the secret once, with a copy button", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined); Object.assign(navigator, { clipboard: { writeText } })
    show(); fireEvent.click(screen.getByRole("tab", { name: "Ingest keys" })); await screen.findByLabelText("Key name")
    expect((screen.getByText("Create key") as HTMLButtonElement).disabled).toBe(true); set("Key name", "Teltonika"); fireEvent.click(screen.getByText("Create key"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/telematics/ingest-keys", { name: "Teltonika" }))
    expect((await screen.findByLabelText("New API key")).textContent).toBe("tk_new1_SECRETVALUE"); expect(screen.getByText(/will not be shown again/)).toBeTruthy()
    fireEvent.click(screen.getByText("Copy key")); await waitFor(() => expect(writeText).toHaveBeenCalledWith("tk_new1_SECRETVALUE")); expect(await screen.findByText("Copied")).toBeTruthy()
    fireEvent.click(screen.getByText("I have saved it")); expect(screen.queryByLabelText("New API key")).toBeNull()
  })
  it("revokes only after a confirm that says the provider is cut off", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Ingest keys" })); fireEvent.click(await screen.findByLabelText("Revoke Cartrack production")); expect(del).not.toHaveBeenCalled()
    fireEvent.click(screen.getByLabelText("Confirm revoke Cartrack production")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fleet/telematics/ingest-keys/k1"))
  })
})
