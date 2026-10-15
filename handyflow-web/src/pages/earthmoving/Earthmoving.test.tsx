import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const patch = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a),
    patch: (...a: unknown[]) => patch(...a), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import AssetsTab from "./AssetsTab"
import IncidentsTab from "./IncidentsTab"
import MaintenanceTab from "./MaintenanceTab"
import { fetchAssets } from "./shared/api"
import { STATUS_TRANSITIONS, authorityForStatusChange, isRelease, needsReason } from "./shared/constants"

afterEach(cleanup)

const asset = (over: Record<string, unknown> = {}) => ({
  id: "a1", name: "CAT D9", fleetNumber: "D9-001", assetType: "DOZER", make: "Caterpillar", model: "D9T", year: 2020,
  serialNumber: "S1", registration: "GP1", ownershipType: "OWN", hireSupplier: null, hireStartDate: null, hireEndDate: null,
  status: "AVAILABLE", currentSite: null, currentClient: null, dailyRate: 18500, hourlyRate: 2200, currentHours: 100,
  lastServiceHours: 0, serviceIntervalHours: 250, dueForService: false, notes: null, createdAt: "2026-01-01T00:00:00Z", ...over,
})
const incident = (over: Record<string, unknown> = {}) => ({
  id: "i1", assetId: "a1", type: "BREAKDOWN", severity: "CRITICAL", title: "Hydraulic failure", description: null,
  operatorName: null, siteName: null, latitude: null, longitude: null, status: "OPEN", reportedAt: "2026-10-14T08:00:00Z",
  resolvedAt: null, resolutionNotes: null, ...over,
})

let assets: unknown[] = []
let incidents: unknown[] = []
let history: unknown[] = []
const page = (content: unknown[]) => ({ data: { data: { content, last: true } } })

const mount = (ui: React.ReactElement) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = []
  assets = [asset()]
  incidents = []
  history = []
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("status-history") ? page(history) : url.includes("assignable-users") ? { data: [{ id: "u2", name: "Thandi Supervisor" }] } : url.includes("/incidents") ? page(incidents) : url.includes("/assets") && !url.includes("/maintenance") ? page(assets) : page([])))
  patch.mockResolvedValue({ data: { data: asset() } })
  put.mockResolvedValue({ data: { data: asset() } })
})

describe("status rules in the screen", () => {
  it("mirror the server: release needs its own authority, returning from site needs none of a reason", () => {
    expect(authorityForStatusChange("MAINTENANCE", "AVAILABLE")).toBe("EARTHMOVING_RELEASE")
    expect(authorityForStatusChange("BREAKDOWN", "AVAILABLE")).toBe("EARTHMOVING_RELEASE")
    expect(authorityForStatusChange("DEPLOYED", "AVAILABLE")).toBe("EARTHMOVING_DEPLOY")
    expect(authorityForStatusChange("AVAILABLE", "RETIRED")).toBe("EARTHMOVING_ADMIN")
    expect(isRelease("BREAKDOWN", "AVAILABLE")).toBe(true)
    expect(isRelease("DEPLOYED", "AVAILABLE")).toBe(false)
    expect(needsReason("DEPLOYED", "AVAILABLE")).toBe(false)
    expect(needsReason("MAINTENANCE", "AVAILABLE")).toBe(true)
    for (const list of Object.values(STATUS_TRANSITIONS)) expect(list).not.toContain("DEPLOYED")
  })
})

describe("loading the fleet", () => {
  it("reads every page, not just the first 200", async () => {
    get.mockReset()
    get.mockResolvedValueOnce({ data: { data: { content: [asset({ id: "a1" })], last: false } } })
      .mockResolvedValueOnce({ data: { data: { content: [asset({ id: "a2" })], last: true } } })
    const all = await fetchAssets()
    expect(all.map(a => a.id)).toEqual(["a1", "a2"])
    expect(get).toHaveBeenCalledTimes(2)
    expect(get.mock.calls[1][0]).toContain("page=1")
  })
})

