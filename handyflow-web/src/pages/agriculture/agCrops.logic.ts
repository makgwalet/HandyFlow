// src/pages/agriculture/agCrops.logic.ts
//
// Pure rules for crops and costs: no React, no network. Several exist because the BACKEND does not enforce
// them, so the screens have to (see the notes on each).
import type { CropCycle, CropType, CycleStatus, HarvestRecord } from "./agCrops.types"
import { canConvertUnit, isMassUnit } from "./agUnits"

// -- Lifecycle ----------------------------------------------------------------------------------------------

/** The happy path, in order. FAILED and ABANDONED are exits, not steps. */
export const STEPS: CycleStatus[] = ["PLANNED", "PLANTED", "GROWING", "HARVESTING", "HARVESTED"]
export const ACTIVE_STATUSES: CycleStatus[] = ["PLANTED", "GROWING", "HARVESTING"]
export const STATUS_LABEL: Record<CycleStatus, string> = {
  PLANNED: "Planned", PLANTED: "Planted", GROWING: "Growing", HARVESTING: "Harvesting",
  HARVESTED: "Harvested", FAILED: "Failed", ABANDONED: "Abandoned",
}
export const isTerminal = (s: CycleStatus) => s === "HARVESTED" || s === "FAILED" || s === "ABANDONED"

/** Index on the stepper, or -1 for a cycle that left the happy path (FAILED / ABANDONED). */
export const stepIndex = (s: CycleStatus) => STEPS.indexOf(s)

export interface CycleActions {
  recordPlanting: boolean; markGrowing: boolean; startHarvest: boolean; completeHarvest: boolean
  fail: boolean; abandon: boolean; edit: boolean
}

/**
 * Which lifecycle buttons to offer. The transitions mirror AgCropCycle. fail/abandon are deliberately
 * narrower than the server, which accepts them in ANY status (even on an already harvested cycle).
 */
export function allowedActions(status: CycleStatus): CycleActions {
  return {
    recordPlanting: status === "PLANNED",
    markGrowing: status === "PLANTED",
    startHarvest: status === "PLANTED" || status === "GROWING",
    completeHarvest: status === "HARVESTING",
    fail: status === "PLANTED" || status === "GROWING" || status === "HARVESTING",
    abandon: !isTerminal(status),
    edit: true,
  }
}

export type LogKind = "input" | "scouting" | "harvest"
/** Whether a record of this kind makes sense now. Inputs and scouting stop once a cycle is failed/abandoned;
 *  harvests only apply once something is in the ground (or was harvested: late entry). */
export function canLog(kind: LogKind, status: CycleStatus): boolean {
  if (status === "FAILED" || status === "ABANDONED") return false
  if (kind === "harvest") return status !== "PLANNED"
  return true
}

// -- Dates (business day, Africa/Johannesburg) --------------------------------------------------------------

export const BUSINESS_TZ = "Africa/Johannesburg"
export const todayISO = (now: Date = new Date()) => now.toLocaleDateString("en-CA", { timeZone: BUSINESS_TZ })

/** YYYY-MM-DD plus n days, computed in UTC so a timezone can never shift the day. */
export function addDays(iso: string, n: number): string {
  const [y, m, d] = iso.split("-").map(Number)
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10)
}

/** Expected harvest = planting date + the crop type's typical growing days. null when either is unknown. */
export function suggestExpectedHarvest(plantingDate: string, typicalGrowingDays: number | null | undefined): string | null {
  if (!plantingDate || !typicalGrowingDays || typicalGrowingDays <= 0) return null
  return addDays(plantingDate, Math.round(typicalGrowingDays))
}

/** Harvest is overdue when the expected date has passed and the crop is still PLANTED/GROWING (harvesting has begun otherwise). */
export function isHarvestOverdue(c: Pick<CropCycle, "status" | "expectedHarvestDate">, today: string = todayISO()): boolean {
  return !!c.expectedHarvestDate && (c.status === "PLANTED" || c.status === "GROWING") && c.expectedHarvestDate < today
}

// -- Labels and lookups -------------------------------------------------------------------------------------

export function cropName(c: Pick<CropCycle, "cropTypeId">, types: CropType[]): string {
  return types.find(t => t.id === c.cropTypeId)?.name ?? "Unknown crop"
}

