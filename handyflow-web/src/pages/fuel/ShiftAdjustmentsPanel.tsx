import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { btnGhost, btnPrimary, Chip, inputStyle, labelStyle, ReasonModal } from "../warehousing/whseUi"
import { ADJ_LABEL, ADJ_STATUS_LABEL, canDecideAdjustment, canWithdrawAdjustment, fmtR, validateAdjustment, type ShiftAdjustment } from "./forecourtOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const BASE = "/api/v1/fuel/forecourt"

/** Refunds, voids and discounts on one shift. A supervisor, not the person who recorded it, approves each; pending ones block closing. */
export default function ShiftAdjustmentsPanel({ shiftId, open }: { shiftId: string; open: boolean }) {
  const canRun = usePermissions(["FUEL_FORECOURT", "FUEL_MANAGE"])
  const canSupervise = usePermissions(["FUEL_SHIFT_SUPERVISE"])
  const me = useAuthStore(s => s.user?.userId) as string | undefined
  const qc = useQueryClient()
  const q = useQuery<ShiftAdjustment[]>({ queryKey: ["fuel-shift-adjustments", shiftId], queryFn: async () => unwrap(await apiClient.get(`${BASE}/shifts/${shiftId}/adjustments`)) })
  const [form, setForm] = useState({ kind: "REFUND", amount: "", reason: "" })
  const [err, setErr] = useState(""); const [rejecting, setRejecting] = useState<string | null>(null)
  const done = () => { setErr(""); setRejecting(null); qc.invalidateQueries({ queryKey: ["fuel-shift-adjustments", shiftId] }) }
  const record = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/shifts/${shiftId}/adjustments`, { kind: form.kind, amount: Number(form.amount), reason: form.reason.trim() }),
    onSuccess: () => { setForm({ kind: form.kind, amount: "", reason: "" }); done() }, onError: (e: any) => setErr(msg(e, "Could not record that")),
  })
  const act = useMutation({
    mutationFn: (a: { id: string; verb: "approve" | "withdraw" }) => apiClient.post(`${BASE}/adjustments/${a.id}/${a.verb}`, a.verb === "approve" ? {} : undefined),
    onSuccess: done, onError: (e: any) => setErr(msg(e, "Could not complete that")),
  })
  const reject = useMutation({ mutationFn: (a: { id: string; reason: string }) => apiClient.post(`${BASE}/adjustments/${a.id}/reject`, { reason: a.reason }), onSuccess: done })
  const list = Array.isArray(q.data) ? q.data : []
  if (list.length === 0 && !(open && canRun)) return null
  return (
    <div style={{ margin: "12px 0 0" }}>
      <p style={{ margin: "0 0 6px", fontSize: 12.5, fontWeight: 700 }}>Refunds, voids and discounts</p>
      {list.map(a => (
        <div key={a.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", flexWrap: "wrap", fontSize: 12.5 }}>
          <strong>{ADJ_LABEL[a.kind]}</strong> <span>{fmtR(a.amount)}</span>
          <Chip bg={a.status === "APPROVED" ? "var(--hf-success-soft-strong)" : a.status === "PENDING" ? "var(--hf-warning-soft)" : "var(--hf-surface-alt)"}
            fg={a.status === "APPROVED" ? "var(--hf-success-text-strong)" : a.status === "PENDING" ? "var(--hf-warning-text)" : "var(--hf-text-muted)"}>{ADJ_STATUS_LABEL[a.status]}</Chip>
          <span style={{ color: "var(--hf-text-muted)", flex: 1, minWidth: 160 }}>{a.reason}{a.decisionNote ? ` · ${a.decisionNote}` : ""}</span>
          {canSupervise && canDecideAdjustment(a, me) && <>
            <button style={btnPrimary} disabled={act.isPending} aria-label={`Approve ${ADJ_LABEL[a.kind]} ${a.id}`} onClick={() => act.mutate({ id: a.id, verb: "approve" })}>Approve</button>
            <button style={btnGhost} aria-label={`Reject ${ADJ_LABEL[a.kind]} ${a.id}`} onClick={() => setRejecting(a.id)}>Reject</button>
          </>}
          {canRun && canWithdrawAdjustment(a, me) && <button style={btnGhost} aria-label={`Withdraw ${ADJ_LABEL[a.kind]} ${a.id}`} onClick={() => act.mutate({ id: a.id, verb: "withdraw" })}>Withdraw</button>}
          {canSupervise && canWithdrawAdjustment(a, me) && <span style={{ color: "var(--hf-text-muted)" }}>You recorded this, so another supervisor must approve it.</span>}
        </div>
      ))}
      {open && canRun && (
        <div style={{ display: "grid", gridTemplateColumns: "110px 120px 1fr auto", gap: 8, alignItems: "end", marginTop: 8 }}>
          <div><label style={labelStyle} htmlFor={`adj-kind-${shiftId}`}>Type</label>
            <select id={`adj-kind-${shiftId}`} style={inputStyle} value={form.kind} onChange={e => setForm(f => ({ ...f, kind: e.target.value }))}>
              {Object.entries(ADJ_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
          <div><label style={labelStyle} htmlFor={`adj-amt-${shiftId}`}>Amount (R)</label>
            <input id={`adj-amt-${shiftId}`} type="number" min="0" step="0.01" style={inputStyle} value={form.amount} onChange={e => setForm(f => ({ ...f, amount: e.target.value }))} /></div>
          <div><label style={labelStyle} htmlFor={`adj-reason-${shiftId}`}>Reason</label>
            <input id={`adj-reason-${shiftId}`} style={inputStyle} value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} /></div>
          <button style={btnPrimary} disabled={record.isPending} onClick={() => { const v = validateAdjustment(form.amount, form.reason); if (v) return setErr(v); setErr(""); record.mutate() }}>Record</button>
        </div>
      )}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "6px 0 0" }}>{err}</p>}
      {rejecting && <ReasonModal title="Reject" confirmLabel="Reject" pending={reject.isPending} error={reject.error} onConfirm={reason => reject.mutate({ id: rejecting, reason })} onClose={() => setRejecting(null)} />}
    </div>
  )
}
