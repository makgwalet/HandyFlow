// src/pages/agriculture/agProfitability.logic.ts
//
// Pure presentation rules for the profitability screen. The numbers come from the server, already computed; this only filters, labels and explains them.
import type { UnitProfit, UnitType } from "./agProfitability.api"

export const TYPE_LABEL: Record<UnitType, string> = { CROP_CYCLE: "Crop cycle", GROUP: "Group", ANIMAL: "Animal", ENTERPRISE: "Enterprise", UNLISTED: "Other" }

export type TypeFilter = "ALL" | UnitType
export type StateFilter = "ALL" | "COMPLETE" | "IN_PROGRESS"

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

export const stateLabel = (s: UnitProfit["state"]) => (s === "COMPLETE" ? "Final" : "To date")
