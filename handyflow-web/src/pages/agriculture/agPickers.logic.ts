// src/pages/agriculture/agPickers.logic.ts
// What the move-to-area and feed-from-stock pickers offer, and the checks made before a request is sent. The server repeats
// every check (AgMovementRules, AgFeedRules); these only explain a problem before the person presses Save.
import { canonicalUnit, convertUnit, isMassUnit } from "./agUnits"

export interface AreaLike { id: string; name: string; areaType: string; status: string }
export interface StockLike { id: string; itemName: string; unitOfMeasure: string; currentQuantity: number; unitCost: number | null; status: string }

export interface Choice { id: string; label: string }

/** Active, resting and quarantine areas except the one it is already in. Inactive areas are not offered. */
export function areaChoices(areas: AreaLike[], currentAreaId: string | null): Choice[] {
  return areas
    .filter(a => a.status !== "INACTIVE" && a.id !== currentAreaId)
    .sort((a, b) => a.name.localeCompare(b.name))
    .map(a => ({ id: a.id, label: `${a.name} (${a.areaType.toLowerCase().replace(/_/g, " ")})${a.status === "ACTIVE" ? "" : " · " + a.status.toLowerCase()}` }))
}

export interface FeedChoice extends Choice { disabled: boolean }

/** Active stock items. An item not counted in a mass unit (bags, bales) is shown but cannot be picked, with the reason. */
export function feedChoices(items: StockLike[]): FeedChoice[] {
  return items
    .filter(i => i.status === "ACTIVE")
    .sort((a, b) => a.itemName.localeCompare(b.itemName))
    .map(i => {
      const mass = isMassUnit(i.unitOfMeasure)
      const onHand = `${i.currentQuantity} ${i.unitOfMeasure.trim()} on hand`
      return { id: i.id, disabled: !mass, label: mass ? `${i.itemName} · ${onHand}` : `${i.itemName} · counted in ${i.unitOfMeasure.trim()}, change to kg or t to use` }
    })
}

/** The feed name and cost per kg an item suggests. Cost is the item's unit cost converted to kg, or null if unknown. */
export function feedPrefill(item: StockLike): { feedType: string; costPerKg: number | null } {
  if (item.unitCost == null) return { feedType: item.itemName, costPerKg: null }
  const unitsPerKg = convertUnit(1, "kg", item.unitOfMeasure)
  return { feedType: item.itemName, costPerKg: unitsPerKg == null ? null : Math.round(item.unitCost * unitsPerKg * 1e4) / 1e4 }
}

/** Why this much cannot be fed from this item, or null. Quantity is in kg; stock is compared in the item's own unit. */
export function feedStockProblem(item: StockLike, quantityKg: number): string | null {
  if (!(quantityKg > 0)) return null
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
