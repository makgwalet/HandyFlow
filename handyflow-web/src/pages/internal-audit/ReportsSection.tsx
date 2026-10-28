// Engagement reports: draft from a template, factual-accuracy review, issue, controlled distribution (audit backlog A18).
// Managers see every report; reviewers and recipients see only the reports they are named on (the server decides).
import { useEffect, useState } from "react"
import { auditReportsApi, type ReportSummary, type ReportTemplate, type ReportView } from "../../api/auditReports.api"
import { Panel, Pill, Stat } from "./auditUi"
import { filterField, grid } from "./auditUi.styles"
import { reportCounts, REPORT_STEPS, statusText, statusTone, stepStates } from "./reportsUi.logic"

/** A titled panel with space below it, so a stack of them reads as one page. */
const Card = ({ title, children }: { title: string; children: React.ReactNode }) => <div style={{ marginBottom: 14 }}><Panel title={title}>{children}</Panel></div>

const MUTED = "var(--hf-text-muted)"
const BORDER = "var(--hf-border)"
const MIN = 10
const msg = (e: any, f: string) => e?.response?.data?.message ?? f
const btn: React.CSSProperties = { padding: "7px 14px", fontSize: 12.5, fontWeight: 700, cursor: "pointer", borderRadius: 8, border: `1px solid ${BORDER}`, background: "var(--hf-surface)", color: "var(--hf-text-secondary)" }
const primaryBtn: React.CSSProperties = { ...btn, background: "var(--hf-primary)", borderColor: "var(--hf-primary)", color: "var(--hf-text-on-solid)" }
const area: React.CSSProperties = { width: "100%", minHeight: 80, padding: 8, border: `1px solid ${BORDER}`, borderRadius: 8, fontSize: 13, fontFamily: "inherit", boxSizing: "border-box" }

