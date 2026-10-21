import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"
import CatalogueDashboard from "./CatalogueDashboard"
import Product360 from "./Product360"
import { barPercent, outstanding, ringTone, type CatalogueItem, type DashboardData, type ItemOverview } from "./catalogue.logic"

const get = vi.fn()
const navigate = vi.fn()
let perms = new Set<string>()
vi.mock("react-router-dom", async () => ({ ...(await vi.importActual<object>("react-router-dom")), useNavigate: () => navigate }))
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider></MemoryRouter>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CATALOGUE_WRITE"]); get.mockReset(); navigate.mockReset() })
afterEach(cleanup)

const dash = (over: Partial<DashboardData> = {}): DashboardData => ({
  counts: { items: 16, products: 12, services: 4, variants: 7, activeSkus: 12, drafts: 2, suspended: 0, discontinued: 1, discontinuedRecently: 1, incomplete: 4 },
  prices: null,
  growth: [{ month: "2026-09", total: 10 }, { month: "2026-10", total: 16 }],
  byCategory: [{ name: "Hardware", count: 9 }, { name: "No category", count: 3 }],
  recentItems: [{ id: "i1", name: "Cement 50kg", sku: "CEM", categoryName: "Hardware", status: "ACTIVE", defaultPrice: 100, updatedAt: "2026-10-20T08:00:00Z", parentId: null }],
  activity: [{ action: "CREATED", detail: null, entityType: "ITEM", entityId: "i1", entityName: "Cement 50kg", at: "2026-10-20T08:00:00Z" }], ...over,
})
const item = (over: Partial<CatalogueItem> = {}): CatalogueItem => ({
  id: "i1", name: "Cement 50kg", description: "Bag", sku: "CEM", barcode: null, brand: null, itemType: "PRODUCT", status: "ACTIVE", statusReason: null, statusChangedAt: null,
  unit: "each", defaultPrice: 100, vatRate: 15, vatExempt: false, categoryId: null, categoryName: null, costPrice: null, marginPercent: null, version: 1,
  createdAt: "2026-10-01T08:00:00Z", updatedAt: "2026-10-01T08:00:00Z", attributes: [], variantCount: 0, ...over,
})
const overview = (over: Partial<ItemOverview> = {}): ItemOverview => ({
  item: item(), categoryPath: null, openPriceChanges: 0, approvalState: "NONE", parentName: null, missingAttributes: ["Colour"],
  completeness: { score: 60, checks: [{ code: "description", label: "Description", passed: true }, { code: "barcode", label: "Barcode", passed: false }] }, ...over,
})

describe("dashboard and 360 logic", () => {
  it("lists what is outstanding, checks first", () => {
    expect(outstanding(overview())).toEqual(["Barcode is missing", "Colour (required attribute) is missing"])
    expect(outstanding(overview({ missingAttributes: [], completeness: { score: 100, checks: [] } }))).toEqual([])
  })
  it("scales bars against the largest value and keeps small ones visible", () => {
    expect(barPercent(50, 100)).toBe(50)
    expect(barPercent(1, 1000)).toBe(3)
    expect(barPercent(0, 10)).toBe(0)
    expect(barPercent(5, 0)).toBe(0)
  })
  it("colours the score by how complete the record is", () => {
    expect(ringTone(90)).toContain("success")
    expect(ringTone(60)).toContain("warning")
    expect(ringTone(20)).toContain("danger")
  })
})

