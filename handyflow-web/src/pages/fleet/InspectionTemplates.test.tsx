import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import InspectionTemplates from "./InspectionTemplates"
import { emptyTemplate, formFrom, moveItem, templateBody, templateProblem } from "./inspectionTemplates.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), put: (...a: Args) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const server = { id: "t1", name: "Daily vehicle inspection", inspectionType: "DAILY", description: null, intervalDays: 1, appliesToVehicleType: "TRUCK", signatureRequired: true, active: true,
  items: [{ section: "Lights", label: "Headlights", required: true, photoRequiredOnFail: true, severity: "HIGH", sortOrder: 2 }, { section: "Tyres", label: "Tread", required: true, photoRequiredOnFail: false, severity: "MEDIUM", sortOrder: 1 }] }
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><InspectionTemplates onClose={() => {}} /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) => url.endsWith("/t1") ? ok(server) : ok([server]))
  post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("template rules", () => {
  it("asks for a name, valid repeat days and complete items", () => {
    const f = emptyTemplate()
    expect(templateProblem(f)).toBe("Give the inspection a name.")
    f.name = "X"; expect(templateProblem(f)).toBe("Add at least one checklist item.")
    f.items[0] = { ...f.items[0], section: "Tyres" }; expect(templateProblem(f)).toBe("Every checklist item needs a section and a description.")
    f.items[0].label = "Tread"; expect(templateProblem(f)).toBeNull()
    f.intervalDays = "0"; expect(templateProblem(f)).toMatch(/Repeat every/)
  })
  it("loads items in their saved order and builds the request", () => {
    const f = formFrom(server)
    expect(f.items.map(i => i.label)).toEqual(["Tread", "Headlights"])
    expect(templateBody(f)).toMatchObject({ name: "Daily vehicle inspection", intervalDays: 1, appliesToVehicleType: "TRUCK", signatureRequired: true })
  })
  it("moves an item up or down and ignores moves off the ends", () => {
    expect(moveItem([1, 2, 3], 2, 1)).toEqual([1, 3, 2])
    expect(moveItem([1, 2, 3], 0, -1)).toEqual([1, 2, 3])
  })
})

describe("InspectionTemplates", () => {
  it("lists templates", async () => {
    show()
    expect(await screen.findByText("Daily vehicle inspection")).toBeTruthy()
    expect(screen.getByText("Every 1 days")).toBeTruthy()
  })
  it("creates a template", async () => {
    show()
    await screen.findByText("Daily vehicle inspection")
    fireEvent.click(screen.getByText("New inspection"))
    fireEvent.change(screen.getByLabelText("Template name"), { target: { value: "Pre-trip check" } })
    fireEvent.change(screen.getByLabelText("Section 1"), { target: { value: "Tyres" } })
    fireEvent.change(screen.getByLabelText("Item 1"), { target: { value: "Tread depth" } })
    fireEvent.click(screen.getByText("Save inspection"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/inspection-templates", expect.objectContaining({ name: "Pre-trip check", items: [expect.objectContaining({ section: "Tyres", label: "Tread depth" })] })))
  })
  it("edits an existing template with a full replace", async () => {
    show()
    fireEvent.click(await screen.findByText("Edit"))
    await screen.findByDisplayValue("Daily vehicle inspection")
    fireEvent.change(screen.getByLabelText("Template name"), { target: { value: "Daily check" } })
    fireEvent.click(screen.getByText("Save inspection"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/inspection-templates/t1", expect.objectContaining({ name: "Daily check" })))
  })
  it("read-only users can see templates but not change them", async () => {
    perms = new Set(["FLEET_READ"])
    show()
    await screen.findByText("Daily vehicle inspection")
    expect(screen.queryByText("New inspection")).toBeNull()
    expect(screen.queryByText("Edit")).toBeNull()
  })
})
