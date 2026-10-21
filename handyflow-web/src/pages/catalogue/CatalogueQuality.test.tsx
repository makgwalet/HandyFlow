import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"
import DataQualityPanel from "./DataQualityPanel"
import ItemApprovalsPanel from "./ItemApprovalsPanel"
import CatalogueDashboard from "./CatalogueDashboard"
import Product360 from "./Product360"
import { qualityText, rejectProblem, type CatalogueItem, type DashboardData, type DataQuality, type ItemApproval, type ItemOverview } from "./catalogue.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
const navigate = vi.fn()
let perms = new Set<string>()
vi.mock("react-router-dom", async () => ({ ...(await vi.importActual<object>("react-router-dom")), useNavigate: () => navigate }))
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider></MemoryRouter>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CATALOGUE_WRITE"]); for (const m of [get, post, put, navigate]) m.mockReset(); post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

const quality = (over: Partial<DataQuality> = {}): DataQuality => ({
  scanned: 40, total: 3, groups: [
    { kind: "MISSING_CATEGORY", label: "No category", advice: "Add a category.", count: 2, items: [{ itemId: "i1", name: "Cement", sku: "CEM", detail: "No category." }, { itemId: "i2", name: "Sand", sku: null, detail: "No category." }] },
    { kind: "POSSIBLE_DUPLICATE", label: "Possible duplicates", advice: "Keep one.", count: 1, items: [{ itemId: "i3", name: "Brick", sku: "BR", detail: "Looks like 'BRICK' (BR2)." }] },
  ], ...over,
})
const approval = (over: Partial<ItemApproval> = {}): ItemApproval => ({
  id: "a1", itemId: "i1", itemName: "Cement 50kg", sku: "CEM", itemStatus: "DRAFT", status: "PENDING", requestedBy: "u1", requestedAt: "2026-10-20T08:00:00Z",
  note: "Ready to sell", decidedBy: null, decidedAt: null, decisionNote: null, ...over,
})

describe("quality and approval logic", () => {
  it("describes the report", () => {
    expect(qualityText(quality())).toBe("3 things to look at across 40 items.")
    expect(qualityText(quality({ total: 0, scanned: 1, groups: [] }))).toBe("Nothing to fix across 1 item.")
    expect(qualityText(quality({ total: 1 }))).toBe("1 thing to look at across 40 items.")
  })
  it("wants a reason to reject", () => {
    expect(rejectProblem(" a ")).toContain("reason")
    expect(rejectProblem("Price wrong")).toBeNull()
  })
})

