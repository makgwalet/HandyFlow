// src/pages/clinic/HandoffQueueTab.tsx
// Nurse -> doctor handoff queue (DEC-CLINIC-004). Doctors accept a handed-over
// consultation, finish their review, and either sign it or send it back to the
// nurse with a reason and comment. Every move is audited server-side.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import QuestionAnswersReadOnly from "./QuestionAnswersReadOnly"
import QuestionForm from "./QuestionForm"

interface QueueItem {
  id: string; patientId: string; patientName: string; practitionerName?: string; chiefComplaint?: string
  status: "READY_FOR_DOCTOR" | "DOCTOR_REVIEWING" | "DOCTOR_COMPLETED" | string
  createdAt: string
}
interface Transition {
  id: string; fromStatus: string; toStatus: string; reasonCode?: string; comment?: string; createdAt: string
}

export const RETURN_REASONS: { code: string; label: string }[] = [
  { code: "MISSING_OBSERVATIONS",   label: "Missing observations" },
  { code: "INCOMPLETE_HISTORY",     label: "Incomplete history" },
  { code: "WRONG_PATIENT_OR_VISIT", label: "Wrong patient or visit" },
  { code: "CLARIFICATION_NEEDED",   label: "Clarification needed" },
  { code: "OTHER",                  label: "Other" },
]
export const STATUS_LABEL: Record<string, string> = {
  READY_FOR_DOCTOR: "Waiting for doctor", DOCTOR_REVIEWING: "Doctor reviewing",
  DOCTOR_COMPLETED: "Review done, ready to sign", RETURNED_TO_NURSE: "Returned to nurse",
  NURSE_IN_PROGRESS: "Nurse in progress", DRAFT: "Draft", SIGNED: "Signed",
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }
const errMsg = (e: any) => e?.response?.data?.message ?? "Something went wrong"
const base = "/api/v1/clinic/consultations"

const btn = (primary = false): React.CSSProperties => ({
  padding: "7px 14px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-accent)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text)",
})

