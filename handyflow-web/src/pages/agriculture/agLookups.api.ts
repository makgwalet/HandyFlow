// src/pages/agriculture/agLookups.api.ts
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import type { AuditInfo, AuditType } from "./agAudit.logic"

interface SpeciesOption { id: string; name: string }

/** Species id → name, from the same cached list the animal and group forms use. */
export function useSpeciesNames(): Record<string, string> {
  const { data } = useQuery<{ content: SpeciesOption[] }>({
    queryKey: ["ag-species", "picker"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: { size: 200 } })).data,
  })
  const out: Record<string, string> = {}
  for (const s of data?.content ?? []) out[s.id] = s.name
  return out
}

export interface FarmSuggestions { breedsBySpecies: Record<string, string[]>; feedTypes: string[] }

/** Breeds (per species) and feed types already used anywhere on the farm. Empty until loaded, and on an error, so the boxes still work. */
export function useFarmSuggestions(farmId: string | undefined): FarmSuggestions {
  const { data } = useQuery<FarmSuggestions>({
    queryKey: ["ag-suggestions", farmId], enabled: !!farmId, retry: false, staleTime: 60_000,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/suggestions`)).data,
  })
  return data ?? { breedsBySpecies: {}, feedTypes: [] }
}

/** Who added each of these rows (id -> stamp), one request for the list. Empty until loaded, and on an error. */
export function useAuditBatch(type: AuditType, ids: string[]): Record<string, AuditInfo> {
  const key = ids.join(",")
  const { data } = useQuery<Record<string, AuditInfo>>({
    queryKey: ["ag-audit-batch", type, key], enabled: ids.length > 0 && ids.length <= 200, retry: false, staleTime: 30_000,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/audit/${type}`, { params: { ids: key } })).data,
  })
  return data ?? {}
}
