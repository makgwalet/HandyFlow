// src/pages/invoicing/queries.ts
//
// Data hooks shared by the billing screens. The query keys are the ones the create forms already invalidate
// ('quotes', 'invoices', 'recurring-schedules'), so a new quote, retainer or schedule appears without a reload.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { LIST_LIMIT, type CreditNote, type Invoice, type Quote, type RecurringSchedule } from "./billing.logic"

export interface Loaded<T> { rows: T[]; total: number }
const unwrap = (r: any) => r.data?.data ?? r.data

async function loadPage<T>(path: string): Promise<Loaded<T>> {
  const p = unwrap(await apiClient.get(`${path}${path.includes("?") ? "&" : "?"}size=${LIST_LIMIT}&sort=createdAt,desc`))
  const rows: T[] = p?.content ?? (Array.isArray(p) ? p : [])
  return { rows, total: p?.totalElements ?? rows.length }
}

export const useQuotes = () => useQuery<Loaded<Quote>>({ queryKey: ["quotes"], queryFn: () => loadPage<Quote>("/api/v1/invoicing/quotes") })
export const useInvoices = () => useQuery<Loaded<Invoice>>({ queryKey: ["invoices"], queryFn: () => loadPage<Invoice>("/api/v1/invoicing/invoices") })
export const useSchedules = () => useQuery<Loaded<RecurringSchedule>>({ queryKey: ["recurring-schedules"], queryFn: () => loadPage<RecurringSchedule>("/api/v1/invoicing/recurring-schedules") })
export const useAllCreditNotes = () => useQuery<Loaded<CreditNote>>({ queryKey: ["credit-notes-all"], queryFn: () => loadPage<CreditNote>("/api/v1/invoicing/credit-notes") })

/** id -> name for the customers of this tenant, so lists show names, never ids. */
export function useCustomerNames() {
  const q = useQuery<Record<string, string>>({
    queryKey: ["customers-map"],
    staleTime: 60_000,
    queryFn: async () => {
      const p = unwrap(await apiClient.get("/api/v1/crm/customers?size=500"))
      const rows: { id: string; name: string }[] = p?.content ?? (Array.isArray(p) ? p : [])
      return Object.fromEntries(rows.map(c => [c.id, c.name]))
    },
  })
  return q.data ?? {}
}

/** Downloads a PDF the server renders. Resolves to an error message, or null on success. */
export async function downloadPdf(path: string, fileName: string): Promise<string | null> {
  try {
    const res = await apiClient.get(path, { responseType: "blob" } as any)
    const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
    const a = document.createElement("a"); a.href = url; a.download = fileName; a.click()
    URL.revokeObjectURL(url)
    return null
  } catch {
    return "The PDF could not be downloaded. Please try again."
  }
}

/** The message the server gave for a failed call, or a plain fallback. */
export function apiMessage(e: any, fallback: string): string {
  const d = e?.response?.data
  if (d?.data && typeof d.data === "object" && !Array.isArray(d.data)) {
    const first = Object.entries(d.data)[0] as [string, string] | undefined
    if (first) return `${first[0]}: ${first[1]}`
  }
  return d?.message ?? fallback
}
