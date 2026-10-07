// src/pages/clinic/AccessLogTab.tsx
// Administrator view of who opened which patient record and when (POPIA accountability). Read-only: entries are
// written by the server and cannot be changed here. Only successful reads of a patient, consultation or lab result
// are recorded, and list and search screens are not (see backlog S1-6).
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface AccessEntry {
  id: string; user_name?: string | null; user_id?: string | null; patient_name?: string | null; patient_id?: string | null
  resource_type: string; http_method: string; ip_address?: string | null; impersonated?: boolean; accessed_at: string
}
const unwrap = (r: any) => r.data?.data ?? r.data
const RESOURCE: Record<string, string> = { PATIENT: "Patient file", CONSULTATION: "Consultation", LAB_RESULT: "Lab result", OTHER: "Other" }

/** Case-insensitive match on the person who looked or the patient looked at. */
export const matches = (e: AccessEntry, q: string) => {
  const s = q.trim().toLowerCase()
  return !s || [e.user_name, e.patient_name].some(v => (v ?? "").toLowerCase().includes(s))
}

export default function AccessLogTab() {
  const [limit, setLimit] = useState(100)
  const [q, setQ] = useState("")
  const { data, isLoading, isError } = useQuery<AccessEntry[]>({
    queryKey: ["clinic-access-log", limit], retry: false,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/access-log?limit=${limit}`)) ?? [],
  })
  const rows = (data ?? []).filter(e => matches(e, q))
  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 12, flexWrap: "wrap" }}>
        <input aria-label="Filter by name" placeholder="Filter by staff or patient name" value={q} onChange={e => setQ(e.target.value)} style={{ minWidth: 240 }} />
        <select aria-label="How many entries" value={limit} onChange={e => setLimit(Number(e.target.value))}>
          {[100, 250, 500].map(n => <option key={n} value={n}>Latest {n}</option>)}
        </select>
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div>}
      {isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The access log could not be loaded. You need administrator rights to see it.</div>}
      {!isLoading && !isError && rows.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No matching entries.</div>}
      {rows.length > 0 && (
        <table style={{ borderCollapse: "collapse", width: "100%", fontSize: 12 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}>
            <th>When</th><th>Who</th><th>Opened</th><th>Patient</th><th>From</th>
          </tr></thead>
          <tbody>
            {rows.map(e => (
              <tr key={e.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={{ padding: "4px 8px 4px 0", whiteSpace: "nowrap" }}>{new Date(e.accessed_at).toLocaleString("en-ZA")}</td>
                <td>{e.user_name ?? (e.user_id ? "Unknown user" : "—")}{e.impersonated ? " (impersonated)" : ""}</td>
                <td>{RESOURCE[e.resource_type] ?? e.resource_type}</td>
                <td>{e.patient_name ?? (e.patient_id ? "Unknown patient" : "—")}</td>
                <td>{e.ip_address ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
