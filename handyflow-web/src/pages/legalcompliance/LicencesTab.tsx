// src/pages/legalcompliance/LicencesTab.tsx
//
// Licences, permits, certificates, registrations and accreditations. Endpoints (/api/v1/legalcompliance/licences):
//   GET / (status, includeEnded) · /summary · /{id} · /{id}/files?renewal= · POST / · PUT /{id}
//   POST /{id}/renewal/start|abandon|submit|renewed · POST /{id}/suspend|reinstate|revoke|surrender
//   POST /{id}/conditions · PUT|DELETE /{id}/conditions/{cid} · POST /{id}/conditions/{cid}/met|breach
// "Expired" is worked out from the expiry date, never stored. Renewal: start → submit (proof of submission must be attached first) → record the
// renewed licence (the new expiry must be later than the old). Files go through the shared evidence API (entity types LICENCE and LICENCE_RENEWAL).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { EvidenceFiles } from "./LcEvidence"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

const base = "/api/v1/legalcompliance/licences"
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const MIN = 15
const TYPES = ["LICENCE", "PERMIT", "CERTIFICATE", "REGISTRATION", "ACCREDITATION"]
const STATUS: Record<string, { label: string; color: string; bg: string }> = {
  ACTIVE: { label: "Active", color: "#166534", bg: "#DCFCE7" }, EXPIRED: { label: "Expired", color: "#991B1B", bg: "#FEE2E2" },
  RENEWAL_IN_PROGRESS: { label: "Renewal in progress", color: "#1E40AF", bg: "#DBEAFE" }, SUSPENDED: { label: "Suspended", color: "#92400E", bg: "#FEF3C7" },
  REVOKED: { label: "Revoked", color: "#475569", bg: "#E2E8F0" }, SURRENDERED: { label: "Surrendered", color: "#475569", bg: "#E2E8F0" },
}
const CSTATUS: Record<string, { color: string; bg: string }> = { PENDING: { color: "#475569", bg: "#F1F5F9" }, MET: { color: "#166534", bg: "#DCFCE7" }, BREACHED: { color: "#991B1B", bg: "#FEE2E2" } }

interface Lic {
  id: string; reference: string; licenceType: string; title: string; issuingAuthority: string; licenceNumber: string | null; scope: string | null; issueDate: string | null; expiryDate: string | null
  status: string; displayStatus: string; ownerName: string; renewalLeadDays: number; obligationId: string | null; obligationTitle: string | null; renewalStartedOn: string | null
  renewalSubmittedOn: string | null; renewalReference: string | null; renewalProof: { id: string; fileName: string }[]; liveRenewalFiles: number; renewalHistory: Record<string, unknown>[]
  statusReason: string | null; notes: string | null; expired: boolean; expiringSoon: boolean; daysToExpiry: number | null; conditionCount: number; conditionsOverdue: number; conditionsBreached: number
}
interface Cond { id: string; description: string; dueDate: string | null; frequencyMonths: number | null; status: string; lastMetOn: string | null; note: string | null; overdue: boolean; dueSoon: boolean }
interface Detail { licence: Lic; conditions: Cond[] }
interface Summary { live: number; valid: number; expiring: number; expired: number; renewalInProgress: number; suspended: number; conditionsOverdue: number; conditionsBreached: number }
interface Obl { id: string; title: string }

const blank = { id: "", licenceType: "LICENCE", title: "", issuingAuthority: "", licenceNumber: "", scope: "", issueDate: "", expiryDate: "", ownerName: "", renewalLeadDays: "90", obligationId: "", notes: "" }
const blankCond = { id: "", description: "", dueDate: "", frequencyMonths: "" }
type Act = { l: Lic; kind: "suspend" | "reinstate" | "revoke" | "surrender" | "renewal/abandon"; a: string }
type CondAct = { l: Lic; c: Cond; kind: "met" | "breach"; on: string; a: string }
const today = () => new Date().toISOString().slice(0, 10)

