import { apiClient } from "./client"

export interface HeatCell { inherent: number; control: number; count: number; areas: string[] }
export interface HeatRow { processArea: string; low: number; medium: number; high: number; critical: number; notAssessed: number; total: number }
export interface HeatMap { cells: HeatCell[]; byProcessArea: HeatRow[]; assessed: number; notAssessed: number; total: number }

export const auditHeatMapApi = {
  get: () => apiClient.get("/api/v1/internal-audit/heat-map").then(r => (r.data?.data ?? r.data) as HeatMap),
}
