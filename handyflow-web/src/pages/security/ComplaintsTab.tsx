// src/pages/security/ComplaintsTab.tsx
//
// The Complaints section: every guard complaint with filters, and a form to log a new one. A row opens the
// complaint's own page (/security/complaints/:id). `guardId` limits the list to one guard (used on Guard 360).
import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { AlertOctagon, Plus } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import ComplaintForm from "./ComplaintForm"
import {
  CATEGORIES, COMPLAINT_SEVERITY_TONE, SEVERITIES, STATUS_FILTERS, categoryLabel, findingLabel, severityLabel, statusLabel, statusTone,
  type ComplaintSummary,
} from "./complaints.logic"

const sel: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)" }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }
const primary: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "none", borderRadius: 9, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const fmt = (d: string) => new Date(d + "T00:00:00").toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

export default function ComplaintsTab({ guardId }: { guardId?: string }) {
  const navigate = useNavigate()
  const canManage = usePermission("SECURITY_MANAGE") || usePermission("SECURITY_ADMIN")
  const [status, setStatus] = useState("OPEN")
  const [severity, setSeverity] = useState("")
  const [category, setCategory] = useState("")
  const [adding, setAdding] = useState(false)

  const { data, isLoading, error } = useQuery<ComplaintSummary[]>({
    queryKey: ["complaints", guardId ?? "all", status, severity, category],
    queryFn: async () => {
      const q = new URLSearchParams({ size: "100" })
      if (status) q.set("status", status)
      if (severity) q.set("severity", severity)
      if (category) q.set("category", category)
      if (guardId) q.set("guardId", guardId)
      const r = await apiClient.get(`/api/v1/security/complaints?${q}`)
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as ComplaintSummary[]
    },
  })
  const rows = data ?? []

  return (
    <div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <select aria-label="Status" value={status} onChange={e => setStatus(e.target.value)} style={sel}>
          <option value="">Any status</option>{STATUS_FILTERS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}</select>
        <select aria-label="Severity" value={severity} onChange={e => setSeverity(e.target.value)} style={sel}>
          <option value="">Any severity</option>{SEVERITIES.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}</select>
        <select aria-label="Category" value={category} onChange={e => setCategory(e.target.value)} style={sel}>
          <option value="">Any category</option>{CATEGORIES.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}</select>
        {canManage && <button onClick={() => setAdding(true)} style={{ ...primary, marginLeft: "auto" }}><Plus size={14} /> Log complaint</button>}
      </div>

      {adding && <div style={{ marginBottom: 12 }}><ComplaintForm fixedGuardId={guardId} onClose={() => setAdding(false)} onSaved={id => id && navigate(`/security/complaints/${id}`)} /></div>}

      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, overflowX: "auto" }}>
        {isLoading ? <div style={{ color: "var(--hf-text-muted)" }}>Loading complaints...</div>
          : error ? <div role="alert" style={{ color: "var(--hf-danger-text)" }}>Complaints could not be loaded.</div>
          : rows.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No complaints match these filters.</div>
          : <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Number</th>{!guardId && <th style={th}>Guard</th>}<th style={th}>Date</th><th style={th}>Category</th><th style={th}>Severity</th><th style={th}>Status</th><th style={th}>Finding</th></tr></thead>
              <tbody>{rows.map(c => (
                <tr key={c.id}>
                  <td style={td}>
                    <Link to={`/security/complaints/${c.id}`} style={{ color: "var(--hf-accent-text)", fontWeight: 700, textDecoration: "none" }}>{c.complaintNumber}</Link>
                    {c.urgent && <span style={{ marginLeft: 6 }}><Chip tone="bad" icon={<AlertOctagon size={11} />}>urgent</Chip></span>}
                  </td>
                  {!guardId && <td style={td}>{c.guardName ?? "-"}</td>}
                  <td style={td}>{fmt(c.occurredOn)}</td>
                  <td style={td}>{categoryLabel(c.category)}</td>
                  <td style={td}><Chip tone={COMPLAINT_SEVERITY_TONE[c.severity] ?? "neutral"}>{severityLabel(c.severity)}</Chip></td>
                  <td style={td}><Chip tone={statusTone(c.status, c.finding)}>{statusLabel(c.status)}</Chip></td>
                  <td style={td}>{findingLabel(c.finding)}</td>
                </tr>
              ))}</tbody>
            </table>}
      </div>
    </div>
  )
}
