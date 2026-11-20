// src/pages/legalcompliance/CapaTab.tsx
//
// Corrective and preventive actions (CAPA) against control-test findings, audit findings and incidents. Endpoints (/api/v1/legalcompliance/capas):
//   GET / · GET /summary · GET /{id} · POST / · PUT /{id} · POST /{id}/approve|return|start|complete|verify|cancel
// Evidence for completion goes through the shared evidence API (POST /api/v1/evidence, sourceModule=legalcompliance, relatedEntityType=CAPA).
//
// Segregation of duties (enforced by the server, mirrored here): the approver is not the proposer or owner; the verifier is not the owner or
// the person who completed the action. "Owner is me" lets the server know who the owner is — a typed name alone cannot be checked.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge, RowLimitNotice } from "./LcUi"
import { ChevronDown, ChevronUp, Plus, Paperclip, Download, X } from "lucide-react"

interface Capa {
  id: string; reference: string; parentKind: string; parentId: string; parentLabel: string | null; capaType: string; title: string; actionPlan: string; rootCause: string
  ownerName: string; dueDate: string; overdue: boolean; status: string; createdByName: string | null; approvedByName: string | null; approvedAt: string | null; approvalNote: string | null
  returnedCount: number; completedByName: string | null; completedAt: string | null; completionNote: string | null; evidenceSnapshot: { fileName: string }[]
  verifiedByName: string | null; verifiedAt: string | null; verificationNote: string | null; reopenedCount: number; cancelReason: string | null
}
interface EvidenceRef { id: string; fileName: string; fileSizeBytes: number; uploadedByName: string | null }
interface Detail { capa: Capa; liveEvidence: EvidenceRef[] }
interface Summary { proposed: number; approved: number; inProgress: number; awaitingVerification: number; overdue: number }
interface ParentOpt { id: string; reference: string; title: string }

const STATUS: Record<string, { label: string; color: string; bg: string }> = {
  PROPOSED: { label: "Proposed", color: "#475569", bg: "#F1F5F9" }, APPROVED: { label: "Approved", color: "#1D4ED8", bg: "#DBEAFE" }, IN_PROGRESS: { label: "In progress", color: "#92400E", bg: "#FEF3C7" },
  COMPLETED: { label: "Awaiting verification", color: "#6D28D9", bg: "#EDE9FE" }, VERIFIED: { label: "Verified", color: "#166534", bg: "#DCFCE7" }, CANCELLED: { label: "Cancelled", color: "#475569", bg: "#E2E8F0" },
}
const base = "/api/v1/legalcompliance/capas"
const kb = (n: number) => n < 1024 ? `${n} B` : `${Math.round(n / 1024)} KB`
const blank = { capaType: "CORRECTIVE", title: "", actionPlan: "", rootCause: "", ownerName: "", ownerIsMe: false, dueDate: "", parentKind: "AUDIT_FINDING", parentId: "" }
type Form = typeof blank & { id?: string }
type Act = { c: Capa; kind: "approve" | "return" | "complete" | "verify" | "reject-verify" | "cancel"; note: string }

