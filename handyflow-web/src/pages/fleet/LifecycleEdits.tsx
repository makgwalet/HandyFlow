// src/pages/fleet/LifecycleEdits.tsx
//
// Pieces of the Lifecycle tab: edit the open theft report, edit a write-off (salvage and insurance settlement), and the evidence files on an event.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { EVIDENCE_LABEL, EVIDENCE_TYPES, suggestedEvidenceType, theftEditBody, theftEditProblem, toTheftEdit, toWriteOffEdit, writeOffEditBody, writeOffEditProblem, type TheftData, type TheftEditForm, type WriteOffData, type WriteOffEditForm } from "./lifecycle.logic"
import { attachmentProblem, fileSizeText } from "./workOrders.logic"

export interface Evidence { id: string; type: string; description: string | null; fileName: string; sizeBytes: number; uploadedAt: string }

const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5, width: "100%", boxSizing: "border-box" }
const small: React.CSSProperties = { ...field, width: "auto", cursor: "pointer" }
const btn: React.CSSProperties = { ...small, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700 }
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(190px,1fr))", gap: 8, margin: "8px 0" }

function Actions({ problem, pending, label, onCancel, error }: { problem: string | null; pending: boolean; label: string; onCancel: () => void; error: string | null }) {
  return <>
    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
      <button type="submit" style={{ ...btn, opacity: problem || pending ? 0.5 : 1 }} disabled={!!problem || pending}>{pending ? "Saving…" : label}</button>
      <button type="button" style={small} onClick={onCancel}>Cancel</button>
      {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
    </div>
    {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
  </>
}

export function TheftEdit({ vehicleId, theft, onDone }: { vehicleId: string; theft: TheftData; onDone: () => void }) {
  const [f, setF] = useState<TheftEditForm>(() => toTheftEdit(theft))
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({ mutationFn: () => apiClient.put(`/api/v1/fleet/vehicles/${vehicleId}/lifecycle/theft`, theftEditBody(f)), onSuccess: onDone, onError: e => setError(errorOf(e)) })
  const problem = theftEditProblem(f)
  const set = (k: keyof TheftEditForm, v: string) => { setF({ ...f, [k]: v }); setError(null) }
  const text = (k: keyof TheftEditForm, label: string) => <label style={{ fontSize: 12 }}>{label}<input aria-label={label} style={field} value={f[k]} onChange={e => set(k, e.target.value)} /></label>
  return (
    <form aria-label="Edit theft report" onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, margin: "8px 0" }}>
      <strong>Theft report details</strong>
      <div style={grid}>{text("location", "Where")}{text("policeCaseNumber", "SAPS case number")}{text("sapsStation", "SAPS station")}{text("policeReference", "Police reference")}{text("insurer", "Insurer")}{text("claimNumber", "Claim number")}</div>
      <label style={{ fontSize: 12 }}>Tracking information<textarea aria-label="Tracking information" rows={2} style={{ ...field, fontFamily: "inherit" }} value={f.trackingInfo} onChange={e => set("trackingInfo", e.target.value)} /></label>
      <label style={{ fontSize: 12, display: "block", marginTop: 6 }}>Notes<textarea aria-label="Notes" rows={2} style={{ ...field, fontFamily: "inherit" }} value={f.notes} onChange={e => set("notes", e.target.value)} /></label>
      <div style={{ marginTop: 8 }}><Actions problem={problem} pending={save.isPending} label="Save report" onCancel={onDone} error={error} /></div>
    </form>
  )
}

export function WriteOffEdit({ vehicleId, writeOff, onDone }: { vehicleId: string; writeOff: WriteOffData; onDone: () => void }) {
  const [f, setF] = useState<WriteOffEditForm>(() => toWriteOffEdit(writeOff))
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({ mutationFn: () => apiClient.put(`/api/v1/fleet/vehicles/${vehicleId}/lifecycle/write-off`, writeOffEditBody(f)), onSuccess: onDone, onError: e => setError(errorOf(e)) })
  const problem = writeOffEditProblem(f)
  const set = (k: keyof WriteOffEditForm, v: string) => { setF({ ...f, [k]: v }); setError(null) }
  const text = (k: keyof WriteOffEditForm, label: string, type = "text") => <label style={{ fontSize: 12 }}>{label}<input aria-label={label} type={type} style={field} value={f[k]} onChange={e => set(k, e.target.value)} /></label>
  return (
    <form aria-label="Edit write-off" onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, margin: "8px 0" }}>
      <strong>Write-off, salvage and settlement</strong>
      <div style={grid}>{text("assessmentDate", "Assessment date", "date")}{text("assessorName", "Assessor")}{text("decisionDate", "Decision date", "date")}{text("preAccidentValue", "Pre-accident value (R)", "number")}
        {text("salvageValue", "Salvage value (R)", "number")}{text("salvageBuyer", "Salvage buyer")}{text("salvageSoldAt", "Salvage sold on", "date")}{text("insuranceSettlementAmount", "Insurance settlement (R)", "number")}{text("insuranceSettledAt", "Settled on", "date")}</div>
      <label style={{ fontSize: 12 }}>Decision note<textarea aria-label="Decision note" rows={2} style={{ ...field, fontFamily: "inherit" }} value={f.decisionNote} onChange={e => set("decisionNote", e.target.value)} /></label>
      <div style={{ marginTop: 8 }}><Actions problem={problem} pending={save.isPending} label="Save write-off" onCancel={onDone} error={error} /></div>
    </form>
  )
}

