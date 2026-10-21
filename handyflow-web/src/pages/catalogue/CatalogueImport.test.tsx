import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ImportPanel from "./ImportPanel"
import ItemsPanel from "./ItemsPanel"
import {
  bulkBody, bulkProblem, bulkSummary, EMPTY_BULK_FORM, importCount, importSummaryText, resultText,
  type BulkResult, type CatalogueItem, type ImportPreview,
} from "./catalogue.logic"

const get = vi.fn(), post = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CATALOGUE_IMPORT", "CATALOGUE_EXPORT"]); get.mockReset(); post.mockReset() })
afterEach(cleanup)

const preview = (over: Partial<ImportPreview> = {}): ImportPreview => ({
  mode: "CREATE_OR_UPDATE", total: 3, create: 1, update: 1, unchanged: 0, errors: 1, fileProblems: [], unknownColumns: ["Colour"], truncated: false,
  rows: [
    { line: 3, key: "B", action: "ERROR", changes: [], errors: ["A new item needs a name."], notes: [] },
    { line: 2, key: "A", action: "CREATE", changes: ["new item"], errors: [], notes: [] },
    { line: 4, key: "C", action: "UPDATE", changes: ["brand: PPC → Sephaku"], errors: [], notes: [] },
  ], ...over,
})
const file = (csv: string) => { const f = new File([csv], "items.csv", { type: "text/csv" }); Object.defineProperty(f, "text", { value: () => Promise.resolve(csv) }); return f }

describe("import and bulk logic", () => {
  it("only lets an import go ahead when its rows are fine or the bad ones are left out", () => {
    expect(importCount(null, false)).toBeNull()
    expect(importCount(preview(), false)).toBeNull()
    expect(importCount(preview(), true)).toBe(2)
    expect(importCount(preview({ errors: 0 }), false)).toBe(2)
    expect(importCount(preview({ errors: 0, create: 0, update: 0, unchanged: 3 }), false)).toBeNull()
    expect(importCount(preview({ fileProblems: ["The file is empty."] }), true)).toBeNull()
  })
  it("describes the check and the result in words", () => {
    expect(importSummaryText(preview())).toBe("3 rows: 1 new, 1 to update, 0 unchanged, 1 with problems.")
    expect(importSummaryText(preview({ fileProblems: ["The file is empty."] }))).toBe("The file is empty.")
    expect(resultText({ jobId: "j", total: 3, created: 1, updated: 1, unchanged: 0, skipped: 1, failed: 0, problems: [] })).toBe("1 added, 1 updated, 0 unchanged, 1 left out.")
  })
  it("checks the bulk form", () => {
    expect(bulkProblem({ ...EMPTY_BULK_FORM, status: "SUSPENDED", reason: "" })).toContain("reason")
    expect(bulkProblem({ ...EMPTY_BULK_FORM, status: "ACTIVE", reason: "" })).toBeNull()
    expect(bulkProblem({ ...EMPTY_BULK_FORM, action: "CATEGORY" })).toBe("Choose a category.")
    expect(bulkProblem({ ...EMPTY_BULK_FORM, action: "PRICE_PERCENT", percent: "" })).toContain("Enter the percentage")
    expect(bulkProblem({ ...EMPTY_BULK_FORM, action: "PRICE_PERCENT", percent: "0" })).toContain("0%")
    expect(bulkProblem({ ...EMPTY_BULK_FORM, action: "PRICE_PERCENT", percent: "-95" })).toContain("between")
    expect(bulkProblem({ ...EMPTY_BULK_FORM, action: "PRICE_PERCENT", percent: "7,5" })).toBeNull()
  })
  it("builds the request for each kind of change", () => {
    expect(bulkBody(["a"], { ...EMPTY_BULK_FORM, action: "PRICE_PERCENT", percent: "7,5", reason: " cost " }, true)).toEqual({ ids: ["a"], action: "PRICE_PERCENT", dryRun: true, status: undefined, reason: "cost", categoryId: undefined, percent: 7.5, effectiveFrom: undefined })
    expect(bulkBody(["a"], { ...EMPTY_BULK_FORM, action: "CATEGORY", categoryId: "c1" }, false)).toMatchObject({ categoryId: "c1", percent: undefined, status: undefined })
  })
  it("sums up a bulk result", () => {
    const r: BulkResult = { dryRun: true, ok: 2, unchanged: 1, refused: 1, rows: [] }
    expect(bulkSummary(r)).toBe("Would change 2, 1 already as asked, 1 refused.")
    expect(bulkSummary({ ...r, dryRun: false, unchanged: 0, refused: 0 })).toBe("Changed 2.")
  })
})

