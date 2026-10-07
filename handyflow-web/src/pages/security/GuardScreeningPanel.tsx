// src/pages/security/GuardScreeningPanel.tsx
//
// One screening on the Guard 360 Compliance tab, following the vetting trail:
// Request, Evidence, Result, Review (sign-off), Expiry, Renewal. Also the "request a screening" form.
import { useRef, useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Download, Paperclip, RefreshCw, Trash2, X } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { SCREENING_REASONS, SCREENING_TYPES, screeningLabel, todayIso, type EvidenceItem, type ScreeningItem } from "./guard360.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const fmt = (d: string | null) => d ? new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Johannesburg" }) : "-"
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."
const size = (n: number) => n >= 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`

async function downloadEvidence(guardId: string, screeningId: string, ev: EvidenceItem) {
  const r = await apiClient.get(`/api/v1/security/guards/${guardId}/screening/${screeningId}/evidence/${ev.id}/download`, { responseType: "blob" })
  const url = URL.createObjectURL(r.data)
  const a = document.createElement("a"); a.href = url; a.download = ev.fileName; a.click(); URL.revokeObjectURL(url)
}

export function RequestScreening({ guardId, initial, onClose }: { guardId: string; initial?: { screeningType?: string; reason?: string; provider?: string | null }; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({ screeningType: initial?.screeningType ?? "CRIMINAL_RECORD_CHECK", reason: initial?.reason ?? "ONBOARDING", provider: initial?.provider ?? "", requestedAt: todayIso() })
  const [err, setErr] = useState("")
  const create = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/guards/${guardId}/screening`, { ...form, provider: form.provider.trim() || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guard-overview", guardId] }); onClose() },
    onError: e => setErr(errText(e)),
  })
  return (
    <div style={{ ...card, display: "grid", gap: 10 }}>
      <div style={{ fontWeight: 800 }}>Request a screening</div>
      <label style={{ fontSize: 12 }}>Type
        <select value={form.screeningType} onChange={e => setForm({ ...form, screeningType: e.target.value })} style={input}>
          {SCREENING_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select></label>
      <label style={{ fontSize: 12 }}>Reason
        <select value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} style={input}>
          {SCREENING_REASONS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select></label>
      <label style={{ fontSize: 12 }}>Provider (optional)
        <input value={form.provider} onChange={e => setForm({ ...form, provider: e.target.value })} placeholder="Agency doing the check" style={input} /></label>
      <label style={{ fontSize: 12 }}>Requested on
        <input type="date" value={form.requestedAt} onChange={e => setForm({ ...form, requestedAt: e.target.value })} style={input} /></label>
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={() => create.mutate()} disabled={create.isPending} style={primary}>Request screening</button>
        <button onClick={onClose} style={btn}>Cancel</button>
      </div>
    </div>
  )
}