describe("Fleet screen", () => {
  it("a read-only person sees the fleet but no way to change it", async () => {
    perms = ["EARTHMOVING_READ"]
    mount(<AssetsTab />)
    expect((await screen.findAllByText("CAT D9")).length).toBeGreaterThan(0)
    expect(screen.queryByText(/Register Equipment/)).toBeNull()
    expect(screen.queryByLabelText("Edit details")).toBeNull()
    expect(screen.queryByLabelText("Change status")).toBeNull()
    expect(screen.queryByLabelText("Update hour meter")).toBeNull()
  })

  it("edit opens with the machine's details and saves them with a PUT", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount(<AssetsTab />)
    fireEvent.click(await screen.findByLabelText("Edit details"))
    const name = await screen.findByDisplayValue("CAT D9")
    fireEvent.change(name, { target: { value: "CAT D9T" } })
    fireEvent.click(screen.getByText("Save Changes"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    const [url, body] = put.mock.calls[0]
    expect(url).toBe("/api/v1/earthmoving/assets/a1")
    expect(body.name).toBe("CAT D9T")
    expect(body.serialNumber).toBe("S1")
    expect(body.serviceIntervalHours).toBe(250)
  })

  it("never offers Deployed in the status dialog", async () => {
    perms = ["EARTHMOVING_WRITE", "EARTHMOVING_DEPLOY", "EARTHMOVING_MAINTAIN", "EARTHMOVING_ADMIN"]
    mount(<AssetsTab />)
    fireEvent.click(await screen.findByLabelText("Change status"))
    const dlg = await screen.findByRole("dialog")
    expect(dlg.textContent).toContain("Maintenance")
    expect(dlg.textContent).not.toContain("Machine is active on a site")
    expect(dlg.textContent).toContain("Deployments")
  })

  it("release is disabled for someone without the release permission, and says why", async () => {
    perms = ["EARTHMOVING_WRITE", "EARTHMOVING_DEPLOY", "EARTHMOVING_MAINTAIN", "EARTHMOVING_ADMIN"]
    assets = [asset({ status: "MAINTENANCE" })]
    mount(<AssetsTab />)
    fireEvent.click(await screen.findByLabelText("Change status"))
    const release = (await screen.findByText("Release to service")).closest("button") as HTMLButtonElement
    expect(release.disabled).toBe(true)
    expect(screen.getByText(/Needs the EARTHMOVING_RELEASE permission/)).toBeTruthy()
  })

  it("a release needs a written reason before it can be sent", async () => {
    perms = ["EARTHMOVING_WRITE", "EARTHMOVING_RELEASE"]
    assets = [asset({ status: "BREAKDOWN" })]
    mount(<AssetsTab />)
    fireEvent.click(await screen.findByLabelText("Change status"))
    fireEvent.click((await screen.findByText("Release to service")).closest("button") as HTMLButtonElement)
    const submit = screen.getByRole("button", { name: "Release" }) as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "ok" } })
    expect(submit.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Pump replaced, tested under load" } })
    expect(submit.disabled).toBe(false)
    fireEvent.click(submit)
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][0]).toBe("/api/v1/earthmoving/assets/a1/status")
    expect(patch.mock.calls[0][1]).toEqual({ status: "AVAILABLE", note: "Pump replaced, tested under load", overrideReason: null })
  })

  it("an override needs its own reason and is sent only when ticked", async () => {
    perms = ["EARTHMOVING_WRITE", "EARTHMOVING_RELEASE"]
    assets = [asset({ status: "BREAKDOWN" })]
    mount(<AssetsTab />)
    fireEvent.click(await screen.findByLabelText("Change status"))
    fireEvent.click((await screen.findByText("Release to service")).closest("button") as HTMLButtonElement)
    fireEvent.change(screen.getByLabelText(/^Reason/), { target: { value: "Fault was a loose connector" } })
    const submit = screen.getByRole("button", { name: "Release" }) as HTMLButtonElement
    fireEvent.click(screen.getByLabelText(/Release without a completed work order/))
    expect(submit.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Why is this release allowed/), { target: { value: "Supplier fixed it on site" } })
    expect(submit.disabled).toBe(false)
    fireEvent.click(submit)
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][1]).toEqual({ status: "AVAILABLE", note: "Fault was a loose connector", overrideReason: "Supplier fixed it on site" })
  })

  it("shows the status history with names, the work order and any override", async () => {
    perms = ["EARTHMOVING_READ"]
    history = [{ id: "e1", assetId: "a1", fromStatus: "BREAKDOWN", toStatus: "AVAILABLE", reason: "Pump replaced", actorUserId: "u2",
      meterHours: 812, happenedAt: "2026-10-15T08:00:00Z", workOrderId: null, overrideReason: "Supplier fixed it on site" }]
    mount(<AssetsTab />)
    fireEvent.click(await screen.findByLabelText("View profile"))
    fireEvent.click(await screen.findByRole("button", { name: "History" }))
    expect(await screen.findByText(/Thandi Supervisor/)).toBeTruthy()
    expect(screen.getByText(/Pump replaced/)).toBeTruthy()
    expect(screen.getByText(/Supplier fixed it on site/)).toBeTruthy()
  })

  it("a server refusal is shown in the dialog", async () => {
    perms = ["EARTHMOVING_WRITE", "EARTHMOVING_RELEASE"]
    assets = [asset({ status: "BREAKDOWN" })]
    patch.mockRejectedValue({ response: { data: { message: "This machine has an unresolved critical incident." } } })
    mount(<AssetsTab />)
    fireEvent.click(await screen.findByLabelText("Change status"))
    fireEvent.click((await screen.findByText("Release to service")).closest("button") as HTMLButtonElement)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Repaired and checked" } })
    fireEvent.click(screen.getByRole("button", { name: "Release" }))
    expect(await screen.findByText(/unresolved critical incident/)).toBeTruthy()
  })
})

