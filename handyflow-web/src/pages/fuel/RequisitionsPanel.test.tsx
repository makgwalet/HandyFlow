import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import RequisitionsPanel from "./RequisitionsPanel"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const r = (o: any = {}) => ({ id: "r1", reqNumber: "REQ-2026-00001", status: "DRAFT", fuelType: "DIESEL", litres: 10000, tankId: null, supplierId: null, supplierName: null, estimatedPrice: 20, estimatedValue: 200000,
  neededBy: null, reason: "Tank 2 is low", requestedBy: "alice", approvalNote: null, purchaseOrderId: null, purchaseOrderNumber: null, cancelReason: null, createdAt: "2026-11-12T08:00:00Z", ...o })
let rows: any[]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><RequisitionsPanel /></QueryClientProvider>)
}

describe("RequisitionsPanel", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); perms = ["FUEL_PURCHASING_MANAGE"]; rows = [r()]
    get.mockImplementation(async (u: string) => ({ data: { data: u.includes("requisitions") ? rows : [{ id: "s1", name: "Acme", approvalStatus: "APPROVED" }, { id: "s2", name: "Pending Co", approvalStatus: "PENDING_APPROVAL" }] } }))
    post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(cleanup)

  it("lists requisitions with their estimate and status", async () => {
    mount()
    expect(await screen.findByText("REQ-2026-00001")).toBeTruthy()
    expect(screen.getByText("Draft")).toBeTruthy(); expect(screen.getByText("Tank 2 is low")).toBeTruthy()
  })
  it("a draft with no estimated price cannot be submitted", async () => {
    rows = [r({ estimatedPrice: null, estimatedValue: null })]; mount()
    expect((await screen.findByLabelText("Submit REQ-2026-00001") as HTMLButtonElement).disabled).toBe(true)
  })
  it("submits a draft", async () => {
    mount(); fireEvent.click(await screen.findByLabelText("Submit REQ-2026-00001"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/requisitions/r1/submit"))
  })
  it("shows why a rejected requisition came back to draft", async () => {
    rows = [r({ approvalNote: "Approval was rejected" })]; mount()
    expect(await screen.findByText("Approval was rejected")).toBeTruthy()
  })
  it("a requisition waiting for approval can only be cancelled", async () => {
    rows = [r({ status: "PENDING_APPROVAL" })]; mount()
    await screen.findByText("Waiting for approval")
    expect(screen.queryByLabelText("Submit REQ-2026-00001")).toBeNull(); expect(screen.queryByLabelText("Edit REQ-2026-00001")).toBeNull(); expect(screen.getByLabelText("Cancel REQ-2026-00001")).toBeTruthy()
  })
  it("raises a requisition after checking it, sending numbers as numbers, and offers only approved suppliers", async () => {
    mount(); fireEvent.click(await screen.findByText("New requisition"))
    fireEvent.click(screen.getByText("Save")); expect(screen.getByRole("alert").textContent).toMatch(/Litres/)
    fireEvent.change(screen.getByLabelText(/Litres/), { target: { value: "5000" } })
    fireEvent.change(screen.getByLabelText(/Estimated price/), { target: { value: "21.5" } })
    fireEvent.change(screen.getByLabelText(/Why is it needed/), { target: { value: "Weekend deliveries" } })
    expect(screen.queryByText("Pending Co")).toBeNull()
    fireEvent.change(screen.getByLabelText(/Preferred supplier/), { target: { value: "s1" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/requisitions", { fuelType: "DIESEL", litres: 5000, tankId: null, supplierId: "s1", estimatedPrice: 21.5, neededBy: null, reason: "Weekend deliveries" }))
  })
  it("edits a draft with a PUT", async () => {
    mount(); fireEvent.click(await screen.findByLabelText("Edit REQ-2026-00001"))
    fireEvent.change(screen.getByLabelText(/Litres/), { target: { value: "12000" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/fuel/requisitions/r1"); expect(put.mock.calls[0][1]).toMatchObject({ litres: 12000 })
  })
  it("raises a draft purchase order from an approved requisition, needing a supplier first", async () => {
    rows = [r({ status: "APPROVED" })]; mount()
    fireEvent.click(await screen.findByLabelText("Raise order from REQ-2026-00001"))
    fireEvent.click(screen.getByRole("button", { name: "Raise purchase order" })); expect(screen.getByRole("alert").textContent).toMatch(/supplier/)
    fireEvent.change(screen.getByLabelText(/Supplier/), { target: { value: "s1" } })
    fireEvent.click(screen.getByRole("button", { name: "Raise purchase order" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/requisitions/r1/convert", { supplierId: "s1", pricePerLitre: 20, expectedDate: null }))
  })
  it("cancelling needs a reason", async () => {
    mount(); fireEvent.click(await screen.findByLabelText("Cancel REQ-2026-00001"))
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Stock arrived early" } })
    fireEvent.click(screen.getByRole("button", { name: "Cancel requisition" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/requisitions/r1/cancel", { reason: "Stock arrived early" }))
  })
  it("shows the order number once converted, and no actions", async () => {
    rows = [r({ status: "CONVERTED", purchaseOrderNumber: "PO-2026-00007" })]; mount()
    expect(await screen.findByText("PO-2026-00007")).toBeTruthy(); expect(screen.queryByLabelText("Cancel REQ-2026-00001")).toBeNull()
  })
  it("without the purchasing permission it is read only", async () => {
    perms = []; mount(); await screen.findByText("REQ-2026-00001")
    expect(screen.queryByText("New requisition")).toBeNull(); expect(screen.queryByLabelText("Submit REQ-2026-00001")).toBeNull()
  })
  it("says so when there are none, and when the list cannot load", async () => {
    rows = []; mount(); await screen.findByText("No requisitions yet.")
    cleanup(); get.mockRejectedValue(new Error("x")); mount()
    expect((await screen.findByRole("alert")).textContent).toMatch(/Could not load requisitions/)
  })
})