export default function GuardScreeningPanel({ guardId, record, canManage, onClose }: { guardId: string; record: ScreeningItem; canManage: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const base = `/api/v1/security/guards/${guardId}/screening/${record.id}`
  const refresh = () => qc.invalidateQueries({ queryKey: ["guard-overview", guardId] })
  const fileRef = useRef<HTMLInputElement>(null)
  const [label, setLabel] = useState("")
  const [err, setErr] = useState("")
  const [recording, setRecording] = useState(false)
  const [renewing, setRenewing] = useState(false)
  const [result, setResult] = useState({ result: "PASS", conductedBy: record.provider ?? "", conductedAt: todayIso(), nextDueAt: "", reportRef: "", notes: "" })
  const [note, setNote] = useState("")
  const fail = (e: any) => setErr(errText(e))

  const upload = useMutation({
    mutationFn: (file: File) => { const f = new FormData(); f.append("file", file); if (label.trim()) f.append("label", label.trim()); return apiClient.post(`${base}/evidence`, f) },
    onSuccess: () => { setLabel(""); setErr(""); if (fileRef.current) fileRef.current.value = ""; refresh() }, onError: fail,
  })
  const removeFile = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/evidence/${id}`), onSuccess: () => { setErr(""); refresh() }, onError: fail })
  const saveResult = useMutation({
    mutationFn: () => apiClient.post(`${base}/result`, { ...result, conductedBy: result.conductedBy.trim() || null, conductedAt: result.conductedAt || null, nextDueAt: result.nextDueAt || null, reportRef: result.reportRef.trim() || null, notes: result.notes.trim() || null }),
    onSuccess: () => { setRecording(false); setErr(""); refresh() }, onError: fail,
  })
  const decide = useMutation({
    mutationFn: (decision: "CLEARED" | "NOT_CLEARED") => apiClient.post(`${base}/decision`, { decision, note: note.trim() || null }),
    onSuccess: () => { setNote(""); setErr(""); refresh() }, onError: fail,
  })

  const pending = record.result === "PENDING"
  return (
    <div style={{ ...card, display: "grid", gap: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
        <div style={{ fontWeight: 800 }}>{screeningLabel(record.screeningType)}</div>
        <button aria-label="Close screening" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)" }}><X size={16} /></button>
      </div>

      <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", fontSize: 13 }}>
        <div><div style={lab}>1. Requested</div>{fmt(record.requestedAt)}{record.provider ? ` from ${record.provider}` : ""}</div>
        <div><div style={lab}>3. Result</div>{pending ? <Chip tone="warn">Pending</Chip> : <Chip tone={record.result === "PASS" ? "ok" : record.result === "FAIL" ? "bad" : "warn"}>{record.result.toLowerCase()}</Chip>} {record.conductedAt ? `on ${fmt(record.conductedAt)}` : ""}{record.conductedBy ? ` by ${record.conductedBy}` : ""}</div>
        <div><div style={lab}>5. Valid until</div>{fmt(record.nextDueAt)}</div>
        <div><div style={lab}>Report reference</div>{record.reportRef ?? "-"}</div>
      </div>

      <div>
        <div style={lab}>2. Evidence ({record.evidence.length})</div>
        {record.evidence.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No evidence files yet. A passed screening only counts towards readiness once evidence is attached.</div> :
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 6 }}>
            {record.evidence.map(e => (
              <li key={e.id} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, flexWrap: "wrap" }}>
                <Paperclip size={13} />
                <span style={{ overflowWrap: "anywhere" }}>{e.fileName}</span>
                <span style={{ color: "var(--hf-text-muted)" }}>{e.label ? `${e.label} · ` : ""}{size(e.sizeBytes)}{e.uploadedByName ? ` · ${e.uploadedByName}` : ""}</span>
                <button aria-label={`Download ${e.fileName}`} onClick={() => downloadEvidence(guardId, record.id, e)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-accent-text)" }}><Download size={14} /></button>
                {canManage && <button aria-label={`Remove ${e.fileName}`} onClick={() => removeFile.mutate(e.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={14} /></button>}
              </li>
            ))}
          </ul>}
        {canManage && (
          <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap", alignItems: "center" }}>
            <input aria-label="Evidence label" placeholder="What is this file? (optional)" value={label} onChange={e => setLabel(e.target.value)} style={{ ...input, width: 220 }} />
            <input ref={fileRef} aria-label="Evidence file" type="file" onChange={e => { const f = e.target.files?.[0]; if (f) upload.mutate(f) }} style={{ fontSize: 12 }} />
          </div>
        )}
      </div>

      <div>
        <div style={lab}>4. Review and decision</div>
        {record.decision ? (
          <div style={{ fontSize: 13 }}>
            <Chip tone={record.decision === "CLEARED" ? "ok" : "bad"}>{record.decision === "CLEARED" ? "Cleared" : "Not cleared"}</Chip>{" "}
            by {record.decidedByName ?? "a reviewer"} on {fmt(record.decidedAt)}{record.decisionNote ? `: ${record.decisionNote}` : ""}
          </div>
        ) : pending ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Record the result first, then a reviewer signs it off.</div>
          : <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Awaiting sign-off.</div>}
        {canManage && !pending && (
          <div style={{ display: "grid", gap: 8, marginTop: 8, maxWidth: 480 }}>
            <input aria-label="Decision note" placeholder="Note (required if not cleared)" value={note} onChange={e => setNote(e.target.value)} style={input} />
            <div style={{ display: "flex", gap: 8 }}>
              <button onClick={() => decide.mutate("CLEARED")} disabled={decide.isPending} style={primary}>Clear</button>
              <button onClick={() => decide.mutate("NOT_CLEARED")} disabled={decide.isPending} style={{ ...btn, color: "var(--hf-danger-text)" }}>Not cleared</button>
            </div>
          </div>
        )}
      </div>

      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}

      {canManage && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button onClick={() => setRecording(r => !r)} style={btn}>{pending ? "Record result" : "Re-record result"}</button>
          <button onClick={() => setRenewing(r => !r)} style={btn}><RefreshCw size={13} /> Renew</button>
        </div>
      )}

      {recording && (
        <div style={{ display: "grid", gap: 8, maxWidth: 480 }}>
          <label style={{ fontSize: 12 }}>Result
            <select value={result.result} onChange={e => setResult({ ...result, result: e.target.value })} style={input}>
              <option value="PASS">Pass</option><option value="FAIL">Fail</option><option value="INCONCLUSIVE">Inconclusive</option>
            </select></label>
          <label style={{ fontSize: 12 }}>Conducted by
            <input value={result.conductedBy} onChange={e => setResult({ ...result, conductedBy: e.target.value })} style={input} /></label>
          <label style={{ fontSize: 12 }}>Conducted on
            <input type="date" value={result.conductedAt} onChange={e => setResult({ ...result, conductedAt: e.target.value })} style={input} /></label>
          <label style={{ fontSize: 12 }}>Valid until
            <input type="date" value={result.nextDueAt} onChange={e => setResult({ ...result, nextDueAt: e.target.value })} style={input} /></label>
          <label style={{ fontSize: 12 }}>Report reference
            <input value={result.reportRef} onChange={e => setResult({ ...result, reportRef: e.target.value })} style={input} /></label>
          <label style={{ fontSize: 12 }}>Notes
            <input value={result.notes} onChange={e => setResult({ ...result, notes: e.target.value })} style={input} /></label>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Saving a new result removes any earlier sign-off, because the reviewer approved a different result.</div>
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => saveResult.mutate()} disabled={saveResult.isPending} style={primary}>Save result</button>
            <button onClick={() => setRecording(false)} style={btn}>Cancel</button>
          </div>
        </div>
      )}

      {renewing && <RequestScreening guardId={guardId} initial={{ screeningType: record.screeningType, reason: "PERIODIC", provider: record.provider }} onClose={() => setRenewing(false)} />}
    </div>
  )
}

const lab: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, marginBottom: 3 }
