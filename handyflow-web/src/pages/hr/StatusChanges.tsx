// src/pages/hr/StatusChanges.tsx
//
// Suspending and reinstating an employee. Another HR manager decides; nobody approves their own (the server enforces this
// and says so). An emergency security or safety suspension takes effect at once and another HR manager must review it by
// the end of the next business day. Shown only to HR managers.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

export interface StatusItem {
  id: string; employeeId: string; employeeName: string; fromStatus: string; toStatus: string; reason: string; emergency: boolean
  status: string; requestedBy: string | null; requestedByName: string | null; requestedAt: string; reviewDueAt: string | null; reviewOverdue: boolean
}
export interface Waiting { approvals: StatusItem[]; reviews: StatusItem[] }

export const MIN_REASON = 10
export const verb = (to: string) => (to === "SUSPENDED" ? "suspension" : "reinstatement")
export const reasonProblem = (r: string) => (r.trim().length < MIN_REASON ? `Give the reason (at least ${MIN_REASON} characters).` : null)
export const askedBy = (i: Pick<StatusItem, "requestedByName">) => (i.requestedByName?.trim() ? i.requestedByName.trim() : "A colleague")

const messageOf = (e: unknown, fallback: string): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback

export function StatusChangeDialog({ employeeId, employeeName, currentStatus, onClose }: { employeeId: string; employeeName: string; currentStatus: string; onClose: () => void }) {
  const qc = useQueryClient()
  const to = currentStatus === "SUSPENDED" ? "ACTIVE" : "SUSPENDED"
  const [reason, setReason] = useState("")
  const [emergency, setEmergency] = useState(false)
  const [err, setErr] = useState("")
  const [result, setResult] = useState("")
  const send = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/employees/${employeeId}/status-change`, { toStatus: to, reason: reason.trim(), emergency }),
    onSuccess: (res: any) => { setErr(""); setResult(res?.data?.message ?? "Done"); qc.invalidateQueries({ queryKey: ["status-changes-waiting"] }); qc.invalidateQueries({ queryKey: ["hr-employees"] }) },
    onError: (e: unknown) => setErr(messageOf(e, "That could not be recorded.")),
  })
  const problem = reasonProblem(reason)
  return (
    <div role="dialog" aria-modal="true" aria-label={to === "SUSPENDED" ? `Suspend ${employeeName}` : `Reinstate ${employeeName}`}
      style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", zIndex: 200, display: "flex", alignItems: "center", justifyContent: "center", padding: 16 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 14, width: "100%", maxWidth: 440, padding: 20 }}>
        <h3 style={{ margin: "0 0 4px", fontSize: 17 }}>{to === "SUSPENDED" ? "Suspend" : "Reinstate"} {employeeName}</h3>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>
          Another HR manager has to approve this before it takes effect. Nobody approves their own request.
        </p>
        {result ? <div role="status" style={{ fontSize: 13, marginBottom: 12 }}>{result}</div> : <>
          <label htmlFor="sc-reason" style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Reason *</label>
          <textarea id="sc-reason" value={reason} onChange={e => setReason(e.target.value)} rows={3} maxLength={1000}
            style={{ width: "100%", boxSizing: "border-box", padding: 8, border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13 }} />
          {to === "SUSPENDED" && <label style={{ display: "flex", gap: 8, alignItems: "flex-start", fontSize: 13, marginTop: 10 }}>
            <input type="checkbox" checked={emergency} onChange={e => setEmergency(e.target.checked)} />
            <span>Emergency security or safety suspension. Takes effect now, and another HR manager must review it by the end of the next business day.</span>
          </label>}
          {err && <div role="alert" style={{ marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>{err}</div>}
        </>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
          <button type="button" onClick={onClose}>{result ? "Close" : "Cancel"}</button>
          {!result && <button type="button" disabled={!!problem || send.isPending} onClick={() => send.mutate()}>
            {send.isPending ? "Saving…" : emergency ? "Suspend now" : to === "SUSPENDED" ? "Ask for suspension" : "Ask for reinstatement"}</button>}
        </div>
      </div>
    </div>
  )
}

export default function StatusChanges() {
  const allowed = usePermission("HR_MANAGE")
  const qc = useQueryClient()
  const [acting, setActing] = useState<{ item: StatusItem; kind: "decline" | "confirm" | "reverse" } | null>(null)
  const [text, setText] = useState("")
  const [err, setErr] = useState("")
  const [done, setDone] = useState("")

  const { data } = useQuery<Waiting>({
    queryKey: ["status-changes-waiting"], enabled: allowed,
    queryFn: async () => (await apiClient.get("/api/v1/hr/status-changes/waiting")).data?.data ?? { approvals: [], reviews: [] },
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["status-changes-waiting"] }); qc.invalidateQueries({ queryKey: ["hr-employees"] }) }
  const onError = (fallback: string) => (e: unknown) => { setDone(""); setErr(messageOf(e, fallback)) }
  const approve = useMutation({
    mutationFn: (i: StatusItem) => apiClient.post(`/api/v1/hr/status-changes/${i.id}/approve`, {}),
    onSuccess: (_d, i) => { setErr(""); setDone(`${i.employeeName}: ${verb(i.toStatus)} approved.`); refresh() }, onError: onError("That could not be approved."),
  })
  const act = useMutation({
    mutationFn: ({ item, kind }: { item: StatusItem; kind: "decline" | "confirm" | "reverse" }) =>
      kind === "decline" ? apiClient.post(`/api/v1/hr/status-changes/${item.id}/reject`, { note: text.trim() })
        : apiClient.post(`/api/v1/hr/status-changes/${item.id}/review`, { confirm: kind === "confirm", note: text.trim() }),
    onSuccess: (_d, v) => { setErr(""); setDone(`${v.item.employeeName}: ${v.kind === "decline" ? "request declined" : v.kind === "confirm" ? "suspension confirmed" : "suspension reversed, employee reinstated"}.`); setActing(null); setText(""); refresh() },
    onError: onError("That could not be recorded."),
  })

  const approvals = data?.approvals ?? [], reviews = data?.reviews ?? []
  if (!allowed || (approvals.length === 0 && reviews.length === 0 && !done)) return null
  const box = (item: StatusItem, kind: "decline" | "confirm" | "reverse", label: string) => acting?.item.id === item.id && acting.kind === kind ? (
    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
      <input aria-label={kind === "decline" ? "Reason for declining" : "Outcome of the review"} value={text} onChange={e => setText(e.target.value)}
        placeholder={kind === "decline" ? "Why is it declined?" : "What did the review find?"} style={{ padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 13 }} />
      <button type="button" disabled={(kind === "decline" ? !text.trim() : text.trim().length < MIN_REASON) || act.isPending} onClick={() => act.mutate({ item, kind })}>{label}</button>
      <button type="button" onClick={() => { setActing(null); setText("") }}>Back</button>
    </div>) : null

  return (
    <section aria-label="Employee status changes waiting" style={{ marginBottom: 16, padding: 16, border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
      <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 10 }}>Suspensions and reinstatements waiting</div>
      {done && <div role="status" style={{ fontSize: 13, marginBottom: 8 }}>{done}</div>}
      {err && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger)", marginBottom: 8 }}>{err}</div>}
      <div style={{ display: "grid", gap: 8 }}>
        {approvals.map(i => (
          <div key={i.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", padding: "10px 12px", border: "1px solid var(--hf-border-subtle)", borderRadius: 8 }}>
            <div style={{ fontSize: 13, minWidth: 220 }}><strong>{i.employeeName}</strong>: {verb(i.toStatus)}
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{askedBy(i)} asked: {i.reason}</div></div>
            {box(i, "decline", "Decline") ?? <div style={{ display: "flex", gap: 8 }}>
              <button type="button" disabled={approve.isPending} onClick={() => approve.mutate(i)}>Approve</button>
              <button type="button" onClick={() => { setActing({ item: i, kind: "decline" }); setText(""); setErr("") }}>Decline</button></div>}
          </div>
        ))}
        {reviews.map(i => (
          <div key={i.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", padding: "10px 12px", border: `1px solid ${i.reviewOverdue ? "var(--hf-danger)" : "var(--hf-border-subtle)"}`, borderRadius: 8 }}>
            <div style={{ fontSize: 13, minWidth: 220 }}><strong>{i.employeeName}</strong>: emergency suspension, in effect
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{askedBy(i)} suspended: {i.reason}</div>
              <div style={{ fontSize: 12, color: i.reviewOverdue ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>
                {i.reviewOverdue ? "Review overdue" : "Review due"} {i.reviewDueAt ? new Date(i.reviewDueAt).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) : ""}</div></div>
            {box(i, "confirm", "Confirm suspension") ?? box(i, "reverse", "Reverse and reinstate") ?? <div style={{ display: "flex", gap: 8 }}>
              <button type="button" onClick={() => { setActing({ item: i, kind: "confirm" }); setText(""); setErr("") }}>Confirm</button>
              <button type="button" onClick={() => { setActing({ item: i, kind: "reverse" }); setText(""); setErr("") }}>Reverse</button></div>}
          </div>
        ))}
      </div>
    </section>
  )
}
