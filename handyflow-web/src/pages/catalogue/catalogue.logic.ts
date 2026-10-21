// Catalogue items and categories: types, labels, the checks the server also makes (so mistakes show before saving), and request bodies. Pure functions.

export type ItemStatus = "DRAFT" | "ACTIVE" | "SUSPENDED" | "DISCONTINUED" | "ARCHIVED"
export type ItemType = "PRODUCT" | "SERVICE" | "LABOUR" | "SUBSCRIPTION" | "PACKAGE" | "RENTAL" | "CONSUMABLE" | "OTHER"

export const ITEM_STATUSES: ItemStatus[] = ["DRAFT", "ACTIVE", "SUSPENDED", "DISCONTINUED", "ARCHIVED"]
export const STATUS_LABEL: Record<ItemStatus, string> = { DRAFT: "Draft", ACTIVE: "Active", SUSPENDED: "Suspended", DISCONTINUED: "Discontinued", ARCHIVED: "Archived" }
export const STATUS_TONE: Record<ItemStatus, { fg: string; bg: string }> = {
  DRAFT: { fg: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)" },
  ACTIVE: { fg: "var(--hf-success-text)", bg: "var(--hf-success-soft)" },
  SUSPENDED: { fg: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
  DISCONTINUED: { fg: "var(--hf-orange-text)", bg: "var(--hf-orange-soft)" },
  ARCHIVED: { fg: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
}
export const ITEM_TYPES: { id: ItemType; label: string }[] = [
  { id: "PRODUCT", label: "Product" }, { id: "SERVICE", label: "Service" }, { id: "LABOUR", label: "Labour" }, { id: "SUBSCRIPTION", label: "Subscription" },
  { id: "PACKAGE", label: "Package" }, { id: "RENTAL", label: "Rental" }, { id: "CONSUMABLE", label: "Consumable" }, { id: "OTHER", label: "Other" },
]
export const typeLabel = (t: string) => ITEM_TYPES.find(x => x.id === t)?.label ?? t

export interface Category {
  id: string; name: string; description: string | null; sortOrder: number; itemCount: number | null
  parentId: string | null; path: string; depth: number
}
export type UnitKind = "COUNT" | "LENGTH" | "AREA" | "VOLUME" | "WEIGHT" | "TIME" | "OTHER"
export interface Unit { id: string; code: string; name: string; kind: UnitKind; decimals: number; active: boolean; itemCount: number | null }
export type AttributeType = "TEXT" | "NUMBER" | "BOOLEAN" | "DATE" | "LIST"
export interface AttributeDef {
  id: string; categoryId: string | null; categoryPath: string | null; code: string; label: string; type: AttributeType; required: boolean
  options: string[]; min: number | null; max: number | null; maxLength: number | null; sortOrder: number; active: boolean; valueCount: number | null
}
export interface AttributeValue { definitionId: string; code: string; label: string; type: AttributeType; value: string }
export interface CatalogueItem {
  id: string; name: string; description: string | null; sku: string | null; barcode: string | null; brand: string | null; itemType: ItemType
  status: ItemStatus; statusReason: string | null; statusChangedAt: string | null; unit: string; defaultPrice: number; vatRate: number; vatExempt: boolean
  categoryId: string | null; categoryName: string | null; costPrice: number | null; marginPercent: number | null; version: number; createdAt: string; updatedAt: string
  /** Attribute values; only present when a single item was loaded. */
  attributes?: AttributeValue[] | null
  /** Set on a variant: the item it belongs to and its "Large / Red" label. variantCount is how many variants a parent has. */
  parentId?: string | null; variantLabel?: string | null; variantCount?: number
}
export interface PageOf<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }
export interface HistoryLine { id: string; action: string; detail: string | null; actorId: string | null; at: string }

/** The moves the server allows from each state (mirrors the backend rules). */
export const STATUS_MOVES: Record<ItemStatus, ItemStatus[]> = {
  DRAFT: ["ACTIVE", "ARCHIVED"], ACTIVE: ["SUSPENDED", "DISCONTINUED", "ARCHIVED"], SUSPENDED: ["ACTIVE", "DISCONTINUED", "ARCHIVED"],
  DISCONTINUED: ["ACTIVE", "ARCHIVED"], ARCHIVED: ["DRAFT"],
}
export const moveNeedsReason = (to: ItemStatus) => to === "SUSPENDED" || to === "DISCONTINUED" || to === "ARCHIVED"
export const moveNeedsArchivePermission = (to: ItemStatus) => to === "DISCONTINUED" || to === "ARCHIVED"
/** Moves this user may make: archive-type moves need the archive permission. */
export const allowedMoves = (from: ItemStatus, canArchive: boolean): ItemStatus[] => STATUS_MOVES[from].filter(t => canArchive || !moveNeedsArchivePermission(t))
export const moveLabel = (to: ItemStatus): string => ({ DRAFT: "Restore as draft", ACTIVE: "Activate", SUSPENDED: "Suspend", DISCONTINUED: "Discontinue", ARCHIVED: "Archive" }[to])

/** Why a status change cannot be sent yet, or null. */
export function statusProblem(from: ItemStatus, to: ItemStatus, reason: string): string | null {
  if (from === to) return `The item is already ${STATUS_LABEL[to].toLowerCase()}.`
  if (!STATUS_MOVES[from].includes(to)) return `A ${STATUS_LABEL[from].toLowerCase()} item cannot become ${STATUS_LABEL[to].toLowerCase()}.`
  const r = reason.trim()
  if (moveNeedsReason(to) && r.length < 3) return "Say why (at least 3 characters)."
  if (r.length > 255) return "The reason can be at most 255 characters."
  return null
}

// ── Item form ──────────────────────────────────────────────────────────────
export interface ItemForm {
  name: string; description: string; sku: string; barcode: string; brand: string; itemType: ItemType; categoryId: string; unit: string
  defaultPrice: string; costPrice: string; vatRate: string; vatExempt: boolean; draft: boolean
  /** Attribute values by definition id, as typed. */
  attributes: Record<string, string>
}
export const EMPTY_ITEM_FORM: ItemForm = {
  name: "", description: "", sku: "", barcode: "", brand: "", itemType: "PRODUCT", categoryId: "", unit: "Each",
  defaultPrice: "", costPrice: "", vatRate: "15", vatExempt: false, draft: false, attributes: {},
}
export const toItemForm = (i: CatalogueItem): ItemForm => ({
  name: i.name, description: i.description ?? "", sku: i.sku ?? "", barcode: i.barcode ?? "", brand: i.brand ?? "", itemType: i.itemType, categoryId: i.categoryId ?? "",
  unit: i.unit, defaultPrice: String(i.defaultPrice), costPrice: i.costPrice == null ? "" : String(i.costPrice), vatRate: String(i.vatRate), vatExempt: i.vatExempt, draft: false,
  attributes: Object.fromEntries((i.attributes ?? []).map(a => [a.definitionId, a.value])),
})

const SKU_RE = /^[A-Z0-9][A-Z0-9._/-]*$/, BARCODE_RE = /^[A-Za-z0-9][A-Za-z0-9._/-]*$/, MONEY_RE = /^\d+(\.\d{1,2})?$/
const MAX_MONEY = 9_999_999_999_999.99

function moneyProblem(what: string, raw: string, required: boolean): string | null {
  const v = raw.trim().replace(/\s/g, "")
  if (!v) return required ? `${what} is required` : null
  if (!MONEY_RE.test(v)) return /^-/.test(v) ? `${what} cannot be negative` : `${what} must be a number with at most 2 decimals`
  return Number(v) > MAX_MONEY ? `${what} is too large` : null
}

/** Field-by-field problems with the form (empty object when it can be saved). `showCost` is false when the user cannot see or set the cost. */
export function itemProblems(f: ItemForm, showCost: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  const name = f.name.trim()
  if (!name) e.name = "Item name is required"
  else if (name.length > 255) e.name = "The name can be at most 255 characters"
  if (!f.unit.trim()) e.unit = "Unit is required"
  const price = moneyProblem("Price", f.defaultPrice, true)
  if (price) e.defaultPrice = price
  if (showCost) { const cost = moneyProblem("Cost", f.costPrice, false); if (cost) e.costPrice = cost }
  const sku = f.sku.trim().toUpperCase()
  if (sku) { if (sku.length > 50) e.sku = "The SKU can be at most 50 characters"; else if (!SKU_RE.test(sku)) e.sku = "The SKU can only use letters, numbers and . _ / - (no spaces)" }
  const bc = f.barcode.trim()
  if (bc) { if (bc.length > 100) e.barcode = "The barcode can be at most 100 characters"; else if (!BARCODE_RE.test(bc)) e.barcode = "The barcode can only use letters, numbers and . _ / - (no spaces)" }
  if (f.brand.trim().length > 100) e.brand = "The brand can be at most 100 characters"
  if (!f.vatExempt) {
    const v = Number(f.vatRate)
    if (f.vatRate.trim() === "" || !Number.isFinite(v) || v < 0 || v > 100) e.vatRate = "VAT must be between 0 and 100"
  }
  return e
}

/** Request body. Cost is only sent when the user may set it, so an edit never blanks it by accident. */
export function itemBody(f: ItemForm, opts: { canWriteCost: boolean; version?: number; creating?: boolean; attributeIds?: string[] }) {
  return {
    name: f.name.trim(),
    description: f.description.trim() || undefined,
    categoryId: f.categoryId || undefined,
    unit: f.unit.trim(),
    defaultPrice: Number(f.defaultPrice.trim().replace(/\s/g, "")),
    vatRate: f.vatExempt ? 0 : Number(f.vatRate),
    vatExempt: f.vatExempt,
    sku: f.sku.trim().toUpperCase() || undefined,
    barcode: f.barcode.trim() || undefined,
    brand: f.brand.trim() || undefined,
    itemType: f.itemType,
    costPrice: opts.canWriteCost && f.costPrice.trim() ? Number(f.costPrice.trim().replace(/\s/g, "")) : undefined,
    status: opts.creating && f.draft ? "DRAFT" : undefined,
    version: opts.version,
    // The full set for the attributes that apply (blank clears one); left out when none apply, so stored values are not touched.
    attributes: opts.attributeIds && opts.attributeIds.length ? Object.fromEntries(opts.attributeIds.map(id => [id, (f.attributes[id] ?? "").trim()])) : undefined,
  }
}

// ── List filters ───────────────────────────────────────────────────────────
export interface Filters { q: string; categoryId: string; status: "" | ItemStatus; type: "" | ItemType; sort: "name" | "sku" | "price" | "status" | "type" | "updated"; desc: boolean; page: number }
export const EMPTY_FILTERS: Filters = { q: "", categoryId: "", status: "", type: "", sort: "name", desc: false, page: 0 }
export const PAGE_SIZE = 25
export function listParams(f: Filters): Record<string, string | number | boolean> {
  const p: Record<string, string | number | boolean> = { page: f.page, size: PAGE_SIZE, sort: f.sort, desc: f.desc }
  if (f.q.trim()) p.q = f.q.trim()
  if (f.categoryId) p.categoryId = f.categoryId
  if (f.status) p.status = f.status
  if (f.type) p.type = f.type
  return p
}
export const hasFilters = (f: Filters) => !!(f.q.trim() || f.categoryId || f.status || f.type)

// ── Display ────────────────────────────────────────────────────────────────
export const rand = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
export const marginText = (m: number | null | undefined) => (m == null ? "—" : `${m}%`)
export const rangeText = (p: PageOf<unknown>) => (p.totalElements === 0 ? "0 items" : `${p.number * p.size + 1}–${p.number * p.size + p.content.length} of ${p.totalElements}`)

/** The message to show for a failed request: the server's own words where it gave them. */
export function errorMessage(e: unknown, fallback: string): string {
  const data = (e as { response?: { data?: { message?: string; errors?: { field: string; message: string }[] } } } | null)?.response?.data
  return data?.message || fallback
}
/** Field errors the server returned (Bean Validation), keyed by field name. */
export function fieldErrors(e: unknown): Record<string, string> {
  const errs = (e as { response?: { data?: { errors?: { field: string; message: string }[] } } } | null)?.response?.data?.errors
  const out: Record<string, string> = {}
  if (Array.isArray(errs)) for (const x of errs) out[x.field] = x.message
  return out
}
/** Server messages about a duplicate name, SKU or barcode, mapped to the field that caused them. */
export function duplicateField(message: string): "name" | "sku" | "barcode" | null {
  if (/^An item named/.test(message)) return "name"
  if (/SKU/.test(message) && /already uses/.test(message)) return "sku"
  if (/barcode/.test(message) && /already uses/.test(message)) return "barcode"
  return null
}

// ── Category tree ───────────────────────────────────────────────────────────
/** The category and everything below it. */
export function subtreeIds(cats: Category[], rootId: string): string[] {
  const out = [rootId]
  for (let i = 0; i < out.length; i++) for (const c of cats) if (c.parentId === out[i] && !out.includes(c.id)) out.push(c.id)
  return out
}
/** Ids from the category up to the top (nearest first). */
export function selfAndAncestorIds(cats: Category[], id: string | null): string[] {
  const out: string[] = []
  let cur = id
  while (cur && !out.includes(cur)) { out.push(cur); cur = cats.find(c => c.id === cur)?.parentId ?? null }
  return out
}
/** Option text that shows the nesting in a plain <select>. */
export const indented = (c: Category) => `${"\u2003".repeat(Math.max(0, c.depth - 1))}${c.depth > 1 ? "↳ " : ""}${c.name}`
export const MAX_CATEGORY_DEPTH = 5
/** Categories a given category may be moved under: not itself, not its own sub-categories, and not so deep that its subtree passes 5 levels. */
export function parentChoices(cats: Category[], movingId: string | null): Category[] {
  if (!movingId) return cats.filter(c => c.depth < MAX_CATEGORY_DEPTH)
  const below = subtreeIds(cats, movingId)
  const me = cats.find(c => c.id === movingId)
  const height = below.reduce((h, id) => Math.max(h, (cats.find(c => c.id === id)?.depth ?? 1) - (me?.depth ?? 1) + 1), 1)
  return cats.filter(c => !below.includes(c.id) && c.depth + height <= MAX_CATEGORY_DEPTH)
}

// ── Units ───────────────────────────────────────────────────────────────────
export const UNIT_KINDS: { id: UnitKind; label: string }[] = [
  { id: "COUNT", label: "Count" }, { id: "LENGTH", label: "Length" }, { id: "AREA", label: "Area" }, { id: "VOLUME", label: "Volume" },
  { id: "WEIGHT", label: "Weight" }, { id: "TIME", label: "Time" }, { id: "OTHER", label: "Other" },
]
export const kindLabel = (k: string) => UNIT_KINDS.find(x => x.id === k)?.label ?? k
export interface UnitForm { code: string; name: string; kind: UnitKind; decimals: string; active: boolean }
export const EMPTY_UNIT_FORM: UnitForm = { code: "", name: "", kind: "COUNT", decimals: "0", active: true }
export const toUnitForm = (u: Unit): UnitForm => ({ code: u.code, name: u.name, kind: u.kind, decimals: String(u.decimals), active: u.active })
export function unitProblems(f: UnitForm, creating: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  const code = f.code.trim()
  if (creating) { if (!code) e.code = "The unit code is required"; else if (code.length > 50) e.code = "The unit code can be at most 50 characters" }
  if (f.name.trim().length > 60) e.name = "The unit name can be at most 60 characters"
  if (!/^[0-4]$/.test(f.decimals.trim())) e.decimals = "Decimals must be between 0 and 4"
  return e
}
export const unitBody = (f: UnitForm) => ({ code: f.code.trim(), name: f.name.trim() || undefined, kind: f.kind, decimals: Number(f.decimals), active: f.active })
/** The unit to show as selected: the list's own spelling of the code, ignoring case. */
export const matchUnit = (units: Unit[], code: string): Unit | undefined => units.find(u => u.code.toLowerCase() === code.trim().toLowerCase())

// ── Attributes ─────────────────────────────────────────────────────────────
export const ATTRIBUTE_TYPES: { id: AttributeType; label: string }[] = [
  { id: "TEXT", label: "Text" }, { id: "NUMBER", label: "Number" }, { id: "BOOLEAN", label: "Yes / no" }, { id: "DATE", label: "Date" }, { id: "LIST", label: "Pick from a list" },
]
export const attributeTypeLabel = (t: string) => ATTRIBUTE_TYPES.find(x => x.id === t)?.label ?? t

/** The active attributes that apply to an item in this category: its own, those of the categories above it, and the global ones. */
export function applicableAttributes(defs: AttributeDef[], cats: Category[], categoryId: string | null): AttributeDef[] {
  const chain = selfAndAncestorIds(cats, categoryId || null)
  return defs.filter(d => d.active && (d.categoryId == null || chain.includes(d.categoryId))).sort((a, b) => a.sortOrder - b.sortOrder || a.label.localeCompare(b.label))
}

/** One value against its definition (mirrors the server). Null when fine or empty; whether empty is allowed is decided separately. */
export function attributeValueProblem(d: AttributeDef, raw: string): string | null {
  const v = (raw ?? "").trim()
  if (!v) return null
  switch (d.type) {
    case "TEXT": { const lim = d.maxLength ?? 500; return v.length > lim ? `${d.label}: at most ${lim} characters` : null }
    case "NUMBER": {
      const n = Number(v.replace(",", "."))
      if (!Number.isFinite(n) || !/^-?\d+([.,]\d+)?$/.test(v)) return `${d.label}: "${v}" is not a number`
      if (d.min != null && n < d.min) return `${d.label}: must be at least ${d.min}`
      if (d.max != null && n > d.max) return `${d.label}: must be at most ${d.max}`
      return null
    }
    case "BOOLEAN": return /^(true|false|yes|no)$/i.test(v) ? null : `${d.label}: choose yes or no`
    case "DATE": { const ok = /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(v)) && new Date(v).toISOString().slice(0, 10) === v; return ok ? null : `${d.label}: use the date format YYYY-MM-DD` }
    case "LIST": return d.options.some(o => o.toLowerCase() === v.toLowerCase()) ? null : `${d.label}: "${v}" is not one of the allowed options`
  }
}
/** Problems with the attribute values of an item form, keyed by `attr:<definition id>`. Required ones only count for items that are not drafts. */
export function attributeProblems(defs: AttributeDef[], values: Record<string, string>, enforceRequired: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  for (const d of defs) {
    const v = (values[d.id] ?? "").trim()
    if (!v) { if (d.required && enforceRequired) e[`attr:${d.id}`] = `${d.label} is required`; continue }
    const p = attributeValueProblem(d, v)
    if (p) e[`attr:${d.id}`] = p.replace(`${d.label}: `, "").replace(/^./, c => c.toUpperCase())
  }
  return e
}

