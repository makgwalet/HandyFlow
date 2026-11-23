// src/pages/security/SlaTab.tsx
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { isLimited, type MyScope } from "./reportsScopeLogic"
import {
  LIMITED_NOTE, LOAD_FAILED, SAVE_FAILED, SEVERITIES, defaultRange, failureText, liveLine, minutesText, parseMinutes, percentText, rangeError, stateLabel,
  stateTone, targetError, type LiveItem, type Report, type ReportRow, type SlaState, type TargetRow, type TargetsView,
} from "./slaLogic"

type View = "live" | "report" | "targets"
interface Site { id: string; name: string }

const TONE: Record<string, { color: string; bg: string }> = {
  good: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  bad: { color: "var(--hf-danger-text-strong)", bg: "var(--hf-danger-soft)" },
  warn: { color: "var(--hf-warning-text-deep)", bg: "var(--hf-warning-soft-strong)" },
  neutral: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
}
const inp = { padding: "8px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", fontSize: 13, outline: "none", boxSizing: "border-box" as const }
const btn = { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 13, cursor: "pointer", fontWeight: 600 }
const sbtn = { padding: "6px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontSize: 12, cursor: "pointer", color: "var(--hf-text-secondary)" }
const th = { textAlign: "left" as const, padding: "9px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }
const td = { padding: "9px 12px", fontSize: 13, color: "var(--hf-text-secondary)" }

function StatePill({ state }: { state: SlaState }) {
  const t = TONE[stateTone(state)]
  return <span style={{ background: t.bg, color: t.color, borderRadius: 6, padding: "2px 8px", fontSize: 11, fontWeight: 700 }}>{stateLabel(state)}</span>
}

const unwrap = <T,>(r: { data?: { data?: T } & T }): T => ((r.data as { data?: T })?.data ?? r.data) as T

export default function SlaTab() {
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE")
  const [view, setView] = useState<View>("live")
  const [range, setRange] = useState(() => defaultRange(new Date()))
  const [siteId, setSiteId] = useState("")
  const [form, setForm] = useState({ siteId: "", severity: "HIGH", ack: "", resolve: "" })
  const [err, setErr] = useState("")

  const { data: myScope } = useQuery<MyScope | null>({
    queryKey: ["my-site-scope"],
    queryFn: async () => { try { const r = await apiClient.get("/api/v1/security/my-site-scope"); return (r.data?.data ?? r.data ?? null) as MyScope | null } catch { return null } },
  })
  const limited = isLimited(myScope)
  const mayManage = canManage && !limited

  const sites = useQuery<Site[]>({
    queryKey: ["sla-sites"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/sites?size=200"); const p = unwrap<{ content?: Site[] } | Site[]>(r); return (Array.isArray(p) ? p : p?.content ?? []) as Site[] },
  })
  const live = useQuery<LiveItem[]>({
    queryKey: ["sla-live"], enabled: view === "live", refetchInterval: 60000,
    queryFn: async () => unwrap<LiveItem[]>(await apiClient.get("/api/v1/security/sla/live")),
  })
  const problem = rangeError(range.from, range.to)
  const report = useQuery<Report>({
    queryKey: ["sla-report", range.from, range.to, siteId], enabled: view === "report" && !problem,
    queryFn: async () => unwrap<Report>(await apiClient.get(`/api/v1/security/sla/report?from=${range.from}&to=${range.to}${siteId ? `&siteId=${siteId}` : ""}`)),
  })
  const targets = useQuery<TargetsView>({
    queryKey: ["sla-targets"], enabled: view === "targets",
    queryFn: async () => unwrap<TargetsView>(await apiClient.get("/api/v1/security/sla/targets")),
  })

  const save = useMutation({
    mutationFn: (b: object) => apiClient.put("/api/v1/security/sla/targets", b),
    onSuccess: (r) => { setErr(""); setForm((f) => ({ ...f, ack: "", resolve: "" })); qc.setQueryData(["sla-targets"], unwrap<TargetsView>(r)); qc.invalidateQueries({ queryKey: ["sla-live"] }) },
    onError: (e) => setErr(failureText(e, SAVE_FAILED)),
  })
  const remove = useMutation({
    mutationFn: (t: TargetRow) => apiClient.delete(`/api/v1/security/sla/targets?severity=${t.severity}${t.siteId ? `&siteId=${t.siteId}` : ""}`),
    onSuccess: (r) => { setErr(""); qc.setQueryData(["sla-targets"], unwrap<TargetsView>(r)); qc.invalidateQueries({ queryKey: ["sla-live"] }) },
    onError: (e) => setErr(failureText(e, SAVE_FAILED)),
  })

  const onSave = () => {
    const bad = targetError(form.ack, form.resolve)
    if (bad) { setErr(bad); return }
    setErr("")
    save.mutate({ siteId: form.siteId || null, severity: form.severity, acknowledgeMinutes: parseMinutes(form.ack), resolveMinutes: parseMinutes(form.resolve) })
  }

  const tab = (v: View, label: string) => (
    <button key={v} role="tab" aria-selected={view === v} onClick={() => { setView(v); setErr("") }}
      style={{ ...sbtn, padding: "8px 16px", fontSize: 13, fontWeight: 600, background: view === v ? "var(--hf-primary)" : "var(--hf-surface)", color: view === v ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)" }}>{label}</button>
  )

  const reportRow = (r: ReportRow, key: string, bold = false) => (
    <tr key={key} data-testid={`report-${key}`} style={{ borderBottom: "1px solid var(--hf-border-subtle)", fontWeight: bold ? 700 : 400 }}>
      <td style={td}>{r.siteName ?? "All sites"}</td><td style={td}>{r.severity ?? "All"}</td><td style={td}>{r.total}</td><td style={td}>{r.tracked}</td>
      <td style={td}>{r.met}</td><td style={td}>{r.breached}</td><td style={td}>{r.atRisk}</td><td style={td}>{r.pending}</td>
      <td style={td}>{percentText(r.compliancePct)}</td><td style={td}>{minutesText(r.averageAcknowledgeMinutes)}</td><td style={td}>{minutesText(r.averageResolveMinutes)}</td>
    </tr>
  )

  const siteOptions = (sites.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)

  return (
    <div data-testid="sla-tab">
      <p style={{ margin: "0 0 14px", fontSize: 12, color: "var(--hf-text-faint)" }}>
        How soon an incident must be acknowledged and resolved, counted from when it was recorded. Incidents with no target are not tracked.
      </p>
      <div role="tablist" style={{ display: "flex", gap: 8, marginBottom: 16 }}>{tab("live", "Needs attention")}{tab("report", "Report")}{tab("targets", "Targets")}</div>
      {err && <div role="alert" style={{ marginBottom: 12, color: "var(--hf-danger-text-strong)", fontSize: 13 }}>{err}</div>}

      {view === "live" && (
        live.isError ? <div role="alert" style={{ color: "var(--hf-danger-text-strong)", fontSize: 13 }}>{failureText(live.error, LOAD_FAILED)}</div>
        : live.isLoading ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div>
        : (live.data ?? []).length === 0 ? <div data-testid="live-empty" style={{ fontSize: 14, color: "var(--hf-text-muted)", padding: "24px 0" }}>No open incident is breached or close to its target.</div>
        : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Status</th><th style={th}>Site</th><th style={th}>Severity</th><th style={th}>Incident</th><th style={th}>Time</th></tr></thead>
            <tbody>{(live.data ?? []).map((i) => (
              <tr key={i.incidentId} data-testid={`live-${i.incidentId}`} style={{ borderBottom: "1px solid var(--hf-border-subtle)" }}>
                <td style={td}><StatePill state={i.state} /></td><td style={td}>{i.siteName}</td><td style={td}>{i.severity}</td>
                <td style={td}>{i.title || "Untitled incident"}</td><td style={td}>{liveLine(i)}</td>
              </tr>))}
            </tbody>
          </table>
        )
      )}

      {view === "report" && (
        <div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 14 }}>
            <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>From<input type="date" aria-label="From" style={{ ...inp, display: "block", marginTop: 4 }} value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} /></label>
            <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>To<input type="date" aria-label="To" style={{ ...inp, display: "block", marginTop: 4 }} value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} /></label>
            <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Site
              <select aria-label="Report site" style={{ ...inp, display: "block", marginTop: 4 }} value={siteId} onChange={(e) => setSiteId(e.target.value)}><option value="">All sites</option>{siteOptions}</select>
            </label>
          </div>
          {problem ? <div role="alert" style={{ color: "var(--hf-danger-text-strong)", fontSize: 13 }}>{problem}</div>
          : report.isError ? <div role="alert" style={{ color: "var(--hf-danger-text-strong)", fontSize: 13 }}>{failureText(report.error, LOAD_FAILED)}</div>
          : report.isLoading || !report.data ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div>
          : report.data.rows.length === 0 ? <div data-testid="report-empty" style={{ fontSize: 14, color: "var(--hf-text-muted)", padding: "16px 0" }}>No incidents were recorded in this period.</div>
          : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr>{["Site", "Severity", "Incidents", "Tracked", "Met", "Breached", "At risk", "Open", "Compliance", "Avg acknowledge", "Avg resolve"].map((h) => <th key={h} style={th}>{h}</th>)}</tr></thead>
                <tbody>{report.data.rows.map((r, n) => reportRow(r, String(n)))}{reportRow(report.data.total, "total", true)}</tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {view === "targets" && (
        <div>
          {limited && <p data-testid="limited-note" style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{LIMITED_NOTE}</p>}
          {targets.isError ? <div role="alert" style={{ color: "var(--hf-danger-text-strong)", fontSize: 13 }}>{failureText(targets.error, LOAD_FAILED)}</div>
          : targets.isLoading || !targets.data ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div>
          : (
            <div style={{ display: "grid", gap: 16 }}>
              {([["Company default", targets.data.companyDefaults], ["Site targets", targets.data.sites]] as const).map(([title, rows]) => (
                <div key={title}>
                  <h4 style={{ margin: "0 0 6px", fontSize: 14, color: "var(--hf-text)" }}>{title}</h4>
                  {rows.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>None set.</div> : (
                    <table style={{ width: "100%", borderCollapse: "collapse" }}>
                      <thead><tr><th style={th}>Site</th><th style={th}>Severity</th><th style={th}>Acknowledge within</th><th style={th}>Resolve within</th><th style={th} /></tr></thead>
                      <tbody>{rows.map((t) => (
                        <tr key={`${t.siteId}-${t.severity}`} data-testid={`target-${t.siteId ?? "company"}-${t.severity}`} style={{ borderBottom: "1px solid var(--hf-border-subtle)" }}>
                          <td style={td}>{t.siteName ?? "All sites"}</td><td style={td}>{t.severity}</td><td style={td}>{minutesText(t.acknowledgeMinutes)}</td><td style={td}>{minutesText(t.resolveMinutes)}</td>
                          <td style={{ ...td, textAlign: "right" }}>{mayManage && <button style={sbtn} onClick={() => remove.mutate(t)}>Remove</button>}</td>
                        </tr>))}
                      </tbody>
                    </table>
                  )}
                </div>
              ))}
              {mayManage && (
                <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
                  <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Applies to
                    <select aria-label="Applies to" style={{ ...inp, display: "block", marginTop: 4 }} value={form.siteId} onChange={(e) => setForm({ ...form, siteId: e.target.value })}><option value="">All sites (company default)</option>{siteOptions}</select>
                  </label>
                  <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Severity
                    <select aria-label="Severity" style={{ ...inp, display: "block", marginTop: 4 }} value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value })}>{SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}</select>
                  </label>
                  <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Acknowledge within (min)
                    <input aria-label="Acknowledge minutes" inputMode="numeric" style={{ ...inp, display: "block", marginTop: 4, width: 130 }} value={form.ack} onChange={(e) => setForm({ ...form, ack: e.target.value })} />
                  </label>
                  <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Resolve within (min)
                    <input aria-label="Resolve minutes" inputMode="numeric" style={{ ...inp, display: "block", marginTop: 4, width: 130 }} value={form.resolve} onChange={(e) => setForm({ ...form, resolve: e.target.value })} />
                  </label>
                  <button style={{ ...btn, opacity: save.isPending ? 0.6 : 1 }} disabled={save.isPending} onClick={onSave}>Save target</button>
                </div>
              )}
              {mayManage && <p style={{ margin: 0, fontSize: 11, color: "var(--hf-text-faint)" }}>A site target replaces the company default for that site and severity. Saving again for the same site and severity changes it.</p>}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
