import { useState } from "react"
import { auditorApi, type AuditEngagement } from "../../api/auditor.api"

const BORDER = "var(--hf-border)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"
const INK = "var(--hf-text)"
const NAVY = "var(--hf-primary)"
const input = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }

export const STATUS_TEXT: Record<AuditEngagement["status"], string> = { PLANNED: "Planned", ACTIVE: "Running", COMPLETED: "Finished", ARCHIVED: "Archived" }
const NEXT: Partial<Record<AuditEngagement["status"], { action: "start" | "complete" | "archive"; label: string; warn?: string }>> = {
  PLANNED: { action: "start", label: "Start" },
  ACTIVE: { action: "complete", label: "Finish", warn: "Finish this engagement? The auditors attached to it lose access straight away." },
  COMPLETED: { action: "archive", label: "Archive", warn: "Archive this engagement? Every login that belonged to it is revoked for good." },
}
const day = (d: string) => new Date(d + "T00:00:00").toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

/** The external audits this business has: who is auditing, which period, and whether the auditors can get in right now. */
export function EngagementsPanel({ engagements, onChange }: { engagements: AuditEngagement[]; onChange: () => void }) {
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ name: "", firmName: "", periodFrom: "", periodTo: "", entitiesInScope: "" })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(f => ({ ...f, [k]: e.target.value }))
  const valid = form.name.trim() && form.firmName.trim() && form.periodFrom && form.periodTo && form.periodTo >= form.periodFrom

  const save = async () => {
    setBusy(true); setError("")
    try { await auditorApi.createEngagement({ ...form, entitiesInScope: form.entitiesInScope || undefined }); setAdding(false); setForm({ name: "", firmName: "", periodFrom: "", periodTo: "", entitiesInScope: "" }); onChange() }
    catch (e: any) { setError(e.response?.data?.message ?? "Could not save the engagement") }
    finally { setBusy(false) }
  }
  const move = async (g: AuditEngagement) => {
    const n = NEXT[g.status]; if (!n) return
    if (n.warn && !confirm(n.warn)) return
    setError("")
    try { await auditorApi.engagementAction(g.id, n.action); onChange() }
    catch (e: any) { setError(e.response?.data?.message ?? "That did not work") }
  }
  const download = async (g: AuditEngagement) => {
    try {
      const m = await auditorApi.manifest(g.id)
      const url = URL.createObjectURL(new Blob([JSON.stringify(m, null, 2)], { type: "application/json" }))
      const a = document.createElement("a"); a.href = url; a.download = `engagement-${g.name.replace(/\W+/g, "-")}.json`; a.click(); URL.revokeObjectURL(url)
    } catch (e: any) { setError(e.response?.data?.message ?? "Could not build the record") }
  }

  return (
    <div style={{ background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8, padding: 16, marginBottom: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <div style={{ fontSize: 13, fontWeight: 800, color: INK }}>External audits</div>
        {!adding && <button onClick={() => setAdding(true)} style={{ background: "none", border: "none", color: NAVY, fontWeight: 700, cursor: "pointer", fontSize: 12.5 }}>New engagement</button>}
      </div>
      <div style={{ fontSize: 12, color: FAINT, marginBottom: 10 }}>Auditors invited to an engagement can only get in while it is running.</div>
      {error && <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 10 }}>{error}</div>}
      {adding && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 12 }}>
          <label style={{ fontSize: 12, color: MUTED }}>Engagement name<input aria-label="Engagement name" value={form.name} onChange={set("name")} placeholder="e.g. FY2026 year-end audit" style={input} /></label>
          <label style={{ fontSize: 12, color: MUTED }}>Audit firm<input aria-label="Audit firm" value={form.firmName} onChange={set("firmName")} style={input} /></label>
          <label style={{ fontSize: 12, color: MUTED }}>Period from<input aria-label="Period from" type="date" value={form.periodFrom} onChange={set("periodFrom")} style={input} /></label>
          <label style={{ fontSize: 12, color: MUTED }}>Period to<input aria-label="Period to" type="date" value={form.periodTo} onChange={set("periodTo")} style={input} /></label>
          <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 2" }}>Entities covered (optional)<input aria-label="Entities covered" value={form.entitiesInScope} onChange={set("entitiesInScope")} placeholder="e.g. Holdco, Opco Retail" style={input} /></label>
          <div style={{ gridColumn: "1 / span 2", display: "flex", gap: 8 }}>
            <button disabled={!valid || busy} onClick={save} style={{ padding: "7px 14px", background: NAVY, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }}>Save engagement</button>
            <button onClick={() => setAdding(false)} style={{ padding: "7px 14px", background: "none", border: `1px solid ${BORDER}`, borderRadius: 6, fontSize: 12.5, cursor: "pointer" }}>Cancel</button>
          </div>
        </div>
      )}
      {engagements.length === 0 ? <div style={{ fontSize: 12.5, color: FAINT }}>No engagements yet. Auditors can still be invited without one.</div> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {engagements.map(g => {
            const n = NEXT[g.status]
            return (
              <div key={g.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", border: `1px solid ${BORDER}`, borderRadius: 8, padding: "8px 12px" }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: INK }}>{g.name} <span style={{ fontWeight: 600, fontSize: 11, color: MUTED }}>· {STATUS_TEXT[g.status]}</span></div>
                  <div style={{ fontSize: 12, color: MUTED }}>{g.firmName} · {day(g.periodFrom)} to {day(g.periodTo)}{g.entitiesInScope ? ` · ${g.entitiesInScope}` : ""}</div>
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  {(g.status === "COMPLETED" || g.status === "ARCHIVED") && <button onClick={() => download(g)} style={{ background: "none", border: "none", color: NAVY, fontWeight: 600, cursor: "pointer", fontSize: 12 }}>Download record</button>}
                  {n && <button onClick={() => move(g)} style={{ padding: "5px 12px", background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>{n.label}</button>}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
