import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a),
    patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import MeterDialog from "./MeterDialog"
import type { Asset } from "./shared/types"

afterEach(cleanup)

const asset = (over: Record<string, unknown> = {}) => ({
  id: "a1", name: "Tipper", fleetNumber: "T-01", currentHours: 52000, lastServiceHours: 45000, serviceIntervalHours: 5000,
  meterType: "KILOMETRES", meterUnit: "km", ...over,
}) as unknown as Asset
const rows = [
  { id: "r2", meterType: "KILOMETRES", reading: 52000, previousReading: 5200, source: "MANUAL", reason: null, actorUserId: "u2", recordedAt: "2026-10-15T08:00:00Z" },
  { id: "r1", meterType: "KILOMETRES", reading: 5200, previousReading: null, source: "TYPE_CHANGE", reason: "truck odometer", actorUserId: "u1", recordedAt: "2026-10-01T08:00:00Z" },
]
const mount = (a = asset()) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MeterDialog asset={a} onClose={() => {}} /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ"]
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/meter/readings") ? { data: { data: rows } }
      : url.includes("assignable-users") ? { data: [{ id: "u2", name: "Thandi Supervisor" }] }
      : { data: { data: [] } }))
  post.mockResolvedValue({ data: { data: rows[0] } })
  put.mockResolvedValue({ data: { data: rows[0] } })
})

describe("Meter", () => {
  it("shows the history in the machine's own unit, with who and why", async () => {
    mount()
    expect(await screen.findByText(/Counts kilometres/)).toBeTruthy()
    expect(await screen.findByText(/5,200 → 52,000 km/)).toBeTruthy()
    expect(screen.getByText("truck odometer")).toBeTruthy()
    expect(screen.getByText(/Entered by hand · Thandi Supervisor/)).toBeTruthy()
    expect(screen.queryByText("Correct a reading")).toBeNull()
    expect(screen.queryByText(/Change to/)).toBeNull()
  })

  it("a correction needs a reason before it can be sent", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    mount()
    fireEvent.click(await screen.findByText("Correct a reading"))
    fireEvent.change(document.getElementById("mt-corrected") as HTMLInputElement, { target: { value: "5200" } })
    const send = screen.getByText("Correct reading") as HTMLButtonElement
    expect(send.disabled).toBe(true)
    fireEvent.change(document.getElementById("mt-reason") as HTMLInputElement, { target: { value: "typed an extra zero" } })
    expect(send.disabled).toBe(false)
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toContain("/assets/a1/meter/correction")
    expect(post.mock.calls[0][1]).toEqual({ reading: 5200, reason: "typed an extra zero" })
  })

  it("only an administrator can switch a machine between hours and kilometres", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    fireEvent.click(await screen.findByText("Change to hours"))
    for (const [id, v] of [["mc-current", "900"], ["mc-last", "800"], ["mc-interval", "250"], ["mc-reason", "this is a loader"]])
      fireEvent.change(document.getElementById(id) as HTMLInputElement, { target: { value: v } })
    fireEvent.click(screen.getByText("Change meter"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][1]).toMatchObject({ meterType: "HOURS", currentReading: 900, lastServiceReading: 800, serviceInterval: 250 })
  })

  it("a server refusal stays on screen", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_MAINTAIN"]
    post.mockRejectedValue({ response: { data: { message: "The corrected reading (4000) cannot be below the earlier reading of 4800." } } })
    mount()
    fireEvent.click(await screen.findByText("Correct a reading"))
    fireEvent.change(document.getElementById("mt-corrected") as HTMLInputElement, { target: { value: "4000" } })
    fireEvent.change(document.getElementById("mt-reason") as HTMLInputElement, { target: { value: "typed wrongly" } })
    fireEvent.click(screen.getByText("Correct reading"))
    expect(await screen.findByText(/cannot be below the earlier reading/)).toBeTruthy()
  })
})
