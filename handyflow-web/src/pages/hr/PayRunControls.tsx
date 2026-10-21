// src/pages/hr/PayRunControls.tsx
//
// The steps of a pay run (calculate, approve, send back, finalise) and what a checker looks at before approving.
// The server decides what is allowed; the buttons only avoid offering what would be refused.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, AlertTriangle, CheckCircle2 } from "lucide-react"
import { apiClient } from "../../api/client"
import {
  eventText, noteProblem, reviewSummary, stepsFor, waitingFor,
  type Review, type Rights, type RunEvent, type Step,
} from "./payrollControls.logic"

type Wrapped = { data?: { data?: unknown } }
type ApiErr = { response?: { data?: { message?: string } } }
const unwrap = <T,>(r: Wrapped): T => (r.data?.data ?? r.data) as T
const messageOf = (e: unknown, fallback: string) => (e as ApiErr)?.response?.data?.message ?? fallback
const when = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })

export interface RunLite { id: string; payRunNumber: string; status: string; preparedBy: string | null }

const PATH: Record<Exclude<Step["key"], "calculate">, string> = { approve: "approve", sendBack: "send-back", finalise: "finalise" }

export function RunActions({ run, rights, userId, allowSelf, onCalculated, onError }: {
  run: RunLite; rights: Rights; userId: string | undefined; allowSelf: boolean
  onCalculated: (warnings: string[]) => void; onError: (message: string) => void
}) {
  const qc = useQueryClient()
  const [asking, setAsking] = useState<Step | null>(null)
  const [note, setNote] = useState("")
  const steps = stepsFor(run, rights, userId, allowSelf)

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["pay-runs"] })
    qc.invalidateQueries({ queryKey: ["hr-run-review", run.id] })
    qc.invalidateQueries({ queryKey: ["hr-run-history", run.id] })
    qc.invalidateQueries({ queryKey: ["payslips", run.id] })
  }

  const calculate = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/pay-runs/${run.id}/process`),
    onSuccess: res => { refresh(); const w = (unwrap<{ warnings?: string[] }>(res)).warnings; onCalculated(Array.isArray(w) ? w : []) },
    onError: e => onError(messageOf(e, "The pay run could not be calculated.")),
  })

  const act = useMutation({
    mutationFn: (s: Step) => apiClient.post(`/api/v1/hr/pay-runs/${run.id}/${PATH[s.key as keyof typeof PATH]}`, { note: note.trim() || null }),
    onSuccess: () => { refresh(); setAsking(null); setNote(""); onError("") },
    onError: e => onError(messageOf(e, "That step could not be recorded.")),
  })

  if (steps.length === 0) {
    const w = waitingFor(run.status)
    return w ? <div style={{ padding: "8px 20px", fontSize: 12, color: "var(--hf-text-muted)", borderTop: "1px solid var(--hf-border-subtle)" }}>{w}</div> : null
  }

  const problem = asking ? noteProblem(asking, note) : ""
  return (
    <div style={{ padding: "10px 20px 12px", borderTop: "1px solid var(--hf-border-subtle)", background: "var(--hf-surface)" }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <span style={{ fontSize: 12, color: "var(--hf-text-muted)", marginRight: 4 }}>{waitingFor(run.status)}</span>
        {steps.map(s => (
          <button key={s.key} type="button" disabled={!s.enabled || calculate.isPending || act.isPending}
            title={s.reason ?? undefined}
            onClick={e => { e.stopPropagation(); if (s.key === "calculate") calculate.mutate(); else { setAsking(s); setNote("") } }}
            style={{ padding: "7px 14px", borderRadius: 7, fontSize: 13, fontWeight: 600, cursor: s.enabled ? "pointer" : "not-allowed",
              border: s.key === "sendBack" ? "1px solid var(--hf-border)" : "none",
              background: !s.enabled ? "var(--hf-surface-muted)" : s.key === "sendBack" ? "var(--hf-surface)" : "var(--hf-primary)",
              color: !s.enabled ? "var(--hf-text-faint)" : s.key === "sendBack" ? "var(--hf-text-secondary)" : "var(--hf-text-on-solid)" }}>
            {s.key === "calculate" && calculate.isPending ? "Calculating…" : s.label}
          </button>
        ))}
      </div>
      {steps.some(s => !s.enabled && s.reason) && (
        <div style={{ marginTop: 6, fontSize: 12, color: "var(--hf-text-muted)" }}>{steps.find(s => s.reason)?.reason}</div>
      )}
      {asking && (
        <form onClick={e => e.stopPropagation()} onSubmit={e => { e.preventDefault(); if (!problem) act.mutate(asking) }} style={{ marginTop: 10 }}>
          <label htmlFor={`note-${run.id}`} style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>
            {asking.needsNote ? "Reason for sending it back *" : `Note for the record (optional) — ${asking.label}`}
          </label>
          <input id={`note-${run.id}`} value={note} onChange={e => setNote(e.target.value)} maxLength={500} autoFocus
            style={{ width: "100%", padding: "8px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }} />
          {problem && note.length > 0 && <div role="alert" style={{ marginTop: 6, fontSize: 12, color: "var(--hf-danger-text)" }}>{problem}</div>}
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 8 }}>
            <button type="button" onClick={() => setAsking(null)} style={{ padding: "7px 14px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 13, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
            <button type="submit" disabled={!!problem || act.isPending}
              style={{ padding: "7px 16px", border: "none", borderRadius: 7, fontSize: 13, fontWeight: 700, color: "var(--hf-text-on-solid)", cursor: problem ? "not-allowed" : "pointer", background: problem || act.isPending ? "var(--hf-text-faint)" : "var(--hf-primary)" }}>
              {act.isPending ? "Saving…" : `Confirm: ${asking.label}`}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}

export function RunReview({ run, userId }: { run: RunLite; userId: string | undefined }) {
  const showReview = ["PREPARED", "APPROVED", "COMPLETED"].includes(run.status)
  const review = useQuery<Review>({
    queryKey: ["hr-run-review", run.id],
    queryFn: async () => unwrap<Review>(await apiClient.get(`/api/v1/hr/pay-runs/${run.id}/review`)),
    enabled: showReview,
  })
  const history = useQuery<RunEvent[]>({
    queryKey: ["hr-run-history", run.id],
    queryFn: async () => unwrap<RunEvent[]>(await apiClient.get(`/api/v1/hr/pay-runs/${run.id}/history`)),
  })
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 16, marginBottom: 16 }}>
      {showReview && (
        <section aria-label="Review" style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, background: "var(--hf-surface)" }}>
          <h4 style={{ margin: "0 0 6px", fontSize: 13, color: "var(--hf-text)" }}>What to check</h4>
          {review.isLoading ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Checking the run…</div>
            : review.isError ? <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{messageOf(review.error, "The review could not be loaded.")}</div>
            : review.data && (
              <>
                <div style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 12, fontWeight: 600, color: review.data.blocking ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>
                  {review.data.blocking ? <AlertCircle size={13} /> : <CheckCircle2 size={13} />} {reviewSummary(review.data)}
                </div>
                <ul style={{ margin: "8px 0 0", padding: 0, listStyle: "none", fontSize: 12 }}>
                  {review.data.items.map((it, i) => (
                    <li key={`${it.code}-${i}`} style={{ display: "flex", gap: 6, padding: "4px 0", borderTop: "1px solid var(--hf-border-subtle)", color: it.severity === "ERROR" ? "var(--hf-danger-text)" : "var(--hf-warning-text)" }}>
                      {it.severity === "ERROR" ? <AlertCircle size={12} style={{ flexShrink: 0, marginTop: 2 }} /> : <AlertTriangle size={12} style={{ flexShrink: 0, marginTop: 2 }} />}
                      <span><strong>{it.employee ?? "Run"}</strong>{it.employee ? ": " : " "}{it.message}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
        </section>
      )}
      <section aria-label="History" style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, background: "var(--hf-surface)" }}>
        <h4 style={{ margin: "0 0 6px", fontSize: 13, color: "var(--hf-text)" }}>History</h4>
        {history.isLoading ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Loading…</div>
          : history.isError ? <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>The history could not be loaded.</div>
          : (history.data ?? []).length === 0 ? <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Nothing recorded yet. Steps taken from now on are written here.</div>
          : (
            <ol style={{ margin: 0, padding: 0, listStyle: "none", fontSize: 12 }}>
              {(history.data ?? []).map(e => (
                <li key={e.id} style={{ padding: "5px 0", borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <strong>{eventText(e, userId)}</strong> <span style={{ color: "var(--hf-text-faint)" }}>{when(e.at)}</span>
                  {e.note && <div style={{ color: "var(--hf-text-secondary)" }}>{e.note}</div>}
                </li>
              ))}
            </ol>
          )}
      </section>
    </div>
  )
}
