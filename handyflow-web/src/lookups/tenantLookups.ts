// src/lookups/tenantLookups.ts
//
// A company's own additions to the built-in pick-lists. The built-in lists stay in southAfrica.ts; these are merged in after them.
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../api/client"
import { type LookupOption } from "./southAfrica"

export interface TenantLookupValue { id: string; listKey: string; value: string }

const BASE = "/api/v1/compliance/lookups"
const KEY = ["ct-lookups"]

/** Built-in options first, then the company's additions for this list; an addition that repeats a built-in one (any capitalisation) is dropped. */
export function mergeOptions(base: LookupOption[], listKey: string | undefined, extras: TenantLookupValue[]): LookupOption[] {
  if (!listKey) return base
  const seen = new Set(base.map(o => o.value.toLowerCase()))
  const added: LookupOption[] = []
  for (const e of extras) {
    if (e.listKey !== listKey || seen.has(e.value.toLowerCase())) continue
    seen.add(e.value.toLowerCase())
    added.push({ value: e.value, hint: "Added by your company", id: e.id })
  }
  return [...base, ...added]
}

/** Never throws and never returns a non-array: a failed or odd answer just means no additions. */
export function useTenantLookups(enabled: boolean) {
  const q = useQuery<TenantLookupValue[]>({
    queryKey: KEY, enabled, staleTime: 5 * 60 * 1000, refetchOnWindowFocus: false, retry: false,
    queryFn: async () => { try { const d = (await apiClient.get(BASE)).data; return Array.isArray(d) ? d : [] } catch { return [] } },
  })
  return q.data ?? []
}

export function useLookupActions() {
  const qc = useQueryClient()
  return {
    add: async (listKey: string, value: string): Promise<string> => {
      const r = (await apiClient.post(`${BASE}/${listKey}`, { value })).data as TenantLookupValue
      await qc.invalidateQueries({ queryKey: KEY })
      return r.value
    },
    remove: async (id: string): Promise<void> => {
      await apiClient.delete(`${BASE}/${id}`)
      await qc.invalidateQueries({ queryKey: KEY })
    },
  }
}
