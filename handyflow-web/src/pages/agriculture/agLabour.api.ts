// src/pages/agriculture/agLabour.api.ts
//
// Costing recorded labour into the cost ledger (ADR-001, W3). The server never sends a salary: only the hourly RATE worked out from it, and only
// when the user also has an HR permission (`hrRatesAvailable`). Everything needs AGRICULTURE_FINANCE.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { api, useAgMutation } from "./agCrops.api"

export interface FinanceSettings { standardHoursPerWeek: number; labourOnCostPercent: number; configured: boolean }

export type WorkType = "INPUT_APPLICATION" | "HARVEST"

export interface LabourCandidate {
  sourceType: WorkType; sourceId: string; cropCycleId: string; date: string; description: string
  workerId: string | null; workerName: string | null; hours: number
  suggestedRate: number | null          // BASE hourly rate (before on-costs) from HR; null with rateNote saying why
  rateSource: "HR" | "NONE"; rateNote: string | null
  estimatedCost: number | null          // includes the on-cost
}

export interface LabourOverview { settings: FinanceSettings; hrRatesAvailable: boolean; candidates: LabourCandidate[] }

export interface CostLabourBody { items: { sourceType: WorkType; sourceId: string; hourlyRate?: number }[] }

const BASE = "/api/v1/agriculture"
const LABOUR = ["ag", "labour"] as const
const LEDGER = ["ag", "ledger"] as const

export function useFinanceSettings(enabled = true) {
  return useQuery<FinanceSettings>({ queryKey: [...LABOUR, "settings"], enabled, queryFn: async () => (await apiClient.get(`${BASE}/finance/settings`)).data })
}

export function useUpdateFinanceSettings() {
  return useAgMutation((body: { standardHoursPerWeek: number; labourOnCostPercent: number }) => api.put(`/finance/settings`, body),
    { invalidate: [[...LABOUR]], success: "Settings saved. They apply to labour costed from now on.", failure: "Couldn't save the settings." })
}

export function useUncostedLabour(farmId: string, enabled = true) {
  return useQuery<LabourOverview>({
    queryKey: [...LABOUR, "uncosted", farmId], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/labour/uncosted`)).data,
  })
}

export function useCostLabour(farmId: string) {
  return useAgMutation((body: CostLabourBody) => api.post(`/farms/${farmId}/labour/cost`, body),
    { invalidate: [[...LABOUR], [...LEDGER]], success: "Labour costed into the ledger", failure: "Couldn't cost the labour." })
}
