// src/pages/agriculture/agLedger.api.ts
//
// The Agriculture cost ledger (ADR-001, W1). Every endpoint needs AGRICULTURE_FINANCE. Rows are append-only: a mistake is
// reversed (a negative REVERSAL row) rather than edited. Only OTHER_DIRECT costs can be entered by hand.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { api, useAgMutation } from "./agCrops.api"
import type { AnimalResponse } from "./AgAnimalsTab"
import type { GroupResponse } from "./AgGroupsTab"

export type TargetType = "CROP_CYCLE" | "GROUP" | "ANIMAL" | "ENTERPRISE"

export interface CostEntry {
  id: string; farmId: string; entryDate: string; category: string; description: string
  sourceType: string; sourceRef: string | null; targetType: TargetType; targetId: string
  quantity: number | null; unit: string | null; rate: number | null; amount: number; percentage: number
  allocationGroupId: string; reversesEntryId: string | null; status: "ACTIVE" | "REVERSED" | "REVERSAL"
  notes: string | null; createdBy: string | null; createdAt: string
}
export interface CostTotals { byCategory: { category: string; amount: number }[]; total: number }
export interface TargetFilter { type: TargetType; id: string }

export interface CreateCostBody {
  entryDate: string; description: string; amount: number; quantity?: number; unit?: string; notes?: string
  allocations: { targetType: TargetType; targetId: string; percentage: number }[]
}

const BASE = "/api/v1/agriculture"
const LEDGER = ["ag", "ledger"] as const

export function useCostEntries(farmId: string, target: TargetFilter | null, enabled = true) {
  return useQuery<{ content: CostEntry[]; totalElements: number }>({
    queryKey: [...LEDGER, "entries", farmId, target?.type ?? "all", target?.id ?? ""], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/cost-entries`, { params: { targetType: target?.type, targetId: target?.id, size: 200 } })).data,
  })
}

export function useCostTotals(farmId: string, target: TargetFilter | null, enabled = true) {
  return useQuery<CostTotals>({
    queryKey: [...LEDGER, "totals", farmId, target?.type ?? "all", target?.id ?? ""], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/cost-entries/totals`, { params: { targetType: target?.type, targetId: target?.id } })).data,
  })
}

export function useCreateCost(farmId: string) {
  return useAgMutation((body: CreateCostBody) => api.post(`/farms/${farmId}/cost-entries`, body),
    { invalidate: [[...LEDGER]], success: "Cost recorded", failure: "Couldn't record the cost." })
}

export function useReverseCost() {
  return useAgMutation(({ groupId, reason }: { groupId: string; reason: string }) => api.post(`/cost-entries/groups/${groupId}/reverse`, { reason: reason.trim() || undefined }),
    { invalidate: [[...LEDGER]], success: "Cost reversed", failure: "Couldn't reverse the cost." })
}

// the lists a cost can be allocated to (the farm's groups and animals have no shared hook yet)
export function useFarmGroups(farmId: string) {
  return useQuery<GroupResponse[]>({ queryKey: ["ag", "groups-for-ledger", farmId], enabled: !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/groups`, { params: { size: 200 } })).data.content ?? [] })
}
export function useFarmAnimals(farmId: string) {
  return useQuery<AnimalResponse[]>({ queryKey: ["ag", "animals-for-ledger", farmId], enabled: !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/animals`, { params: { size: 200 } })).data.content ?? [] })
}
