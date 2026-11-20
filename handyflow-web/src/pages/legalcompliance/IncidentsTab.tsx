// src/pages/legalcompliance/IncidentsTab.tsx
//
// Compliance incidents and breaches. Endpoints (/api/v1/legalcompliance/incidents):
//   GET / · /summary · POST / (anyone signed in) · PUT /{id} · POST /{id}/investigate|resolve|assess-notification|regulator-notified|subjects-notified|defer-subjects|close
//
// The system records the notification decision; it does not make it and does not impose a statutory deadline. An incident involving personal
// information cannot close until the decision is recorded, and, if notification is required, until the Regulator and data subjects (or a reason)
// are recorded. The investigator cannot close their own incident. Corrective actions are managed on the Corrective Actions tab.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge, RowLimitNotice } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

interface Incident {
  id: string; reference: string; incidentType: string; title: string; description: string; severity: string; status: string; occurredAt: string | null; discoveredAt: string
  reportedByName: string | null; controlId: string | null; controlLabel: string | null; personalInfoInvolved: boolean; dataSubjectsAffected: number | null; dataCategories: string | null
  investigatorName: string | null; containmentActions: string | null; rootCause: string | null; resolutionNote: string | null; notificationAssessedAt: string | null
  notificationAssessedBy: string | null; notificationRequired: boolean | null; notificationRationale: string | null; regulatorNotifiedAt: string | null; regulatorReference: string | null
  subjectsNotifiedAt: string | null; subjectsNotNotifiedReason: string | null; closedByName: string | null; closedAt: string | null; closureNote: string | null
  assessmentOverdue: boolean; capaCount: number; capaVerified: number; closeBlocker: string | null
}
interface Summary { open: number; critical: number; high: number; awaitingAssessment: number; awaitingNotification: number; resolved: number }
interface ControlOpt { id: string; reference: string; title: string }