function saveFile(data: BlobPart, fileName: string) {
  const url = URL.createObjectURL(new Blob([data])), a = document.createElement("a")
  a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url)
}

/** SAPS reports, sale agreements, settlement letters and the like, on one lifecycle event. FLEET_MANAGE adds; FLEET_ADMIN deletes. */
export function EventEvidence({ vehicleId, eventId, eventType, evidence }: { vehicleId: string; eventId: string; eventType: string; evidence: Evidence[] }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE"), canAdmin = usePermission("FLEET_ADMIN")
  const base = `/api/v1/fleet/vehicles/${vehicleId}/lifecycle/events/${eventId}/evidence`
  const [type, setType] = useState(""), [description, setDescription] = useState(""), [file, setFile] = useState<File | null>(null), [fileKey, setFileKey] = useState(0)
  const [confirmId, setConfirmId] = useState<string | null>(null), [error, setError] = useState<string | null>(null)
  const refresh = () => qc.invalidateQueries({ queryKey: ["fleet", "lifecycle", vehicleId] })
  const chosen = type || suggestedEvidenceType(eventType)
  const problem = attachmentProblem(chosen, file, evidence.length)
  const upload = useMutation({
    mutationFn: () => { const f = new FormData(); f.append("file", file as File); f.append("type", chosen); if (description.trim()) f.append("description", description.trim()); return apiClient.post(base, f) },
    onSuccess: () => { setError(null); setType(""); setDescription(""); setFile(null); setFileKey(k => k + 1); refresh() }, onError: e => setError(errorOf(e)),
  })
  const download = useMutation({ mutationFn: async (a: Evidence) => { const r = await apiClient.get(`${base}/${a.id}/download`, { responseType: "blob" }); saveFile(r.data as BlobPart, a.fileName) }, onError: e => setError(errorOf(e)) })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/${id}`), onSuccess: () => { setConfirmId(null); setError(null); refresh() }, onError: e => setError(errorOf(e)) })
  if (evidence.length === 0 && !canManage) return null
  return (
    <div style={{ marginTop: 6 }} aria-label="Evidence">
      <div style={{ fontSize: 11.5, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>Evidence ({evidence.length})</div>
      <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 12.5 }}>{evidence.map(a => (
        <li key={a.id} aria-label={a.fileName} style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", padding: "3px 0" }}>
          <strong>{EVIDENCE_LABEL[a.type] ?? a.type}</strong><span>{a.fileName}</span><span style={{ color: "var(--hf-text-faint)", fontSize: 11.5 }}>{fileSizeText(a.sizeBytes)}</span>
          {a.description && <span style={{ color: "var(--hf-text-muted)" }}>{a.description}</span>}
          <span style={{ marginLeft: "auto", display: "flex", gap: 6 }}>
            <button type="button" style={small} aria-label={`Download ${a.fileName}`} onClick={() => download.mutate(a)}>Download</button>
            {canAdmin && (confirmId === a.id
              ? <><button type="button" style={{ ...small, color: "var(--hf-danger-text)", fontWeight: 700 }} aria-label={`Confirm delete ${a.fileName}`} onClick={() => remove.mutate(a.id)}>Confirm delete</button><button type="button" style={small} onClick={() => setConfirmId(null)}>Keep</button></>
              : <button type="button" style={small} aria-label={`Delete ${a.fileName}`} onClick={() => setConfirmId(a.id)}>Delete</button>)}
          </span>
        </li>))}</ul>
      {canManage && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginTop: 4 }}>
          <select aria-label="Evidence type" style={{ ...small, cursor: "default" }} value={chosen} onChange={e => { setType(e.target.value); setError(null) }}><option value="">Type…</option>{EVIDENCE_TYPES.map(t => <option key={t} value={t}>{EVIDENCE_LABEL[t]}</option>)}</select>
          <input aria-label="Evidence note" placeholder="Note (optional)" style={{ ...field, width: 150 }} value={description} onChange={e => setDescription(e.target.value)} />
          <input key={fileKey} aria-label="Evidence file" type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.docx" onChange={e => { setFile(e.target.files?.[0] ?? null); setError(null) }} />
          <button type="button" style={{ ...btn, opacity: problem || upload.isPending ? 0.5 : 1 }} disabled={!!problem || upload.isPending} onClick={() => upload.mutate()}>{upload.isPending ? "Uploading…" : "Attach"}</button>
          {problem && file && <span style={{ fontSize: 11.5, color: "var(--hf-danger-text)" }}>{problem}</span>}
        </div>)}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
    </div>
  )
}