export interface AttributeForm {
  categoryId: string; code: string; label: string; type: AttributeType; required: boolean; options: string; min: string; max: string; maxLength: string; sortOrder: string; active: boolean
}
export const EMPTY_ATTRIBUTE_FORM: AttributeForm = { categoryId: "", code: "", label: "", type: "TEXT", required: false, options: "", min: "", max: "", maxLength: "", sortOrder: "0", active: true }
export const toAttributeForm = (d: AttributeDef): AttributeForm => ({
  categoryId: d.categoryId ?? "", code: d.code, label: d.label, type: d.type, required: d.required, options: d.options.join("\n"),
  min: d.min == null ? "" : String(d.min), max: d.max == null ? "" : String(d.max), maxLength: d.maxLength == null ? "" : String(d.maxLength), sortOrder: String(d.sortOrder), active: d.active,
})
const optionList = (raw: string) => raw.split("\n").map(o => o.trim()).filter(Boolean)
export function attributeFormProblems(f: AttributeForm, creating: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  if (creating) {
    const code = f.code.trim()
    if (!code) e.code = "The attribute code is required"
    else if (code.length > 40) e.code = "The attribute code can be at most 40 characters"
    else if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(code)) e.code = "Start with a letter; use only letters, numbers and underscores"
  }
  if (!f.label.trim()) e.label = "The attribute name is required"; else if (f.label.trim().length > 60) e.label = "The attribute name can be at most 60 characters"
  if (f.type === "LIST") {
    const opts = optionList(f.options)
    if (opts.length === 0) e.options = "Add at least one option"
    else if (new Set(opts.map(o => o.toLowerCase())).size !== opts.length) e.options = "An option is listed twice"
    else if (opts.length > 50) e.options = "At most 50 options"
    else if (opts.some(o => o.length > 60)) e.options = "An option can be at most 60 characters"
  }
  if (f.type === "NUMBER") {
    const bad = (x: string) => x.trim() !== "" && !Number.isFinite(Number(x))
    if (bad(f.min)) e.min = "Enter a number"
    if (bad(f.max)) e.max = "Enter a number"
    if (!e.min && !e.max && f.min.trim() !== "" && f.max.trim() !== "" && Number(f.min) > Number(f.max)) e.max = "The maximum cannot be below the minimum"
  }
  if (f.type === "TEXT" && f.maxLength.trim() !== "" && !(/^\d+$/.test(f.maxLength.trim()) && Number(f.maxLength) >= 1 && Number(f.maxLength) <= 500)) e.maxLength = "Between 1 and 500"
  if (!/^-?\d+$/.test(f.sortOrder.trim() || "0")) e.sortOrder = "Enter a whole number"
  return e
}
export const attributeBody = (f: AttributeForm) => ({
  categoryId: f.categoryId || undefined, code: f.code.trim(), label: f.label.trim(), type: f.type, required: f.required,
  options: f.type === "LIST" ? optionList(f.options) : undefined,
  min: f.type === "NUMBER" && f.min.trim() !== "" ? Number(f.min) : undefined,
  max: f.type === "NUMBER" && f.max.trim() !== "" ? Number(f.max) : undefined,
  maxLength: f.type === "TEXT" && f.maxLength.trim() !== "" ? Number(f.maxLength) : undefined,
  sortOrder: Number(f.sortOrder.trim() || "0"), active: f.active,
})

