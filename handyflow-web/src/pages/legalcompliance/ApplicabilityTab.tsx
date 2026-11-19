// src/pages/legalcompliance/ApplicabilityTab.tsx
//
// "Which requirements apply to us, and why?" — the organisation profile, the assessor's worklist, and the path from an
// APPLICABLE decision to an assigned obligation. Endpoints (ApplicabilityController, /api/v1/legalcompliance/applicability):
//   GET/PUT /profile · GET /worklist?filter= · GET /requirements/{id}/history
//   POST /assessments · POST /assessments/{id}/approve · POST /assessments/{id}/reject
//   POST /requirements/{id}/generate-obligation
//
// The UI never auto-assigns anything. The suggestion badge is a hint with its reasons; a person records a justified decision.
// APPLICABLE takes effect at once; NOT_APPLICABLE waits for someone with LEGALCOMPLIANCE_APPLICABILITY_APPROVE (or ADMIN).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ListChecks, ChevronDown, ChevronUp, Building2, History } from "lucide-react"

interface Requirement {
  id: string; reference: string; title: string; requirementText: string; category: string; recurrence: string
  minEmployees: number | null; industries: string[]; activities: string[]; evidenceExpectation: string | null
}
interface Assessment {
  id: string; requirementId: string; decision: string; justification: string; status: string; suggestion: string | null
  suggestionReasons: string[]; assessedByName: string | null; assessedAt: string; decidedByName: string | null
  decidedAt: string | null; decisionNote: string | null; supersededAt: string | null
}
interface Row {
  requirement: Requirement; instrumentTitle: string; instrumentIdentifier: string; versionLabel: string
  suggestion: string; suggestionReasons: string[]; state: string; assessment: Assessment | null; obligationId: string | null
}
interface Profile { industries: string[]; activities: string[]; employeeCount: number | null; entityType: string | null; updatedAt: string | null }

const FILTERS: [string, string][] = [["ALL", "All"], ["UNASSESSED", "Unassessed"], ["PENDING", "Awaiting approval"], ["APPLICABLE", "Applicable"], ["NOT_APPLICABLE", "Not applicable"]]
const SUGGESTION_CFG: Record<string, { label: string; color: string; bg: string }> = {
  LIKELY_APPLICABLE: { label: "Likely applicable", color: "#166534", bg: "#DCFCE7" },
  LIKELY_NOT_APPLICABLE: { label: "Likely not applicable", color: "#475569", bg: "#F1F5F9" },
  NEEDS_REVIEW: { label: "Needs review", color: "#92400E", bg: "#FEF3C7" },
}
const STATE_CFG: Record<string, { label: string; color: string; bg: string }> = {
  UNASSESSED: { label: "Unassessed", color: "#92400E", bg: "#FEF3C7" },
  PENDING: { label: "Awaiting approval", color: "#1D4ED8", bg: "#DBEAFE" },
  APPLICABLE: { label: "Applicable", color: "#166534", bg: "#DCFCE7" },
  NOT_APPLICABLE: { label: "Not applicable", color: "#475569", bg: "#E2E8F0" },
}
const ASSESS_STATUS: Record<string, string> = { PENDING_APPROVAL: "Awaiting approval", APPROVED: "Approved", REJECTED: "Rejected", SUPERSEDED: "Superseded" }
const RECURRENCES = ["ONCE", "MONTHLY", "QUARTERLY", "ANNUALLY"]
const csv = (s: string) => s.split(",").map(x => x.trim()).filter(Boolean)
const base = "/api/v1/legalcompliance/applicability"

