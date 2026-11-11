// src/pages/events/vendorOps.ts — pure helpers for the vendors table: filtering, sorting, totals and joining the finance figures.
export interface Vendor { id: string; vendorType: string; companyName: string; contactName: string | null; contactPhone: string | null; contactEmail: string | null; serviceDescription: string | null; quotedAmount: number | null; confirmed: boolean; notes: string | null }
export interface SupplierFin { vendorId: string; committed: number; invoiced: number; paid: number; outstanding: number }
export type SortKey = "companyName" | "vendorType" | "quotedAmount" | "status"
export type StatusFilter = "ALL" | "CONFIRMED" | "AWAITING"
export interface Filters { q: string; type: string; status: StatusFilter }

export function filterVendors(list: Vendor[], f: Filters): Vendor[] {
  const q = f.q.trim().toLowerCase()
  return list.filter(v =>
    (f.type === "ALL" || v.vendorType === f.type) &&
    (f.status === "ALL" || (f.status === "CONFIRMED") === v.confirmed) &&
    (q === "" || [v.companyName, v.contactName, v.contactEmail, v.serviceDescription].some(x => (x ?? "").toLowerCase().includes(q))))
}

/** Missing quotes sort last whichever way you sort amounts, so blanks never crowd the top. */
export function sortVendors(list: Vendor[], key: SortKey, dir: "asc" | "desc"): Vendor[] {
  const s = dir === "asc" ? 1 : -1
  return [...list].sort((a, b) => {
    if (key === "quotedAmount") { const x = a.quotedAmount, y = b.quotedAmount; if (x == null && y == null) return a.companyName.localeCompare(b.companyName); if (x == null) return 1; if (y == null) return -1; return (x - y) * s || a.companyName.localeCompare(b.companyName) }
    if (key === "status") return (Number(a.confirmed) - Number(b.confirmed)) * s || a.companyName.localeCompare(b.companyName)
    return (a[key] ?? "").localeCompare(b[key] ?? "") * s || a.companyName.localeCompare(b.companyName)
  })
}

export const vendorTotals = (list: Vendor[]) => ({ count: list.length, confirmed: list.filter(v => v.confirmed).length, awaiting: list.filter(v => !v.confirmed).length, quoted: list.reduce((a, v) => a + (v.quotedAmount ?? 0), 0), confirmedQuoted: list.filter(v => v.confirmed).reduce((a, v) => a + (v.quotedAmount ?? 0), 0) })
export const finFor = (fin: SupplierFin[], id: string) => fin.find(x => x.vendorId === id)
/** A confirmed vendor with nothing committed in the budget is a booking the finance side does not know about. */
export const uncommitted = (v: Vendor, fin: SupplierFin[] | undefined) => !!fin && v.confirmed && (finFor(fin, v.id)?.committed ?? 0) === 0
export const typeCounts = (list: Vendor[]) => Object.entries(list.reduce<Record<string, number>>((m, v) => ((m[v.vendorType] = (m[v.vendorType] ?? 0) + 1), m), {})).sort((a, b) => b[1] - a[1])
export const typeText = (t: string) => t.charAt(0) + t.slice(1).toLowerCase().replace(/_/g, " ")
