import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import DeviceCompliancePanel from "./DeviceCompliancePanel"

const policy = (o: Record<string, unknown> = {}) => ({ minAppVersion: null, minOsMajor: null, requireScreenLock: false, blockRooted: false, asksAnything: false, updatedAt: null, ...o })
const report = (o: Record<string, unknown> = {}) => ({ data: { data: { generatedAt: "2026-11-23T08:00:00Z", policy: policy(), activeDevices: 3, compliant: 3, nonCompliant: 0, unknown: 0, items: [], ...o } } })
const item = (o: Record<string, unknown> = {}) => ({ deviceId: "d1", deviceName: "Gate phone", deviceType: "SHARED_SITE_DEVICE", guardId: null, guardName: null, status: "NON_COMPLIANT", violations: ["APP_TOO_OLD"], missing: [], appVersion: "2.0", osVersion: "12", screenLockOn: true, rooted: false, postureReportedAt: null, ...o })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DeviceCompliancePanel /></QueryClientProvider>)

describe("DeviceCompliancePanel", () => {
  beforeEach(() => { api.get.mockReset(); api.put.mockReset() })
  afterEach(cleanup)

  it("says plainly when no requirements are set", async () => {
    api.get.mockResolvedValue(report())
    show()
    expect(await screen.findByText("No requirements set yet, so every phone counts as compliant.")).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith("/api/v1/security/devices/compliance")
    expect(screen.getByText(/Report only: nothing is blocked/)).toBeTruthy()
  })

  it("lists phones that fall short and phones that have not reported enough", async () => {
    api.get.mockResolvedValue(report({
      policy: policy({ asksAnything: true, minAppVersion: "2.4", requireScreenLock: true }), compliant: 1, nonCompliant: 1, unknown: 1,
      items: [
        item(),
        item({ deviceId: "d2", deviceName: "Spare", status: "UNKNOWN", violations: [], missing: ["SCREEN_LOCK"], appVersion: null, osVersion: null }),
      ],
    }))
    show()
    expect(await screen.findByText("Gate phone")).toBeTruthy()
    expect(screen.getByText("App needs updating")).toBeTruthy()
    expect(screen.getByText("Not reported: screen lock")).toBeTruthy()
    expect(screen.getByText("1 of 3 active phones meet the policy, 1 fall short, 1 not reported enough to tell.")).toBeTruthy()
    expect((screen.getByLabelText("Lowest app version") as HTMLInputElement).value).toBe("2.4")
    expect((screen.getByLabelText("Screen lock required") as HTMLInputElement).checked).toBe(true)
  })

  it("saves a changed policy and reloads", async () => {
    api.get.mockResolvedValue(report())
    api.put.mockImplementation(() => { api.get.mockResolvedValue(report({ policy: policy({ asksAnything: true, minAppVersion: "2.4", minOsMajor: 12, updatedAt: "2026-11-23T08:05:00Z" }) })); return Promise.resolve({ data: { data: {} } }) })
    show()
    const save = (await screen.findByText("Save policy")) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Lowest app version"), { target: { value: " 2.4 " } })
    fireEvent.change(screen.getByLabelText("Lowest Android or iOS version"), { target: { value: "12" } })
    fireEvent.click(screen.getByLabelText("No rooted or jailbroken phones"))
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/api/v1/security/devices/compliance-policy", { minAppVersion: "2.4", minOsMajor: 12, requireScreenLock: false, blockRooted: true }))
    await waitFor(() => expect((screen.getByLabelText("Lowest app version") as HTMLInputElement).value).toBe("2.4"))
    expect((screen.getByText("Save policy") as HTMLButtonElement).disabled).toBe(true)
  })

  it("refuses a bad version before asking the server", async () => {
    api.get.mockResolvedValue(report())
    show()
    fireEvent.change(await screen.findByLabelText("Lowest app version"), { target: { value: "2.x" } })
    expect(screen.getByRole("alert").textContent).toBe("The lowest app version must be numbers separated by dots, like 2.4 or 3.10.1.")
    expect((screen.getByText("Save policy") as HTMLButtonElement).disabled).toBe(true)
    expect(api.put).not.toHaveBeenCalled()
  })

  it("shows the server's reason when saving fails and keeps what was typed", async () => {
    api.get.mockResolvedValue(report())
    api.put.mockRejectedValue({ response: { status: 403, data: { message: "Only someone with the whole company can do this." } } })
    show()
    fireEvent.change(await screen.findByLabelText("Lowest Android or iOS version"), { target: { value: "13" } })
    fireEvent.click(screen.getByText("Save policy"))
    await waitFor(() => expect(screen.getAllByRole("alert").length).toBeGreaterThan(0))
    expect((screen.getByLabelText("Lowest Android or iOS version") as HTMLInputElement).value).toBe("13")
  })

  it("explains when the list cannot be loaded", async () => {
    api.get.mockRejectedValue({ response: { status: 403, data: { code: "SITE_SCOPE_LIMITED", message: "Your access is limited to some sites." } } })
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
    expect(screen.queryByText("Save policy")).toBeNull()
  })
})
