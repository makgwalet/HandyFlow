import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import InspectionsTab from "./InspectionsTab"
import { bySection, completionProblems, startBody, startProblem, type Result } from "./inspections.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), put: (...a: Args) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const res = (o: Partial<Result>): Result => ({ id: "r1", section: "Tyres", label: "Front left", sortOrder: 1, required: true, photoRequiredOnFail: false, severity: "MEDIUM", outcome: null, comment: null, photoIds: [], ...o })
const summary = { id: "i1", registration: "ABC123MP", templateName: "Daily vehicle inspection", inspectorName: "Sam", status: "IN_PROGRESS", verdict: null, failedCount: 0, startedAt: "2026-10-20T06:00:00Z" }
let results: Result[]
let signed = false
const detail = (status = "IN_PROGRESS", verdict: string | null = null) => ({ summary: { ...summary, status, verdict }, signatureRequired: true, signatureRecorded: signed, signerName: signed ? "Sam" : null, results, photos: [] })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><InspectionsTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); get.mockReset(); post.mockReset(); put.mockReset(); signed = false
  results = [res({ id: "r1", label: "Front left" }), res({ id: "r2", label: "Headlights", section: "Lights", sortOrder: 2, photoRequiredOnFail: true }), res({ id: "r3", label: "Radio", section: "Body", sortOrder: 3, required: false })]
  get.mockImplementation((url: string) => url.includes("/inspections/i1") ? ok(detail())
    : url.includes("/inspection-templates") ? ok([{ id: "t1", name: "Daily vehicle inspection", active: true }])
    : url.includes("/vehicles") ? ok({ content: [{ id: "v1", registration: "ABC123MP" }] })
    : ok({ content: [summary] }))
  post.mockResolvedValue({ data: { data: detail() } }); put.mockResolvedValue({ data: { data: detail() } })
})
afterEach(cleanup)

describe("inspection rules", () => {
  it("groups items by section in set-up order", () => {
    expect(bySection(results).map(g => g.section)).toEqual(["Tyres", "Lights", "Body"])
  })
  it("lists everything that blocks completing", () => {
    const d = { signatureRequired: true, signatureRecorded: false, results }
    expect(completionProblems(d)).toEqual(["2 required items are not answered yet.", "The inspector's signature is needed."])
    const done = { signatureRequired: true, signatureRecorded: true, results: [res({ outcome: "PASS" }), res({ id: "r2", label: "Headlights", outcome: "FAIL", photoRequiredOnFail: true })] }
    expect(completionProblems(done)).toEqual(["A photo is needed for: Headlights."])
    expect(completionProblems({ signatureRequired: false, signatureRecorded: false, results: [res({ outcome: "N/A" })] })).toEqual([])
  })
  it("checks and builds the start request", () => {
    expect(startProblem({ vehicleId: "", templateId: "", odometer: "", inspectorName: "" })).toMatch(/vehicle/)
    expect(startProblem({ vehicleId: "v", templateId: "", odometer: "", inspectorName: "" })).toMatch(/inspection/)
    expect(startProblem({ vehicleId: "v", templateId: "t", odometer: "-5", inspectorName: "" })).toMatch(/odometer/)
    expect(startBody({ vehicleId: "v", templateId: "t", odometer: "", inspectorName: " Sam " })).toEqual({ templateId: "t", odometer: null, inspectorName: "Sam" })
  })
})

describe("InspectionsTab", () => {
  it("lists inspections and opens one to continue", async () => {
    show(); await screen.findByText("Daily vehicle inspection"); fireEvent.click(screen.getByText("Continue"))
    expect(await screen.findByText("Front left")).toBeTruthy(); expect(screen.getByText("0 of 3 answered · 0 failed")).toBeTruthy()
  })
  it("saves an answer with its comment", async () => {
    show(); fireEvent.click(await screen.findByText("Continue")); await screen.findByText("Front left")
    fireEvent.change(screen.getByLabelText("Comment on Front left"), { target: { value: "Worn" } })
    fireEvent.click(within("Front left", "Fail"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/inspections/i1/answers", { answers: [{ resultId: "r1", outcome: "FAIL", comment: "Worn" }] }))
  })
  it("keeps Complete disabled until the checklist and signature are done, then completes", async () => {
    results = results.map(r => ({ ...r, outcome: r.required ? "PASS" : null })); signed = true
    show(); fireEvent.click(await screen.findByText("Continue")); await screen.findByText("Front left")
    const btn = screen.getByText("Complete inspection") as HTMLButtonElement
    expect(btn.disabled).toBe(false); fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/inspections/i1/complete", {}))
  })
  it("blocks Complete and says why", async () => {
    show(); fireEvent.click(await screen.findByText("Continue")); await screen.findByText("Front left")
    expect((screen.getByText("Complete inspection") as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText("2 required items are not answered yet.")).toBeTruthy()
  })
  it("shows a finished failed inspection read-only with the issue note", async () => {
    results = results.map(r => ({ ...r, outcome: "FAIL" })); signed = true
    get.mockImplementation((url: string) => url.includes("/inspections/i1") ? ok(detail("COMPLETED", "FAIL")) : ok({ content: [{ ...summary, status: "COMPLETED", verdict: "FAIL", failedCount: 3 }] }))
    show(); await screen.findByText("Failed (3)"); fireEvent.click(screen.getByText("View"))
    expect(await screen.findByText(/maintenance issue was logged/)).toBeTruthy(); expect(screen.queryByText("Complete inspection")).toBeNull()
  })
  it("offers to create the starter template when there are none", async () => {
    get.mockImplementation((url: string) => url.includes("/inspection-templates") ? ok([]) : ok({ content: [] }))
    show(); fireEvent.click(screen.getByText("Start an inspection"))
    fireEvent.click(await screen.findByText("Create the daily vehicle inspection"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/inspection-templates/starter"))
  })
  it("starts an inspection and opens it", async () => {
    show(); fireEvent.click(await screen.findByText("Start an inspection")); await screen.findByText("ABC123MP", { selector: "option" })
    fireEvent.change(screen.getByLabelText("Vehicle"), { target: { value: "v1" } }); fireEvent.change(screen.getByLabelText("Inspection"), { target: { value: "t1" } })
    fireEvent.click(screen.getByText("Start"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/inspections", { templateId: "t1", odometer: null, inspectorName: null }))
    expect(await screen.findByText("Front left")).toBeTruthy()
  })
  it("read-only users cannot start or answer", async () => {
    perms = new Set(["FLEET_READ"]); show(); expect(screen.queryByText("Start an inspection")).toBeNull()
    fireEvent.click(await screen.findByText("View")); await screen.findByText("Front left")
    expect(screen.queryByText("Complete inspection")).toBeNull(); expect(screen.queryByLabelText("Comment on Front left")).toBeNull()
  })
})

/** The Pass/Fail/N/A button inside one checklist row. */
function within(label: string, name: string) {
  const row = screen.getByLabelText(label, { selector: "li" })
  return Array.from(row.querySelectorAll("button")).find(b => b.textContent === name) as HTMLElement
}