// ── Pricing ──────────────────────────────────────────────────────────────────
export type PriceChangeStatus = "PENDING" | "SCHEDULED" | "APPLIED" | "REJECTED" | "CANCELLED"
export interface PriceList { id: string; code: string; name: string; defaultList: boolean; active: boolean; sortOrder: number }
export interface PriceChange {
  id: string; itemId: string; itemName: string | null; priceListId: string; priceListName: string | null
  oldPrice: number | null; newPrice: number; changePercent: number | null; effectiveFrom: string; reason: string | null
  status: PriceChangeStatus; proposedByMe: boolean; proposedAt: string; decidedAt: string | null; decisionNote: string | null; appliedAt: string | null
}
export interface ListPrice { priceListId: string; code: string; name: string; defaultList: boolean; price: number | null; priceInclVat: number | null; marginPercent: number | null; open: PriceChange[] }
export interface ItemPricing {
  itemId: string; itemName: string; unit: string; minPrice: number | null; costPrice: number | null; vatRate: number; vatExempt: boolean
  approvalRequired: boolean; lists: ListPrice[]
}

export const PRICE_STATUS_LABEL: Record<PriceChangeStatus, string> = { PENDING: "Waiting for approval", SCHEDULED: "Scheduled", APPLIED: "In force", REJECTED: "Rejected", CANCELLED: "Cancelled" }

