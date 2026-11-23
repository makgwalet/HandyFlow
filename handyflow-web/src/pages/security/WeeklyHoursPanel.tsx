// src/pages/security/WeeklyHoursPanel.tsx
//
// Guards near or over their weekly hours on the roster, double bookings and short rests. The server decides who is flagged.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { useAuthStore } from "../../store/auth.store"
import { useCompanyZone } from "./companyZone"
import { STATE_TONE, addDays, canGoNextWeek, limitsText, mondayOf, problems, rowThresholds, stateLabel, summaryText, todaySast, weekTitle, type HoursReport } from "./weeklyHoursLogic"
import { DEFAULT_CATEGORY, categoriesOf, errorText, validPolicies } from "./workingTimeLogic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }
const th: React.CSSProperties = { textAlign: "left", padding: 10, fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: 10, fontSize: 13, borderTop: "1px solid var(--hf-border)", verticalAlign: "top" }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const navBtn: React.CSSProperties = { padding: "4px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", display: "inline-flex", alignItems: "center" }

export default function WeeklyHoursPanel() {
  const { ready: zoneReady } = useCompanyZone()
  const [picked, setPicked] = useState<string | null>(null)
  const week = picked ?? mondayOf(todaySast())
  const setWeek = (f: (w: string) => string) => setPicked(f(week))
  const [all, setAll] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const qc = useQueryClient()
  const canManage = useAuthStore(s => s.hasPermission("SECURITY_MANAGE"))
  const policies = useQuery({
    queryKey: ["working-time-policies"], enabled: canManage,
    queryFn: async () => (await apiClient.get("/api/v1/security/working-time-policies")).data?.data,
  })
  const categories = categoriesOf(validPolicies(policies.data) ?? [])
  const assign = useMutation({
    mutationFn: async (v: { guardId: string; category: string }) => { await apiClient.put(`/api/v1/security/guards/${v.guardId}/working-time-category`, { category: v.category }) },
    onSuccess: () => { setProblem(null); qc.invalidateQueries({ queryKey: ["weekly-hours"] }) },
    onError: e => setProblem(errorText(e, "The category could not be changed.")),
  })

  const { data, isLoading, error } = useQuery<HoursReport>({
    queryKey: ["weekly-hours", week, all], enabled: zoneReady,
    queryFn: async () => (await apiClient.get("/api/v1/security/workforce/weekly-hours", { params: { week, flaggedOnly: !all } })).data?.data,
  })
  const rows = Array.isArray(data?.rows) ? data!.rows : null

  return (
    <section aria-label="Weekly hours" style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
        <div style={{ fontWeight: 700 }}>Weekly hours</div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <button type="button" aria-label="Previous week" style={navBtn} onClick={() => setWeek(w => addDays(w, -7))}><ChevronLeft size={14} /></button>
          <span style={{ fontSize: 13, minWidth: 130, textAlign: "center" }}>{weekTitle(week)}</span>
          <button type="button" aria-label="Next week" style={{ ...navBtn, opacity: canGoNextWeek(week) ? 1 : 0.4 }} disabled={!canGoNextWeek(week)} onClick={() => setWeek(w => addDays(w, 7))}><ChevronRight size={14} /></button>
          <label style={{ fontSize: 13, display: "flex", gap: 4, alignItems: "center" }}><input type="checkbox" checked={all} onChange={e => setAll(e.target.checked)} />Show all rostered guards</label>
        </div>
      </div>

      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data && !rows)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Weekly hours could not be loaded.</div>}
      {data && rows && (
        <>
          <div style={{ fontWeight: 600, marginBottom: 8, color: data.flagged > 0 ? "var(--hf-danger-text)" : "var(--hf-text-primary)" }}>{summaryText(data)}</div>
          {problem && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginBottom: 8 }}>{problem}</div>}
          {rows.length > 0 && (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Guard</th><th style={th}>Rostered hours</th><th style={th}>Category</th><th style={th}>Status</th><th style={th}>Shifts</th><th style={th}>Problems</th></tr></thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.guardId}>
                      <td style={td}>{r.guardName ?? "Unknown guard"}</td>
                      <td style={td}>{r.hours} h</td>
                      <td style={td}>
                        {canManage && policies.isSuccess && validPolicies(policies.data) ? (
                          <select aria-label={`Category for ${r.guardName ?? "guard"}`} style={{ padding: "4px 6px", fontSize: 13, border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)" }}
                            value={r.category ?? DEFAULT_CATEGORY} disabled={assign.isPending}
                            onChange={e => assign.mutate({ guardId: r.guardId, category: e.target.value })}>
                            {Array.from(new Set([r.category ?? DEFAULT_CATEGORY, ...categories])).map(c => <option key={c} value={c}>{c === DEFAULT_CATEGORY ? "Default" : c}</option>)}
                          </select>
                        ) : (r.category ?? DEFAULT_CATEGORY) === DEFAULT_CATEGORY ? <span style={muted}>Default</span> : <span>{r.category}</span>}
                      </td>
                      <td style={td}><Chip tone={STATE_TONE[r.state] ?? "neutral"}>{stateLabel(r.state, rowThresholds(r, data.thresholds))}</Chip></td>
                      <td style={td}>{r.shifts}</td>
                      <td style={td}>{problems(r).length === 0 ? <span style={muted}>-</span> : problems(r).map((p, i) => <div key={i}>{p}</div>)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {data.truncated && <div style={{ ...muted, marginTop: 6 }}>Only the first rows are shown.</div>}
          <div style={{ ...muted, marginTop: 8 }}>{limitsText(data.thresholds)}</div>
        </>
      )}
    </section>
  )
}
