// src/pages/compliancetender/pricing.logic.ts
//
// Pure rules for the tender pricing screen (ADR-004): reading what the person typed, wording, grouping and the CSV. The arithmetic itself is the server's; nothing here recalculates a price.
import type { LineRequest, PricingLine, PricingSettings, SettingsRequest, TenderPricing } from "./pricing.api"

export const fmtZar = (v: number | null | undefined): string =>
  v == null || Number.isNaN(v) ? "—" : `R ${v.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/**
 * A number as typed by a South African user: "1 250,50", "1250.50" and "R 1,250.50" are all fine. Returns null when it is not a number at all (so "12abc" is refused, not read as 12).
 * A single comma with no dot is a decimal comma; a comma before a dot is a thousands separator.
 */
export function parseDecimal(raw: string): number | null {
  let s = raw.replace(/\s/g, "").replace(/^R/i, "")
  if (s === "") return null
  if (s.includes(",") && s.includes(".")) s = s.replace(/,/g, "")
  else if ((s.match(/,/g) ?? []).length === 1) s = s.replace(",", ".")
  else s = s.replace(/,/g, "")
  if (!/^-?\d+(\.\d+)?$/.test(s) && !/^-?\.\d+$/.test(s)) return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}

/** Decimal places in a number as typed, ignoring trailing zeros ("1.50" has 1). */
function decimalPlaces(n: number): number {
  const m = /^-?\d*\.?(\d*)$/.exec(String(n))
  return m ? m[1].length : 0
}

export interface LineDraft { section: string; itemRef: string; description: string; unit: string; quantity: string; unitCost: string }
export const EMPTY_LINE: LineDraft = { section: "", itemRef: "", description: "", unit: "", quantity: "", unitCost: "" }

export type Parsed<T> = { ok: true; value: T } | { ok: false; errors: Record<string, string> }

export function lineToDraft(l: PricingLine): LineDraft {
  return { section: l.section, itemRef: l.itemRef ?? "", description: l.description, unit: l.unit ?? "", quantity: String(l.quantity), unitCost: String(l.unitCost) }
}

/** What to send for a line, or what is wrong with it. The server checks again; this only saves a round trip and says it in words. */
export function parseLine(d: LineDraft): Parsed<LineRequest> {
  const errors: Record<string, string> = {}
  if (!d.description.trim()) errors.description = "Describe the item"
  else if (d.description.trim().length > 500) errors.description = "Keep the description under 500 characters"
  if (d.section.trim().length > 120) errors.section = "Keep the section name under 120 characters"
  if (d.itemRef.trim().length > 40) errors.itemRef = "Keep the reference under 40 characters"
  if (d.unit.trim().length > 30) errors.unit = "Keep the unit under 30 characters"
  const quantity = parseDecimal(d.quantity)
  const unitCost = parseDecimal(d.unitCost)
  if (quantity == null) errors.quantity = "Enter a quantity"
  else if (quantity < 0) errors.quantity = "Quantity cannot be negative"
  if (unitCost == null) errors.unitCost = "Enter a cost per unit"
  else if (unitCost < 0) errors.unitCost = "Cost cannot be negative"
  if (quantity != null && !errors.quantity && decimalPlaces(quantity) > 3) errors.quantity = "Quantity can have at most 3 decimal places"
  if (unitCost != null && !errors.unitCost && decimalPlaces(unitCost) > 2) errors.unitCost = "Cost can have at most 2 decimal places (enter a total on a quantity of 1 for finer prices)"
  if (Object.keys(errors).length > 0 || quantity == null || unitCost == null) return { ok: false, errors }
  return { ok: true, value: { section: d.section.trim(), itemRef: d.itemRef.trim(), description: d.description.trim(), unit: d.unit.trim(), quantity, unitCost } }
}

export interface SettingsDraft { overheadPct: string; contingencyPct: string; profitPct: string; vatApplies: boolean; notes: string }

export function settingsToDraft(s: PricingSettings): SettingsDraft {
  return { overheadPct: String(s.overheadPct), contingencyPct: String(s.contingencyPct), profitPct: String(s.profitPct), vatApplies: s.vatApplies, notes: s.notes ?? "" }
}

/** A blank percentage means 0. Anything else must be a number from 0 to 100. */
export function parseSettings(d: SettingsDraft): Parsed<SettingsRequest> {
  const errors: Record<string, string> = {}
  const pct = (field: "overheadPct" | "contingencyPct" | "profitPct", label: string): number => {
    if (d[field].trim() === "") return 0
    const n = parseDecimal(d[field])
    if (n == null) { errors[field] = `${label} must be a number`; return 0 }
    if (n < 0 || n > 100) { errors[field] = `${label} must be between 0 and 100`; return 0 }
    return n
  }
  const overheadPct = pct("overheadPct", "Overhead")
  const contingencyPct = pct("contingencyPct", "Contingency")
  const profitPct = pct("profitPct", "Profit")
  if (d.notes.length > 5000) errors.notes = "Keep the notes under 5000 characters"
  if (Object.keys(errors).length > 0) return { ok: false, errors }
  return { ok: true, value: { overheadPct, contingencyPct, profitPct, vatApplies: d.vatApplies, notes: d.notes } }
}

/** True when the draft says something different from what is saved, so Save is only offered when there is something to save. */
export function settingsChanged(d: SettingsDraft, s: PricingSettings): boolean {
  const p = parseSettings(d)
  if (!p.ok) return true
  return p.value.overheadPct !== s.overheadPct || p.value.contingencyPct !== s.contingencyPct || p.value.profitPct !== s.profitPct
    || p.value.vatApplies !== s.vatApplies || p.value.notes.trim() !== (s.notes ?? "").trim()
}

const trim = (n: number) => String(Number(n.toFixed(2)))

/** One sentence saying how the price is built, so nobody has to guess the basis of each markup. */
export function basisText(s: PricingSettings): string {
  const vat = s.vatApplies ? `VAT at ${trim(s.vatRatePct)}% is added` : "VAT is not added"
  return `Overhead ${trim(s.overheadPct)}% and contingency ${trim(s.contingencyPct)}% are added to direct cost; profit ${trim(s.profitPct)}% is added to cost plus both; ${vat}.`
}

export function lockedText(status: string): string {
  return `This tender is ${status.toLowerCase().replace(/_/g, " ")}, so its pricing can no longer be changed. It is kept as the record of what was priced.`
}

export function marginText(m: number | null): string {
  return m == null ? "No price yet" : `${m.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}% of the price is profit`
}

export interface LineGroup { section: string; lines: PricingLine[]; subtotal: number }

/** Lines grouped by section, in the order the sections first appear. Subtotals come from the server's own section totals so they always match the breakdown. */
export function groupLines(p: Pick<TenderPricing, "lines" | "breakdown">): LineGroup[] {
  const subtotal = new Map(p.breakdown.sections.map(s => [s.section, s.subtotal]))
  const groups: LineGroup[] = []
  for (const l of p.lines) {
    let g = groups.find(x => x.section === l.section)
    if (!g) { g = { section: l.section, lines: [], subtotal: subtotal.get(l.section) ?? 0 }; groups.push(g) }
    g.lines.push(l)
  }
  return groups
}

/** Spreadsheet programs run text starting with = + - @ as a formula; a leading apostrophe makes it plain text. */
function csvCell(v: string): string {
  const safe = /^[=+\-@\t\r]/.test(v) ? `'${v}` : v
  return /[",\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe
}
const num = (n: number) => n.toFixed(2)

