// src/pages/security/GuardCompetencyPanel.tsx
//
// The Skills tab on Guard 360: competencies with certificate evidence, issue and expiry dates and a named verifier.
// A competency counts as met when in date, with a certificate, and verified. Only those marked "required for
// deployment" count towards the guard's readiness percentage.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { BadgeCheck, Pencil, Plus, Trash2, X } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import EvidenceFiles from "./EvidenceFiles"
import { COMPETENCY_TYPES, competencyFormError, readinessState, todayIso, type CompetencyItem } from "./guard360.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }
const lab: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, marginBottom: 3 }
const fmt = (d: string | null) => d ? new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Johannesburg" }) : "-"
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."
const name = (c: CompetencyItem) => c.title || c.label

function CompetencyForm({ guardId, existing, onClose }: { guardId: string; existing?: CompetencyItem; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState({
    competencyType: existing?.competencyType ?? "FIRST_AID", title: existing?.title ?? "", issuedBy: existing?.issuedBy ?? "",
    issueDate: existing?.issueDate ?? "", expiryDate: existing?.expiryDate ?? "", certificateRef: existing?.certificateRef ?? "",
    required: existing?.required ?? false, notes: existing?.notes ?? "",
  })
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, title: f.title.trim() || null, issuedBy: f.issuedBy.trim() || null, issueDate: f.issueDate || null, expiryDate: f.expiryDate || null, certificateRef: f.certificateRef.trim() || null, notes: f.notes.trim() || null }
      return existing ? apiClient.put(`/api/v1/security/guards/${guardId}/competencies/${existing.id}`, body) : apiClient.post(`/api/v1/security/guards/${guardId}/competencies`, body)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guard-overview", guardId] }); onClose() },
    onError: e => setErr(errText(e)),
  })
  const submit = () => { const m = competencyFormError(f, todayIso()); if (m) setErr(m); else { setErr(""); save.mutate() } }
  return (
    <div style={{ ...card, display: "grid", gap: 10 }}>
      <div style={{ fontWeight: 800 }}>{existing ? `Edit ${name(existing)}` : "Add a competency"}</div>
      {existing?.verifiedAt && <div style={{ fontSize: 12, color: "var(--hf-warning-text)" }}>Saving changes removes the earlier verification, so it will need verifying again.</div>}
      <label style={{ fontSize: 12 }}>Competency
        <select value={f.competencyType} onChange={e => setF({ ...f, competencyType: e.target.value })} style={input}>
          {COMPETENCY_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select></label>
      {f.competencyType === "OTHER" && <label style={{ fontSize: 12 }}>Name
        <input value={f.title} onChange={e => setF({ ...f, title: e.target.value })} style={input} /></label>}
      <label style={{ fontSize: 12 }}>Issued by
        <input value={f.issuedBy} onChange={e => setF({ ...f, issuedBy: e.target.value })} placeholder="Training provider or authority" style={input} /></label>
      <div style={{ display: "grid", gap: 10, gridTemplateColumns: "1fr 1fr" }}>
        <label style={{ fontSize: 12 }}>Issue date
          <input type="date" value={f.issueDate} onChange={e => setF({ ...f, issueDate: e.target.value })} style={input} /></label>
        <label style={{ fontSize: 12 }}>Expiry date
          <input type="date" value={f.expiryDate} onChange={e => setF({ ...f, expiryDate: e.target.value })} style={input} /></label>
      </div>
      <label style={{ fontSize: 12 }}>Certificate number
        <input value={f.certificateRef} onChange={e => setF({ ...f, certificateRef: e.target.value })} style={input} /></label>
      <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
        <input type="checkbox" checked={f.required} onChange={e => setF({ ...f, required: e.target.checked })} />
        Required for deployment (counts towards this guard's readiness)</label>
      <label style={{ fontSize: 12 }}>Notes
        <input value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} style={input} /></label>
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={submit} disabled={save.isPending} style={primary}>{existing ? "Save changes" : "Add competency"}</button>
        <button onClick={onClose} style={btn}>Cancel</button>
      </div>
    </div>
  )
}