/** cycleName is optional on the server, so fall back to "Maize, PAN 6767". */
export function cycleLabel(c: Pick<CropCycle, "cropTypeId" | "cycleName" | "variety">, types: CropType[]): string {
  if (c.cycleName?.trim()) return c.cycleName
  return c.variety?.trim() ? `${cropName(c, types)}, ${c.variety}` : cropName(c, types)
}

// -- KPIs ---------------------------------------------------------------------------------------------------

export interface CycleKpis { total: number; active: number; planned: number; harvesting: number; areaInProduction: number }

export function cycleKpis(cycles: CropCycle[]): CycleKpis {
  const active = cycles.filter(c => ACTIVE_STATUSES.includes(c.status))
  return {
    total: cycles.length,
    active: active.length,
    planned: cycles.filter(c => c.status === "PLANNED").length,
    harvesting: cycles.filter(c => c.status === "HARVESTING").length,
    areaInProduction: round(active.reduce((sum, c) => sum + (c.areaPlantedHectares ?? 0), 0), 2),
  }
}

export function statusCounts(cycles: CropCycle[]): { status: CycleStatus; count: number }[] {
  return (Object.keys(STATUS_LABEL) as CycleStatus[])
    .map(status => ({ status, count: cycles.filter(c => c.status === status).length }))
    .filter(x => x.count > 0)
}

// -- Money and stock checks the server does not make for us ---------------------------------------------------

export const round = (n: number, dp = 2) => Math.round(n * 10 ** dp) / 10 ** dp

/**
 * The backend stores an input application's `cost` exactly as sent and the cost reports read THAT figure
 * (it is not derived from the stock item). Leaving it blank silently understates crop cost, so the form
 * prefills quantity x unit cost.
 */
export function suggestInputCost(quantity: number, unitCost: number | null | undefined): number | null {
  if (!Number.isFinite(quantity) || quantity <= 0 || unitCost == null || unitCost < 0) return null
  return round(quantity * unitCost, 2)
}

/** The server rejects over-issuing with a 409; say so before sending. */
export function stockProblem(quantity: number, item: { itemName: string; currentQuantity: number; unitOfMeasure: string } | undefined): string | null {
  if (!item || !Number.isFinite(quantity) || quantity <= 0) return null
  return quantity > item.currentQuantity ? `Only ${item.currentQuantity} ${item.unitOfMeasure} of ${item.itemName} in stock.` : null
}

/**
 * The server converts kg and t before summing yield and REJECTS a unit it cannot convert to the crop's unit, so this returns
 * the problem (to show and to block on) rather than a soft warning. With no crop unit it falls back to the unit already
 * used on this cycle. Mixing kg and t is fine; mixing kg and bags is not.
 */
export function harvestUnitProblem(unit: string, cropUnit: string | null | undefined, existing: Pick<HarvestRecord, "unitOfMeasure">[]): string | null {
  if (!unit.trim()) return null
  const target = cropUnit?.trim() ? cropUnit : existing[0]?.unitOfMeasure
  if (!target || canConvertUnit(unit, target)) return null
  return `${unit.trim()} can't be converted to ${target.trim()}, which this crop is reported in. ${isMassUnit(target) ? "Use a mass unit such as kg or t." : `Use ${target.trim()}.`}`
}

export function distinctUnits(records: Pick<HarvestRecord, "unitOfMeasure">[]): string[] {
  return [...new Set(records.map(r => r.unitOfMeasure.trim()))]
}

// -- Export -------------------------------------------------------------------------------------------------

/**
 * RFC 4180 style CSV: quotes fields containing a comma, quote or newline, doubles embedded quotes.
 * A TEXT cell that starts with = + - @ (or a tab or return) is prefixed with an apostrophe, so a spreadsheet shows it as text instead of running it as a formula
 * (CSV injection): names and tags are typed by users. Numbers are passed as numbers and are never altered, so a negative figure stays a figure.
 */
export function toCsv(rows: (string | number | null | undefined)[][]): string {
  const cell = (v: string | number | null | undefined) => {
    let s = v == null ? "" : String(v)
    if (typeof v === "string" && /^[=+\-@\t\r]/.test(s)) s = `'${s}`
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  return rows.map(r => r.map(cell).join(",")).join("\r\n")
}

export const sumBy = <T,>(rows: T[], f: (r: T) => number | null | undefined) => rows.reduce((s, r) => s + (f(r) ?? 0), 0)
