// src/pages/compliancetender/rates.api.ts
//
// The rates library: reusable costs for tender pricing, entered by hand or imported from a supplier's price list. The server owns identity (description + unit + supplier) and
// what an import does; the screen sends the file's text and shows the result.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export type RateCategory = "MATERIAL" | "LABOUR" | "PLANT" | "SUBCONTRACT" | "OTHER"

export interface Rate {
  id: string; category: RateCategory; itemRef: string | null; description: string; unit: string; unitCost: number; supplier: string; notes: string | null
  active: boolean; previousUnitCost: number | null; priceChangedAt: string | null; source: "MANUAL" | "IMPORT"; updatedAt: string
}

export interface RateRequest {
  category: RateCategory; itemRef: string; description: string; unit: string; unitCost: number; supplier: string; notes: string; active?: boolean
}

export interface ImportRequest { csv: string; supplier: string; defaultCategory: string; dryRun: boolean }

export interface ImportResult {
  dryRun: boolean; created: number; updated: number; unchanged: number; skipped: number
  priceChanges: { description: string; unit: string; supplier: string; from: number; to: number }[]
  problems: { line: number; message: string }[]
}

const KEY = ["ct-rates"]
const base = "/api/v1/compliance/rates"

export function useRates(enabled = true) {
  return useQuery<Rate[]>({ queryKey: KEY, enabled, queryFn: async () => { const d = (await apiClient.get(base)).data; return Array.isArray(d) ? d : [] } })
}

export function useRateMutations() {
  const qc = useQueryClient()
  const refresh = () => qc.invalidateQueries({ queryKey: KEY })
  return {
    create: useMutation({ mutationFn: async (r: RateRequest) => (await apiClient.post(base, r)).data as Rate, onSuccess: refresh }),
    update: useMutation({ mutationFn: async (a: { id: string; req: RateRequest }) => (await apiClient.put(`${base}/${a.id}`, a.req)).data as Rate, onSuccess: refresh }),
    remove: useMutation({ mutationFn: async (id: string) => { await apiClient.delete(`${base}/${id}`) }, onSuccess: refresh }),
    importCsv: useMutation({
      mutationFn: async (r: ImportRequest) => (await apiClient.post(`${base}/import`, r)).data as ImportResult,
      onSuccess: (res) => { if (!res.dryRun) refresh() },
    }),
  }
}
