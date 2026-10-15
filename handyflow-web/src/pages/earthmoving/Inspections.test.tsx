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
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import InspectionsTab from "./InspectionsTab"

afterEach(cleanup)

const CHECK = ["Tyres or tracks", "Brakes"]
const inspection = (over: Record<string, unknown> = {}) => ({
  id: "i1", assetId: "a1", machine: "T-01 — Tipper", inspectorUserId: "u2", performedAt: "2026-10-15T06:00:00Z", meterReading: null,
  result: "DEFECTS", notes: null, items: [{ item: "Brakes", outcome: "DEFECT", comment: "Soft pedal", incidentId: "x" }], inspectionType: "PRE_USE", ...over,
})
const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><InspectionsTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = []
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/inspections/checklist") ? { data: { data: CHECK } }
      : url.includes("/inspections") ? { data: { data: [inspection()] } }
      : url.includes("assignable-users") ? { data: [{ id: "u2", name: "Sipho Operator" }] }
      : { data: { data: { content: [{ id: "a1", name: "Tipper", fleetNumber: "T-01", status: "AVAILABLE" }], last: true } } }))
  post.mockResolvedValue({ data: { data: inspection({ result: "UNSAFE", machine: "T-01 — Tipper" }) } })
})

describe("Inspections", () => {
  it("lists inspections with their findings and gives a reader no button to record one", async () => {
    perms = ["EARTHMOVING_READ"]
    mount()
    expect(await screen.findByText("Passed with defects")).toBeTruthy()
    expect(screen.getByText(/Soft pedal/)).toBeTruthy()
    expect(await screen.findByText(/Sipho Operator/)).toBeTruthy()
    expect(screen.queryByText("New inspection")).toBeNull()
  })

  it("will not save until every finding has a note, and warns before an unsafe finding is saved", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    fireEvent.click(await screen.findByText("New inspection"))
    await screen.findByLabelText("Brakes: Unsafe")
    await waitFor(() => expect(screen.getAllByRole("option", { name: /T-01/, hidden: true }).length).toBe(2))
    fireEvent.change(screen.getByLabelText(/Machine \*/), { target: { value: "a1" } })
    const save = screen.getByText("Save inspection") as HTMLButtonElement
    expect(save.disabled).toBe(false)
    fireEvent.click(screen.getByLabelText("Brakes: Unsafe"))
    expect(save.disabled).toBe(true)
    expect(screen.getByRole("alert").textContent).toMatch(/out of service/)
    fireEvent.change(screen.getByLabelText("What was seen: Brakes"), { target: { value: "Pedal to the floor" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalled())
    const body = post.mock.calls[0][1]
    expect(body.assetId).toBe("a1")
    expect(body.items).toEqual([
      { item: "Tyres or tracks", outcome: "OK", comment: null },
      { item: "Brakes", outcome: "UNSAFE", comment: "Pedal to the floor" },
    ])
    expect(await screen.findByText(/taken out of service/)).toBeTruthy()
  })

  it("asks for the meter on a return inspection and sends its type", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    fireEvent.click(await screen.findByText("New inspection"))
    await screen.findByLabelText("Brakes: Unsafe")
    await waitFor(() => expect(screen.getAllByRole("option", { name: /T-01/, hidden: true }).length).toBe(2))
    fireEvent.change(screen.getByLabelText(/Machine \*/), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Type of inspection"), { target: { value: "RETURN" } })
    const save = screen.getByText("Save inspection") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Meter reading/), { target: { value: "1250" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].inspectionType).toBe("RETURN")
    expect(post.mock.calls[0][1].meterReading).toBe(1250)
  })

  it("shows what kind of inspection each one was", async () => {
    perms = ["EARTHMOVING_READ"]
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/inspections/checklist") ? { data: { data: CHECK } }
        : url.includes("/inspections") ? { data: { data: [inspection({ inspectionType: "HANDOVER" })] } }
        : url.includes("assignable-users") ? { data: [] }
        : { data: { data: { content: [], last: true } } }))
    mount()
    expect(await screen.findByText(/Handover to customer/)).toBeTruthy()
  })
})

import { inspectionAgo } from "./shared/format"

describe("inspectionAgo", () => {
  const now = new Date("2026-10-15T12:00:00Z")
  it("says how long ago in plain words", () => {
    expect(inspectionAgo(null, now)).toBe("Never inspected")
    expect(inspectionAgo("2026-10-15T06:00:00Z", now)).toBe("Inspected today")
    expect(inspectionAgo("2026-10-14T06:00:00Z", now)).toBe("Inspected yesterday")
    expect(inspectionAgo("2026-10-10T12:00:00Z", now)).toBe("Inspected 5 days ago")
  })
})
