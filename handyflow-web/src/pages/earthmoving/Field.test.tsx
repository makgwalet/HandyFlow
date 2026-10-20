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

import FieldTab from "./FieldTab"
import { loadQueue, QUEUE_KEY } from "./shared/offlineQueue"

afterEach(() => { cleanup(); localStorage.clear() })

let conflicts: unknown[] = []
const setOnline = (v: boolean) => Object.defineProperty(window.navigator, "onLine", { value: v, configurable: true })
const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><FieldTab /></QueryClientProvider>)
}
const record = async (value: string) => {
  await waitFor(() => expect(screen.getByRole("option", { name: /D9-001/ })).toBeTruthy())
  fireEvent.change(screen.getByLabelText("Machine"), { target: { value: "a1" } })
  fireEvent.change(screen.getByLabelText("Meter reading"), { target: { value: value } })
  fireEvent.click(screen.getByText("Save entry"))
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  setOnline(true)
  perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
  conflicts = []
  get.mockImplementation((url: string) => {
    if (url.includes("/field/conflicts")) return Promise.resolve({ data: { data: conflicts } })
    if (url.includes("/assets")) return Promise.resolve({ data: { data: { content: [{ id: "a1", name: "CAT D9", fleetNumber: "D9-001", status: "AVAILABLE" }], totalPages: 1 } } })
    return Promise.resolve({ data: { data: [] } })
  })
})

describe("Field capture", () => {
  it("saves the entry on the phone first and sends it when connected", async () => {
    post.mockImplementation((_u: string, body: { entries: { submissionId: string }[] }) =>
      Promise.resolve({ data: { data: body.entries.map(e => ({ submissionId: e.submissionId, status: "SYNCED", reason: null })) } }))
    mount()
    await record("1010")
    await waitFor(() => expect(post).toHaveBeenCalled())
    const sent = post.mock.calls[0][1].entries[0]
    expect(post.mock.calls[0][0]).toBe("/api/v1/earthmoving/field/sync")
    expect(sent).toMatchObject({ kind: "METER_READING", assetId: "a1", meterValue: 1010 })
    expect(sent.submissionId).toMatch(/^[0-9a-f-]{36}$/)
    await waitFor(() => expect(screen.getByText("Sent")).toBeTruthy())
    expect(loadQueue()[0].state).toBe("SYNCED")
  })

  it("offline: keeps the entry as waiting and sends nothing", async () => {
    setOnline(false)
    mount()
    await record("1010")
    await waitFor(() => expect(screen.getByText("Waiting to send")).toBeTruthy())
    expect(post).not.toHaveBeenCalled()
    expect(screen.getByText(/Saved on this phone/)).toBeTruthy()
    expect(loadQueue()).toHaveLength(1)
  })

  it("sends the waiting entries, with the same id, when the connection returns", async () => {
    setOnline(false)
    post.mockImplementation((_u: string, body: { entries: { submissionId: string }[] }) =>
      Promise.resolve({ data: { data: body.entries.map(e => ({ submissionId: e.submissionId, status: "SYNCED", reason: null })) } }))
    mount()
    await record("1010")
    const id = loadQueue()[0].submissionId
    setOnline(true)
    window.dispatchEvent(new Event("online"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].entries[0].submissionId).toBe(id)
    await waitFor(() => expect(screen.getByText("Sent")).toBeTruthy())
  })

  it("a failed send leaves the entry waiting, not lost", async () => {
    post.mockRejectedValue(new Error("Network Error"))
    mount()
    await record("1010")
    await waitFor(() => expect(screen.getByText(/No connection/)).toBeTruthy())
    expect(screen.getByText("Waiting to send")).toBeTruthy()
    expect(loadQueue()[0].state).toBe("PENDING")
  })

  it("shows a refusal with its reason and a conflict as needing review", async () => {
    post.mockImplementation((_u: string, body: { entries: { submissionId: string }[] }) =>
      Promise.resolve({ data: { data: body.entries.map(e => ({ submissionId: e.submissionId, status: "CONFLICT", reason: "A newer reading was recorded on the server" })) } }))
    mount()
    await record("1010")
    await waitFor(() => expect(screen.getByText("Needs review")).toBeTruthy())
    expect(screen.getByText(/A newer reading was recorded/)).toBeTruthy()
  })

  it("refuses a bad entry before saving it", async () => {
    mount()
    await waitFor(() => expect(screen.getByText("Save entry")).toBeTruthy())
    fireEvent.click(screen.getByText("Save entry"))
    await waitFor(() => expect(screen.getByText(/Choose the machine/)).toBeTruthy())
    expect(localStorage.getItem(QUEUE_KEY)).toBeNull()
    expect(post).not.toHaveBeenCalled()
  })

  it("says plainly that deployment, release and disposal approval are not captured offline", async () => {
    mount()
    await waitFor(() => expect(screen.getByText(/need a live check/)).toBeTruthy())
  })

  it("someone who can only read cannot make entries", async () => {
    perms = ["EARTHMOVING_READ"]
    mount()
    await waitFor(() => expect(screen.getByText(/can see field entries but not make them/)).toBeTruthy())
    expect(screen.queryByText("Save entry")).toBeNull()
  })

  it("lets a maintainer keep the server's reading or apply the phone's", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE", "EARTHMOVING_MAINTAIN"]
    conflicts = [{ id: "c1", submissionId: "s1", kind: "METER_READING", assetId: "a1", assetName: "CAT D9", capturedAt: "2026-10-20T08:00:00Z", receivedAt: "2026-10-20T12:00:00Z", status: "CONFLICT", reason: "A newer reading was recorded", duplicate: false, reviewDecision: null, reviewNote: null, reviewedAt: null }]
    post.mockResolvedValue({ data: { data: {} } })
    mount()
    await waitFor(() => expect(screen.getByText("Apply this entry")).toBeTruthy())
    fireEvent.click(screen.getByText("Keep the server's reading"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/field/conflicts/c1/review", { decision: "KEPT_SERVER", note: null }))
  })
})
