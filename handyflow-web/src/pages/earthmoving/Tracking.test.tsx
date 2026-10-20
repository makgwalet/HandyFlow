import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "me", permissions: perms } }),
}))

import TrackingTab from "./TrackingTab"

afterEach(cleanup)

const none = { freshness: "NEVER", lastSeen: null, positionAt: null, latitude: null, longitude: null, meterAt: null, meterValue: null, meterUnit: null, providerCode: null }
const row = (over: Record<string, unknown> = {}) => ({
  assetId: "a1", assetName: "CAT D9", fleetNumber: "D9-001", meterUnit: "HOURS", machineMeter: 1000, activeDevices: 0,
  provider: none, manual: none, providerMeterDifference: null, ...over,
})
let rows: unknown[] = []
let devices: unknown[] = []

const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><TrackingTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ"]
  rows = [row()]
  devices = []
  get.mockImplementation((url: string) => {
    if (url.includes("/telematics/status")) return Promise.resolve({ data: { data: rows } })
    if (url.includes("/telematics/devices")) return Promise.resolve({ data: { data: devices } })
    if (url.includes("/telematics/providers")) return Promise.resolve({ data: { data: [] } })
    if (url.includes("/assets")) return Promise.resolve({ data: { data: { content: [{ id: "a1", name: "CAT D9", fleetNumber: "D9-001", status: "AVAILABLE" }], totalPages: 1 } } })
    return Promise.resolve({ data: { data: [] } })
  })
  post.mockResolvedValue({ data: { data: {} } })
})

describe("Tracking", () => {
  it("says never seen, and last seen rather than live", async () => {
    mount()
    await waitFor(() => expect(screen.getByText("CAT D9", { exact: false })).toBeTruthy())
    expect(screen.getAllByText("Never seen").length).toBe(2)
    expect(screen.getByText(/not where it is now/)).toBeTruthy()
  })

  it("shows the tracker and typed-in readings apart, with the meter difference", async () => {
    rows = [row({
      provider: { ...none, freshness: "FRESH", lastSeen: "2026-10-20T10:00:00Z", positionAt: "2026-10-20T10:00:00Z", latitude: -26.2041, longitude: 28.0473, meterAt: "2026-10-20T10:00:00Z", meterValue: 1012.5, meterUnit: "HOURS", providerCode: "ACME" },
      manual: { ...none, freshness: "OFFLINE", lastSeen: "2026-10-17T10:00:00Z", meterAt: "2026-10-17T10:00:00Z", meterValue: 990, meterUnit: "HOURS" },
      providerMeterDifference: 12.5, activeDevices: 1,
    })]
    mount()
    await waitFor(() => expect(screen.getByText("Seen in the last 30 minutes")).toBeTruthy())
    expect(screen.getByText("Not seen for over a day")).toBeTruthy()
    expect(screen.getByText(/Position -26.20410, 28.04730/)).toBeTruthy()
    expect(screen.getByText(/Tracker meter reads 12.5 hrs above the machine's meter/)).toBeTruthy()
  })

  it("hides actions from someone who can only read", async () => {
    mount()
    await waitFor(() => expect(screen.getByText("CAT D9", { exact: false })).toBeTruthy())
    expect(screen.queryByText("Record a reading")).toBeNull()
    expect(screen.queryByText("Map a device")).toBeNull()
  })

  it("records a reading typed in by hand, in the machine's own unit", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    await waitFor(() => expect(screen.getByText("Record a reading")).toBeTruthy())
    fireEvent.click(screen.getByText("Record a reading"))
    fireEvent.click(screen.getByRole("button", { name: "Save reading" }))
    await waitFor(() => expect(screen.getByText(/Give a position, a meter value, or both/)).toBeTruthy())
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Meter (hours)"), { target: { value: "1005" } })
    fireEvent.click(screen.getByRole("button", { name: "Save reading" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/telematics/assets/a1/readings",
      { latitude: null, longitude: null, meterValue: 1005, meterUnit: "HOURS", note: null }))
  })

  it("refuses half a position", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    await waitFor(() => expect(screen.getByText("Record a reading")).toBeTruthy())
    fireEvent.click(screen.getByText("Record a reading"))
    fireEvent.change(screen.getByLabelText("Latitude"), { target: { value: "-26.2" } })
    fireEvent.click(screen.getByRole("button", { name: "Save reading" }))
    await waitFor(() => expect(screen.getByText(/needs both latitude and longitude/)).toBeTruthy())
    expect(post).not.toHaveBeenCalled()
  })

  it("lets an administrator map a device and warns that no provider is connected", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    await waitFor(() => expect(screen.getByText("Map a device")).toBeTruthy())
    fireEvent.click(screen.getByText("Map a device"))
    await waitFor(() => expect(screen.getByText(/No tracking provider is connected yet/)).toBeTruthy())
    await waitFor(() => expect(screen.getByRole("option", { name: /D9-001/ })).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Machine"), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Provider code"), { target: { value: "ACME" } })
    fireEvent.change(screen.getByLabelText("Device id"), { target: { value: "DEV-1" } })
    fireEvent.click(screen.getByRole("button", { name: "Map device" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/telematics/devices",
      { assetId: "a1", providerCode: "ACME", externalId: "DEV-1", label: null }))
  })

  it("needs a reason to unmap a device", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    devices = [{ id: "d1", assetId: "a1", assetName: "CAT D9", fleetNumber: "D9-001", providerCode: "ACME", externalId: "DEV-1", label: null, active: true, mappedAt: "2026-10-01T08:00:00Z", unmappedAt: null, unmapReason: null }]
    mount()
    await waitFor(() => expect(screen.getByText("Unmap")).toBeTruthy())
    fireEvent.click(screen.getByText("Unmap"))
    fireEvent.click(screen.getAllByRole("button", { name: "Unmap" }).slice(-1)[0])
    await waitFor(() => expect(screen.getByText(/reason of at least 5 characters/)).toBeTruthy())
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Unit removed" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Unmap" }).slice(-1)[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/telematics/devices/d1/unmap", { reason: "Unit removed" }))
  })
})
