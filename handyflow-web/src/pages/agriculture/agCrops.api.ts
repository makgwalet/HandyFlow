// src/pages/agriculture/agCrops.api.ts
//
// Every request the Crops, Seasons and Cost screens make. Lists are paged on the server (Page<T>), cost
// summaries are plain lists. Failures toast the server's own message: business-rule errors reach the
// client as 409/400 with a readable message ("cannot issue 50 kg of Maize seed, only 30 left").
import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast, errorMessage } from "../../store/toast.store"
import type { FarmResponse } from "./AgFarmsTab"
import type { ProductionAreaResponse } from "./AgProductionAreasTab"
import type { EnterpriseResponse } from "./AgEnterprisesTab"
import type { InventoryItemResponse } from "./AgInventoryTab"
import type {
  AnimalCost, CropCycle, CropCycleCost, CropType, CycleStatus, GroupCost, HarvestRecord, InputApplication, Page, ScoutingRecord, Season,
} from "./agCrops.types"

const BASE = "/api/v1/agriculture"
const pageContent = <T,>(p: Page<T> | T[] | null | undefined): T[] => (Array.isArray(p) ? p : p?.content ?? [])

export const agKeys = {
  farms: ["ag", "farms-active"] as const,
  cropTypes: (category?: string) => ["ag", "crop-types", category ?? "all"] as const,
  seasons: (farmId: string) => ["ag", "seasons", farmId] as const,
  cycles: (farmId: string, status?: string) => ["ag", "cycles", farmId, status ?? "all"] as const,
  cyclesOfFarm: (farmId: string) => ["ag", "cycles", farmId] as const,
  cycle: (id: string) => ["ag", "cycle", id] as const,
  cycleCost: (id: string) => ["ag", "cycle-cost", id] as const,
  inputs: (id: string) => ["ag", "inputs", id] as const,
  scouting: (id: string) => ["ag", "scouting", id] as const,
  harvests: (id: string) => ["ag", "harvests", id] as const,
  areas: (farmId: string) => ["ag", "areas", farmId] as const,
  enterprises: (farmId: string) => ["ag", "enterprises", farmId] as const,
  inventory: (farmId: string) => ["ag", "inventory", farmId] as const,
  costs: (kind: "crops" | "animals" | "groups", farmId: string) => ["ag", "costs", kind, farmId] as const,
}

// -- Reads --------------------------------------------------------------------------------------------------

export function useActiveFarms() {
  return useQuery<FarmResponse[]>({
    queryKey: agKeys.farms,
    queryFn: async () => pageContent((await apiClient.get(`${BASE}/farms`, { params: { status: "ACTIVE", size: 100 } })).data as Page<FarmResponse>),
  })
}

export function useCropTypes(category?: string) {
  return useQuery<CropType[]>({
    queryKey: agKeys.cropTypes(category),
    queryFn: async () => pageContent((await apiClient.get(`${BASE}/crop-types`, { params: { size: 200, category: category || undefined } })).data as Page<CropType>),
  })
}

export function useSeasons(farmId: string) {
  return useQuery<Season[]>({
    queryKey: agKeys.seasons(farmId), enabled: !!farmId,
    queryFn: async () => pageContent((await apiClient.get(`${BASE}/farms/${farmId}/seasons`, { params: { size: 100 } })).data as Page<Season>),
  })
}

/** `status` is filtered by the server; every other filter (season, search) is applied client-side. */
export function useCropCycles(farmId: string, status?: CycleStatus | "") {
  return useQuery<CropCycle[]>({
    queryKey: agKeys.cycles(farmId, status || undefined), enabled: !!farmId,
    queryFn: async () => pageContent((await apiClient.get(`${BASE}/farms/${farmId}/crop-cycles`, { params: { size: 200, status: status || undefined } })).data as Page<CropCycle>),
  })
}

