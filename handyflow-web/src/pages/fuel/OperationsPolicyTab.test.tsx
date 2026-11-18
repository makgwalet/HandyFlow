import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import OperationsPolicyTab from "./OperationsPolicyTab"

const get = vi.fn(); const put = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const refused = (m: string) => Promise.reject(Object.assign(new Error(m), { response: { data: { message: m } } }))
const conflict = { id: "c1", kind: "SHIFT_CLOSE", reason: "This shift is already closed.", createdAt: "2026-11-18T09:00:00Z", capturedAt: null }

function reply(over: Record<string, () => Promise<unknown>> = {}) {
  get.mockImplementation((u: string) => {
    for (const [k, f] of Object.entries(over)) if (u.includes(k)) return f()
    if (u.includes("settings/arrival-check")) return ok({ freshHours: 12, defaultFreshHours: 12, enforced: false })
    if (u.includes("settings/pod-policy")) return ok({ enforced: false, allowedExceptions: ["UNATTENDED_SITE"] })
    if (u.includes("overdue-policy")) return ok({ graceMinutes: 120, escalationMinutes: 60 })
    if (u.includes("conflicts/policy")) return ok({ maxAgeHours: 72, clockSkewMinutes: 5 })
    if (u.includes("enforcement-readiness")) return ok({ days: 30, delivered: 120, wouldRefuseNoReceiver: 7, wouldRefuseNoArrivalCheck: 95, wouldRefuseNoSignature: 11 })
    if (u.includes("/blockers")) return ok({ control: "ARRIVAL_CHECK", target: "PILOT", allowed: true, blockers: [] })
    if (u.includes("/observations")) return ok([{ deliveryId: "d1", message: "No check has been made on this load.", at: "2026-11-18T09:00:00Z" }])
    if (u.includes("settings/enforcement")) return ok([
      { control: "ARRIVAL_CHECK", state: "MONITOR", pilotDepotIds: [], reason: "Start watching", stateSince: "2026-11-01T08:00:00Z", version: 2, observationsLast7Days: 4, nextStates: ["OFF", "PILOT"] },
      { control: "POD_POLICY", state: "OFF", pilotDepotIds: [], reason: null, stateSince: null, version: 0, observationsLast7Days: 0, nextStates: ["MONITOR"] },
    ])
    if (u.includes("fuel/depots")) return ok([{ id: "dep1", name: "Germiston depot" }, { id: "dep2", name: "Durban depot" }])
    if (u.includes("push/preferences")) return ok({ OVERDUE_DELIVERY: true, DIP_VARIANCE: false })
    if (u.includes("push/recent")) return ok([{ id: "e1", event_type: "OVERDUE_DELIVERY", status: "FAILED", attempts: 3, last_error: "Device not registered", created_at: "2026-11-18T08:00:00Z" }])
    if (u.includes("offline/conflicts")) return ok([conflict])
    return ok(null)
  })
}
function mount() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><OperationsPolicyTab /></QueryClientProvider>)
}

