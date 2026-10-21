import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ItemsPanel from "./ItemsPanel"
import PriceListsPanel from "./PriceListsPanel"
import PriceApprovalsPanel from "./PriceApprovalsPanel"
import { visibleGroups, CATALOGUE_SECTIONS } from "../../navigation/moduleSections"
import {
  EMPTY_PRICE_FORM, EMPTY_PRICE_LIST_FORM, changeText, rand, minPriceBody, minPriceProblem, priceBody, priceListBody, priceListProblems, priceProblems,
  type CatalogueItem, type ItemPricing, type PriceChange, type PriceList,
} from "./catalogue.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const fail = (status: number, message: string) => Promise.reject({ response: { status, data: { message } } })
beforeEach(() => {
  perms = new Set(["CATALOGUE_WRITE", "CATALOGUE_PRICE_READ", "CATALOGUE_PRICE_WRITE", "CATALOGUE_PRICE_APPROVE", "CATALOGUE_ADMIN"])
  for (const m of [get, post, put, del]) m.mockReset()
  for (const m of [post, put, del]) m.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

const item = (): CatalogueItem => ({
  id: "i1", name: "Cement 50kg", description: null, sku: "CEM-50", barcode: null, brand: null, itemType: "PRODUCT", status: "ACTIVE", statusReason: null, statusChangedAt: null,
  unit: "each", defaultPrice: 100, vatRate: 15, vatExempt: false, categoryId: null, categoryName: null, costPrice: 60, marginPercent: 40, version: 1, createdAt: "2026-10-01T08:00:00Z", updatedAt: "2026-10-01T08:00:00Z",
})
const change = (over: Partial<PriceChange> = {}): PriceChange => ({
  id: "c1", itemId: "i1", itemName: "Cement 50kg", priceListId: "l1", priceListName: "Retail", oldPrice: 100, newPrice: 120, changePercent: 20, effectiveFrom: "2026-11-01", reason: "Supplier increase",
  status: "PENDING", proposedByMe: false, proposedAt: "2026-10-21T08:00:00Z", decidedAt: null, decisionNote: null, appliedAt: null, ...over,
})
const pricing = (over: Partial<ItemPricing> = {}): ItemPricing => ({
  itemId: "i1", itemName: "Cement 50kg", unit: "each", minPrice: 80, costPrice: 60, vatRate: 15, vatExempt: false, approvalRequired: true,
  lists: [
    { priceListId: "l1", code: "RETAIL", name: "Retail", defaultList: true, price: 100, priceInclVat: 115, marginPercent: 40, open: [] },
    { priceListId: "l2", code: "TRADE", name: "Trade", defaultList: false, price: null, priceInclVat: null, marginPercent: null, open: [] },
  ], ...over,
})
const list = (over: Partial<PriceList> = {}): PriceList => ({ id: "l1", code: "RETAIL", name: "Retail", defaultList: true, active: true, sortOrder: 0, ...over })
function serve(o: { approval?: boolean; pricing?: ItemPricing; queue?: PriceChange[]; lists?: PriceList[]; history?: PriceChange[] } = {}) {
  get.mockImplementation((url: string) => {
    if (url.endsWith("/settings")) return ok({ priceApprovalRequired: o.approval ?? true, updatedAt: null })
    if (url.endsWith("/pricing")) return ok(o.pricing ?? pricing())
    if (url.endsWith("/price-changes") && url.includes("/items/")) return ok(o.history ?? [])
    if (url.endsWith("/price-changes")) return ok(o.queue ?? [])
    if (url.endsWith("/price-lists")) return ok(o.lists ?? [list(), list({ id: "l2", code: "TRADE", name: "Trade", defaultList: false, sortOrder: 10 })])
    if (url.includes("/items/search")) return ok({ content: [item()], totalElements: 1, totalPages: 1, number: 0, size: 25 })
    if (/\/items\/[^/]+$/.test(url)) return ok(item())
    return ok([])
  })
}

describe("pricing logic", () => {
  const ctx = { minPrice: 80, current: 100, approvalRequired: true, today: "2026-10-21" }
  const good = { ...EMPTY_PRICE_FORM, priceListId: "l1", newPrice: "120", reason: "Supplier increase" }
  it("accepts a good proposal", () => expect(priceProblems(good, ctx)).toEqual({}))
  it("checks the price", () => {
    expect(priceProblems({ ...good, newPrice: "" }, ctx).newPrice).toBe("Enter the new price.")
    expect(priceProblems({ ...good, newPrice: "120,5" }, ctx).newPrice).toBeUndefined()
    expect(priceProblems({ ...good, newPrice: "1.234" }, ctx).newPrice).toBe("Enter an amount like 120 or 120.50.")
    expect(priceProblems({ ...good, newPrice: "79.99" }, ctx).newPrice).toBe(`The price cannot be below the minimum price of ${rand(80)}.`)
    expect(priceProblems({ ...good, newPrice: "100" }, ctx).newPrice).toBe("That is already the price.")
    expect(priceProblems({ ...good, newPrice: "100", effectiveFrom: "2026-12-01" }, ctx).newPrice).toBeUndefined()
  })
  it("refuses a date in the past and needs a reason only when approval is required", () => {
    expect(priceProblems({ ...good, effectiveFrom: "2026-10-20" }, ctx).effectiveFrom).toBe("The effective date cannot be in the past.")
    expect(priceProblems({ ...good, reason: " " }, ctx).reason).toBe("Say why the price is changing (at least 3 characters).")
    expect(priceProblems({ ...good, reason: "" }, { ...ctx, approvalRequired: false }).reason).toBeUndefined()
  })
  it("builds the request", () => {
    expect(priceBody({ ...good, newPrice: "12,5", effectiveFrom: "" })).toEqual({ priceListId: "l1", newPrice: 12.5, effectiveFrom: undefined, reason: "Supplier increase" })
  })
  it("reads the minimum price field", () => {
    expect(minPriceProblem("", 100)).toBeNull()
    expect(minPriceProblem("abc", 100)).toBe("Enter an amount like 80 or 80.50.")
    expect(minPriceProblem("150", 100)).toContain("already below that")
    expect(minPriceBody("")).toEqual({ minPrice: null })
    expect(minPriceBody("80,5")).toEqual({ minPrice: 80.5 })
  })
  it("describes a change", () => {
    expect(changeText({ oldPrice: 100, newPrice: 120, changePercent: 20 })).toBe(`${rand(100)} → ${rand(120)} (+20%)`)
    expect(changeText({ oldPrice: 120, newPrice: 100, changePercent: -16.7 })).toBe(`${rand(120)} → ${rand(100)} (-16.7%)`)
    expect(changeText({ oldPrice: null, newPrice: 90, changePercent: null })).toBe(rand(90))
  })
  it("checks a price list", () => {
    expect(priceListProblems(EMPTY_PRICE_LIST_FORM, true)).toMatchObject({ code: "The code is required.", name: "The name is required." })
    expect(priceListProblems({ ...EMPTY_PRICE_LIST_FORM, code: "2x", name: "n" }, true).code).toContain("Start with a letter")
    expect(priceListProblems({ ...EMPTY_PRICE_LIST_FORM, name: "Trade" }, false)).toEqual({})
    expect(priceListBody({ code: " trade ", name: " Trade ", sortOrder: "5", active: true })).toEqual({ code: "trade", name: "Trade", sortOrder: 5, active: true })
  })
  it("shows the pricing sections only to people who can use them", () => {
    const ids = (p: string[]) => visibleGroups(CATALOGUE_SECTIONS, p).flatMap(g => g.sections.map(s => s.id))
    expect(ids([])).not.toContain("pricelists")
    expect(ids(["CATALOGUE_PRICE_READ"])).toEqual(expect.arrayContaining(["pricelists", "priceapprovals"]))
    expect(ids(["CATALOGUE_PRICE_APPROVE"])).toContain("priceapprovals")
  })
})

describe("pricing on an item", () => {
  it("opens the prices of an item and shows each list with VAT and margin", async () => {
    serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Pricing of Cement 50kg" }))
    const dlg = within(await screen.findByRole("dialog"))
    expect(await dlg.findByText("Trade")).toBeTruthy()
    expect(dlg.getByText(rand(115))).toBeTruthy()
    expect(dlg.getByText("40%")).toBeTruthy()
    expect(dlg.getByText(/Changes wait for someone else to approve them/)).toBeTruthy()
  })
  it("hides the Pricing button from someone who cannot see prices", async () => {
    perms = new Set(["CATALOGUE_WRITE"])
    serve()
    wrap(<ItemsPanel />)
    await screen.findByText("Cement 50kg")
    expect(screen.queryByRole("button", { name: "Pricing of Cement 50kg" })).toBeNull()
  })
  it("checks the new price before sending, then submits it for approval", async () => {
    serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Pricing of Cement 50kg" }))
    fireEvent.click(await screen.findByRole("button", { name: "Change price on Retail" }))
    const dlg = within(screen.getAllByRole("dialog").at(-1)!)
    fireEvent.change(dlg.getByLabelText(/New price/), { target: { value: "70" } })
    fireEvent.change(dlg.getByLabelText(/Reason/), { target: { value: "Supplier increase" } })
    fireEvent.click(dlg.getByRole("button", { name: "Submit for approval" }))
    expect(await dlg.findByText(`The price cannot be below the minimum price of ${rand(80)}.`)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(dlg.getByLabelText(/New price/), { target: { value: "120" } })
    fireEvent.click(dlg.getByRole("button", { name: "Submit for approval" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/catalogue/items/i1/price-changes")
    expect(post.mock.calls[0][1]).toMatchObject({ priceListId: "l1", newPrice: 120, reason: "Supplier increase" })
  })
  it("does not ask for a reason or say 'approval' when the tenant needs none", async () => {
    serve({ approval: false, pricing: pricing({ approvalRequired: false }) })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Pricing of Cement 50kg" }))
    fireEvent.click(await screen.findByRole("button", { name: "Change price on Retail" }))
    const dlg = within(screen.getAllByRole("dialog").at(-1)!)
    fireEvent.change(dlg.getByLabelText(/New price/), { target: { value: "120" } })
    fireEvent.click(dlg.getByRole("button", { name: "Change price" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
  })
  it("shows a server refusal in the dialog", async () => {
    serve()
    post.mockImplementation(() => fail(409, "A price change for Retail is already waiting for a decision. Decide or cancel it first."))
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Pricing of Cement 50kg" }))
    fireEvent.click(await screen.findByRole("button", { name: "Change price on Retail" }))
    const dlg = within(screen.getAllByRole("dialog").at(-1)!)
    fireEvent.change(dlg.getByLabelText(/New price/), { target: { value: "120" } })
    fireEvent.change(dlg.getByLabelText(/Reason/), { target: { value: "Supplier increase" } })
    fireEvent.click(dlg.getByRole("button", { name: "Submit for approval" }))
    expect(await dlg.findByText(/already waiting for a decision/)).toBeTruthy()
  })
  it("saves and clears the minimum price", async () => {
    serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Pricing of Cement 50kg" }))
    const dlg = within(await screen.findByRole("dialog"))
    const box = await dlg.findByLabelText(/Minimum price/)
    fireEvent.change(box, { target: { value: "" } })
    fireEvent.click(dlg.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0]).toEqual(["/api/v1/catalogue/items/i1/min-price", { minPrice: null }])
  })
  it("locks the price on the edit form while approval is required", async () => {
    serve({ approval: true })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Edit Cement 50kg" }))
    const dlg = within(await screen.findByRole("dialog"))
    await waitFor(() => expect((dlg.getByLabelText(/Default price/) as HTMLInputElement).readOnly).toBe(true))
    expect(dlg.getByText(/Use Pricing on the item/, { selector: "span" })).toBeTruthy()
  })
  it("leaves the price editable when approval is off", async () => {
    serve({ approval: false })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Edit Cement 50kg" }))
    const dlg = within(await screen.findByRole("dialog"))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/catalogue/settings"))
    expect((dlg.getByLabelText(/Default price/) as HTMLInputElement).readOnly).toBe(false)
  })
})

describe("price lists panel", () => {
  it("shows the approval switch and saves a change", async () => {
    serve({ approval: true })
    wrap(<PriceListsPanel />)
    const box = await screen.findByRole("checkbox", { name: /Price changes need approval/ })
    await waitFor(() => expect((box as HTMLInputElement).checked).toBe(true))
    fireEvent.click(box)
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0]).toEqual(["/api/v1/catalogue/settings", { priceApprovalRequired: false }])
  })
  it("lets only an administrator change the switch or the lists", async () => {
    perms = new Set(["CATALOGUE_PRICE_READ"])
    serve()
    wrap(<PriceListsPanel />)
    const box = await screen.findByRole("checkbox", { name: /Price changes need approval/ })
    expect((box as HTMLInputElement).disabled).toBe(true)
    expect(screen.queryByRole("button", { name: /Add price list/ })).toBeNull()
    expect(screen.getByText(/Only a catalogue administrator/)).toBeTruthy()
  })
  it("lists the lists and offers no delete for the default one", async () => {
    serve()
    wrap(<PriceListsPanel />)
    expect(await screen.findByText("TRADE")).toBeTruthy()
    expect(screen.getByRole("button", { name: "Delete TRADE" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Delete RETAIL" })).toBeNull()
  })
  it("checks the form and creates a list", async () => {
    serve()
    wrap(<PriceListsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add price list/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.click(dlg.getByRole("button", { name: "Create price list" }))
    expect(await dlg.findByText("The code is required.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(dlg.getByLabelText(/Code/), { target: { value: "WHOLESALE" } })
    fireEvent.change(dlg.getByLabelText(/^Name/), { target: { value: "Wholesale" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create price list" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ code: "WHOLESALE", name: "Wholesale" })
  })
  it("shows a duplicate code on the code field", async () => {
    serve()
    post.mockImplementation(() => fail(409, "A price list with the code 'TRADE' already exists."))
    wrap(<PriceListsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add price list/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/Code/), { target: { value: "trade" } })
    fireEvent.change(dlg.getByLabelText(/^Name/), { target: { value: "Trade 2" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create price list" }))
    expect(await dlg.findByText("A price list with the code 'TRADE' already exists.")).toBeTruthy()
  })
  it("shows why a list cannot be deleted", async () => {
    serve()
    del.mockImplementation(() => fail(409, "This price list has 3 price changes on record. Switch it off instead."))
    wrap(<PriceListsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Delete TRADE" }))
    expect(await screen.findByText(/Switch it off instead/)).toBeTruthy()
  })
})

describe("price approvals panel", () => {
  it("lets an approver approve someone else's change", async () => {
    serve({ queue: [change()] })
    wrap(<PriceApprovalsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Approve Cement 50kg" }))
    const dlg = within(await screen.findByRole("dialog"))
    expect(dlg.getByText(new RegExp(`${rand(100)} → ${rand(120)}`.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")))).toBeTruthy()
    fireEvent.click(dlg.getByRole("button", { name: "Approve" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/catalogue/price-changes/c1/approve")
  })
  it("offers no approve or reject on a change the user proposed, only withdraw", async () => {
    serve({ queue: [change({ proposedByMe: true })] })
    wrap(<PriceApprovalsPanel />)
    await screen.findByText(/someone else decides/)
    expect(screen.queryByRole("button", { name: "Approve Cement 50kg" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Reject Cement 50kg" })).toBeNull()
    expect(screen.getByRole("button", { name: "Cancel Cement 50kg" }).textContent).toBe("Withdraw")
  })
  it("needs a reason to reject", async () => {
    serve({ queue: [change()] })
    wrap(<PriceApprovalsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Reject Cement 50kg" }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.click(dlg.getByRole("button", { name: "Reject" }))
    expect(await dlg.findByText("Say why (at least 3 characters).")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(dlg.getByLabelText(/Why are you rejecting it/), { target: { value: "Margin too thin" } })
    fireEvent.click(dlg.getByRole("button", { name: "Reject" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0]).toEqual(["/api/v1/catalogue/price-changes/c1/reject", { note: "Margin too thin" }])
  })
  it("shows a server refusal", async () => {
    serve({ queue: [change()] })
    post.mockImplementation(() => fail(403, "You proposed this change, so someone else has to decide it."))
    wrap(<PriceApprovalsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Approve Cement 50kg" }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.click(dlg.getByRole("button", { name: "Approve" }))
    expect(await dlg.findByText("You proposed this change, so someone else has to decide it.")).toBeTruthy()
  })
  it("gives a user without the approve permission no approve or reject buttons", async () => {
    perms = new Set(["CATALOGUE_PRICE_READ"])
    serve({ queue: [change()] })
    wrap(<PriceApprovalsPanel />)
    await screen.findByText("Cement 50kg")
    expect(screen.queryByRole("button", { name: "Approve Cement 50kg" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Cancel Cement 50kg" })).toBeNull()
  })
  it("switches to the scheduled changes", async () => {
    serve({ queue: [] })
    wrap(<PriceApprovalsPanel />)
    expect(await screen.findByText("Nothing is waiting for approval.")).toBeTruthy()
    fireEvent.click(screen.getByRole("tab", { name: "Scheduled" }))
    expect(await screen.findByText("No price changes are scheduled.")).toBeTruthy()
    expect(get.mock.calls.at(-1)![1]).toEqual({ params: { status: "SCHEDULED" } })
  })
})