export default function CapaTab() {
  const qc = useQueryClient()
  const { user, fullName } = useAuth()
  const admin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = usePermission("LEGALCOMPLIANCE_FINDING_MANAGE") || admin
  const canApprove = usePermission("LEGALCOMPLIANCE_CAPA_APPROVE") || admin
  const canVerify = usePermission("LEGALCOMPLIANCE_FINDING_VERIFY") || admin

  const [status, setStatus] = useState("")
  const [includeFinished, setIncludeFinished] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<Form | null>(null)
  const [act, setAct] = useState<Act | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")
  const [uploading, setUploading] = useState(false)

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-capas", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: capas = [], isLoading } = useQuery<Capa[]>({ queryKey: ["lc-capas", "list", status, includeFinished], queryFn: async () => unwrap(await apiClient.get(base, { params: { ...(status ? { status } : {}), includeFinished } })) })
  const { data: detail } = useQuery<Detail>({ queryKey: ["lc-capas", "detail", expanded], enabled: !!expanded, queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)) })
  const { data: auditFindings = [] } = useQuery<ParentOpt[]>({ queryKey: ["lc-audit-findings", "open"], enabled: !!form && !form.id, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/audit-findings")) })
  const { data: incidents = [] } = useQuery<ParentOpt[]>({ queryKey: ["lc-incidents", "open-list"], enabled: !!form && !form.id, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/incidents")) })
  const { data: testFindings = [] } = useQuery<ParentOpt[]>({ queryKey: ["lc-findings", "open-list"], enabled: !!form && !form.id, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/findings")) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-capas"] }); qc.invalidateQueries({ queryKey: ["lc-audit-findings"] }); qc.invalidateQueries({ queryKey: ["lc-findings"] }); setApiError("") }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: Form) => {
      const common = { capaType: f.capaType, title: f.title.trim(), actionPlan: f.actionPlan.trim(), rootCause: f.rootCause.trim(), ownerId: f.ownerIsMe ? user?.userId ?? null : null, ownerName: f.ownerIsMe ? fullName ?? f.ownerName : f.ownerName.trim(), dueDate: f.dueDate }
      return f.id ? apiClient.put(`${base}/${f.id}`, common)
        : apiClient.post(base, { ...common, findingId: f.parentKind === "FINDING" ? f.parentId : null, auditFindingId: f.parentKind === "AUDIT_FINDING" ? f.parentId : null, incidentId: f.parentKind === "INCIDENT" ? f.parentId : null })
    }, onSuccess: ok("Action saved", () => setForm(null)), onError: fail("Failed to save action"),
  })
  const start = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/start`), onSuccess: ok("Work started"), onError: fail("Failed to start") })
  const run = useMutation({
    mutationFn: (a: Act) => {
      const path = a.kind === "reject-verify" ? "verify" : a.kind
      const body = a.kind === "verify" ? { effective: true, note: a.note.trim() } : a.kind === "reject-verify" ? { effective: false, note: a.note.trim() }
        : a.kind === "cancel" ? { reason: a.note.trim() } : { note: a.note.trim() || null }
      return apiClient.post(`${base}/${a.c.id}/${path}`, body)
    }, onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed"),
  })
  const detach = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/evidence/${id}/detach`), onSuccess: () => qc.invalidateQueries({ queryKey: ["lc-capas", "detail"] }), onError: fail("Failed to remove evidence") })

  const upload = async (id: string, file: File) => {
    setUploading(true); setApiError("")
    try {
      const fd = new FormData()
      fd.append("file", file); fd.append("evidenceType", "CAPA_EVIDENCE"); fd.append("sourceModule", "legalcompliance"); fd.append("relatedEntityType", "CAPA"); fd.append("relatedEntityId", id)
      await apiClient.post("/api/v1/evidence", fd, { headers: { "Content-Type": "multipart/form-data" } })
      qc.invalidateQueries({ queryKey: ["lc-capas", "detail"] })
    } catch (e) { setApiError(errMsg(e, "Failed to upload evidence")) } finally { setUploading(false) }
  }
  const download = async (e: EvidenceRef) => {
    const r = await apiClient.get(`/api/v1/evidence/${e.id}/download`, { responseType: "blob" })
    const url = URL.createObjectURL(r.data); const a = document.createElement("a"); a.href = url; a.download = e.fileName; a.click(); URL.revokeObjectURL(url)
  }

  const parents = form?.parentKind === "FINDING" ? testFindings : form?.parentKind === "INCIDENT" ? incidents : auditFindings
  const set = (k: keyof Form, v: any) => setForm(f => f && { ...f, [k]: v })
  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const titles: Record<Act["kind"], string> = { approve: "Approve action", return: "Return to proposer", complete: "Mark completed", verify: "Verify as effective", "reject-verify": "Not effective — send back", cancel: "Cancel action" }
  const minLen = (k: Act["kind"]) => k === "approve" ? 0 : 15

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Proposed", summary?.proposed)}{card("Approved", summary?.approved, "#1D4ED8")}{card("In progress", summary?.inProgress, "#92400E")}
        {card("Awaiting verification", summary?.awaitingVerification, "#6D28D9")}{card("Overdue", summary?.overdue, summary?.overdue ? "#991B1B" : undefined)}
      </div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blank }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Propose action</button>}
        <select aria-label="Filter by status" style={{ ...input, width: 190 }} value={status} onChange={e => setStatus(e.target.value)}><option value="">All open statuses</option>{Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</select>
        <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6 }}><input type="checkbox" checked={includeFinished} onChange={e => setIncludeFinished(e.target.checked)} /> Show verified and cancelled</label>
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !form && !act && <Err m={apiError} />}

      <RowLimitNotice count={capas.length} />
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : capas.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No corrective or preventive actions.</p> : capas.map(c => {
        const open = expanded === c.id, st = STATUS[c.status], d = detail?.capa.id === c.id ? detail : null
        return (
          <div key={c.id} style={{ border: `1px solid ${c.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : c.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.reference} — {c.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{c.capaType.toLowerCase()} · {c.parentLabel ?? "—"} · owner {c.ownerName} · due {fmtDate(c.dueDate)}{c.overdue ? " (overdue)" : ""}</div>
              </div>
              <Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <p style={{ marginTop: 0 }}><b>Root cause:</b> {c.rootCause}</p><p><b>Action:</b> {c.actionPlan}</p>
                <p style={{ fontSize: 12 }}>Proposed by {c.createdByName ?? "—"}{c.returnedCount ? ` · returned ${c.returnedCount}×` : ""}{c.reopenedCount ? ` · sent back after completion ${c.reopenedCount}×` : ""}</p>
                {c.approvalNote && <p><b>{c.status === "PROPOSED" ? "Reviewer" : "Approver"} ({c.approvedByName}, {fmtDateTime(c.approvedAt)}):</b> {c.approvalNote}</p>}
                {c.completionNote && <p><b>Completed</b> by {c.completedByName} on {fmtDateTime(c.completedAt)}: {c.completionNote}</p>}
                {c.verificationNote && <p><b>Verifier ({c.verifiedByName}, {fmtDateTime(c.verifiedAt)}):</b> {c.verificationNote}</p>}
                {c.cancelReason && <p><b>Cancelled:</b> {c.cancelReason}</p>}

                {(c.status === "IN_PROGRESS" || c.status === "COMPLETED" || c.status === "VERIFIED") && (
                  <div style={{ margin: "8px 0" }}>
                    <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}><Paperclip size={12} style={{ verticalAlign: -1 }} /> Evidence ({d?.liveEvidence.length ?? 0})</div>
                    {(d?.liveEvidence ?? []).map(e => (
                      <div key={e.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 12, padding: "2px 0" }}>
                        <button onClick={() => download(e)} style={{ all: "unset", cursor: "pointer", color: "#4338CA", display: "flex", gap: 4, alignItems: "center" }}><Download size={12} /> {e.fileName}</button>
                        <span style={{ color: "#94A3B8" }}>{kb(e.fileSizeBytes)}{e.uploadedByName ? ` · ${e.uploadedByName}` : ""}</span>
                        {c.status === "IN_PROGRESS" && canManage && <button aria-label="Remove evidence" onClick={() => { if (confirm("Remove this file from the action? The file itself is preserved.")) detach.mutate(e.id) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={12} /></button>}
                      </div>
                    ))}
                    {c.status !== "IN_PROGRESS" && c.evidenceSnapshot.length > 0 && <div style={{ fontSize: 11, color: "#94A3B8" }}>{c.evidenceSnapshot.length} file(s) were attached at completion.</div>}
                    {c.status === "IN_PROGRESS" && canManage && (
                      <label style={{ ...ghostBtn, display: "inline-block", marginTop: 6, fontSize: 12, padding: "5px 12px" }}>{uploading ? "Uploading…" : "Attach file"}
                        <input type="file" style={{ display: "none" }} disabled={uploading} onChange={e => { const f = e.target.files?.[0]; if (f) upload(c.id, f); e.target.value = "" }} /></label>
                    )}
                  </div>
                )}
                {apiError && <Err m={apiError} />}

                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
                  {c.status === "PROPOSED" && canManage && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: c.id, capaType: c.capaType, title: c.title, actionPlan: c.actionPlan, rootCause: c.rootCause, ownerName: c.ownerName, ownerIsMe: false, dueDate: c.dueDate, parentKind: c.parentKind, parentId: c.parentId }) }}>Revise</button>}
                  {c.status === "PROPOSED" && canApprove && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ c, kind: "approve", note: "" }) }}>Approve</button>}
                  {c.status === "PROPOSED" && canApprove && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ c, kind: "return", note: "" }) }}>Return</button>}
                  {c.status === "APPROVED" && canManage && <button style={primaryBtn} disabled={start.isPending} onClick={() => start.mutate(c.id)}>Start work</button>}
                  {c.status === "IN_PROGRESS" && canManage && <button style={primaryBtn} disabled={(d?.liveEvidence.length ?? 0) < 1} onClick={() => { setApiError(""); setAct({ c, kind: "complete", note: "" }) }}>Mark completed</button>}
                  {c.status === "COMPLETED" && canVerify && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ c, kind: "verify", note: "" }) }}>Verify effective</button>}
                  {c.status === "COMPLETED" && canVerify && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ c, kind: "reject-verify", note: "" }) }}>Not effective</button>}
                  {c.status !== "VERIFIED" && c.status !== "CANCELLED" && canManage && <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setAct({ c, kind: "cancel", note: "" }) }}>Cancel</button>}
                </div>
                {c.status === "IN_PROGRESS" && (d?.liveEvidence.length ?? 0) < 1 && <p style={{ fontSize: 12, color: "#64748B" }}>Attach at least one evidence file before marking this completed.</p>}
                {c.status === "COMPLETED" && <p style={{ fontSize: 12, color: "#64748B" }}>Verification must be by someone other than the owner and {c.completedByName ?? "the person who completed it"}.</p>}
              </div>
            )}
          </div>
        )
      })}

      {form && (
        <Modal title={form.id ? "Revise action" : "Propose corrective / preventive action"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          {!form.id && (
            <>
              <div style={{ display: "flex", gap: 12 }}>
                <div style={{ flex: 1 }}><Field l="Against *"><select style={input} value={form.parentKind} onChange={e => setForm({ ...form, parentKind: e.target.value, parentId: "" })}><option value="AUDIT_FINDING">Audit finding</option><option value="FINDING">Control test finding</option><option value="INCIDENT">Incident</option></select></Field></div>
                <div style={{ flex: 2 }}><Field l="Finding or incident *"><select style={input} value={form.parentId} onChange={e => set("parentId", e.target.value)}><option value="">Choose…</option>{parents.map(p => <option key={p.id} value={p.id}>{p.reference} — {p.title}</option>)}</select></Field></div>
              </div>
            </>
          )}
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={form.capaType} onChange={e => set("capaType", e.target.value)}><option value="CORRECTIVE">Corrective (fix it)</option><option value="PREVENTIVE">Preventive (stop it recurring)</option></select></Field></div>
            <div style={{ flex: 1 }}><Field l="Due date *"><input type="date" style={input} value={form.dueDate} onChange={e => set("dueDate", e.target.value)} /></Field></div>
          </div>
          <Field l="Title *"><input style={input} value={form.title} onChange={e => set("title", e.target.value)} /></Field>
          <Field l="Root cause * (at least 15 characters)"><textarea style={{ ...input, minHeight: 60 }} value={form.rootCause} onChange={e => set("rootCause", e.target.value)} /></Field>
          <Field l="Action plan * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={form.actionPlan} onChange={e => set("actionPlan", e.target.value)} /></Field>
          <Field l="Owner *">
            <input style={input} disabled={form.ownerIsMe} value={form.ownerIsMe ? fullName ?? "" : form.ownerName} onChange={e => set("ownerName", e.target.value)} />
            <label style={{ fontSize: 12, color: "#475569", display: "flex", gap: 6, marginTop: 4 }}><input type="checkbox" checked={form.ownerIsMe} onChange={e => set("ownerIsMe", e.target.checked)} /> I am the owner (you then cannot approve or verify this action)</label>
          </Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={save.isPending || !form.title.trim() || form.rootCause.trim().length < 15 || form.actionPlan.trim().length < 15 || !form.dueDate || (!form.ownerIsMe && !form.ownerName.trim()) || (!form.id && !form.parentId)} onClick={() => save.mutate(form)}>{form.id ? "Save changes" : "Propose"}</button>
          </div>
        </Modal>
      )}

      {act && (
        <Modal title={`${titles[act.kind]} — ${act.c.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          <Field l={act.kind === "approve" ? "Note (optional)" : act.kind === "complete" ? "What was done * (at least 15 characters)" : "Note * (at least 15 characters)"}>
            <textarea style={{ ...input, minHeight: 80 }} value={act.note} onChange={e => setAct({ ...act, note: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAct(null)}>Back</button>
            <button style={act.kind === "cancel" ? { ...primaryBtn, background: "#B91C1C" } : primaryBtn} disabled={run.isPending || act.note.trim().length < minLen(act.kind)} onClick={() => run.mutate(act)}>{titles[act.kind]}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
