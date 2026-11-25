// src/pages/complianceservices/PortfolioPage.tsx
//
// Early warning: which clients show signs of trouble, and why, the ones with the most serious signs first. It reads what is already recorded.
// It gives no score and does not say a client complies; a client that is not listed only has nothing recorded that matches these signs.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { FILTERS, counts, filtered, headline, linkFor, severityLabel, type Summary } from "./portfolioRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const input: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", minWidth: 200 }
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })
const tag = (severity: string): React.CSSProperties => ({
  fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 999, whiteSpace: "nowrap",
  border: `1px solid ${severity === "HIGH" ? "var(--hf-danger-text, #c0392b)" : "var(--hf-border)"}`,
  color: severity === "HIGH" ? "var(--hf-danger-text, #c0392b)" : "var(--hf-text-muted)",
})

export default function PortfolioPage() {
  const [filter, setFilter] = useState("ALL")
  const [search, setSearch] = useState("")
  const { data, isLoading, error } = useQuery<Summary>({
    queryKey: ["cs-portfolio"],
    queryFn: async () => unwrap<Summary>(await apiClient.get("/api/v1/compliance-services/portfolio/early-warning")),
  })
  if (isLoading) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>
  if (error || !data) return <div style={{ fontSize: 13, color: "var(--hf-danger-text, #c0392b)" }}>The early warning list could not be loaded.</div>
  const shown = filtered(data.clients, filter, search)
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text)" }}>{headline(data)}</div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        Signs are taken from what is recorded: deadlines, documents, cases, regulatory changes and agreements. This list is a place to start looking. It is not a score and not a statement that any client does or does not comply.
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {FILTERS.map(f => <button key={f[0]} style={chip(filter === f[0])} aria-pressed={filter === f[0]} onClick={() => setFilter(f[0])}>{f[1]}</button>)}
        <span style={{ flex: 1 }} />
        <input style={input} aria-label="Search clients" placeholder="Search clients" value={search} onChange={e => setSearch(e.target.value)} />
      </div>
      {data.clients.length > 0 && shown.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No clients match.</div>}
      {shown.map(c => (
        <div key={c.clientId} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", display: "grid", gap: 6 }}>
          <div style={{ display: "flex", gap: 10, alignItems: "baseline", flexWrap: "wrap" }}>
            <Link to={linkFor(c.clientId, "profile")} style={{ fontWeight: 700, fontSize: 14 }}>{c.clientName}</Link>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{counts(c)}</span>
          </div>
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 4 }}>
            {c.signals.map(s => (
              <li key={s.code} style={{ display: "flex", gap: 8, alignItems: "baseline", flexWrap: "wrap" }}>
                <span style={tag(s.severity)}>{severityLabel(s.severity)}</span>
                <Link to={linkFor(c.clientId, s.tab)} style={{ fontSize: 13 }}>{s.label}</Link>
                <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{s.detail}</span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}
