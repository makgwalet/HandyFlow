// src/pages/agriculture/agProfitability.api.ts
//
// The farm's gross-margin report (ADR-001, W5): revenue (ex-VAT, net of credit notes) minus DIRECT production costs, unit by unit. Computed live by
// the server and never stored. Needs AGRICULTURE_FINANCE and INVOICE_READ because it shows revenue.
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export type UnitType = "CROP_CYCLE" | "GROUP" | "ANIMAL" | "ENTERPRISE" | "UNLISTED"

export interface UnitProfit {
  targetType: UnitType; targetId: string | null; label: string; status: string | null
  // COMPLETE: the margin is final. IN_PROGRESS: to date, unsold stock is not valued. BREEDING_STOCK: an animal flagged as breeding stock; its purchase
  // price (capital) is not counted, and it is shown apart from the production margins.
  state: "COMPLETE" | "IN_PROGRESS" | "BREEDING_STOCK"
  revenue: number
  recordedCost: number                        // feed, health, seed, inputs and an animal's purchase price (recorded where they happen)
  labour: number; equipment: number; fuel: number; otherDirect: number
  directCost: number; grossMargin: number
  marginPercent: number | null                // null when there is no revenue
  caveats: string[]
}

export interface Totals { revenue: number; recordedCost: number; labour: number; equipment: number; fuel: number; otherDirect: number; directCost: number; grossMargin: number; marginPercent: number | null }
export interface Subtotal { units: number; revenue: number; directCost: number; grossMargin: number; marginPercent: number | null }

export interface Profitability { farmId: string; totals: Totals; complete: Subtotal; inProgress: Subtotal; breedingStock: Subtotal; units: UnitProfit[]; notes: string[] }

/** The whole farm, or one season of it (that season's crop cycles only) when {@code seasonId} is given. */
export function useProfitability(farmId: string, seasonId: string | null = null, enabled = true) {
  return useQuery<Profitability>({
    queryKey: ["ag", "profitability", farmId, seasonId ?? "all"], enabled: enabled && !!farmId,
    placeholderData: keepPreviousData,   // keep the controls and the old figures on screen while a new season loads; the page marks them as updating
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/profitability`, { params: seasonId ? { seasonId } : undefined })).data,
  })
}

/** One farm in the overview: that farm's own Profitability totals, unchanged. */
export interface FarmMargin {
  farmId: string; farmName: string
  revenue: number; directCost: number; grossMargin: number; marginPercent: number | null
  finishedUnits: number; runningUnits: number; breedingStockUnits: number
  cautions: number                             // units with a caveat plus the farm's own warnings: things to read with care
}

/** Every active farm side by side. The totals are the exact sum of the farms' own figures, so this never disagrees with a farm's screen. */
export interface ProfitabilityOverview { totals: Totals; complete: Subtotal; inProgress: Subtotal; breedingStock: Subtotal; farms: FarmMargin[]; notes: string[] }

export function useProfitabilityOverview(enabled = true) {
  return useQuery<ProfitabilityOverview>({
    queryKey: ["ag", "profitability", "overview"], enabled,
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/profitability/overview")).data,
  })
}
