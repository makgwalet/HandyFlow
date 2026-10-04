// src/pages/agriculture/agProfitability.api.ts
//
// The farm's gross-margin report (ADR-001, W5): revenue (ex-VAT, net of credit notes) minus DIRECT production costs, unit by unit. Computed live by
// the server and never stored. Needs AGRICULTURE_FINANCE and INVOICE_READ because it shows revenue.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export type UnitType = "CROP_CYCLE" | "GROUP" | "ANIMAL" | "ENTERPRISE" | "UNLISTED"

export interface UnitProfit {
  targetType: UnitType; targetId: string | null; label: string; status: string | null
  state: "COMPLETE" | "IN_PROGRESS"          // COMPLETE: the margin is final. IN_PROGRESS: to date, unsold stock is not valued.
  revenue: number
  recordedCost: number                        // feed, health, seed, inputs and an animal's purchase price (recorded where they happen)
  labour: number; equipment: number; fuel: number; otherDirect: number
  directCost: number; grossMargin: number
  marginPercent: number | null                // null when there is no revenue
  caveats: string[]
}

export interface Totals { revenue: number; recordedCost: number; labour: number; equipment: number; fuel: number; otherDirect: number; directCost: number; grossMargin: number; marginPercent: number | null }
export interface Subtotal { units: number; revenue: number; directCost: number; grossMargin: number; marginPercent: number | null }

export interface Profitability { farmId: string; totals: Totals; complete: Subtotal; inProgress: Subtotal; units: UnitProfit[]; notes: string[] }

export function useProfitability(farmId: string, enabled = true) {
  return useQuery<Profitability>({
    queryKey: ["ag", "profitability", farmId], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/profitability`)).data,
  })
}