describe("ImportPanel", () => {
  const serve = () => get.mockImplementation((url: string) => (url.endsWith("/jobs") ? ok([{ id: "j1", fileName: "old.csv", mode: "CREATE_ONLY", totalRows: 5, created: 5, updated: 0, unchanged: 0, skipped: 0, failed: 0, createdAt: "2026-10-01T08:00:00Z" }]) : ok([])))
  const choose = async (csv = "sku,name,price\nA,One,1\n") => {
    fireEvent.change(screen.getByLabelText("CSV file"), { target: { files: [file(csv)] } })
    await waitFor(() => expect((screen.getByRole("button", { name: "Check file" }) as HTMLButtonElement).disabled).toBe(false))
  }
  it("checks a file, shows problems first, and imports only after the check", async () => {
    serve(); post.mockImplementation((url: string) => (url.endsWith("/preview") ? ok(preview({ errors: 0, total: 2, rows: preview().rows.slice(1) })) : ok({ jobId: "j2", total: 2, created: 1, updated: 1, unchanged: 0, skipped: 0, failed: 0, problems: [] })))
    render(<QueryClientProvider client={new QueryClient()}><ImportPanel /></QueryClientProvider>)
    expect(screen.queryByRole("button", { name: /^Import \d+ row/ })).toBeNull()
    await choose()
    fireEvent.click(screen.getByRole("button", { name: "Check file" }))
    expect(await screen.findByText(/2 rows: 1 new/)).toBeTruthy()
    expect(screen.getByText(/Ignored columns: Colour/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Import 2 rows" }))
    expect(await screen.findByText(/Import finished: 1 added, 1 updated/)).toBeTruthy()
    expect(post).toHaveBeenCalledWith("/api/v1/catalogue/import", expect.objectContaining({ csv: "sku,name,price\nA,One,1\n", mode: "CREATE_OR_UPDATE" }))
  })
  it("blocks the import while rows have problems unless they are left out", async () => {
    serve(); post.mockImplementation(() => ok(preview()))
    render(<QueryClientProvider client={new QueryClient()}><ImportPanel /></QueryClientProvider>)
    await choose()
    fireEvent.click(screen.getByRole("button", { name: "Check file" }))
    const problemRow = (await screen.findByText("Problem")).closest("tr") as HTMLElement
    expect(within(problemRow).getByText("A new item needs a name.")).toBeTruthy()
    expect((screen.getByRole("button", { name: "Nothing to import yet" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByLabelText(/Leave out the 1 row/))
    expect((screen.getByRole("button", { name: "Import 2 rows" }) as HTMLButtonElement).disabled).toBe(false)
  })
  it("shows the server's reason when the file cannot be checked", async () => {
    serve(); post.mockImplementation(() => Promise.reject({ response: { status: 400, data: { message: "Choose a file" } } }))
    render(<QueryClientProvider client={new QueryClient()}><ImportPanel /></QueryClientProvider>)
    await choose()
    fireEvent.click(screen.getByRole("button", { name: "Check file" }))
    expect(await screen.findByText("Choose a file")).toBeTruthy()
  })
  it("shows recent imports, and hides import from people who may only export", async () => {
    serve(); render(<QueryClientProvider client={new QueryClient()}><ImportPanel /></QueryClientProvider>)
    expect(await screen.findByText("old.csv")).toBeTruthy()
    cleanup(); perms = new Set(["CATALOGUE_EXPORT"])
    render(<QueryClientProvider client={new QueryClient()}><ImportPanel /></QueryClientProvider>)
    expect(screen.getByRole("button", { name: "Export catalogue" })).toBeTruthy()
    expect(screen.queryByLabelText("CSV file")).toBeNull()
    expect(screen.queryByRole("region", { name: "Recent imports" })).toBeNull()
  })
  it("offers the export only to people who may export", () => {
    perms = new Set(["CATALOGUE_IMPORT"]); serve()
    render(<QueryClientProvider client={new QueryClient()}><ImportPanel /></QueryClientProvider>)
    expect(screen.getByRole("button", { name: "Download template" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Export catalogue" })).toBeNull()
  })
})

describe("bulk change from the item list", () => {
  const item = (id: string, name: string): CatalogueItem => ({
    id, name, description: null, sku: id.toUpperCase(), barcode: null, brand: null, itemType: "PRODUCT", status: "ACTIVE", statusReason: null, statusChangedAt: null,
    unit: "each", defaultPrice: 100, vatRate: 15, vatExempt: false, categoryId: null, categoryName: null, costPrice: 40, marginPercent: 60, version: 1,
    createdAt: "2026-10-01T08:00:00Z", updatedAt: "2026-10-01T08:00:00Z",
  })
  const serve = () => get.mockImplementation((url: string) => {
    if (url.includes("/items/search")) { const c = [item("a", "Cement"), item("b", "Sand")]; return ok({ content: c, totalElements: 2, totalPages: 1, number: 0, size: 25 }) }
    return ok([])
  })
  it("checks first, then applies to the items that passed", async () => {
    perms = new Set(["CATALOGUE_WRITE", "CATALOGUE_PRICE_READ", "CATALOGUE_PRICE_WRITE"]); serve()
    post.mockImplementation((_u: string, b: { dryRun: boolean }) => ok({ dryRun: b.dryRun, ok: 1, unchanged: 0, refused: 1, rows: [
      { id: "a", name: "Cement", sku: "A", outcome: "OK", message: "100.00 → 110.00" }, { id: "b", name: "Sand", sku: "B", outcome: "REFUSED", message: "Below the minimum price." }] }))
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByLabelText("Select all items on this page"))
    expect(screen.getByText("2 selected")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Change selected…" }))
    fireEvent.change(screen.getByLabelText("What do you want to change?"), { target: { value: "PRICE_PERCENT" } })
    expect((screen.getByRole("button", { name: /^Apply to/ }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Change prices by (%)"), { target: { value: "10" } })
    fireEvent.click(screen.getByRole("button", { name: "Check first" }))
    expect(await screen.findByText("Would change 1, 1 refused.")).toBeTruthy()
    expect(screen.getByText("Below the minimum price.")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Apply to 1 item" }))
    await waitFor(() => expect(post).toHaveBeenLastCalledWith("/api/v1/catalogue/items/bulk", expect.objectContaining({ dryRun: false, action: "PRICE_PERCENT", percent: 10, ids: ["a", "b"] })))
    expect(await screen.findByText("Changed 1, 1 refused.")).toBeTruthy()
  })
  it("offers no selection to readers and no price option without the price permission", async () => {
    perms = new Set(["CATALOGUE_READ"]); serve()
    wrap(<ItemsPanel />)
    await screen.findByText("Cement")
    expect(screen.queryByLabelText("Select all items on this page")).toBeNull()
    cleanup(); perms = new Set(["CATALOGUE_WRITE"]); serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByLabelText("Select Cement"))
    fireEvent.click(screen.getByRole("button", { name: "Change selected…" }))
    expect(screen.queryByRole("option", { name: "Prices, by a percentage" })).toBeNull()
  })
  it("asks for a reason before checking a suspension", async () => {
    perms = new Set(["CATALOGUE_WRITE"]); serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByLabelText("Select Cement"))
    fireEvent.click(screen.getByRole("button", { name: "Change selected…" }))
    fireEvent.click(screen.getByRole("button", { name: "Check first" }))
    expect(await screen.findByText(/Give a reason/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
})
