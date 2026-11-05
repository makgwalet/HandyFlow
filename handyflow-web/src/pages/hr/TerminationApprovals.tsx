// src/pages/hr/TerminationApprovals.tsx
//
// Terminations waiting for a second HR manager. Ending employment is decided by someone other than the person who
// asked (the server enforces this and says so). Shown only to HR managers, and only when something is waiting.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

export interface PendingTermination {
  id: string; employeeId: string; employeeName: string; endDate: string; reason: string; endType: string | null
  noticeGivenOn: string | null; requestedBy: string | null; requestedByName: string | null; requestedAt: string
}

export const askedByText = (r: Pick<PendingTermination, "requestedByName">) => r.requestedByName?.trim() ? r.requestedByName.trim() : "A colleague"

const messageOf = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback

export default function TerminationApprovals() {
  const allowed = usePermission("HR_MANAGE")
  const qc = useQueryClient()
  const [declining, setDeclining] = useState<PendingTermination | null>(null)
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const [done, setDone] = useState("")

  const { data } = useQuery<PendingTermination[]>({
    queryKey: ["pending-terminations"], enabled: allowed,
    queryFn: async () => (await apiClient.get("/api/v1/hr/terminations/pending")).data?.data ?? [],
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["pending-terminations"] }); qc.invalidateQueries({ queryKey: ["hr-employees"] }) }
  const approve = useMutation({
    mutationFn: (r: PendingTermination) => apiClient.post(`/api/v1/hr/terminations/${r.id}/approve`, {}),
    onSuccess: (_d, r) => { setErr(""); setDone(`Termination of ${r.employeeName} approved.`); refresh() },
    onError: (e: unknown) => { setDone(""); setErr(messageOf(e, "The termination could not be approved.")) },
  })
  const decline = useMutation({
    mutationFn: (r: PendingTermination) => apiClient.post(`/api/v1/hr/terminations/${r.id}/reject`, { note: reason.trim() }),
    onSuccess: (_d, r) => { setErr(""); setDone(`Termination of ${r.employeeName} declined. They stay employed.`); setDeclining(null); setReason(""); refresh() },
    onError: (e: unknown) => setErr(messageOf(e, "The termination could not be declined.")),
  })

  const rows = data ?? []
  if (!allowed || (rows.length === 0 && !done)) return null
  return (
    <section aria-label="Terminations waiting for approval" style={{ marginBottom: 16, padding: 16, border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
      <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 10 }}>Terminations waiting for approval</div>
      {done && <div role="status" style={{ fontSize: 13, marginBottom: 8 }}>{done}</div>}
      {err && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger)", marginBottom: 8 }}>{err}</div>}
      <div style={{ display: "grid", gap: 8 }}>
        {rows.map(r => (
          <div key={r.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", padding: "10px 12px", border: "1px solid var(--hf-border-subtle)", borderRadius: 8 }}>
            <div style={{ fontSize: 13, minWidth: 220 }}>
              <strong>{r.employeeName}</strong>, last day {r.endDate}
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{askedByText(r)} asked: {r.reason}</div>
            </div>
            {declining?.id === r.id ? (
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <input aria-label="Reason for declining" value={reason} onChange={e => setReason(e.target.value)} placeholder="Why is it declined?" style={{ padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 13 }} />
                <button type="button" disabled={!reason.trim() || decline.isPending} onClick={() => decline.mutate(r)}>Decline termination</button>
                <button type="button" onClick={() => { setDeclining(null); setReason("") }}>Back</button>
              </div>
            ) : (
              <div style={{ display: "flex", gap: 8 }}>
                <button type="button" disabled={approve.isPending} onClick={() => approve.mutate(r)}>Approve</button>
                <button type="button" onClick={() => { setDeclining(r); setErr("") }}>Decline</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </section>
  )
}
