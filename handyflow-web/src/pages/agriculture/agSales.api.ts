// src/pages/agriculture/agSales.api.ts
//
// Linking invoice lines to production (ADR-001, W2). Agriculture stores NO sales or money: revenue is computed live by the server from
// the invoice (ex-VAT, net of credit notes, only while the invoice is issued). Every endpoint needs AGRICULTURE_FINANCE and INVOICE_READ.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { api, useAgMutation } from "./agCrops.api"
import type { TargetFilter, TargetType } from "./agLedger.api"

export interface SaleLine {
  invoiceId: string; invoiceNumber: string; invoiceStatus: string; customerId: string | null; customerName: string | null; issuedOn: string | null
  currency: string; lineItemId: string; description: string; unit: string | null; quantity: number; unitPrice: number
  lineTotal: number            // ex-VAT, before credit notes
  netRevenue: number           // ex-VAT, after the line's share of credit notes
  allocatedQuantity: number; remainingQuantity: number
}

export interface SalesAllocation {
  id: string; farmId: string; invoiceId: string; invoiceNumber: string | null; invoiceLineId: string; description: string | null; customerName: string | null
  targetType: TargetType; targetId: string; quantity: number; unit: string | null; headCount: number | null; soldOn: string; notes: string | null; status: string
  invoiceStatus: string | null
  revenue: number; counted: boolean; notCountedReason: string | null; createdAt: string
}

export interface SalesTotals { revenue: number; allocationCount: number; notCountedCount: number; byTarget: { targetType: TargetType; targetId: string; quantity: number; revenue: number }[] }

export interface SaleLineQuery { from: string; to: string; q: string }

export interface AllocateBody {
  invoiceLineId: string; soldOn?: string; notes?: string
  allocations: { targetType: TargetType; targetId: string; quantity: number; headCount?: number }[]
}

const BASE = "/api/v1/agriculture"
const SALES = ["ag", "sales"] as const

export function useSaleLines(farmId: string, query: SaleLineQuery, enabled: boolean) {
  return useQuery<SaleLine[]>({
    queryKey: [...SALES, "lines", farmId, query.from, query.to, query.q], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/sales/lines`, { params: { from: query.from || undefined, to: query.to || undefined, q: query.q.trim() || undefined } })).data ?? [],
  })
}

export function useSalesAllocations(farmId: string, target: TargetFilter | null, enabled = true) {
  return useQuery<{ content: SalesAllocation[]; totalElements: number }>({
    queryKey: [...SALES, "allocations", farmId, target?.type ?? "all", target?.id ?? ""], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/sales-allocations`, { params: { targetType: target?.type, targetId: target?.id, size: 200 } })).data,
  })
}

export function useSalesTotals(farmId: string, target: TargetFilter | null, enabled = true) {
  return useQuery<SalesTotals>({
    queryKey: [...SALES, "totals", farmId, target?.type ?? "all", target?.id ?? ""], enabled: enabled && !!farmId,
    queryFn: async () => (await apiClient.get(`${BASE}/farms/${farmId}/sales-allocations/totals`, { params: { targetType: target?.type, targetId: target?.id } })).data,
  })
}

export function useAllocateSale(farmId: string) {
  return useAgMutation((body: AllocateBody) => api.post(`/farms/${farmId}/sales-allocations`, body),
    { invalidate: [[...SALES]], success: "Sale allocated to production", failure: "Couldn't allocate the sale." })
}

export function useRemoveAllocation() {
  return useAgMutation((id: string) => api.del(`/sales-allocations/${id}`),
    { invalidate: [[...SALES]], success: "Allocation removed", failure: "Couldn't remove the allocation." })
}