describe("OperationsPolicyTab", () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); post.mockReset(); put.mockResolvedValue({ data: {} }); post.mockResolvedValue({ data: {} }); perms = ["FUEL_READ", "FUEL_CONFIGURATION_MANAGE", "FUEL_MANAGE"]; reply() })
  afterEach(cleanup)

  it("loads every policy", async () => {
    mount()
    expect(((await screen.findByLabelText("Hours a check stays fresh")) as HTMLInputElement).value).toBe("12")
    expect((screen.getByLabelText("Tell the driver after") as HTMLInputElement).value).toBe("120")
    expect((screen.getByLabelText("Longest a saved write may wait") as HTMLInputElement).value).toBe("72")
    expect((screen.getByLabelText("Unattended site") as HTMLInputElement).checked).toBe(true)
    expect(screen.queryByLabelText("Enforce the arrival check")).toBeNull()
    expect(screen.getByLabelText("Arrival check stage").textContent).toBe("Monitor")
  })

  it("saves the arrival check hours without changing the stage", async () => {
    mount()
    fireEvent.change(await screen.findByLabelText("Hours a check stays fresh"), { target: { value: "24" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]!)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/settings/arrival-check", { freshHours: 24, enforced: false }))
  })

  it("will not send hours the server would refuse", async () => {
    mount()
    fireEvent.change(await screen.findByLabelText("Hours a check stays fresh"), { target: { value: "100" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]!)
    expect(await screen.findByText(/between 1 and 72/)).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
  })

  it("shows the server's refusal in words", async () => {
    put.mockImplementation(() => refused("Hours must be between 1 and 72."))
    mount()
    fireEvent.click((await screen.findAllByRole("button", { name: "Save" }))[0]!)
    expect(await screen.findByText("Hours must be between 1 and 72.")).toBeTruthy()
  })

  it("saves the POD policy with the chosen exceptions", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Approved after-hours delivery"))
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]!)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/settings/pod-policy", { enforced: false, allowedExceptions: ["UNATTENDED_SITE", "AFTER_HOURS_APPROVED"] }))
  })

  it("saves the overdue delays and the offline limits", async () => {
    mount()
    fireEvent.change(await screen.findByLabelText("Tell the driver after"), { target: { value: "90" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[2]!)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/push/overdue-policy", { graceMinutes: 90, escalationMinutes: 60 }))
    fireEvent.change(screen.getByLabelText("Longest a saved write may wait"), { target: { value: "48" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[3]!)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/offline/conflicts/policy", { maxAgeHours: 48, clockSkewMinutes: 5 }))
  })

  it("a viewer sees the values but cannot change them", async () => {
    perms = ["FUEL_READ"]
    mount()
    expect(((await screen.findByLabelText("Hours a check stays fresh")) as HTMLInputElement).disabled).toBe(true)
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull()
    expect(screen.queryByText("Recent alerts")).toBeNull()       // needs the configuration permission
    expect(screen.queryByText("Refused offline writes")).toBeNull() // needs a manager or supervisor
  })

  it("lists recent alerts with what went wrong", async () => {
    mount()
    expect(await screen.findByText("Device not registered")).toBeTruthy()
    expect(screen.getAllByText("Overdue delivery").length).toBeGreaterThan(0)
  })

  it("lists refused offline writes and marks one as dealt with", async () => {
    mount()
    expect(await screen.findByText("This shift is already closed.")).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Mark Close shift and cash-up as dealt with"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/offline/conflicts/c1/resolve", {}))
  })

  it("says so when a policy cannot be loaded, rather than showing a blank card", async () => {
    reply({ "overdue-policy": () => refused("Fuel is not enabled") })
    mount()
    expect(await screen.findByText("Fuel is not enabled")).toBeTruthy()
  })

  it("shows each control's stage, what it would have refused, and the look-back", async () => {
    mount()
    expect((await screen.findByLabelText("Arrival check stage")).textContent).toBe("Monitor")
    expect(screen.getByLabelText("Proof of delivery: receiver name and signature stage").textContent).toBe("Off")
    expect(screen.getByText(/4 delivery checks would have been refused in the last 7 days/)).toBeTruthy()
    expect((await screen.findByLabelText("No signature")).textContent).toBe("11")
  })

  it("moves a control up one stage with a reason, choosing pilot depots", async () => {
    mount()
    fireEvent.click(await screen.findByRole("button", { name: "Arrival check: move to Pilot" }))
    fireEvent.click(await screen.findByLabelText("Germiston depot"))
    fireEvent.change(screen.getByLabelText("Arrival check reason"), { target: { value: "Start with the Germiston gate" } })
    fireEvent.click(screen.getByRole("button", { name: "Apply" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/settings/enforcement/ARRIVAL_CHECK", { state: "PILOT", pilotDepotIds: ["dep1"], reason: "Start with the Germiston gate" }))
  })

  it("lists what stands in the way and will not apply while something does", async () => {
    reply({ "/blockers": () => ok({ control: "ARRIVAL_CHECK", target: "PILOT", allowed: false, blockers: ["MONITOR has run since 2026-11-16; it needs at least 7 days before the next stage."] }) })
    mount()
    fireEvent.click(await screen.findByRole("button", { name: "Arrival check: move to Pilot" }))
    expect(await screen.findByText(/needs at least 7 days/)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Apply" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("asks for a reason and for depots before sending", async () => {
    mount()
    fireEvent.click(await screen.findByRole("button", { name: "Arrival check: move to Pilot" }))
    fireEvent.click(screen.getByRole("button", { name: "Apply" }))
    expect(await screen.findByText(/Give a reason/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Arrival check reason"), { target: { value: "Start the pilot" } })
    fireEvent.click(screen.getByRole("button", { name: "Apply" }))
    expect(await screen.findByText(/Choose the depots/)).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
  })

  it("rolls back without being blocked, and shows the server's refusal in words", async () => {
    put.mockImplementation(() => refused("Cannot move ARRIVAL_CHECK to OFF: nope."))
    mount()
    fireEvent.click(await screen.findByRole("button", { name: "Arrival check: go back to Off" }))
    fireEvent.change(screen.getByLabelText("Arrival check reason"), { target: { value: "Drivers need more training" } })
    fireEvent.click(screen.getByRole("button", { name: "Apply" }))
    expect(await screen.findByText(/Cannot move ARRIVAL_CHECK to OFF/)).toBeTruthy()
  })

  it("shows what a control would have refused", async () => {
    mount()
    fireEvent.click((await screen.findAllByRole("button", { name: "Show" }))[0]!)
    expect(await screen.findByText(/No check has been made on this load/)).toBeTruthy()
  })

  it("lets a person switch one of their own alerts off", async () => {
    mount()
    const box = (await screen.findByLabelText("Alert: Dip variance")) as HTMLInputElement
    await waitFor(() => expect(box.checked).toBe(false))
    fireEvent.click(box)
    fireEvent.click(screen.getByRole("button", { name: "Save my choices" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/push/preferences", { enabled: expect.objectContaining({ DIP_VARIANCE: true, OVERDUE_DELIVERY: true }) }))
  })

  it("hides the rollout from people who cannot configure Fuel", async () => {
    perms = ["FUEL_READ"]
    mount()
    await screen.findByLabelText("Hours a check stays fresh")
    expect(screen.queryByText("Rollout of enforcement")).toBeNull()
  })
})
