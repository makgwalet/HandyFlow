import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

import FleetHealthPanel from "./FleetHealthPanel"

function show() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><FleetHealthPanel /></QueryClientProvider>)
}

describe("FleetHealthPanel", () => {
  beforeEach(() => { get.mockReset() })
  afterEach(() => cleanup())

  it("lists phones with their problems", async () => {
    get.mockResolvedValue({ data: { data: { generatedAt: "2026-11-21T10:00:00Z", activeDevices: 4, withIssues: 1, critical: 1, items: [
      { deviceId: "d1", deviceName: "Gate phone", deviceType: "SHARED_SITE_DEVICE", siteId: null, guardId: null, guardName: null,
        lastSeenAt: null, batteryPct: 8, appVersion: "2.0.1", onSession: true, critical: true, issues: ["OFFLINE", "CRITICAL_BATTERY"] }] } } })
    show()
    expect(await screen.findByText("Gate phone")).toBeTruthy()
    expect(screen.getByText("Silent while on shift")).toBeTruthy()
    expect(screen.getByText("Battery almost flat")).toBeTruthy()
    expect(screen.getByText("8%")).toBeTruthy()
    expect(screen.getByText("1 phone needs attention, 1 urgent.")).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/security/devices/fleet-health")
  })

  it("says all is well when nothing needs attention", async () => {
    get.mockResolvedValue({ data: { data: { generatedAt: "x", activeDevices: 12, withIssues: 0, critical: 0, items: [] } } })
    show()
    expect(await screen.findByText("All 12 active phones are reporting normally.")).toBeTruthy()
    expect(screen.queryByRole("table")).toBeNull()
  })

  it("shows an error instead of claiming all is well", async () => {
    get.mockImplementation(() => Promise.reject(new Error("boom")))
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
    expect(screen.queryByText(/reporting normally/)).toBeNull()
  })
})
