// src/pages/security/AccessLogTab.tsx
//
// Who revealed a guard's ID or bank number, downloaded evidence, exported payroll or opened vetting. SECURITY_ADMIN only (the server enforces it).
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { RefreshCw } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { KIND_OPTIONS, LIMIT_NOTE, emptyLine, isSupportSession, readEntries, subject, what, whenLabel, who, type AccessLogEntry } from "./accessLogLogic"
import { useCompanyZone } from "./companyZone"

const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "7px 11px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const th: React.CSSProperties = { textAlign: "left", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, padding: "6px 8px", borderBottom: "1px solid var(--hf-border)" }
const td: React.CSSProperties = { padding: "8px", fontSize: 13, borderBottom: "1px solid var(--hf-border)", verticalAlign: "top" }

export default function AccessLogTab() {
  useCompanyZone()
  const [kind, setKind] = useState("")
  const q = useQuery<AccessLogEntry[]>({
    queryKey: ["security-access-log", kind],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/access-log", { params: { limit: 200, ...(kind ? { entityType: kind } : {}) } })
      return readEntries(r.data?.data ?? r.data)
    },
    retry: false,
  })
  const rows = q.data ?? []
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <label style={{ fontSize: 12 }}>Show{" "}
          <select aria-label="Kind of record" value={kind} onChange={e => setKind(e.target.value)} style={input}>
            {KIND_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </label>
        <button type="button" style={btn} onClick={() => q.refetch()} disabled={q.isFetching}><RefreshCw size={14} /> Refresh</button>
        <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{LIMIT_NOTE}</span>
      </div>
      {q.isError ? (
        <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The access log could not be loaded. Only a Security administrator can see it.</div>
      ) : q.isLoading ? (
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div>
      ) : rows.length === 0 ? (
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{emptyLine(kind)}</div>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>When</th><th style={th}>Who</th><th style={th}>What</th><th style={th}>About</th></tr></thead>
            <tbody>
              {rows.map(e => (
                <tr key={e.id}>
                  <td style={td}>{whenLabel(e.occurredAt)}</td>
                  <td style={td}>{who(e)} {isSupportSession(e) && <Chip tone="warn">Support</Chip>}</td>
                  <td style={td}>{what(e)}</td>
                  <td style={td}>{subject(e)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
