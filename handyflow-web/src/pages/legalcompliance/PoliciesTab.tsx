// src/pages/legalcompliance/PoliciesTab.tsx
//
// Policies, procedures and standards with versioning. Endpoints (/api/v1/legalcompliance/policies):
//   GET / · /summary · /{id} · POST / · PUT /{id} · POST /{id}/versions · PUT /versions/{vid} · POST /versions/{vid}/submit|approve|return|publish · POST /{id}/retire
//
// A version's text can only change while it is a draft. The approver cannot be the person who wrote (last edited) the version. Publishing a new
// version supersedes the old one and closes attestation campaigns open on it — launch a new campaign from the Attestations tab.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

interface Policy {
  id: string; reference: string; title: string; policyType: string; ownerName: string; reviewMonths: number; status: string; publishedVersion: number | null; effectiveDate: string | null
  nextReviewDue: string | null; reviewOverdue: boolean; reviewDueSoon: boolean; openVersionStatus: string | null; openVersion: number | null; retiredReason: string | null; openCampaigns: number
}
interface Version {
  id: string; versionNumber: number; status: string; content: string; changeSummary: string | null; authorName: string | null; submittedAt: string | null; contentHash: string | null
  approvedByName: string | null; approvedAt: string | null; reviewNote: string | null; returnedCount: number; effectiveDate: string | null; publishedByName: string | null; publishedAt: string | null
}
interface Detail { policy: Policy; versions: Version[] }
interface Summary { active: number; reviewOverdue: number; reviewDueSoon: number; awaitingApproval: number; drafts: number }

const VSTATUS: Record<string, { label: string; color: string; bg: string }> = {
  DRAFT: { label: "Draft", color: "#475569", bg: "#F1F5F9" }, IN_REVIEW: { label: "In review", color: "#92400E", bg: "#FEF3C7" }, APPROVED: { label: "Approved", color: "#1D4ED8", bg: "#DBEAFE" },
  PUBLISHED: { label: "Published", color: "#166534", bg: "#DCFCE7" }, SUPERSEDED: { label: "Superseded", color: "#64748B", bg: "#E2E8F0" },
}
const base = "/api/v1/legalcompliance/policies"
const blank = { id: "", title: "", policyType: "POLICY", ownerName: "", reviewMonths: "12", content: "" }
type Act = { v: Version; kind: "approve" | "return" | "publish"; note: string; effective: string }

