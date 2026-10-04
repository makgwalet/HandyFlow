// src/pages/agriculture/agProfitability.logic.ts
//
// Pure presentation rules for the profitability screen. The numbers come from the server, already computed; this only filters, labels and explains them.
import type { Profitability, ProfitabilityOverview, UnitProfit, UnitType } from "./agProfitability.api"

export const TYPE_LABEL: Record<UnitType, string> = { CROP_CYCLE: "Crop cycle", GROUP: "Group", ANIMAL: "Animal", ENTERPRISE: "Enterprise", UNLISTED: "Other" }

export type TypeFilter = "ALL" | UnitType
export type StateFilter = "ALL" | "COMPLETE" | "IN_PROGRESS" | "BREEDING_STOCK"

export function filterUnits(units: UnitProfit[], type: TypeFilter, state: StateFilter): UnitProfit[] {
  return units.filter(u => (type === "ALL" || u.targetType === type) && (state === "ALL" || u.state === state))
}

/** The unit types that actually appear, in a stable order, so the filter only offers choices that show something. */
export function typesPresent(units: UnitProfit[]): UnitType[] {
  const order: UnitType[] = ["CROP_CYCLE", "GROUP", "ANIMAL", "ENTERPRISE", "UNLISTED"]
  return order.filter(t => units.some(u => u.targetType === t))
}

export type Tone = "gain" | "loss" | "even"
export const toneOf = (margin: number): Tone => (margin > 0 ? "gain" : margin < 0 ? "loss" : "even")

/** "70,0%" style. A missing percentage is a dash, because there is no revenue to be a percentage of. */
export const percentText = (p: number | null): string => (p == null ? "—" : `${p.toLocaleString("en-ZA", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`)

/** The cost parts of a unit that are not zero, in a fixed order, for the detail row. */
export function costParts(u: UnitProfit): { label: string; amount: number }[] {
  return [
    { label: "Recorded costs (feed, health, seed, inputs, purchase price)", amount: u.recordedCost },
    { label: "Labour", amount: u.labour },
    { label: "Equipment", amount: u.equipment },
    { label: "Fuel", amount: u.fuel },
    { label: "Other direct costs", amount: u.otherDirect },
  ].filter(p => p.amount !== 0)
}

export const stateLabel = (s: UnitProfit["state"]) => (s === "COMPLETE" ? "Final" : s === "BREEDING_STOCK" ? "Breeding stock" : "To date")

// -- Export -------------------------------------------------------------------------------------------------------

const STATE_TEXT = { COMPLETE: "Final", IN_PROGRESS: "To date", BREEDING_STOCK: "Breeding stock" } as const

/**
 * The report as rows for toCsv. Money is written as plain numbers to two decimals (no currency symbol, a dot for the decimal) so a spreadsheet can add it up;
 * a loss is a negative number. Text cells are guarded against spreadsheet formulas by toCsv. The caveats and the report's notes travel with the numbers, so a file
 * passed to an accountant carries its own warnings.
 */
export function profitabilityCsvRows(data: Profitability, ctx: { farm: string; season: string | null; generatedOn: string }): (string | number | null)[][] {
  const m = (n: number) => Number(n.toFixed(2))
  const head = ["Type", "Unit", "Status", "Margin is", "Revenue", "Recorded costs", "Labour", "Equipment", "Fuel", "Other direct costs", "Direct costs", "Gross margin", "Margin %", "Read with care"]
  const t = data.totals
  const rows: (string | number | null)[][] = [
    ["Gross margin report"], ["Farm", ctx.farm], ["Covers", ctx.season ? `Season: ${ctx.season} (its crop cycles only)` : "Whole farm"], ["Generated", ctx.generatedOn],
    ["Gross margin only: revenue (ex-VAT, net of credit notes) minus direct production costs. Not net profit."],
    [], head,
    ...data.units.map(u => [TYPE_LABEL[u.targetType], u.label, u.status ?? "", STATE_TEXT[u.state], m(u.revenue), m(u.recordedCost), m(u.labour), m(u.equipment), m(u.fuel), m(u.otherDirect), m(u.directCost), m(u.grossMargin), u.marginPercent, u.caveats.join(" | ")]),
    ["Total", "", "", "", m(t.revenue), m(t.recordedCost), m(t.labour), m(t.equipment), m(t.fuel), m(t.otherDirect), m(t.directCost), m(t.grossMargin), t.marginPercent, ""],
  ]
  if (data.notes.length) rows.push([], ["Notes"], ...data.notes.map(n => [n]))
  return rows
}

/** A safe file name: letters, digits and dashes only, so a farm or season name cannot break the path. */
export function profitabilityFileName(farm: string, season: string | null, generatedOn: string): string {
  const slug = (x: string) => x.normalize("NFKD").replace(/[^A-Za-z0-9]+/g, "-").replace(/^-+|-+$/g, "").toLowerCase() || "farm"
  return `gross-margin-${slug(farm)}${season ? `-${slug(season)}` : ""}-${generatedOn}.csv`
}

/**
 * The all-farms overview as rows for toCsv: each farm's own figures as plain numbers to two places, a total, and the notes. The total is the server's exact sum of the farms.
 * Text cells are guarded against spreadsheet formulas by toCsv.
 */
export function overviewCsvRows(o: ProfitabilityOverview, ctx: { generatedOn: string }): (string | number | null)[][] {
  const m = (n: number) => Number(n.toFixed(2))
  const t = o.totals
  return [
    ["Gross margin, all farms"], ["Generated", ctx.generatedOn],
    ["Gross margin only: revenue (ex-VAT, net of credit notes) minus direct production costs. Not net profit."],
    [], ["Farm", "Revenue", "Direct costs", "Gross margin", "Margin %", "Finished units", "Running units", "Breeding stock units", "Read with care"],
    ...o.farms.map(f => [f.farmName, m(f.revenue), m(f.directCost), m(f.grossMargin), f.marginPercent, f.finishedUnits, f.runningUnits, f.breedingStockUnits, f.cautions]),
    ["Total", m(t.revenue), m(t.directCost), m(t.grossMargin), t.marginPercent, o.complete.units, o.inProgress.units, o.breedingStock.units, null],
    ...(o.notes.length ? [[], ["Notes"], ...o.notes.map(n => [n])] : []),
  ]
}

/** A safe file name for the overview export. */
export function overviewFileName(generatedOn: string): string { return `gross-margin-all-farms-${generatedOn}.csv` }