describe("CatalogueDashboard", () => {
  it("shows the totals, categories, recent items and activity", async () => {
    get.mockImplementation(() => ok(dash()))
    wrap(<CatalogueDashboard />)
    const totals = await screen.findByRole("region", { name: "Totals" })
    expect(within(totals).getByText("16")).toBeTruthy()
    expect(within(totals).getByText("Incomplete records")).toBeTruthy()
    expect(within(totals).queryByText("Price changes waiting")).toBeNull()
    expect(within(screen.getByRole("list", { name: "Items by category" })).getByText("Hardware")).toBeTruthy()
    expect(within(screen.getByRole("region", { name: "Recent items" })).getByText("Cement 50kg")).toBeTruthy()
    expect(within(screen.getByRole("region", { name: "Recent activity" })).getByText("Created")).toBeTruthy()
  })
  it("shows the price queue only when the server sends it", async () => {
    get.mockImplementation(() => ok(dash({ prices: { pending: 3, scheduled: 2 } })))
    wrap(<CatalogueDashboard />)
    expect(await screen.findByText("Price changes waiting")).toBeTruthy()
    expect(screen.getByRole("button", { name: "Review price changes" })).toBeTruthy()
  })
  it("offers Add item only to people who may write, and goes to the items screen", async () => {
    get.mockImplementation(() => ok(dash()))
    wrap(<CatalogueDashboard />)
    fireEvent.click(await screen.findByRole("button", { name: "Add item" }))
    expect(navigate).toHaveBeenCalledWith("/catalogue/items?new=1")
    cleanup(); perms = new Set(["CATALOGUE_READ"])
    wrap(<CatalogueDashboard />)
    await screen.findByRole("region", { name: "Totals" })
    expect(screen.queryByRole("button", { name: "Add item" })).toBeNull()
  })
  it("opens a recent item in Product 360", async () => {
    get.mockImplementation(() => ok(dash()))
    wrap(<CatalogueDashboard />)
    fireEvent.click(await screen.findByRole("button", { name: "Cement 50kg" }))
    expect(navigate).toHaveBeenCalledWith("/catalogue/items?item=i1")
  })
  it("says so when loading fails", async () => {
    get.mockImplementation(() => Promise.reject({ response: { status: 500, data: { message: "boom" } } }))
    wrap(<CatalogueDashboard />)
    expect(await screen.findByText(/boom|Couldn't load the dashboard/)).toBeTruthy()
  })
})

describe("Product360", () => {
  const serve = (o: ItemOverview = overview()) => get.mockImplementation((url: string) => {
    if (url.endsWith("/overview")) return ok(o)
    if (url.endsWith("/history")) return ok({ content: [{ id: "h1", action: "UPDATED", detail: "name: A → B", actorId: null, at: "2026-10-20T08:00:00Z" }], totalElements: 1, totalPages: 1, number: 0, size: 50 })
    if (url.endsWith("/variants")) return ok({ parentId: "i1", parentName: "Cement 50kg", parentSku: "CEM", parentPrice: 100, options: [], variants: [] })
    if (url.endsWith("/pricing")) return ok({ itemId: "i1", itemName: "Cement 50kg", unit: "each", minPrice: null, costPrice: 40, vatRate: 15, vatExempt: false, approvalRequired: false, lists: [{ priceListId: "p1", code: "RETAIL", name: "Retail", defaultList: true, price: 100, priceInclVat: 115, marginPercent: 60, open: [] }] })
    return ok([])
  })
  it("shows the score, the header and what is outstanding", async () => {
    serve(); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    expect(await screen.findByRole("img", { name: "Completeness 60 percent" })).toBeTruthy()
    expect(screen.getByText("CEM")).toBeTruthy()
    const out = screen.getByRole("region", { name: "Outstanding" })
    expect(within(out).getByText("Barcode is missing")).toBeTruthy()
    expect(within(out).getByText("Colour (required attribute) is missing")).toBeTruthy()
  })
  it("says a complete record is complete", async () => {
    serve(overview({ missingAttributes: [], completeness: { score: 100, checks: [{ code: "sku", label: "SKU", passed: true }] } }))
    wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    expect(await screen.findByText(/Nothing outstanding/)).toBeTruthy()
  })
  it("hides the Pricing tab from people without price access", async () => {
    serve(); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    await screen.findByRole("tablist")
    expect(screen.queryByRole("tab", { name: "Pricing" })).toBeNull()
    expect(screen.getByRole("tab", { name: "History" })).toBeTruthy()
  })
  it("shows pricing, variants and history in their tabs", async () => {
    perms = new Set(["CATALOGUE_WRITE", "CATALOGUE_PRICE_READ"])
    serve(); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    fireEvent.click(await screen.findByRole("tab", { name: "Pricing" }))
    expect(await screen.findByText("Retail")).toBeTruthy()
    fireEvent.click(screen.getByRole("tab", { name: "Variants" }))
    expect(await screen.findByText(/No variants/)).toBeTruthy()
    fireEvent.click(screen.getByRole("tab", { name: "History" }))
    expect(await screen.findByText("name: A → B")).toBeTruthy()
  })
  it("does not offer Variants on a variant", async () => {
    serve(overview({ item: item({ parentId: "p1", variantLabel: "Red" }), parentName: "Tee" }))
    wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    await waitFor(() => expect(screen.getByText(/Variant of Tee/)).toBeTruthy())
    expect(screen.queryByRole("tab", { name: "Variants" })).toBeNull()
  })
})
