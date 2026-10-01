// src/pages/agriculture/agTrends.api.ts
//
// GET /api/v1/agriculture/trends?farmId=&months=: monthly costs, harvest (tonnes and per crop), births and deaths, plus
// last-30-days comparisons. Shapes mirror AgTrendsResponse. Omit farmId for every farm. All lists line up with `months`.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface TrendMonth { key: string; start: string; end: string; partial: boolean }
export interface CostMonth { month: string; seed: number; inputs: number; feed: number; health: number; animalPurchases: number; total: number }
export interface CropSeries { cropTypeId: string; cropName: string; unit: string; values: number[]; total: number; excludedRecords: number }
export interface LivestockMonth { month: string; births: number; deaths: number; estimatedLoss: number }
export interface Comparison {
  key: string; label: string; unit: string; current: number; previous: number; changePercent: number | null
  currentFrom: string; currentTo: string; previousFrom: string; previousTo: string
}
export interface AgTrends {
  asOf: string; farmId: string | null
  months: TrendMonth[]
  costs: CostMonth[]
  production: { tonnes: { month: string; tonnes: number }[]; byCrop: CropSeries[]; excludedRecords: number }
  livestock: LivestockMonth[]
  comparisons: Comparison[]
  limitations: string[]
}

export function useAgTrends(farmId: string | null, months: number, enabled = true) {
  return useQuery<AgTrends>({
    queryKey: ["ag", "trends", farmId ?? "all", months], enabled,
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/trends", { params: { farmId: farmId || undefined, months } })).data as AgTrends,
  })
}
