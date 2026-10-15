import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const put = vi.fn()
const patch = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: (...a: unknown[]) => put(...a),
    patch: (...a: unknown[]) => patch(...a), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import AlertsTab from "./AlertsTab"

afterEach(cleanup)

const alert = (over: Record<string, unknown> = {}) => ({
  id: "al1", alertType: "BREAKDOWN", severity: "CRITICAL", assetId: "a1", refType: "ASSET", refId: "a1",
  title: "Breakdown reported: D9-001", message: "D9 broke down at Mine A.", status: "OPEN", level: 1,
  createdAt: "2026-10-15T08:00:00Z", escalatedAt: null, acknowledgedBy: null, acknowledgedAt: null, resolvedAt: null, resolutionNote: null,
  deliveries: [{ userId: "u2", level: 1, status: "SENT", detail: null, sentAt: "2026-10-15T08:00:01Z" }], ...over,
})
const rule = (over: Record<string, unknown> = {}) => ({
  alertType: "SERVICE_DUE", enabled: true, escalateAfterMinutes: null, initialUserIds: [], escalationUserIds: [],
  usesAdminFallback: true, canBeDisabled: true, ...over,
})
let alerts: unknown[] = []
let rules: unknown[] = []
const page = (content: unknown[]) => ({ data: { data: { content, last: true } } })
const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AlertsTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = []
  alerts = [alert()]
  rules = [rule(), rule({ alertType: "BREAKDOWN", canBeDisabled: false })]
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/alerts/rules") ? { data: { data: rules } }
      : url.includes("/alerts") ? page(alerts)
      : url.includes("assignable-users") ? { data: [{ id: "u2", name: "Thandi Supervisor" }, { id: "u3", name: "Sipho Manager" }] }
      : page([])))
  patch.mockResolvedValue({ data: { data: alert() } })
  put.mockResolvedValue({ data: { data: rule() } })
})

describe("Alerts", () => {
  it("shows what happened, how serious it is and who it was sent to", async () => {
    perms = ["EARTHMOVING_READ"]
    mount()
    expect(await screen.findByText("Breakdown reported: D9-001")).toBeTruthy()
    expect(screen.getByText("CRITICAL")).toBeTruthy()
    expect(await screen.findByText(/Sent to Thandi Supervisor/)).toBeTruthy()
    expect(screen.queryByText("Acknowledge")).toBeNull()
    expect(screen.queryByText(/Who is told/)).toBeNull()
  })

  it("someone with write authority acknowledges", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    fireEvent.click(await screen.findByText("Acknowledge"))
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][0]).toContain("/alerts/al1/acknowledge")
  })

  it("resolving needs a written note", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    mount()
    fireEvent.click(await screen.findByText("Resolve…"))
    const dlg = await screen.findByRole("dialog")
    const go = within(dlg).getByText("Resolve", { selector: "button" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText(/What was done/), { target: { value: "Hose replaced and tested" } })
    fireEvent.click(go)
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][0]).toContain("/alerts/al1/resolve")
    expect(patch.mock.calls[0][1]).toEqual({ note: "Hose replaced and tested" })
  })

  it("marks an escalated alert and a failed send", async () => {
    perms = ["EARTHMOVING_READ"]
    alerts = [alert({ level: 2, deliveries: [{ userId: "u3", level: 2, status: "FAILED", detail: "x", sentAt: "2026-10-15T08:30:00Z" }] })]
    mount()
    expect(await screen.findByText("Escalated")).toBeTruthy()
    expect(await screen.findByText(/Sipho Manager \(failed\)/)).toBeTruthy()
  })
})

describe("Who is told", () => {
  it("is for administrators only", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    mount()
    await screen.findByText("Breakdown reported: D9-001")
    expect(screen.queryByText("Who is told")).toBeNull()
  })

  it("shows the fallback and saves the people chosen with the waiting time", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    fireEvent.click(await screen.findByText("Who is told"))
    expect((await screen.findAllByText(/company administrators \(nobody chosen\)/)).length).toBe(2)
    fireEvent.click(await screen.findByLabelText("Edit Service due"))
    const dlg = await screen.findByRole("dialog")
    const first = within(dlg).getAllByLabelText("Thandi Supervisor")[0]
    fireEvent.click(first)
    const next = within(dlg).getAllByLabelText("Sipho Manager")[1]
    fireEvent.click(next)
    const save = within(dlg).getByText("Save") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(within(dlg).getByLabelText(/Wait this many minutes/), { target: { value: "30" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toContain("/alerts/rules/SERVICE_DUE")
    expect(put.mock.calls[0][1]).toEqual({ enabled: true, escalateAfterMinutes: 30, initialUserIds: ["u2"], escalationUserIds: ["u3"] })
  })

  it("offers no switch-off for breakdown alerts and says so", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    fireEvent.click(await screen.findByText("Who is told"))
    fireEvent.click(await screen.findByLabelText("Edit Breakdown or accident"))
    const dlg = await screen.findByRole("dialog")
    expect(within(dlg).queryByText(/Send these alerts/)).toBeNull()
    expect(within(dlg).getByRole("note").textContent).toContain("cannot be switched off")
  })
})
