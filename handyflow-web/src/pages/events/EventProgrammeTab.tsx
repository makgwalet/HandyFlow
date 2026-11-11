// src/pages/events/EventProgrammeTab.tsx
//
// The day's programme (run sheet) for the selected event: sessions in time order with a "you are here" marker and how late the day is running; start,
// finish and cancel sessions while the event is live; and "we're running late" to move everything still to come. Reading needs EVENTS_READ; changes need
// EVENTS_OPS_MANAGE. Polls every 20 seconds while the event is live.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import {
  canCancelSession, canCompleteSession, canDeleteSession, canEditSession, canStartSession, clock, delayText, lateText, SESSION_TYPES, sessionBody, sessionProblem,
  sessionStatusText, shiftProblem, typeText, type Session,
} from "./opsView"

interface RunSheet { sessions: Session[]; current: Session | null; next: Session | null; runningLateMinutes: number }
const field: React.CSSProperties = { ...inputStyle, width: "100%" }
type Dlg = { k: "edit"; s?: Session } | { k: "shift" } | { k: "cancel"; s: Session } | null

export default function EventProgrammeTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["EVENTS_OPS_MANAGE", "EVENTS_MANAGE"])
  const [dlg, setDlg] = useState<Dlg>(null)
  const [f, setF] = useState<Record<string, string>>({})
  const set = (k: string, v: string) => setF(p => ({ ...p, [k]: v }))
  const base = `/api/v1/events/${eventId}`
  const { data: event } = useQuery<any>({ queryKey: ["event-one", eventId], enabled: !!eventId, queryFn: async () => { const r = await apiClient.get(base); return r.data?.data ?? r.data } })
  const { data: sheet, isLoading, error } = useQuery<RunSheet>({
    queryKey: ["event-run-sheet", eventId], enabled: !!eventId, refetchInterval: event?.status === "LIVE" ? 20000 : false,
    queryFn: async () => { const r = await apiClient.get(`${base}/run-sheet`); return r.data?.data ?? r.data },
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["event-run-sheet"] }); qc.invalidateQueries({ queryKey: ["event-readiness"] }); qc.invalidateQueries({ queryKey: ["event-audit"] }) }
  const act = useMutation({ mutationFn: ({ id, a }: { id: string; a: "start" | "complete" }) => apiClient.post(`${base}/sessions/${id}/${a}`), onSuccess: refresh })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/sessions/${id}`), onSuccess: refresh })
  const run = useMutation({
    mutationFn: async () => {
      if (!dlg) return
      if (dlg.k === "edit") { const body = sessionBody(f as any); return dlg.s ? apiClient.put(`${base}/sessions/${dlg.s.id}`, body) : apiClient.post(`${base}/sessions`, body) }
      if (dlg.k === "shift") return apiClient.post(`${base}/sessions/shift`, { minutes: Number(f.minutes), fromSessionId: f.from || null })
      return apiClient.post(`${base}/sessions/${dlg.s.id}/cancel`)
    },
    onSuccess: () => { refresh(); setDlg(null) },
  })
  const open = (d: Dlg, init: Record<string, string> = {}) => { setDlg(d); setF(init); run.reset() }

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  const sessions = sheet?.sessions ?? []
  const upcoming = sessions.filter(s => s.status === "SCHEDULED")

  let title = "", label = "", problem: string | null = null, body: React.ReactNode = null
  if (dlg?.k === "edit") {
    title = dlg.s ? "Edit session" : "Add session"; label = "Save session"; problem = sessionProblem({ title: f.title ?? "", sessionType: f.sessionType ?? "", startAt: f.startAt ?? "", endAt: f.endAt ?? "" })
    body = <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
      <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="ss-title">Title *</label><input id="ss-title" style={field} maxLength={200} value={f.title ?? ""} onChange={e => set("title", e.target.value)} /></div>
      <div><label style={labelStyle} htmlFor="ss-type">Type *</label><select id="ss-type" style={field} value={f.sessionType ?? ""} onChange={e => set("sessionType", e.target.value)}><option value="">Choose…</option>{SESSION_TYPES.map(t => <option key={t} value={t}>{typeText(t)}</option>)}</select></div>
      <div><label style={labelStyle} htmlFor="ss-room">Room</label><input id="ss-room" style={field} maxLength={100} value={f.room ?? ""} onChange={e => set("room", e.target.value)} /></div>
      <div><label style={labelStyle} htmlFor="ss-start">Starts *</label><input id="ss-start" type="datetime-local" style={field} value={f.startAt ?? ""} onChange={e => set("startAt", e.target.value)} /></div>
      <div><label style={labelStyle} htmlFor="ss-end">Ends *</label><input id="ss-end" type="datetime-local" style={field} value={f.endAt ?? ""} onChange={e => set("endAt", e.target.value)} /></div>
      <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="ss-speaker">Speaker or host</label><input id="ss-speaker" style={field} maxLength={200} value={f.speaker ?? ""} onChange={e => set("speaker", e.target.value)} /></div>
      <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="ss-notes">Notes</label><input id="ss-notes" style={field} maxLength={1000} value={f.notes ?? ""} onChange={e => set("notes", e.target.value)} /></div>
    </div>
  } else if (dlg?.k === "shift") {
    title = "Running late?"; label = "Move programme"; problem = shiftProblem(f.minutes ?? "")
    body = <>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Moves every session that has not started yet by the same number of minutes. It is refused if that would cause a room clash. Sessions already started or finished are never moved.</p>
      <label style={labelStyle} htmlFor="sh-min">Minutes (negative = earlier) *</label><input id="sh-min" style={field} inputMode="numeric" value={f.minutes ?? ""} onChange={e => set("minutes", e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="sh-from">From</label>
      <select id="sh-from" style={field} value={f.from ?? ""} onChange={e => set("from", e.target.value)}><option value="">Everything still to come</option>{upcoming.map(s => <option key={s.id} value={s.id}>{clock(s.startAt)} {s.title}</option>)}</select>
    </>
  } else if (dlg?.k === "cancel") {
    title = "Cancel session"; label = "Cancel session"; body = <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>{dlg.s.title} ({clock(dlg.s.startAt)}) stays on the run sheet, marked cancelled.</p>
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Programme for {eventTitle} · <strong>{lateText(sheet?.runningLateMinutes ?? 0)}</strong></p>
        {canManage && <div style={{ display: "flex", gap: 6 }}>
          <button type="button" style={btnGhost} disabled={upcoming.length === 0} onClick={() => open({ k: "shift" }, { minutes: "", from: "" })}>Running late?</button>
          <button type="button" style={btnPrimary} onClick={() => open({ k: "edit" }, { title: "", sessionType: "", startAt: event?.startDatetime?.slice(0, 16) ?? "", endAt: "", room: "", speaker: "", notes: "" })}>Add session</button>
        </div>}
      </div>
      {sheet?.current && <p role="status" style={{ ...card, padding: "8px 14px", borderLeft: "3px solid var(--hf-danger)", fontSize: 13 }}>Now: <strong>{sheet.current.title}</strong>{sheet.current.room ? ` · ${sheet.current.room}` : ""} until {clock(sheet.current.endAt)}</p>}
      {sheet?.next && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>Next: {clock(sheet.next.startAt)} {sheet.next.title}</p>}
      <ErrorLine error={error ?? act.error ?? del.error} fallback="That could not be completed" />
      {isLoading ? <p>Loading…</p> : sessions.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No sessions yet. Add the first one to build the run sheet.</p> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {sessions.map(s => (
            <div key={s.id} style={{ ...card, padding: "8px 14px", opacity: s.status === "CANCELLED" ? 0.55 : 1, borderLeft: s.status === "IN_PROGRESS" ? "3px solid var(--hf-danger)" : undefined }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700 }}>{clock(s.startAt)}–{clock(s.endAt)} · {s.title} <span style={{ fontWeight: 400, fontSize: 11.5, color: "var(--hf-text-muted)" }}>{typeText(s.sessionType)} · {sessionStatusText(s.status)}</span></div>
                  <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{[s.room, s.speaker].filter(Boolean).join(" · ")}{s.delayMinutes != null ? `${s.room || s.speaker ? " · " : ""}${delayText(s.delayMinutes)}` : ""}</div>
                </div>
                {canManage && <div style={{ display: "flex", gap: 6 }}>
                  {canStartSession(s.status, event?.status) && <button type="button" style={btnPrimary} disabled={act.isPending} onClick={() => act.mutate({ id: s.id, a: "start" })}>Start</button>}
                  {canCompleteSession(s.status) && <button type="button" style={btnPrimary} disabled={act.isPending} onClick={() => act.mutate({ id: s.id, a: "complete" })}>Finish</button>}
                  {canEditSession(s.status) && <button type="button" style={btnGhost} onClick={() => open({ k: "edit", s }, { title: s.title, sessionType: s.sessionType, startAt: s.startAt.slice(0, 16), endAt: s.endAt.slice(0, 16), room: s.room ?? "", speaker: s.speaker ?? "", notes: s.notes ?? "" })}>Edit</button>}
                  {canCancelSession(s.status) && <button type="button" style={btnGhost} onClick={() => open({ k: "cancel", s })}>Cancel</button>}
                  {canDeleteSession(s.status) && <button type="button" style={btnGhost} disabled={del.isPending} onClick={() => del.mutate(s.id)}>Delete</button>}
                </div>}
              </div>
            </div>
          ))}
        </div>
      )}
      {dlg && (
        <Modal title={title} onClose={() => setDlg(null)} width={dlg.k === "edit" ? 560 : 440}>
          {body}
          <ErrorLine error={run.error} fallback="That could not be completed" />
          {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={() => setDlg(null)}>Back</button>
            <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || run.isPending} onClick={() => run.mutate()}>{run.isPending ? "Working…" : label}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
