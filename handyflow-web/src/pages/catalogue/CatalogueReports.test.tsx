import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"
import ReportsPanel from "./ReportsPanel"
import SuggestionsPanel, { ItemSuggestions } from "./SuggestionsPanel"
import { confidenceText, EMPTY_REPORT_OPTIONS, enrichmentText, reportParams, reportProblem, type Enrichment, type Report, type ReportDef } from "./catalogue.logic"

const get = vi.fn(), post = vi.fn()
const navigate = vi.fn()
let perms = new Set<string>()
vi.mock("react-router-dom", async () => ({ ...(await vi.importActual<object>("react-router-dom")), useNavigate: () => navigate }))
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider></MemoryRouter>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CATALOGUE_WRITE", "CATALOGUE_EXPORT"]); for (const m of [get, post, navigate]) m.mockReset(); post.mockResolvedValue(ok({})) })
afterEach(cleanup)

const defs = (): ReportDef[] => [
  { code: "PRICE_LIST", group: "Pricing", title: "Price list", description: "Every item with its price.", needsPrices: false, available: true },
  { code: "LOW_MARGIN", group: "Pricing", title: "Low margins", description: "Items below a margin.", needsPrices: true, available: false },
  { code: "NO_PICTURE", group: "Gaps", title: "Items without a picture", description: "Main items with no picture.", needsPrices: false, available: true },
]
const report = (over: Partial<Report> = {}): Report => ({
  code: "NO_PICTURE", title: "Items without a picture", description: "", columns: [{ key: "name", label: "Item", numeric: false }, { key: "price", label: "Price", numeric: true }],
  rows: [["Cement", "100.00"], ["Sand", "50.00"]], total: 2, truncated: false, generatedAt: "2026-10-21T10:00:00Z", note: null, ...over,
})

describe("report logic", () => {
  it("checks the settings a report takes", () => {
    expect(reportProblem("NO_PICTURE", { ...EMPTY_REPORT_OPTIONS, days: "abc" })).toBe("")   // not a setting of this report
    expect(reportProblem("LOW_MARGIN", { ...EMPTY_REPORT_OPTIONS, marginLimit: "100" })).toContain("0 to 99")
    expect(reportProblem("LOW_MARGIN", { ...EMPTY_REPORT_OPTIONS, marginLimit: "2.5" })).toContain("whole number")
    expect(reportProblem("LOW_MARGIN", EMPTY_REPORT_OPTIONS)).toBe("")
    expect(reportProblem("PRICE_CHANGES", { ...EMPTY_REPORT_OPTIONS, days: "0" })).toContain("1 to 730")
    expect(reportProblem("RECENT_CHANGES", { ...EMPTY_REPORT_OPTIONS, days: "30" })).toBe("")
  })
  it("sends only the settings the report takes and that are filled in", () => {
    expect(reportParams("LOW_MARGIN", { listId: "l1", marginLimit: "15", days: "5" })).toEqual({ marginLimit: 15 })
    expect(reportParams("PRICE_LIST", { listId: "l1", marginLimit: "15", days: "5" })).toEqual({ listId: "l1" })
    expect(reportParams("PRICE_CHANGES", { listId: "", marginLimit: "", days: "" })).toEqual({})
    expect(reportParams("NO_PICTURE", { listId: "l1", marginLimit: "1", days: "1" })).toEqual({})
  })
  it("words the suggestions", () => {
    expect(confidenceText(90)).toBe("Very likely"); expect(confidenceText(75)).toBe("Likely"); expect(confidenceText(60)).toBe("Possible")
    expect(enrichmentText({ scanned: 1, itemsWithSuggestions: 0, truncated: false, items: [] })).toBe("Nothing to suggest across 1 item.")
    expect(enrichmentText({ scanned: 40, itemsWithSuggestions: 1, truncated: false, items: [] })).toBe("1 item with suggestions across 40 items.")
  })
})