export default function HandoffQueueTab() {
  const qc = useQueryClient()
  const canSign = usePermission("CLINIC_CLINICAL_SIGN")  // doctor actions
  const [error, setError] = useState("")
  const [returning, setReturning] = useState<QueueItem | null>(null)
  const [reason, setReason] = useState("MISSING_OBSERVATIONS")
  const [comment, setComment] = useState("")
  const [historyFor, setHistoryFor] = useState<string | null>(null)

  const { data: queue = [], isLoading } = useQuery<QueueItem[]>({
    queryKey: ["clinic-handoff-queue"],
    queryFn: async () => unwrap(await apiClient.get(`${base}/handoff-queue`)),
    refetchInterval: 30_000,
  })
  // Groups already started on the expanded consultation: the doctor edits these, nothing new is opened.
  const { data: storedCodes = [] } = useQuery<string[]>({
    queryKey: ["clinic-handoff-form-codes", historyFor],
    enabled: !!historyFor,
    queryFn: async () => Object.keys(unwrap(await apiClient.get(`/api/v1/clinic/consultations/${historyFor}/form-data`))?.groups ?? {}),
  })
  const { data: history = [] } = useQuery<Transition[]>({
    queryKey: ["clinic-handoff-history", historyFor],
    enabled: !!historyFor,
    queryFn: async () => unwrap(await apiClient.get(`${base}/${historyFor}/transitions`)),
  })

  const act = useMutation({
    mutationFn: async (a: { id: string; path: string; body?: object }) =>
      apiClient.post(`${base}/${a.id}/${a.path}`, a.body ?? {}),
    onSuccess: () => {
      setError(""); setReturning(null); setComment("")
      qc.invalidateQueries({ queryKey: ["clinic-handoff-queue"] })
      qc.invalidateQueries({ queryKey: ["clinic-handoff-history"] })
    },
    onError: (e: any) => setError(errMsg(e)),
  })

  const fmt = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

  return (
    <div>
      <p style={{ margin: "0 0 16px", color: "var(--hf-text-muted)", fontSize: 13 }}>
        Consultations handed over by nurses, oldest first. Accepting takes the consultation; returning sends it back with a reason.
      </p>
      {error && <div role="alert" style={{ marginBottom: 12, padding: 10, borderRadius: 8,
        background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>}
      {isLoading && <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>}
      {!isLoading && queue.length === 0 && (
        <div style={{ padding: 32, textAlign: "center", color: "var(--hf-text-muted)",
          border: "1px dashed var(--hf-border)", borderRadius: 12 }}>Nothing waiting for a doctor.</div>
      )}
      <div style={{ display: "grid", gap: 10 }}>
        {queue.map(item => (
          <div key={item.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14,
            background: "var(--hf-surface)", display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ flex: "1 1 220px", minWidth: 0 }}>
              <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{item.patientName}</div>
              <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
                {item.chiefComplaint || "No complaint recorded"} · {fmt(item.createdAt)}
              </div>
              <div style={{ fontSize: 12, marginTop: 4, color: "var(--hf-accent-text)", fontWeight: 600 }}>
                {STATUS_LABEL[item.status] ?? item.status}
              </div>
            </div>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {canSign && item.status === "READY_FOR_DOCTOR" && (
                <button style={btn(true)} disabled={act.isPending}
                  onClick={() => act.mutate({ id: item.id, path: "accept" })}>Accept</button>
              )}
              {canSign && item.status === "DOCTOR_REVIEWING" && (
                <button style={btn(true)} disabled={act.isPending}
                  onClick={() => act.mutate({ id: item.id, path: "doctor-complete" })}>Finish review</button>
              )}
              {canSign && item.status === "DOCTOR_COMPLETED" && (
                <button style={btn(true)} disabled={act.isPending}
                  onClick={() => { if (window.confirm("Sign this consultation? It will complete the visit.")) act.mutate({ id: item.id, path: "sign" }) }}>
                  Sign</button>
              )}
              {canSign && <button style={btn()} disabled={act.isPending}
                onClick={() => { setReturning(item); setReason("MISSING_OBSERVATIONS"); setComment(""); setError("") }}>
                Return to nurse</button>}
              <button style={btn()} onClick={() => setHistoryFor(historyFor === item.id ? null : item.id)}>
                {historyFor === item.id ? "Hide details" : "Answers & history"}</button>
            </div>
            {historyFor === item.id && (
              <div style={{ flexBasis: "100%" }}>
                {canSign && item.status === "DOCTOR_REVIEWING" && storedCodes.length > 0
                  ? <QuestionForm consultationId={item.id} patientId={item.patientId} visitType="CONSULTATION" groupCodes={storedCodes} />
                  : <QuestionAnswersReadOnly consultationId={item.id} />}
              </div>
            )}
            {historyFor === item.id && (
              <ol style={{ flexBasis: "100%", margin: "8px 0 0", paddingLeft: 18, fontSize: 12, color: "var(--hf-text-muted)" }}>
                {history.map(t => (
                  <li key={t.id}>
                    {fmt(t.createdAt)}: {STATUS_LABEL[t.fromStatus] ?? t.fromStatus} → {STATUS_LABEL[t.toStatus] ?? t.toStatus}
                    {t.reasonCode ? ` (${RETURN_REASONS.find(r => r.code === t.reasonCode)?.label ?? t.reasonCode})` : ""}
                    {t.comment ? `: ${t.comment}` : ""}
                  </li>
                ))}
                {history.length === 0 && <li>No moves recorded.</li>}
              </ol>
            )}
          </div>
        ))}
      </div>

      {returning && (
        <div role="dialog" aria-modal="true" aria-label="Return to nurse" style={{ position: "fixed", inset: 0,
          background: "rgba(0,0,0,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 16 }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 20, width: "100%", maxWidth: 440 }}>
            <h3 style={{ margin: "0 0 4px", fontSize: 17, color: "var(--hf-text)" }}>Return to nurse</h3>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 12 }}>{returning.patientName}</div>
            <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text)" }}>Reason</label>
            <select value={reason} onChange={e => setReason(e.target.value)}
              style={{ width: "100%", padding: 8, margin: "4px 0 12px", borderRadius: 8, border: "1px solid var(--hf-border)" }}>
              {RETURN_REASONS.map(r => <option key={r.code} value={r.code}>{r.label}</option>)}
            </select>
            <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text)" }}>Comment (required)</label>
            <textarea value={comment} onChange={e => setComment(e.target.value)} rows={3}
              style={{ width: "100%", padding: 8, marginTop: 4, borderRadius: 8, border: "1px solid var(--hf-border)", boxSizing: "border-box" }} />
            {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
              <button style={btn()} onClick={() => setReturning(null)}>Cancel</button>
              <button style={btn(true)} disabled={act.isPending || !comment.trim()}
                onClick={() => act.mutate({ id: returning.id, path: "return-to-nurse", body: { reasonCode: reason, comment } })}>
                Return</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