/** "R 100.00 → R 120.00 (+20%)" for a change; "R 120.00" when there was no earlier price. */
export function changeText(c: Pick<PriceChange, "oldPrice" | "newPrice" | "changePercent">): string {
  const pct = c.changePercent == null ? "" : ` (${c.changePercent > 0 ? "+" : ""}${c.changePercent}%)`
  return c.oldPrice == null ? rand(c.newPrice) : `${rand(c.oldPrice)} → ${rand(c.newPrice)}${pct}`
}

export const todayIso = (now: Date = new Date()) => `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`

export interface PriceForm { priceListId: string; newPrice: string; effectiveFrom: string; reason: string }
export const EMPTY_PRICE_FORM: PriceForm = { priceListId: "", newPrice: "", effectiveFrom: "", reason: "" }

/** Checks a proposal before it is sent. The server repeats every rule. */
export function priceProblems(f: PriceForm, ctx: { minPrice: number | null; current: number | null; approvalRequired: boolean; today?: string }): Record<string, string> {
  const e: Record<string, string> = {}
  const raw = f.newPrice.trim().replace(",", ".")
  if (!f.priceListId) e.priceListId = "Choose a price list."
  if (!raw) e.newPrice = "Enter the new price."
  else if (!/^\d+(\.\d{1,2})?$/.test(raw)) e.newPrice = "Enter an amount like 120 or 120.50."
  else {
    const n = Number(raw)
    if (ctx.minPrice != null && n < ctx.minPrice) e.newPrice = `The price cannot be below the minimum price of ${rand(ctx.minPrice)}.`
    else if (ctx.current != null && n === ctx.current && (!f.effectiveFrom || f.effectiveFrom <= (ctx.today ?? todayIso()))) e.newPrice = "That is already the price."
  }
  if (f.effectiveFrom && f.effectiveFrom < (ctx.today ?? todayIso())) e.effectiveFrom = "The effective date cannot be in the past."
  if (ctx.approvalRequired && f.reason.trim().length < 3) e.reason = "Say why the price is changing (at least 3 characters)."
  if (f.reason.trim().length > 255) e.reason = "The reason can be at most 255 characters."
  return e
}