function CompetencyDetail({ guardId, c, canManage, onClose }: { guardId: string; c: CompetencyItem; canManage: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const base = `/api/v1/security/guards/${guardId}/competencies/${c.id}`
  const refresh = () => qc.invalidateQueries({ queryKey: ["guard-overview", guardId] })
  const [err, setErr] = useState("")
  const [note, setNote] = useState("")
  const [editing, setEditing] = useState(false)
  const [confirmRemove, setConfirmRemove] = useState(false)
  const verify = useMutation({ mutationFn: () => apiClient.post(`${base}/verify`, { note: note.trim() || null }), onSuccess: () => { setNote(""); setErr(""); refresh() }, onError: e => setErr(errText(e)) })
  const remove = useMutation({ mutationFn: () => apiClient.delete(base), onSuccess: () => { refresh(); onClose() }, onError: e => setErr(errText(e)) })
  const st = readinessState(c.state)

  if (editing) return <CompetencyForm guardId={guardId} existing={c} onClose={() => setEditing(false)} />
  return (
    <div style={{ ...card, display: "grid", gap: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
        <div style={{ fontWeight: 800 }}>{name(c)} <Chip tone={st.tone}>{st.label}</Chip> <span style={{ fontSize: 12, fontWeight: 400, color: "var(--hf-text-muted)" }}>{c.detail}</span></div>
        <button aria-label="Close competency" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)" }}><X size={16} /></button>
      </div>
      <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", fontSize: 13 }}>
        <div><div style={lab}>Issued by</div>{c.issuedBy ?? "-"}</div>
        <div><div style={lab}>Issued</div>{fmt(c.issueDate)}</div>
        <div><div style={lab}>Expires</div>{fmt(c.expiryDate)}</div>
        <div><div style={lab}>Certificate no.</div>{c.certificateRef ?? "-"}</div>
        <div><div style={lab}>Required for deployment</div>{c.required ? "Yes" : "No"}</div>
      </div>
      {c.notes && <div style={{ fontSize: 13 }}>{c.notes}</div>}

      <div>
        <div style={lab}>Certificate ({c.evidence.length})</div>
        <EvidenceFiles baseUrl={`${base}/evidence`} items={c.evidence} canManage={canManage}
          emptyHint="No certificate attached. It cannot be verified until one is."
          onChanged={() => { setErr(""); refresh() }} onError={setErr} />
      </div>

      <div>
        <div style={lab}>Verification</div>
        {c.verifiedAt ? (
          <div style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><BadgeCheck size={14} style={{ color: "var(--hf-success-text-strong)", flexShrink: 0 }} /><span>Verified by {c.verifiedByName ?? "a reviewer"} on {fmt(c.verifiedAt)}{c.verificationNote ? `: ${c.verificationNote}` : ""}</span></div>
        ) : <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Not verified yet.</div>}
        {canManage && !c.verifiedAt && (
          <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
            <input aria-label="Verification note" placeholder="Note (optional), e.g. original sighted" value={note} onChange={e => setNote(e.target.value)} style={{ ...input, width: 300 }} />
            <button onClick={() => verify.mutate()} disabled={verify.isPending || c.evidence.length === 0} title={c.evidence.length === 0 ? "Attach the certificate first" : undefined} style={primary}>Verify</button>
          </div>
        )}
      </div>

      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      {canManage && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button onClick={() => setEditing(true)} style={btn}><Pencil size={13} /> Edit</button>
          {!confirmRemove ? <button onClick={() => setConfirmRemove(true)} style={{ ...btn, color: "var(--hf-danger-text)" }}><Trash2 size={13} /> Remove</button>
            : <>
              <span style={{ fontSize: 13, alignSelf: "center" }}>Remove this competency?</span>
              <button onClick={() => remove.mutate()} disabled={remove.isPending} style={{ ...btn, color: "var(--hf-danger-text)" }}>Yes, remove</button>
              <button onClick={() => setConfirmRemove(false)} style={btn}>Keep it</button>
            </>}
        </div>
      )}
    </div>
  )
}

export default function SkillsTab({ guardId, competencies, canManage, initialOpenId }: { guardId: string; competencies: CompetencyItem[]; canManage: boolean; initialOpenId?: string | null }) {
  const [openId, setOpenId] = useState<string | null>(initialOpenId ?? null)
  const [adding, setAdding] = useState(false)
  const open = competencies.find(c => c.id === openId) ?? null
  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
          <div style={{ fontWeight: 800 }}>Skills and competencies ({competencies.length})</div>
          {canManage && <button onClick={() => setAdding(true)} style={primary}><Plus size={14} /> Add competency</button>}
        </div>
        {competencies.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No competencies recorded. Add first aid, firearm competency, driver and similar, each with its certificate and expiry.</div> :
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Competency</th><th style={th}>Issued by</th><th style={th}>Expires</th><th style={th}>Certificate</th><th style={th}>Status</th><th style={th}></th></tr></thead>
              <tbody>{competencies.map(c => {
                const st = readinessState(c.state)
                return (
                  <tr key={c.id}>
                    <td style={td}>{name(c)}{c.required && <> <Chip tone="info">Required</Chip></>}</td>
                    <td style={td}>{c.issuedBy ?? "-"}</td>
                    <td style={td}>{fmt(c.expiryDate)}</td>
                    <td style={td}>{c.evidence.length > 0 ? `${c.evidence.length} file${c.evidence.length === 1 ? "" : "s"}` : "None"}</td>
                    <td style={td}><Chip tone={st.tone}>{st.label}</Chip> <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{c.detail}</span></td>
                    <td style={td}><button onClick={() => setOpenId(c.id)} style={{ ...btn, padding: "4px 10px" }}>Open</button></td>
                  </tr>
                )
              })}</tbody>
            </table>
          </div>}
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>A competency is met when it is in date, has a certificate attached and has been verified. Only those marked required for deployment count towards readiness.</div>
      </div>
      {adding && <CompetencyForm guardId={guardId} onClose={() => setAdding(false)} />}
      {open && <CompetencyDetail key={open.id} guardId={guardId} c={open} canManage={canManage} onClose={() => setOpenId(null)} />}
    </div>
  )
}