export default function ApplicabilityTab() {
  const qc = useQueryClient()
  const hasAssess = usePermission("LEGALCOMPLIANCE_APPLICABILITY_ASSESS")
  const hasApprove = usePermission("LEGALCOMPLIANCE_APPLICABILITY_APPROVE")
  const hasManage = usePermission("LEGALCOMPLIANCE_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canAssess = hasAssess || hasManage || hasAdmin
  const canApprove = hasApprove || hasAdmin
  const canManage = hasManage || hasAdmin

  const [filter, setFilter] = useState("ALL")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showProfile, setShowProfile] = useState(false)
  const [profileForm, setProfileForm] = useState({ industries: "", activities: "", employeeCount: "", entityType: "" })
  const [assess, setAssess] = useState<Row | null>(null)
  const [assessForm, setAssessForm] = useState({ decision: "APPLICABLE", justification: "" })
  const [approve, setApprove] = useState<Row | null>(null)
  const [approveForm, setApproveForm] = useState({ note: "", retireLinkedObligation: false })
  const [reject, setReject] = useState<Row | null>(null)
  const [rejectReason, setRejectReason] = useState("")
  const [generate, setGenerate] = useState<Row | null>(null)
  const [genForm, setGenForm] = useState({ responsibleUserName: "", reviewDate: "", recurrence: "", departmentName: "" })
  const [history, setHistory] = useState<Row | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: profile } = useQuery<Profile>({
    queryKey: ["lc-profile"], queryFn: async () => one(await apiClient.get(`${base}/profile`)),
  })
  const { data: rows = [], isLoading } = useQuery<Row[]>({
    queryKey: ["lc-worklist", filter], queryFn: async () => unwrap(await apiClient.get(`${base}/worklist`, { params: { filter } })),
  })
  const { data: past = [] } = useQuery<Assessment[]>({
    queryKey: ["lc-assess-history", history?.requirement.id], enabled: !!history,
    queryFn: async () => unwrap(await apiClient.get(`${base}/requirements/${history!.requirement.id}/history`)),
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["lc-worklist"] }); qc.invalidateQueries({ queryKey: ["lc-assess-history"] })
    qc.invalidateQueries({ queryKey: ["lc-obligations"] }); qc.invalidateQueries({ queryKey: ["lc-obligations-all"] }); qc.invalidateQueries({ queryKey: ["lc-calendar-30"] })
    setApiError("")
  }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))

  const saveProfile = useMutation({
    mutationFn: (body: any) => apiClient.put(`${base}/profile`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lc-profile"] }); refresh(); setShowProfile(false); setNotice("Profile saved — suggestions refreshed") },
    onError: fail("Failed to save profile"),
  })
  const doAssess = useMutation({
    mutationFn: (body: any) => apiClient.post(`${base}/assessments`, body),
    onSuccess: (_r, v: any) => { refresh(); setAssess(null); setNotice(v.decision === "NOT_APPLICABLE" ? "Recorded — it now needs approval before it takes effect" : "Recorded as applicable") },
    onError: fail("Failed to record assessment"),
  })
  const doApprove = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/assessments/${id}/approve`, body),
    onSuccess: () => { refresh(); setApprove(null); setNotice("Approved") }, onError: fail("Failed to approve"),
  })
  const doReject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => apiClient.post(`${base}/assessments/${id}/reject`, { reason }),
    onSuccess: () => { refresh(); setReject(null); setNotice("Rejected") }, onError: fail("Failed to reject"),
  })
  const doGenerate = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/requirements/${id}/generate-obligation`, body),
    onSuccess: (r) => { refresh(); setGenerate(null); setNotice(one(r).created ? "Obligation created — find it on the Obligations tab" : "An obligation already exists for this requirement") },
    onError: fail("Failed to create obligation"),
  })

  const unassessed = rows.filter(r => r.state === "UNASSESSED").length
  const openProfile = () => {
    setApiError(""); setProfileForm({ industries: (profile?.industries ?? []).join(", "), activities: (profile?.activities ?? []).join(", "),
      employeeCount: profile?.employeeCount?.toString() ?? "", entityType: profile?.entityType ?? "" }); setShowProfile(true)
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: "14px 18px", marginBottom: 16 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <Building2 size={20} color="#4338CA" />
          <div style={{ fontSize: 13, color: "#475569" }}>
            <b style={{ color: "#0F172A" }}>Organisation profile</b> —{" "}
            {profile && (profile.industries.length || profile.activities.length || profile.employeeCount != null)
              ? `${profile.employeeCount ?? "?"} employees · industries ${profile.industries.join(", ") || "not set"} · activities ${profile.activities.join(", ") || "not set"}`
              : "not set yet. Without it every suggestion will read “Needs review”."}
          </div>
        </div>
        {canAssess && <button onClick={openProfile} style={ghostBtn}>{profile?.updatedAt ? "Edit profile" : "Set up profile"}</button>}
      </div>

      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}

      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 16, alignItems: "center" }}>
        {FILTERS.map(([v, l]) => (
          <button key={v} onClick={() => setFilter(v)} style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filter === v ? 600 : 400,
            background: filter === v ? "#4338CA" : "#F1F5F9", color: filter === v ? "#fff" : "#64748B" }}>{l}</button>
        ))}
        {filter === "ALL" && unassessed > 0 && <span style={{ fontSize: 12, color: "#92400E" }}>{unassessed} still to assess</span>}
      </div>

      <Err m={apiError} />

      {isLoading ? <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading applicability worklist...</div>
        : rows.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
            <ListChecks size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
            <div style={{ fontWeight: 600, color: "#475569" }}>Nothing to assess here</div>
            <div style={{ fontSize: 13, marginTop: 6 }}>Requirements appear once an instrument has a published (CURRENT) version. Add them on the Regulatory Register tab.</div>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {rows.map(r => {
              const sg = SUGGESTION_CFG[r.suggestion] ?? SUGGESTION_CFG.NEEDS_REVIEW
              const st = STATE_CFG[r.state] ?? STATE_CFG.UNASSESSED
              const key = r.requirement.id
              const open = expanded === key
              return (
                <div key={key} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", background: "#fff" }}>
                  <div style={{ padding: "14px 18px", display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center" }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 3 }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{r.requirement.reference} · {r.requirement.title}</span>
                        <Badge text={st.label} color={st.color} bg={st.bg} />
                        <Badge text={`Suggestion: ${sg.label}`} color={sg.color} bg={sg.bg} />
                        {r.obligationId && <Badge text="Obligation created" color="#4338CA" bg="#E0E7FF" />}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>{r.instrumentTitle} ({r.instrumentIdentifier}) · {r.versionLabel}</div>
                    </div>
                    <div style={{ display: "flex", gap: 6, alignItems: "center", flexShrink: 0 }}>
                      {canAssess && r.state !== "PENDING" && <button onClick={() => { setAssess(r); setAssessForm({ decision: r.suggestion === "LIKELY_NOT_APPLICABLE" ? "NOT_APPLICABLE" : "APPLICABLE", justification: "" }); setApiError("") }} style={{ ...ghostBtn, padding: "6px 12px" }}>{r.state === "UNASSESSED" ? "Assess" : "Reassess"}</button>}
                      {canApprove && r.state === "PENDING" && r.assessment && <>
                        <button onClick={() => { setApprove(r); setApproveForm({ note: "", retireLinkedObligation: false }); setApiError("") }} style={{ ...primaryBtn, padding: "6px 12px" }}>Approve</button>
                        <button onClick={() => { setReject(r); setRejectReason(""); setApiError("") }} style={{ ...ghostBtn, padding: "6px 12px", color: "#B91C1C" }}>Reject</button>
                      </>}
                      {canManage && r.state === "APPLICABLE" && !r.obligationId && <button onClick={() => { setGenerate(r); setGenForm({ responsibleUserName: "", reviewDate: "", recurrence: "", departmentName: "" }); setApiError("") }} style={{ ...primaryBtn, padding: "6px 12px" }}>Create obligation</button>}
                      <button onClick={() => setExpanded(open ? null : key)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>{open ? <ChevronUp size={18} /> : <ChevronDown size={18} />}</button>
                    </div>
                  </div>
                  {open && (
                    <div style={{ borderTop: "1px solid #F1F5F9", padding: "14px 18px", background: "#F8FAFC", fontSize: 13, color: "#475569" }}>
                      <div style={{ whiteSpace: "pre-wrap", marginBottom: 10 }}>{r.requirement.requirementText}</div>
                      <div style={{ fontWeight: 600, color: "#0F172A", marginBottom: 4 }}>Why this suggestion</div>
                      <ul style={{ margin: "0 0 10px", paddingLeft: 18 }}>{r.suggestionReasons.map((x, n) => <li key={n}>{x}</li>)}</ul>
                      {r.assessment && (
                        <div style={{ marginBottom: 8 }}>
                          <b style={{ color: "#0F172A" }}>Latest decision:</b> {r.assessment.decision.replace("_", " ")} ({ASSESS_STATUS[r.assessment.status] ?? r.assessment.status}) by {r.assessment.assessedByName ?? "—"} on {fmtDate(r.assessment.assessedAt)}
                          {r.assessment.decidedByName && r.assessment.status !== "PENDING_APPROVAL" ? `, decided by ${r.assessment.decidedByName}` : ""}
                          <div style={{ fontStyle: "italic", marginTop: 2 }}>“{r.assessment.justification}”</div>
                        </div>
                      )}
                      <button onClick={() => setHistory(r)} style={{ ...ghostBtn, padding: "5px 10px", display: "inline-flex", gap: 5, alignItems: "center" }}><History size={12} /> Decision history</button>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}

      {showProfile && (
        <Modal title="Organisation profile" onClose={() => setShowProfile(false)}>
          <Err m={apiError} />
          <div style={{ fontSize: 12, color: "#64748B", marginBottom: 12 }}>Used only to suggest which requirements are likely to apply. It never assigns or approves anything.</div>
          <Field l="Industries (comma-separated)"><input style={input} value={profileForm.industries} onChange={e => setProfileForm({ ...profileForm, industries: e.target.value })} placeholder="e.g. RETAIL, MANUFACTURING" /></Field>
          <Field l="Activities (comma-separated)"><input style={input} value={profileForm.activities} onChange={e => setProfileForm({ ...profileForm, activities: e.target.value })} placeholder="e.g. PROCESSES_PERSONAL_INFO, HANDLES_CASH" /></Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <Field l="Employees"><input type="number" min={0} style={input} value={profileForm.employeeCount} onChange={e => setProfileForm({ ...profileForm, employeeCount: e.target.value })} /></Field>
            <Field l="Entity type"><input style={input} value={profileForm.entityType} onChange={e => setProfileForm({ ...profileForm, entityType: e.target.value })} placeholder="e.g. Private company" /></Field>
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setShowProfile(false)}>Cancel</button>
            <button style={primaryBtn} disabled={saveProfile.isPending} onClick={() => saveProfile.mutate({
              industries: csv(profileForm.industries), activities: csv(profileForm.activities),
              employeeCount: profileForm.employeeCount === "" ? null : Number(profileForm.employeeCount), entityType: profileForm.entityType || null })}>Save</button>
          </div>
        </Modal>
      )}

      {assess && (
        <Modal title={`Assess ${assess.requirement.reference} · ${assess.requirement.title}`} onClose={() => setAssess(null)}>
          <Err m={apiError} />
          <div style={{ fontSize: 12.5, color: "#64748B", marginBottom: 12 }}>System suggestion: <b>{SUGGESTION_CFG[assess.suggestion]?.label}</b>. This is a hint — you decide, and your reasoning is kept on record.</div>
          <Field l="Decision">
            <div style={{ display: "flex", gap: 8 }}>
              {[["APPLICABLE", "Applies to us"], ["NOT_APPLICABLE", "Does not apply"]].map(([v, l]) => (
                <button key={v} onClick={() => setAssessForm({ ...assessForm, decision: v })} style={{ flex: 1, padding: "9px 12px", borderRadius: 8, cursor: "pointer", fontSize: 13, fontWeight: 600,
                  border: `1.5px solid ${assessForm.decision === v ? "#4338CA" : "#E2E8F0"}`, background: assessForm.decision === v ? "#EEF2FF" : "#fff", color: assessForm.decision === v ? "#4338CA" : "#475569" }}>{l}</button>
              ))}
            </div>
          </Field>
          {assessForm.decision === "NOT_APPLICABLE" && <div style={{ fontSize: 12, color: "#92400E", background: "#FFFBEB", borderRadius: 8, padding: "8px 10px", marginBottom: 12 }}>Excluding a requirement needs a second person's approval before it takes effect.</div>}
          <Field l="Justification * (at least 15 characters)"><textarea style={{ ...input, minHeight: 90 }} value={assessForm.justification} onChange={e => setAssessForm({ ...assessForm, justification: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAssess(null)}>Cancel</button>
            <button style={primaryBtn} disabled={assessForm.justification.trim().length < 15 || doAssess.isPending}
              onClick={() => doAssess.mutate({ requirementId: assess.requirement.id, decision: assessForm.decision, justification: assessForm.justification })}>Record decision</button>
          </div>
        </Modal>
      )}

      {approve && approve.assessment && (
        <Modal title="Approve “does not apply”" onClose={() => setApprove(null)}>
          <Err m={apiError} />
          <div style={{ fontSize: 13, color: "#475569", marginBottom: 10 }}><b>{approve.requirement.reference} · {approve.requirement.title}</b><br />
            Assessed by {approve.assessment.assessedByName ?? "—"}: “{approve.assessment.justification}”</div>
          {approve.obligationId && (
            <label style={{ display: "flex", gap: 8, alignItems: "flex-start", fontSize: 13, background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, padding: 10, marginBottom: 12 }}>
              <input type="checkbox" checked={approveForm.retireLinkedObligation} onChange={e => setApproveForm({ ...approveForm, retireLinkedObligation: e.target.checked })} />
              <span>An active obligation was created from this requirement. Approving means it is retired (the history stays on record). Tick to confirm.</span>
            </label>
          )}
          <Field l="Note"><textarea style={{ ...input, minHeight: 60 }} value={approveForm.note} onChange={e => setApproveForm({ ...approveForm, note: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setApprove(null)}>Cancel</button>
            <button style={primaryBtn} disabled={(!!approve.obligationId && !approveForm.retireLinkedObligation) || doApprove.isPending}
              onClick={() => doApprove.mutate({ id: approve.assessment!.id, body: { note: approveForm.note || null, retireLinkedObligation: approveForm.retireLinkedObligation } })}>Approve</button>
          </div>
        </Modal>
      )}

      {reject && reject.assessment && (
        <Modal title="Reject assessment" onClose={() => setReject(null)}>
          <Err m={apiError} />
          <Field l="Reason *"><textarea style={{ ...input, minHeight: 80 }} value={rejectReason} onChange={e => setRejectReason(e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setReject(null)}>Cancel</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={!rejectReason.trim() || doReject.isPending} onClick={() => doReject.mutate({ id: reject.assessment!.id, reason: rejectReason })}>Reject</button>
          </div>
        </Modal>
      )}

      {generate && (
        <Modal title={`Create obligation from ${generate.requirement.reference}`} onClose={() => setGenerate(null)}>
          <Err m={apiError} />
          <Field l="Responsible person *"><input style={input} value={genForm.responsibleUserName} onChange={e => setGenForm({ ...genForm, responsibleUserName: e.target.value })} /></Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <Field l="First review / due date *"><input type="date" style={input} value={genForm.reviewDate} onChange={e => setGenForm({ ...genForm, reviewDate: e.target.value })} /></Field>
            <Field l={`Frequency (default ${generate.requirement.recurrence})`}>
              <select style={input} value={genForm.recurrence} onChange={e => setGenForm({ ...genForm, recurrence: e.target.value })}>
                <option value="">Use the requirement's</option>{RECURRENCES.map(x => <option key={x}>{x}</option>)}
              </select>
            </Field>
          </div>
          <Field l="Department"><input style={input} value={genForm.departmentName} onChange={e => setGenForm({ ...genForm, departmentName: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setGenerate(null)}>Cancel</button>
            <button style={primaryBtn} disabled={!genForm.responsibleUserName.trim() || !genForm.reviewDate || doGenerate.isPending}
              onClick={() => doGenerate.mutate({ id: generate.requirement.id, body: { responsibleUserName: genForm.responsibleUserName, reviewDate: genForm.reviewDate,
                recurrence: genForm.recurrence || null, departmentName: genForm.departmentName || null } })}>Create obligation</button>
          </div>
        </Modal>
      )}

      {history && (
        <Modal wide title={`Decision history — ${history.requirement.reference}`} onClose={() => setHistory(null)}>
          {past.length === 0 ? <div style={{ color: "#94A3B8", fontSize: 13 }}>No decisions recorded yet.</div> : past.map(a => (
            <div key={a.id} style={{ borderBottom: "1px solid #F1F5F9", padding: "10px 0", fontSize: 13, color: "#475569" }}>
              <div><b style={{ color: "#0F172A" }}>{a.decision.replace("_", " ")}</b> · {ASSESS_STATUS[a.status] ?? a.status} · {fmtDateTime(a.assessedAt)} · {a.assessedByName ?? "—"}</div>
              <div style={{ fontStyle: "italic" }}>“{a.justification}”</div>
              <div style={{ fontSize: 12, color: "#94A3B8" }}>System suggested {SUGGESTION_CFG[a.suggestion ?? ""]?.label ?? "—"}
                {a.decidedByName && a.status !== "APPROVED" || (a.decidedByName && a.decidedAt !== a.assessedAt) ? ` · decided by ${a.decidedByName} ${fmtDateTime(a.decidedAt)}` : ""}
                {a.decisionNote ? ` · note: ${a.decisionNote}` : ""}{a.supersededAt ? ` · superseded ${fmtDate(a.supersededAt)}` : ""}</div>
            </div>
          ))}
        </Modal>
      )}
    </div>
  )
}