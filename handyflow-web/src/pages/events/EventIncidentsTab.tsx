// src/pages/events/EventIncidentsTab.tsx
//
// Incidents on the day and the emergency contacts. Anyone who may report (EVENTS_INCIDENT_REPORT, check-in staff, ops) can report an incident even
// without being able to read the list; reading the list and the contacts needs EVENTS_OPS_READ; handling (take, assign, escalate, resolve, close, reopen)
// needs EVENTS_OPS_MANAGE. Nothing pushes an alert, so overdue incidents are shown at the top in red with what is wrong. Refreshes every 20 seconds.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import {
  canAcknowledge, canAssign, canClose, canEscalate, canReopen, canResolve, CONTACT_TYPES, contactBody, contactProblem, higherSeverities, INCIDENT_CATEGORIES, incidentBody,
  incidentProblem, incidentStatusText, label, minutesText, overdueText, SEVERITIES, severityTone, sortIncidents, telHref, type Contact, type Incident,
} from "./peopleOps"

const field: React.CSSProperties = { ...inputStyle, width: "100%" }
const TONE = { bad: "var(--hf-danger-text)", warn: "var(--hf-warning-text)", muted: "var(--hf-text-muted)" } as const
interface Summary { total: number; unresolved: number; resolved: number; overdue: number; unresolvedBySeverity: Record<string, number> }
type Dlg = { k: "report" } | { k: "assign" | "escalate" | "resolve" | "reopen" | "note"; i: Incident } | { k: "contact"; c?: Contact } | null

