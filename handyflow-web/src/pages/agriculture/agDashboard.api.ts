// src/pages/agriculture/agDashboard.api.ts
//
// GET /api/v1/agriculture/dashboard: every ACTIVE farm in one call (totals, farm types, locations, crops in production,
// livestock by species and the ranked attention list). Shapes mirror AgDashboardResponse. Only facts Agriculture records are
// here: there is no revenue, labour cost, equipment cost or weather.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface AttentionItem {
  type: string; severity: string; title: string; description: string | null; dueDate: string | null
  referenceId: string; farmId: string | null; farmName: string | null
}
export interface DashboardFarm {
  id: string; name: string; farmType: string | null; province: string | null; region: string | null
  latitude: number | null; longitude: number | null; totalHectares: number | null
  hectaresInProduction: number; cropCyclesInProduction: number; animalCount: number; groupHead: number
  attentionCount: number; urgentCount: number
}
export interface AgDashboardData {
  asOf: string
  totals: {
    farmCount: number; totalHectares: number; farmsWithoutHectares: number
    cropCyclesInProduction: number; hectaresInProduction: number; plannedCropCycles: number
    animalCount: number; groupCount: number; groupHead: number; totalHead: number
  }
  farmTypes: { farmType: string; farmCount: number; hectares: number }[]
  farms: DashboardFarm[]
  crops: {
    byStatus: { status: string; cycles: number; hectares: number }[]
    inProduction: { cropTypeId: string; cropName: string; cycles: number; hectares: number }[]
  }
  livestock: { speciesId: string; name: string; category: string | null; animals: number; groupHead: number; totalHead: number }[]
  attention: { total: number; bySeverity: { severity: string; count: number }[]; items: AttentionItem[] }
}

export function useAgDashboard() {
  return useQuery<AgDashboardData>({
    queryKey: ["ag", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/dashboard")).data as AgDashboardData,
  })
}
