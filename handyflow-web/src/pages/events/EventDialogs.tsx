// src/pages/events/EventDialogs.tsx
//
// The dialogs for working on one event: edit its details, postpone it, copy it and cancel it. They replace the old inline form and confirm box; every
// failure shows the server's own message in the dialog instead of an alert().
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { canEdit, eventBody, eventFormProblem, formFromEvent, postponeProblem, shiftEnd, toApiValue, toInputValue, type EventForm } from "./eventActions"

export const EVENT_TYPES = ["CONFERENCE", "WEDDING", "CHURCH", "FESTIVAL", "CORPORATE", "COMMUNITY", "FUNDRAISER", "GENERAL"]

function useRefresh() {
  const qc = useQueryClient()
  return () => { qc.invalidateQueries({ queryKey: ["events"] }); qc.invalidateQueries({ queryKey: ["events-kpi"] }); qc.invalidateQueries({ queryKey: ["event-audit"] }) }
}

function Footer({ problem, pending, label, onClose, onSubmit, danger }: { problem: string | null; pending: boolean; label: string; onClose: () => void; onSubmit: () => void; danger?: boolean }) {
  return (
    <>
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button type="button" onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
        <button type="button" onClick={onSubmit} disabled={!!problem || pending}
          style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1, ...(danger ? { background: "var(--hf-danger)" } : {}) }}>
          {pending ? "Working…" : label}
        </button>
      </div>
    </>
  )
}

const field: React.CSSProperties = { ...inputStyle, width: "100%" }

