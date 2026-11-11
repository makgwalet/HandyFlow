import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { btnGhost, btnPrimary, card, Chip, ReasonModal } from "../warehousing/whseUi"
import { canDecide, canWithdraw, type AdjustmentRequest } from "./wholesaleOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb

/** Large stock adjustments waiting for a second person. Shows nothing when there are none. */
export default function AdjustmentRequestsPanel() {
  const canApprove = usePermissions(["FUEL_STOCK_ADJUST_APPROVE"])
  const canRaise = usePermissions(["FUEL_STOCK_ADJUST"])
  const me = useAuthStore(s => s.user?.userId) as string | undefined
  const qc = useQueryClient()
  const q = useQuery<AdjustmentRequest[]>({ queryKey: ["fuel-adjustment-requests"], enabled: canApprove || canRaise, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/adjustment-requests")) })
  const [rejecting, setRejecting] = useState<string | null>(null)
  const [err, setErr] = useState("")
  const done = () => { setErr(""); setRejecting(null); ["fuel-adjustment-requests", "fuel-availability", "fuel-ledger", "tanks"].forEach(k => qc.invalidateQueries({ queryKey: [k] })) }
  const act = useMutation({ mutationFn: (a: { id: string; verb: "approve" | "withdraw" }) => apiClient.post(`/api/v1/fuel/adjustment-requests/${a.id}/${a.verb}`), onSuccess: done, onError: (e: any) => setErr(msg(e, "Could not complete that")) })
  const reject = useMutation({ mutationFn: (a: { id: string; reason: string }) => apiClient.post(`/api/v1/fuel/adjustment-requests/${a.id}/reject`, { reason: a.reason }), onSuccess: done })
  const pending = (Array.isArray(q.data) ? q.data : []).filter(r => r.status === "PENDING")
  if (pending.length === 0) return null
  return (
    <div style={{ ...card, padding: 14, marginBottom: 14 }}>
      <h3 style={{ margin: "0 0 8px", fontSize: 14 }}>Adjustments waiting for a second person</h3>
      {pending.map(r => (
        <div key={r.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 0", borderTop: "1px solid var(--hf-border)", flexWrap: "wrap" }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            <strong style={{ fontSize: 13 }}>{r.tankName}</strong> <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">{r.litres > 0 ? "+" : ""}{r.litres.toLocaleString("en-ZA")} L</Chip>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{r.reason}</div>
          </div>
          {canApprove && canDecide(r, me) && <>
            <button style={btnPrimary} disabled={act.isPending} onClick={() => act.mutate({ id: r.id, verb: "approve" })} aria-label={`Approve adjustment for ${r.tankName}`}>Approve</button>
            <button style={btnGhost} onClick={() => setRejecting(r.id)} aria-label={`Reject adjustment for ${r.tankName}`}>Reject</button>
          </>}
          {canRaise && canWithdraw(r, me) && <button style={btnGhost} onClick={() => act.mutate({ id: r.id, verb: "withdraw" })} aria-label={`Withdraw adjustment for ${r.tankName}`}>Withdraw</button>}
          {canApprove && !canDecide(r, me) && !canWithdraw(r, me) && null}
          {canApprove && canWithdraw(r, me) && <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>You raised this, so someone else must approve it.</span>}
        </div>
      ))}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {rejecting && <ReasonModal title="Reject adjustment" confirmLabel="Reject" pending={reject.isPending} error={reject.error} onConfirm={reason => reject.mutate({ id: rejecting, reason })} onClose={() => setRejecting(null)} />}
    </div>
  )
}
