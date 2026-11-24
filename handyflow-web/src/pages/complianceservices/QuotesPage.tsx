// src/pages/complianceservices/QuotesPage.tsx
//
// Every quote across clients, with a filter by status. A quote waiting for approval is the administrator's queue.
// Opening a quote goes to the client's Quotes tab, where it is read and worked on.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { formatMoney } from "./pricingRules"
import { FILTERS, APPROVER_LABELS, statusLabel } from "./quoteRules"

interface Summary { id: string; clientId: string; clientName: string | null; quoteNumber: string; title: string | null; status: string; validUntil: string | null; total: number; requiredApprover: string; maxDiscountPct: number }

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })

export default function QuotesPage() {
  const nav = useNavigate()
  const [status, setStatus] = useState("")
  const { data: list = [], isLoading } = useQuery<Summary[]>({
    queryKey: ["cs-quotes-all", status],
    queryFn: async () => unwrap<Summary[]>(await apiClient.get("/api/v1/compliance-services/quotes", { params: status ? { status } : {} })),
  })
  const total = list.reduce((n, q) => n + (q.total ?? 0), 0)

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>All quotes, newest first. Open one to read it or work on it in the client's Quotes tab.</div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {FILTERS.map(f => <button key={f[0]} style={chip(status === f[0])} onClick={() => setStatus(f[0])}>{f[1]}</button>)}
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && list.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No quotes{status ? ` that are ${statusLabel(status).toLowerCase()}` : ""}.</div>}
      {list.map(q => (
        <button key={q.id} onClick={() => nav(`/complianceservices/clients/${q.clientId}?tab=quotes`)}
          style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", background: "transparent", textAlign: "left", cursor: "pointer", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{q.quoteNumber}</span>
          <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{q.clientName ?? "Unknown client"}{q.title ? ` · ${q.title}` : ""}</span>
          {q.status === "PENDING_APPROVAL" && <span style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)" }}>{APPROVER_LABELS[q.requiredApprover]}</span>}
          <span style={{ flex: 1 }} />
          <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{statusLabel(q.status)}{q.validUntil ? ` · until ${q.validUntil}` : ""} · {formatMoney(q.total)}</span>
        </button>
      ))}
      {list.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{list.length} quote{list.length === 1 ? "" : "s"} · {formatMoney(total)} including VAT</div>}
    </div>
  )
}