export default function PoliciesTab() {
  const qc = useQueryClient()
  const admin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = usePermission("LEGALCOMPLIANCE_POLICY_MANAGE") || admin
  const canApprove = usePermission("LEGALCOMPLIANCE_POLICY_APPROVE") || admin

  const [includeRetired, setIncludeRetired] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blank | null>(null)
  const [edit, setEdit] = useState<{ policyId: string; versionId: string | null; content: string; summary: string } | null>(null)
  const [act, setAct] = useState<Act | null>(null)
  const [retire, setRetire] = useState<{ p: Policy; reason: string } | null>(null)
  const [reading, setReading] = useState<Version | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-policies", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: policies = [], isLoading } = useQuery<Policy[]>({ queryKey: ["lc-policies", "list", includeRetired], queryFn: async () => unwrap(await apiClient.get(base, { params: { includeRetired } })) })
  const { data: detail } = useQuery<Detail>({ queryKey: ["lc-policies", "detail", expanded], enabled: !!expanded, queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-policies"] }); qc.invalidateQueries({ queryKey: ["lc-campaigns"] }); setApiError("") }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: typeof blank) => {
      const body = { title: f.title.trim(), policyType: f.policyType, ownerName: f.ownerName.trim(), reviewMonths: +f.reviewMonths || 12, content: f.content }
      return f.id ? apiClient.put(`${base}/${f.id}`, body) : apiClient.post(base, body)
    }, onSuccess: ok("Policy saved", () => setForm(null)), onError: fail("Failed to save policy"),
  })
  const saveVersion = useMutation({
    mutationFn: (e: NonNullable<typeof edit>) => e.versionId ? apiClient.put(`${base}/versions/${e.versionId}`, { content: e.content, changeSummary: e.summary || null })
      : apiClient.post(`${base}/${e.policyId}/versions`, { content: e.content, changeSummary: e.summary || null }),
    onSuccess: ok("Draft saved", () => setEdit(null)), onError: fail("Failed to save draft"),
  })
  const submit = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/versions/${id}/submit`), onSuccess: ok("Submitted for approval"), onError: fail("Failed to submit") })
  const run = useMutation({
    mutationFn: (a: Act) => a.kind === "publish" ? apiClient.post(`${base}/versions/${a.v.id}/publish`, { effectiveDate: a.effective || null })
      : a.kind === "approve" ? apiClient.post(`${base}/versions/${a.v.id}/approve`, { note: a.note.trim() || null }) : apiClient.post(`${base}/versions/${a.v.id}/return`, { note: a.note.trim() }),
    onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed"),
  })
  const doRetire = useMutation({ mutationFn: (r: { id: string; reason: string }) => apiClient.post(`${base}/${r.id}/retire`, { reason: r.reason.trim() }), onSuccess: ok("Policy retired", () => setRetire(null)), onError: fail("Failed to retire") })

  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const latestPublished = (d: Detail | null) => d?.versions.find(v => v.status === "PUBLISHED")

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Active policies", summary?.active)}{card("Review overdue", summary?.reviewOverdue, summary?.reviewOverdue ? "#991B1B" : undefined)}{card("Review due in 30 days", summary?.reviewDueSoon, "#C2410C")}
        {card("Awaiting approval", summary?.awaitingApproval, "#92400E")}{card("Drafts", summary?.drafts)}
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blank }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> New policy</button>}
        <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6 }}><input type="checkbox" checked={includeRetired} onChange={e => setIncludeRetired(e.target.checked)} /> Show retired</label>
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !form && !edit && !act && !retire && <Err m={apiError} />}

      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : policies.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No policies yet.</p> : policies.map(p => {
        const open = expanded === p.id, d = detail?.policy.id === p.id ? detail : null
        return (
          <div key={p.id} style={{ border: `1px solid ${p.reviewOverdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : p.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{p.reference} — {p.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{p.policyType.toLowerCase()} · owner {p.ownerName} · {p.publishedVersion ? `v${p.publishedVersion} in force since ${fmtDate(p.effectiveDate)}` : "not yet published"}{p.nextReviewDue ? ` · review ${p.reviewOverdue ? "was due " : "due "}${fmtDate(p.nextReviewDue)}` : ""}</div>
              </div>
              {p.status === "RETIRED" && <Badge text="Retired" color="#475569" bg="#E2E8F0" />}
              {p.reviewOverdue && <Badge text="Review overdue" color="#991B1B" bg="#FEE2E2" />}
              {p.openVersionStatus && <Badge text={`v${p.openVersion} ${VSTATUS[p.openVersionStatus].label.toLowerCase()}`} color={VSTATUS[p.openVersionStatus].color} bg={VSTATUS[p.openVersionStatus].bg} />}
              {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {p.retiredReason && <p style={{ marginTop: 0 }}><b>Retired:</b> {p.retiredReason}</p>}
                {apiError && <Err m={apiError} />}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "6px 0 10px" }}>
                  {canManage && p.status === "ACTIVE" && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: p.id, title: p.title, policyType: p.policyType, ownerName: p.ownerName, reviewMonths: String(p.reviewMonths), content: "" }) }}>Edit details</button>}
                  {canManage && p.status === "ACTIVE" && !p.openVersionStatus && <button style={primaryBtn} onClick={() => { setApiError(""); setEdit({ policyId: p.id, versionId: null, content: latestPublished(d)?.content ?? "", summary: "" }) }}>Draft new version</button>}
                  {canManage && p.status === "ACTIVE" && <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setRetire({ p, reason: "" }) }}>Retire</button>}
                </div>
                <div style={{ fontWeight: 700, marginBottom: 6 }}>Versions</div>
                {(d?.versions ?? []).map(v => {
                  const st = VSTATUS[v.status]
                  return (
                    <div key={v.id} style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 10, marginBottom: 6, background: "#F8FAFC" }}>
                      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                        <b style={{ color: "#0F172A" }}>Version {v.versionNumber}</b><Badge text={st.label} color={st.color} bg={st.bg} />
                        <span style={{ fontSize: 12, color: "#64748B" }}>by {v.authorName ?? "—"}{v.returnedCount ? ` · returned ${v.returnedCount}×` : ""}{v.effectiveDate ? ` · effective ${fmtDate(v.effectiveDate)}` : ""}</span>
                        <button style={{ ...ghostBtn, padding: "3px 10px", fontSize: 12 }} onClick={() => setReading(v)}>Read</button>
                      </div>
                      {v.changeSummary && <div style={{ fontSize: 12, marginTop: 4 }}><b>What changed:</b> {v.changeSummary}</div>}
                      {v.reviewNote && <div style={{ fontSize: 12 }}><b>Reviewer ({v.approvedByName}, {fmtDateTime(v.approvedAt)}):</b> {v.reviewNote}</div>}
                      {v.contentHash && <div style={{ fontSize: 11, color: "#94A3B8", fontFamily: "monospace" }}>sha-256 {v.contentHash.slice(0, 16)}…</div>}
                      <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
                        {v.status === "DRAFT" && canManage && <button style={ghostBtn} onClick={() => { setApiError(""); setEdit({ policyId: p.id, versionId: v.id, content: v.content, summary: v.changeSummary ?? "" }) }}>Edit draft</button>}
                        {v.status === "DRAFT" && canManage && <button style={primaryBtn} disabled={submit.isPending} onClick={() => submit.mutate(v.id)}>Submit for approval</button>}
                        {v.status === "IN_REVIEW" && canApprove && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ v, kind: "approve", note: "", effective: "" }) }}>Approve</button>}
                        {v.status === "IN_REVIEW" && canApprove && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ v, kind: "return", note: "", effective: "" }) }}>Return</button>}
                        {v.status === "APPROVED" && canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ v, kind: "publish", note: "", effective: new Date().toISOString().slice(0, 10) }) }}>Publish</button>}
                      </div>
                      {v.status === "IN_REVIEW" && <div style={{ fontSize: 12, color: "#64748B", marginTop: 4 }}>Approval must be by someone other than {v.authorName ?? "the author"}.</div>}
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )
      })}

      {form && (
        <Modal title={form.id ? "Edit policy details" : "New policy"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <Field l="Title *"><input style={input} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={form.policyType} onChange={e => setForm({ ...form, policyType: e.target.value })}><option value="POLICY">Policy</option><option value="PROCEDURE">Procedure</option><option value="STANDARD">Standard</option></select></Field></div>
            <div style={{ flex: 1 }}><Field l="Review every (months)"><input type="number" min={1} max={60} style={input} value={form.reviewMonths} onChange={e => setForm({ ...form, reviewMonths: e.target.value })} /></Field></div>
          </div>
          <Field l="Owner *"><input style={input} value={form.ownerName} onChange={e => setForm({ ...form, ownerName: e.target.value })} /></Field>
          {!form.id && <Field l="Policy text * (at least 50 characters to submit)"><textarea style={{ ...input, minHeight: 160 }} value={form.content} onChange={e => setForm({ ...form, content: e.target.value })} /></Field>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={save.isPending || !form.title.trim() || !form.ownerName.trim() || (!form.id && !form.content.trim())} onClick={() => save.mutate(form)}>Save</button>
          </div>
        </Modal>
      )}

      {edit && (
        <Modal title={edit.versionId ? "Edit draft" : "Draft new version"} onClose={() => setEdit(null)}>
          <Err m={apiError} />
          <Field l="Policy text *"><textarea style={{ ...input, minHeight: 220 }} value={edit.content} onChange={e => setEdit({ ...edit, content: e.target.value })} /></Field>
          <Field l="What changed (required from version 2, at least 15 characters)"><textarea style={{ ...input, minHeight: 50 }} value={edit.summary} onChange={e => setEdit({ ...edit, summary: e.target.value })} /></Field>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Whoever saves the draft last counts as its author and cannot approve it.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setEdit(null)}>Cancel</button>
            <button style={primaryBtn} disabled={saveVersion.isPending || !edit.content.trim()} onClick={() => saveVersion.mutate(edit)}>Save draft</button>
          </div>
        </Modal>
      )}

      {act && (
        <Modal title={`${act.kind === "approve" ? "Approve" : act.kind === "return" ? "Return" : "Publish"} version ${act.v.versionNumber}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.kind === "publish" ? (
            <>
              <Field l="Effective date"><input type="date" style={input} value={act.effective} onChange={e => setAct({ ...act, effective: e.target.value })} /></Field>
              <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>This supersedes the version now in force and closes any open attestation campaign on it.</p>
            </>
          ) : (
            <Field l={act.kind === "approve" ? "Note (optional)" : "What must change * (at least 15 characters)"}><textarea style={{ ...input, minHeight: 80 }} value={act.note} onChange={e => setAct({ ...act, note: e.target.value })} /></Field>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAct(null)}>Back</button>
            <button style={primaryBtn} disabled={run.isPending || (act.kind === "return" && act.note.trim().length < 15)} onClick={() => run.mutate(act)}>{act.kind === "approve" ? "Approve" : act.kind === "return" ? "Return to author" : "Publish"}</button>
          </div>
        </Modal>
      )}

      {retire && (
        <Modal title={`Retire ${retire.p.reference}`} onClose={() => setRetire(null)}>
          <Err m={apiError} />
          <Field l="Reason * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={retire.reason} onChange={e => setRetire({ ...retire, reason: e.target.value })} /></Field>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Open attestation campaigns on this policy will be closed.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setRetire(null)}>Cancel</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={doRetire.isPending || retire.reason.trim().length < 15} onClick={() => doRetire.mutate({ id: retire.p.id, reason: retire.reason })}>Retire policy</button>
          </div>
        </Modal>
      )}

      {reading && (
        <Modal title={`Version ${reading.versionNumber}`} onClose={() => setReading(null)}>
          <div style={{ whiteSpace: "pre-wrap", fontSize: 13, color: "#0F172A", maxHeight: 420, overflow: "auto" }}>{reading.content}</div>
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 10 }}><button style={ghostBtn} onClick={() => setReading(null)}>Close</button></div>
        </Modal>
      )}
    </div>
  )
}
