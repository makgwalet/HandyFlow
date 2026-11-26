// src/pages/trainingprovider/TrainProvSlotResources.tsx
//
// The resources booked into one timetable slot. Booking is the provider's own record and does not show that anything meets a standard.
// A clash on a resource that is not shareable is refused unless an administrator approves it with a written reason. Released with a reason, never deleted.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  BOOKING_NOTE, bookBody, bookProblem, bookingState, bookingWarning, emptyBooking, overrideProblem, releaseProblem, type BookForm, type Booking, type CheckResult, type ResourceOption,
} from "./resourceBookingRules"
import { Badge, Field, Notice } from "./tpUi"
import { errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

const API = `${LRN}/resource-bookings`

function BookPanel({ slotId, siteId, onDone, onCancel }: { slotId: string; siteId: string; onDone: () => void; onCancel: () => void }) {
  const isAdmin = usePermissions(["TRAININGPROVIDER_ADMIN"])
  const { data: options = [] } = useQuery<ResourceOption[]>({
    queryKey: ["tp-bookable", siteId],
    queryFn: async () => unwrap<ResourceOption[]>(await apiClient.get(`${LRN}/site-resources`, { params: { siteId, status: "IN_SERVICE" } })),
  })
  const [f, setF] = useState<BookForm>(emptyBooking())
  const [clash, setClash] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const post = async () => { await apiClient.post(`${API}/slots/${slotId}`, bookBody(f)); onDone() }
  const save = async () => {
    const p = bookProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      if (!clash) {
        const c = unwrap<CheckResult>(await apiClient.post(`${API}/slots/${slotId}/check`, bookBody(f)))
        if (!c.ok) { setClash(c.clash ?? "That clashes with another booking"); return }
        await post()
      } else {
        const o = overrideProblem(f.overrideReason); if (o) { setError(o); return }
        await post()
      }
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
      <Field label="Resource"><select aria-label="Resource" style={input} value={f.resourceId} onChange={e => { setF({ ...f, resourceId: e.target.value }); setClash("") }}><option value="">Choose…</option>{options.map(o => <option key={o.id} value={o.id}>{o.name} · {o.resourceNo} · {o.quantity} held{o.shareable ? " · shareable" : ""}</option>)}</select></Field>
      <Field label="Quantity"><input aria-label="Quantity" type="number" min={1} style={input} value={f.quantity} onChange={e => { setF({ ...f, quantity: e.target.value }); setClash("") }} /></Field>
      {clash && (
        <div style={{ display: "grid", gap: 6 }}>
          <div role="alert" style={{ fontSize: 13, color: "var(--hf-warning-text-strong)" }}>{clash}</div>
          {isAdmin ? <Field label="Why is the clash acceptable?"><input style={input} value={f.overrideReason} onChange={e => setF({ ...f, overrideReason: e.target.value })} /></Field>
            : <div style={muted}>Only an administrator can approve a clash.</div>}
        </div>
      )}
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}>
        {(!clash || isAdmin) && <button style={solid} disabled={busy} onClick={() => void save()}>{clash ? "Book anyway" : "Book resource"}</button>}
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

function BookingRow({ b, canManage, onChanged }: { b: Booking; canManage: boolean; onChanged: () => void }) {
  const [releasing, setReleasing] = useState(false)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const warn = bookingWarning(b)
  const send = async () => {
    const p = releaseProblem(reason); if (p) { setError(p); return }
    try { await apiClient.post(`${API}/${b.id}/release`, { reason: reason.trim() }); setReleasing(false); setReason(""); onChanged() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ display: "grid", gap: 4, fontSize: 13 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <span>{b.quantity} × {b.resourceName ?? "Resource"} <span style={muted}>{b.resourceNo}</span></span>
        <Badge text={bookingState(b)} tone={b.status === "BOOKED" && b.slotStatus === "SCHEDULED" ? "good" : "neutral"} />
      </div>
      {b.overrideReason && <div style={muted}>Approved over a clash: {b.overrideReason}</div>}
      {b.releasedReason && <div style={muted}>Released: {b.releasedReason}</div>}
      {b.venueNote && <div style={muted}>{b.venueNote}</div>}
      {warn && <div role="alert" style={{ fontSize: 12, color: "var(--hf-warning-text-strong)" }}>{warn}</div>}
      {canManage && b.status === "BOOKED" && b.slotStatus === "SCHEDULED" && !releasing && <button style={{ ...ghost, justifySelf: "start" }} onClick={() => { setReleasing(true); setError("") }}>Release</button>}
      {releasing && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Why is it released?"><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => void send()}>Release booking</button><button style={ghost} onClick={() => setReleasing(false)}>Keep it</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvSlotResources({ slotId, siteId, canManage }: { slotId: string; siteId: string; canManage: boolean }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<Booking[]>({
    queryKey: ["tp-slot-resources", slotId], enabled: open,
    queryFn: async () => unwrap<Booking[]>(await apiClient.get(`${API}/slots/${slotId}`)),
  })
  const refresh = () => Promise.all([qc.invalidateQueries({ queryKey: ["tp-slot-resources", slotId] }), qc.invalidateQueries({ queryKey: ["tp-cohort-slot-needs"] })])
  if (!open) return <button style={{ ...ghost, marginTop: 8 }} onClick={() => setOpen(true)}>Resources</button>
  return (
    <div style={{ display: "grid", gap: 8, marginTop: 8, borderTop: "1px solid var(--hf-border)", paddingTop: 8 }}>
      <div style={muted}>{BOOKING_NOTE}</div>
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No resources booked for this slot.</div> : data.map(b => <BookingRow key={b.id} b={b} canManage={canManage} onChanged={() => void refresh()} />)}
      {canManage && !adding && <button style={{ ...ghost, justifySelf: "start" }} onClick={() => setAdding(true)}>Book a resource</button>}
      {adding && <BookPanel slotId={slotId} siteId={siteId} onDone={() => { setAdding(false); void refresh() }} onCancel={() => setAdding(false)} />}
      <button style={{ ...ghost, justifySelf: "start" }} onClick={() => setOpen(false)}>Hide resources</button>
    </div>
  )
}