export const priceBody = (f: PriceForm) => ({
  priceListId: f.priceListId, newPrice: Number(f.newPrice.trim().replace(",", ".")),
  effectiveFrom: f.effectiveFrom || undefined, reason: f.reason.trim() || undefined,
})

/** The minimum price field: empty clears it. */
export function minPriceProblem(raw: string, current: number | null): string | null {
  const s = raw.trim().replace(",", ".")
  if (!s) return null
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return "Enter an amount like 80 or 80.50."
  if (current != null && Number(s) > current) return `The current price (${rand(current)}) is already below that. Change the price first.`
  return null
}
export const minPriceBody = (raw: string) => ({ minPrice: raw.trim() ? Number(raw.trim().replace(",", ".")) : null })

export interface PriceListForm { code: string; name: string; sortOrder: string; active: boolean }
export const EMPTY_PRICE_LIST_FORM: PriceListForm = { code: "", name: "", sortOrder: "10", active: true }
export const toPriceListForm = (l: PriceList): PriceListForm => ({ code: l.code, name: l.name, sortOrder: String(l.sortOrder), active: l.active })
export function priceListProblems(f: PriceListForm, creating: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  if (creating) {
    const c = f.code.trim()
    if (!c) e.code = "The code is required."
    else if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(c)) e.code = "Start with a letter; use only letters, numbers and underscores."
    else if (c.length > 30) e.code = "The code can be at most 30 characters."
  }
  if (!f.name.trim()) e.name = "The name is required."
  else if (f.name.trim().length > 60) e.name = "The name can be at most 60 characters."
  if (f.sortOrder.trim() && !/^-?\d+$/.test(f.sortOrder.trim())) e.sortOrder = "Whole number."
  return e
}
export const priceListBody = (f: PriceListForm) => ({ code: f.code.trim(), name: f.name.trim(), sortOrder: f.sortOrder.trim() ? Number(f.sortOrder) : undefined, active: f.active })

// ── Variants ─────────────────────────────────────────────────────────────────
export interface VariantOption { name: string; values: string[] }
export interface VariantRow { id: string; name: string; label: string; values: string[]; sku: string | null; barcode: string | null; status: ItemStatus; defaultPrice: number; costPrice: number | null; version: number }
export interface VariantsView { parentId: string; parentName: string; parentSku: string | null; parentPrice: number; options: VariantOption[]; variants: VariantRow[] }
export const MAX_VARIANT_OPTIONS = 3, MAX_OPTION_VALUES = 20, MAX_VARIANTS_PER_REQUEST = 100, MAX_VARIANTS_PER_ITEM = 200

/** "S, M, L" → ["S","M","L"]; blanks dropped; repeats (ignoring case) dropped. */
export function parseValues(text: string): string[] {
  const out: string[] = []
  for (const part of text.split(/[,;\n]/)) {
    const v = part.trim()
    if (v && !out.some(o => o.toLowerCase() === v.toLowerCase())) out.push(v)
  }
  return out
}

/** One value per option, every combination, first option changing slowest. */
export function combinations(valuesPerOption: string[][]): string[][] {
  return valuesPerOption.reduce<string[][]>((acc, values) => acc.flatMap(prefix => values.map(v => [...prefix, v])), [[]])
}

export const comboKey = (values: string[]) => values.map(v => v.trim().toLowerCase()).join("|")
export const comboLabel = (values: string[]) => values.join(" / ")

/** Parent SKU plus three letters of each value: CEM-50 + Large + Red = CEM-50-LAR-RED. */
export function suggestVariantSku(parentSku: string | null | undefined, values: string[]): string {
  if (!parentSku || !parentSku.trim()) return ""
  const parts = values.map(v => { const p = v.toUpperCase().replace(/[^A-Z0-9]/g, ""); return (p || "X").slice(0, 3) })
  return [parentSku.trim().toUpperCase(), ...parts].join("-").slice(0, 50)
}

export interface OptionDraft { name: string; values: string }
export interface MatrixRow { key: string; values: string[]; include: boolean; sku: string; barcode: string; price: string }

/** Checks the option drafts before a matrix is built. Returns a message or null. */
export function optionProblem(opts: OptionDraft[], lockedNames?: string[]): string | null {
  if (opts.length === 0) return "Add at least one option, for example Size."
  if (opts.length > MAX_VARIANT_OPTIONS) return `An item can have at most ${MAX_VARIANT_OPTIONS} options.`
  const names = opts.map(o => o.name.trim().toLowerCase())
  if (names.some(n => !n)) return "Each option needs a name, for example Size or Colour."
  if (new Set(names).size !== names.length) return "Two options have the same name."
  if (lockedNames && lockedNames.length > 0 && (lockedNames.length !== names.length || lockedNames.some((n, i) => n.toLowerCase() !== names[i])))
    return `This item already has the options ${lockedNames.join(", ")}. Use the same options.`
  for (const o of opts) {
    const vs = parseValues(o.values)
    if (vs.length === 0) return `The option '${o.name.trim()}' needs at least one value.`
    if (vs.length > MAX_OPTION_VALUES) return `The option '${o.name.trim()}' can have at most ${MAX_OPTION_VALUES} values.`
    if (vs.some(v => /[|=]/.test(v))) return "A value cannot contain | or =."
    if (vs.some(v => v.length > 50)) return "A value can be at most 50 characters."
  }
  const total = opts.reduce((n, o) => n * parseValues(o.values).length, 1)
  if (total > MAX_VARIANTS_PER_REQUEST) return `That makes ${total} variants. Use fewer values (at most ${MAX_VARIANTS_PER_REQUEST} at a time).`
  return null
}

/** The rows of the matrix for the options, leaving out combinations that already exist. */
export function buildMatrix(opts: OptionDraft[], parentSku: string | null, parentPrice: number, existing: VariantRow[]): MatrixRow[] {
  const have = new Set(existing.map(v => comboKey(v.values)))
  return combinations(opts.map(o => parseValues(o.values))).filter(v => !have.has(comboKey(v))).map(values => (
    { key: comboKey(values), values, include: true, sku: suggestVariantSku(parentSku, values), barcode: "", price: String(parentPrice) }))
}

