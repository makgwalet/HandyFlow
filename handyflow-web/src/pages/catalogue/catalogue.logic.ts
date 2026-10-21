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

export interface Category { id: string; name: string; description: string | null; sortOrder: number; itemCount: number | null }
export interface CatalogueItem {
  id: string; name: string; description: string | null; sku: string | null; barcode: string | null; brand: string | null; itemType: ItemType
  status: ItemStatus; statusReason: string | null; statusChangedAt: string | null; unit: string; defaultPrice: number; vatRate: number; vatExempt: boolean
  categoryId: string | null; categoryName: string | null; costPrice: number | null; marginPercent: number | null; version: number; createdAt: string; updatedAt: string
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
}
export const EMPTY_ITEM_FORM: ItemForm = {
  name: "", description: "", sku: "", barcode: "", brand: "", itemType: "PRODUCT", categoryId: "", unit: "Each",
  defaultPrice: "", costPrice: "", vatRate: "15", vatExempt: false, draft: false,
}
export const toItemForm = (i: CatalogueItem): ItemForm => ({
  name: i.name, description: i.description ?? "", sku: i.sku ?? "", barcode: i.barcode ?? "", brand: i.brand ?? "", itemType: i.itemType, categoryId: i.categoryId ?? "",
  unit: i.unit, defaultPrice: String(i.defaultPrice), costPrice: i.costPrice == null ? "" : String(i.costPrice), vatRate: String(i.vatRate), vatExempt: i.vatExempt, draft: false,
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
export function itemBody(f: ItemForm, opts: { canWriteCost: boolean; version?: number; creating?: boolean }) {
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