describe("ReportsPanel", () => {
  it("lists reports by group and switches off those needing price access", async () => {
    get.mockImplementation(() => ok(defs()))
    wrap(<ReportsPanel />)
    expect(await screen.findByRole("button", { name: "Price list" })).toBeTruthy()
    expect((screen.getByRole("button", { name: "Low margins" }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText("Gaps")).toBeTruthy()
    expect(screen.getByText("Choose a report on the left.")).toBeTruthy()
  })
  it("runs a report, shows the table and offers the CSV to someone who may export", async () => {
    get.mockImplementation((url: string) => url.endsWith("/reports") ? ok(defs()) : ok(report()))
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Items without a picture" }))
    expect(screen.queryByRole("button", { name: "Download CSV" })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Run report" }))
    expect(await screen.findByText("Cement")).toBeTruthy()
    expect(screen.getByText(/2 rows\./)).toBeTruthy()
    expect(screen.getByRole("button", { name: "Download CSV" })).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/catalogue/reports/NO_PICTURE", { params: {} })
  })
  it("keeps the CSV from someone who may not export", async () => {
    perms = new Set(["CATALOGUE_WRITE"])
    get.mockImplementation((url: string) => url.endsWith("/reports") ? ok(defs()) : ok(report()))
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Items without a picture" }))
    fireEvent.click(screen.getByRole("button", { name: "Run report" }))
    expect(await screen.findByText("Cement")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Download CSV" })).toBeNull()
  })
  it("will not run a report with bad settings and passes good ones", async () => {
    perms = new Set(["CATALOGUE_WRITE"])
    const all: ReportDef[] = [...defs(), { code: "PRICE_CHANGES", group: "Pricing", title: "Price changes", description: "Recent.", needsPrices: true, available: true }]
    get.mockImplementation((url: string) => url.endsWith("/reports") ? ok(all) : ok(report({ code: "PRICE_CHANGES" })))
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Price changes" }))
    fireEvent.change(screen.getByLabelText("Last (days)"), { target: { value: "999" } })
    expect(screen.getByRole("alert").textContent).toContain("1 to 730")
    expect((screen.getByRole("button", { name: "Run report" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Last (days)"), { target: { value: "60" } })
    fireEvent.click(screen.getByRole("button", { name: "Run report" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/catalogue/reports/PRICE_CHANGES", { params: { days: 60 } }))
  })
  it("shows the server's refusal", async () => {
    get.mockImplementation((url: string) => url.endsWith("/reports") ? ok(defs()) : Promise.reject({ response: { data: { message: "You are not allowed to see them." } } }))
    wrap(<ReportsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Items without a picture" }))
    fireEvent.click(screen.getByRole("button", { name: "Run report" }))
    expect(await screen.findByText(/not allowed to see them/)).toBeTruthy()
  })
})

const enrichment = (): Enrichment => ({
  scanned: 40, itemsWithSuggestions: 1, truncated: false, items: [{ itemId: "i1", name: "Cement 10kg", sku: null, suggestions: [
    { kind: "CATEGORY", value: "cat-1", label: "Building materials", confidence: 100, reason: "Similar items are in this category: Cement 25kg.", applicable: true },
    { kind: "PRICE", value: "59.97", label: "59.97", confidence: 60, reason: "The usual margin is 33.3%.", applicable: false }] }],
})

describe("SuggestionsPanel", () => {
  it("shows suggestions with their reasons and applies one", async () => {
    get.mockImplementation(() => ok(enrichment()))
    wrap(<SuggestionsPanel />)
    expect(await screen.findByText("1 item with suggestions across 40 items.")).toBeTruthy()
    expect(screen.getByText(/Similar items are in this category/)).toBeTruthy()
    expect(screen.getByText(/Price hint: R 59.97/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Apply Price/ })).toBeNull()   // a hint cannot be applied here
    fireEvent.click(screen.getByRole("button", { name: "Apply Category Building materials" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/items/i1/enrichment/apply", { kind: "CATEGORY", value: "cat-1" }))
  })
  it("opens the item when its name is pressed", async () => {
    get.mockImplementation(() => ok(enrichment()))
    wrap(<SuggestionsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Cement 10kg" }))
    expect(navigate).toHaveBeenCalledWith("/catalogue/items?item=i1")
  })
  it("says when there is nothing to suggest", async () => {
    get.mockImplementation(() => ok({ scanned: 3, itemsWithSuggestions: 0, truncated: false, items: [] }))
    wrap(<SuggestionsPanel />)
    expect(await screen.findByText("Nothing to suggest across 3 items.")).toBeTruthy()
  })
  it("shows the server's reason when applying fails", async () => {
    get.mockImplementation(() => ok(enrichment()))
    post.mockRejectedValue({ response: { data: { message: "This item already has a category." } } })
    wrap(<SuggestionsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Apply Category Building materials" }))
    expect(await screen.findByText(/already has a category/)).toBeTruthy()
  })
})

describe("ItemSuggestions", () => {
  it("shows an item's suggestions, and nothing at all when there are none", async () => {
    get.mockImplementation(() => ok({ itemId: "i1", name: "Cement 10kg", sku: null, suggestions: enrichment().items[0].suggestions }))
    wrap(<ItemSuggestions itemId="i1" canWrite onApplied={() => undefined} />)
    const region = await screen.findByRole("region", { name: "Suggestions" })
    expect(within(region).getByText(/Category: Building materials/)).toBeTruthy()
    cleanup()
    get.mockImplementation(() => ok({ itemId: "i1", name: "Cement", sku: "C", suggestions: [] }))
    const { container } = wrap(<ItemSuggestions itemId="i1" canWrite onApplied={() => undefined} />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
  it("gives a reader no Apply button", async () => {
    get.mockImplementation(() => ok({ itemId: "i1", name: "Cement 10kg", sku: null, suggestions: enrichment().items[0].suggestions }))
    wrap(<ItemSuggestions itemId="i1" canWrite={false} onApplied={() => undefined} />)
    await screen.findByRole("region", { name: "Suggestions" })
    expect(screen.queryByRole("button")).toBeNull()
  })
})