/** Problems per matrix row (keyed by row key + field) for the rows that are ticked. */
export function matrixProblems(rows: MatrixRow[]): Record<string, string> {
  const e: Record<string, string> = {}
  const picked = rows.filter(r => r.include)
  if (picked.length === 0) { e.general = "Tick at least one variant to create."; return e }
  const skus = new Map<string, string>(), codes = new Map<string, string>()
  for (const r of picked) {
    const sku = r.sku.trim().toUpperCase(), code = r.barcode.trim().toLowerCase()
    if (sku) {
      if (!SKU_RE.test(sku)) e[`${r.key}:sku`] = "Letters, numbers and . _ / - only."
      else if (sku.length > 50) e[`${r.key}:sku`] = "At most 50 characters."
      else if (skus.has(sku)) e[`${r.key}:sku`] = "Same SKU as another row."
      skus.set(sku, r.key)
    }
    if (code) {
      if (!BARCODE_RE.test(r.barcode.trim())) e[`${r.key}:barcode`] = "Letters, numbers and . _ / - only."
      else if (codes.has(code)) e[`${r.key}:barcode`] = "Same barcode as another row."
      codes.set(code, r.key)
    }
    const p = r.price.trim().replace(",", ".")
    if (!p) e[`${r.key}:price`] = "Required."
    else if (!/^\d+(\.\d{1,2})?$/.test(p)) e[`${r.key}:price`] = "Like 120 or 120.50."
  }
  return e
}

export const variantsBody = (opts: OptionDraft[], rows: MatrixRow[]) => ({
  options: opts.map(o => ({ name: o.name.trim(), values: parseValues(o.values) })),
  variants: rows.filter(r => r.include).map(r => ({
    values: r.values, sku: r.sku.trim() || undefined, barcode: r.barcode.trim() || undefined, defaultPrice: Number(r.price.trim().replace(",", ".")),
  })),
})

export interface VariantEditForm { sku: string; barcode: string; price: string; cost: string }
export const toVariantEditForm = (v: VariantRow): VariantEditForm => ({ sku: v.sku ?? "", barcode: v.barcode ?? "", price: String(v.defaultPrice), cost: v.costPrice == null ? "" : String(v.costPrice) })
export function variantEditProblems(f: VariantEditForm, showCost: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  if (f.sku.trim() && !SKU_RE.test(f.sku.trim().toUpperCase())) e.sku = "The SKU can only use letters, numbers and . _ / - (no spaces)"
  if (f.barcode.trim() && !BARCODE_RE.test(f.barcode.trim())) e.barcode = "The barcode can only use letters, numbers and . _ / - (no spaces)"
  if (!/^\d+(\.\d{1,2})?$/.test(f.price.trim().replace(",", "."))) e.price = "Price must be a number with at most 2 decimals"
  if (showCost && f.cost.trim() && !/^\d+(\.\d{1,2})?$/.test(f.cost.trim().replace(",", "."))) e.cost = "Cost must be a number with at most 2 decimals"
  return e
}
export const variantEditBody = (f: VariantEditForm, version: number, canWriteCost: boolean) => ({
  sku: f.sku.trim() || undefined, barcode: f.barcode.trim() || undefined, defaultPrice: Number(f.price.trim().replace(",", ".")),
  costPrice: canWriteCost && f.cost.trim() ? Number(f.cost.trim().replace(",", ".")) : undefined, version,
})


// ── Dashboard and Product 360 ────────────────────────────────────────────────
export interface DashboardData {
  counts: { items: number; products: number; services: number; variants: number; activeSkus: number; drafts: number; suspended: number; discontinued: number; discontinuedRecently: number; incomplete: number }
  /** Null unless the user deals with prices. */
  prices: { pending: number; scheduled: number } | null
  attention: { awaitingApproval: number; importsWithProblems: number }
  growth: { month: string; total: number }[]
  byCategory: { name: string; count: number }[]
  recentItems: { id: string; name: string; sku: string | null; categoryName: string | null; status: ItemStatus; defaultPrice: number; updatedAt: string; parentId: string | null }[]
  activity: { action: string; detail: string | null; entityType: string; entityId: string; entityName: string | null; at: string }[]
}

export interface ItemOverview {
  item: CatalogueItem; categoryPath: string | null; completeness: { score: number; checks: { code: string; label: string; passed: boolean }[] }
  missingAttributes: string[]; openPriceChanges: number; approvalState: "NONE" | "DRAFT" | "PRICE_CHANGE_PENDING" | "APPROVAL_PENDING"; parentName: string | null
  approvalRequired: boolean; pendingApprovalId: string | null
}

export const APPROVAL_LABEL: Record<ItemOverview["approvalState"], string> = { NONE: "Nothing waiting", DRAFT: "Draft, not yet active", PRICE_CHANGE_PENDING: "Price change awaiting approval", APPROVAL_PENDING: "Waiting for approval to go live" }

/** Everything still to fix on an item, in plain words: failed checks first, then missing required attributes. */
export function outstanding(o: ItemOverview): string[] {
  return [...o.completeness.checks.filter(c => !c.passed).map(c => `${c.label} is missing`), ...o.missingAttributes.map(a => `${a} (required attribute) is missing`)]
}

export const ringTone = (score: number) => (score >= 80 ? "var(--hf-success-text)" : score >= 50 ? "var(--hf-warning-text)" : "var(--hf-danger-text)")

export const actionText = (a: string) => ({
  CREATED: "Created", UPDATED: "Edited", PRICE_CHANGED: "Price changed", STATUS_CHANGED: "Status changed", DELETED: "Deleted",
  PRICE_PROPOSED: "Price change proposed", PRICE_APPROVED: "Price change approved", PRICE_REJECTED: "Price change rejected", PRICE_CANCELLED: "Price change cancelled", SETTING_CHANGED: "Setting changed",
  APPROVAL_REQUESTED: "Submitted for approval", ITEM_APPROVED: "Approved", ITEM_REJECTED: "Rejected", APPROVAL_WITHDRAWN: "Request withdrawn",
  MEDIA_ADDED: "File added", MEDIA_CHANGED: "File changed", MEDIA_REMOVED: "File removed", BUNDLE_CHANGED: "Package parts changed",
  PUBLISHED: "Published", UNPUBLISHED: "Taken off a channel", CHANNEL_CHANGED: "Channel changed",
}[a] ?? a)

/** Share of the largest value, 0–100, for the plain bar charts. */
export const barPercent = (value: number, max: number) => (max <= 0 ? 0 : Math.max(value > 0 ? 3 : 0, Math.round((value / max) * 100)))


