// src/pages/complianceservices/AgreementsPage.tsx
//
// Every agreement across clients, with a filter by status. Opening one goes to the client's Agreements tab, where it
// is read and worked on.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { formatMoney } from "./pricingRules"
import AgreementSettingsPanel from "./AgreementSettingsPanel"
import { FILTERS, frequencyLabel, statusLabel } from "./agreementRules"

interface Summary {
  id: string; clientId: string; clientName: string | null; quoteNumber: string | null; agreementNumber: string; title: string | null; status: string
  startDate: string; endDate: string | null; billingFrequency: string; billingAmount: number; nextBillingDate: string | null; attention: string | null
}

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })

export default function AgreementsPage() {
  const nav = useNavigate()
  const [status, setStatus] = useState("")
  const { data: list = [], isLoading } = useQuery<Summary[]>({
    queryKey: ["cs-agreements-all", status],
    queryFn: async () => unwrap<Summary[]>(await apiClient.get("/api/v1/compliance-services/agreements", { params: status ? { status } : {} })),
  })

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>All agreements, newest first. Open one to read it or work on it in the client's Agreements tab.</div>
      <AgreementSettingsPanel />
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {FILTERS.map(f => <button key={f[0]} style={chip(status === f[0])} onClick={() => setStatus(f[0])}>{f[1]}</button>)}
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && list.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No agreements{status ? ` that are ${statusLabel(status).toLowerCase()}` : ""}.</div>}
      {list.map(a => (
        <button key={a.id} onClick={() => nav(`/complianceservices/clients/${a.clientId}?tab=agreements`)}
          style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", background: "transparent", textAlign: "left", cursor: "pointer", display: "grid", gap: 4 }}>
          <span style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{a.agreementNumber}</span>
            <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{a.clientName ?? "Unknown client"}{a.title ? ` · ${a.title}` : ""}</span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
              {statusLabel(a.status)} · {formatMoney(a.billingAmount)} {frequencyLabel(a.billingFrequency).toLowerCase()}{a.nextBillingDate ? ` · next ${a.nextBillingDate}` : ""}
            </span>
          </span>
          {a.attention && <span style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)" }}>{a.attention}</span>}
        </button>
      ))}
      {list.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{list.length} agreement{list.length === 1 ? "" : "s"}</div>}
    </div>
  )
}
