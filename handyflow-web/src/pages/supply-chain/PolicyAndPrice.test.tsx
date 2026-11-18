import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { PolicyTab } from "./PolicyTab"
import { ConvertRequisitionDialog } from "./ConvertRequisitionDialog"
import { priceView, convertProblem, convertBody, policyProblem, policyBody, type ConvertForm } from "./scmOps"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
let perms = ["SCM_ADMIN"]
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (needed: string[]) => needed.some(n => perms.includes(n)) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = (ui: JSX.Element) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = ["SCM_ADMIN"] }); afterEach(cleanup)

const POLICY = { effective: { budgetBasis: "EX_VAT", vatRecoverablePct: 100, includeNonRecoverableVat: true, priceIncreaseTolerancePct: 5 }, customised: false }

describe("price helpers", () => {
  it("describes a saving, a small rise and a rise beyond tolerance", () => {
    expect(priceView(100, 95, 100, 5)).toMatchObject({ kind: "decrease", saving: 500 })
    expect(priceView(100, 105, 10, 5).kind).toBe("within")
    expect(priceView(100, 120, 100, 5).kind).toBe("beyond")
    expect(priceView(100, 100, 1, 5).kind).toBe("unchanged")
    expect(priceView(0, 10, 1, 5).kind).toBe("beyond")
  })
  const form = (over: Partial<ConvertForm["lines"][0]> = {}): ConvertForm => ({ supplierId: "s1", notes: "", lines: [{ requisitionLineId: "l1", itemName: "Bolts", selected: true, quantity: "10", unitCost: "100", estimate: 100, reason: "", note: "", quotationRef: "", ...over }] })
  it("needs a reason only when the price moved, and a note for OTHER", () => {
    expect(convertProblem(form())).toBeNull()
    expect(convertProblem(form({ unitCost: "95" }))).toContain("why the price differs")
    expect(convertProblem(form({ unitCost: "95", reason: "OTHER" }))).toContain("explain")
    expect(convertProblem(form({ unitCost: "95", reason: "NEGOTIATED_DISCOUNT" }))).toBeNull()
    expect(convertProblem({ ...form(), supplierId: "" })).toContain("supplier")
  })
  it("sends the reason only for a changed price", () => {
    expect(convertBody(form()).lines[0]).toMatchObject({ priceChangeReason: null, unitCost: 100 })
    expect(convertBody(form({ unitCost: "95", reason: "NEGOTIATED_DISCOUNT", quotationRef: " Q1 " })).lines[0]).toMatchObject({ priceChangeReason: "NEGOTIATED_DISCOUNT", quotationRef: "Q1" })
  })
  it("validates the policy", () => {
    const f = { budgetBasis: "EX_VAT", vatRecoverablePct: "100", includeNonRecoverableVat: true, priceIncreaseTolerancePct: "5" }
    expect(policyProblem(f)).toBeNull()
    expect(policyProblem({ ...f, vatRecoverablePct: "101" })).toContain("Recoverable")
    expect(policyProblem({ ...f, priceIncreaseTolerancePct: "" })).toContain("tolerance")
    expect(policyBody(f)).toEqual({ budgetBasis: "EX_VAT", vatRecoverablePct: 100, includeNonRecoverableVat: true, priceIncreaseTolerancePct: 5 })
  })
})

describe("PolicyTab", () => {
  it("saves the policy, and lists a price increase for a second person to decide", async () => {
    get.mockImplementation((u: string) => String(u).endsWith("price-overrides")
      ? ok([{ id: "c1", requisitionNumber: "REQ-1", orderNumber: "PO-9", itemName: "Bolts", quantity: 100, requisitionUnitCost: 100, unitCost: 120, reason: "MARKET_PRICE_CHANGE", note: null, quotationRef: null, convertedByName: "Bea", createdAt: "2026-11-18T10:00:00Z" }])
      : ok(POLICY))
    put.mockResolvedValue({}); post.mockResolvedValue({})
    wrap(<PolicyTab />)
    expect(await screen.findByText("PO-9")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Price tolerance"), { target: { value: "8" } })
    fireEvent.click(screen.getByText("Save policy"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][1]).toMatchObject({ budgetBasis: "EX_VAT", priceIncreaseTolerancePct: 8 })
    fireEvent.click(screen.getByText("Approve"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(String(post.mock.calls[0][0])).toContain("price-overrides/c1/decision")
    expect(post.mock.calls[0][1]).toMatchObject({ approve: true })
  })
  it("is read-only for someone who is not an administrator", async () => {
    perms = ["SCM_READ"]
    get.mockImplementation(() => ok(POLICY))
    wrap(<PolicyTab />)
    expect(await screen.findByText(/Only a Supply Chain administrator/)).toBeTruthy()
    expect(screen.queryByText("Price increases waiting for approval")).toBeNull()
  })
})

describe("ConvertRequisitionDialog", () => {
  it("asks for a reason when the price moves, and sends it", async () => {
    get.mockImplementation((u: string) => String(u).includes("/suppliers") ? ok([{ id: "s1", name: "Alpha" }])
      : String(u).includes("procurement-policy") ? ok(POLICY)
      : ok({ id: "r1", number: "REQ-1", lines: [{ id: "l1", itemName: "Bolts", qtyRequested: 100, qtyRemaining: 100, estimatedUnitCost: 100 }] }))
    post.mockResolvedValue({})
    const onDone = vi.fn()
    wrap(<ConvertRequisitionDialog requisitionId="r1" number="REQ-1" onClose={() => {}} onDone={onDone} />)
    await screen.findByText("Bolts")
    fireEvent.change(screen.getByLabelText("Supplier"), { target: { value: "s1" } })
    fireEvent.change(screen.getByLabelText("Unit price Bolts"), { target: { value: "120" } })
    expect(await screen.findByText(/beyond the 5% tolerance/)).toBeTruthy()
    fireEvent.click(screen.getByText("Create draft PO"))
    expect(await screen.findByText(/why the price differs/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason Bolts"), { target: { value: "MARKET_PRICE_CHANGE" } })
    fireEvent.click(screen.getByText("Create draft PO"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].lines[0]).toMatchObject({ requisitionLineId: "l1", unitCost: 120, priceChangeReason: "MARKET_PRICE_CHANGE" })
    await waitFor(() => expect(onDone).toHaveBeenCalled())
  })
})