// ── Import, export and bulk change ───────────────────────────────────────────
export type ImportMode = "CREATE_ONLY" | "UPDATE_ONLY" | "CREATE_OR_UPDATE"
export const IMPORT_MODES: { id: ImportMode; label: string; hint: string }[] = [
  { id: "CREATE_OR_UPDATE", label: "Add new items and update existing ones", hint: "Items are matched by SKU. Empty cells leave an existing item as it is." },
  { id: "CREATE_ONLY", label: "Add new items only", hint: "A row whose SKU already exists is a problem." },
  { id: "UPDATE_ONLY", label: "Update existing items only", hint: "A row whose SKU does not exist is a problem." },
]
export type RowAction = "CREATE" | "UPDATE" | "UNCHANGED" | "ERROR"
export const ROW_ACTION_LABEL: Record<RowAction, string> = { CREATE: "New", UPDATE: "Update", UNCHANGED: "No change", ERROR: "Problem" }
export interface ImportRow { line: number; key: string; action: RowAction; changes: string[]; errors: string[]; notes: string[] }
export interface ImportPreview { mode: ImportMode; total: number; create: number; update: number; unchanged: number; errors: number; fileProblems: string[]; unknownColumns: string[]; rows: ImportRow[]; truncated: boolean }
export interface ImportResult { jobId: string; total: number; created: number; updated: number; unchanged: number; skipped: number; failed: number; problems: string[] }
export interface ImportJob { id: string; fileName: string | null; mode: ImportMode; totalRows: number; created: number; updated: number; unchanged: number; skipped: number; failed: number; createdAt: string }

export const MAX_IMPORT_CHARS = 2_000_000

/** How many rows an import would write, given whether rows with problems are left out. Null when it cannot go ahead. */
export function importCount(p: ImportPreview | null, skipErrors: boolean): number | null {
  if (!p || p.fileProblems.length > 0) return null
  if (p.errors > 0 && !skipErrors) return null
  const n = p.create + p.update
  return n > 0 ? n : null
}

export const importSummaryText = (p: ImportPreview) =>
  p.fileProblems.length > 0 ? p.fileProblems[0] : `${p.total} row${p.total === 1 ? "" : "s"}: ${p.create} new, ${p.update} to update, ${p.unchanged} unchanged, ${p.errors} with problems.`

export const resultText = (r: ImportResult) =>
  `${r.created} added, ${r.updated} updated, ${r.unchanged} unchanged` + (r.skipped ? `, ${r.skipped} left out` : "") + (r.failed ? `, ${r.failed} could not be saved` : "") + "."

export type BulkAction = "STATUS" | "CATEGORY" | "PRICE_PERCENT"
export interface BulkForm { action: BulkAction; status: ItemStatus; reason: string; categoryId: string; percent: string; effectiveFrom: string }
export const EMPTY_BULK_FORM: BulkForm = { action: "STATUS", status: "SUSPENDED", reason: "", categoryId: "", percent: "", effectiveFrom: "" }
export interface BulkRow { id: string; name: string; sku: string | null; outcome: "OK" | "UNCHANGED" | "REFUSED"; message: string }
export interface BulkResult { dryRun: boolean; ok: number; unchanged: number; refused: number; rows: BulkRow[] }
export const MAX_BULK_ITEMS = 200

export function bulkProblem(f: BulkForm): string | null {
  if (f.action === "STATUS") return moveNeedsReason(f.status) && f.reason.trim().length < 3 ? "Give a reason of at least 3 characters." : null
  if (f.action === "CATEGORY") return f.categoryId ? null : "Choose a category."
  const p = Number(f.percent.replace(",", "."))
  if (!f.percent.trim() || Number.isNaN(p)) return "Enter the percentage, for example 5 or -10."
  if (p === 0) return "A change of 0% changes nothing."
  if (p < -90 || p > 500) return "The percentage must be between -90 and 500."
  return null
}

export function bulkBody(ids: string[], f: BulkForm, dryRun: boolean) {
  return {
    ids, action: f.action, dryRun,
    status: f.action === "STATUS" ? f.status : undefined,
    reason: f.reason.trim() || undefined,
    categoryId: f.action === "CATEGORY" ? f.categoryId : undefined,
    percent: f.action === "PRICE_PERCENT" ? Number(f.percent.replace(",", ".")) : undefined,
    effectiveFrom: f.action === "PRICE_PERCENT" && f.effectiveFrom ? f.effectiveFrom : undefined,
  }
}

export const bulkSummary = (r: BulkResult) =>
  `${r.dryRun ? "Would change" : "Changed"} ${r.ok}` + (r.unchanged ? `, ${r.unchanged} already as asked` : "") + (r.refused ? `, ${r.refused} refused` : "") + "."

/** Hands text to the browser as a file download. Does nothing where downloads are not possible. */
export function saveText(fileName: string, text: string): void {
  try {
    const url = URL.createObjectURL(new Blob([text], { type: "text/csv;charset=utf-8" }))
    const a = document.createElement("a")
    a.href = url; a.download = fileName
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(url)
  } catch { /* no download support, e.g. in tests */ }
}


// ── Data quality and item approval ───────────────────────────────────────────
export interface QualityFinding { itemId: string; name: string; sku: string | null; detail: string }
export interface QualityGroup { kind: string; label: string; advice: string; count: number; items: QualityFinding[] }
export interface DataQuality { scanned: number; total: number; groups: QualityGroup[] }

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN"
export const APPROVAL_TABS: { id: ApprovalStatus; label: string }[] = [{ id: "PENDING", label: "Waiting" }, { id: "APPROVED", label: "Approved" }, { id: "REJECTED", label: "Rejected" }]
export interface ItemApproval {
  id: string; itemId: string; itemName: string | null; sku: string | null; itemStatus: string | null; status: ApprovalStatus
  requestedBy: string | null; requestedAt: string; note: string | null; decidedBy: string | null; decidedAt: string | null; decisionNote: string | null
}
export interface CatalogueSettings { priceApprovalRequired: boolean; itemApprovalRequired: boolean; updatedAt: string | null }

/** Rejecting needs a reason; approving and submitting do not. */
export const rejectProblem = (note: string): string | null => (note.trim().length < 3 ? "Give a reason of at least 3 characters." : null)

export const qualityText = (q: DataQuality) =>
  q.total === 0 ? `Nothing to fix across ${q.scanned} item${q.scanned === 1 ? "" : "s"}.` : `${q.total} thing${q.total === 1 ? "" : "s"} to look at across ${q.scanned} item${q.scanned === 1 ? "" : "s"}.`


