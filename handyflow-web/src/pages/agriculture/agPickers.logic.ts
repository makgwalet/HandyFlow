// src/pages/agriculture/agPickers.logic.ts
// What the move-to-area and feed-from-stock pickers offer, and the checks made before a request is sent. The server repeats
// every check (AgMovementRules, AgFeedRules); these only explain a problem before the person presses Save.
import { canonicalUnit, convertUnit, isMassUnit } from "./agUnits"

export interface AreaLike { id: string; name: string; areaType: string; status: string }
export interface StockLike { id: string; itemName: string; unitOfMeasure: string; currentQuantity: number; unitCost: number | null; status: string; packSizeKg?: number | null }

export interface Choice { id: string; label: string }

/** Active, resting and quarantine areas except the one it is already in. Inactive areas are not offered. */
export function areaChoices(areas: AreaLike[], currentAreaId: string | null): Choice[] {
  return areas
    .filter(a => a.status !== "INACTIVE" && a.id !== currentAreaId)
    .sort((a, b) => a.name.localeCompare(b.name))
    .map(a => ({ id: a.id, label: `${a.name} (${a.areaType.toLowerCase().replace(/_/g, " ")})${a.status === "ACTIVE" ? "" : " · " + a.status.toLowerCase()}` }))
}

export interface FeedChoice extends Choice { disabled: boolean }

const COUNT_UNITS = ["bag", "bags", "bale", "bales", "sack", "sacks"]
/** Bags, bales and sacks: counted, not weighed. Mirrors AgPackRules on the server. */
export const isCountUnit = (unit: string | null | undefined) => COUNT_UNITS.includes((unit ?? "").trim().toLowerCase())
/** "bags" becomes "bag", for labels. */
export function unitSingular(unit: string | null | undefined): string {
  const u = (unit ?? "").trim().toLowerCase()
  return COUNT_UNITS.includes(u) && u.endsWith("s") ? u.slice(0, -1) : u
}
/** True for a bag, bale or sack item that says how many kg one holds. */
export const hasPackSize = (item: StockLike) => isCountUnit(item.unitOfMeasure) && item.packSizeKg != null && item.packSizeKg > 0

const round3 = (n: number) => Math.round(n * 1000) / 1000
export const kgToBags = (kg: number, packKg: number) => round3(kg / packKg)
export const bagsToKg = (bags: number, packKg: number) => round3(bags * packKg)

export type FeedEntry = "kg" | "bags"
/** The amount sent: kilograms or bags, never both. The server converts bags with the item's own kg per bag. */
export function feedQuantityPayload(entry: FeedEntry, value: string): { quantityKg: number | null; quantityBags: number | null } {
  const n = Number(value)
  return entry === "bags" ? { quantityKg: null, quantityBags: n } : { quantityKg: n, quantityBags: null }
}
/** "= 2.4 bags" or "= 120 kg", for the entry the person did not use. Empty when there is nothing to show. */
export function feedEquivalent(item: StockLike, entry: FeedEntry, value: string): string {
  const n = Number(value)
  if (!hasPackSize(item) || !(n > 0)) return ""
  const pack = item.packSizeKg as number
  return entry === "kg" ? `= ${kgToBags(n, pack)} ${unitSingular(item.unitOfMeasure)}${kgToBags(n, pack) === 1 ? "" : "s"}` : `= ${bagsToKg(n, pack)} kg`
}

/** Active stock items. A bag, bale or sack item can be picked once it has a kg per bag; any other item not counted in a mass unit is shown but cannot be picked, with the reason. */
export function feedChoices(items: StockLike[]): FeedChoice[] {
  return items
    .filter(i => i.status === "ACTIVE")
    .sort((a, b) => a.itemName.localeCompare(b.itemName))
    .map(i => {
      const mass = isMassUnit(i.unitOfMeasure)
      const onHand = `${i.currentQuantity} ${i.unitOfMeasure.trim()} on hand`
      if (hasPackSize(i)) return { id: i.id, disabled: false, label: `${i.itemName} · ${onHand} (${i.packSizeKg} kg per ${unitSingular(i.unitOfMeasure)})` }
      if (isCountUnit(i.unitOfMeasure)) return { id: i.id, disabled: true, label: `${i.itemName} · counted in ${i.unitOfMeasure.trim()}, set kg per ${unitSingular(i.unitOfMeasure)} in Stock to use` }
      return { id: i.id, disabled: !mass, label: mass ? `${i.itemName} · ${onHand}` : `${i.itemName} · counted in ${i.unitOfMeasure.trim()}, change to kg or t to use` }
    })
}

