// C04: office dialogs to set a delivery window and to record a failed delivery attempt.
import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Modal, btnPrimary, btnGhost, inputStyle, labelStyle } from "../warehousing/whseUi"
import { FAILURE_REASONS, validateFailure } from "./transportOps"

const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null
/** datetime-local value (local time) to an ISO instant, or null when blank. */
const toIso = (v: string) => v ? new Date(v).toISOString() : null
const toLocalInput = (iso?: string | null) => { if (!iso) return ""; const d = new Date(iso); const p = (n: number) => String(n).padStart(2, "0"); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}` }

export function WindowDialog({ deliveryId, start, end, onDone, onClose }: { deliveryId: string; start?: string | null; end?: string | null; onDone: () => void; onClose: () => void }) {
  const [from, setFrom] = useState(toLocalInput(start)); const [to, setTo] = useState(toLocalInput(end)); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/fuel/deliveries/${deliveryId}/window`, { windowStart: toIso(from), windowEnd: toIso(to) }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the window")),
  })
  return (
    <Modal title="Delivery window" onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>When the customer can receive the fuel. Leave a box empty for no limit.</p>
      <label style={labelStyle}>From</label><input aria-label="Window from" type="datetime-local" value={from} onChange={e => setFrom(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>To</label><input aria-label="Window to" type="datetime-local" value={to} onChange={e => setTo(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (from && to && new Date(to) <= new Date(from)) return setErr("The window must end after it starts."); setErr(""); save.mutate() }}>Save window</button>
      </div>
    </Modal>
  )
}

export function FailedAttemptDialog({ deliveryId, onDone, onClose }: { deliveryId: string; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [note, setNote] = useState(""); const [litres, setLitres] = useState(""); const [again, setAgain] = useState(""); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/deliveries/${deliveryId}/failed-attempt`, {
      reasonCode: reason, note: note.trim() || null, litresReturned: litres.trim() ? Number(litres) : null, rescheduleTo: toIso(again),
    }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not record the failed attempt")),
  })
  return (
    <Modal title="Record a failed delivery" onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>The load goes back to Scheduled to be tried again. Nothing is invoiced and no stock is taken.</p>
      <label style={labelStyle}>Reason *</label>
      <select aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle}>
        <option value="">Choose…</option>{FAILURE_REASONS.map(r => <option key={r.code} value={r.code}>{r.label}</option>)}
      </select>
      <label style={{ ...labelStyle, marginTop: 10 }}>Note</label><input aria-label="Note" value={note} onChange={e => setNote(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Litres brought back to the depot</label><input aria-label="Litres returned" type="number" value={litres} onChange={e => setLitres(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Try again at</label><input aria-label="Try again at" type="datetime-local" value={again} onChange={e => setAgain(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateFailure(reason, note, litres); if (v) return setErr(v); setErr(""); save.mutate() }}>Record failed delivery</button>
      </div>
    </Modal>
  )
}
