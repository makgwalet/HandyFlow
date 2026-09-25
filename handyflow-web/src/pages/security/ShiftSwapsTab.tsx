// src/pages/security/ShiftSwapsTab.tsx
//
// FIX (P1 backlog): confirmed via direct source read before building —
// ShiftSwapController has a full two-stage swap workflow (guard
// requests -> proposed guard accepts -> supervisor approves/rejects)
// with zero frontend surface anywhere. Scoped to the supervisor-facing
// half only (list pending, approve, reject) - the controller's own doc
// comment explicitly notes createSwapRequest/acceptSwap are
// guard-initiated, mixed-caller-type endpoints in the same shape as
// CheckpointScanController, and that whether guards can even reach
// this tenant-JWT surface from their own mobile app is a separate, open
// question already flagged elsewhere — not something to build a second,
// duplicate admin-side "create a swap" form around.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Repeat, CheckCircle2, XCircle, Clock3, AlertCircle } from "lucide-react"

interface ShiftSwap {
  id: string; originalShiftId: string; shiftSummary: string
  requestingGuardId: string; requestingGuardName: string
  proposedGuardId: string | null; proposedGuardName: string | null
  status: "PENDING" | "PROPOSED_ACCEPTED" | "APPROVED" | "REJECTED" | "CANCELLED"
  proposedAcceptedAt: string | null; requestedAt: string
  decidedBy: string | null; decidedByName: string | null; decidedAt: string | null
  reason: string | null; rejectionReason: string | null
  validationPassed: boolean | null; validationNotes: string | null
  createdAt: string
}

const STATUS_CFG: Record<string, { label: string; color: string; bg: string }> = {
  PENDING:            { label: "Awaiting Acceptance", color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft)" },
  PROPOSED_ACCEPTED:  { label: "Ready for Approval",   color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
  APPROVED:           { label: "Approved",             color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  REJECTED:           { label: "Rejected",             color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  CANCELLED:          { label: "Cancelled",            color: "var(--hf-text-faint)", bg: "var(--hf-surface-muted)" },
}

const fmtDateTime = (s: string | null) => s ? new Date(s).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"

export default function ShiftSwapsTab() {
  const qc = useQueryClient()
  const [error, setError] = useState("")
  const [rejectFor, setRejectFor] = useState<ShiftSwap | null>(null)
  const [rejectReason, setRejectReason] = useState("")

  const { data: swaps, isLoading } = useQuery<{ content: ShiftSwap[] }>({
    queryKey: ["shift-swaps-pending"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/shifts/swaps?size=100")
      return (r.data?.data ?? r.data) as { content: ShiftSwap[] }
    },
  })

  const approve = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/shifts/swaps/${id}/approve`, {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["shift-swaps-pending"] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to approve swap"),
  })
  const reject = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/shifts/swaps/${rejectFor!.id}/reject`, { reason: rejectReason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["shift-swaps-pending"] }); setRejectFor(null); setRejectReason(""); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to reject swap"),
  })

  const list = swaps?.content ?? []

  return (
    <div>
      <div style={{ marginBottom: 18 }}>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>Shift Swap Requests</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 2 }}>Requests awaiting a proposed guard's acceptance or your approval</div>
      </div>

      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
      ) : list.length === 0 ? (
        <div style={{ textAlign: "center", padding: "48px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <Repeat size={28} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 10px", opacity: 0.4 }} />
          No open swap requests.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {list.map(s => {
            const sc = STATUS_CFG[s.status] ?? STATUS_CFG.PENDING
            const canDecide = s.status === "PROPOSED_ACCEPTED" && !!s.proposedGuardId
            return (
              <div key={s.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 16px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{s.shiftSummary}</span>
                      <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 8px", borderRadius: 20, background: sc.bg, color: sc.color }}>{sc.label}</span>
                    </div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                      <strong>{s.requestingGuardName}</strong> wants to swap
                      {s.proposedGuardName ? <> with <strong>{s.proposedGuardName}</strong></> : " — open request, no guard proposed yet"}
                    </div>
                    {s.reason && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>"{s.reason}"</div>}
                    <div style={{ fontSize: 11, color: "var(--hf-text-disabled)", marginTop: 6 }}>
                      Requested {fmtDateTime(s.requestedAt)}
                      {s.proposedAcceptedAt && <> · Accepted {fmtDateTime(s.proposedAcceptedAt)}</>}
                    </div>
                    {s.validationNotes && (
                      <div style={{ display: "flex", alignItems: "flex-start", gap: 6, marginTop: 8, padding: "6px 10px", background: s.validationPassed === false ? "var(--hf-danger-soft)" : "var(--hf-warning-soft)", border: `1px solid ${s.validationPassed === false ? "var(--hf-danger-border)" : "var(--hf-warning-border)"}`, borderRadius: 7 }}>
                        <AlertCircle size={13} style={{ color: s.validationPassed === false ? "var(--hf-danger-text)" : "var(--hf-warning-text-strong)", marginTop: 1, flexShrink: 0 }} />
                        <span style={{ fontSize: 11, color: s.validationPassed === false ? "var(--hf-danger-text)" : "var(--hf-warning-text-strong)" }}>{s.validationNotes}</span>
                      </div>
                    )}
                  </div>
                  {canDecide && (
                    <div style={{ display: "flex", gap: 8, flexShrink: 0 }}>
                      <button onClick={() => approve.mutate(s.id)} disabled={approve.isPending}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <CheckCircle2 size={13} /> Approve
                      </button>
                      <button onClick={() => { setRejectReason(""); setRejectFor(s) }}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <XCircle size={13} /> Reject
                      </button>
                    </div>
                  )}
                  {s.status === "PENDING" && (
                    <div style={{ display: "flex", alignItems: "center", gap: 5, color: "var(--hf-text-faint)", fontSize: 12, flexShrink: 0 }}>
                      <Clock3 size={13} /> Waiting on guard
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {rejectFor && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 26, width: 420 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Reject Swap — {rejectFor.shiftSummary}</h3>
            <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }}>Reason</label>
            <textarea value={rejectReason} onChange={e => setRejectReason(e.target.value)} rows={3}
              style={{ width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, resize: "vertical" as const, fontFamily: "inherit", outline: "none" }} />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 18 }}>
              <button onClick={() => setRejectFor(null)} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button onClick={() => reject.mutate()} disabled={reject.isPending}
                style={{ padding: "9px 18px", border: "none", borderRadius: 9, background: "var(--hf-danger)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
                {reject.isPending ? "Rejecting…" : "Reject Swap"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
