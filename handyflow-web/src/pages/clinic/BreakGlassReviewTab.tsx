// Reviewers of break-glass access: who opened a restricted record, why, what they looked at, and whether someone has checked it
// (CLINIC-DEC-009, patch 0189). The same people are emailed and given a task when the glass is broken.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { activity, headline, isReviewed, needsCloserLook, type BreakGlassRow } from "./breakGlassView"

const unwrap = (r: any) => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] }
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })

export default function BreakGlassReviewTab() {
  const qc = useQueryClient()
  const [onlyWaiting, setOnlyWaiting] = useState(true)
  const [open, setOpen] = useState<string | null>(null)
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const { data = [], isLoading, isError } = useQuery<BreakGlassRow[]>({
    queryKey: ["clinic-break-glass", onlyWaiting], retry: false,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/break-glass/sessions?unacknowledged=${onlyWaiting}&limit=100`)),
  })
  const ack = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/clinic/break-glass/sessions/${id}/acknowledge`, { note: note.trim() || undefined }),
    onSuccess: () => { setOpen(null); setNote(""); setError(""); qc.invalidateQueries({ queryKey: ["clinic-break-glass"] }); qc.invalidateQueries({ queryKey: ["clinic-tasks"] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not record the review."),
  })
  return (
    <div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        <div style={{ fontWeight: 700 }}>{headline(data)}</div>
        <label style={{ fontSize: 13 }}><input type="checkbox" checked={onlyWaiting} onChange={e => setOnlyWaiting(e.target.checked)} /> Only those not yet reviewed</label>
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div>}
      {isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The review list could not be loaded. You need permission to review break-glass access.</div>}
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginBottom: 8 }}>{error}</div>}
      {data.map(r => (
        <div key={r.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px", marginBottom: 8 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><b>{r.userName ?? "Unknown user"}</b> opened <b>{r.patientName ?? "an unknown patient"}</b></div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{when(r.startedAt)} (open until {when(r.expiresAt)})</div>
          </div>
          <div style={{ fontSize: 13, margin: "4px 0", whiteSpace: "pre-wrap" }}>Reason given: {r.reason}</div>
          <div style={{ fontSize: 12, color: needsCloserLook(r) ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>{activity(r)}</div>
          {isReviewed(r)
            ? <div style={{ fontSize: 12, marginTop: 4 }}>Reviewed {when(r.acknowledgedAt!)}{r.acknowledgedNote ? `: ${r.acknowledgedNote}` : ""}</div>
            : open === r.id
              ? <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
                  <input aria-label="Review note" placeholder="Note (optional): what you found" value={note} maxLength={500} onChange={e => setNote(e.target.value)} style={{ flex: 1, minWidth: 220 }} />
                  <button type="button" disabled={ack.isPending} onClick={() => ack.mutate(r.id)}>Mark as reviewed</button>
                  <button type="button" onClick={() => { setOpen(null); setNote("") }}>Cancel</button>
                </div>
              : <button type="button" style={{ marginTop: 8 }} onClick={() => { setOpen(r.id); setNote(""); setError("") }}>Review</button>}
        </div>
      ))}
      {!isLoading && !isError && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{onlyWaiting ? "Nothing is waiting for review." : "Nobody has broken the glass."}</div>}
    </div>
  )
}
