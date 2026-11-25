// src/pages/complianceservices/BillingPage.tsx
//
// The billing worklist across clients: bills ready to raise, upcoming ones, and what has been invoiced. Raising a bill
// makes a draft invoice in Invoicing, where someone reviews and issues it.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import BillingEventRow, { type BillingEvent } from "./BillingEventRow"
import { FILTERS, byUrgency } from "./billingRules"
import { formatMoney } from "./pricingRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })

export default function BillingPage() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_BILLING_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [filter, setFilter] = useState("DUE")
  const { data = [], isLoading } = useQuery<BillingEvent[]>({
    queryKey: ["cs-billing-all", filter],
    queryFn: async () => unwrap<BillingEvent[]>(await apiClient.get("/api/v1/compliance-services/billing-events", { params: filter ? { filter } : {} })),
  })
  const rows = byUrgency(data)
  const total = rows.reduce((n, e) => n + (e.amount ?? 0), 0)
  const changed = () => { qc.invalidateQueries({ queryKey: ["cs-billing-all"] }); qc.invalidateQueries({ queryKey: ["cs-billing"] }) }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Scheduled bills from active agreements. Raising one makes a draft invoice in Invoicing; nobody is billed until someone reviews and issues it there. Make a bill list for an agreement from its Agreements tab.
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {FILTERS.map(f => <button key={f[0]} style={chip(filter === f[0])} onClick={() => setFilter(f[0])}>{f[1]}</button>)}
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && rows.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing here.</div>}
      {rows.map(e => <BillingEventRow key={e.id} event={e} canManage={canManage} showClient onChanged={changed} />)}
      {rows.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{rows.length} bill{rows.length === 1 ? "" : "s"} · {formatMoney(total)} before VAT</div>}
    </div>
  )
}