export function useCropCycle(id: string) {
  return useQuery<CropCycle>({ queryKey: agKeys.cycle(id), queryFn: async () => (await apiClient.get(`${BASE}/crop-cycles/${id}`)).data as CropCycle })
}
export function useCycleCost(id: string) {
  return useQuery<CropCycleCost>({ queryKey: agKeys.cycleCost(id), queryFn: async () => (await apiClient.get(`${BASE}/crop-cycles/${id}/cost-summary`)).data as CropCycleCost })
}
export function useInputs(id: string) {
  return useQuery<InputApplication[]>({ queryKey: agKeys.inputs(id), queryFn: async () => pageContent((await apiClient.get(`${BASE}/crop-cycles/${id}/input-applications`, { params: { size: 200 } })).data as Page<InputApplication>) })
}
export function useScouting(id: string) {
  return useQuery<ScoutingRecord[]>({ queryKey: agKeys.scouting(id), queryFn: async () => pageContent((await apiClient.get(`${BASE}/crop-cycles/${id}/scouting-records`, { params: { size: 200 } })).data as Page<ScoutingRecord>) })
}
export function useHarvests(id: string) {
  return useQuery<HarvestRecord[]>({ queryKey: agKeys.harvests(id), queryFn: async () => pageContent((await apiClient.get(`${BASE}/crop-cycles/${id}/harvest-records`, { params: { size: 200 } })).data as Page<HarvestRecord>) })
}

// farm reference data the crop screens join against by id
export function useAreas(farmId: string) {
  return useQuery<ProductionAreaResponse[]>({ queryKey: agKeys.areas(farmId), enabled: !!farmId, queryFn: async () => pageContent((await apiClient.get(`${BASE}/farms/${farmId}/production-areas`, { params: { size: 200 } })).data as Page<ProductionAreaResponse>) })
}
export function useEnterprises(farmId: string) {
  return useQuery<EnterpriseResponse[]>({ queryKey: agKeys.enterprises(farmId), enabled: !!farmId, queryFn: async () => pageContent((await apiClient.get(`${BASE}/farms/${farmId}/enterprises`, { params: { size: 200 } })).data as Page<EnterpriseResponse>) })
}
export function useInventory(farmId: string) {
  return useQuery<InventoryItemResponse[]>({ queryKey: agKeys.inventory(farmId), enabled: !!farmId, queryFn: async () => pageContent((await apiClient.get(`${BASE}/farms/${farmId}/inventory-items`, { params: { size: 200 } })).data as Page<InventoryItemResponse>) })
}

export function useFarmCosts<K extends "crops" | "animals" | "groups">(kind: K, farmId: string) {
  type R = K extends "crops" ? CropCycleCost : K extends "animals" ? AnimalCost : GroupCost
  const path = kind === "crops" ? "crop-cycles" : kind
  return useQuery<R[]>({
    queryKey: agKeys.costs(kind, farmId), enabled: !!farmId,
    queryFn: async () => ((await apiClient.get(`${BASE}/farms/${farmId}/${path}/cost-summary`)).data ?? []) as R[],
  })
}

// -- Writes -------------------------------------------------------------------------------------------------

interface MutationOptions { invalidate: QueryKey[]; success?: string; failure: string }

/** A write that refreshes the given queries, confirms success (optional) and toasts the server's reason on failure. */
export function useAgMutation<V, R = unknown>(fn: (v: V) => Promise<R>, o: MutationOptions) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => { o.invalidate.forEach(k => qc.invalidateQueries({ queryKey: k })); if (o.success) toast.success(o.success) },
    onError: e => toast.error(errorMessage(e, o.failure)),
  })
}

/** Everything about one cycle changes together (status, costs, lists), so refresh the lot. */
export const cycleKeys = (farmId: string, id: string): QueryKey[] => [agKeys.cyclesOfFarm(farmId), agKeys.cycle(id), agKeys.cycleCost(id), ["ag", "costs", "crops", farmId]]

export const api = {
  post: (url: string, body?: unknown) => apiClient.post(`${BASE}${url}`, body),
  put: (url: string, body?: unknown) => apiClient.put(`${BASE}${url}`, body),
  patch: (url: string, body?: unknown) => apiClient.patch(`${BASE}${url}`, body),
  del: (url: string) => apiClient.delete(`${BASE}${url}`),
}

/** Download rows as a CSV file (client-side; there is no export endpoint). */
export function downloadCsv(fileName: string, csv: string) {
  const url = URL.createObjectURL(new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" }))
  const a = document.createElement("a"); a.href = url; a.download = fileName
  document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url)
}
