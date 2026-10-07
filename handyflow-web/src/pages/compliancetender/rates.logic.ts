// src/pages/compliancetender/rates.logic.ts
//
// Pure rules for the rates library screen: reading what was typed, filtering, wording the result of an import, and turning a rate into a price-schedule line. Nothing here talks to
// the server. A line made from a rate is a COPY: it keeps the cost it had when it was added.
import type { ImportResult, Rate, RateCategory, RateRequest } from "./rates.api"
import type { LineRequest } from "./pricing.api"
import { fmtZar, parseDecimal } from "./pricing.logic"

export const CATEGORY_LABEL: Record<RateCategory, string> = { MATERIAL: "Material", LABOUR: "Labour", PLANT: "Plant", SUBCONTRACT: "Subcontract", OTHER: "Other" }
export const CATEGORIES = Object.keys(CATEGORY_LABEL) as RateCategory[]

export interface RateDraft { category: RateCategory; itemRef: string; description: string; unit: string; unitCost: string; supplier: string; notes: string; active: boolean }
export const EMPTY_RATE: RateDraft = { category: "MATERIAL", itemRef: "", description: "", unit: "", unitCost: "", supplier: "", notes: "", active: true }

export function rateToDraft(r: Rate): RateDraft {
  return { category: r.category, itemRef: r.itemRef ?? "", description: r.description, unit: r.unit, unitCost: String(r.unitCost), supplier: r.supplier, notes: r.notes ?? "", active: r.active }
}

/** What to send for a rate, or what is wrong with it. The server checks again; this saves a round trip and says it in words. */
export function parseRate(d: RateDraft): { ok: true; value: RateRequest } | { ok: false; errors: Record<string, string> } {
  const errors: Record<string, string> = {}
  if (!d.description.trim()) errors.description = "Describe what this is."
  if (d.description.length > 500) errors.description = "Keep the description under 500 characters."
  const cost = parseDecimal(d.unitCost)
  if (cost == null) errors.unitCost = "Enter the cost as a number."
  else if (cost < 0) errors.unitCost = "A cost cannot be negative."
  else if (Math.round(cost * 100) / 100 !== cost) errors.unitCost = "A cost can have at most 2 decimal places."
  if (Object.keys(errors).length) return { ok: false, errors }
  return { ok: true, value: { category: d.category, itemRef: d.itemRef.trim(), description: d.description.trim(), unit: d.unit.trim(), unitCost: cost as number, supplier: d.supplier.trim(), notes: d.notes.trim(), active: d.active } }
}

export interface RateFilter { search: string; category: RateCategory | "ALL"; supplier: string | "ALL"; showInactive: boolean }
export const DEFAULT_FILTER: RateFilter = { search: "", category: "ALL", supplier: "ALL", showInactive: false }

/** Rates that match every filter; the search looks at description, code, supplier and notes, ignoring case. */
export function filterRates(rates: Rate[], f: RateFilter): Rate[] {
  const q = f.search.trim().toLowerCase()
  return rates.filter(r =>
    (f.showInactive || r.active)
    && (f.category === "ALL" || r.category === f.category)
    && (f.supplier === "ALL" || r.supplier === f.supplier)
    && (q === "" || [r.description, r.itemRef ?? "", r.supplier, r.notes ?? ""].some(t => t.toLowerCase().includes(q))))
}

/** The suppliers in the library, alphabetical; rates with no supplier are not a supplier. */
export function suppliersOf(rates: Rate[]): string[] {
  return [...new Set(rates.map(r => r.supplier).filter(s => s !== ""))].sort((a, b) => a.localeCompare(b))
}

/** "+R 15.00" or "-R 5.00" for a price that moved, or null when it has never changed. */
export function changeText(r: Pick<Rate, "unitCost" | "previousUnitCost">): string | null {
  if (r.previousUnitCost == null || r.previousUnitCost === r.unitCost) return null
  const d = Math.round((r.unitCost - r.previousUnitCost) * 100) / 100
  return `${d > 0 ? "+" : "-"}${fmtZar(Math.abs(d))} from ${fmtZar(r.previousUnitCost)}`
}

/** One sentence on what an import did, or would do for a dry run. */
export function importSummary(r: ImportResult): string {
  const parts = [`${r.created} new`, `${r.updated} updated`, `${r.unchanged} unchanged`]
  if (r.skipped > 0) parts.push(`${r.skipped} skipped`)
  return `${r.dryRun ? "Would import" : "Imported"}: ${parts.join(", ")}`
}

/** Can anything be imported? A file with only problems (or nothing) offers no Import button. */
export function importable(r: ImportResult | undefined): boolean {
  return !!r && r.dryRun && r.created + r.updated > 0
}

/** The price-schedule line for a rate: its details copied, with the quantity and section the user chose. The supplier is deliberately not copied: the schedule can end up in a document that others read. Returns null when the quantity is not a usable number. */
export function lineFromRate(rate: Rate, quantityText: string, section: string): LineRequest | null {
  const q = parseDecimal(quantityText)
  if (q == null || q < 0 || Math.round(q * 1000) / 1000 !== q) return null
  return { section: section.trim(), itemRef: rate.itemRef ?? "", description: rate.description, unit: rate.unit, quantity: q, unitCost: rate.unitCost }
}