const SEV: Record<string, { color: string; bg: string }> = { LOW: { color: "#166534", bg: "#DCFCE7" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, HIGH: { color: "#C2410C", bg: "#FFEDD5" }, CRITICAL: { color: "#991B1B", bg: "#FEE2E2" } }
const STATUS: Record<string, { label: string; color: string; bg: string }> = {
  REPORTED: { label: "Reported", color: "#991B1B", bg: "#FEE2E2" }, INVESTIGATING: { label: "Investigating", color: "#92400E", bg: "#FEF3C7" }, RESOLVED: { label: "Resolved", color: "#1D4ED8", bg: "#DBEAFE" }, CLOSED: { label: "Closed", color: "#166534", bg: "#DCFCE7" },
}
const TYPES = ["PRIVACY_BREACH", "SECURITY", "REGULATORY", "OPERATIONAL", "ETHICAL"]
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const base = "/api/v1/legalcompliance/incidents"
const nowLocal = () => { const d = new Date(); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }
const iso = (local: string) => new Date(local).toISOString()
const toLocal = (instant: string | null) => { if (!instant) return ""; const d = new Date(instant); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }
const blank = { id: "", incidentType: "PRIVACY_BREACH", title: "", description: "", severity: "MEDIUM", occurredAt: "", discoveredAt: "", personalInfoInvolved: false, dataSubjectsAffected: "", dataCategories: "", controlId: "" }
type Form = typeof blank
type Act = { i: Incident; kind: "resolve" | "assess" | "regulator" | "subjects" | "defer" | "close"; a: string; b: string; c: string; flag: boolean; when: string }

export default function IncidentsTab() {
  const qc = useQueryClient()
  const { user, fullName } = useAuth()
  const hasManage = usePermission("LEGALCOMPLIANCE_INCIDENT_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin

  const [includeClosed, setIncludeClosed] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<Form | null>(null)
  const [act, setAct] = useState<Act | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-incidents", "summary"], enabled: canManage, queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: incidents = [], isLoading } = useQuery<Incident[]>({ queryKey: ["lc-incidents", "list", includeClosed], enabled: canManage, queryFn: async () => unwrap(await apiClient.get(base, { params: { includeClosed } })) })
  const { data: controls = [] } = useQuery<ControlOpt[]>({ queryKey: ["lc-controls-all"], enabled: !!form, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/controls")) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-incidents"] }); qc.invalidateQueries({ queryKey: ["lc-capas"] }); setApiError("") }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: Form) => {
      const body = {
        incidentType: f.incidentType, title: f.title.trim(), description: f.description.trim(), severity: f.severity, occurredAt: f.occurredAt ? iso(f.occurredAt) : null, discoveredAt: iso(f.discoveredAt),
        personalInfoInvolved: f.personalInfoInvolved, dataSubjectsAffected: f.personalInfoInvolved && f.dataSubjectsAffected ? +f.dataSubjectsAffected : null,
        dataCategories: f.personalInfoInvolved ? f.dataCategories.trim() || null : null, controlId: f.controlId || null,
      }
      return f.id ? apiClient.put(`${base}/${f.id}`, body) : apiClient.post(base, body)
    }, onSuccess: ok("Incident saved", () => setForm(null)), onError: fail("Failed to save incident"),
  })
  const investigate = useMutation({
    mutationFn: (id: string) => apiClient.post(`${base}/${id}/investigate`, { investigatorId: user?.userId ?? null, investigatorName: fullName ?? "Me" }),
    onSuccess: ok("You are now the investigator"), onError: fail("Failed to start investigation"),
  })
  const run = useMutation({
    mutationFn: (a: Act) => {
      const id = a.i.id
      switch (a.kind) {
        case "resolve": return apiClient.post(`${base}/${id}/resolve`, { containmentActions: a.a.trim(), rootCause: a.b.trim(), resolutionNote: a.c.trim() })
        case "assess": return apiClient.post(`${base}/${id}/assess-notification`, { required: a.flag, rationale: a.a.trim() })
        case "regulator": return apiClient.post(`${base}/${id}/regulator-notified`, { notifiedAt: iso(a.when), reference: a.a.trim() || null })
        case "subjects": return apiClient.post(`${base}/${id}/subjects-notified`, { notifiedAt: iso(a.when) })
        case "defer": return apiClient.post(`${base}/${id}/defer-subjects`, { reason: a.a.trim() })
        case "close": return apiClient.post(`${base}/${id}/close`, { note: a.a.trim() })
      }
    }, onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed"),
  })

  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const set = (k: keyof Form, v: any) => setForm(f => f && { ...f, [k]: v })
  const openAct = (i: Incident, kind: Act["kind"]) => { setApiError(""); setAct({ i, kind, a: kind === "assess" ? i.notificationRationale ?? "" : "", b: i.rootCause ?? "", c: "", flag: i.notificationRequired ?? true, when: nowLocal() }) }
  const titles: Record<Act["kind"], string> = { resolve: "Resolve incident", assess: "Notification assessment", regulator: "Regulator notified", subjects: "Data subjects notified", defer: "Data subjects not notified", close: "Close incident" }
  const valid = (a: Act) => a.kind === "resolve" ? a.a.trim().length >= 15 && a.b.trim().length >= 15 && a.c.trim().length >= 15 : a.kind === "regulator" || a.kind === "subjects" ? !!a.when : a.a.trim().length >= 15

  return (
    <div>
      {canManage && (
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
          {card("Open incidents", summary?.open)}{card("Critical", summary?.critical, "#991B1B")}{card("High", summary?.high, "#C2410C")}
          {card("Awaiting assessment", summary?.awaitingAssessment, summary?.awaitingAssessment ? "#991B1B" : undefined)}{card("Awaiting notification", summary?.awaitingNotification, "#92400E")}{card("Resolved, not closed", summary?.resolved, "#1D4ED8")}
        </div>
      )}
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
        <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blank, discoveredAt: nowLocal() }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Report incident</button>
        {canManage && <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6 }}><input type="checkbox" checked={includeClosed} onChange={e => setIncludeClosed(e.target.checked)} /> Show closed</label>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !form && !act && <Err m={apiError} />}
      {!canManage && <p style={{ fontSize: 13, color: "#64748B" }}>Anyone can report an incident. Investigating and closing incidents is limited to the people responsible for them.</p>}

      <RowLimitNotice count={incidents.length} />
      {canManage && (isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : incidents.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No incidents.</p> : incidents.map(i => {
        const open = expanded === i.id
        return (
          <div key={i.id} style={{ border: `1px solid ${i.assessmentOverdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : i.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{i.reference} — {i.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{pretty(i.incidentType)} · discovered {fmtDateTime(i.discoveredAt)}{i.investigatorName ? ` · investigator ${i.investigatorName}` : ""}{i.personalInfoInvolved ? " · personal information" : ""}</div>
              </div>
              {i.assessmentOverdue && <Badge text="Assessment overdue" color="#991B1B" bg="#FEE2E2" />}
              <Badge text={i.severity} {...SEV[i.severity]} /><Badge text={STATUS[i.status].label} color={STATUS[i.status].color} bg={STATUS[i.status].bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <p style={{ marginTop: 0 }}>{i.description}</p>
                <p style={{ fontSize: 12 }}>Reported by {i.reportedByName ?? "—"}{i.occurredAt ? ` · occurred ${fmtDateTime(i.occurredAt)}` : ""}{i.controlLabel ? ` · control ${i.controlLabel}` : ""}{i.personalInfoInvolved ? ` · ${i.dataSubjectsAffected ?? "unknown number of"} data subject(s)${i.dataCategories ? `: ${i.dataCategories}` : ""}` : ""}</p>
                {i.containmentActions && <p><b>Containment:</b> {i.containmentActions}</p>}
                {i.rootCause && <p><b>Root cause:</b> {i.rootCause}</p>}
                {i.resolutionNote && <p><b>Resolution:</b> {i.resolutionNote}</p>}
                {i.personalInfoInvolved && (
                  <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, padding: 10, marginBottom: 8 }}>
                    <div style={{ fontWeight: 700, marginBottom: 4 }}>Notification duties</div>
                    {i.notificationAssessedAt ? (
                      <>
                        <div>{i.notificationRequired ? "Notification required" : "Notification not required"} — assessed by {i.notificationAssessedBy} on {fmtDateTime(i.notificationAssessedAt)}: {i.notificationRationale}</div>
                        {i.notificationRequired && <div>Regulator: {i.regulatorNotifiedAt ? `notified ${fmtDateTime(i.regulatorNotifiedAt)}${i.regulatorReference ? ` (ref ${i.regulatorReference})` : ""}` : "not yet recorded"}</div>}
                        {i.notificationRequired && <div>Data subjects: {i.subjectsNotifiedAt ? `notified ${fmtDateTime(i.subjectsNotifiedAt)}` : i.subjectsNotNotifiedReason ? `not notified — ${i.subjectsNotNotifiedReason}` : "not yet recorded"}</div>}
                      </>
                    ) : <div style={{ color: "#991B1B" }}>No assessment yet. The system does not decide whether notification is required; the person assessing does.</div>}
                  </div>
                )}
                {i.closedAt && <p><b>Closed</b> by {i.closedByName} on {fmtDateTime(i.closedAt)}: {i.closureNote}</p>}
                <p style={{ fontSize: 12 }}>Corrective actions: {i.capaVerified}/{i.capaCount} verified (manage them on the Corrective Actions tab).</p>
                {i.status !== "CLOSED" && i.closeBlocker && <p style={{ fontSize: 12, color: "#92400E" }}>Cannot close yet: {i.closeBlocker}</p>}
                {apiError && <Err m={apiError} />}
                {i.status !== "CLOSED" && (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: i.id, incidentType: i.incidentType, title: i.title, description: i.description, severity: i.severity, occurredAt: toLocal(i.occurredAt), discoveredAt: toLocal(i.discoveredAt), personalInfoInvolved: i.personalInfoInvolved, dataSubjectsAffected: i.dataSubjectsAffected?.toString() ?? "", dataCategories: i.dataCategories ?? "", controlId: i.controlId ?? "" }) }}>Edit details</button>
                    {i.status === "REPORTED" && <button style={primaryBtn} disabled={investigate.isPending} onClick={() => investigate.mutate(i.id)}>I will investigate</button>}
                    {i.status === "INVESTIGATING" && <button style={primaryBtn} onClick={() => openAct(i, "resolve")}>Resolve</button>}
                    {i.personalInfoInvolved && <button style={ghostBtn} onClick={() => openAct(i, "assess")}>{i.notificationAssessedAt ? "Revise assessment" : "Assess notification"}</button>}
                    {i.notificationRequired && <button style={ghostBtn} onClick={() => openAct(i, "regulator")}>Regulator notified</button>}
                    {i.notificationRequired && <button style={ghostBtn} onClick={() => openAct(i, "subjects")}>Data subjects notified</button>}
                    {i.notificationRequired && <button style={ghostBtn} onClick={() => openAct(i, "defer")}>Not notifying data subjects</button>}
                    {i.status === "RESOLVED" && !i.closeBlocker && <button style={primaryBtn} onClick={() => openAct(i, "close")}>Close</button>}
                  </div>
                )}
              </div>
            )}
          </div>
        )
      }))}

      {form && (
        <Modal title={form.id ? "Edit incident" : "Report incident"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={form.incidentType} onChange={e => set("incidentType", e.target.value)}>{TYPES.map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Severity *"><select style={input} value={form.severity} onChange={e => set("severity", e.target.value)}>{["LOW", "MEDIUM", "HIGH", "CRITICAL"].map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
          </div>
          <Field l="Title *"><input style={input} value={form.title} onChange={e => set("title", e.target.value)} /></Field>
          <Field l="What happened * (at least 15 characters)"><textarea style={{ ...input, minHeight: 80 }} value={form.description} onChange={e => set("description", e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="When it occurred (if known)"><input type="datetime-local" style={input} value={form.occurredAt} onChange={e => set("occurredAt", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="When it was discovered *"><input type="datetime-local" style={input} value={form.discoveredAt} onChange={e => set("discoveredAt", e.target.value)} /></Field></div>
          </div>
          <label style={{ fontSize: 13, display: "flex", gap: 6, marginBottom: 8 }}><input type="checkbox" checked={form.personalInfoInvolved} onChange={e => set("personalInfoInvolved", e.target.checked)} /> Personal information may be involved</label>
          {form.personalInfoInvolved && (
            <div style={{ display: "flex", gap: 12 }}>
              <div style={{ flex: 1 }}><Field l="Data subjects affected"><input type="number" min={0} style={input} value={form.dataSubjectsAffected} onChange={e => set("dataSubjectsAffected", e.target.value)} /></Field></div>
              <div style={{ flex: 2 }}><Field l="Kinds of information"><input style={input} value={form.dataCategories} onChange={e => set("dataCategories", e.target.value)} /></Field></div>
            </div>
          )}
          <Field l="Related control (optional)"><select style={input} value={form.controlId} onChange={e => set("controlId", e.target.value)}><option value="">None</option>{controls.map(c => <option key={c.id} value={c.id}>{c.reference} — {c.title}</option>)}</select></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={save.isPending || !form.title.trim() || form.description.trim().length < 15 || !form.discoveredAt} onClick={() => save.mutate(form)}>{form.id ? "Save" : "Report"}</button>
          </div>
        </Modal>
      )}

      {act && (
        <Modal title={`${titles[act.kind]} — ${act.i.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.kind === "resolve" && (<>
            <Field l="Containment actions * (at least 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
            <Field l="Root cause * (at least 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={act.b} onChange={e => setAct({ ...act, b: e.target.value })} /></Field>
            <Field l="Resolution * (at least 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={act.c} onChange={e => setAct({ ...act, c: e.target.value })} /></Field>
          </>)}
          {act.kind === "assess" && (<>
            <Field l="Decision *"><select style={input} value={act.flag ? "yes" : "no"} onChange={e => setAct({ ...act, flag: e.target.value === "yes" })}><option value="yes">Notification is required</option><option value="no">Notification is not required</option></select></Field>
            <Field l="Rationale * (at least 15 characters)"><textarea style={{ ...input, minHeight: 80 }} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
          </>)}
          {(act.kind === "regulator" || act.kind === "subjects") && <Field l="When *"><input type="datetime-local" style={input} value={act.when} onChange={e => setAct({ ...act, when: e.target.value })} /></Field>}
          {act.kind === "regulator" && <Field l="Regulator's reference"><input style={input} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>}
          {(act.kind === "defer" || act.kind === "close") && <Field l={act.kind === "defer" ? "Reason * (at least 15 characters)" : "Closure note * (at least 15 characters)"}><textarea style={{ ...input, minHeight: 80 }} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>}
          {act.kind === "close" && <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>You cannot close an incident you investigated.</p>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button>
            <button style={primaryBtn} disabled={run.isPending || !valid(act)} onClick={() => run.mutate(act)}>{titles[act.kind]}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