export function ReportsSection() {
  const [list, setList] = useState<ReportSummary[]>([])
  const [openId, setOpenId] = useState<string | null>(null)
  const [templates, setTemplates] = useState<ReportTemplate[]>([])
  const [engagements, setEngagements] = useState<{ id: string; name: string }[]>([])
  const [engagementId, setEngagementId] = useState("")
  const [template, setTemplate] = useState("STANDARD")
  const [error, setError] = useState("")

  const load = () => auditReportsApi.list().then(setList).catch(e => setError(msg(e, "Failed to load reports")))
  useEffect(() => {
    load()
    auditReportsApi.templates().then(setTemplates).catch(() => { setTemplates([]); setError("Could not load the list of report templates. Refresh to try again.") })
    auditReportsApi.engagements().then(setEngagements).catch(() => { setEngagements([]); setError("Could not load the list of engagements. Refresh to try again.") })
  }, [])

  const create = async () => {
    setError("")
    try { const v = await auditReportsApi.create(engagementId, template, ""); await load(); setOpenId(v.summary.id) }
    catch (e: any) { setError(msg(e, "Failed to create the report")) }
  }

  if (openId) return <ReportDetail id={openId} onBack={() => { setOpenId(null); load() }} />
  const counts = reportCounts(list)
  return (
    <div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      <div style={{ ...grid(150), marginBottom: 14 }}>
        <Stat label="Reports" value={counts.total} />
        <Stat label="In factual review" value={counts.inReview} tone={counts.inReview ? "warning" : undefined} sub={counts.openComments ? `${counts.openComments} open comment${counts.openComments === 1 ? "" : "s"}` : undefined} />
        <Stat label="Ready to issue" value={counts.ready} />
        <Stat label="Issued" value={counts.issued} />
      </div>
      {templates.length > 0 && engagements.length > 0 && (
        <Card title="New report">
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <select aria-label="Engagement for the report" style={filterField} value={engagementId} onChange={e => setEngagementId(e.target.value)}>
              <option value="">Choose an engagement…</option>
              {engagements.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
            <select aria-label="Report template" style={filterField} value={template} onChange={e => setTemplate(e.target.value)}>
              {templates.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
            </select>
            <button style={primaryBtn} disabled={!engagementId} onClick={create}>Start report</button>
          </div>
        </Card>
      )}
      <Card title="Reports">
        {list.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No reports.</span> : (
          <ul style={{ margin: 0, paddingLeft: 0, listStyle: "none" }}>
            {list.map(r => (
              <li key={r.id} style={{ padding: "10px 0", borderBottom: `1px solid var(--hf-border-subtle)`, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 600, color: "var(--hf-text)", overflowWrap: "anywhere" }}>{r.title}</div>
                  <div style={{ fontSize: 12, color: MUTED, marginTop: 2 }}>{`Version ${r.version}`}{r.openItems ? ` · ${r.openItems} open comment${r.openItems === 1 ? "" : "s"}` : ""}</div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <Pill label={statusText(r.status)} tone={statusTone(r.status)} />
                  <button style={btn} onClick={() => setOpenId(r.id)}>Open</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

function ReportDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const [v, setV] = useState<ReportView | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [users, setUsers] = useState<{ id: string; name: string }[]>([])
  const [drafts, setDrafts] = useState<Record<number, string>>({})
  const [personKind, setPersonKind] = useState<"REVIEWER" | "RECIPIENT">("REVIEWER")
  const [personId, setPersonId] = useState("")
  const [comment, setComment] = useState("")
  const [answers, setAnswers] = useState<Record<string, string>>({})

  useEffect(() => { auditReportsApi.get(id).then(setV).catch(e => setError(msg(e, "Failed to load the report"))) }, [id])
  useEffect(() => { if (v?.manager) auditReportsApi.users().then(setUsers).catch(() => { setUsers([]); setError("Could not load the list of people. Refresh to try again.") }) }, [v?.manager])

  const run = async (fn: () => Promise<ReportView>, fallback: string) => {
    setBusy(true); setError("")
    try { setV(await fn()) } catch (e: any) { setError(msg(e, fallback)) } finally { setBusy(false) }
  }

  if (!v) return error ? <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div> : <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  const s = v.summary
  const isManager = v.manager
  const reviewing = s.status === "FACTUAL_REVIEW"

  return (
    <div>
      <button style={btn} onClick={onBack}>← Back to reports</button>
      <h2 style={{ fontSize: 18, fontWeight: 800, margin: "14px 0 2px", color: "var(--hf-text)" }}>{s.title}</h2>
      <div style={{ fontSize: 12.5, color: MUTED, marginBottom: 12 }}>Version {s.version} · {statusText(s.status)}{v.contentHash ? ` · fingerprint ${v.contentHash.slice(0, 12)}…` : ""}</div>
      <ol aria-label="Report stage" style={{ display: "flex", gap: 6, flexWrap: "wrap", listStyle: "none", margin: "0 0 14px", padding: 0 }}>
        {REPORT_STEPS.map((step, i) => {
          const st = stepStates(s.status)[i]
          return (
            <li key={step.status} aria-current={st === "current" ? "step" : undefined}
              style={{ padding: "4px 12px", borderRadius: 999, fontSize: 12, fontWeight: 700, border: "1px solid var(--hf-border)",
                background: st === "current" ? "var(--hf-info-soft)" : "var(--hf-surface)", color: st === "current" ? "var(--hf-primary-text)" : st === "done" ? "var(--hf-success-text)" : "var(--hf-text-faint)" }}>
              {st === "done" ? "✓ " : ""}{step.label}
            </li>
          )
        })}
      </ol>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}

      {isManager && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
          {s.status === "DRAFT" && <button style={primaryBtn} disabled={busy} onClick={() => run(() => auditReportsApi.startReview(id), "Failed to start the review")}>Start factual-accuracy review</button>}
          {reviewing && <button style={btn} disabled={busy} onClick={() => run(() => auditReportsApi.closeReview(id), "Failed to close the review")}>Close review</button>}
          {(reviewing || s.status === "READY") && <button style={btn} disabled={busy} onClick={() => run(() => auditReportsApi.reopen(id), "Failed to reopen")}>Back to draft</button>}
          {s.status === "READY" && <button style={primaryBtn} disabled={busy} onClick={() => run(() => auditReportsApi.issue(id), "Failed to issue the report")}>Issue report</button>}
          {s.status === "ISSUED" && <button style={btn} disabled={busy} onClick={() => run(() => auditReportsApi.newVersion(id), "Failed to start a new version")}>Start a new version</button>}
        </div>
      )}

      {v.sections.map(sec => (
        <Card key={sec.position} title={`${sec.position}. ${sec.title}`}>
          {v.canEdit ? (
            <>
              <textarea aria-label={`Section ${sec.position} text`} style={area} value={drafts[sec.position] ?? sec.body} onChange={e => setDrafts({ ...drafts, [sec.position]: e.target.value })} />
              <button style={{ ...btn, marginTop: 6 }} disabled={busy || drafts[sec.position] === undefined}
                onClick={() => run(async () => { const r = await auditReportsApi.editSection(id, sec.position, sec.title, drafts[sec.position]); setDrafts(d => { const n = { ...d }; delete n[sec.position]; return n }); return r }, "Failed to save the section")}>Save section</button>
            </>
          ) : <div style={{ whiteSpace: "pre-wrap", fontSize: 13 }}>{sec.body || "—"}</div>}
        </Card>
      ))}

      {(isManager || v.items.length > 0 || reviewing) && (
        <Card title="Factual-accuracy comments">
          {v.items.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No comments.</span> : v.items.map(i => (
            <div key={i.id} style={{ padding: "6px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
              <div>{i.sectionPosition ? `Section ${i.sectionPosition}: ` : ""}{i.comment} <span style={{ color: MUTED }}>— {i.raisedByName} · {i.status.toLowerCase()}</span></div>
              {i.response && <div style={{ color: MUTED }}>Answer ({i.respondedByName}): {i.response}</div>}
              {isManager && i.status === "OPEN" && reviewing && (
                <div style={{ marginTop: 4 }}>
                  <textarea aria-label="Answer to comment" style={{ ...area, minHeight: 44 }} value={answers[i.id] ?? ""} onChange={e => setAnswers({ ...answers, [i.id]: e.target.value })} />
                  {(["ACCEPTED", "DECLINED"] as const).map(d => (
                    <button key={d} style={{ ...btn, marginRight: 6, marginTop: 4 }} disabled={busy || (answers[i.id] ?? "").trim().length < MIN}
                      onClick={() => run(() => auditReportsApi.respond(id, i.id, d, answers[i.id]), "Failed to answer")}>{d === "ACCEPTED" ? "Accept and answer" : "Decline and answer"}</button>
                  ))}
                </div>
              )}
            </div>
          ))}
          {reviewing && (
            <div style={{ marginTop: 8 }}>
              <textarea aria-label="New comment" style={area} placeholder="What is factually wrong? (at least 10 characters)" value={comment} onChange={e => setComment(e.target.value)} />
              <button style={{ ...btn, marginTop: 4 }} disabled={busy || comment.trim().length < MIN}
                onClick={() => run(async () => { const r = await auditReportsApi.raiseItem(id, null, comment); setComment(""); return r }, "Failed to raise the comment")}>Raise comment</button>
            </div>
          )}
        </Card>
      )}

      {isManager && (
        <Card title="Reviewers and recipients">
          {v.people.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>Nobody named yet.</span> : v.people.map(p => (
            <div key={p.id} style={{ fontSize: 13, padding: "4px 0", display: "flex", justifyContent: "space-between" }}>
              <span>{p.name} <span style={{ color: MUTED }}>· {p.kind.toLowerCase()}{p.kind === "RECIPIENT" ? ` · read ${p.viewCount} time${p.viewCount === 1 ? "" : "s"}` : ""}</span></span>
              <button style={btn} disabled={busy} onClick={() => run(() => auditReportsApi.removePerson(id, p.id), "Failed to remove")}>Remove</button>
            </div>
          ))}
          <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
            <select aria-label="Role on the report" style={filterField} value={personKind} onChange={e => setPersonKind(e.target.value as any)}>
              <option value="REVIEWER">Reviewer (checks facts in the draft)</option>
              <option value="RECIPIENT">Recipient (reads the issued report)</option>
            </select>
            <select aria-label="Person" style={filterField} value={personId} onChange={e => setPersonId(e.target.value)}>
              <option value="">Choose a person…</option>
              {users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
            <button style={btn} disabled={busy || !personId} onClick={() => run(async () => { const r = await auditReportsApi.addPerson(id, personKind, personId); setPersonId(""); return r }, "Failed to add")}>Add</button>
          </div>
        </Card>
      )}
    </div>
  )
}
