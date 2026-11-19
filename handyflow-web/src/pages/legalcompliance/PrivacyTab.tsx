// src/pages/legalcompliance/PrivacyTab.tsx
//
// Privacy impact assessments, PAIA requests and regulator filings. Endpoints (/api/v1/legalcompliance/privacy):
//   /pias (GET, POST, PUT /{id}, POST /{id}/submit|approve|reject) · /pias/summary
//   /paia (GET, POST, PUT /{id}, POST /{id}/extend|fee|decide|complaint|close) · /paia/summary
//   /filings (GET, POST, PUT /{id}, GET /{id}/files, POST /{id}/prepare|submit|acknowledge) · /filings/summary
// Everything here is the organisation's own record. Nothing is filed with, or approved by, the Information Regulator. The existing POPIA register and
// DSAR tabs are unchanged; a PIA can point at a processing activity from the POPIA register.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { EvidenceFiles } from "./LcEvidence"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

const base = "/api/v1/legalcompliance/privacy"
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const today = () => new Date().toISOString().slice(0, 10)
const RISK: Record<string, { color: string; bg: string }> = { LOW: { color: "#166534", bg: "#DCFCE7" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, HIGH: { color: "#991B1B", bg: "#FEE2E2" } }
const NEUTRAL = { color: "#475569", bg: "#F1F5F9" }
const MIN = 15

type Section = "pias" | "paia" | "filings"

export default function PrivacyTab() {
  const [section, setSection] = useState<Section>("pias")
  const sec = (id: Section, label: string) => (
    <button onClick={() => setSection(id)} style={{ ...ghostBtn, background: section === id ? "#EEF2FF" : "#fff", color: section === id ? "#4338CA" : "#475569", borderColor: section === id ? "#C7D2FE" : "#E2E8F0" }}>{label}</button>
  )
  return (
    <div>
      <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, padding: "8px 12px", fontSize: 12, color: "#64748B", marginBottom: 12 }}>
        These are your own records. Nothing here is filed with, or approved by, the Information Regulator. Statutory periods shown are defaults to confirm with your legal adviser.
      </div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>{sec("pias", "Impact assessments")}{sec("paia", "PAIA requests")}{sec("filings", "Regulator filings")}</div>
      {section === "pias" && <PiaSection />}
      {section === "paia" && <PaiaSection />}
      {section === "filings" && <FilingSection />}
    </div>
  )
}