export default function LicencesTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_LICENCE_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin

  const [filter, setFilter] = useState("")
  const [showEnded, setShowEnded] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blank | null>(null)
  const [condForm, setCondForm] = useState<{ l: Lic; v: typeof blankCond } | null>(null)
  const [act, setAct] = useState<Act | null>(null)
  const [condAct, setCondAct] = useState<CondAct | null>(null)
  const [submit, setSubmit] = useState<{ l: Lic; on: string; ref: string; files: number } | null>(null)
  const [renewed, setRenewed] = useState<{ l: Lic; issue: string; expiry: string; number: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-lic", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: licences = [], isLoading } = useQuery<Lic[]>({ queryKey: ["lc-lic", "list", filter, showEnded], queryFn: async () => unwrap(await apiClient.get(base, { params: { ...(filter ? { status: filter } : {}), includeEnded: showEnded } })) })
  const { data: detail } = useQuery<Detail>({ queryKey: ["lc-lic", "detail", expanded], enabled: !!expanded, queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)) })
  const { data: obligations = [] } = useQuery<Obl[]>({ queryKey: ["lc-lic", "obligations"], enabled: !!form, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/obligations?size=200")) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-lic"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: (f: typeof blank) => {
      const body = { licenceType: f.licenceType, title: f.title.trim(), issuingAuthority: f.issuingAuthority.trim(), licenceNumber: f.licenceNumber.trim() || null, scope: f.scope.trim() || null, issueDate: f.issueDate || null, expiryDate: f.expiryDate || null, ownerName: f.ownerName.trim(), renewalLeadDays: +f.renewalLeadDays || 90, obligationId: f.obligationId || null, notes: f.notes.trim() || null }
      return f.id ? apiClient.put(`${base}/${f.id}`, body) : apiClient.post(base, body)
    }, onSuccess: ok("Saved", () => setForm(null)), onError: fail("Failed to save licence"),
  })
  const startRenewal = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/renewal/start`), onSuccess: ok("Renewal started"), onError: fail("Cannot start renewal") })
  const run = useMutation({ mutationFn: (a: Act) => apiClient.post(`${base}/${a.l.id}/${a.kind}`, { reason: a.a.trim() }), onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed") })
  const doSubmit = useMutation({ mutationFn: (s: NonNullable<typeof submit>) => apiClient.post(`${base}/${s.l.id}/renewal/submit`, { submittedOn: s.on, reference: s.ref.trim() || null }), onSuccess: ok("Submission recorded", () => setSubmit(null)), onError: fail("Cannot record submission") })
  const doRenewed = useMutation({ mutationFn: (r: NonNullable<typeof renewed>) => apiClient.post(`${base}/${r.l.id}/renewal/renewed`, { newIssueDate: r.issue || null, newExpiryDate: r.expiry, newLicenceNumber: r.number.trim() || null }), onSuccess: ok("Renewal recorded", () => setRenewed(null)), onError: fail("Cannot record renewal") })
  const saveCond = useMutation({
    mutationFn: (x: NonNullable<typeof condForm>) => {
      const body = { description: x.v.description.trim(), dueDate: x.v.dueDate || null, frequencyMonths: x.v.frequencyMonths ? +x.v.frequencyMonths : null }
      return x.v.id ? apiClient.put(`${base}/${x.l.id}/conditions/${x.v.id}`, body) : apiClient.post(`${base}/${x.l.id}/conditions`, body)
    }, onSuccess: ok("Condition saved", () => setCondForm(null)), onError: fail("Failed to save condition"),
  })
  const removeCond = useMutation({ mutationFn: ({ l, c }: { l: Lic; c: Cond }) => apiClient.delete(`${base}/${l.id}/conditions/${c.id}`), onSuccess: ok("Condition removed"), onError: fail("Failed to remove condition") })
  const doCond = useMutation({
    mutationFn: (a: CondAct) => a.kind === "met" ? apiClient.post(`${base}/${a.l.id}/conditions/${a.c.id}/met`, { metOn: a.on, note: a.a.trim() || null }) : apiClient.post(`${base}/${a.l.id}/conditions/${a.c.id}/breach`, { reason: a.a.trim() }),
    onSuccess: ok("Condition updated", () => setCondAct(null)), onError: fail("Failed to update condition"),
  })

  const set = (k: keyof typeof blank, v: string) => setForm(f => f && { ...f, [k]: v })
  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 120, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const bad = (n?: number) => (n ? "#991B1B" : undefined)
  const actTitle = { suspend: "Suspend", reinstate: "Reinstate", revoke: "Revoke", surrender: "Surrender", "renewal/abandon": "Abandon renewal" }
  const actLabel = { suspend: "Reason for suspension", reinstate: "Basis for reinstating", revoke: "Reason for revocation", surrender: "Reason for surrender", "renewal/abandon": "Why is the renewal being abandoned?" }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("In force", summary?.live)}{card("Valid", summary?.valid, "#166534")}{card("Due for renewal", summary?.expiring, "#92400E")}{card("Expired", summary?.expired, bad(summary?.expired))}
        {card("Renewal in progress", summary?.renewalInProgress, "#1E40AF")}{card("Conditions overdue", summary?.conditionsOverdue, bad(summary?.conditionsOverdue))}{card("Conditions breached", summary?.conditionsBreached, bad(summary?.conditionsBreached))}
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ ...blank }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Add licence</button>}
        <select style={{ ...input, width: 190 }} value={filter} onChange={e => setFilter(e.target.value)}><option value="">All statuses</option>{["ACTIVE", "RENEWAL_IN_PROGRESS", "SUSPENDED", "REVOKED", "SURRENDERED"].map(s => <option key={s} value={s}>{STATUS[s].label}</option>)}</select>
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={showEnded} onChange={e => setShowEnded(e.target.checked)} /> Include revoked and surrendered</label>
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !form && !act && !condForm && !condAct && !submit && !renewed && <Err m={apiError} />}

      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : licences.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No licences or permits recorded.</p> : licences.map(l => {
        const open = expanded === l.id
        const d = open && detail?.licence.id === l.id ? detail : null
        const final = l.status === "REVOKED" || l.status === "SURRENDERED"
        const st = STATUS[l.displayStatus] ?? STATUS.ACTIVE
        const live = d?.licence.liveRenewalFiles ?? 0
        const flagged = l.expired || l.conditionsOverdue > 0 || l.conditionsBreached > 0
        return (
          <div key={l.id} style={{ border: `1px solid ${flagged ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : l.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{l.reference} — {l.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{pretty(l.licenceType)} · {l.issuingAuthority}{l.licenceNumber ? ` · ${l.licenceNumber}` : ""} · owner {l.ownerName}{l.expiryDate ? ` · ${l.expired ? "expired" : "expires"} ${fmtDate(l.expiryDate)}` : " · no expiry"}</div></div>
              {l.expiringSoon && l.daysToExpiry !== null && <Badge text={`${l.daysToExpiry} day${l.daysToExpiry === 1 ? "" : "s"} left`} color="#92400E" bg="#FEF3C7" />}
              {l.conditionsOverdue > 0 && <Badge text={`${l.conditionsOverdue} condition(s) overdue`} color="#991B1B" bg="#FEE2E2" />}
              {l.conditionsBreached > 0 && <Badge text={`${l.conditionsBreached} breached`} color="#991B1B" bg="#FEE2E2" />}
              <Badge text={st.label} color={st.color} bg={st.bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {l.scope && <p style={{ marginTop: 0, whiteSpace: "pre-wrap" }}>{l.scope}</p>}
                <p style={{ fontSize: 12 }}>{l.issueDate ? `Issued ${fmtDate(l.issueDate)} · ` : ""}renewal reminders start {l.renewalLeadDays} days before expiry{l.obligationTitle ? ` · supports obligation: ${l.obligationTitle}` : ""}</p>
                {l.statusReason && <p style={{ fontSize: 12, color: "#92400E" }}>{pretty(l.status)}: {l.statusReason}</p>}
                {l.notes && <p style={{ fontSize: 12, whiteSpace: "pre-wrap" }}>{l.notes}</p>}

                <div style={{ fontWeight: 700, marginBottom: 4 }}>Licence document</div>
                {!final && <EvidenceFiles filesUrl={`${base}/${l.id}/files`} entityType="LICENCE" entityId={l.id} evidenceType="LICENCE_DOCUMENT" canEdit={canManage} onChange={refresh} />}

                {l.status === "RENEWAL_IN_PROGRESS" && (
                  <div style={{ background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, padding: 10, margin: "10px 0" }}>
                    <div style={{ fontWeight: 700, color: "#1E40AF" }}>Renewal started {fmtDate(l.renewalStartedOn)}</div>
                    {l.renewalSubmittedOn ? (
                      <p style={{ fontSize: 12, margin: "4px 0" }}>Submitted {fmtDate(l.renewalSubmittedOn)}{l.renewalReference ? ` · reference ${l.renewalReference}` : ""} · proof: {l.renewalProof.map(p => p.fileName).join(", ")}. Record the renewed licence once the authority has issued it.</p>
                    ) : (
                      <p style={{ fontSize: 12, margin: "4px 0" }}>Attach proof that the renewal was submitted (receipt, application, acknowledgement), then record the submission.</p>
                    )}
                    {!l.renewalSubmittedOn && <EvidenceFiles filesUrl={`${base}/${l.id}/files?renewal=true`} entityType="LICENCE_RENEWAL" entityId={l.id} evidenceType="LICENCE_RENEWAL_PROOF" canEdit={canManage} onChange={refresh} />}
                  </div>
                )}

                <div style={{ fontWeight: 700, margin: "10px 0 4px" }}>Conditions</div>
                {!d ? <p style={{ fontSize: 12 }}>Loading…</p> : d.conditions.length === 0 ? <p style={{ fontSize: 12 }}>No conditions recorded.</p> : d.conditions.map(c => (
                  <div key={c.id} style={{ border: "1px solid #F1F5F9", borderRadius: 8, padding: "8px 10px", marginBottom: 6 }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                      <div style={{ flex: 1, minWidth: 160 }}>{c.description} <span style={{ fontSize: 12, color: "#64748B" }}>{c.dueDate ? `· due ${fmtDate(c.dueDate)}` : ""}{c.frequencyMonths ? ` · every ${c.frequencyMonths} month(s)` : ""}{c.lastMetOn ? ` · last met ${fmtDate(c.lastMetOn)}` : ""}</span></div>
                      {c.overdue && <Badge text="Overdue" color="#991B1B" bg="#FEE2E2" />}{c.dueSoon && !c.overdue && <Badge text="Due soon" color="#92400E" bg="#FEF3C7" />}<Badge text={pretty(c.status)} {...CSTATUS[c.status]} />
                    </div>
                    {c.note && <div style={{ fontSize: 12, color: c.status === "BREACHED" ? "#991B1B" : "#64748B" }}>{c.note}</div>}
                    {canManage && !final && (
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4 }}>
                        {c.status !== "MET" && <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} onClick={() => { setApiError(""); setCondAct({ l, c, kind: "met", on: today(), a: "" }) }}>{c.status === "BREACHED" ? "Put right" : "Mark met"}</button>}
                        {c.status === "PENDING" && <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} onClick={() => { setApiError(""); setCondAct({ l, c, kind: "breach", on: "", a: "" }) }}>Record breach</button>}
                        <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} onClick={() => { setApiError(""); setCondForm({ l, v: { id: c.id, description: c.description, dueDate: c.dueDate ?? "", frequencyMonths: c.frequencyMonths ? String(c.frequencyMonths) : "" } }) }}>Edit</button>
                        <button style={{ ...ghostBtn, padding: "4px 10px", fontSize: 12 }} onClick={() => { if (confirm("Remove this condition?")) removeCond.mutate({ l, c }) }}>Remove</button>
                      </div>
                    )}
                  </div>
                ))}

                {l.renewalHistory.length > 0 && (
                  <div style={{ marginTop: 10 }}>
                    <div style={{ fontWeight: 700, marginBottom: 4 }}>Renewal history</div>
                    {[...l.renewalHistory].reverse().map((h, i) => (
                      <div key={i} style={{ fontSize: 12, color: "#64748B" }}>Renewed {fmtDate(String(h.renewedOn))}: expiry {h.previousExpiry ? fmtDate(String(h.previousExpiry)) : "none"} → {fmtDate(String(h.newExpiry))}{h.reference ? ` · ref ${String(h.reference)}` : ""}{h.recordedBy ? ` · by ${String(h.recordedBy)}` : ""}</div>
                    ))}
                  </div>
                )}

                {apiError && <Err m={apiError} />}
                {canManage && !final && (
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 10 }}>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setCondForm({ l, v: { ...blankCond } }) }}>Add condition</button>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: l.id, licenceType: l.licenceType, title: l.title, issuingAuthority: l.issuingAuthority, licenceNumber: l.licenceNumber ?? "", scope: l.scope ?? "", issueDate: l.issueDate ?? "", expiryDate: l.expiryDate ?? "", ownerName: l.ownerName, renewalLeadDays: String(l.renewalLeadDays), obligationId: l.obligationId ?? "", notes: l.notes ?? "" }) }}>Edit details</button>
                    {l.status === "ACTIVE" && l.expiryDate && <button style={primaryBtn} disabled={startRenewal.isPending} onClick={() => startRenewal.mutate(l.id)}>Start renewal</button>}
                    {l.status === "RENEWAL_IN_PROGRESS" && !l.renewalSubmittedOn && <button style={primaryBtn} disabled={live === 0} title={live === 0 ? "Attach proof first" : ""} onClick={() => { setApiError(""); setSubmit({ l, on: today(), ref: "", files: live }) }}>Record submission</button>}
                    {l.status === "RENEWAL_IN_PROGRESS" && l.renewalSubmittedOn && <button style={primaryBtn} onClick={() => { setApiError(""); setRenewed({ l, issue: "", expiry: "", number: "" }) }}>Record renewed licence</button>}
                    {l.status === "RENEWAL_IN_PROGRESS" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ l, kind: "renewal/abandon", a: "" }) }}>Abandon renewal</button>}
                    {(l.status === "ACTIVE" || l.status === "RENEWAL_IN_PROGRESS") && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ l, kind: "suspend", a: "" }) }}>Suspend</button>}
                    {l.status === "SUSPENDED" && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ l, kind: "reinstate", a: "" }) }}>Reinstate</button>}
                    <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ l, kind: "surrender", a: "" }) }}>Surrender</button>
                    <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ l, kind: "revoke", a: "" }) }}>Record revocation</button>
                  </div>
                )}
              </div>
            )}
          </div>
        )
      })}

      {form && (
        <Modal title={form.id ? "Edit licence" : "Add licence"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={form.licenceType} onChange={e => set("licenceType", e.target.value)}>{TYPES.map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
            <div style={{ flex: 2 }}><Field l="Title *"><input style={input} value={form.title} onChange={e => set("title", e.target.value)} /></Field></div>
          </div>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 2 }}><Field l="Issuing authority *"><input style={input} value={form.issuingAuthority} onChange={e => set("issuingAuthority", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Number"><input style={input} value={form.licenceNumber} onChange={e => set("licenceNumber", e.target.value)} /></Field></div>
          </div>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Issued"><input type="date" style={input} value={form.issueDate} onChange={e => set("issueDate", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Expires"><input type="date" style={input} value={form.expiryDate} disabled={!!form.id && licences.find(x => x.id === form.id)?.status === "RENEWAL_IN_PROGRESS"} onChange={e => set("expiryDate", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Remind (days before)"><input type="number" min={1} max={365} style={input} value={form.renewalLeadDays} onChange={e => set("renewalLeadDays", e.target.value)} /></Field></div>
          </div>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Owner *"><input style={input} value={form.ownerName} onChange={e => set("ownerName", e.target.value)} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Supports obligation"><select style={input} value={form.obligationId} onChange={e => set("obligationId", e.target.value)}><option value="">None</option>{obligations.map(o => <option key={o.id} value={o.id}>{o.title}</option>)}</select></Field></div>
          </div>
          <Field l="Scope — what it permits"><textarea style={{ ...input, minHeight: 50 }} value={form.scope} onChange={e => set("scope", e.target.value)} /></Field>
          <Field l="Notes"><textarea style={{ ...input, minHeight: 40 }} value={form.notes} onChange={e => set("notes", e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button><button style={primaryBtn} disabled={save.isPending || !form.title.trim() || !form.issuingAuthority.trim() || !form.ownerName.trim()} onClick={() => save.mutate(form)}>Save</button></div>
        </Modal>
      )}
      {condForm && (
        <Modal title={`${condForm.v.id ? "Edit" : "Add"} condition — ${condForm.l.title}`} onClose={() => setCondForm(null)}>
          <Err m={apiError} />
          <Field l={`Condition * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 60 }} value={condForm.v.description} onChange={e => setCondForm({ ...condForm, v: { ...condForm.v, description: e.target.value } })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l={condForm.v.frequencyMonths ? "First due date *" : "Due date"}><input type="date" style={input} value={condForm.v.dueDate} onChange={e => setCondForm({ ...condForm, v: { ...condForm.v, dueDate: e.target.value } })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Repeats every (months)"><input type="number" min={1} max={120} placeholder="one-off" style={input} value={condForm.v.frequencyMonths} onChange={e => setCondForm({ ...condForm, v: { ...condForm.v, frequencyMonths: e.target.value } })} /></Field></div>
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setCondForm(null)}>Cancel</button><button style={primaryBtn} disabled={saveCond.isPending || condForm.v.description.trim().length < MIN || (!!condForm.v.frequencyMonths && !condForm.v.dueDate)} onClick={() => saveCond.mutate(condForm)}>Save</button></div>
        </Modal>
      )}
      {condAct && (
        <Modal title={condAct.kind === "met" ? (condAct.c.status === "BREACHED" ? "Put breach right" : "Mark condition met") : "Record breach"} onClose={() => setCondAct(null)}>
          <Err m={apiError} />
          <p style={{ fontSize: 13, color: "#475569", marginTop: 0 }}>{condAct.c.description}</p>
          {condAct.kind === "met" && <Field l="Date met *"><input type="date" max={today()} style={input} value={condAct.on} onChange={e => setCondAct({ ...condAct, on: e.target.value })} /></Field>}
          <Field l={condAct.kind === "breach" ? `Describe the breach * (at least ${MIN} characters)` : condAct.c.status === "BREACHED" ? `How was it put right? * (at least ${MIN} characters)` : "Note"}><textarea style={{ ...input, minHeight: 60 }} value={condAct.a} onChange={e => setCondAct({ ...condAct, a: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setCondAct(null)}>Cancel</button>
            <button style={primaryBtn} disabled={doCond.isPending || (condAct.kind === "met" ? !condAct.on || (condAct.c.status === "BREACHED" && condAct.a.trim().length < MIN) : condAct.a.trim().length < MIN)} onClick={() => doCond.mutate(condAct)}>Save</button></div>
        </Modal>
      )}
      {act && (
        <Modal title={`${actTitle[act.kind]} — ${act.l.title}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          <Field l={`${actLabel[act.kind]} * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button><button style={primaryBtn} disabled={run.isPending || act.a.trim().length < MIN} onClick={() => run.mutate(act)}>Confirm</button></div>
        </Modal>
      )}
      {submit && (
        <Modal title={`Record renewal submission — ${submit.l.title}`} onClose={() => setSubmit(null)}>
          <Err m={apiError} />
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>{submit.files} proof file(s) attached will be saved with the submission.</p>
          <Field l="Submitted on *"><input type="date" max={today()} style={input} value={submit.on} onChange={e => setSubmit({ ...submit, on: e.target.value })} /></Field>
          <Field l="Reference given by the authority"><input style={input} value={submit.ref} onChange={e => setSubmit({ ...submit, ref: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setSubmit(null)}>Cancel</button><button style={primaryBtn} disabled={doSubmit.isPending || !submit.on} onClick={() => doSubmit.mutate(submit)}>Record</button></div>
        </Modal>
      )}
      {renewed && (
        <Modal title={`Record renewed licence — ${renewed.l.title}`} onClose={() => setRenewed(null)}>
          <Err m={apiError} />
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Current expiry {fmtDate(renewed.l.expiryDate)}. The new expiry must be later.</p>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="New issue date"><input type="date" style={input} value={renewed.issue} onChange={e => setRenewed({ ...renewed, issue: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="New expiry date *"><input type="date" style={input} value={renewed.expiry} onChange={e => setRenewed({ ...renewed, expiry: e.target.value })} /></Field></div>
          </div>
          <Field l="New licence number (if changed)"><input style={input} value={renewed.number} onChange={e => setRenewed({ ...renewed, number: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setRenewed(null)}>Cancel</button><button style={primaryBtn} disabled={doRenewed.isPending || !renewed.expiry} onClick={() => doRenewed.mutate(renewed)}>Record</button></div>
        </Modal>
      )}
    </div>
  )
}
