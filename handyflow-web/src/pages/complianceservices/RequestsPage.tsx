// src/pages/complianceservices/RequestsPage.tsx
//
// Every client query across the practice, those waiting for an answer first. Open one to read and reply; it is recorded under the client.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import QueryThread from "./QueryThread"
import { filterQueries, queryStatusLabel, sortQueries, type StaffQuery } from "./contactRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const input: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", minWidth: 200 }
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })
const FILTERS: [string, string][] = [["ALL", "All"], ["OPEN", "Waiting for us"], ["ANSWERED", "Answered"], ["CLOSED", "Closed"]]

export default function RequestsPage() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [status, setStatus] = useState("OPEN")
  const [search, setSearch] = useState("")
  const [open, setOpen] = useState<string | null>(null)
  const { data = [], isLoading, error } = useQuery<StaffQuery[]>({ queryKey: ["cs-portal-queries-all"], queryFn: async () => unwrap<StaffQuery[]>(await apiClient.get("/api/v1/compliance-services/portal-queries")) })
  const shown = sortQueries(filterQueries(data, status, search))
  const changed = () => { qc.invalidateQueries({ queryKey: ["cs-portal-queries-all"] }); qc.invalidateQueries({ queryKey: ["cs-portal-queries"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }) }
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Client queries</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>What clients have written to you through the portal. Documents you have asked for are on each client's Requests tab.</div></div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {FILTERS.map(([id, text]) => <button key={id} style={chip(status === id)} onClick={() => setStatus(id)}>{text}</button>)}
        <input aria-label="Search" placeholder="Search client or subject" style={input} value={search} onChange={e => setSearch(e.target.value)} />
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {error != null && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text, #c0392b)" }}>Could not load the queries</div>}
      {!isLoading && !error && shown.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No queries match.</div>}
      {shown.map(q => (
        <div key={q.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, display: "grid", gap: 8 }}>
          <div style={{ display: "flex", gap: 10, alignItems: "baseline", flexWrap: "wrap" }}>
            <button onClick={() => setOpen(open === q.id ? null : q.id)} aria-expanded={open === q.id} style={{ all: "unset", cursor: "pointer", fontWeight: 600, fontSize: 13 }}>{q.subject}</button>
            <Link to={`/complianceservices/clients/${q.clientId}?tab=requests`} style={{ fontSize: 12 }}>{q.clientName ?? "Client"}</Link>
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{queryStatusLabel(q.status)}</span>
          </div>
          {open === q.id && <QueryThread id={q.id} canManage={canManage} onChanged={changed} />}
        </div>
      ))}
    </div>
  )
}
