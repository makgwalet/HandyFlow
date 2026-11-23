// src/pages/agriculture/agLedger.api.ts
//
// The Agriculture cost ledger (ADR-001, W1). Every endpoint needs AGRICULTURE_FINANCE. Rows are append-only: a mistake is
// reversed (a negative REVERSAL row) rather than edited. Only OTHER_DIRECT costs can be entered by hand.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { api, useAgMutation } from "./agCrops.api"
import type { AnimalResponse } from "./AgAnimalsTab"
import type { GroupResponse } from "./AgGroupsTab"
import { fetchAllPages } from "./agPaging.logic"

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
export interface DateRange { from?: string; to?: string }
const LEDGER = ["ag", "ledger"] as const

/** Ledger rows, optionally only those dated from..to (both included, either may be empty). */
export function useCostEntries(farmId: string, target: TargetFilter | null, enabled = true, range: DateRange = {}) {
  return useQuery<{ content: CostEntry[]; totalElements: number }>({
    queryKey: [...LEDGER, "entries", farmId, target?.type ?? "all", target?.id ?? "", range.from ?? "", range.to ?? ""], enabled: enabled && !!farmId,
    queryFn: async () => {
      const r = await fetchAllPages<CostEntry>(async (page, size) => (await apiClient.get(`${BASE}/farms/${farmId}/cost-entries`, { params: { targetType: target?.type, targetId: target?.id, ...range, page, size } })).data)
      return { content: r.items, totalElements: r.total }
    },
  })
}

export function useCostTotals(farmId: string, target: TargetFilter | null, enabled = true, range: DateRange = {}) {
  return useQuery<CostTotals>({
    queryKey: [...LEDGER, "totals", farmId, target?.type ?? "all", target?.id ?? "", range.from ?? "", range.to ?? ""], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/cost-entries/totals`, { params: { targetType: target?.type, targetId: target?.id, ...range } })).data,
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

// the lists a cost can be allocated to: every page, up to 2,000 rows (the server sends at most 200 at a time)
export function useFarmGroups(farmId: string) {
  return useQuery<GroupResponse[]>({ queryKey: ["ag", "groups-for-ledger", farmId], enabled: !!farmId,
    queryFn: async () => (await fetchAllPages<GroupResponse>(async (page, size) => (await apiClient.get(`${BASE}/farms/${farmId}/groups`, { params: { page, size } })).data)).items })
}
export function useFarmAnimals(farmId: string) {
  return useQuery<AnimalResponse[]>({ queryKey: ["ag", "animals-for-ledger", farmId], enabled: !!farmId,
    queryFn: async () => (await fetchAllPages<AnimalResponse>(async (page, size) => (await apiClient.get(`${BASE}/farms/${farmId}/animals`, { params: { page, size } })).data)).items })
}
