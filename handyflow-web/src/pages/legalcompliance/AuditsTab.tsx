// src/pages/legalcompliance/AuditsTab.tsx
//
// Audit engagements and the findings raised inside them. Endpoints:
//   /api/v1/legalcompliance/audits            GET · /summary · /{id} · POST · PUT /{id} · POST /{id}/start|report|complete|cancel · POST /{id}/findings
//   /api/v1/legalcompliance/audit-findings    GET · POST /{id}/respond · POST /{id}/close
//
// An internal audit is blocked from starting if its lead auditor owns a control in scope (needs "I am the lead auditor" so the server knows who
// that is). Findings other than observations are closed only after every corrective action on them is verified — see the Corrective Actions tab.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

interface Audit {
  id: string; reference: string; title: string; auditType: string; scope: string; objectives: string; workProgramme: string | null; leadAuditorName: string
  auditingFirm: string | null; plannedStart: string; plannedEnd: string; status: string; overdue: boolean; controlIds: string[]; controlLabels: string[]
  overallRating: string | null; conclusion: string | null; completedAt: string | null; completedByName: string | null; cancelReason: string | null; findingCount: number; openFindingCount: number
}
interface AFinding {
  id: string; reference: string; auditId: string; controlLabel: string | null; findingType: string; title: string; description: string; rootCause: string | null
  severity: string; status: string; ownerName: string; dueDate: string; overdue: boolean; managementResponse: string | null; respondedByName: string | null
  closedByName: string | null; closedAt: string | null; closureNote: string | null; capaCount: number; capaVerified: number; closeBlocker: string | null
}
interface Summary { planned: number; inFieldwork: number; reporting: number; overdue: number; completedThisYear: number; openFindings: number; overdueFindings: number }
interface ControlOpt { id: string; reference: string; title: string }

