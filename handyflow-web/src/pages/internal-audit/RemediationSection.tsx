// Findings & Remediation: every finding across all engagements, and the action plans that put them right.

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { auditRemediationApi, type RemediationAction, type RemediationFinding } from "../../api/auditRemediation.api"
import { FilterBar, Meter, Pager, Pill, Stat } from "./auditUi"
import { filterField, grid } from "./auditUi.styles"
import { paginate, shortDate } from "./auditUi.logic"
import { actionStatus, dueText, filterActions, filterFindings, findingStatus, severityLabel, severityTone, type ActionFilter, type FindingFilter } from "./remediation.logic"

const PAGE_SIZE = 10
const MUTED = "var(--hf-text-muted)"
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", borderBottom: "1px solid var(--hf-border)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }
const link: React.CSSProperties = { background: "none", border: "none", padding: 0, color: "var(--hf-primary-text)", fontWeight: 600, fontSize: 12, cursor: "pointer" }

const todayJhb = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export function RemediationSection({ today }: { today?: string }) {
  const now = today ?? todayJhb()
  const { data, isLoading, isError } = useQuery({ queryKey: ["ia-remediation"], queryFn: auditRemediationApi.board })
  const [tab, setTab] = useState<"findings" | "actions">("findings")
  const [ff, setFf] = useState<FindingFilter>({ severity: "all", status: "open", q: "" })
  const [af, setAf] = useState<ActionFilter>({ status: "all", q: "" })
  const [page, setPage] = useState(1)
  const [open, setOpen] = useState<RemediationFinding | null>(null)

  if (isLoading) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  if (isError || !data) return <div role="alert" style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13 }}>Findings could not be loaded. Refresh the page; if it keeps failing, contact support.</div>

  const s = data.summary
  const findings = filterFindings(data.findings, ff)
  const actions = filterActions(data.actions, af)
  const pf = paginate(findings, page, PAGE_SIZE)
  const pa = paginate(actions, page, PAGE_SIZE)
  const setFind = (patch: Partial<FindingFilter>) => { setFf({ ...ff, ...patch }); setPage(1) }
  const setAct = (patch: Partial<ActionFilter>) => { setAf({ ...af, ...patch }); setPage(1) }

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={grid(170)}>
        <Stat label="Open findings" value={s.open + s.inProgress} sub={`${s.openCritical} critical, ${s.openHigh} high`} />
        <Stat label="Past due" value={s.pastDue} tone={s.pastDue ? "danger" : undefined} sub="open findings past their date" />
        <Stat label="Open with no action" value={s.openWithoutAction} tone={s.openWithoutAction ? "warning" : undefined} sub="nothing agreed yet" />
        <Stat label="Actions overdue" value={s.actionsOverdue} tone={s.actionsOverdue ? "danger" : undefined} sub={`${s.actionsAwaiting} awaiting validation`} />
        <Stat label="Actions validated" value={`${s.remediationPct}%`} sub={`${s.actionsValidated} of ${s.actions}`} />
      </div>

      <div role="tablist" aria-label="Findings or actions" style={{ display: "flex", gap: 6 }}>
        {(["findings", "actions"] as const).map(t => (
          <button key={t} role="tab" aria-selected={tab === t} onClick={() => { setTab(t); setPage(1) }}
            style={{ padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: tab === t ? "var(--hf-info-soft)" : "var(--hf-surface)", color: tab === t ? "var(--hf-primary-text)" : "var(--hf-text-secondary)", fontWeight: 600, fontSize: 13, cursor: "pointer" }}>
            {t === "findings" ? `Findings (${s.findings})` : `Actions (${data.actions.length})`}
          </button>
        ))}
      </div>

      {tab === "findings" ? (
        <div>
          <FilterBar>
            <input aria-label="Search findings" placeholder="Search findings…" value={ff.q} onChange={e => setFind({ q: e.target.value })} style={{ ...filterField, minWidth: 200 }} />
            <select aria-label="Severity" value={ff.severity} onChange={e => setFind({ severity: e.target.value })} style={filterField}>
              <option value="all">All severities</option>
              {["CRITICAL", "HIGH", "MEDIUM", "LOW", "UNRATED"].map(v => <option key={v} value={v}>{severityLabel(v)}</option>)}
            </select>
            <select aria-label="Finding status" value={ff.status} onChange={e => setFind({ status: e.target.value as FindingFilter["status"] })} style={filterField}>
              <option value="open">Open and in progress</option>
              <option value="all">All findings</option>
              <option value="pastDue">Past due</option>
              <option value="noAction">Open with no action</option>
              <option value="settled">Resolved or closed</option>
            </select>
          </FilterBar>
          {findings.length === 0 ? <p style={{ fontSize: 13, color: MUTED }}>{data.findings.length === 0 ? "No findings have been raised yet." : "No findings match these filters."}</p> : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 720 }}>
                <thead><tr><th style={th}>Finding</th><th style={th}>Severity</th><th style={th}>Status</th><th style={th}>Owner</th><th style={th}>Due</th><th style={th}>Remediation</th><th style={th} aria-label="Open" /></tr></thead>
                <tbody>
                  {pf.items.map(r => (
                    <tr key={r.id}>
                      <td style={td}>
                        <strong>{r.title}</strong>{r.repeat && <span style={{ marginLeft: 6 }}><Pill label="Repeat" tone="warning" /></span>}
                        <div style={{ fontSize: 12, color: MUTED }}>{[r.engagement, r.area].filter(Boolean).join(" · ")}</div>
                      </td>
                      <td style={td}><Pill label={severityLabel(r.severity)} tone={severityTone(r.severity)} /></td>
                      <td style={td}><Pill label={findingStatus(r.status).label} tone={findingStatus(r.status).tone} /></td>
                      <td style={td}>{r.owner ?? "—"}</td>
                      <td style={td}>{shortDate(r.dueDate)}{r.pastDue && <div style={{ fontSize: 11.5, color: "var(--hf-danger-text)", fontWeight: 600 }}>{dueText(r.dueDate, now)}</div>}</td>
                      <td style={{ ...td, minWidth: 130 }}>
                        {r.actions === 0 ? <span style={{ fontSize: 12, color: MUTED }}>No actions</span> : (
                          <>
                            <Meter pct={r.remediationPct} color="var(--hf-success-text)" label={`${r.title} remediation`} />
                            <div style={{ fontSize: 11.5, color: MUTED, marginTop: 3 }}>{r.actionsDone} of {r.actions} validated{r.actionsOverdue ? `, ${r.actionsOverdue} late` : ""}</div>
                          </>
                        )}
                      </td>
                      <td style={td}><button type="button" style={link} onClick={() => setOpen(r)} aria-label={`View ${r.title}`}>View</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pager page={pf} onPage={setPage} />
        </div>
      ) : (
        <div>
          <FilterBar>
            <input aria-label="Search actions" placeholder="Search actions…" value={af.q} onChange={e => setAct({ q: e.target.value })} style={{ ...filterField, minWidth: 200 }} />
            <select aria-label="Action status" value={af.status} onChange={e => setAct({ status: e.target.value as ActionFilter["status"] })} style={filterField}>
              <option value="all">All actions</option>
              <option value="overdue">Overdue</option>
              <option value="open">Open</option>
              <option value="awaiting">Awaiting validation</option>
              <option value="validated">Validated</option>
            </select>
          </FilterBar>
          {actions.length === 0 ? <p style={{ fontSize: 13, color: MUTED }}>{data.actions.length === 0 ? "No action plans have been agreed yet." : "No actions match these filters."}</p> : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 680 }}>
                <thead><tr><th style={th}>Action</th><th style={th}>Finding</th><th style={th}>Owner</th><th style={th}>Due</th><th style={th}>Status</th></tr></thead>
                <tbody>
                  {pa.items.map((r: RemediationAction) => (
                    <tr key={r.id}>
                      <td style={td}>{r.description}</td>
                      <td style={td}>{r.finding}<div style={{ fontSize: 12, color: MUTED }}>{r.engagement}</div></td>
                      <td style={td}>{r.owner ?? "—"}</td>
                      <td style={td}>{shortDate(r.dueDate)}{r.overdue && <div style={{ fontSize: 11.5, color: "var(--hf-danger-text)", fontWeight: 600 }}>{dueText(r.dueDate, now)}</div>}</td>
                      <td style={td}><Pill label={actionStatus(r.status).label} tone={actionStatus(r.status).tone} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pager page={pa} onPage={setPage} />
        </div>
      )}

      {open && <FindingPanel finding={open} actions={data.actions.filter(a => a.findingId === open.id)} today={now} onClose={() => setOpen(null)} />}
    </div>
  )
}

function FindingPanel({ finding: f, actions, today, onClose }: { finding: RemediationFinding; actions: RemediationAction[]; today: string; onClose: () => void }) {
  const field = (label: string, body: string | null) => body ? <div><div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>{label}</div><div style={{ fontSize: 13, whiteSpace: "pre-wrap" }}>{body}</div></div> : null
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", justifyContent: "flex-end", zIndex: 1000 }} onClick={onClose}>
      <aside role="dialog" aria-modal="true" aria-label={f.title} onClick={e => e.stopPropagation()}
        style={{ background: "var(--hf-surface)", width: "min(480px, 100%)", height: "100%", overflowY: "auto", padding: 22, display: "grid", gap: 14, alignContent: "start" }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
          <h3 style={{ margin: 0, fontSize: 16, color: "var(--hf-text)" }}>{f.title}</h3>
          <button type="button" onClick={onClose} aria-label="Close" style={{ ...link, fontSize: 18 }}>×</button>
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <Pill label={severityLabel(f.severity)} tone={severityTone(f.severity)} />
          <Pill label={findingStatus(f.status).label} tone={findingStatus(f.status).tone} />
          {f.repeat && <Pill label="Repeat" tone="warning" />}
        </div>
        <div style={{ fontSize: 12.5, color: MUTED }}>{[f.engagement, f.area].filter(Boolean).join(" · ")}<br />Owner: {f.owner ?? "not assigned"} · Due {shortDate(f.dueDate)}{f.pastDue ? ` (${dueText(f.dueDate, today)})` : ""}</div>
        {field("What was found", f.description)}
        {field("Recommendation", f.recommendation)}
        {field("Management response", f.managementResponse)}
        <div>
          <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", marginBottom: 6 }}>Actions ({actions.length})</div>
          {actions.length === 0 ? <div style={{ fontSize: 13, color: MUTED }}>No actions agreed yet.</div> : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
              {actions.map(a => (
                <li key={a.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: "8px 10px", fontSize: 13 }}>
                  {a.description}
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 4, fontSize: 12, color: MUTED }}>
                    <span>{a.owner ?? "No owner"} · {shortDate(a.dueDate)}{a.overdue ? ` (${dueText(a.dueDate, today)})` : ""}</span>
                    <Pill label={actionStatus(a.status).label} tone={actionStatus(a.status).tone} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>
    </div>
  )
}
