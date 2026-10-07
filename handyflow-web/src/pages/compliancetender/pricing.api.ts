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

/**
 * Where a tender's pricing lives. The company's own tenders and a client's tenders (complianceservices) have the same screen and the same shapes; only the addresses, the
 * permissions and the cache key differ.
 */
export interface PricingScope {
  key: string                                  // cache key prefix
  apiBase: string                              // e.g. /api/v1/compliance
  manage: string; admin: string                // the permissions that allow pricing (it is commercially sensitive, so READ alone is not enough)
  tenderPage: (id: string) => string           // the tender's own page, for links back
  pricingPage: (id: string) => string
  rates: boolean                               // whether the rates library can be used here
}
export const COMPANY_SCOPE: PricingScope = {
  key: "ct-tender-pricing", apiBase: "/api/v1/compliance", manage: "COMPLIANCE_MANAGE", admin: "COMPLIANCE_ADMIN",
  tenderPage: id => `/compliancetender/tenders/${id}`, pricingPage: id => `/compliancetender/tenders/${id}/pricing`, rates: true,
}
export const CLIENT_SCOPE: PricingScope = {
  key: "cs-tender-pricing", apiBase: "/api/v1/compliance-services", manage: "COMPLIANCE_SERVICES_MANAGE", admin: "COMPLIANCE_SERVICES_ADMIN",
  tenderPage: id => `/complianceservices/tenders/${id}`, pricingPage: id => `/complianceservices/tenders/${id}/pricing`, rates: false,
}

const base = (scope: PricingScope, tenderId: string) => `${scope.apiBase}/tenders/${tenderId}/pricing`
const lineUrl = (scope: PricingScope, lineId: string) => `${scope.apiBase}/tenders/pricing/lines/${lineId}`

export function usePricing(tenderId: string | undefined, enabled = true, scope: PricingScope = COMPANY_SCOPE) {
  return useQuery<TenderPricing>({
    queryKey: [scope.key, tenderId], enabled: !!tenderId && enabled,
    queryFn: async () => (await apiClient.get(base(scope, tenderId!))).data,
  })
}

/** Every pricing write returns the re-priced schedule, which replaces what is cached, so the totals on screen can never lag behind the server. */
export function usePricingMutations(tenderId: string, scope: PricingScope = COMPANY_SCOPE) {
  const qc = useQueryClient()
  const key = [scope.key, tenderId]
  const store = (p: TenderPricing) => qc.setQueryData(key, p)
  return {
    saveSettings: useMutation({ mutationFn: async (r: SettingsRequest) => (await apiClient.put(`${base(scope, tenderId)}/settings`, r)).data as TenderPricing, onSuccess: store }),
    addLine: useMutation({ mutationFn: async (r: LineRequest) => (await apiClient.post(`${base(scope, tenderId)}/lines`, r)).data as TenderPricing, onSuccess: store }),
    updateLine: useMutation({ mutationFn: async (a: { id: string; req: LineRequest }) => (await apiClient.put(lineUrl(scope, a.id), a.req)).data as TenderPricing, onSuccess: store }),
    deleteLine: useMutation({ mutationFn: async (id: string) => (await apiClient.delete(lineUrl(scope, id))).data as TenderPricing, onSuccess: store }),
  }
}
