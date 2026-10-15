import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import MaintenanceTab from "./MaintenanceTab"

afterEach(cleanup)

const asset = (over: Record<string, unknown>) => ({
  id: "a1", name: "Tipper", fleetNumber: "T-01", status: "AVAILABLE", currentHours: 52000, lastServiceHours: 45000,
  serviceIntervalHours: 5000, dueForService: false, meterType: "KILOMETRES", meterUnit: "km", ...over,
})

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
})

const mount = (a: Record<string, unknown>) => {
  get.mockImplementation((url: string) => Promise.resolve(
    url.endsWith("/assets") || url.includes("/assets?") ? { data: { data: { content: [a], last: true } } }
      : { data: { data: { content: [], last: true } } }))
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MaintenanceTab /></QueryClientProvider>)
}

describe("Meter labels", () => {
  it("speaks kilometres for a machine that counts kilometres", async () => {
    mount(asset({}))
    await screen.findByText(/T-01/)
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "a1" } })
    expect(await screen.findByText("Current kilometres")).toBeTruthy()
    expect(screen.getByText("Kilometres Since Service")).toBeTruthy()
    expect(screen.getByText("Every 5000 km")).toBeTruthy()
    expect(screen.queryByText(/hrs/)).toBeNull()
  })

  it("keeps hours for plant", async () => {
    mount(asset({ meterType: "HOURS", meterUnit: "hrs", name: "Excavator" }))
    await screen.findByText(/T-01/)
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "a1" } })
    expect(await screen.findByText("Current hours")).toBeTruthy()
    expect(screen.getByText("Every 5000 hrs")).toBeTruthy()
  })
})
