// Audit programme library: apply IT, cyber, operational and compliance checks to an engagement and record the results (audit backlog A21).
import { useEffect, useState } from "react"
import { auditProgrammeApi as api, type PgLibrary, type PgProgramme, type PgStep } from "../../api/auditProgramme.api"
import { Card, Tile, TileRow } from "../../components/DashboardBits"

const MUTED = "var(--hf-text-muted)"
const BORDER = "var(--hf-border)"
const MIN = 10
const msg = (e: any, f: string) => e?.response?.data?.message ?? f
const btn: React.CSSProperties = { padding: "6px 12px", fontSize: 12, fontWeight: 700, cursor: "pointer", borderRadius: 6, border: `1px solid ${BORDER}`, background: "var(--hf-surface)" }
const area: React.CSSProperties = { width: "100%", minHeight: 48, padding: 8, border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, fontFamily: "inherit", boxSizing: "border-box" }
const STATUS: Record<string, string> = { NOT_STARTED: "Not started", IN_PROGRESS: "In progress", COMPLETED: "Completed", NOT_APPLICABLE: "Not applicable" }
const CONCLUSION: Record<string, string> = { EFFECTIVE: "Effective", PARTIALLY_EFFECTIVE: "Partly effective", INEFFECTIVE: "Ineffective" }

