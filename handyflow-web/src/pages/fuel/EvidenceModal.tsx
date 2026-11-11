import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { btnGhost, btnPrimary, Chip, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { canReplace, canVerify, emptyEvidence, hasVerifiedProof, KIND_LABEL, toEvidenceRequest, validateEvidence, type Evidence, type EvidenceForm } from "./distributionOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

/** Proof of delivery for one delivery: add, replace (before verification) and verify (by someone else, which locks it). */
export default function EvidenceModal({ deliveryId, label, canAdd, onClose }: { deliveryId: string; label: string; canAdd: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["FUEL_MANAGE"])
  const canVerifyPerm = usePermissions(["FUEL_EVIDENCE_VERIFY"])
  const me = useAuthStore(s => s.user?.userId) as string | undefined
  const key = ["fuel-evidence", deliveryId]
  const q = useQuery<Evidence[]>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/deliveries/${deliveryId}/evidence`)) })
  const [form, setForm] = useState<EvidenceForm | null>(null)
  const [replacing, setReplacing] = useState<Evidence | null>(null)
  const [problem, setProblem] = useState("")
  const refresh = () => { qc.invalidateQueries({ queryKey: key }); setForm(null); setReplacing(null); setProblem("") }
  const add = useMutation({ mutationFn: (f: EvidenceForm) => apiClient.post(`/api/v1/fuel/deliveries/${deliveryId}/evidence`, toEvidenceRequest(f)), onSuccess: refresh })
  const verify = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/evidence/${id}/verify`), onSuccess: refresh })
  const replace = useMutation({
    mutationFn: (a: { id: string; reason: string }) => apiClient.post(`/api/v1/fuel/evidence/${a.id}/supersede`, { reason: a.reason, replacement: form ? toEvidenceRequest(form) : null }),
    onSuccess: refresh,
  })
  const list = Array.isArray(q.data) ? q.data : []
  const set = (k: keyof EvidenceForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setProblem(""); setForm(f => f && ({ ...f, [k]: e.target.value } as EvidenceForm)) }
  return (
    <Modal title={`Proof of delivery: ${label}`} onClose={onClose} width={560}>
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : (
        <>
          {!hasVerifiedProof(list) && <p style={{ fontSize: 12.5, color: "var(--hf-warning-text)", margin: "0 0 10px" }}>No verified proof yet.</p>}
          {list.length === 0 && <p style={{ fontSize: 13 }}>Nothing recorded yet.</p>}
          {list.map(e => (
            <div key={e.id} style={{ padding: "8px 0", borderTop: "1px solid var(--hf-border)", opacity: e.status === "SUPERSEDED" ? 0.6 : 1 }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <strong style={{ fontSize: 13 }}>{KIND_LABEL[e.kind]}</strong>
                {e.locked && <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">Verified and locked</Chip>}
                {e.status === "SUPERSEDED" && <Chip bg="var(--hf-surface-alt)" fg="var(--hf-text-muted)">Replaced</Chip>}
                <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{when(e.capturedAt)}</span>
                <span style={{ flex: 1 }} />
                {canVerifyPerm && canVerify(e, me) && <button style={btnPrimary} disabled={verify.isPending} onClick={() => verify.mutate(e.id)} aria-label={`Verify ${KIND_LABEL[e.kind]}`}>Verify</button>}
                {canManage && canReplace(e) && <button style={btnGhost} onClick={() => { setForm(emptyEvidence()); setReplacing(e) }} aria-label={`Replace ${KIND_LABEL[e.kind]}`}>Replace</button>}
              </div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                {e.kind === "GPS" ? `${e.latitude}, ${e.longitude}${e.accuracyM != null ? ` (±${e.accuracyM} m)` : ""}` : e.fileRef}
                {e.note ? ` · ${e.note}` : ""}{e.supersededReason ? ` · Replaced: ${e.supersededReason}` : ""}
              </div>
            </div>
          ))}
          <ErrorLine error={verify.error} />
          {canAdd && !form && <button style={{ ...btnPrimary, marginTop: 12 }} onClick={() => setForm(emptyEvidence())}>Add evidence</button>}
          {form && !replacing && (
            <div style={{ marginTop: 12, borderTop: "1px solid var(--hf-border)", paddingTop: 12 }}>
              <EvidenceFields form={form} set={set} />
              {problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{problem}</p>}
              <ErrorLine error={add.error} />
              <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
                <button style={btnGhost} onClick={() => setForm(null)}>Cancel</button>
                <button style={btnPrimary} disabled={add.isPending} onClick={() => { const p = validateEvidence(form); if (p) return setProblem(p); add.mutate(form) }}>Save evidence</button>
              </div>
            </div>
          )}
          {form && replacing && (
            <ReplaceBlock form={form} set={set} problem={problem} pending={replace.isPending} error={replace.error}
              onCancel={() => { setForm(null); setReplacing(null); setProblem("") }}
              onConfirm={reason => { const p = validateEvidence(form); if (p) return setProblem(p); replace.mutate({ id: replacing.id, reason }) }} />
          )}
        </>
      )}
    </Modal>
  )
}

function EvidenceFields({ form, set }: { form: EvidenceForm; set: (k: keyof EvidenceForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => void }) {
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div>
        <label style={labelStyle} htmlFor="ev-kind">Type</label>
        <select id="ev-kind" style={{ ...inputStyle, width: "100%" }} value={form.kind} onChange={set("kind")}>
          {Object.entries(KIND_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
      </div>
      {form.kind === "GPS" ? (
        <>
          <div><label style={labelStyle} htmlFor="ev-lat">Latitude</label><input id="ev-lat" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={form.latitude} onChange={set("latitude")} /></div>
          <div><label style={labelStyle} htmlFor="ev-lon">Longitude</label><input id="ev-lon" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={form.longitude} onChange={set("longitude")} /></div>
          <div><label style={labelStyle} htmlFor="ev-acc">Accuracy (metres, optional)</label><input id="ev-acc" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={form.accuracyM} onChange={set("accuracyM")} /></div>
        </>
      ) : (
        <div><label style={labelStyle} htmlFor="ev-file">File link or reference</label><input id="ev-file" style={{ ...inputStyle, width: "100%" }} value={form.fileRef} onChange={set("fileRef")} /></div>
      )}
      <div><label style={labelStyle} htmlFor="ev-note">Note (optional)</label><input id="ev-note" style={{ ...inputStyle, width: "100%" }} value={form.note} maxLength={300} onChange={set("note")} /></div>
    </div>
  )
}

function ReplaceBlock({ form, set, problem, pending, error, onCancel, onConfirm }: {
  form: EvidenceForm; set: (k: keyof EvidenceForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => void
  problem: string; pending: boolean; error: unknown; onCancel: () => void; onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState("")
  return (
    <div style={{ marginTop: 12, borderTop: "1px solid var(--hf-border)", paddingTop: 12 }}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>The old record is kept and marked as replaced. Enter the correct evidence and the reason.</p>
      <EvidenceFields form={form} set={set} />
      <label style={{ ...labelStyle, marginTop: 8 }} htmlFor="ev-reason">Reason for replacing *</label>
      <input id="ev-reason" style={{ ...inputStyle, width: "100%" }} maxLength={300} value={reason} onChange={e => setReason(e.target.value)} />
      {problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{problem}</p>}
      <ErrorLine error={error} />
      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        <button style={btnGhost} onClick={onCancel}>Cancel</button>
        <button style={btnPrimary} disabled={pending || reason.trim().length < 5} onClick={() => onConfirm(reason.trim())}>Replace evidence</button>
      </div>
    </div>
  )
}