/** Creates a new event (no `event`) or edits an existing one. Free/paid cannot flip once guests have registered; the server says so if tried. */
export function EventFormModal({ event, onClose }: { event?: any; onClose: () => void }) {
  const refresh = useRefresh()
  const [f, setF] = useState<EventForm>(event ? formFromEvent(event) : {
    title: "", description: "", eventType: "GENERAL", venueName: "", venueAddress: "", venueCapacity: "", startDatetime: "", endDatetime: "",
    isFree: true, isPrivate: false, registrationDeadline: "", notes: "",
  })
  const set = <K extends keyof EventForm>(k: K, v: EventForm[K]) => setF(p => ({ ...p, [k]: v }))
  const live = event?.status === "LIVE"
  const save = useMutation({
    mutationFn: () => event ? apiClient.put(`/api/v1/events/${event.id}`, eventBody(f)) : apiClient.post("/api/v1/events", eventBody(f)),
    onSuccess: () => { refresh(); onClose() },
  })
  const problem = eventFormProblem(f)
  return (
    <Modal title={event ? "Edit event" : "New event"} onClose={onClose} width={640}>
      {event && !canEdit(event.status) && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>A {event.status.toLowerCase()} event can no longer be edited.</p>}
      {live && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>This event is live, so its start time and registration deadline are locked.</p>}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="ev-title">Event title *</label>
          <input id="ev-title" style={field} value={f.title} maxLength={300} onChange={e => set("title", e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="ev-type">Event type</label>
          <select id="ev-type" style={field} value={f.eventType} onChange={e => set("eventType", e.target.value)}>{EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="ev-cap">Venue capacity (empty = no limit)</label>
          <input id="ev-cap" style={field} inputMode="numeric" value={f.venueCapacity} onChange={e => set("venueCapacity", e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="ev-venue">Venue name</label>
          <input id="ev-venue" style={field} value={f.venueName} maxLength={300} onChange={e => set("venueName", e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="ev-addr">Venue address</label>
          <input id="ev-addr" style={field} value={f.venueAddress} maxLength={2000} onChange={e => set("venueAddress", e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="ev-start">Starts *</label>
          <input id="ev-start" type="datetime-local" style={field} value={f.startDatetime} disabled={live} onChange={e => set("startDatetime", e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="ev-end">Ends *</label>
          <input id="ev-end" type="datetime-local" style={field} value={f.endDatetime} onChange={e => set("endDatetime", e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="ev-deadline">Registration closes</label>
          <input id="ev-deadline" type="datetime-local" style={field} value={f.registrationDeadline} disabled={live} onChange={e => set("registrationDeadline", e.target.value)} /></div>
        <div style={{ display: "flex", gap: 16, alignItems: "center", paddingTop: 20, fontSize: 13 }}>
          <label><input type="checkbox" checked={f.isFree} onChange={e => set("isFree", e.target.checked)} /> Free event</label>
          <label><input type="checkbox" checked={f.isPrivate} onChange={e => set("isPrivate", e.target.checked)} /> Private</label>
        </div>
        <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="ev-desc">Description</label>
          <textarea id="ev-desc" style={{ ...field, minHeight: 60 }} value={f.description} onChange={e => set("description", e.target.value)} /></div>
        <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="ev-notes">Internal notes</label>
          <textarea id="ev-notes" style={{ ...field, minHeight: 44 }} value={f.notes} onChange={e => set("notes", e.target.value)} /></div>
      </div>
      <ErrorLine error={save.error} fallback="The event could not be saved" />
      <Footer problem={problem} pending={save.isPending} label={event ? "Save changes" : "Create event"} onClose={onClose} onSubmit={() => save.mutate()} />
    </Modal>
  )
}

export function PostponeModal({ event, onClose }: { event: any; onClose: () => void }) {
  const refresh = useRefresh()
  const cur = { start: toInputValue(event.startDatetime), end: toInputValue(event.endDatetime) }
  const [start, setStart] = useState(cur.start)
  const [end, setEnd] = useState(cur.end)
  const [reason, setReason] = useState("")
  const [notify, setNotify] = useState(true)
  const go = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/events/${event.id}/postpone`, { newStartDatetime: toApiValue(start), newEndDatetime: toApiValue(end), reason: reason.trim(), notifyGuests: notify }),
    onSuccess: () => { refresh(); onClose() },
  })
  return (
    <Modal title={`Postpone "${event.title}"`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>Registrations and tickets stay valid. Ticket sale windows are not moved.</p>
      <label style={labelStyle} htmlFor="pp-start">New start *</label>
      <input id="pp-start" type="datetime-local" style={field} value={start} onChange={e => { setEnd(shiftEnd(start, end, e.target.value)); setStart(e.target.value) }} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="pp-end">New end *</label>
      <input id="pp-end" type="datetime-local" style={field} value={end} onChange={e => setEnd(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="pp-reason">Reason *</label>
      <textarea id="pp-reason" style={{ ...field, minHeight: 60 }} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
      <label style={{ display: "block", fontSize: 13, marginTop: 10 }}><input type="checkbox" checked={notify} onChange={e => setNotify(e.target.checked)} /> Email registered guests about the new date</label>
      <ErrorLine error={go.error} fallback="The event could not be postponed" />
      <Footer problem={postponeProblem(start, end, reason, cur)} pending={go.isPending} label="Postpone event" onClose={onClose} onSubmit={() => go.mutate()} />
    </Modal>
  )
}

export function DuplicateModal({ event, onClose }: { event: any; onClose: () => void }) {
  const refresh = useRefresh()
  const [start, setStart] = useState("")
  const [title, setTitle] = useState(`Copy of ${event.title}`.slice(0, 200))
  const go = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/events/${event.id}/duplicate`, { newStartDatetime: toApiValue(start), title: title.trim() || null }),
    onSuccess: () => { refresh(); onClose() },
  })
  return (
    <Modal title={`Copy "${event.title}"`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>Makes a new draft with the same details and ticket tiers. Guests, vendors and check-ins are not copied.</p>
      <label style={labelStyle} htmlFor="dup-title">Title</label>
      <input id="dup-title" style={field} maxLength={200} value={title} onChange={e => setTitle(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="dup-start">New start *</label>
      <input id="dup-start" type="datetime-local" style={field} value={start} onChange={e => setStart(e.target.value)} />
      <ErrorLine error={go.error} fallback="The event could not be copied" />
      <Footer problem={start ? null : "Pick the new start; the end, registration deadline and ticket sale dates move with it."} pending={go.isPending} label="Create copy" onClose={onClose} onSubmit={() => go.mutate()} />
    </Modal>
  )
}

export function CancelEventModal({ event, onClose }: { event: any; onClose: () => void }) {
  const refresh = useRefresh()
  const [reason, setReason] = useState("")
  const [notify, setNotify] = useState(true)
  const go = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/events/${event.id}/cancel`, { reason: reason.trim(), notifyGuests: notify }),
    onSuccess: () => { refresh(); onClose() },
  })
  return (
    <Modal title={`Cancel "${event.title}"?`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>
        Registration closes, the waitlist is closed and any open checkouts are cancelled. Money already paid is not returned automatically; handle refunds from Payments. This cannot be undone.
      </p>
      <label style={labelStyle} htmlFor="ce-reason">Reason *</label>
      <textarea id="ce-reason" style={{ ...field, minHeight: 60 }} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
      <label style={{ display: "block", fontSize: 13, marginTop: 10 }}><input type="checkbox" checked={notify} onChange={e => setNotify(e.target.checked)} /> Email registered guests</label>
      <ErrorLine error={go.error} fallback="The event could not be cancelled" />
      <Footer problem={reason.trim().length >= 5 ? null : "Say why (at least 5 characters); it is kept in the audit trail."} pending={go.isPending} label="Cancel event" danger onClose={onClose} onSubmit={() => go.mutate()} />
    </Modal>
  )
}