/** The price schedule as CSV: one row per line, then the build-up of the price. Amounts are plain numbers so they sort and add in a spreadsheet. */
export function buildCsv(p: TenderPricing, tenderNumber: string): string {
  const rows: string[][] = [["Tender", tenderNumber], [], ["Section", "Ref", "Description", "Unit", "Quantity", "Unit cost", "Total"]]
  for (const g of groupLines(p)) for (const l of g.lines) rows.push([l.section, l.itemRef ?? "", l.description, l.unit ?? "", String(l.quantity), num(l.unitCost), num(l.lineTotal)])
  const b = p.breakdown, s = p.settings
  rows.push([], ["Direct cost", "", "", "", "", "", num(b.directCost)],
    [`Overhead ${trim(s.overheadPct)}%`, "", "", "", "", "", num(b.overhead)],
    [`Contingency ${trim(s.contingencyPct)}%`, "", "", "", "", "", num(b.contingency)],
    [`Profit ${trim(s.profitPct)}%`, "", "", "", "", "", num(b.profit)],
    ["Price excluding VAT", "", "", "", "", "", num(b.priceExVat)],
    [s.vatApplies ? `VAT ${trim(s.vatRatePct)}%` : "VAT (not added)", "", "", "", "", "", num(b.vat)],
    ["Price including VAT", "", "", "", "", "", num(b.priceInclVat)])
  return rows.map(r => r.map(csvCell).join(",")).join("\r\n")
}
