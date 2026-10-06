// src/pages/compliancetender/pricing.api.ts
//
// Tender pricing (ADR-004): the cost-based price schedule of the tenant's own tender. The server does every calculation (TenderPriceCalculator); the screen only shows what comes back.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface PricingSettings {
  overheadPct: number; contingencyPct: number; profitPct: number
  vatApplies: boolean; vatRatePct: number; notes: string | null
}

export interface PricingLine {
  id: string; section: string; itemRef: string | null; description: string; unit: string | null
  quantity: number; unitCost: number; lineTotal: number; sortOrder: number
}

export interface PricingSection { section: string; lineCount: number; subtotal: number }

export interface PricingBreakdown {
  directCost: number; overhead: number; contingency: number; profit: number
  priceExVat: number; vat: number; priceInclVat: number
  marginPct: number | null               // profit as a share of the price ex VAT; null when there is no price yet
  sections: PricingSection[]
}

export interface TenderPricing {
  tenderId: string; tenderStatus: string
  editable: boolean                      // false once the tender has been submitted
  configured: boolean                    // false until something has been saved; settings are then the defaults
  estimatedValue: number | null          // as entered on the tender; whether it includes VAT is not recorded
  settings: PricingSettings
  lines: PricingLine[]
  breakdown: PricingBreakdown
}

export interface LineRequest { section: string; itemRef: string; description: string; unit: string; quantity: number; unitCost: number }
export interface SettingsRequest { overheadPct: number; contingencyPct: number; profitPct: number; vatApplies: boolean; notes: string }

const base = (tenderId: string) => `/api/v1/compliance/tenders/${tenderId}/pricing`
const lineUrl = (lineId: string) => `/api/v1/compliance/tenders/pricing/lines/${lineId}`

export function usePricing(tenderId: string | undefined, enabled = true) {
  return useQuery<TenderPricing>({
    queryKey: ["ct-tender-pricing", tenderId], enabled: !!tenderId && enabled,
    queryFn: async () => (await apiClient.get(base(tenderId!))).data,
  })
}

/** Every pricing write returns the re-priced schedule, which replaces what is cached, so the totals on screen can never lag behind the server. */
export function usePricingMutations(tenderId: string) {
  const qc = useQueryClient()
  const key = ["ct-tender-pricing", tenderId]
  const store = (p: TenderPricing) => qc.setQueryData(key, p)
  return {
    saveSettings: useMutation({ mutationFn: async (r: SettingsRequest) => (await apiClient.put(`${base(tenderId)}/settings`, r)).data as TenderPricing, onSuccess: store }),
    addLine: useMutation({ mutationFn: async (r: LineRequest) => (await apiClient.post(`${base(tenderId)}/lines`, r)).data as TenderPricing, onSuccess: store }),
    updateLine: useMutation({ mutationFn: async (a: { id: string; req: LineRequest }) => (await apiClient.put(lineUrl(a.id), a.req)).data as TenderPricing, onSuccess: store }),
    deleteLine: useMutation({ mutationFn: async (id: string) => (await apiClient.delete(lineUrl(id))).data as TenderPricing, onSuccess: store }),
  }
}
