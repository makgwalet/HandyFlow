import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ItemsPanel from "./ItemsPanel"
import CategoriesPanel from "./CategoriesPanel"
import UnitsPanel from "./UnitsPanel"
import AttributesPanel from "./AttributesPanel"
import { visibleGroups, CATALOGUE_SECTIONS } from "../../navigation/moduleSections"
import {
  EMPTY_ATTRIBUTE_FORM, EMPTY_FILTERS, EMPTY_ITEM_FORM, EMPTY_UNIT_FORM, allowedMoves, applicableAttributes, attributeBody, attributeFormProblems, attributeProblems,
  duplicateField, errorMessage, fieldErrors, indented, itemBody, itemProblems, listParams, matchUnit, parentChoices, rangeText, selfAndAncestorIds, statusProblem,
  subtreeIds, toItemForm, unitBody, unitProblems,
  type AttributeDef, type CatalogueItem, type Category, type ItemForm, type Unit,
} from "./catalogue.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const fail = (status: number, message: string, errors?: { field: string; message: string }[]) => Promise.reject({ response: { status, data: { message, errors } } })
const ALL = ["CATALOGUE_WRITE", "CATALOGUE_ARCHIVE", "CATALOGUE_PRICE_READ", "CATALOGUE_PRICE_WRITE"]
beforeEach(() => { perms = new Set(ALL); for (const m of [get, post, put, del]) m.mockReset(); for (const m of [post, put, del]) m.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

const item = (over: Partial<CatalogueItem> = {}): CatalogueItem => ({
  id: "i1", name: "Cement 50kg", description: null, sku: "CEM-50", barcode: "6001234567890", brand: "PPC", itemType: "PRODUCT", status: "ACTIVE", statusReason: null, statusChangedAt: null,
  unit: "Each", defaultPrice: 120, vatRate: 15, vatExempt: false, categoryId: "c1", categoryName: "Building", costPrice: 80, marginPercent: 33.3, version: 4, createdAt: "2026-10-01T08:00:00Z", updatedAt: "2026-10-01T08:00:00Z", ...over,
})
const cat = (over: Partial<Category> = {}): Category => ({ id: "c1", name: "Building", description: null, sortOrder: 0, itemCount: 2, parentId: null, path: over.name ?? "Building", depth: 1, ...over })
const unit = (over: Partial<Unit> = {}): Unit => ({ id: "u1", code: "each", name: "Each", kind: "COUNT", decimals: 0, active: true, itemCount: 3, ...over })
const def = (over: Partial<AttributeDef> = {}): AttributeDef => ({
  id: "a1", categoryId: null, categoryPath: null, code: "colour", label: "Colour", type: "TEXT", required: false, options: [], min: null, max: null, maxLength: null, sortOrder: 0, active: true, valueCount: 0, ...over,
})
const pageOf = (content: CatalogueItem[], over: Record<string, number> = {}) => ({ content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 25, ...over })
function serve(o: { items?: CatalogueItem[]; page?: ReturnType<typeof pageOf>; cats?: Category[]; history?: unknown[]; units?: Unit[]; defs?: AttributeDef[]; full?: CatalogueItem } = {}) {
  get.mockImplementation((url: string) => {
    if (url.endsWith("/history")) return ok({ content: o.history ?? [], totalElements: (o.history ?? []).length })
    if (url.includes("/items/search")) return ok(o.page ?? pageOf(o.items ?? [item()]))
    if (/\/items\/[^/]+$/.test(url)) return ok(o.full ?? item())
    if (url.includes("/categories")) return ok(o.cats ?? [cat()])
    if (url.endsWith("/units")) return ok(o.units ?? [unit(), unit({ id: "u2", code: "kg", name: "Kilogram", kind: "WEIGHT", decimals: 3, itemCount: 0 })])
    if (url.endsWith("/attributes")) return ok(o.defs ?? [])
    return ok([])
  })
}
const lastSearch = () => get.mock.calls.filter(c => String(c[0]).includes("/items/search")).at(-1)![1].params as Record<string, unknown>

describe("catalogue logic", () => {
  const good: ItemForm = { ...EMPTY_ITEM_FORM, name: "Cement", defaultPrice: "10" }
  it("accepts a minimal form", () => expect(itemProblems(good, true)).toEqual({}))
  it("checks name, unit and price", () => {
    expect(itemProblems({ ...good, name: "  " }, false).name).toBe("Item name is required")
    expect(itemProblems({ ...good, name: "x".repeat(256) }, false).name).toBe("The name can be at most 255 characters")
    expect(itemProblems({ ...good, unit: "" }, false).unit).toBe("Unit is required")
    expect(itemProblems({ ...good, defaultPrice: "" }, false).defaultPrice).toBe("Price is required")
    expect(itemProblems({ ...good, defaultPrice: "abc" }, false).defaultPrice).toBe("Price must be a number with at most 2 decimals")
    expect(itemProblems({ ...good, defaultPrice: "-5" }, false).defaultPrice).toBe("Price cannot be negative")
    expect(itemProblems({ ...good, defaultPrice: "1.234" }, false).defaultPrice).toBe("Price must be a number with at most 2 decimals")
    expect(itemProblems({ ...good, defaultPrice: "99999999999999" }, false).defaultPrice).toBe("Price is too large")
  })
  it("checks SKU, barcode, brand and cost", () => {
    expect(itemProblems({ ...good, sku: "ab 12" }, false).sku).toBe("The SKU can only use letters, numbers and . _ / - (no spaces)")
    expect(itemProblems({ ...good, sku: "ab-12/x" }, false).sku).toBeUndefined()
    expect(itemProblems({ ...good, sku: "A".repeat(51) }, false).sku).toBe("The SKU can be at most 50 characters")
    expect(itemProblems({ ...good, barcode: "600 123" }, false).barcode).toBe("The barcode can only use letters, numbers and . _ / - (no spaces)")
    expect(itemProblems({ ...good, brand: "b".repeat(101) }, false).brand).toBe("The brand can be at most 100 characters")
    expect(itemProblems({ ...good, costPrice: "-1" }, true).costPrice).toBe("Cost cannot be negative")
    expect(itemProblems({ ...good, costPrice: "-1" }, false).costPrice).toBeUndefined()
  })
  it("checks VAT unless the item is exempt", () => {
    expect(itemProblems({ ...good, vatRate: "150" }, false).vatRate).toBe("VAT must be between 0 and 100")
    expect(itemProblems({ ...good, vatRate: "", vatExempt: true }, false).vatRate).toBeUndefined()
  })
  it("builds the request body", () => {
    const f = { ...good, name: " Cement ", sku: " cem-1 ", barcode: " 600 ".trim(), costPrice: "6.5", description: " ", draft: true }
    expect(itemBody(f, { canWriteCost: true, version: 3, creating: true })).toMatchObject({ name: "Cement", sku: "CEM-1", barcode: "600", costPrice: 6.5, defaultPrice: 10, vatRate: 15, vatExempt: false, status: "DRAFT", version: 3, description: undefined })
    expect(itemBody(f, { canWriteCost: false, creating: false }).costPrice).toBeUndefined()
    expect(itemBody(f, { canWriteCost: false, creating: false }).status).toBeUndefined()
    expect(itemBody({ ...f, vatExempt: true }, { canWriteCost: true }).vatRate).toBe(0)
  })
  it("round-trips an item through the form", () => {
    const f = toItemForm(item({ description: "Bag", costPrice: null }))
    expect(f).toMatchObject({ name: "Cement 50kg", sku: "CEM-50", unit: "Each", defaultPrice: "120", costPrice: "", description: "Bag", categoryId: "c1" })
  })
  it("only sends the filters that are set", () => {
    expect(listParams(EMPTY_FILTERS)).toEqual({ page: 0, size: 25, sort: "name", desc: false })
    expect(listParams({ ...EMPTY_FILTERS, q: " pipe ", status: "ACTIVE", type: "SERVICE", categoryId: "c9", page: 2 })).toEqual({ page: 2, size: 25, sort: "name", desc: false, q: "pipe", status: "ACTIVE", type: "SERVICE", categoryId: "c9" })
  })
  it("allows only the lifecycle moves the server allows", () => {
    expect(allowedMoves("ACTIVE", true)).toEqual(["SUSPENDED", "DISCONTINUED", "ARCHIVED"])
    expect(allowedMoves("ACTIVE", false)).toEqual(["SUSPENDED"])
    expect(allowedMoves("ARCHIVED", false)).toEqual(["DRAFT"])
    expect(allowedMoves("DRAFT", false)).toEqual(["ACTIVE"])
  })
  it("checks a status change", () => {
    expect(statusProblem("ACTIVE", "DISCONTINUED", " ")).toBe("Say why (at least 3 characters).")
    expect(statusProblem("ACTIVE", "DISCONTINUED", "range ended")).toBeNull()
    expect(statusProblem("DRAFT", "ACTIVE", "")).toBeNull()
    expect(statusProblem("ACTIVE", "ACTIVE", "")).toBe("The item is already active.")
    expect(statusProblem("DRAFT", "SUSPENDED", "xxx")).toBe("A draft item cannot become suspended.")
  })
  it("reads server errors", () => {
    expect(errorMessage({ response: { data: { message: "Nope" } } }, "x")).toBe("Nope")
    expect(errorMessage(new Error("boom"), "fallback")).toBe("fallback")
    expect(fieldErrors({ response: { data: { errors: [{ field: "name", message: "Required" }] } } })).toEqual({ name: "Required" })
    expect(duplicateField("An item named 'X' already exists.")).toBe("name")
    expect(duplicateField("Another item already uses the SKU 'X'.")).toBe("sku")
    expect(duplicateField("Another item already uses the barcode '1'.")).toBe("barcode")
    expect(duplicateField("Something else")).toBeNull()
  })
  it("describes the visible range", () => {
    expect(rangeText({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 25 })).toBe("0 items")
    expect(rangeText({ content: new Array(25).fill(1), totalElements: 60, totalPages: 3, number: 1, size: 25 })).toBe("26–50 of 60")
  })
})

describe("catalogue structure logic", () => {
  const tree = [
    cat({ id: "b", name: "Building", depth: 1 }),
    cat({ id: "c", name: "Cement", parentId: "b", depth: 2, path: "Building › Cement" }),
    cat({ id: "d", name: "Bagged", parentId: "c", depth: 3, path: "Building › Cement › Bagged" }),
    cat({ id: "t", name: "Tools", depth: 1 }),
  ]
  it("finds a category's subtree and its ancestors", () => {
    expect(subtreeIds(tree, "b")).toEqual(["b", "c", "d"])
    expect(subtreeIds(tree, "t")).toEqual(["t"])
    expect(selfAndAncestorIds(tree, "d")).toEqual(["d", "c", "b"])
    expect(selfAndAncestorIds(tree, null)).toEqual([])
  })
  it("indents nested categories in a select", () => {
    expect(indented(tree[0])).toBe("Building")
    expect(indented(tree[2])).toBe("\u2003\u2003↳ Bagged")
  })
  it("offers only valid parents when moving a category", () => {
    expect(parentChoices(tree, "c").map(c => c.id)).toEqual(["b", "t"])               // not itself or Bagged
    expect(parentChoices(tree, "t").map(c => c.id)).toEqual(["b", "c", "d"])
    expect(parentChoices(tree, null).map(c => c.id)).toEqual(["b", "c", "d", "t"])
    const deep = [...tree, cat({ id: "e", name: "E", parentId: "d", depth: 4 }), cat({ id: "f", name: "F", parentId: "e", depth: 5 })]
    expect(parentChoices(deep, null).map(c => c.id)).not.toContain("f")                 // nothing can go below level 5
    expect(parentChoices(deep, "c").map(c => c.id)).toEqual(["b", "t"])                 // Cement's subtree is 4 levels tall, so it fits only under level 1
  })
  it("matches a unit ignoring case", () => {
    expect(matchUnit([unit()], " EACH ")?.code).toBe("each")
    expect(matchUnit([unit()], "box")).toBeUndefined()
  })
  it("checks a unit form", () => {
    expect(unitProblems(EMPTY_UNIT_FORM, true).code).toBe("The unit code is required")
    expect(unitProblems({ ...EMPTY_UNIT_FORM, code: "x".repeat(51) }, true).code).toBe("The unit code can be at most 50 characters")
    expect(unitProblems({ ...EMPTY_UNIT_FORM, code: "bag", decimals: "5" }, true).decimals).toBe("Decimals must be between 0 and 4")
    expect(unitProblems({ ...EMPTY_UNIT_FORM, code: "", decimals: "2" }, false)).toEqual({})   // the code is not edited on an existing unit
    expect(unitBody({ ...EMPTY_UNIT_FORM, code: " bag ", name: " ", decimals: "0" })).toMatchObject({ code: "bag", name: undefined, decimals: 0, kind: "COUNT", active: true })
  })
  it("picks the attributes that apply: own, ancestors and global, not siblings or switched off", () => {
    const defs = [def({ id: "g", label: "Note" }), def({ id: "b", categoryId: "b", label: "Grade" }), def({ id: "c", categoryId: "c", label: "Bag kg" }),
      def({ id: "t", categoryId: "t", label: "Voltage" }), def({ id: "x", label: "Off", active: false })]
    expect(applicableAttributes(defs, tree, "d").map(d => d.id)).toEqual(["b", "c", "g"].sort((a, z) => defs.find(d => d.id === a)!.label.localeCompare(defs.find(d => d.id === z)!.label)))
    expect(applicableAttributes(defs, tree, "t").map(d => d.id).sort()).toEqual(["g", "t"])
    expect(applicableAttributes(defs, tree, null).map(d => d.id)).toEqual(["g"])
  })
  it("checks attribute values by type", () => {
    const defs = [
      def({ id: "n", label: "Length", type: "NUMBER", min: 1, max: 10 }), def({ id: "b", label: "Hazardous", type: "BOOLEAN" }), def({ id: "d", label: "Expiry", type: "DATE" }),
      def({ id: "l", label: "Grade", type: "LIST", options: ["Standard", "Premium"] }), def({ id: "t", label: "Code", type: "TEXT", maxLength: 3 }),
    ]
    expect(attributeProblems(defs, { n: "5", b: "yes", d: "2026-10-21", l: "premium", t: "abc" }, true)).toEqual({})
    const bad = attributeProblems(defs, { n: "11", b: "maybe", d: "21/10/2026", l: "Gold", t: "abcd" }, true)
    expect(bad["attr:n"]).toBe("Must be at most 10")
    expect(bad["attr:b"]).toBe("Choose yes or no")
    expect(bad["attr:d"]).toBe("Use the date format YYYY-MM-DD")
    expect(bad["attr:l"]).toBe('"Gold" is not one of the allowed options')
    expect(bad["attr:t"]).toBe("At most 3 characters")
    expect(attributeProblems(defs, { n: "abc" }, true)["attr:n"]).toBe('"abc" is not a number')
    expect(attributeProblems(defs, { d: "2026-02-30" }, true)["attr:d"]).toBe("Use the date format YYYY-MM-DD")
  })
  it("requires required attributes except on a draft", () => {
    const defs = [def({ id: "r", label: "Colour", required: true })]
    expect(attributeProblems(defs, {}, true)).toEqual({ "attr:r": "Colour is required" })
    expect(attributeProblems(defs, { r: "  " }, true)).toEqual({ "attr:r": "Colour is required" })
    expect(attributeProblems(defs, {}, false)).toEqual({})
  })
  it("sends the full attribute set only when some apply", () => {
    const f: ItemForm = { ...EMPTY_ITEM_FORM, name: "Paint", defaultPrice: "10", attributes: { a: " Red ", b: "" } }
    expect(itemBody(f, { canWriteCost: false, attributeIds: ["a", "b", "c"] }).attributes).toEqual({ a: "Red", b: "", c: "" })
    expect(itemBody(f, { canWriteCost: false, attributeIds: [] }).attributes).toBeUndefined()
    expect(itemBody(f, { canWriteCost: false }).attributes).toBeUndefined()
  })
  it("carries stored attribute values into the form", () => {
    const f = toItemForm(item({ attributes: [{ definitionId: "a1", code: "colour", label: "Colour", type: "TEXT", value: "Red" }] }))
    expect(f.attributes).toEqual({ a1: "Red" })
    expect(toItemForm(item()).attributes).toEqual({})
  })
  it("checks an attribute form", () => {
    expect(attributeFormProblems(EMPTY_ATTRIBUTE_FORM, true)).toMatchObject({ code: "The attribute code is required", label: "The attribute name is required" })
    expect(attributeFormProblems({ ...EMPTY_ATTRIBUTE_FORM, code: "2x", label: "X" }, true).code).toBe("Start with a letter; use only letters, numbers and underscores")
    expect(attributeFormProblems({ ...EMPTY_ATTRIBUTE_FORM, code: "grade", label: "Grade", type: "LIST" }, true).options).toBe("Add at least one option")
    expect(attributeFormProblems({ ...EMPTY_ATTRIBUTE_FORM, code: "grade", label: "Grade", type: "LIST", options: "A\na" }, true).options).toBe("An option is listed twice")
    expect(attributeFormProblems({ ...EMPTY_ATTRIBUTE_FORM, code: "len", label: "Len", type: "NUMBER", min: "5", max: "1" }, true).max).toBe("The maximum cannot be below the minimum")
    expect(attributeFormProblems({ ...EMPTY_ATTRIBUTE_FORM, code: "c", label: "C", maxLength: "0" }, true).maxLength).toBe("Between 1 and 500")
    expect(attributeFormProblems({ ...EMPTY_ATTRIBUTE_FORM, code: "", label: "Grade" }, false)).toEqual({})   // the code is fixed after creation
  })
  it("builds an attribute body with only the settings that suit the type", () => {
    expect(attributeBody({ ...EMPTY_ATTRIBUTE_FORM, code: " grade ", label: " Grade ", type: "LIST", options: "A\n\n B ", min: "1", maxLength: "9", required: true }))
      .toMatchObject({ code: "grade", label: "Grade", type: "LIST", options: ["A", "B"], min: undefined, maxLength: undefined, required: true })
    expect(attributeBody({ ...EMPTY_ATTRIBUTE_FORM, code: "len", label: "Len", type: "NUMBER", min: "0", max: "9.5" })).toMatchObject({ min: 0, max: 9.5, options: undefined })
  })
  it("shows the setup screens only to catalogue administrators", () => {
    const names = (perms: string[]) => visibleGroups(CATALOGUE_SECTIONS, perms).flatMap(g => g.sections.map(s => s.id))
    expect(names([])).toEqual(["dashboard", "items", "categories"])
    expect(names(["SETTINGS_MANAGE"])).toEqual(["dashboard", "items", "categories"])
    expect(names(["CATALOGUE_ADMIN"])).toEqual(["dashboard", "items", "categories", "pricelists", "units", "attributes"])
  })
})

describe("items panel", () => {
  it("lists items from the server with identifiers, status and margin", async () => {
    serve()
    wrap(<ItemsPanel />)
    expect(await screen.findByText("Cement 50kg")).toBeTruthy()
    expect(screen.getByText(/SKU CEM-50 · Barcode 6001234567890 · PPC/)).toBeTruthy()
    const row = within(screen.getByText("Cement 50kg").closest("tr") as HTMLElement)
    expect(row.getByText("Active")).toBeTruthy()
    expect(row.getByText("33.3%")).toBeTruthy()
    expect(lastSearch()).toEqual({ page: 0, size: 25, sort: "name", desc: false })
  })
  it("settings access alone opens editing but not prices", async () => {
    perms = new Set(["SETTINGS_MANAGE"])
    serve()
    wrap(<ItemsPanel />)
    await screen.findByText("Cement 50kg")
    expect(screen.queryByText("Margin")).toBeNull()
    expect(screen.getByRole("button", { name: /Add item/ })).toBeTruthy()
    expect(screen.getByRole("button", { name: /Delete Cement/ })).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: /Edit Cement/ }))
    expect(within(await screen.findByRole("dialog")).queryByLabelText(/Cost price/)).toBeNull()
  })
  it("hides cost and write actions from a read-only user", async () => {
    perms = new Set()
    serve()
    wrap(<ItemsPanel />)
    await screen.findByText("Cement 50kg")
    expect(screen.queryByText("Margin")).toBeNull()
    expect(screen.queryByRole("button", { name: /Add item/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /Edit Cement/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /Delete Cement/ })).toBeNull()
    expect(screen.getByRole("button", { name: /History of Cement/ })).toBeTruthy()
  })
  it("asks the server again when a filter changes, and can clear them", async () => {
    serve()
    wrap(<ItemsPanel />)
    await screen.findByText("Cement 50kg")
    fireEvent.change(screen.getByLabelText("Filter by status"), { target: { value: "SUSPENDED" } })
    await waitFor(() => expect(lastSearch().status).toBe("SUSPENDED"))
    fireEvent.change(screen.getByLabelText("Filter by type"), { target: { value: "SERVICE" } })
    await waitFor(() => expect(lastSearch().type).toBe("SERVICE"))
    fireEvent.click(screen.getByRole("button", { name: "Clear filters" }))
    await waitFor(() => expect(lastSearch()).toEqual({ page: 0, size: 25, sort: "name", desc: false }))
  })
  it("searches after the user pauses typing", async () => {
    serve()
    wrap(<ItemsPanel />)
    await screen.findByText("Cement 50kg")
    fireEvent.change(screen.getByLabelText("Search items"), { target: { value: "cem" } })
    await waitFor(() => expect(lastSearch().q).toBe("cem"))
  })
  it("explains an empty catalogue and an empty search differently", async () => {
    serve({ page: pageOf([]) })
    wrap(<ItemsPanel />)
    expect(await screen.findByText("No items yet")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Filter by status"), { target: { value: "DRAFT" } })
    expect(await screen.findByText("No items match")).toBeTruthy()
  })
  it("shows a load failure", async () => {
    get.mockImplementation((url: string) => (url.includes("/items/search") ? fail(500, "Database down") : ok([])))
    wrap(<ItemsPanel />)
    expect(await screen.findByText("Database down")).toBeTruthy()
  })
  it("pages and sorts on the server", async () => {
    serve({ page: pageOf([item()], { totalElements: 60, totalPages: 3 }) })
    wrap(<ItemsPanel />)
    await screen.findByText("Cement 50kg")
    fireEvent.click(screen.getByRole("button", { name: /Next/ }))
    await waitFor(() => expect(lastSearch().page).toBe(1))
    fireEvent.click(screen.getByRole("button", { name: /^Price/ }))
    await waitFor(() => expect(lastSearch()).toMatchObject({ sort: "price", desc: false, page: 1 }))
    fireEvent.click(screen.getByRole("button", { name: /^Price/ }))
    await waitFor(() => expect(lastSearch()).toMatchObject({ sort: "price", desc: true }))
  })

  it("creates an item with a cleaned SKU", async () => {
    serve({ page: pageOf([]) })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/Item name/), { target: { value: " Paint 5L " } })
    fireEvent.change(dlg.getByLabelText("SKU"), { target: { value: "pnt-5l" } })
    fireEvent.change(dlg.getByLabelText(/Default price/), { target: { value: "249.90" } })
    fireEvent.change(dlg.getByLabelText(/Cost price/), { target: { value: "150" } })
    fireEvent.click(dlg.getByLabelText(/Save as a draft/))
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/catalogue/items")
    expect(post.mock.calls[0][1]).toMatchObject({ name: "Paint 5L", sku: "PNT-5L", defaultPrice: 249.9, costPrice: 150, status: "DRAFT", vatRate: 15, itemType: "PRODUCT" })
  })
  it("blocks an invalid form and shows each message", async () => {
    serve({ page: pageOf([]) })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText("SKU"), { target: { value: "a b" } })
    fireEvent.change(dlg.getByLabelText("Barcode"), { target: { value: "1 2" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    expect(await dlg.findByText("Item name is required")).toBeTruthy()
    expect(dlg.getByText("Price is required")).toBeTruthy()
    expect(dlg.getByText("The SKU can only use letters, numbers and . _ / - (no spaces)")).toBeTruthy()
    expect(dlg.getByText("The barcode can only use letters, numbers and . _ / - (no spaces)")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
  it("puts a duplicate SKU message on the SKU field", async () => {
    serve({ page: pageOf([]) })
    post.mockImplementation(() => fail(409, "Another item already uses the SKU 'CEM-50'."))
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/Item name/), { target: { value: "Cement" } })
    fireEvent.change(dlg.getByLabelText("SKU"), { target: { value: "cem-50" } })
    fireEvent.change(dlg.getByLabelText(/Default price/), { target: { value: "10" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    expect(await dlg.findByText("Another item already uses the SKU 'CEM-50'.")).toBeTruthy()
    expect(screen.getByRole("dialog")).toBeTruthy()
  })
  it("does not show the cost field without price access, and shows it read-only without price write", async () => {
    perms = new Set(["CATALOGUE_WRITE"])
    serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    expect(within(await screen.findByRole("dialog")).queryByLabelText(/Cost price/)).toBeNull()
    cleanup(); perms = new Set(["CATALOGUE_WRITE", "CATALOGUE_PRICE_READ"]); serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Edit Cement/ }))
    expect((within(await screen.findByRole("dialog")).getByLabelText(/Cost price/) as HTMLInputElement).readOnly).toBe(true)
  })
  it("sends the version on edit, and tells the user when someone else saved first", async () => {
    serve()
    put.mockImplementation(() => fail(409, "This item was changed by someone else. Reload it and try again."))
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Edit Cement/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/Default price/), { target: { value: "130" } })
    fireEvent.click(dlg.getByRole("button", { name: "Save changes" }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/catalogue/items/i1")
    expect(put.mock.calls[0][1]).toMatchObject({ defaultPrice: 130, version: 4, sku: "CEM-50" })
    expect(await dlg.findByText("This item was changed by someone else. Reload it and try again.")).toBeTruthy()
  })
  it("offers no edit for an archived item", async () => {
    serve({ items: [item({ status: "ARCHIVED" })] })
    wrap(<ItemsPanel />)
    await screen.findByText("Cement 50kg")
    expect(screen.queryByRole("button", { name: /Edit Cement/ })).toBeNull()
    expect(screen.getByRole("button", { name: /Change status of Cement/ })).toBeTruthy()
  })

  it("needs a reason to discontinue, then sends status, reason and version", async () => {
    serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Change status of Cement/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText("Change to"), { target: { value: "DISCONTINUED" } })
    fireEvent.click(dlg.getByRole("button", { name: "Discontinue" }))
    expect(await dlg.findByText("Say why (at least 3 characters).")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(dlg.getByLabelText(/Reason/), { target: { value: "Range ended" } })
    fireEvent.click(dlg.getByRole("button", { name: "Discontinue" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0]).toEqual(["/api/v1/catalogue/items/i1/status", { status: "DISCONTINUED", reason: "Range ended", version: 4 }])
  })
  it("without the archive permission the discontinue and archive moves are not offered", async () => {
    perms = new Set(["CATALOGUE_WRITE"])
    serve()
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Change status of Cement/ }))
    const opts = within(await screen.findByRole("dialog")).getAllByRole("option").map(o => o.textContent)
    expect(opts).toEqual(["Suspend"])
  })
  it("deletes after confirmation and shows a server refusal", async () => {
    serve()
    del.mockImplementation(() => fail(404, "Item not found"))
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Delete Cement/ }))
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Yes, Delete" }))
    expect(await screen.findByText("Item not found")).toBeTruthy()
    expect(del.mock.calls[0][0]).toBe("/api/v1/catalogue/items/i1")
  })
  it("shows an item's history", async () => {
    serve({ history: [{ id: "h1", action: "PRICE_CHANGED", detail: "price: 100.00 -> 120.00", actorId: null, at: "2026-10-21T10:00:00Z" }] })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /History of Cement/ }))
    expect(await screen.findByText("Price changed")).toBeTruthy()
    expect(screen.getByText("price: 100.00 -> 120.00")).toBeTruthy()
  })
})

