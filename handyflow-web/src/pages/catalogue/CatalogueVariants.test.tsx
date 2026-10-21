import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ItemsPanel from "./ItemsPanel"
import {
  buildMatrix, combinations, matrixProblems, optionProblem, parseValues, suggestVariantSku, variantEditBody, variantEditProblems, variantsBody,
  type CatalogueItem, type VariantRow, type VariantsView,
} from "./catalogue.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const fail = (status: number, message: string) => Promise.reject({ response: { status, data: { message } } })
beforeEach(() => {
  perms = new Set(["CATALOGUE_WRITE", "CATALOGUE_PRICE_READ", "CATALOGUE_PRICE_WRITE"])
  for (const m of [get, post, put, del]) m.mockReset()
  for (const m of [post, put, del]) m.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

const item = (over: Partial<CatalogueItem> = {}): CatalogueItem => ({
  id: "i1", name: "Tee shirt", description: null, sku: "TEE", barcode: null, brand: null, itemType: "PRODUCT", status: "ACTIVE", statusReason: null, statusChangedAt: null,
  unit: "each", defaultPrice: 100, vatRate: 15, vatExempt: false, categoryId: null, categoryName: null, costPrice: 40, marginPercent: 60, version: 1,
  createdAt: "2026-10-01T08:00:00Z", updatedAt: "2026-10-01T08:00:00Z", ...over,
})
const variant = (over: Partial<VariantRow> = {}): VariantRow => ({ id: "v1", name: "Tee shirt — S / Red", label: "S / Red", values: ["S", "Red"], sku: "TEE-S-RED", barcode: null, status: "ACTIVE", defaultPrice: 90, costPrice: 40, version: 2, ...over })
const view = (over: Partial<VariantsView> = {}): VariantsView => ({ parentId: "i1", parentName: "Tee shirt", parentSku: "TEE", parentPrice: 100, options: [], variants: [], ...over })
function serve(o: { items?: CatalogueItem[]; view?: VariantsView } = {}) {
  get.mockImplementation((url: string) => {
    if (url.endsWith("/variants")) return ok(o.view ?? view())
    if (url.endsWith("/settings")) return ok({ priceApprovalRequired: false })
    if (url.includes("/items/search")) { const c = o.items ?? [item()]; return ok({ content: c, totalElements: c.length, totalPages: 1, number: 0, size: 25 }) }
    return ok([])
  })
}

describe("variant logic", () => {
  it("reads comma separated values, dropping blanks and repeats", () => {
    expect(parseValues(" S, M ,, m, L ")).toEqual(["S", "M", "L"])
    expect(parseValues("")).toEqual([])
  })
  it("builds every combination in option order", () => {
    expect(combinations([["S", "M"], ["Red", "Blue"]])).toEqual([["S", "Red"], ["S", "Blue"], ["M", "Red"], ["M", "Blue"]])
  })
  it("suggests a SKU from the parent's", () => {
    expect(suggestVariantSku("cem-50", ["Large", "Red"])).toBe("CEM-50-LAR-RED")
    expect(suggestVariantSku(null, ["S"])).toBe("")
    expect(suggestVariantSku("T", ["***"])).toBe("T-X")
  })
  it("checks the options before building", () => {
    expect(optionProblem([])).toBe("Add at least one option, for example Size.")
    expect(optionProblem([{ name: " ", values: "S" }])).toBe("Each option needs a name, for example Size or Colour.")
    expect(optionProblem([{ name: "Size", values: "S" }, { name: "size", values: "M" }])).toBe("Two options have the same name.")
    expect(optionProblem([{ name: "Size", values: "" }])).toBe("The option 'Size' needs at least one value.")
    expect(optionProblem([{ name: "Size", values: "a|b" }])).toBe("A value cannot contain | or =.")
    expect(optionProblem([{ name: "A", values: "1,2,3,4,5,6,7,8,9,10,11" }, { name: "B", values: "1,2,3,4,5,6,7,8,9,10,11" }])).toContain("makes 121 variants")
    expect(optionProblem([{ name: "Size", values: "S" }], ["Size", "Colour"])).toContain("already has the options Size, Colour")
    expect(optionProblem([{ name: "size", values: "S" }], ["Size"])).toBeNull()
  })
  it("leaves out combinations that already exist", () => {
    const rows = buildMatrix([{ name: "Size", values: "S, M" }, { name: "Colour", values: "Red" }], "TEE", 100, [variant()])
    expect(rows.map(r => r.values)).toEqual([["M", "Red"]])
    expect(rows[0]).toMatchObject({ sku: "TEE-M-RED", price: "100", include: true })
  })
  it("checks the ticked rows", () => {
    const rows = buildMatrix([{ name: "Size", values: "S, M" }], "TEE", 100, [])
    expect(matrixProblems(rows)).toEqual({})
    expect(matrixProblems([{ ...rows[0], include: false }, { ...rows[1], include: false }]).general).toBe("Tick at least one variant to create.")
    expect(matrixProblems([{ ...rows[0], sku: "A" }, { ...rows[1], sku: "a" }])[`${rows[1].key}:sku`]).toBe("Same SKU as another row.")
    expect(matrixProblems([{ ...rows[0], sku: "bad sku" }, rows[1]])[`${rows[0].key}:sku`]).toBe("Letters, numbers and . _ / - only.")
    expect(matrixProblems([{ ...rows[0], price: "" }, rows[1]])[`${rows[0].key}:price`]).toBe("Required.")
    expect(matrixProblems([{ ...rows[0], price: "1.234" }, rows[1]])[`${rows[0].key}:price`]).toBe("Like 120 or 120.50.")
  })
  it("builds the request from the ticked rows only", () => {
    const opts = [{ name: " Size ", values: "S, M" }]
    const rows = buildMatrix(opts, "TEE", 100, [])
    rows[1].include = false
    expect(variantsBody(opts, rows)).toEqual({ options: [{ name: "Size", values: ["S", "M"] }], variants: [{ values: ["S"], sku: "TEE-S", barcode: undefined, defaultPrice: 100 }] })
  })
  it("checks and builds a variant edit", () => {
    const f = { sku: "tee-1", barcode: "", price: "95,5", cost: "" }
    expect(variantEditProblems(f, true)).toEqual({})
    expect(variantEditProblems({ ...f, price: "x" }, true).price).toBe("Price must be a number with at most 2 decimals")
    expect(variantEditBody(f, 3, true)).toEqual({ sku: "tee-1", barcode: undefined, defaultPrice: 95.5, costPrice: undefined, version: 3 })
    expect(variantEditBody({ ...f, cost: "40" }, 3, false).costPrice).toBeUndefined()
  })
})

describe("variants in the item list", () => {
  it("shows how many variants a parent has and offers Variants instead of Pricing", async () => {
    serve({ items: [item({ variantCount: 3 })] })
    wrap(<ItemsPanel />)
    expect(await screen.findByText("3 variants")).toBeTruthy()
    expect(screen.getByRole("button", { name: "Variants of Tee shirt" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Pricing of Tee shirt" })).toBeNull()
  })
  it("shows a variant's label and gives it no Variants button", async () => {
    serve({ items: [item({ id: "v1", name: "Tee shirt — S / Red", parentId: "i1", variantLabel: "S / Red" })] })
    wrap(<ItemsPanel />)
    expect(await screen.findByText("Variant: S / Red")).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Variants of/ })).toBeNull()
    expect(screen.getByRole("button", { name: /Pricing of/ })).toBeTruthy()
  })
  it("lets a plain item get variants", async () => {
    serve()
    wrap(<ItemsPanel />)
    expect(await screen.findByRole("button", { name: "Variants of Tee shirt" })).toBeTruthy()
  })
})

describe("variants dialog", () => {
  const open = async () => { fireEvent.click(await screen.findByRole("button", { name: "Variants of Tee shirt" })); return within(await screen.findByRole("dialog")) }

  it("lists the variants with SKU and price", async () => {
    serve({ view: view({ options: [{ name: "Size", values: ["S"] }, { name: "Colour", values: ["Red"] }], variants: [variant()] }) })
    wrap(<ItemsPanel />)
    const dlg = await open()
    expect(await dlg.findByText("S / Red")).toBeTruthy()
    expect(dlg.getByText("TEE-S-RED")).toBeTruthy()
    expect(dlg.getByText(/no longer sold/)).toBeTruthy()
  })
  it("builds the combinations, then creates only the ticked ones", async () => {
    serve()
    wrap(<ItemsPanel />)
    const dlg = await open()
    fireEvent.click(await dlg.findByRole("button", { name: "Add variants" }))
    fireEvent.change(dlg.getByLabelText("Values"), { target: { value: "S, M" } })
    fireEvent.click(dlg.getByRole("button", { name: "Show combinations" }))
    expect(await dlg.findByLabelText("SKU for S")).toBeTruthy()
    expect((dlg.getByLabelText("SKU for M") as HTMLInputElement).value).toBe("TEE-M")
    fireEvent.click(dlg.getByLabelText("Create M"))
    fireEvent.click(dlg.getByRole("button", { name: "Create variants" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/catalogue/items/i1/variants")
    expect(post.mock.calls[0][1]).toEqual({ options: [{ name: "Size", values: ["S", "M"] }], variants: [{ values: ["S"], sku: "TEE-S", barcode: undefined, defaultPrice: 100 }] })
  })
  it("explains a problem with the options instead of building", async () => {
    serve()
    wrap(<ItemsPanel />)
    const dlg = await open()
    fireEvent.click(await dlg.findByRole("button", { name: "Add variants" }))
    fireEvent.click(dlg.getByRole("button", { name: "Show combinations" }))
    expect(await dlg.findByText("The option 'Size' needs at least one value.")).toBeTruthy()
  })
  it("shows a server refusal", async () => {
    serve()
    post.mockImplementation(() => fail(409, "Another item already uses the SKU 'TEE-S'."))
    wrap(<ItemsPanel />)
    const dlg = await open()
    fireEvent.click(await dlg.findByRole("button", { name: "Add variants" }))
    fireEvent.change(dlg.getByLabelText("Values"), { target: { value: "S" } })
    fireEvent.click(dlg.getByRole("button", { name: "Show combinations" }))
    fireEvent.click(await dlg.findByRole("button", { name: "Create variants" }))
    expect(await dlg.findByText("Another item already uses the SKU 'TEE-S'.")).toBeTruthy()
  })
  it("locks the option names once variants exist and skips combinations already made", async () => {
    serve({ view: view({ options: [{ name: "Size", values: ["S", "M"] }], variants: [variant({ label: "S", values: ["S"] })] }) })
    wrap(<ItemsPanel />)
    const dlg = await open()
    fireEvent.click(await dlg.findByRole("button", { name: "Add more variants" }))
    expect((dlg.getByLabelText("Option 1") as HTMLInputElement).readOnly).toBe(true)
    expect(dlg.queryByRole("button", { name: /Add option/ })).toBeNull()
    fireEvent.click(dlg.getByRole("button", { name: "Show combinations" }))
    expect(await dlg.findByLabelText("SKU for M")).toBeTruthy()
    expect(dlg.queryByLabelText("SKU for S")).toBeNull()
  })
  it("edits a variant's SKU and price", async () => {
    serve({ view: view({ options: [{ name: "Size", values: ["S"] }, { name: "Colour", values: ["Red"] }], variants: [variant()] }) })
    wrap(<ItemsPanel />)
    const dlg = await open()
    fireEvent.click(await dlg.findByRole("button", { name: "Edit S / Red" }))
    const edit = within(screen.getAllByRole("dialog").at(-1)!)
    fireEvent.change(edit.getByLabelText(/^Price/), { target: { value: "95" } })
    fireEvent.click(edit.getByRole("button", { name: "Save changes" }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/catalogue/items/i1/variants/v1")
    expect(put.mock.calls[0][1]).toMatchObject({ sku: "TEE-S-RED", defaultPrice: 95, version: 2 })
  })
  it("shows the server's reason when a price edit needs approval", async () => {
    serve({ view: view({ options: [{ name: "Size", values: ["S"] }], variants: [variant()] }) })
    put.mockImplementation(() => fail(400, "Prices on this account change through a price change request, which needs approval. Use Change price on the item."))
    wrap(<ItemsPanel />)
    const dlg = await open()
    fireEvent.click(await dlg.findByRole("button", { name: "Edit S / Red" }))
    const edit = within(screen.getAllByRole("dialog").at(-1)!)
    fireEvent.change(edit.getByLabelText(/^Price/), { target: { value: "95" } })
    fireEvent.click(edit.getByRole("button", { name: "Save changes" }))
    expect(await edit.findByText(/needs approval/)).toBeTruthy()
  })
  it("gives a reader no way to add or edit", async () => {
    perms = new Set(["CATALOGUE_PRICE_READ"])
    serve({ items: [item({ variantCount: 1 })], view: view({ options: [{ name: "Size", values: ["S"] }], variants: [variant()] }) })
    wrap(<ItemsPanel />)
    const dlg = await open()
    await dlg.findByText("S / Red")
    expect(dlg.queryByRole("button", { name: /Add (more )?variants/ })).toBeNull()
    expect(dlg.queryByRole("button", { name: "Edit S / Red" })).toBeNull()
  })
})
