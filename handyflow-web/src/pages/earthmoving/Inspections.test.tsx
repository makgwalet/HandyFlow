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

const q = (text: string, over: Record<string, unknown> = {}) => ({
  id: null, section: null, text, answerType: "PASS_FAIL", required: true, builtIn: true, min: null, max: null, unit: null, failOutcome: "DEFECT", ...over,
})
const active = (questions: unknown[], over: Record<string, unknown> = {}) => ({
  versionId: null, versionNo: null, inspectionType: "PRE_USE", requiresSignoff: false, effectiveFrom: null, source: "BUILT_IN", questions, ...over,
})
const CHECK = active([q("Tyres or tracks"), q("Brakes")])
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
    url.includes("/checklists/active") ? { data: { data: CHECK } }
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
    await waitFor(() => expect(save.disabled).toBe(false)) // the return checklist loads first
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].inspectionType).toBe("RETURN")
    expect(post.mock.calls[0][1].meterReading).toBe(1250)
  })

  it("asks the company's own questions: a number out of range needs a note, a sign-off is required, and the version is sent", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    const own = active([q("Brakes"), q("Hydraulic pressure", { builtIn: false, answerType: "NUMERIC", min: 180, max: 220, unit: "bar", failOutcome: "UNSAFE", section: "Hydraulics" }),
      q("Operator remarks", { builtIn: false, answerType: "TEXT", required: false })], { versionId: "v3", versionNo: 3, source: "COMPANY", requiresSignoff: true })
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/checklists/active") ? { data: { data: own } }
        : url.includes("/inspections") ? { data: { data: [] } }
        : url.includes("assignable-users") ? { data: [] }
        : { data: { data: { content: [{ id: "a1", name: "Tipper", fleetNumber: "T-01", status: "AVAILABLE" }], last: true } } }))
    mount()
    fireEvent.click(await screen.findByText("New inspection"))
    await screen.findByLabelText(/Hydraulic pressure/)
    await waitFor(() => expect(screen.getAllByRole("option", { name: /T-01/, hidden: true }).length).toBe(2))
    fireEvent.change(screen.getByLabelText(/Machine \*/), { target: { value: "a1" } })
    expect(screen.getByText(/Company checklist, version 3/)).toBeTruthy()
    const save = screen.getByText("Save inspection") as HTMLButtonElement
    expect(save.disabled).toBe(true) // the required number is missing
    fireEvent.change(screen.getByLabelText(/Hydraulic pressure/), { target: { value: "250" } })
    expect(save.disabled).toBe(true) // out of range: needs what was seen
    expect(screen.getByRole("alert").textContent).toMatch(/out of service/)
    fireEvent.change(screen.getByLabelText("What was seen: Hydraulic pressure"), { target: { value: "Gauge reads high when warm" } })
    expect(save.disabled).toBe(true) // sign-off still missing
    fireEvent.change(screen.getByLabelText(/Sign-off/), { target: { value: "Thabo M" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalled())
    const body = post.mock.calls[0][1]
    expect(body.templateVersionId).toBe("v3")
    expect(body.signOffName).toBe("Thabo M")
    expect(body.items).toEqual([
      { item: "Brakes", outcome: "OK", comment: null },
      { item: "Hydraulic pressure", comment: "Gauge reads high when warm", valueNumber: 250 },
    ])
  })

  it("says so when a kind of inspection has no checklist yet", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/checklists/active") ? { data: { data: null } }
        : url.includes("/inspections") ? { data: { data: [] } }
        : url.includes("assignable-users") ? { data: [] }
        : { data: { data: { content: [], last: true } } }))
    mount()
    fireEvent.click(await screen.findByText("New inspection"))
    fireEvent.change(await screen.findByLabelText("Type of inspection"), { target: { value: "MAINTENANCE" } })
    expect(await screen.findByText(/There is no maintenance checklist yet/)).toBeTruthy()
    expect((screen.getByText("Save inspection") as HTMLButtonElement).disabled).toBe(true)
  })

  it("shows what kind of inspection each one was", async () => {
    perms = ["EARTHMOVING_READ"]
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/checklists/active") ? { data: { data: CHECK } }
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