describe("items panel: units and attributes", () => {
  it("offers the tenant's own units and sends the list's spelling of the code", async () => {
    serve({ page: pageOf([]) })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    const dlg = within(await screen.findByRole("dialog"))
    const unitSelect = await waitFor(() => { const el = dlg.getByLabelText(/Unit/) as HTMLSelectElement; expect(el.options.length).toBeGreaterThan(1); return el })
    expect([...unitSelect.options].map(o => o.value)).toEqual(["each", "kg"])
    expect(unitSelect.value).toBe("each")                                   // the default "Each" is shown as the list's "each"
    fireEvent.change(dlg.getByLabelText(/Item name/), { target: { value: "Sand" } })
    fireEvent.change(dlg.getByLabelText(/Default price/), { target: { value: "10" } })
    fireEvent.change(unitSelect, { target: { value: "kg" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ unit: "kg" })
  })
  it("keeps showing an item's unit even if it is no longer in the active list", async () => {
    serve({ units: [unit({ code: "kg", name: "Kilogram" })] })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Edit Cement/ }))
    const dlg = within(await screen.findByRole("dialog"))
    await waitFor(() => expect((dlg.getByLabelText(/Unit/) as HTMLSelectElement).value).toBe("Each"))
  })
  it("shows the attributes that apply to the chosen category and sends their values", async () => {
    const cats = [cat({ id: "b", name: "Building" }), cat({ id: "t", name: "Tools" })]
    serve({ page: pageOf([]), cats, defs: [
      def({ id: "g", label: "Note" }), def({ id: "b1", categoryId: "b", label: "Grade", type: "LIST", options: ["Standard", "Premium"] }),
      def({ id: "t1", categoryId: "t", label: "Voltage", type: "NUMBER" }),
    ] })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    const dlg = within(await screen.findByRole("dialog"))
    await dlg.findByLabelText("Note")                                       // global attribute is there with no category
    expect(dlg.queryByLabelText("Grade")).toBeNull()
    fireEvent.change(dlg.getByLabelText(/Category/), { target: { value: "b" } })
    expect(await dlg.findByLabelText("Grade")).toBeTruthy()
    expect(dlg.queryByLabelText("Voltage")).toBeNull()
    fireEvent.change(dlg.getByLabelText("Grade"), { target: { value: "Premium" } })
    fireEvent.change(dlg.getByLabelText(/Item name/), { target: { value: "Cement" } })
    fireEvent.change(dlg.getByLabelText(/Default price/), { target: { value: "10" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].attributes).toEqual({ g: "", b1: "Premium" })
  })
  it("blocks saving until a required attribute is filled, but not for a draft", async () => {
    serve({ page: pageOf([]), defs: [def({ id: "r", label: "Colour", required: true })] })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    const dlg = within(await screen.findByRole("dialog"))
    await dlg.findByLabelText(/Colour/)
    fireEvent.change(dlg.getByLabelText(/Item name/), { target: { value: "Paint" } })
    fireEvent.change(dlg.getByLabelText(/Default price/), { target: { value: "10" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    expect(await dlg.findByText("Colour is required")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(dlg.getByLabelText(/Save as a draft/))
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
  })
  it("loads the stored attribute values when editing an item", async () => {
    serve({ defs: [def({ id: "a1", label: "Colour" })], full: item({ attributes: [{ definitionId: "a1", code: "colour", label: "Colour", type: "TEXT", value: "Red" }] }) })
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Edit Cement/ }))
    const dlg = within(await screen.findByRole("dialog"))
    await waitFor(() => expect((dlg.getByLabelText("Colour") as HTMLInputElement).value).toBe("Red"))
    fireEvent.change(dlg.getByLabelText("Colour"), { target: { value: "Blue" } })
    fireEvent.click(dlg.getByRole("button", { name: "Save changes" }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][1].attributes).toEqual({ a1: "Blue" })
  })
  it("shows a server message about an attribute on the form", async () => {
    serve({ page: pageOf([]), defs: [def({ id: "a1", label: "Colour" })] })
    post.mockImplementation(() => fail(400, "Colour: at most 5 characters."))
    wrap(<ItemsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add item/ }))
    const dlg = within(await screen.findByRole("dialog"))
    await dlg.findByLabelText("Colour")
    fireEvent.change(dlg.getByLabelText(/Item name/), { target: { value: "Paint" } })
    fireEvent.change(dlg.getByLabelText(/Default price/), { target: { value: "10" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create item" }))
    expect(await dlg.findByText("Colour: at most 5 characters.")).toBeTruthy()
  })
})

describe("units panel", () => {
  it("lists the units with their use", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve()
    wrap(<UnitsPanel />)
    expect(await screen.findByText("Kilogram")).toBeTruthy()
    const row = within(screen.getByText("Kilogram").closest("tr") as HTMLElement)
    expect(row.getByText("Weight")).toBeTruthy()
    expect(row.getByText("3")).toBeTruthy()
  })
  it("gives a reader no way to change units", async () => {
    perms = new Set(["CATALOGUE_WRITE"])
    serve()
    wrap(<UnitsPanel />)
    await screen.findByText("Kilogram")
    expect(screen.queryByRole("button", { name: /Add unit/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /Edit kg/ })).toBeNull()
  })
  it("checks the form, then creates a unit", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve()
    wrap(<UnitsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add unit/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.click(dlg.getByRole("button", { name: "Create unit" }))
    expect(await dlg.findByText("The unit code is required")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(dlg.getByLabelText(/Code/), { target: { value: "bag" } })
    fireEvent.change(dlg.getByLabelText("Decimals"), { target: { value: "0" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create unit" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0]).toEqual(["/api/v1/catalogue/units", { code: "bag", name: undefined, kind: "COUNT", decimals: 0, active: true }])
  })
  it("puts a duplicate code on the code field", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve()
    post.mockImplementation(() => fail(409, "A unit with the code 'kg' already exists."))
    wrap(<UnitsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add unit/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/Code/), { target: { value: "kg" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create unit" }))
    expect(await dlg.findByText("A unit with the code 'kg' already exists.")).toBeTruthy()
  })
  it("does not let the code of an existing unit be edited", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve()
    wrap(<UnitsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Edit kg/ }))
    const dlg = within(await screen.findByRole("dialog"))
    expect((dlg.getByLabelText(/Code/) as HTMLInputElement).readOnly).toBe(true)
    fireEvent.change(dlg.getByLabelText("Name"), { target: { value: "Kilo" } })
    fireEvent.click(dlg.getByRole("button", { name: "Save changes" }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe("/api/v1/catalogue/units/u2")
    expect(put.mock.calls[0][1]).toMatchObject({ name: "Kilo", decimals: 3 })
  })
  it("explains that a unit in use is switched off, not deleted", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve()
    del.mockImplementation(() => fail(409, "This unit is used by 3 items. Switch it off instead of deleting it."))
    wrap(<UnitsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Delete each/ }))
    const dlg = within(await screen.findByRole("dialog"))
    expect(dlg.getByText(/Switch it off instead of deleting it, so those items keep their unit/)).toBeTruthy()
    fireEvent.click(dlg.getByRole("button", { name: "Yes, Delete" }))
    expect(await dlg.findByText("This unit is used by 3 items. Switch it off instead of deleting it.")).toBeTruthy()
  })
})

describe("attributes panel", () => {
  it("lists attributes with where they apply", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve({ defs: [def({ id: "g", label: "Note" }), def({ id: "b", label: "Grade", code: "grade", type: "LIST", options: ["A"], categoryId: "b", categoryPath: "Building › Cement", required: true, valueCount: 4 })] })
    wrap(<AttributesPanel />)
    const row = within((await screen.findByText("Grade")).closest("tr") as HTMLElement)
    expect(row.getByText("Building › Cement")).toBeTruthy()
    expect(row.getByText("Pick from a list")).toBeTruthy()
    expect(row.getByText("Yes")).toBeTruthy()
    expect(within(screen.getByText("Note").closest("tr") as HTMLElement).getByText("All items")).toBeTruthy()
  })
  it("needs options for a list, then sends the whole definition", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve({ cats: [cat({ id: "b", name: "Building" })] })
    wrap(<AttributesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add attribute/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/^Name/), { target: { value: "Grade" } })
    fireEvent.change(dlg.getByLabelText(/^Code/), { target: { value: "grade" } })
    fireEvent.change(dlg.getByLabelText("Type"), { target: { value: "LIST" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create attribute" }))
    expect(await dlg.findByText("Add at least one option")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(dlg.getByLabelText(/Options/), { target: { value: "Standard\nPremium" } })
    fireEvent.change(dlg.getByLabelText("Applies to"), { target: { value: "b" } })
    fireEvent.click(dlg.getByLabelText(/Required/))
    fireEvent.click(dlg.getByRole("button", { name: "Create attribute" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ code: "grade", label: "Grade", type: "LIST", options: ["Standard", "Premium"], categoryId: "b", required: true, active: true })
  })
  it("fixes the code, type and category of an existing attribute", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve({ defs: [def({ label: "Colour" })] })
    wrap(<AttributesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Edit Colour/ }))
    const dlg = within(await screen.findByRole("dialog"))
    expect((dlg.getByLabelText(/^Code/) as HTMLInputElement).readOnly).toBe(true)
    expect((dlg.getByLabelText("Type") as HTMLSelectElement).disabled).toBe(true)
    expect((dlg.getByLabelText("Applies to") as HTMLSelectElement).disabled).toBe(true)
  })
  it("shows a duplicate code from the server on the code field", async () => {
    perms = new Set(["CATALOGUE_ADMIN"])
    serve()
    post.mockImplementation(() => fail(409, "An attribute with the code 'colour' already exists."))
    wrap(<AttributesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add attribute/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/^Name/), { target: { value: "Colour" } })
    fireEvent.change(dlg.getByLabelText(/^Code/), { target: { value: "colour" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create attribute" }))
    expect(await dlg.findByText("An attribute with the code 'colour' already exists.")).toBeTruthy()
  })
  it("gives a reader no way to change attributes", async () => {
    perms = new Set(["CATALOGUE_WRITE"])
    serve({ defs: [def({ label: "Colour" })] })
    wrap(<AttributesPanel />)
    await screen.findByText("Colour")
    expect(screen.queryByRole("button", { name: /Add attribute/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /Edit Colour/ })).toBeNull()
  })
})

describe("categories panel", () => {
  it("shows sub-categories indented under their parent", async () => {
    serve({ cats: [cat({ id: "b", name: "Building" }), cat({ id: "c", name: "Cement", parentId: "b", depth: 2, path: "Building › Cement", itemCount: 0 })] })
    wrap(<CategoriesPanel />)
    await screen.findByText("Cement")
    expect(screen.getByText("Building").closest("[data-depth]")?.getAttribute("data-depth")).toBe("1")
    expect(screen.getByText("Cement").closest("[data-depth]")?.getAttribute("data-depth")).toBe("2")
  })
  it("adds a sub-category under the chosen parent", async () => {
    serve({ cats: [cat({ id: "b", name: "Building" })] })
    wrap(<CategoriesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Add a sub-category to Building" }))
    const dlg = within(await screen.findByRole("dialog"))
    expect((dlg.getByLabelText(/Inside/) as HTMLSelectElement).value).toBe("b")
    fireEvent.change(dlg.getByLabelText(/Category name/), { target: { value: "Cement" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create category" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ name: "Cement", parentId: "b" })
  })
  it("does not offer a category or its own sub-categories as its parent", async () => {
    serve({ cats: [cat({ id: "b", name: "Building" }), cat({ id: "c", name: "Cement", parentId: "b", depth: 2 }), cat({ id: "t", name: "Tools" })] })
    wrap(<CategoriesPanel />)
    await screen.findByText("Cement")
    fireEvent.click(screen.getAllByRole("button", { name: "" })[0])        // Building's menu
    fireEvent.click(await screen.findByText("Edit category"))
    const dlg = within(await screen.findByRole("dialog"))
    const options = [...(dlg.getByLabelText(/Inside/) as HTMLSelectElement).options].map(o => o.value)
    expect(options).toEqual(["", "t"])
  })
  it("sends no parent for a top-level category", async () => {
    serve()
    wrap(<CategoriesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add category/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.change(dlg.getByLabelText(/Category name/), { target: { value: "Tools" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create category" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].parentId).toBeUndefined()
  })

  it("lists categories with item counts", async () => {
    serve({ cats: [cat({ itemCount: 1 }), cat({ id: "c2", name: "Tools", itemCount: 0 })] })
    wrap(<CategoriesPanel />)
    expect(await screen.findByText("Building")).toBeTruthy()
    expect(screen.getByText("1 item")).toBeTruthy()
    expect(screen.getByText("0 items")).toBeTruthy()
  })
  it("checks the name and shows a duplicate from the server on the field", async () => {
    serve()
    post.mockImplementation(() => fail(409, "A category named 'Building' already exists."))
    wrap(<CategoriesPanel />)
    fireEvent.click(await screen.findByRole("button", { name: /Add category/ }))
    const dlg = within(await screen.findByRole("dialog"))
    fireEvent.click(dlg.getByRole("button", { name: "Create category" }))
    expect(await dlg.findByText("Category name is required")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(dlg.getByLabelText(/Category name/), { target: { value: "Building" } })
    fireEvent.click(dlg.getByRole("button", { name: "Create category" }))
    expect(await dlg.findByText("A category named 'Building' already exists.")).toBeTruthy()
  })
  it("explains that a category with items cannot be deleted", async () => {
    serve({ cats: [cat({ itemCount: 3 })] })
    del.mockImplementation(() => fail(409, "This category still has 3 items. Move or delete them first."))
    wrap(<CategoriesPanel />)
    await screen.findByText("Building")
    fireEvent.click(screen.getByRole("button", { name: "" }))   // the category's menu button
    fireEvent.click(await screen.findByText("Delete category"))
    const dlg = within(await screen.findByRole("dialog"))
    expect(dlg.getByText(/Move or delete them first, then try again/)).toBeTruthy()
    fireEvent.click(dlg.getByRole("button", { name: "Yes, Delete" }))
    expect(await dlg.findByText("This category still has 3 items. Move or delete them first.")).toBeTruthy()
  })
})
