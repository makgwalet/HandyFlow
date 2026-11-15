import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AutomationTab from "./AutomationTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const rule = (o: any = {}) => ({ id: "r1", tankId: "t1", tankName: "Tank 1", fuelType: "DIESEL_50", currentLitres: 1500, supplierId: "s1", supplierName: "Sasol", reorderBelowLitres: 2000, orderLitres: 5000,
  estimatedPrice: 21.5, enabled: true, belowLevel: true, lastCheckedAt: null, lastDraftedAt: null, ...o })
let rules: any[] = []; let log: any[] = []; let alertOn = false; let reviewDays: number | null = null

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><AutomationTab /></QueryClientProvider>)
}

describe("AutomationTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = ["FUEL_PURCHASING_MANAGE"]
    rules = [rule()]; log = []; alertOn = false; reviewDays = null
    post.mockResolvedValue({ data: { data: { checked: 1, drafted: 1, alreadyOpen: 0, failed: 0, requisitionNumbers: ["REQ-2026-00001"] } } })
    put.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/rules")) return { data: { data: rules } }
      if (u.endsWith("/log")) return { data: { data: log } }
      if (u.endsWith("/settings")) return { data: { data: { alertOverduePurchaseOrders: alertOn, varianceReviewDays: reviewDays } } }
      if (u.endsWith("/tanks")) return { data: { data: [{ id: "t1", name: "Tank 1" }, { id: "t2", name: "Tank 2" }] } }
      if (u.endsWith("/suppliers")) return { data: { data: [{ id: "s1", name: "Sasol" }] } }
      return { data: { data: [] } }
    })
    vi.spyOn(window, "confirm").mockReturnValue(true)
  })
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it("shows each rule with the level, the quantity and whether the tank is below its level, and says nothing is ordered for you", async () => {
    mount()
    expect(await screen.findByText("Tank 1")).toBeTruthy()
    expect(screen.getByText("Below level")).toBeTruthy()
    expect(screen.getByText(/nothing is submitted, approved or ordered for you/)).toBeTruthy()
  })

  it("adds a rule for a tank, validating first", async () => {
    mount()
    await screen.findByText("Tank 1")
    fireEvent.click(screen.getByText("Add rule"))
    fireEvent.click(screen.getByText("Save"))
    expect((await screen.findAllByRole("alert")).some(a => /Choose the tank/.test(a.textContent ?? ""))).toBe(true)
    expect(post).not.toHaveBeenCalled()
    await screen.findByText("Tank 2")
    fireEvent.change(screen.getByLabelText("Tank *"), { target: { value: "t2" } })
    fireEvent.change(screen.getByLabelText(/Draft a requisition when the tank is below/), { target: { value: "3000" } })
    fireEvent.change(screen.getByLabelText("Litres to ask for *"), { target: { value: "8000" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/automation/rules", { tankId: "t2", supplierId: null, reorderBelowLitres: 3000, orderLitres: 8000, estimatedPrice: null, enabled: true }))
  })

  it("'Check now' asks first, then reports what was drafted and that nothing was submitted", async () => {
    mount()
    await screen.findByText("Tank 1")
    fireEvent.click(screen.getByText("Check now"))
    expect(window.confirm).toHaveBeenCalled()
    expect(await screen.findByText(/1 requisition drafted \(REQ-2026-00001\)\. Nothing was submitted or ordered\./)).toBeTruthy()
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/automation/run")
  })

  it("switches a rule off without losing its numbers", async () => {
    mount()
    await screen.findByText("Tank 1")
    fireEvent.click(screen.getByText("Switch off"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/automation/rules/r1", expect.objectContaining({ enabled: false, reorderBelowLitres: 2000, orderLitres: 5000, supplierId: "s1", estimatedPrice: 21.5 })))
  })

  it("the overdue-order alert is off until chosen, and choosing it saves", async () => {
    mount()
    const box = (await screen.findByLabelText(/Tell the administrators when a purchase order passes/)) as HTMLInputElement
    await waitFor(() => expect(box.disabled).toBe(false))
    expect(box.checked).toBe(false)
    fireEvent.click(box)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/automation/settings", { alertOverduePurchaseOrders: true, varianceReviewDays: null }))
  })

  it("the variance days box starts empty (no default) and saving sends the number with the other setting", async () => {
    mount()
    const box = (await screen.findByLabelText(/delivery variance is still unresolved after this many days/)) as HTMLInputElement
    await waitFor(() => expect(box.disabled).toBe(false))
    expect(box.value).toBe("")
    fireEvent.change(box, { target: { value: "5" } })
    fireEvent.click(screen.getByText("Save days"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/automation/settings", { alertOverduePurchaseOrders: false, varianceReviewDays: 5 }))
  })

  it("refuses days outside 1 to 365", async () => {
    mount()
    const box = (await screen.findByLabelText(/delivery variance is still unresolved after this many days/)) as HTMLInputElement
    await waitFor(() => expect(box.disabled).toBe(false))
    fireEvent.change(box, { target: { value: "0" } })
    fireEvent.click(screen.getByText("Save days"))
    expect(await screen.findByText(/1 to 365/)).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
  })

  it("lists what the rules did, including failures", async () => {
    log = [{ id: "l1", ruleId: "r1", tankName: "Tank 1", outcome: "DRAFTED", detail: "REQ-2026-00001 drafted for Tank 1.", requisitionId: "q", ranAt: "2026-11-12T04:30:00Z" },
      { id: "l2", ruleId: "r1", tankName: "Tank 1", outcome: "FAILED", detail: "Supplier is suspended", requisitionId: null, ranAt: "2026-11-11T04:30:00Z" }]
    mount()
    expect(await screen.findByText(/REQ-2026-00001 drafted for Tank 1\./)).toBeTruthy()
    expect(screen.getByText("Failed")).toBeTruthy(); expect(screen.getByText(/Supplier is suspended/)).toBeTruthy()
  })

  it("read-only users can look but not add, run or change anything", async () => {
    perms = ["FUEL_READ"]
    mount()
    await screen.findByText("Tank 1")
    expect(screen.queryByText("Add rule")).toBeNull(); expect(screen.queryByText("Check now")).toBeNull(); expect(screen.queryByText("Switch off")).toBeNull()
    expect((screen.getByLabelText(/Tell the administrators/) as HTMLInputElement).disabled).toBe(true)
  })
})
