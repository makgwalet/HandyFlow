// src/pages/agriculture/agEquipmentFuel.logic.ts
//
// Pure rules for the equipment and fuel screen. The server is the authority (it re-reads the rate from Fleet or the cost from Fuel, snapshots it
// and refuses double-allocation); these mirror its arithmetic so the figure shown before saving is the figure that gets recorded.
import type { EquipmentOption, FuelDispatchRow } from "./agEquipmentFuel.api"
import { validateAllocation, type ShareInput } from "./agLedger.logic"

export const MAX_HOURS_PER_USE = 24

const round = (n: number, places: number) => { const f = 10 ** places; return Math.round((n + Number.EPSILON) * f) / f }

/** The rate as the ledger stores it, to four places. */
export const snapshotRate = (rate: number) => round(rate, 4)

/** What the hours of use cost at the machine's operating rate, to the cent; null while the machine has no rate. */
export function equipmentCost(hours: number, ratePerHour: number | null): number | null {
  if (ratePerHour == null || !(ratePerHour > 0) || !(hours > 0)) return null
  return round(hours * snapshotRate(ratePerHour), 2)
}

const num = (s: string) => Number(s.trim().replace(",", "."))

/** The first reason one day's machine use cannot be saved, or null. Allocation rules are the ledger's own (agLedger.logic). */
export function validateEquipmentUse(machine: EquipmentOption | undefined, hoursText: string, date: string, shares: ShareInput[], today: string): string | null {
  if (!machine) return "Choose the machine."
  if (machine.operatingRatePerHour == null || !(machine.operatingRatePerHour > 0)) return `Set an operating rate for ${machine.registration} in Fleet first (service and repairs per hour).`
  const h = num(hoursText)
  if (hoursText.trim() === "" || !Number.isFinite(h) || h <= 0) return "Enter the hours above zero."
  if (h > MAX_HOURS_PER_USE) return `One use can't be more than ${MAX_HOURS_PER_USE} hours; enter a separate use for each day.`
  const cost = equipmentCost(h, machine.operatingRatePerHour)
  if (cost == null || !(cost > 0)) return "The cost rounds to nothing; check the hours."
  return validateAllocation(String(cost), "machine use", date, shares, today)
}

/** The first reason a fuel dispatch cannot be allocated as set up, or null. */
export function validateFuelAllocation(row: FuelDispatchRow, shares: ShareInput[], today: string): string | null {
  if (row.cost == null || !(row.cost > 0)) return "Fuel recorded no cost for this dispatch, so it can't be allocated."
  return validateAllocation(String(row.cost), "fuel", row.date, shares, today)
}

/** Litres without trailing noise. */
export const litresText = (l: number) => `${Number(l.toLocaleString("en-US", { maximumFractionDigits: 2, useGrouping: false }))} L`
