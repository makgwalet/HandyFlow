// src/pages/events/EventStaffTab.tsx
//
// The event-day roster: who is on shift, where, and whether they have turned up, with coverage per role. It carries phone numbers and emails, so it needs
// EVENTS_OPS_READ (or manage). Changes need EVENTS_OPS_MANAGE. The server refuses overlapping shifts for one person and says so.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { canArriveStaff, canCancelStaff, canConfirmStaff, canEditStaff, canNoShowStaff, coverageGaps, coverageText, label, staffBody, staffProblem, staffStatusText, STAFF_KINDS, STAFF_ROLES, telHref, type Coverage, type Staff } from "./peopleOps"

const field: React.CSSProperties = { ...inputStyle, width: "100%" }

export default function EventStaffTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canRead = usePermissions(["EVENTS_OPS_READ", "EVENTS_OPS_MANAGE", "EVENTS_MANAGE"])
  const canManage = usePermissions(["EVENTS_OPS_MANAGE", "EVENTS_MANAGE"])
  const [dlg, setDlg] = useState<{ s?: Staff } | null>(null)
  const [f, setF] = useState<Record<string, string>>({})
  const set = (k: string, v: string) => setF(p => ({ ...p, [k]: v }))
  const base = `/api/v1/events/${eventId}`
  const { data: staff = [], isLoading, error } = useQuery<Staff[]>({ queryKey: ["event-staff", eventId], enabled: !!eventId && canRead, queryFn: async () => { const r = await apiClient.get(`${base}/staff`); return r.data?.data ?? r.data ?? [] } })
  const { data: coverage = [] } = useQuery<Coverage[]>({ queryKey: ["event-staff-coverage", eventId], enabled: !!eventId && canRead, queryFn: async () => { const r = await apiClient.get(`${base}/staff/coverage`); return r.data?.data ?? r.data ?? [] } })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["event-staff"] }); qc.invalidateQueries({ queryKey: ["event-staff-coverage"] }); qc.invalidateQueries({ queryKey: ["event-readiness"] }) }
  const act = useMutation({ mutationFn: ({ id, a }: { id: string; a: "confirm" | "arrive" | "no-show" | "cancel" }) => apiClient.post(`${base}/staff/${id}/${a}`), onSuccess: refresh })
  const save = useMutation({
    mutationFn: () => { const b = staffBody(f); return dlg?.s ? apiClient.put(`${base}/staff/${dlg.s.id}`, b) : apiClient.post(`${base}/staff`, b) },
    onSuccess: () => { refresh(); setDlg(null) },
  })
  const open = (s?: Staff) => { setDlg({ s }); save.reset(); setF(s ? { fullName: s.fullName, kind: s.kind, role: s.role, phone: s.phone ?? "", email: s.email ?? "", post: s.post ?? "", shiftStart: s.shiftStart.slice(0, 16), shiftEnd: s.shiftEnd.slice(0, 16), notes: s.notes ?? "" } : { fullName: "", kind: "STAFF", role: "", phone: "", email: "", post: "", shiftStart: "", shiftEnd: "", notes: "" }) }

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  if (!canRead) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to the event roster. It holds staff phone numbers.</p>
  const gaps = coverageGaps(coverage)
  const problem = staffProblem({ fullName: f.fullName ?? "", kind: f.kind ?? "", role: f.role ?? "", email: f.email, shiftStart: f.shiftStart ?? "", shiftEnd: f.shiftEnd ?? "" })
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Roster for {eventTitle}</p>
        {canManage && <button type="button" style={btnPrimary} onClick={() => open()}>Add to roster</button>}
      </div>
      <ErrorLine error={error ?? act.error} fallback="That could not be completed" />
      {coverage.length > 0 && (
        <section style={{ ...card, padding: 12, marginBottom: 12 }}>
          <h3 style={{ fontSize: 14, margin: "0 0 6px" }}>Coverage</h3>
          {coverage.map(c => <div key={c.role} style={{ fontSize: 13 }}><strong>{label(c.role)}</strong>: {coverageText(c)}</div>)}
          {gaps.length > 0 && <p role="note" style={{ fontSize: 12.5, color: "var(--hf-warning-text)", margin: "8px 0 0" }}>Still to arrive: {gaps.map(g => `${g.missing} ${label(g.role).toLowerCase()}`).join(", ")}.</p>}
        </section>
      )}
      {isLoading ? <p>Loading…</p> : staff.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>Nobody is rostered yet.</p> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {staff.map(s => (
            <div key={s.id} style={{ ...card, padding: "8px 14px", opacity: s.status === "CANCELLED" ? 0.55 : 1 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700 }}>{s.fullName} <span style={{ fontWeight: 400, fontSize: 11.5, color: "var(--hf-text-muted)" }}>{label(s.role)} · {label(s.kind)} · {staffStatusText(s.status)}</span></div>
                  <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                    {s.shiftStart.slice(11, 16)}–{s.shiftEnd.slice(11, 16)}{s.post ? ` · ${s.post}` : ""}{s.phone && <> · <a href={telHref(s.phone)}>{s.phone}</a></>}{s.email ? ` · ${s.email}` : ""}
                  </div>
                </div>
                {canManage && <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  {canConfirmStaff(s.status) && <button type="button" style={btnGhost} disabled={act.isPending} onClick={() => act.mutate({ id: s.id, a: "confirm" })}>Confirm</button>}
                  {canArriveStaff(s.status) && <button type="button" style={btnPrimary} disabled={act.isPending} onClick={() => act.mutate({ id: s.id, a: "arrive" })}>Arrived</button>}
                  {canNoShowStaff(s.status) && <button type="button" style={btnGhost} disabled={act.isPending} onClick={() => act.mutate({ id: s.id, a: "no-show" })}>No-show</button>}
                  {canEditStaff(s.status) && <button type="button" style={btnGhost} onClick={() => open(s)}>Edit</button>}
                  {canCancelStaff(s.status) && <button type="button" style={btnGhost} disabled={act.isPending} onClick={() => act.mutate({ id: s.id, a: "cancel" })}>Cancel shift</button>}
                </div>}
              </div>
            </div>
          ))}
        </div>
      )}
      {dlg && (
        <Modal title={dlg.s ? "Edit shift" : "Add to roster"} onClose={() => setDlg(null)} width={520}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="st-name">Full name *</label><input id="st-name" style={field} maxLength={200} value={f.fullName ?? ""} onChange={e => set("fullName", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="st-kind">Kind *</label><select id="st-kind" style={field} value={f.kind ?? ""} onChange={e => set("kind", e.target.value)}>{STAFF_KINDS.map(k => <option key={k} value={k}>{label(k)}</option>)}</select></div>
            <div><label style={labelStyle} htmlFor="st-role">Role *</label><select id="st-role" style={field} value={f.role ?? ""} onChange={e => set("role", e.target.value)}><option value="">Choose…</option>{STAFF_ROLES.map(r => <option key={r} value={r}>{label(r)}</option>)}</select></div>
            <div><label style={labelStyle} htmlFor="st-phone">Phone</label><input id="st-phone" style={field} maxLength={50} value={f.phone ?? ""} onChange={e => set("phone", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="st-email">Email</label><input id="st-email" style={field} maxLength={254} value={f.email ?? ""} onChange={e => set("email", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="st-from">Shift starts *</label><input id="st-from" type="datetime-local" style={field} value={f.shiftStart ?? ""} onChange={e => set("shiftStart", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="st-to">Shift ends *</label><input id="st-to" type="datetime-local" style={field} value={f.shiftEnd ?? ""} onChange={e => set("shiftEnd", e.target.value)} /></div>
            <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="st-post">Post (where)</label><input id="st-post" style={field} maxLength={200} value={f.post ?? ""} onChange={e => set("post", e.target.value)} /></div>
            <div style={{ gridColumn: "1/-1" }}><label style={labelStyle} htmlFor="st-notes">Notes</label><input id="st-notes" style={field} maxLength={500} value={f.notes ?? ""} onChange={e => set("notes", e.target.value)} /></div>
          </div>
          <ErrorLine error={save.error} fallback="The shift could not be saved" />
          {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={() => setDlg(null)}>Back</button>
            <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Working…" : "Save shift"}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