const STATUS: Record<string, { label: string; color: string; bg: string }> = {
  PLANNED: { label: "Planned", color: "#475569", bg: "#F1F5F9" }, FIELDWORK: { label: "Fieldwork", color: "#1D4ED8", bg: "#DBEAFE" }, REPORTING: { label: "Reporting", color: "#92400E", bg: "#FEF3C7" },
  COMPLETED: { label: "Completed", color: "#166534", bg: "#DCFCE7" }, CANCELLED: { label: "Cancelled", color: "#475569", bg: "#E2E8F0" },
}
const RATING: Record<string, { label: string; color: string; bg: string }> = {
  SATISFACTORY: { label: "Satisfactory", color: "#166534", bg: "#DCFCE7" }, NEEDS_IMPROVEMENT: { label: "Needs improvement", color: "#C2410C", bg: "#FFEDD5" }, UNSATISFACTORY: { label: "Unsatisfactory", color: "#991B1B", bg: "#FEE2E2" },
}
const SEV: Record<string, { color: string; bg: string }> = { LOW: { color: "#166534", bg: "#DCFCE7" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, HIGH: { color: "#C2410C", bg: "#FFEDD5" }, CRITICAL: { color: "#991B1B", bg: "#FEE2E2" } }
const FSTATUS: Record<string, { label: string; color: string; bg: string }> = {
  OPEN: { label: "Open", color: "#991B1B", bg: "#FEE2E2" }, IN_REMEDIATION: { label: "In remediation", color: "#92400E", bg: "#FEF3C7" }, CLOSED: { label: "Closed", color: "#166534", bg: "#DCFCE7" },
}
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const base = "/api/v1/legalcompliance/audits"
const fbase = "/api/v1/legalcompliance/audit-findings"
const today = () => new Date().toISOString().slice(0, 10)
const blankAudit = { id: "", title: "", auditType: "INTERNAL", scope: "", objectives: "", workProgramme: "", leadAuditorName: "", leadIsMe: false, auditingFirm: "", plannedStart: today(), plannedEnd: today(), controlIds: [] as string[] }
const blankFinding = { controlId: "", findingType: "DEFICIENCY", title: "", description: "", rootCause: "", severity: "MEDIUM", ownerName: "", dueDate: "" }

export default function AuditsTab() {
  const qc = useQueryClient()
  const { user, fullName } = useAuth()
  const admin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canAudit = usePermission("LEGALCOMPLIANCE_AUDIT_MANAGE") || admin
  const canRespond = usePermission("LEGALCOMPLIANCE_FINDING_MANAGE") || admin
  const canClose = usePermission("LEGALCOMPLIANCE_FINDING_VERIFY") || admin

  const [expanded, setExpanded] = useState<string | null>(null)
  const [audit, setAudit] = useState<typeof blankAudit | null>(null)
  const [finding, setFinding] = useState<{ auditId: string; f: typeof blankFinding } | null>(null)
  const [complete, setComplete] = useState<{ a: Audit; rating: string; conclusion: string } | null>(null)
  const [cancel, setCancel] = useState<{ a: Audit; reason: string } | null>(null)
  const [respond, setRespond] = useState<{ f: AFinding; response: string; ownerName: string; dueDate: string } | null>(null)
  const [closing, setClosing] = useState<{ f: AFinding; note: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-audits", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: audits = [], isLoading } = useQuery<Audit[]>({ queryKey: ["lc-audits", "list"], queryFn: async () => unwrap(await apiClient.get(base)) })
  const { data: controls = [] } = useQuery<ControlOpt[]>({ queryKey: ["lc-controls-active"], enabled: !!audit || !!finding, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/controls", { params: { status: "ACTIVE" } })) })
  const { data: findings = [] } = useQuery<AFinding[]>({ queryKey: ["lc-audit-findings", expanded], enabled: !!expanded, queryFn: async () => unwrap(await apiClient.get(fbase, { params: { auditId: expanded, includeClosed: true } })) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-audits"] }); qc.invalidateQueries({ queryKey: ["lc-audit-findings"] }); qc.invalidateQueries({ queryKey: ["lc-capas"] }); setApiError("") }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (a: typeof blankAudit) => {
      const body = {
        title: a.title.trim(), auditType: a.auditType, scope: a.scope.trim(), objectives: a.objectives.trim(), workProgramme: a.workProgramme.trim() || null,
        auditorId: a.leadIsMe && a.auditType === "INTERNAL" ? user?.userId ?? null : null, leadAuditorName: a.leadIsMe ? fullName ?? a.leadAuditorName : a.leadAuditorName.trim(),
        auditingFirm: a.auditType === "EXTERNAL" ? a.auditingFirm.trim() : null, plannedStart: a.plannedStart, plannedEnd: a.plannedEnd, controlIds: a.controlIds,
      }
      return a.id ? apiClient.put(`${base}/${a.id}`, body) : apiClient.post(base, body)
    }, onSuccess: ok("Audit saved", () => setAudit(null)), onError: fail("Failed to save audit"),
  })
  const useAct = (path: string, msg: string) => useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/${path}`), onSuccess: ok(msg), onError: fail("Action failed") })
  const start = useAct("start", "Fieldwork started"), report = useAct("report", "Moved to reporting")
  const doComplete = useMutation({ mutationFn: (c: { id: string; rating: string; conclusion: string }) => apiClient.post(`${base}/${c.id}/complete`, { rating: c.rating, conclusion: c.conclusion.trim() }), onSuccess: ok("Audit completed", () => setComplete(null)), onError: fail("Failed to complete") })
  const doCancel = useMutation({ mutationFn: (c: { id: string; reason: string }) => apiClient.post(`${base}/${c.id}/cancel`, { reason: c.reason.trim() }), onSuccess: ok("Audit cancelled", () => setCancel(null)), onError: fail("Failed to cancel") })
  const raise = useMutation({
    mutationFn: ({ auditId, f }: { auditId: string; f: typeof blankFinding }) => apiClient.post(`${base}/${auditId}/findings`, {
      controlId: f.controlId || null, findingType: f.findingType, title: f.title.trim(), description: f.description.trim(), rootCause: f.rootCause.trim() || null, severity: f.severity,
      ownerName: f.ownerName.trim() || null, dueDate: f.dueDate || null,
    }), onSuccess: ok("Finding raised", () => setFinding(null)), onError: fail("Failed to raise finding"),
  })
  const doRespond = useMutation({ mutationFn: (r: { id: string; response: string; ownerName: string; dueDate: string }) => apiClient.post(`${fbase}/${r.id}/respond`, { response: r.response.trim(), ownerName: r.ownerName.trim(), dueDate: r.dueDate }), onSuccess: ok("Management response recorded", () => setRespond(null)), onError: fail("Failed to save response") })
  const doClose = useMutation({ mutationFn: (c: { id: string; note: string }) => apiClient.post(`${fbase}/${c.id}/close`, { note: c.note.trim() }), onSuccess: ok("Finding closed", () => setClosing(null)), onError: fail("Failed to close finding") })

  const openEdit = (a: Audit) => { setApiError(""); setAudit({ id: a.id, title: a.title, auditType: a.auditType, scope: a.scope, objectives: a.objectives, workProgramme: a.workProgramme ?? "", leadAuditorName: a.leadAuditorName, leadIsMe: false, auditingFirm: a.auditingFirm ?? "", plannedStart: a.plannedStart, plannedEnd: a.plannedEnd, controlIds: a.controlIds }) }
  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 120, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const setA = (k: keyof typeof blankAudit, v: any) => setAudit(a => a && { ...a, [k]: v })
  const setF = (k: keyof typeof blankFinding, v: string) => setFinding(x => x && { ...x, f: { ...x.f, [k]: v } })

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Planned", summary?.planned)}{card("In fieldwork", summary?.inFieldwork, "#1D4ED8")}{card("Reporting", summary?.reporting, "#92400E")}
        {card("Overdue", summary?.overdue, summary?.overdue ? "#991B1B" : undefined)}{card("Completed this year", summary?.completedThisYear, "#166534")}
        {card("Open findings", summary?.openFindings)}{card("Findings overdue", summary?.overdueFindings, summary?.overdueFindings ? "#991B1B" : undefined)}
      </div>
      {canAudit && <button style={{ ...primaryBtn, marginBottom: 12 }} onClick={() => { setApiError(""); setAudit({ ...blankAudit }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Plan audit</button>}
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !audit && !finding && !complete && !cancel && !respond && !closing && <Err m={apiError} />}

      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : audits.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No audits yet. Plan an internal or external audit.</p> : audits.map(a => {
        const open = expanded === a.id, st = STATUS[a.status]
        return (
          <div key={a.id} style={{ border: `1px solid ${a.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : a.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{a.reference} — {a.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{pretty(a.auditType)}{a.auditingFirm ? ` (${a.auditingFirm})` : ""} · {fmtDate(a.plannedStart)} to {fmtDate(a.plannedEnd)}{a.overdue ? " (overdue)" : ""} · lead {a.leadAuditorName} · {a.findingCount} finding(s), {a.openFindingCount} open</div>
              </div>
              {a.overallRating && <Badge text={RATING[a.overallRating].label} color={RATING[a.overallRating].color} bg={RATING[a.overallRating].bg} />}
              <Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <p style={{ marginTop: 0 }}><b>Scope:</b> {a.scope}</p><p><b>Objectives:</b> {a.objectives}</p>
                {a.workProgramme && <p><b>Work programme:</b> {a.workProgramme}</p>}
                {a.controlLabels.length > 0 && <p><b>Controls in scope:</b> {a.controlLabels.join("; ")}</p>}
                {a.conclusion && <p><b>Conclusion</b> ({a.completedByName}, {fmtDateTime(a.completedAt)}): {a.conclusion}</p>}
                {a.cancelReason && <p><b>Cancelled:</b> {a.cancelReason}</p>}
                {canAudit && (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "8px 0" }}>
                    {(a.status === "PLANNED" || a.status === "FIELDWORK") && <button style={ghostBtn} onClick={() => openEdit(a)}>Edit</button>}
                    {a.status === "PLANNED" && <button style={primaryBtn} disabled={start.isPending} onClick={() => start.mutate(a.id)}>Start fieldwork</button>}
                    {a.status === "FIELDWORK" && <button style={primaryBtn} disabled={report.isPending} onClick={() => report.mutate(a.id)}>Move to reporting</button>}
                    {a.status === "REPORTING" && <button style={primaryBtn} onClick={() => { setApiError(""); setComplete({ a, rating: "SATISFACTORY", conclusion: "" }) }}>Complete audit</button>}
                    {(a.status === "FIELDWORK" || a.status === "REPORTING") && <button style={ghostBtn} onClick={() => { setApiError(""); setFinding({ auditId: a.id, f: { ...blankFinding } }) }}>Raise finding</button>}
                    {(a.status === "PLANNED" || a.status === "FIELDWORK" || a.status === "REPORTING") && a.findingCount === 0 && <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setCancel({ a, reason: "" }) }}>Cancel</button>}
                  </div>
                )}
                {apiError && <Err m={apiError} />}
                <div style={{ fontWeight: 700, margin: "10px 0 6px" }}>Findings</div>
                {findings.length === 0 ? <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>None raised.</p> : findings.map(f => (
                  <div key={f.id} style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 10, marginBottom: 6, background: f.overdue ? "#FEF2F2" : "#F8FAFC" }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                      <b style={{ color: "#0F172A" }}>{f.reference} — {f.title}</b>
                      <Badge text={f.severity} {...SEV[f.severity]} /><Badge text={pretty(f.findingType)} color="#475569" bg="#F1F5F9" /><Badge text={FSTATUS[f.status].label} color={FSTATUS[f.status].color} bg={FSTATUS[f.status].bg} />
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B", margin: "2px 0" }}>{f.controlLabel ?? "No control linked"} · owner {f.ownerName} · due {fmtDate(f.dueDate)}{f.overdue ? " (overdue)" : ""} · actions {f.capaVerified}/{f.capaCount} verified</div>
                    <div>{f.description}</div>
                    {f.rootCause && <div><b>Root cause:</b> {f.rootCause}</div>}
                    {f.managementResponse && <div><b>Management response</b> ({f.respondedByName}): {f.managementResponse}</div>}
                    {f.closedAt && <div><b>Closed</b> by {f.closedByName} on {fmtDateTime(f.closedAt)}: {f.closureNote}</div>}
                    {f.status !== "CLOSED" && f.closeBlocker && <div style={{ fontSize: 12, color: "#92400E" }}>Cannot close yet: {f.closeBlocker}. Manage actions on the Corrective Actions tab.</div>}
                    {f.status !== "CLOSED" && (
                      <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
                        {canRespond && <button style={ghostBtn} onClick={() => { setApiError(""); setRespond({ f, response: f.managementResponse ?? "", ownerName: f.ownerName, dueDate: f.dueDate }) }}>{f.managementResponse ? "Revise response" : "Management response"}</button>}
                        {canClose && !f.closeBlocker && <button style={primaryBtn} onClick={() => { setApiError(""); setClosing({ f, note: "" }) }}>Close finding</button>}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      })}

      {audit && (
        <Modal title={audit.id ? "Edit audit" : "Plan audit"} onClose={() => setAudit(null)}>
          <Err m={apiError} />
          <Field l="Title *"><input style={input} value={audit.title} onChange={e => setA("title", e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={audit.auditType} onChange={e => setA("auditType", e.target.value)}><option value="INTERNAL">Internal</option><option value="EXTERNAL">External</option></select></Field></div>
            <div style={{ flex: 1 }}><Field l="Planned start *"><input type="date" style={input} value={audit.plannedStart} onChange={e => setA("plannedStart", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Planned end *"><input type="date" style={input} value={audit.plannedEnd} onChange={e => setA("plannedEnd", e.target.value)} /></Field></div>
          </div>
          {audit.auditType === "EXTERNAL" && <Field l="Auditing firm *"><input style={input} value={audit.auditingFirm} onChange={e => setA("auditingFirm", e.target.value)} /></Field>}
          <Field l="Lead auditor *">
            <input style={input} disabled={audit.leadIsMe} value={audit.leadIsMe ? fullName ?? "" : audit.leadAuditorName} onChange={e => setA("leadAuditorName", e.target.value)} />
            {audit.auditType === "INTERNAL" && <label style={{ fontSize: 12, color: "#475569", display: "flex", gap: 6, marginTop: 4 }}><input type="checkbox" checked={audit.leadIsMe} onChange={e => setA("leadIsMe", e.target.checked)} /> I am the lead auditor (lets the system check you do not own a control in scope)</label>}
          </Field>
          <Field l="Scope * (at least 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={audit.scope} onChange={e => setA("scope", e.target.value)} /></Field>
          <Field l="Objectives * (at least 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={audit.objectives} onChange={e => setA("objectives", e.target.value)} /></Field>
          <Field l="Work programme"><textarea style={{ ...input, minHeight: 60 }} value={audit.workProgramme} onChange={e => setA("workProgramme", e.target.value)} /></Field>
          <Field l="Controls in scope">
            <div style={{ maxHeight: 130, overflow: "auto", border: "1px solid #E2E8F0", borderRadius: 8, padding: 6 }}>
              {controls.length === 0 ? <span style={{ fontSize: 12, color: "#94A3B8" }}>No active controls.</span> : controls.map(c => (
                <label key={c.id} style={{ display: "flex", gap: 6, fontSize: 12, padding: "2px 0" }}>
                  <input type="checkbox" checked={audit.controlIds.includes(c.id)} onChange={e => setA("controlIds", e.target.checked ? [...audit.controlIds, c.id] : audit.controlIds.filter(x => x !== c.id))} /> {c.reference} — {c.title}
                </label>))}
            </div>
          </Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAudit(null)}>Cancel</button>
            <button style={primaryBtn} disabled={save.isPending || !audit.title.trim() || audit.scope.trim().length < 15 || audit.objectives.trim().length < 15 || (!audit.leadIsMe && !audit.leadAuditorName.trim()) || (audit.auditType === "EXTERNAL" && !audit.auditingFirm.trim()) || audit.plannedEnd < audit.plannedStart}
              onClick={() => save.mutate(audit)}>Save</button>
          </div>
        </Modal>
      )}

      {finding && (
        <Modal title="Raise audit finding" onClose={() => setFinding(null)}>
          <Err m={apiError} />
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={finding.f.findingType} onChange={e => setF("findingType", e.target.value)}>{["NON_CONFORMANCE", "DEFICIENCY", "BREACH", "OBSERVATION"].map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Severity *"><select style={input} value={finding.f.severity} onChange={e => setF("severity", e.target.value)}>{["LOW", "MEDIUM", "HIGH", "CRITICAL"].map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
          </div>
          <Field l="Title *"><input style={input} value={finding.f.title} onChange={e => setF("title", e.target.value)} /></Field>
          <Field l="Description * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={finding.f.description} onChange={e => setF("description", e.target.value)} /></Field>
          <Field l="Root cause"><textarea style={{ ...input, minHeight: 50 }} value={finding.f.rootCause} onChange={e => setF("rootCause", e.target.value)} /></Field>
          <Field l="Related control"><select style={input} value={finding.f.controlId} onChange={e => setF("controlId", e.target.value)}><option value="">None</option>{controls.map(c => <option key={c.id} value={c.id}>{c.reference} — {c.title}</option>)}</select></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Owner (defaults to the control's owner)"><input style={input} value={finding.f.ownerName} onChange={e => setF("ownerName", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Due date (defaults by severity)"><input type="date" style={input} value={finding.f.dueDate} onChange={e => setF("dueDate", e.target.value)} /></Field></div>
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setFinding(null)}>Cancel</button>
            <button style={primaryBtn} disabled={raise.isPending || !finding.f.title.trim() || finding.f.description.trim().length < 15 || (!finding.f.ownerName.trim() && !finding.f.controlId)} onClick={() => raise.mutate(finding)}>Raise finding</button>
          </div>
        </Modal>
      )}

      {complete && (
        <Modal title={`Complete ${complete.a.reference}`} onClose={() => setComplete(null)}>
          <Err m={apiError} />
          <Field l="Overall rating *"><select style={input} value={complete.rating} onChange={e => setComplete({ ...complete, rating: e.target.value })}>{Object.entries(RATING).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</select></Field>
          <Field l="Conclusion * (at least 30 characters)"><textarea style={{ ...input, minHeight: 90 }} value={complete.conclusion} onChange={e => setComplete({ ...complete, conclusion: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setComplete(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doComplete.isPending || complete.conclusion.trim().length < 30} onClick={() => doComplete.mutate({ id: complete.a.id, rating: complete.rating, conclusion: complete.conclusion })}>Complete audit</button>
          </div>
        </Modal>
      )}

      {cancel && (
        <Modal title={`Cancel ${cancel.a.reference}`} onClose={() => setCancel(null)}>
          <Err m={apiError} />
          <Field l="Reason * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={cancel.reason} onChange={e => setCancel({ ...cancel, reason: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setCancel(null)}>Keep audit</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={doCancel.isPending || cancel.reason.trim().length < 15} onClick={() => doCancel.mutate({ id: cancel.a.id, reason: cancel.reason })}>Cancel audit</button>
          </div>
        </Modal>
      )}

      {respond && (
        <Modal title={`Management response — ${respond.f.reference}`} onClose={() => setRespond(null)}>
          <Err m={apiError} />
          <Field l="Response * (at least 15 characters)"><textarea style={{ ...input, minHeight: 80 }} value={respond.response} onChange={e => setRespond({ ...respond, response: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Owner *"><input style={input} value={respond.ownerName} onChange={e => setRespond({ ...respond, ownerName: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Due date *"><input type="date" style={input} value={respond.dueDate} onChange={e => setRespond({ ...respond, dueDate: e.target.value })} /></Field></div>
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setRespond(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doRespond.isPending || respond.response.trim().length < 15 || !respond.ownerName.trim() || !respond.dueDate} onClick={() => doRespond.mutate({ id: respond.f.id, response: respond.response, ownerName: respond.ownerName, dueDate: respond.dueDate })}>Save response</button>
          </div>
        </Modal>
      )}

      {closing && (
        <Modal title={`Close ${closing.f.reference}`} onClose={() => setClosing(null)}>
          <Err m={apiError} />
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>You cannot close a finding you own or gave the management response for.</p>
          <Field l="Closure note * (at least 15 characters)"><textarea style={{ ...input, minHeight: 80 }} value={closing.note} onChange={e => setClosing({ ...closing, note: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setClosing(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doClose.isPending || closing.note.trim().length < 15} onClick={() => doClose.mutate({ id: closing.f.id, note: closing.note })}>Close finding</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