function Card({ label, v, color = "#0F172A" }: { label: string; v: number | undefined; color?: string }) {
  return (
    <div style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
}
const Notice = ({ m }: { m: string }) => m ? <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{m}</div> : null
const Actions = ({ children }: { children: React.ReactNode }) => <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>{children}</div>
function Row({ open, onToggle, title, sub, badges, danger, children }: { open: boolean; onToggle: () => void; title: string; sub: string; badges: React.ReactNode; danger?: boolean; children: React.ReactNode }) {
  return (
    <div style={{ border: `1px solid ${danger ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
      <button onClick={onToggle} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
        <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{title}</div><div style={{ fontSize: 12, color: "#64748B" }}>{sub}</div></div>
        {badges}{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>
      {open && <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>{children}</div>}
    </div>
  )
}

// ═══ PIAs ═══
interface Pia {
  id: string; reference: string; title: string; popiaActivityId: string | null; activityName: string | null; description: string; necessity: string; risks: string; mitigations: string
  residualRisk: string; status: string; assessorName: string; reviewerName: string | null; reviewedAt: string | null; reviewNote: string | null; nextReviewDue: string | null; reviewOverdue: boolean
}
interface PiaSummary { draft: number; submitted: number; approved: number; rejected: number; highResidual: number; reviewOverdue: number }
const PIA_STATUS: Record<string, { label: string; color: string; bg: string }> = {
  DRAFT: { label: "Draft", ...NEUTRAL }, SUBMITTED: { label: "In review", color: "#92400E", bg: "#FEF3C7" }, APPROVED: { label: "Approved", color: "#166534", bg: "#DCFCE7" }, REJECTED: { label: "Rejected", color: "#991B1B", bg: "#FEE2E2" },
}
const blankPia = { id: "", title: "", popiaActivityId: "", description: "", necessity: "", risks: "", mitigations: "", residualRisk: "MEDIUM" }

function PiaSection() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_PRIVACY_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const hasApprove = usePermission("LEGALCOMPLIANCE_PRIVACY_APPROVE")
  const canApprove = hasApprove || hasAdmin
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blankPia | null>(null)
  const [act, setAct] = useState<{ p: Pia; kind: "approve" | "reject"; note: string; months: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<PiaSummary>({ queryKey: ["lc-pia", "summary"], queryFn: async () => one(await apiClient.get(`${base}/pias/summary`)) })
  const { data: pias = [], isLoading } = useQuery<Pia[]>({ queryKey: ["lc-pia", "list"], queryFn: async () => unwrap(await apiClient.get(`${base}/pias`)) })
  const { data: activities = [] } = useQuery<{ id: string; activityName: string }[]>({ queryKey: ["lc-popia-activities"], enabled: !!form, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/popia-activities")) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-pia"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: typeof blankPia) => {
      const body = { title: f.title.trim(), popiaActivityId: f.popiaActivityId || null, description: f.description.trim(), necessity: f.necessity.trim(), risks: f.risks.trim(), mitigations: f.mitigations.trim(), residualRisk: f.residualRisk }
      return f.id ? apiClient.put(`${base}/pias/${f.id}`, body) : apiClient.post(`${base}/pias`, body)
    }, onSuccess: ok("Assessment saved", () => setForm(null)), onError: fail("Failed to save assessment"),
  })
  const submit = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/pias/${id}/submit`), onSuccess: ok("Submitted for review"), onError: fail("Failed to submit") })
  const review = useMutation({
    mutationFn: (a: NonNullable<typeof act>) => a.kind === "approve"
      ? apiClient.post(`${base}/pias/${a.p.id}/approve`, { note: a.note.trim() || null, reviewMonths: a.months ? +a.months : 12 })
      : apiClient.post(`${base}/pias/${a.p.id}/reject`, { note: a.note.trim() }),
    onSuccess: ok("Review recorded", () => setAct(null)), onError: fail("Failed to record review"),
  })
  const set = (k: keyof typeof blankPia, v: string) => setForm(f => f && { ...f, [k]: v })
  const valid = (f: typeof blankPia) => f.title.trim() && [f.description, f.necessity, f.risks, f.mitigations].every(t => t.trim().length >= MIN)

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        <Card label="In draft" v={summary?.draft} /><Card label="In review" v={summary?.submitted} color="#92400E" /><Card label="Approved" v={summary?.approved} color="#166534" />
        <Card label="Approved with high residual risk" v={summary?.highResidual} color="#991B1B" /><Card label="Review overdue" v={summary?.reviewOverdue} color={summary?.reviewOverdue ? "#991B1B" : undefined} />
      </div>
      {canManage && <button style={{ ...primaryBtn, marginBottom: 12 }} onClick={() => { setApiError(""); setForm({ ...blankPia }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> New assessment</button>}
      <Notice m={notice} />
      {apiError && !form && !act && <Err m={apiError} />}
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : pias.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No privacy impact assessments yet.</p> : pias.map(p => (
        <Row key={p.id} open={expanded === p.id} onToggle={() => { setExpanded(expanded === p.id ? null : p.id); setApiError("") }} danger={p.reviewOverdue}
          title={`${p.reference} — ${p.title}`} sub={`Assessor ${p.assessorName}${p.activityName ? ` · activity: ${p.activityName}` : ""}${p.nextReviewDue ? ` · review ${fmtDate(p.nextReviewDue)}` : ""}`}
          badges={<>{p.reviewOverdue && <Badge text="Review overdue" color="#991B1B" bg="#FEE2E2" />}<Badge text={`Residual ${p.residualRisk}`} {...RISK[p.residualRisk]} /><Badge text={PIA_STATUS[p.status].label} color={PIA_STATUS[p.status].color} bg={PIA_STATUS[p.status].bg} /></>}>
          <p style={{ marginTop: 0 }}><b>Processing:</b> {p.description}</p>
          <p><b>Necessary and proportionate because:</b> {p.necessity}</p>
          <p><b>Risks to data subjects:</b> {p.risks}</p>
          <p><b>Mitigations:</b> {p.mitigations}</p>
          {p.reviewerName && <p><b>Reviewed by {p.reviewerName}</b> on {fmtDateTime(p.reviewedAt)}{p.reviewNote ? `: ${p.reviewNote}` : ""}</p>}
          {apiError && <Err m={apiError} />}
          <Actions>
            {canManage && (p.status === "DRAFT" || p.status === "REJECTED") && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: p.id, title: p.title, popiaActivityId: p.popiaActivityId ?? "", description: p.description, necessity: p.necessity, risks: p.risks, mitigations: p.mitigations, residualRisk: p.residualRisk }) }}>Edit</button>}
            {canManage && p.status === "DRAFT" && <button style={primaryBtn} disabled={submit.isPending} onClick={() => submit.mutate(p.id)}>Submit for review</button>}
            {canApprove && p.status === "SUBMITTED" && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ p, kind: "approve", note: "", months: "12" }) }}>Approve</button>}
            {canApprove && p.status === "SUBMITTED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ p, kind: "reject", note: "", months: "" }) }}>Reject</button>}
          </Actions>
          {p.status === "SUBMITTED" && <p style={{ fontSize: 12, color: "#64748B" }}>You cannot approve or reject an assessment you wrote or last edited.</p>}
        </Row>
      ))}

      {form && (
        <Modal wide title={form.id ? "Edit assessment" : "New privacy impact assessment"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <Field l="Title *"><input style={input} value={form.title} onChange={e => set("title", e.target.value)} /></Field>
          <Field l="Processing activity from the POPIA register (optional)"><select style={input} value={form.popiaActivityId} onChange={e => set("popiaActivityId", e.target.value)}><option value="">None</option>{activities.map(a => <option key={a.id} value={a.id}>{a.activityName}</option>)}</select></Field>
          <Field l={`What is being processed and why * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 60 }} value={form.description} onChange={e => set("description", e.target.value)} /></Field>
          <Field l="Why it is necessary and proportionate *"><textarea style={{ ...input, minHeight: 60 }} value={form.necessity} onChange={e => set("necessity", e.target.value)} /></Field>
          <Field l="Risks to data subjects *"><textarea style={{ ...input, minHeight: 60 }} value={form.risks} onChange={e => set("risks", e.target.value)} /></Field>
          <Field l="Mitigations *"><textarea style={{ ...input, minHeight: 60 }} value={form.mitigations} onChange={e => set("mitigations", e.target.value)} /></Field>
          <Field l="Residual risk after mitigation *"><select style={input} value={form.residualRisk} onChange={e => set("residualRisk", e.target.value)}>{["LOW", "MEDIUM", "HIGH"].map(r => <option key={r} value={r}>{pretty(r)}</option>)}</select></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button><button style={primaryBtn} disabled={save.isPending || !valid(form)} onClick={() => save.mutate(form)}>Save</button></div>
        </Modal>
      )}
      {act && (
        <Modal title={`${act.kind === "approve" ? "Approve" : "Reject"} — ${act.p.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.kind === "approve" && act.p.residualRisk === "HIGH" && <p style={{ fontSize: 12, color: "#991B1B", marginTop: 0 }}>Residual risk is HIGH: say why it is accepted.</p>}
          <Field l={act.kind === "approve" ? (act.p.residualRisk === "HIGH" ? `Why the risk is accepted * (at least ${MIN} characters)` : "Note (optional)") : `What needs to change * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={act.note} onChange={e => setAct({ ...act, note: e.target.value })} /></Field>
          {act.kind === "approve" && <Field l="Review again in (months)"><input type="number" min={1} max={60} style={input} value={act.months} onChange={e => setAct({ ...act, months: e.target.value })} /></Field>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button>
            <button style={primaryBtn} disabled={review.isPending || (act.kind === "reject" || act.p.residualRisk === "HIGH" ? act.note.trim().length < MIN : false)} onClick={() => review.mutate(act)}>{act.kind === "approve" ? "Approve" : "Reject"}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}

// ═══ PAIA ═══
interface Paia {
  id: string; reference: string; requesterName: string; requesterContact: string | null; onBehalfOf: string | null; recordDescription: string; receivedDate: string; dueDate: string; extendedUntil: string | null
  extensionReason: string | null; effectiveDue: string; status: string; assignedToName: string | null; feeAmount: number | null; feePaidOn: string | null; decision: string | null; decisionDate: string | null
  decisionReason: string | null; decidedByName: string | null; complaintLodgedOn: string | null; complaintReference: string | null; complaintOutcome: string | null; overdue: boolean; dueSoon: boolean
  canExtend: boolean; maxExtension: string
}
interface PaiaSummary { open: number; overdue: number; dueSoon: number; decided: number; refusedOrPartial: number }
type PaiaAct = { r: Paia; kind: "extend" | "fee" | "decide" | "complaint"; a: string; b: string; c: string; d: string }
const blankPaia = { id: "", requesterName: "", requesterContact: "", onBehalfOf: "", recordDescription: "", receivedDate: today(), assignedTo: "" }
const DECISIONS = ["GRANTED", "PARTIALLY_GRANTED", "REFUSED", "TRANSFERRED"]

function PaiaSection() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_PRIVACY_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const [includeClosed, setIncludeClosed] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blankPaia | null>(null)
  const [act, setAct] = useState<PaiaAct | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<PaiaSummary>({ queryKey: ["lc-paia", "summary"], queryFn: async () => one(await apiClient.get(`${base}/paia/summary`)) })
  const { data: requests = [], isLoading } = useQuery<Paia[]>({ queryKey: ["lc-paia", "list", includeClosed], queryFn: async () => unwrap(await apiClient.get(`${base}/paia`, { params: { includeClosed } })) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-paia"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: typeof blankPaia) => {
      const body = { requesterName: f.requesterName.trim(), requesterContact: f.requesterContact.trim() || null, onBehalfOf: f.onBehalfOf.trim() || null, recordDescription: f.recordDescription.trim(), receivedDate: f.receivedDate, assignedTo: f.assignedTo.trim() || null }
      return f.id ? apiClient.put(`${base}/paia/${f.id}`, body) : apiClient.post(`${base}/paia`, body)
    }, onSuccess: ok("Request saved", () => setForm(null)), onError: fail("Failed to save request"),
  })
  const run = useMutation({
    mutationFn: (a: PaiaAct) => {
      const id = a.r.id
      switch (a.kind) {
        case "extend": return apiClient.post(`${base}/paia/${id}/extend`, { until: a.a, reason: a.b.trim() })
        case "fee": return apiClient.post(`${base}/paia/${id}/fee`, { amount: a.a ? +a.a : null, paidOn: a.b || null })
        case "decide": return apiClient.post(`${base}/paia/${id}/decide`, { decision: a.a, decisionDate: a.b, reason: a.c.trim(), decidedBy: a.d.trim() })
        case "complaint": return apiClient.post(`${base}/paia/${id}/complaint`, { lodgedOn: a.a, reference: a.b.trim() || null, outcome: a.c.trim() || null })
      }
    }, onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed"),
  })
  const close = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/paia/${id}/close`), onSuccess: ok("Request closed"), onError: fail("Failed to close") })
  const set = (k: keyof typeof blankPaia, v: string) => setForm(f => f && { ...f, [k]: v })
  const titles = { extend: "Extend due date", fee: "Record fee", decide: "Record decision", complaint: "Record complaint" }
  const valid = (a: PaiaAct) => a.kind === "extend" ? !!a.a && a.b.trim().length >= MIN : a.kind === "fee" ? true : a.kind === "decide" ? !!a.a && !!a.b && a.c.trim().length >= MIN && !!a.d.trim() : !!a.a

  return (
    <div>
      <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Default timing: decide within 30 days of receipt (PAIA s25), with one extension of up to a further 30 days (s26). A request not decided in time risks being treated as refused (s27). Confirm the periods that apply to you with your legal adviser.</p>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        <Card label="Awaiting decision" v={summary?.open} /><Card label="Overdue" v={summary?.overdue} color={summary?.overdue ? "#991B1B" : undefined} /><Card label="Due within 5 days" v={summary?.dueSoon} color="#92400E" />
        <Card label="Decided" v={summary?.decided} /><Card label="Refused or part-refused" v={summary?.refusedOrPartial} color="#C2410C" />
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blankPaia }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Log request</button>}
        <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6 }}><input type="checkbox" checked={includeClosed} onChange={e => setIncludeClosed(e.target.checked)} /> Show closed</label>
      </div>
      <Notice m={notice} />
      {apiError && !form && !act && <Err m={apiError} />}
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : requests.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No PAIA requests.</p> : requests.map(r => (
        <Row key={r.id} open={expanded === r.id} onToggle={() => { setExpanded(expanded === r.id ? null : r.id); setApiError("") }} danger={r.overdue}
          title={`${r.reference} — ${r.requesterName}`} sub={`Received ${fmtDate(r.receivedDate)} · due ${fmtDate(r.effectiveDue)}${r.extendedUntil ? " (extended)" : ""}${r.assignedToName ? ` · ${r.assignedToName}` : ""}`}
          badges={<>{r.overdue && <Badge text="Overdue — deemed-refusal risk" color="#991B1B" bg="#FEE2E2" />}{r.dueSoon && <Badge text="Due soon" color="#92400E" bg="#FEF3C7" />}
            {r.decision && <Badge text={pretty(r.decision)} {...(r.decision === "GRANTED" ? { color: "#166534", bg: "#DCFCE7" } : { color: "#C2410C", bg: "#FFEDD5" })} />}<Badge text={r.status === "RECEIVED" ? "Awaiting decision" : r.status === "DECIDED" ? "Decided" : "Closed"} {...NEUTRAL} /></>}>
          <p style={{ marginTop: 0 }}><b>Records requested:</b> {r.recordDescription}</p>
          <p style={{ fontSize: 12 }}>{r.requesterContact ? `Contact ${r.requesterContact}` : ""}{r.onBehalfOf ? ` · on behalf of ${r.onBehalfOf}` : ""}{r.extensionReason ? ` · extended: ${r.extensionReason}` : ""}</p>
          {(r.feeAmount != null) && <p style={{ fontSize: 12 }}>Fee R{r.feeAmount.toFixed(2)} — {r.feePaidOn ? `paid ${fmtDate(r.feePaidOn)}` : "unpaid"}</p>}
          {r.decision && <p><b>Decision ({pretty(r.decision)}, {fmtDate(r.decisionDate)}, {r.decidedByName}):</b> {r.decisionReason}</p>}
          {r.complaintLodgedOn && <p><b>Complaint lodged {fmtDate(r.complaintLodgedOn)}</b>{r.complaintReference ? ` (ref ${r.complaintReference})` : ""}{r.complaintOutcome ? ` — ${r.complaintOutcome}` : ""}</p>}
          {apiError && <Err m={apiError} />}
          {canManage && r.status !== "CLOSED" && (
            <Actions>
              {r.status === "RECEIVED" && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: r.id, requesterName: r.requesterName, requesterContact: r.requesterContact ?? "", onBehalfOf: r.onBehalfOf ?? "", recordDescription: r.recordDescription, receivedDate: r.receivedDate, assignedTo: r.assignedToName ?? "" }) }}>Edit</button>}
              {r.status === "RECEIVED" && r.canExtend && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ r, kind: "extend", a: "", b: "", c: "", d: "" }) }}>Extend</button>}
              <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ r, kind: "fee", a: r.feeAmount?.toString() ?? "", b: r.feePaidOn ?? "", c: "", d: "" }) }}>Fee</button>
              {r.status === "RECEIVED" && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ r, kind: "decide", a: "GRANTED", b: today(), c: "", d: "" }) }}>Record decision</button>}
              {r.status === "DECIDED" && r.decision !== "GRANTED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ r, kind: "complaint", a: today(), b: r.complaintReference ?? "", c: r.complaintOutcome ?? "", d: "" }) }}>Record complaint</button>}
              {r.status === "DECIDED" && <button style={primaryBtn} disabled={close.isPending} onClick={() => close.mutate(r.id)}>Close</button>}
            </Actions>
          )}
        </Row>
      ))}

      {form && (
        <Modal title={form.id ? "Edit PAIA request" : "Log PAIA request"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <Field l="Requester *"><input style={input} value={form.requesterName} onChange={e => set("requesterName", e.target.value)} /></Field>
          <Field l="Contact details"><input style={input} value={form.requesterContact} onChange={e => set("requesterContact", e.target.value)} /></Field>
          <Field l="On behalf of (if any)"><input style={input} value={form.onBehalfOf} onChange={e => set("onBehalfOf", e.target.value)} /></Field>
          <Field l={`Records requested * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={form.recordDescription} onChange={e => set("recordDescription", e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Date received *"><input type="date" style={input} value={form.receivedDate} disabled={!!form.id} max={today()} onChange={e => set("receivedDate", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Handled by"><input style={input} value={form.assignedTo} onChange={e => set("assignedTo", e.target.value)} /></Field></div>
          </div>
          {!form.id && form.receivedDate && <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>The due date is set from the date received and cannot be edited later.</p>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={save.isPending || !form.requesterName.trim() || form.recordDescription.trim().length < MIN || !form.receivedDate} onClick={() => save.mutate(form)}>Save</button></div>
        </Modal>
      )}
      {act && (
        <Modal title={`${titles[act.kind]} — ${act.r.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.kind === "extend" && (<>
            <Field l={`New due date * (after ${fmtDate(act.r.dueDate)}, no later than ${fmtDate(act.r.maxExtension)})`}><input type="date" style={input} min={act.r.dueDate} max={act.r.maxExtension} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
            <Field l={`Reason * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 60 }} value={act.b} onChange={e => setAct({ ...act, b: e.target.value })} /></Field>
          </>)}
          {act.kind === "fee" && (<>
            <Field l="Fee amount (R)"><input type="number" min={0} step="0.01" style={input} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
            <Field l="Paid on"><input type="date" style={input} value={act.b} onChange={e => setAct({ ...act, b: e.target.value })} /></Field>
          </>)}
          {act.kind === "decide" && (<>
            <div style={{ display: "flex", gap: 12 }}>
              <div style={{ flex: 1 }}><Field l="Decision *"><select style={input} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })}>{DECISIONS.map(d => <option key={d} value={d}>{pretty(d)}</option>)}</select></Field></div>
              <div style={{ flex: 1 }}><Field l="Decision date *"><input type="date" style={input} min={act.r.receivedDate} value={act.b} onChange={e => setAct({ ...act, b: e.target.value })} /></Field></div>
            </div>
            <Field l="Decided by *"><input style={input} value={act.d} onChange={e => setAct({ ...act, d: e.target.value })} /></Field>
            <Field l={act.a === "GRANTED" ? `Basis for the decision * (at least ${MIN} characters)` : `Ground for refusing, part-refusing or transferring * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={act.c} onChange={e => setAct({ ...act, c: e.target.value })} /></Field>
          </>)}
          {act.kind === "complaint" && (<>
            <Field l="Lodged on *"><input type="date" style={input} min={act.r.decisionDate ?? undefined} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
            <Field l="Reference"><input style={input} value={act.b} onChange={e => setAct({ ...act, b: e.target.value })} /></Field>
            <Field l="Outcome (if known)"><textarea style={{ ...input, minHeight: 50 }} value={act.c} onChange={e => setAct({ ...act, c: e.target.value })} /></Field>
            <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>This only records the complaint. It is not managed or submitted from here.</p>
          </>)}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button><button style={primaryBtn} disabled={run.isPending || !valid(act)} onClick={() => run.mutate(act)}>{titles[act.kind]}</button></div>
        </Modal>
      )}
    </div>
  )
}

// ═══ Filings ═══
interface Filing {
  id: string; reference: string; filingType: string; title: string; periodLabel: string | null; dueDate: string; ownerName: string; status: string; submittedOn: string | null; submittedByName: string | null
  regulatorReference: string | null; proof: { id: string; fileName: string }[]; liveProofCount: number; notes: string | null; overdue: boolean; dueSoon: boolean
}
interface FilingSummary { pending: number; overdue: number; dueSoon: number; submitted: number }
const FILING_TYPES = ["INFORMATION_OFFICER_REGISTRATION", "PAIA_MANUAL_REVIEW", "PAIA_ANNUAL_REPORT", "POPIA_SELF_ASSESSMENT", "OTHER"]
const FILING_STATUS: Record<string, { label: string; color: string; bg: string }> = {
  PLANNED: { label: "Planned", ...NEUTRAL }, PREPARED: { label: "Prepared", color: "#1D4ED8", bg: "#DBEAFE" }, SUBMITTED: { label: "Submitted", color: "#92400E", bg: "#FEF3C7" }, ACKNOWLEDGED: { label: "Acknowledged", color: "#166534", bg: "#DCFCE7" },
}
const blankFiling = { id: "", filingType: "PAIA_ANNUAL_REPORT", title: "", periodLabel: "", dueDate: "", ownerName: "", notes: "" }

function FilingSection() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_PRIVACY_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const [includeDone, setIncludeDone] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blankFiling | null>(null)
  const [act, setAct] = useState<{ f: Filing; kind: "submit" | "acknowledge"; a: string; b: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<FilingSummary>({ queryKey: ["lc-filing", "summary"], queryFn: async () => one(await apiClient.get(`${base}/filings/summary`)) })
  const { data: filings = [], isLoading } = useQuery<Filing[]>({ queryKey: ["lc-filing", "list", includeDone], queryFn: async () => unwrap(await apiClient.get(`${base}/filings`, { params: { includeDone } })) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-filing"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: typeof blankFiling) => {
      const body = { filingType: f.filingType, title: f.title.trim(), periodLabel: f.periodLabel.trim() || null, dueDate: f.dueDate, ownerName: f.ownerName.trim(), notes: f.notes.trim() || null }
      return f.id ? apiClient.put(`${base}/filings/${f.id}`, body) : apiClient.post(`${base}/filings`, body)
    }, onSuccess: ok("Filing saved", () => setForm(null)), onError: fail("Failed to save filing"),
  })
  const prepare = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/filings/${id}/prepare`), onSuccess: ok("Marked prepared"), onError: fail("Failed") })
  const run = useMutation({
    mutationFn: (a: NonNullable<typeof act>) => a.kind === "submit"
      ? apiClient.post(`${base}/filings/${a.f.id}/submit`, { submittedOn: a.a, submittedBy: a.b.trim() })
      : apiClient.post(`${base}/filings/${a.f.id}/acknowledge`, { regulatorReference: a.a.trim() }),
    onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed"),
  })
  const set = (k: keyof typeof blankFiling, v: string) => setForm(f => f && { ...f, [k]: v })

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        <Card label="Not yet submitted" v={summary?.pending} /><Card label="Overdue" v={summary?.overdue} color={summary?.overdue ? "#991B1B" : undefined} /><Card label="Due within 30 days" v={summary?.dueSoon} color="#92400E" /><Card label="Submitted" v={summary?.submitted} color="#166534" />
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blankFiling }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Plan filing</button>}
        <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6 }}><input type="checkbox" checked={includeDone} onChange={e => setIncludeDone(e.target.checked)} /> Show submitted</label>
      </div>
      <Notice m={notice} />
      {apiError && !form && !act && <Err m={apiError} />}
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : filings.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No filings planned.</p> : filings.map(f => (
        <Row key={f.id} open={expanded === f.id} onToggle={() => { setExpanded(expanded === f.id ? null : f.id); setApiError("") }} danger={f.overdue}
          title={`${f.reference} — ${f.title}`} sub={`${pretty(f.filingType)}${f.periodLabel ? ` · ${f.periodLabel}` : ""} · due ${fmtDate(f.dueDate)} · owner ${f.ownerName}`}
          badges={<>{f.overdue && <Badge text="Overdue" color="#991B1B" bg="#FEE2E2" />}{f.dueSoon && <Badge text="Due soon" color="#92400E" bg="#FEF3C7" />}<Badge text={FILING_STATUS[f.status].label} color={FILING_STATUS[f.status].color} bg={FILING_STATUS[f.status].bg} /></>}>
          {f.notes && <p style={{ marginTop: 0 }}>{f.notes}</p>}
          {f.submittedOn && <p>Submitted {fmtDate(f.submittedOn)} by {f.submittedByName}{f.regulatorReference ? ` · regulator reference ${f.regulatorReference}` : ""}</p>}
          {f.status === "SUBMITTED" || f.status === "ACKNOWLEDGED"
            ? <p style={{ fontSize: 12 }}>Proof kept at submission: {f.proof.length ? f.proof.map(p => p.fileName).join(", ") : "none"}.</p>
            : (f.status === "PREPARED" || f.status === "PLANNED") && <EvidenceFiles filesUrl={`${base}/filings/${f.id}/files`} entityType="FILING" entityId={f.id} evidenceType="FILING_PROOF" canEdit={canManage} onChange={refresh} />}
          {apiError && <Err m={apiError} />}
          {canManage && (
            <Actions>
              {(f.status === "PLANNED" || f.status === "PREPARED") && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: f.id, filingType: f.filingType, title: f.title, periodLabel: f.periodLabel ?? "", dueDate: f.dueDate, ownerName: f.ownerName, notes: f.notes ?? "" }) }}>Edit</button>}
              {f.status === "PLANNED" && <button style={primaryBtn} disabled={prepare.isPending} onClick={() => prepare.mutate(f.id)}>Mark prepared</button>}
              {f.status === "PREPARED" && <button style={primaryBtn} disabled={f.liveProofCount === 0} title={f.liveProofCount === 0 ? "Attach proof of submission first" : undefined} onClick={() => { setApiError(""); setAct({ f, kind: "submit", a: today(), b: "" }) }}>Record submission</button>}
              {f.status === "SUBMITTED" && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ f, kind: "acknowledge", a: "", b: "" }) }}>Record acknowledgement</button>}
            </Actions>
          )}
        </Row>
      ))}

      {form && (
        <Modal title={form.id ? "Edit filing" : "Plan filing"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <Field l="Type *"><select style={input} value={form.filingType} onChange={e => set("filingType", e.target.value)}>{FILING_TYPES.map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field>
          <Field l="Title *"><input style={input} value={form.title} onChange={e => set("title", e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Period"><input style={input} placeholder="e.g. 2025/26" value={form.periodLabel} onChange={e => set("periodLabel", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Due date *"><input type="date" style={input} value={form.dueDate} onChange={e => set("dueDate", e.target.value)} /></Field></div>
          </div>
          <Field l="Owner *"><input style={input} value={form.ownerName} onChange={e => set("ownerName", e.target.value)} /></Field>
          <Field l="Notes"><textarea style={{ ...input, minHeight: 50 }} value={form.notes} onChange={e => set("notes", e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={save.isPending || !form.title.trim() || !form.dueDate || !form.ownerName.trim()} onClick={() => save.mutate(form)}>Save</button></div>
        </Modal>
      )}
      {act && (
        <Modal title={`${act.kind === "submit" ? "Record submission" : "Record acknowledgement"} — ${act.f.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.kind === "submit" ? (<>
            <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>This records that you submitted it, outside this system. The {act.f.liveProofCount} proof file(s) attached now are kept with the record.</p>
            <Field l="Submitted on *"><input type="date" style={input} max={today()} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
            <Field l="Submitted by *"><input style={input} value={act.b} onChange={e => setAct({ ...act, b: e.target.value })} /></Field>
          </>) : <Field l="Regulator's reference *"><input style={input} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button>
            <button style={primaryBtn} disabled={run.isPending || !act.a.trim() || (act.kind === "submit" && !act.b.trim())} onClick={() => run.mutate(act)}>Save</button></div>
        </Modal>
      )}
    </div>
  )
}