// ── Media, documents and packages ────────────────────────────────────────────
export interface MediaFile { id: string; itemId: string; kind: "IMAGE" | "DOCUMENT"; title: string | null; altText: string | null; fileName: string; contentType: string; sizeBytes: number; primary: boolean; sortOrder: number; uploadedAt: string; url: string }
export interface BundleLine { itemId: string; name: string; sku: string | null; status: string; quantity: number; unitPrice: number | null; lineTotal: number | null }
export interface BundleView { bundleId: string; name: string; sku: string | null; type: string; price: number | null; components: BundleLine[]; partsTotal: number | null; partsCost: number | null; saving: number | null; unpricedParts: number }
export interface UsedIn { bundleId: string; name: string; sku: string | null; status: string; quantity: number }

export const MEDIA_TYPES = ["image/jpeg", "image/png", "image/webp", "application/pdf"]
export const MEDIA_MAX_BYTES: Record<"IMAGE" | "DOCUMENT", number> = { IMAGE: 5 * 1024 * 1024, DOCUMENT: 10 * 1024 * 1024 }

export const fileSizeText = (bytes: number) => (bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`)

/** What is wrong with a file before it is sent, or "" when it can go. The server checks again. */
export function mediaProblem(file: { name: string; type: string; size: number }): string {
  if (!MEDIA_TYPES.includes(file.type)) return "Use a JPEG, PNG or WebP picture, or a PDF document."
  const kind = file.type === "application/pdf" ? "DOCUMENT" : "IMAGE"
  if (file.size <= 0) return "That file is empty."
  if (file.size > MEDIA_MAX_BYTES[kind]) return `${kind === "IMAGE" ? "Pictures" : "Documents"} can be up to ${MEDIA_MAX_BYTES[kind] / 1024 / 1024} MB.`
  return ""
}

export interface BundleDraftLine { itemId: string; quantity: string }

/** What is wrong with the parts list, or "". */
export function bundleProblem(lines: BundleDraftLine[], selfId: string): string {
  if (lines.length === 0) return "Add at least one component."
  if (lines.length > 30) return "A package can have at most 30 components."
  const seen = new Set<string>()
  for (const l of lines) {
    if (!l.itemId) return "Choose an item for every line."
    if (l.itemId === selfId) return "A package cannot contain itself."
    if (seen.has(l.itemId)) return "Each item can appear only once. Raise its quantity instead."
    seen.add(l.itemId)
    const q = Number(l.quantity.replace(",", "."))
    if (!l.quantity.trim() || !Number.isFinite(q) || q <= 0) return "Each quantity must be more than zero."
    if (q > 9999.999) return "A quantity can be at most 9999.999."
    if (!/^\d+([.,]\d{1,3})?$/.test(l.quantity.trim())) return "A quantity can have at most 3 decimals."
  }
  return ""
}

export const bundleBody = (lines: BundleDraftLine[]) => ({ components: lines.map(l => ({ itemId: l.itemId, quantity: Number(l.quantity.replace(",", ".")) })) })

export const savingText = (saving: number | null, unpriced: number) =>
  unpriced > 0 ? `${unpriced} part${unpriced === 1 ? " has" : "s have"} no price, so the saving cannot be worked out.`
    : saving == null ? "—" : saving > 0 ? `Customers save ${rand(saving)} against buying the parts separately.` : saving < 0 ? `The package costs ${rand(-saving)} more than its parts.` : "The package costs the same as its parts."


// ── Channels and publishing ──────────────────────────────────────────────────
export type ChannelKind = "POS" | "WEB" | "MARKETPLACE" | "PRINT" | "OTHER"
export const CHANNEL_KINDS: { id: ChannelKind; label: string }[] = [
  { id: "POS", label: "Till (point of sale)" }, { id: "WEB", label: "Online shop" }, { id: "MARKETPLACE", label: "Marketplace" }, { id: "PRINT", label: "Printed price list" }, { id: "OTHER", label: "Other" },
]
export const channelKindLabel = (k: string) => CHANNEL_KINDS.find(x => x.id === k)?.label ?? k
export interface Channel { id: string; code: string; name: string; kind: ChannelKind; priceListId: string | null; priceListName: string | null; active: boolean; publishedCount: number }
export interface ChannelItem { itemId: string; name: string | null; sku: string | null; status: string; publishedAt: string }
export interface PublishRow { itemId: string; name: string | null; sku: string | null; outcome: string; problems: string[] }
export interface PublishResult { channelId: string; dryRun: boolean; done: number; unchanged: number; blocked: number; rows: PublishRow[] }
export interface ItemChannel { channelId: string; code: string; name: string; kind: ChannelKind; channelActive: boolean; published: boolean; publishedAt: string | null; problems: string[] }

export interface ChannelForm { code: string; name: string; kind: ChannelKind; priceListId: string; active: boolean }
export const EMPTY_CHANNEL_FORM: ChannelForm = { code: "", name: "", kind: "WEB", priceListId: "", active: true }
export const toChannelForm = (c: Channel): ChannelForm => ({ code: c.code, name: c.name, kind: c.kind, priceListId: c.priceListId ?? "", active: c.active })

export function channelProblems(f: ChannelForm, isNew: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  if (isNew && !/^[a-z0-9][a-z0-9-]{1,29}$/.test(f.code.trim().toLowerCase())) e.code = "2 to 30 letters, digits or hyphens, e.g. web-shop"
  if (!f.name.trim()) e.name = "Required"
  else if (f.name.trim().length > 80) e.name = "At most 80 characters"
  return e
}
export const channelBody = (f: ChannelForm) => ({ code: f.code.trim().toLowerCase(), name: f.name.trim(), kind: f.kind, priceListId: f.priceListId || null, active: f.active })

export const OUTCOME_LABEL: Record<string, string> = {
  PUBLISHED: "Published", WOULD_PUBLISH: "Can be published", ALREADY: "Already there", BLOCKED: "Cannot be published",
  UNPUBLISHED: "Taken off", WOULD_UNPUBLISH: "Would be taken off", NOT_PUBLISHED: "Was not published",
}
export function publishText(r: PublishResult, verb: "publish" | "unpublish" = "publish"): string {
  const parts: string[] = []
  const w = verb === "publish" ? (r.dryRun ? "can be published" : "published") : (r.dryRun ? "would be taken off" : "taken off")
  parts.push(`${r.done} ${w}`)
  if (r.unchanged > 0) parts.push(verb === "publish" ? `${r.unchanged} already there` : `${r.unchanged} were not published`)
  if (r.blocked > 0) parts.push(`${r.blocked} cannot be published`)
  return parts.join(", ") + "."
}