describe("DataQualityPanel", () => {
  it("lists groups, expands one, and opens an item", async () => {
    get.mockImplementation(() => ok(quality()))
    wrap(<DataQualityPanel />)
    expect(await screen.findByText("3 things to look at across 40 items.")).toBeTruthy()
    const group = screen.getByRole("region", { name: "No category" })
    expect(within(group).queryByText("Cement")).toBeNull()
    fireEvent.click(within(group).getByRole("button", { name: "Show items" }))
    fireEvent.click(within(group).getByRole("button", { name: "Cement" }))
    expect(navigate).toHaveBeenCalledWith("/catalogue/items?item=i1")
  })
  it("says when there is nothing to fix", async () => {
    get.mockImplementation(() => ok(quality({ total: 0, groups: [] })))
    wrap(<DataQualityPanel />)
    expect(await screen.findByText(/Nothing to fix across 40 items/)).toBeTruthy()
  })
  it("says when the check fails", async () => {
    get.mockImplementation(() => Promise.reject({ response: { status: 500, data: { message: "boom" } } }))
    wrap(<DataQualityPanel />)
    expect(await screen.findByText(/boom|Couldn't run the check/)).toBeTruthy()
  })
})

describe("ItemApprovalsPanel", () => {
  const serve = (on = true, rows: ItemApproval[] = [approval()]) => get.mockImplementation((url: string) => (url.endsWith("/settings") ? ok({ priceApprovalRequired: true, itemApprovalRequired: on, updatedAt: null }) : ok(rows)))
  it("shows waiting requests and lets an approver approve with a note", async () => {
    perms = new Set(["CATALOGUE_APPROVE"]); serve()
    wrap(<ItemApprovalsPanel />)
    expect(await screen.findByText("Cement 50kg")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Approve Cement 50kg" }))
    fireEvent.change(screen.getByLabelText("Note (optional)"), { target: { value: " fine " } })
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Approve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/approvals/a1/approve", { note: "fine" }))
  })
  it("will not send a rejection without a reason", async () => {
    perms = new Set(["CATALOGUE_APPROVE"]); serve()
    wrap(<ItemApprovalsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Reject Cement 50kg" }))
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Reject" }))
    expect(await screen.findByText(/Give a reason/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Price is wrong" } })
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Reject" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/approvals/a1/reject", { note: "Price is wrong" }))
  })
  it("shows the server's reason when someone tries to approve their own request", async () => {
    perms = new Set(["CATALOGUE_APPROVE"]); serve()
    post.mockImplementation(() => Promise.reject({ response: { status: 403, data: { message: "You cannot decide on your own request. Someone else must." } } }))
    wrap(<ItemApprovalsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Approve Cement 50kg" }))
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Approve" }))
    expect(await screen.findByText(/cannot decide on your own request/)).toBeTruthy()
  })
  it("gives writers a withdraw button but no decision buttons", async () => {
    perms = new Set(["CATALOGUE_WRITE"]); serve()
    wrap(<ItemApprovalsPanel />)
    await screen.findByText("Cement 50kg")
    expect(screen.queryByRole("button", { name: "Approve Cement 50kg" })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Withdraw Cement 50kg" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/approvals/a1/withdraw"))
  })
  it("lets only an administrator change the setting", async () => {
    perms = new Set(["CATALOGUE_ADMIN"]); serve(false, [])
    wrap(<ItemApprovalsPanel />)
    const box = await screen.findByLabelText(/New items need approval before they go live/)
    await waitFor(() => expect((box as HTMLInputElement).disabled).toBe(false))
    fireEvent.click(box)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/catalogue/settings", { itemApprovalRequired: true }))
    cleanup(); perms = new Set(["CATALOGUE_WRITE"]); serve(false, [])
    wrap(<ItemApprovalsPanel />)
    expect(((await screen.findByLabelText(/New items need approval before they go live/)) as HTMLInputElement).disabled).toBe(true)
  })
})

describe("approval on the dashboard and the item page", () => {
  const dash: DashboardData = {
    counts: { items: 5, products: 5, services: 0, variants: 0, activeSkus: 3, drafts: 2, suspended: 0, discontinued: 0, discontinuedRecently: 0, incomplete: 0 },
    prices: null, attention: { awaitingApproval: 2, importsWithProblems: 1 }, growth: [], byCategory: [], recentItems: [], activity: [],
  }
  const item = (over: Partial<CatalogueItem> = {}): CatalogueItem => ({
    id: "i1", name: "Cement 50kg", description: "Bag", sku: "CEM", barcode: null, brand: null, itemType: "PRODUCT", status: "DRAFT", statusReason: null, statusChangedAt: null,
    unit: "each", defaultPrice: 100, vatRate: 15, vatExempt: false, categoryId: null, categoryName: null, costPrice: null, marginPercent: null, version: 1,
    createdAt: "2026-10-01T08:00:00Z", updatedAt: "2026-10-01T08:00:00Z", attributes: [], variantCount: 0, ...over,
  })
  const overview = (over: Partial<ItemOverview> = {}): ItemOverview => ({
    item: item(), categoryPath: null, openPriceChanges: 0, approvalState: "DRAFT", parentName: null, missingAttributes: [], approvalRequired: true, pendingApprovalId: null,
    completeness: { score: 80, checks: [] }, ...over,
  })
  const serve = (o: ItemOverview) => get.mockImplementation((url: string) => (url.endsWith("/overview") ? ok(o) : url.endsWith("/approval") ? ok(approval()) : ok([])))

  it("shows what is waiting for approval and imports with problems", async () => {
    get.mockImplementation(() => ok(dash)); wrap(<CatalogueDashboard />)
    expect(await screen.findByText("Awaiting approval")).toBeTruthy()
    expect(screen.getByText("Imports with problems")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Awaiting approval/ }))
    expect(navigate).toHaveBeenCalledWith("/catalogue/approvals")
  })
  it("hides those tiles when there is nothing to look at", async () => {
    get.mockImplementation(() => ok({ ...dash, counts: { ...dash.counts, drafts: 0 }, attention: { awaitingApproval: 0, importsWithProblems: 0 } })); wrap(<CatalogueDashboard />)
    await screen.findByRole("region", { name: "Totals" })
    expect(screen.queryByText("Awaiting approval")).toBeNull()
    expect(screen.queryByText("Imports with problems")).toBeNull()
  })
  it("offers Submit for approval on a draft when approval is required", async () => {
    serve(overview()); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    fireEvent.click(await screen.findByRole("button", { name: "Submit for approval" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/items/i1/approval", {}))
  })
  it("offers no submit button when approval is off, the item is active, or the user cannot write", async () => {
    serve(overview({ approvalRequired: false })); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    await screen.findByRole("tablist"); expect(screen.queryByRole("button", { name: "Submit for approval" })).toBeNull()
    cleanup(); serve(overview({ item: item({ status: "ACTIVE" }) })); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    await screen.findByRole("tablist"); expect(screen.queryByRole("button", { name: "Submit for approval" })).toBeNull()
    cleanup(); perms = new Set(["CATALOGUE_READ"]); serve(overview()); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    await screen.findByRole("tablist"); expect(screen.queryByRole("button", { name: "Submit for approval" })).toBeNull()
  })
  it("shows a pending request, and lets an approver decide from the item page", async () => {
    perms = new Set(["CATALOGUE_WRITE", "CATALOGUE_APPROVE"])
    serve(overview({ approvalState: "APPROVAL_PENDING", pendingApprovalId: "a1" })); wrap(<Product360 itemId="i1" onClose={() => {}} onChanged={() => {}} />)
    expect(await screen.findByText(/Waiting for approval: Ready to sell/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Submit for approval" })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Approve" }))
    fireEvent.click(within(screen.getByRole("dialog", { name: /Approve/ })).getByRole("button", { name: "Approve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/approvals/a1/approve", { note: undefined }))
  })
})
