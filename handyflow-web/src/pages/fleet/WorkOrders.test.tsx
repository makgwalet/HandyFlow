import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WorkOrdersTab from "./WorkOrdersTab"
import { vehicleFromQuery, canEditLines, completeProblem, emptyComplete, emptyLine, emptyOrder, emptyQuote, lineProblem, orderBody, orderProblem, quoteProblem, stepsFor, techProblem, emptyTech, varianceText } from "./workOrders.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), put: (...a: Args) => put(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const order = (o: object = {}) => ({ id: "w1", referenceNumber: "WO-2026-0007", vehicleId: "v1", registration: "ABC123MP", type: "SERVICE", title: "90 000 km service", priority: "NORMAL", status: "OPEN", assignedTechnicianName: "Sipho", quotedAmount: null, actualTotal: 0, scheduledFor: null, ...o })
let status = "OPEN"
const detail = () => ({ summary: order({ status, quotedAmount: 4000, actualTotal: 4500 }), description: null, rejectionReason: null, holdReason: null, lines: [{ id: "l1", lineType: "PART", description: "Oil filter", quantity: 1, unitCost: 250, lineTotal: 250, technicianName: null }],
  labourTotal: 0, partsTotal: 250, subletTotal: 0, otherTotal: 0, varianceToQuote: 500, overQuote: true, downtimeHours: 6.5, downtimeOngoing: true })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WorkOrdersTab /></QueryClientProvider>)
const set = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE", "FLEET_ADMIN"]); status = "OPEN"; for (const m of [get, post, put, del]) m.mockReset()
  post.mockResolvedValue({ data: { data: detail() } }); put.mockResolvedValue({ data: { data: detail() } }); del.mockResolvedValue({ data: { data: detail() } })
  get.mockImplementation((url: string) => url.includes("/work-orders/w1") ? ok(detail()) : url.includes("/work-orders") ? ok({ content: [order({ status })] })
    : url.includes("/technicians") ? ok([{ id: "t1", name: "Sipho", phone: "082", hourlyRate: 350, active: true }]) : url.includes("/vehicles") ? ok({ content: [{ id: "v1", registration: "ABC123MP" }] }) : ok([]))
})
afterEach(cleanup)

describe("work order rules", () => {
  it("offers the next steps for each status, with approval for admins only", () => {
    expect(stepsFor("OPEN").map(s => s.step)).toEqual(["QUOTE", "START", "CANCEL"])
    expect(stepsFor("QUOTED").filter(s => s.admin).map(s => s.step)).toEqual(["APPROVE", "REJECT"])
    expect(stepsFor("IN_PROGRESS").map(s => s.step)).toEqual(["HOLD", "COMPLETE", "CANCEL"])
    expect(stepsFor("COMPLETED")).toEqual([]); expect(canEditLines("COMPLETED")).toBe(false); expect(canEditLines("ON_HOLD")).toBe(true)
  })
  it("checks the forms", () => {
    expect(orderProblem(emptyOrder())).toMatch(/vehicle/); expect(orderProblem({ ...emptyOrder(), vehicleId: "v" })).toMatch(/work/)
    expect(orderBody({ ...emptyOrder(), vehicleId: "v", title: " Brakes " })).toMatchObject({ title: "Brakes", technicianId: null, supplier: null })
    expect(quoteProblem(emptyQuote())).toMatch(/amount/)
    expect(lineProblem(emptyLine())).toMatch(/Describe/)
    expect(lineProblem({ ...emptyLine(), description: "Filter" })).toBe("Enter the cost.")
    expect(lineProblem({ ...emptyLine(), lineType: "LABOUR", description: "Fit" })).toBe("Choose the technician or enter a rate.")
    expect(lineProblem({ ...emptyLine(), lineType: "LABOUR", description: "Fit", technicianId: "t1" })).toBeNull()
    expect(completeProblem("SERVICE", emptyComplete())).toMatch(/odometer/); expect(completeProblem("REPAIR", emptyComplete())).toBeNull()
    expect(techProblem(emptyTech())).toMatch(/name/)
  })
  it("words the variance", () => { expect([500, -300, 0, null].map(varianceText)).toEqual(["R 500 over the quote", "R 300 under the quote", "On the quote", null]) })
})