export function ProgrammeSection() {
  const [engagements, setEngagements] = useState<{ id: string; name: string }[]>([])
  const [engagementId, setEngagementId] = useState("")
  const [lib, setLib] = useState<PgLibrary | null>(null)
  const [p, setP] = useState<PgProgramme | null>(null)
  const [findings, setFindings] = useState<{ id: string; title: string }[]>([])
  const [picked, setPicked] = useState<string[]>([])
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [edit, setEdit] = useState<{ code: string; status: string; conclusion: string; note: string } | null>(null)

  useEffect(() => {
    api.engagements().then(setEngagements).catch(() => { setEngagements([]); setError("Could not load the list of engagements. Refresh to try again.") })
    api.library().then(setLib).catch(e => setError(msg(e, "Failed to load the library")))
  }, [])
  const choose = (id: string) => { setEngagementId(id); setP(null); setEdit(null); setPicked([]); setError("") }
  useEffect(() => {
    if (!engagementId) return
    api.get(engagementId).then(setP).catch(e => setError(msg(e, "Failed to load the programme")))
    api.findings(engagementId).then(setFindings).catch(() => { setFindings([]); setError("Could not load the list of findings. Refresh to try again.") })
  }, [engagementId])

  const run = async (fn: () => Promise<PgProgramme>, fallback: string, after?: () => void) => {
    setBusy(true); setError("")
    try { setP(await fn()); after?.() } catch (e: any) { setError(msg(e, fallback)) } finally { setBusy(false) }
  }
  const toggle = (d: string) => setPicked(x => x.includes(d) ? x.filter(y => y !== d) : [...x, d])

  return (
    <div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      <Card title="Engagement">
        <select aria-label="Engagement" value={engagementId} onChange={e => choose(e.target.value)}>
          <option value="">Choose an engagement…</option>{engagements.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
        </select>
      </Card>
      {lib && engagementId && (
        <Card title="Add checks from the library">
          <div style={{ display: "flex", gap: 14, flexWrap: "wrap", fontSize: 13 }}>
            {Object.entries(lib.domains).map(([d, name]) => (
              <label key={d}><input type="checkbox" aria-label={name} checked={picked.includes(d)} onChange={() => toggle(d)} /> {name} ({lib.controls.filter(c => c.domain === d).length})</label>
            ))}
          </div>
          <button style={{ ...btn, marginTop: 8 }} disabled={busy || picked.length === 0} onClick={() => run(() => api.apply(engagementId, picked, []), "Failed to add the checks", () => setPicked([]))}>Add to programme</button>
          <div style={{ fontSize: 12, color: MUTED, marginTop: 6 }}>{lib.notice}</div>
        </Card>
      )}
      {p && p.steps.length > 0 && (
        <>
          <TileRow>
            <Tile label="Done" value={`${p.overall.donePct}%`} sub={`${p.overall.completed + p.overall.notApplicable} of ${p.overall.total}`} />
            <Tile label="Ineffective" value={p.overall.ineffective} tone={p.overall.ineffective ? "warning" : undefined} />
            <Tile label="Partly effective" value={p.overall.partial} />
            <Tile label="Needing a finding" value={p.overall.needingFinding} tone={p.overall.needingFinding ? "warning" : undefined} sub="weak result, none raised" />
          </TileRow>
          {Object.entries(p.byDomain).map(([d, c]) => (
            <Card key={d} title={`${lib?.domains[d] ?? d} · ${c.donePct}% done`}>
              {p.steps.filter(s => s.domain === d).map(s => renderStep(s))}
            </Card>
          ))}
        </>
      )}
      {p && p.steps.length === 0 && <div style={{ fontSize: 13, color: MUTED }}>No checks on this engagement yet.</div>}
    </div>
  )

  // A plain function, not a component: a component defined here would be rebuilt on every keystroke and the note box would lose focus.
  function renderStep(s: PgStep) {
    const open = edit?.code === s.controlCode
    return (
      <div key={s.controlCode} style={{ padding: "8px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
        <div><strong>{s.controlCode}</strong> {s.objective} <span style={{ color: MUTED }}>· {s.area} · {STATUS[s.status]}{s.conclusion ? ` · ${CONCLUSION[s.conclusion]}` : ""}</span></div>
        <div style={{ color: MUTED, marginTop: 2 }}>{s.procedure}{s.reference ? ` (${s.reference})` : ""}</div>
        {s.note && !open && <div style={{ marginTop: 4 }}>{s.note}</div>}
        {s.needsFinding && <div style={{ marginTop: 4, color: "var(--hf-warning-text, #92400e)" }}>No finding raised for this result.</div>}
        {s.status !== "NOT_STARTED" && (s.conclusion === "INEFFECTIVE" || s.conclusion === "PARTIALLY_EFFECTIVE") && (
          <select aria-label={`Finding for ${s.controlCode}`} value={s.findingId ?? ""} onChange={e => run(() => api.link(engagementId, s.controlCode, e.target.value || null), "Failed to link the finding")} style={{ marginTop: 4 }}>
            <option value="">Link a finding…</option>{findings.map(f => <option key={f.id} value={f.id}>{f.title}</option>)}
          </select>
        )}
        {open ? (
          <div style={{ display: "grid", gap: 6, marginTop: 6 }}>
            <select aria-label={`Status of ${s.controlCode}`} value={edit.status} onChange={e => setEdit({ ...edit, status: e.target.value })}>
              {["IN_PROGRESS", "COMPLETED", "NOT_APPLICABLE"].map(k => <option key={k} value={k}>{STATUS[k]}</option>)}
            </select>
            {edit.status === "COMPLETED" && (
              <select aria-label={`Conclusion for ${s.controlCode}`} value={edit.conclusion} onChange={e => setEdit({ ...edit, conclusion: e.target.value })}>
                {Object.entries(CONCLUSION).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
              </select>
            )}
            <textarea aria-label={`Note for ${s.controlCode}`} style={area} placeholder={edit.status === "IN_PROGRESS" ? "Notes" : "What was done, or why it does not apply (at least 10 characters)"} value={edit.note} onChange={e => setEdit({ ...edit, note: e.target.value })} />
            <div><button style={btn} disabled={busy || (edit.status !== "IN_PROGRESS" && edit.note.trim().length < MIN)}
              onClick={() => run(() => api.record(engagementId, s.controlCode, edit.status, edit.status === "COMPLETED" ? edit.conclusion : null, edit.note), "Failed to save", () => setEdit(null))}>Save</button></div>
          </div>
        ) : <button style={{ ...btn, marginTop: 6 }} onClick={() => setEdit({ code: s.controlCode, status: s.status === "NOT_STARTED" ? "IN_PROGRESS" : s.status, conclusion: s.conclusion ?? "EFFECTIVE", note: s.note ?? "" })}>{s.status === "NOT_STARTED" ? "Start" : "Update"} {s.controlCode}</button>}
      </div>
    )
  }
}
