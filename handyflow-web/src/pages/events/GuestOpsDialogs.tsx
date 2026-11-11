// src/pages/events/GuestOpsDialogs.tsx
//
// Forms for one-person guest operations: transfer a ticket to someone else, and add someone to the waitlist.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { emptyPerson, personBody, transferProblem, waitlistProblem, type Person } from "./guestOps"

const field: React.CSSProperties = { ...inputStyle, width: "100%" }

export function PersonFields({ p, set, idPrefix }: { p: Person; set: (k: keyof Person, v: string) => void; idPrefix: string }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
      <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor={`${idPrefix}-name`}>Full name *</label>
        <input id={`${idPrefix}-name`} style={field} maxLength={200} value={p.fullName} onChange={e => set("fullName", e.target.value)} /></div>
      <div><label style={labelStyle} htmlFor={`${idPrefix}-email`}>Email</label>
        <input id={`${idPrefix}-email`} style={field} maxLength={254} value={p.email} onChange={e => set("email", e.target.value)} /></div>
      <div><label style={labelStyle} htmlFor={`${idPrefix}-phone`}>Phone</label>
        <input id={`${idPrefix}-phone`} style={field} maxLength={50} value={p.phone} onChange={e => set("phone", e.target.value)} /></div>
      <div><label style={labelStyle} htmlFor={`${idPrefix}-company`}>Company</label>
        <input id={`${idPrefix}-company`} style={field} maxLength={200} value={p.company} onChange={e => set("company", e.target.value)} /></div>
      <div><label style={labelStyle} htmlFor={`${idPrefix}-diet`}>Dietary requirements</label>
        <input id={`${idPrefix}-diet`} style={field} maxLength={500} value={p.dietaryRequirements} onChange={e => set("dietaryRequirements", e.target.value)} /></div>
    </div>
  )
}

function Actions({ problem, pending, label, onClose, onSubmit }: { problem: string | null; pending: boolean; label: string; onClose: () => void; onSubmit: () => void }) {
  return (
    <>
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button type="button" onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
        <button type="button" onClick={onSubmit} disabled={!!problem || pending} style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }}>{pending ? "Working…" : label}</button>
      </div>
    </>
  )
}

export function TransferModal({ eventId, guest, onClose }: { eventId: string; guest: { id: string; fullName: string; ticketNumber?: string }; onClose: () => void }) {
  const qc = useQueryClient()
  const [p, setP] = useState<Person>(emptyPerson())
  const [reason, setReason] = useState("")
  const go = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/events/${eventId}/guests/${guest.id}/transfer`, { ...personBody(p), reason: reason.trim() || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-guests"] }); qc.invalidateQueries({ queryKey: ["event-stats", eventId] }); qc.invalidateQueries({ queryKey: ["event-audit"] }); onClose() },
  })
  return (
    <Modal title={`Transfer ${guest.fullName}'s ticket`} onClose={onClose} width={520}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>The new holder gets a new ticket and QR code by email. The old QR code stops working straight away.</p>
      <PersonFields p={p} set={(k, v) => setP(x => ({ ...x, [k]: v }))} idPrefix="tr" />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="tr-reason">Reason</label>
      <input id="tr-reason" style={field} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
      <ErrorLine error={go.error} fallback="The ticket could not be transferred" />
      <Actions problem={transferProblem(p, reason)} pending={go.isPending} label="Transfer ticket" onClose={onClose} onSubmit={() => go.mutate()} />
    </Modal>
  )
}

export function WaitlistJoinModal({ eventId, tiers, onClose }: { eventId: string; tiers: { id: string; name: string }[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [p, setP] = useState<Person>(emptyPerson())
  const [tierId, setTierId] = useState("")
  const go = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/events/${eventId}/waitlist`, { ...personBody(p), tierId: tierId || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-waitlist"] }); onClose() },
  })
  return (
    <Modal title="Add to the waitlist" onClose={onClose} width={520}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>Only possible while the event or ticket type is full. Their ticket is emailed when a place opens, so an email is needed.</p>
      <PersonFields p={p} set={(k, v) => setP(x => ({ ...x, [k]: v }))} idPrefix="wl" />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="wl-tier">Waiting for</label>
      <select id="wl-tier" style={field} value={tierId} onChange={e => setTierId(e.target.value)}>
        <option value="">Any place at the event</option>
        {tiers.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
      </select>
      <ErrorLine error={go.error} fallback="They could not be added to the waitlist" />
      <Actions problem={waitlistProblem(p)} pending={go.isPending} label="Add to waitlist" onClose={onClose} onSubmit={() => go.mutate()} />
    </Modal>
  )
}