export default function EventIncidentsTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canRead = usePermissions(["EVENTS_OPS_READ", "EVENTS_OPS_MANAGE", "EVENTS_MANAGE"])
  const canManage = usePermissions(["EVENTS_OPS_MANAGE", "EVENTS_MANAGE"])
  const canReport = usePermissions(["EVENTS_INCIDENT_REPORT", "EVENTS_OPS_MANAGE", "EVENTS_CHECKIN", "EVENTS_MANAGE"])
  const [active, setActive] = useState(true)
  const [openId, setOpenId] = useState<string | null>(null)
  const [dlg, setDlg] = useState<Dlg>(null)
  const [f, setF] = useState<Record<string, string>>({})
  const set = (k: string, v: string) => setF(p => ({ ...p, [k]: v }))
  const base = `/api/v1/events/${eventId}`
  const { data: summary } = useQuery<Summary>({ queryKey: ["event-incident-summary", eventId], enabled: !!eventId && canRead, refetchInterval: 20000, queryFn: async () => { const r = await apiClient.get(`${base}/incidents/summary`); return r.data?.data ?? r.data } })
  const { data: list, error } = useQuery<{ content: Incident[] }>({ queryKey: ["event-incidents", eventId, active], enabled: !!eventId && canRead, refetchInterval: 20000, queryFn: async () => { const r = await apiClient.get(`${base}/incidents`, { params: { activeOnly: active, size: 100 } }); return r.data?.data ?? r.data } })
  const { data: detail } = useQuery<Incident>({ queryKey: ["event-incident", eventId, openId], enabled: !!eventId && canRead && !!openId, queryFn: async () => { const r = await apiClient.get(`${base}/incidents/${openId}`); return r.data?.data ?? r.data } })
  const { data: contacts = [] } = useQuery<Contact[]>({ queryKey: ["event-contacts", eventId], enabled: !!eventId && canRead, queryFn: async () => { const r = await apiClient.get(`${base}/contacts`); return r.data?.data ?? r.data ?? [] } })
  const refresh = () => ["event-incident-summary", "event-incidents", "event-incident", "event-contacts", "event-readiness"].forEach(k => qc.invalidateQueries({ queryKey: [k] }))
  const ack = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/incidents/${id}/acknowledge`), onSuccess: refresh })
  const close = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/incidents/${id}/close`), onSuccess: refresh })
  const delContact = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/contacts/${id}`), onSuccess: refresh })
  const run = useMutation({
    mutationFn: async () => {
      if (!dlg) return
      if (dlg.k === "report") return apiClient.post(`${base}/incidents`, incidentBody(f))
      if (dlg.k === "contact") { const b = contactBody(f); return dlg.c ? apiClient.put(`${base}/contacts/${dlg.c.id}`, b) : apiClient.post(`${base}/contacts`, b) }
      const u = `${base}/incidents/${dlg.i.id}`
      if (dlg.k === "assign") return apiClient.post(`${u}/assign`, { assigneeName: f.name.trim(), assigneeUserId: null })
      if (dlg.k === "escalate") return apiClient.post(`${u}/escalate`, { reason: f.reason.trim(), raiseSeverityTo: f.severity || null })
      if (dlg.k === "resolve") return apiClient.post(`${u}/resolve`, { resolution: f.resolution.trim() })
      if (dlg.k === "reopen") return apiClient.post(`${u}/reopen`, { reason: f.reason.trim() })
      return apiClient.post(`${u}/notes`, { note: f.note.trim() })
    },
    onSuccess: () => { refresh(); setDlg(null) },
  })
  const open = (d: Dlg, init: Record<string, string> = {}) => { setDlg(d); setF(init); run.reset() }

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  if (!canRead && !canReport) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to incidents for events.</p>

  let title = "", lab = "", problem: string | null = null, body: React.ReactNode = null, width = 440
  const L = (id: string, t: string) => <label style={{ ...labelStyle, marginTop: 10 }} htmlFor={id}>{t}</label>
  if (dlg?.k === "report") {
    title = "Report an incident"; lab = "Report incident"; width = 500; problem = incidentProblem({ title: f.title ?? "", category: f.category ?? "", severity: f.severity ?? "" })
    body = <>
      <label style={labelStyle} htmlFor="in-title">What happened *</label><input id="in-title" style={field} maxLength={200} value={f.title ?? ""} onChange={e => set("title", e.target.value)} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div>{L("in-cat", "Category *")}<select id="in-cat" style={field} value={f.category ?? ""} onChange={e => set("category", e.target.value)}><option value="">Choose…</option>{INCIDENT_CATEGORIES.map(c => <option key={c} value={c}>{label(c)}</option>)}</select></div>
        <div>{L("in-sev", "How serious *")}<select id="in-sev" style={field} value={f.severity ?? ""} onChange={e => set("severity", e.target.value)}><option value="">Choose…</option>{SEVERITIES.map(s => <option key={s} value={s}>{label(s)}</option>)}</select></div>
      </div>
      {L("in-loc", "Where")}<input id="in-loc" style={field} maxLength={200} value={f.location ?? ""} onChange={e => set("location", e.target.value)} />
      {L("in-desc", "Details")}<textarea id="in-desc" style={{ ...field, minHeight: 60 }} maxLength={2000} value={f.description ?? ""} onChange={e => set("description", e.target.value)} />
      {(f.severity === "HIGH" || f.severity === "CRITICAL" || f.category === "MEDICAL") && <p role="note" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "10px 0 0" }}>If anyone is in danger, call the emergency contacts below first, then report it here.</p>}
    </>
  } else if (dlg?.k === "assign") {
    title = "Assign incident"; lab = "Assign"; problem = (f.name ?? "").trim() ? null : "Enter who is taking it."
    body = <>{L("as-name", "Assign to *")}<input id="as-name" style={field} maxLength={200} value={f.name ?? ""} onChange={e => set("name", e.target.value)} /></>
  } else if (dlg?.k === "escalate") {
    title = "Escalate incident"; lab = "Escalate"; problem = (f.reason ?? "").trim() ? null : "Give the reason for escalating."
    body = <>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Moves it up one level (now level {dlg.i.escalationLevel}, the highest is 3).</p>
      {L("es-why", "Reason *")}<input id="es-why" style={field} maxLength={500} value={f.reason ?? ""} onChange={e => set("reason", e.target.value)} />
      {higherSeverities(dlg.i.severity).length > 0 && <>{L("es-sev", "Also raise severity to")}<select id="es-sev" style={field} value={f.severity ?? ""} onChange={e => set("severity", e.target.value)}><option value="">Keep {label(dlg.i.severity)}</option>{higherSeverities(dlg.i.severity).map(s => <option key={s} value={s}>{label(s)}</option>)}</select></>}
    </>
  } else if (dlg?.k === "resolve") {
    title = "Resolve incident"; lab = "Mark resolved"; problem = (f.resolution ?? "").trim() ? null : "Say how it was resolved."
    body = <>{L("rs-how", "How was it resolved? *")}<textarea id="rs-how" style={{ ...field, minHeight: 60 }} maxLength={1000} value={f.resolution ?? ""} onChange={e => set("resolution", e.target.value)} /></>
  } else if (dlg?.k === "reopen") {
    title = "Reopen incident"; lab = "Reopen"; problem = (f.reason ?? "").trim() ? null : "Say why it is being reopened."
    body = <>{L("ro-why", "Reason *")}<input id="ro-why" style={field} maxLength={500} value={f.reason ?? ""} onChange={e => set("reason", e.target.value)} /></>
  } else if (dlg?.k === "note") {
    title = "Add a note"; lab = "Add note"; problem = (f.note ?? "").trim() ? null : "Write the note."
    body = <>{L("nt-note", "Note *")}<textarea id="nt-note" style={{ ...field, minHeight: 60 }} maxLength={2000} value={f.note ?? ""} onChange={e => set("note", e.target.value)} /></>
  } else if (dlg?.k === "contact") {
    title = dlg.c ? "Edit contact" : "Add emergency contact"; lab = "Save contact"; width = 500; problem = contactProblem({ contactType: f.contactType ?? "", name: f.name ?? "", phone: f.phone ?? "", email: f.email })
    body = <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
      <div>{L("ct-type", "Type *")}<select id="ct-type" style={field} value={f.contactType ?? ""} onChange={e => set("contactType", e.target.value)}><option value="">Choose…</option>{CONTACT_TYPES.map(c => <option key={c} value={c}>{label(c)}</option>)}</select></div>
      <div>{L("ct-name", "Name *")}<input id="ct-name" style={field} maxLength={200} value={f.name ?? ""} onChange={e => set("name", e.target.value)} /></div>
      <div>{L("ct-org", "Organisation")}<input id="ct-org" style={field} maxLength={200} value={f.organisation ?? ""} onChange={e => set("organisation", e.target.value)} /></div>
      <div>{L("ct-phone", "Phone *")}<input id="ct-phone" style={field} maxLength={50} value={f.phone ?? ""} onChange={e => set("phone", e.target.value)} /></div>
      <div>{L("ct-alt", "Alternate phone")}<input id="ct-alt" style={field} maxLength={50} value={f.alternatePhone ?? ""} onChange={e => set("alternatePhone", e.target.value)} /></div>
      <div>{L("ct-email", "Email")}<input id="ct-email" style={field} maxLength={254} value={f.email ?? ""} onChange={e => set("email", e.target.value)} /></div>
    </div>
  }

  const incidents = sortIncidents(list?.content ?? [])
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Incidents for {eventTitle}</p>
        <div style={{ display: "flex", gap: 6 }}>
          {canRead && <select aria-label="Show" style={inputStyle} value={active ? "active" : "all"} onChange={e => setActive(e.target.value === "active")}><option value="active">Unresolved only</option><option value="all">All incidents</option></select>}
          {canReport && <button type="button" style={btnPrimary} onClick={() => open({ k: "report" }, { title: "", category: "", severity: "", location: "", description: "" })}>Report incident</button>}
        </div>
      </div>
      {!canRead && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>You can report incidents. The operations team sees and handles them.</p>}

      {canRead && contacts.length > 0 && (
        <section style={{ ...card, padding: 12, marginBottom: 12 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
            <h3 style={{ fontSize: 14, margin: 0 }}>Emergency contacts</h3>
            {canManage && <button type="button" style={btnGhost} onClick={() => open({ k: "contact" }, { contactType: "", name: "", organisation: "", phone: "", alternatePhone: "", email: "" })}>Add contact</button>}
          </div>
          {contacts.map(c => (
            <div key={c.id} style={{ fontSize: 13, display: "flex", justifyContent: "space-between", gap: 8, padding: "2px 0" }}>
              <span><strong>{label(c.contactType)}</strong> · {c.name}{c.organisation ? ` (${c.organisation})` : ""} · <a href={telHref(c.phone)}>{c.phone}</a>{c.alternatePhone ? <> / <a href={telHref(c.alternatePhone)}>{c.alternatePhone}</a></> : ""}</span>
              {canManage && <span style={{ display: "flex", gap: 4 }}>
                <button type="button" style={btnGhost} onClick={() => open({ k: "contact", c }, { contactType: c.contactType, name: c.name, organisation: c.organisation ?? "", phone: c.phone, alternatePhone: c.alternatePhone ?? "", email: c.email ?? "" })}>Edit</button>
                <button type="button" style={btnGhost} disabled={delContact.isPending} onClick={() => delContact.mutate(c.id)}>Remove</button></span>}
            </div>
          ))}
        </section>
      )}
      {canRead && contacts.length === 0 && canManage && <p style={{ fontSize: 12.5, color: "var(--hf-warning-text)" }}>No emergency contacts yet. <button type="button" style={btnGhost} onClick={() => open({ k: "contact" }, { contactType: "", name: "", organisation: "", phone: "", alternatePhone: "", email: "" })}>Add contact</button></p>}

      {canRead && summary && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
          {[["Unresolved", summary.unresolved], ["Overdue", summary.overdue], ["Resolved", summary.resolved], ["Total", summary.total]].map(([k, v]) => <div key={k as string} style={{ ...card, padding: "8px 14px", minWidth: 100 }}><div style={{ fontSize: 20, fontWeight: 800, color: k === "Overdue" && (v as number) > 0 ? TONE.bad : undefined }}>{v}</div><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{k}</div></div>)}
        </div>
      )}
      <ErrorLine error={error ?? ack.error ?? close.error ?? delContact.error} fallback="That could not be completed" />
      {canRead && (incidents.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No incidents{active ? " waiting on anyone" : ""}.</p> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {incidents.map(i => {
            const od = overdueText(i); const tl = openId === i.id ? detail?.timeline ?? [] : []
            return (
              <div key={i.id} style={{ ...card, padding: "10px 14px", borderLeft: `3px solid ${od ? "var(--hf-danger)" : "var(--hf-border)"}` }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 700 }}>{i.reference} · {i.title} <span style={{ fontWeight: 700, fontSize: 11.5, color: TONE[severityTone(i.severity)] }}>{label(i.severity)}</span></div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{label(i.category)} · {incidentStatusText(i.status)}{i.escalationLevel > 0 ? ` (level ${i.escalationLevel})` : ""}{i.location ? ` · ${i.location}` : ""} · open {minutesText(i.minutesOpen)}{i.assignedToName ? ` · with ${i.assignedToName}` : ""}</div>
                    {od && <div role="alert" style={{ fontSize: 12.5, fontWeight: 700, color: "var(--hf-danger-text)" }}>{od}</div>}
                    {i.resolution && <div style={{ fontSize: 12 }}>Resolution: {i.resolution}</div>}
                  </div>
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    <button type="button" style={btnGhost} onClick={() => setOpenId(openId === i.id ? null : i.id)}>{openId === i.id ? "Hide history" : "History"}</button>
                    {canReport && <button type="button" style={btnGhost} onClick={() => open({ k: "note", i }, { note: "" })}>Add note</button>}
                    {canManage && canAcknowledge(i) && <button type="button" style={btnPrimary} disabled={ack.isPending} onClick={() => ack.mutate(i.id)}>Take it</button>}
                    {canManage && canAssign(i.status) && <button type="button" style={btnGhost} onClick={() => open({ k: "assign", i }, { name: "" })}>Assign</button>}
                    {canManage && canEscalate(i) && <button type="button" style={btnGhost} onClick={() => open({ k: "escalate", i }, { reason: "", severity: "" })}>Escalate</button>}
                    {canManage && canResolve(i.status) && <button type="button" style={btnPrimary} onClick={() => open({ k: "resolve", i }, { resolution: "" })}>Resolve</button>}
                    {canManage && canClose(i.status) && <button type="button" style={btnGhost} disabled={close.isPending} onClick={() => close.mutate(i.id)}>Close</button>}
                    {canManage && canReopen(i.status) && <button type="button" style={btnGhost} onClick={() => open({ k: "reopen", i }, { reason: "" })}>Reopen</button>}
                  </div>
                </div>
                {openId === i.id && <div style={{ marginTop: 8, paddingTop: 8, borderTop: "1px dashed var(--hf-border)", fontSize: 12.5 }}>
                  {i.description && <p style={{ margin: "0 0 6px" }}>{i.description}</p>}
                  {tl.length === 0 ? <span style={{ color: "var(--hf-text-muted)" }}>No history yet.</span> : tl.map(t => <div key={t.id}>{new Date(t.createdAt).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })} · {t.authorName ?? "System"}: {t.note}</div>)}
                </div>}
              </div>
            )
          })}
        </div>
      ))}
      {dlg && (
        <Modal title={title} onClose={() => setDlg(null)} width={width}>
          {body}
          <ErrorLine error={run.error} fallback="That could not be completed" />
          {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={() => setDlg(null)}>Back</button>
            <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || run.isPending} onClick={() => run.mutate()}>{run.isPending ? "Working…" : lab}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
