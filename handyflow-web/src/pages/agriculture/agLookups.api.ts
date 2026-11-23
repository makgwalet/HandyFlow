// src/pages/agriculture/agLookups.api.ts
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

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