describe("WorkOrdersTab", () => {
  it("lists work orders and opens one with its costs, variance and downtime", async () => {
    show(); fireEvent.click(await screen.findByRole("button", { name: "Open" }))
    expect(await screen.findByText("Oil filter")).toBeTruthy(); expect(screen.getByText("R 500 over the quote")).toBeTruthy(); expect(screen.getByText(/off the road 6.5 h so far/)).toBeTruthy()
  })
  it("records a quotation", async () => {
    show(); fireEvent.click(await screen.findByRole("button", { name: "Open" })); fireEvent.click(await screen.findByText("Record quotation"))
    set("Quoted amount", "4000"); set("Supplier", "Toyota Sandton"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/quotation", expect.objectContaining({ amount: 4000, supplier: "Toyota Sandton" })))
  })
  it("lets an admin approve a quotation but not a manager", async () => {
    status = "QUOTED"; show(); fireEvent.click(await screen.findByRole("button", { name: "Open" })); fireEvent.click(await screen.findByText("Approve"))
    fireEvent.click(screen.getByText("Confirm")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/approve", {}))
    cleanup(); perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); show(); fireEvent.click(await screen.findByRole("button", { name: "Open" })); await screen.findByText("Oil filter"); expect(screen.queryByText("Approve")).toBeNull()
  })
  it("needs a reason to put on hold or cancel", async () => {
    status = "IN_PROGRESS"; show(); fireEvent.click(await screen.findByRole("button", { name: "Open" })); fireEvent.click(await screen.findByText("Put on hold"))
    expect((screen.getByText("Confirm") as HTMLButtonElement).disabled).toBe(true)
    set("Reason", "Waiting for parts"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/hold", { reason: "Waiting for parts" }))
  })
  it("requires the odometer to complete a service", async () => {
    status = "IN_PROGRESS"; show(); fireEvent.click(await screen.findByRole("button", { name: "Open" })); fireEvent.click(await screen.findByText("Complete"))
    expect((screen.getByText("Confirm") as HTMLButtonElement).disabled).toBe(true)
    set("Odometer", "90500"); set("Next service at", "100500"); fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/complete", expect.objectContaining({ odometer: 90500, nextServiceKm: 100500 })))
  })
  it("adds and removes cost lines", async () => {
    show(); fireEvent.click(await screen.findByRole("button", { name: "Open" })); await screen.findByText("Oil filter")
    set("Cost description", "Air filter"); set("Unit cost", "180"); fireEvent.click(screen.getByText("Add cost"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/lines", { lineType: "PART", description: "Air filter", quantity: 1, unitCost: 180, technicianId: null }))
    fireEvent.click(screen.getByLabelText("Remove Oil filter")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fleet/work-orders/w1/lines/l1"))
  })
  it("creates a work order and opens it", async () => {
    show(); fireEvent.click(await screen.findByText("New work order")); await screen.findByText("ABC123MP", { selector: "option" })
    set("Vehicle", "v1"); set("Title", "Replace brake pads"); fireEvent.click(screen.getByText("Create"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/work-orders", expect.objectContaining({ vehicleId: "v1", title: "Replace brake pads", type: "SERVICE" })))
  })
  it("adds a technician", async () => {
    show(); fireEvent.click(await screen.findByText("Technicians")); await screen.findByText(/Sipho · 082/)
    set("Technician name", "Thabo"); set("Hourly rate", "300"); fireEvent.click(screen.getByText("Add technician"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/technicians", { name: "Thabo", phone: null, email: null, hourlyRate: 300, active: true }))
  })
  it("read-only users can look but not change", async () => {
    perms = new Set(["FLEET_READ"]); show(); expect(screen.queryByText("New work order")).toBeNull()
    fireEvent.click(await screen.findByRole("button", { name: "Open" })); await screen.findByText("Oil filter"); expect(screen.queryByText("Add cost")).toBeNull(); expect(screen.queryByText("Start work")).toBeNull()
  })
})

describe("work order link from a vehicle", () => {
  it("reads the vehicle from the address", () => {
    expect(vehicleFromQuery("?vehicleId=abc-1")).toBe("abc-1"); expect(vehicleFromQuery("?x=1")).toBe(""); expect(vehicleFromQuery("")).toBe(""); expect(vehicleFromQuery("?vehicleId=%20v2%20")).toBe("v2")
  })
})