describe("Incidents", () => {
  it("resolving asks what was done and sends it", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    incidents = [incident()]
    mount(<IncidentsTab />)
    fireEvent.click(await screen.findByText("Resolve"))
    const submit = within(screen.getByRole("dialog")).getByRole("button", { name: "Resolve" }) as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/What was done/), { target: { value: "Hose replaced and pressure tested" } })
    expect(submit.disabled).toBe(false)
    fireEvent.click(submit)
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][1]).toEqual({ resolutionNotes: "Hose replaced and pressure tested" })
  })

  it("someone without maintain authority cannot resolve, and without write cannot report", async () => {
    perms = ["EARTHMOVING_READ"]
    incidents = [incident()]
    mount(<IncidentsTab />)
    expect(await screen.findByText("Hydraulic failure")).toBeTruthy()
    expect(screen.queryByText("Resolve")).toBeNull()
    expect(screen.queryByText(/Report Incident/)).toBeNull()
  })
})

describe("Maintenance", () => {
  it("says that recording work does not release a machine that is in maintenance", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    assets = [asset({ status: "MAINTENANCE" })]
    mount(<MaintenanceTab />)
    const select = await screen.findByRole("combobox")
    await screen.findByText(/CAT D9/, { selector: "option" })
    fireEvent.change(select, { target: { value: "a1" } })
    expect((await screen.findByRole("note")).textContent).toContain("does not put it back in service")
  })

  it("hides the record button from someone without maintain authority", async () => {
    perms = ["EARTHMOVING_READ"]
    mount(<MaintenanceTab />)
    const select = await screen.findByRole("combobox")
    await screen.findByText(/CAT D9/, { selector: "option" })
    fireEvent.change(select, { target: { value: "a1" } })
    expect(screen.queryByText(/Record Maintenance/)).toBeNull()
  })
})