/** The feed name and cost per kg an item suggests. Cost is the item's unit cost converted to kg, or null if unknown. */
export function feedPrefill(item: StockLike): { feedType: string; costPerKg: number | null } {
  if (item.unitCost == null) return { feedType: item.itemName, costPerKg: null }
  if (hasPackSize(item)) return { feedType: item.itemName, costPerKg: Math.round((item.unitCost / (item.packSizeKg as number)) * 1e4) / 1e4 }
  const unitsPerKg = convertUnit(1, "kg", item.unitOfMeasure)
  return { feedType: item.itemName, costPerKg: unitsPerKg == null ? null : Math.round(item.unitCost * unitsPerKg * 1e4) / 1e4 }
}

/** Why this much cannot be fed from this item, or null. Quantity is in kg; stock is compared in the item's own unit. */
export function feedStockProblem(item: StockLike, quantityKg: number): string | null {
  if (!(quantityKg > 0)) return null
  if (hasPackSize(item)) {
    const bags = kgToBags(quantityKg, item.packSizeKg as number)
    return bags > item.currentQuantity ? `Only ${item.currentQuantity} ${item.unitOfMeasure.trim()} of ${item.itemName} on hand (${quantityKg} kg is ${bags} ${unitSingular(item.unitOfMeasure)}${bags === 1 ? "" : "s"}).` : null
  }
  if (isCountUnit(item.unitOfMeasure)) return `${item.itemName} is counted in ${item.unitOfMeasure.trim()}, so kilograms cannot be taken off it. Set kg per ${unitSingular(item.unitOfMeasure)} in Stock to feed from it.`
  const needed = convertUnit(quantityKg, "kg", item.unitOfMeasure)
  if (needed == null) return `${item.itemName} is counted in ${item.unitOfMeasure.trim()}, so kilograms cannot be taken off it.`
  if (needed > item.currentQuantity) return `Only ${item.currentQuantity} ${item.unitOfMeasure.trim()} of ${item.itemName} on hand (${quantityKg} kg is ${needed} ${canonicalUnit(item.unitOfMeasure)}).`
  return null
}

export interface MovementForm { type: string; toAreaId: string; targetType: "animal" | "group"; countMoved: string; currentCount: number | null; toFarmId?: string; reason?: string }

export function movementFormError(f: MovementForm): string | null {
  if (f.type === "AREA_TRANSFER" && !f.toAreaId) return "Choose the production area to move to."
  if (f.type === "FARM_TRANSFER") {
    if (!f.toFarmId) return "Choose the farm to transfer to."
    if (f.targetType === "group" && f.countMoved.trim() !== "" && f.currentCount != null && Number(f.countMoved) !== f.currentCount) return "Only a whole group can change farm. Leave the count empty."
  }
  if (f.targetType === "group" && (f.type === "SALE" || f.type === "TRANSFER_OUT") && (f.reason ?? "").trim().length < 3) return "Give a reason, for example who it was sold to."
  if (f.targetType === "group" && f.countMoved.trim() !== "") {
    if (!/^\d+$/.test(f.countMoved.trim()) || Number(f.countMoved) < 1) return "Count moved must be a whole number of 1 or more."
    if (f.currentCount != null && Number(f.countMoved) > f.currentCount) return `The group only has ${f.currentCount}.`
  }
  return null
}

/** True when the move will be recorded but the group stays in its area (fewer head than the group has). Sales and transfers out do take head off. */
export function isPartGroupMove(f: MovementForm): boolean {
  return f.type === "AREA_TRANSFER" && f.targetType === "group" && f.countMoved.trim() !== "" && f.currentCount != null && Number(f.countMoved) < f.currentCount
}

export const MOVEMENT_TYPES: { value: string; label: string }[] = [
  { value: "AREA_TRANSFER", label: "Move to another area" },
  { value: "FARM_TRANSFER", label: "Transfer to another farm" },
  { value: "SALE", label: "Sale (leaves the farm)" },
  { value: "TRANSFER_OUT", label: "Transfer out (leaves the farm)" },
]
