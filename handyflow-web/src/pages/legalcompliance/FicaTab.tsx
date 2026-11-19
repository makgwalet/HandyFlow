// src/pages/legalcompliance/FicaTab.tsx
//
// FICA / RMCP support. Endpoints (/api/v1/legalcompliance/fica), all restricted to LEGALCOMPLIANCE_FICA_MANAGE:
//   GET/PUT /profile · GET /summary · /parties (GET, POST, PUT /{id}, POST /{id}/screened|verify|reopen|decline, owners under /{id}/owners) · /escalations (GET, POST, POST /{id}/review|decide)
// FICA applies only to organisations that are accountable institutions. Until the profile says so, the party and escalation endpoints answer 409
// FICA_NOT_APPLICABLE and this tab shows only the profile questionnaire. The platform does not rate risk, screen anyone or file reports with any authority.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

const base = "/api/v1/legalcompliance/fica"
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()
const today = () => new Date().toISOString().slice(0, 10)
const MIN = 15
const RISK: Record<string, { color: string; bg: string }> = { LOW: { color: "#166534", bg: "#DCFCE7" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, HIGH: { color: "#991B1B", bg: "#FEE2E2" } }
const PSTATUS: Record<string, { label: string; color: string; bg: string }> = {
  PENDING: { label: "Pending", color: "#475569", bg: "#F1F5F9" }, ENHANCED_REQUIRED: { label: "Enhanced due diligence", color: "#991B1B", bg: "#FEE2E2" }, VERIFIED: { label: "Verified", color: "#166534", bg: "#DCFCE7" }, DECLINED: { label: "Declined", color: "#475569", bg: "#E2E8F0" },
}

interface Profile { configured: boolean; accountableInstitution: boolean; basis: string | null; complianceOfficerName: string | null; rmcpPolicyId: string | null; rmcpPolicyTitle: string | null; rmcpApprovedOn: string | null; rmcpNextReview: string | null; rmcpReviewOverdue: boolean; updatedByName: string | null }
interface Owner { id: string; ownerName: string; ownershipPercent: number | null; controlBasis: string | null; verified: boolean }
interface Party {
  id: string; reference: string; partyName: string; partyType: string; identifierHint: string | null; riskRating: string; pep: boolean; status: string; sanctionsScreenedOn: string | null; verifiedOn: string | null
  verifiedByName: string | null; seniorApprovalName: string | null; nextReviewDue: string | null; reviewOverdue: boolean; notes: string | null; createdByName: string | null; owners: Owner[]
}
interface Escalation { id: string; reference: string; partyId: string | null; partyLabel: string | null; description: string; raisedByName: string; status: string; decision: string | null; decisionNote: string | null; decidedByName: string | null; decidedAt: string | null; externalReference: string | null }
interface Summary { pending: number; enhancedRequired: number; verified: number; reviewOverdue: number; openEscalations: number }
interface PolicyOpt { id: string; reference: string; title: string }

export default function FicaTab() {
  const { data: profile, isLoading } = useQuery<Profile>({ queryKey: ["lc-fica", "profile"], queryFn: async () => one(await apiClient.get(`${base}/profile`)) })
  const [section, setSection] = useState<"parties" | "escalations" | "profile">("parties")
  if (isLoading || !profile) return <p style={{ color: "#64748B" }}>Loading…</p>
  if (!profile.accountableInstitution) return <ProfileForm profile={profile} intro />
  const sec = (id: typeof section, label: string) => (
    <button onClick={() => setSection(id)} style={{ ...ghostBtn, background: section === id ? "#EEF2FF" : "#fff", color: section === id ? "#4338CA" : "#475569", borderColor: section === id ? "#C7D2FE" : "#E2E8F0" }}>{label}</button>
  )
  return (
    <div>
      <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, padding: "8px 12px", fontSize: 12, color: "#92400E", marginBottom: 12 }}>
        Restricted area. Do not discuss an escalation with the person it concerns: telling them can be an offence. This system records your decisions; it does not rate risk, screen anyone or report to any authority. Your RMCP governs.
      </div>
      {profile.rmcpReviewOverdue && <div style={{ background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, padding: "8px 12px", fontSize: 13, color: "#991B1B", marginBottom: 12 }}>The RMCP review was due {fmtDate(profile.rmcpNextReview)}.</div>}
      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>{sec("parties", "Due diligence")}{sec("escalations", "Escalations")}{sec("profile", "Profile & RMCP")}</div>
      {section === "parties" && <Parties />}
      {section === "escalations" && <Escalations />}
      {section === "profile" && <ProfileForm profile={profile} />}
    </div>
  )
}

function ProfileForm({ profile, intro }: { profile: Profile; intro?: boolean }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ accountable: profile.accountableInstitution, basis: profile.basis ?? "", officer: profile.complianceOfficerName ?? "", policyId: profile.rmcpPolicyId ?? "", approvedOn: profile.rmcpApprovedOn ?? "", nextReview: profile.rmcpNextReview ?? "" })
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")
  const { data: policies = [] } = useQuery<PolicyOpt[]>({ queryKey: ["lc-policies-opts"], enabled: f.accountable, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/policies")) })
  const save = useMutation({
    mutationFn: () => apiClient.put(`${base}/profile`, { accountableInstitution: f.accountable, basis: f.basis.trim(), complianceOfficerName: f.accountable ? f.officer.trim() || null : null, rmcpPolicyId: f.accountable ? f.policyId || null : null, rmcpApprovedOn: f.accountable ? f.approvedOn || null : null, rmcpNextReview: f.accountable ? f.nextReview || null : null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lc-fica"] }); setApiError(""); setNotice("Profile saved") }, onError: (e: unknown) => setApiError(errMsg(e, "Failed to save profile")),
  })
  return (
    <div style={{ maxWidth: 620 }}>
      {intro && <p style={{ fontSize: 14, color: "#475569", marginTop: 0 }}>FICA duties fall on accountable institutions and similar listed persons, not on every business. Nothing FICA-related is switched on until you confirm below that it applies to you, and say why. Your legal adviser should confirm this.</p>}
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      <Err m={apiError} />
      <label style={{ fontSize: 14, display: "flex", gap: 8, marginBottom: 12 }}><input type="checkbox" checked={f.accountable} onChange={e => setF({ ...f, accountable: e.target.checked })} /> We are an accountable institution</label>
      <Field l={`Basis * — why FICA does or does not apply (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={f.basis} onChange={e => setF({ ...f, basis: e.target.value })} /></Field>
      {f.accountable && (<>
        <Field l="Compliance officer *"><input style={input} value={f.officer} onChange={e => setF({ ...f, officer: e.target.value })} /></Field>
        <Field l="RMCP document (a policy in the Policies tab)"><select style={input} value={f.policyId} onChange={e => setF({ ...f, policyId: e.target.value })}><option value="">Not linked</option>{policies.map(p => <option key={p.id} value={p.id}>{p.reference} — {p.title}</option>)}</select></Field>
        <div style={{ display: "flex", gap: 12 }}>
          <div style={{ flex: 1 }}><Field l="RMCP last approved"><input type="date" style={input} value={f.approvedOn} onChange={e => setF({ ...f, approvedOn: e.target.value })} /></Field></div>
          <div style={{ flex: 1 }}><Field l="RMCP next review"><input type="date" style={input} value={f.nextReview} onChange={e => setF({ ...f, nextReview: e.target.value })} /></Field></div>
        </div>
      </>)}
      <button style={primaryBtn} disabled={save.isPending || f.basis.trim().length < MIN || (f.accountable && !f.officer.trim())} onClick={() => save.mutate()}>Save profile</button>
    </div>
  )
}

// ═══ Parties ═══
const blankParty = { id: "", partyName: "", partyType: "INDIVIDUAL", identifierHint: "", riskRating: "LOW", pep: false, notes: "" }
type PAct = { p: Party; kind: "screened" | "verify" | "reopen" | "decline"; a: string }
const blankOwner = { name: "", pct: "", control: "" }

function Parties() {
  const qc = useQueryClient()
  const { fullName } = useAuth()
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<typeof blankParty | null>(null)
  const [act, setAct] = useState<PAct | null>(null)
  const [ownerFor, setOwnerFor] = useState<{ p: Party; id: string; v: typeof blankOwner } | null>(null)
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")
  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-fica", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: parties = [], isLoading } = useQuery<Party[]>({ queryKey: ["lc-fica", "parties"], queryFn: async () => unwrap(await apiClient.get(`${base}/parties`)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-fica"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }
  const save = useMutation({
    mutationFn: (f: typeof blankParty) => { const body = { partyName: f.partyName.trim(), partyType: f.partyType, identifierHint: f.identifierHint.trim() || null, riskRating: f.riskRating, pep: f.pep, notes: f.notes.trim() || null }; return f.id ? apiClient.put(`${base}/parties/${f.id}`, body) : apiClient.post(`${base}/parties`, body) },
    onSuccess: ok("Party saved", () => setForm(null)), onError: fail("Failed to save party"),
  })
  const run = useMutation({
    mutationFn: (a: PAct) => {
      const id = a.p.id
      switch (a.kind) {
        case "screened": return apiClient.post(`${base}/parties/${id}/screened`, { screenedOn: a.a })
        case "verify": return apiClient.post(`${base}/parties/${id}/verify`, { seniorApprover: a.a.trim() || null })
        case "reopen": return apiClient.post(`${base}/parties/${id}/reopen`, { reason: a.a.trim() })
        case "decline": return apiClient.post(`${base}/parties/${id}/decline`, { reason: a.a.trim() })
      }
    }, onSuccess: ok("Saved", () => setAct(null)), onError: fail("Action failed"),
  })
  const saveOwner = useMutation({
    mutationFn: (o: NonNullable<typeof ownerFor>) => { const body = { ownerName: o.v.name.trim(), ownershipPercent: o.v.pct ? +o.v.pct : null, controlBasis: o.v.control.trim() || null }; return o.id ? apiClient.put(`${base}/parties/${o.p.id}/owners/${o.id}`, body) : apiClient.post(`${base}/parties/${o.p.id}/owners`, body) },
    onSuccess: ok("Owner saved", () => setOwnerFor(null)), onError: fail("Failed to save owner"),
  })
  const ownerAct = useMutation({
    mutationFn: ({ p, o, kind }: { p: Party; o: Owner; kind: "verify" | "remove" }) => kind === "verify" ? apiClient.post(`${base}/parties/${p.id}/owners/${o.id}/verify`) : apiClient.delete(`${base}/parties/${p.id}/owners/${o.id}`),
    onSuccess: ok("Updated"), onError: fail("Failed to update owner"),
  })
  const set = (k: keyof typeof blankParty, v: string | boolean) => setForm(f => f && { ...f, [k]: v })
  const titles = { screened: "Record sanctions screening", verify: "Verify party", reopen: "Reopen for review", decline: "Decline party" }
  const needsSenior = (p: Party) => p.riskRating === "HIGH" || p.pep

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {([["Pending", summary?.pending, undefined], ["Enhanced due diligence", summary?.enhancedRequired, "#991B1B"], ["Verified", summary?.verified, "#166534"], ["Review overdue", summary?.reviewOverdue, summary?.reviewOverdue ? "#991B1B" : undefined], ["Open escalations", summary?.openEscalations, "#92400E"]] as [string, number | undefined, string | undefined][]).map(([l, v, c]) => (
          <div key={l} style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}><div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{l}</div><div style={{ fontSize: 24, fontWeight: 800, color: c ?? "#0F172A" }}>{v ?? "—"}</div></div>
        ))}
      </div>
      <button style={{ ...primaryBtn, marginBottom: 12 }} onClick={() => { setApiError(""); setForm({ ...blankParty }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Onboard party</button>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !form && !act && !ownerFor && <Err m={apiError} />}
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : parties.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No parties yet.</p> : parties.map(p => {
        const open = expanded === p.id
        const editableOwners = p.status === "PENDING" || p.status === "ENHANCED_REQUIRED"
        return (
          <div key={p.id} style={{ border: `1px solid ${p.reviewOverdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : p.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{p.reference} — {p.partyName}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{pretty(p.partyType)}{p.identifierHint ? ` · …${p.identifierHint}` : ""}{p.nextReviewDue ? ` · review ${fmtDate(p.nextReviewDue)}` : ""}</div></div>
              {p.reviewOverdue && <Badge text="Review overdue" color="#991B1B" bg="#FEE2E2" />}{p.pep && <Badge text="PEP" color="#6D28D9" bg="#EDE9FE" />}
              <Badge text={`Risk ${p.riskRating}`} {...RISK[p.riskRating]} /><Badge text={PSTATUS[p.status].label} color={PSTATUS[p.status].color} bg={PSTATUS[p.status].bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <p style={{ marginTop: 0, fontSize: 12 }}>Onboarded by {p.createdByName ?? "—"} · sanctions screened {p.sanctionsScreenedOn ? fmtDate(p.sanctionsScreenedOn) : "not yet"}{p.verifiedOn ? ` · verified ${fmtDate(p.verifiedOn)} by ${p.verifiedByName}` : ""}{p.seniorApprovalName ? ` · senior approval ${p.seniorApprovalName}` : ""}</p>
                {p.notes && <p style={{ whiteSpace: "pre-wrap" }}>{p.notes}</p>}
                {(p.partyType === "COMPANY" || p.partyType === "TRUST") && (
                  <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, padding: 10, marginBottom: 8 }}>
                    <div style={{ fontWeight: 700, marginBottom: 4 }}>Beneficial owners</div>
                    {p.owners.length === 0 && <div style={{ color: "#92400E" }}>None recorded. A company or trust cannot be verified without them.</div>}
                    {p.owners.map(o => (
                      <div key={o.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "2px 0" }}>
                        <span style={{ flex: 1 }}>{o.ownerName} — {o.ownershipPercent != null ? `${o.ownershipPercent}%` : o.controlBasis}</span>
                        <Badge text={o.verified ? "Verified" : "Not verified"} color={o.verified ? "#166534" : "#92400E"} bg={o.verified ? "#DCFCE7" : "#FEF3C7"} />
                        {editableOwners && <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => setOwnerFor({ p, id: o.id, v: { name: o.ownerName, pct: o.ownershipPercent?.toString() ?? "", control: o.controlBasis ?? "" } })}>Edit</button>}
                        {editableOwners && !o.verified && <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => ownerAct.mutate({ p, o, kind: "verify" })}>Verify</button>}
                        {editableOwners && <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => { if (confirm(`Remove ${o.ownerName}?`)) ownerAct.mutate({ p, o, kind: "remove" }) }}>Remove</button>}
                      </div>
                    ))}
                    {editableOwners && <button style={{ ...ghostBtn, marginTop: 6, fontSize: 12, padding: "4px 10px" }} onClick={() => setOwnerFor({ p, id: "", v: { ...blankOwner } })}>Add owner</button>}
                    {!editableOwners && <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>Owners are locked while the party is {p.status.toLowerCase()}. Reopen it to change them.</div>}
                  </div>
                )}
                {apiError && <Err m={apiError} />}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {p.status !== "DECLINED" && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: p.id, partyName: p.partyName, partyType: p.partyType, identifierHint: p.identifierHint ?? "", riskRating: p.riskRating, pep: p.pep, notes: p.notes ?? "" }) }}>Edit</button>}
                  {p.status !== "DECLINED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ p, kind: "screened", a: today() }) }}>Record screening</button>}
                  {editableOwners && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ p, kind: "verify", a: "" }) }}>Verify</button>}
                  {p.status === "VERIFIED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ p, kind: "reopen", a: "" }) }}>Reopen for review</button>}
                  {p.status !== "DECLINED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAct({ p, kind: "decline", a: "" }) }}>Decline</button>}
                </div>
                {editableOwners && <p style={{ fontSize: 12, color: "#64748B" }}>You cannot verify a party you onboarded{fullName ? ` (signed in as ${fullName})` : ""}.</p>}
              </div>
            )}
          </div>
        )
      })}

      {form && (
        <Modal title={form.id ? "Edit party" : "Onboard party"} onClose={() => setForm(null)}>
          <Err m={apiError} />
          <Field l="Name *"><input style={input} value={form.partyName} onChange={e => set("partyName", e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type *"><select style={input} value={form.partyType} onChange={e => set("partyType", e.target.value)}>{["INDIVIDUAL", "COMPANY", "TRUST", "OTHER"].map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Risk rating * (your RMCP decides)"><select style={input} value={form.riskRating} onChange={e => set("riskRating", e.target.value)}>{["LOW", "MEDIUM", "HIGH"].map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
          </div>
          <Field l="Last digits of ID / registration number (max 8)"><input style={input} maxLength={8} value={form.identifierHint} onChange={e => set("identifierHint", e.target.value)} /></Field>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: -6 }}>Do not enter a full identity number here.</p>
          <label style={{ fontSize: 13, display: "flex", gap: 6, marginBottom: 8 }}><input type="checkbox" checked={form.pep} onChange={e => set("pep", e.target.checked)} /> Politically exposed person (or close associate)</label>
          <Field l="Notes"><textarea style={{ ...input, minHeight: 50 }} value={form.notes} onChange={e => set("notes", e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button><button style={primaryBtn} disabled={save.isPending || !form.partyName.trim()} onClick={() => save.mutate(form)}>Save</button></div>
        </Modal>
      )}
      {ownerFor && (
        <Modal title={`${ownerFor.id ? "Edit" : "Add"} beneficial owner — ${ownerFor.p.partyName}`} onClose={() => setOwnerFor(null)}>
          <Err m={apiError} />
          <Field l="Name *"><input style={input} value={ownerFor.v.name} onChange={e => setOwnerFor({ ...ownerFor, v: { ...ownerFor.v, name: e.target.value } })} /></Field>
          <Field l="Ownership %"><input type="number" min={0} max={100} step="0.01" style={input} value={ownerFor.v.pct} onChange={e => setOwnerFor({ ...ownerFor, v: { ...ownerFor.v, pct: e.target.value } })} /></Field>
          <Field l="How control is exercised (required if no percentage)"><input style={input} value={ownerFor.v.control} onChange={e => setOwnerFor({ ...ownerFor, v: { ...ownerFor.v, control: e.target.value } })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setOwnerFor(null)}>Cancel</button>
            <button style={primaryBtn} disabled={saveOwner.isPending || !ownerFor.v.name.trim() || (!ownerFor.v.pct && !ownerFor.v.control.trim())} onClick={() => saveOwner.mutate(ownerFor)}>Save</button></div>
        </Modal>
      )}
      {act && (
        <Modal title={`${titles[act.kind]} — ${act.p.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          {act.kind === "screened" && <Field l="Screened on *"><input type="date" style={input} max={today()} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>}
          {act.kind === "verify" && (<>
            <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Needs a sanctions screening on record{act.p.partyType === "COMPANY" || act.p.partyType === "TRUST" ? " and every beneficial owner verified" : ""}. The next review is set from the risk rating.</p>
            {needsSenior(act.p) && <Field l="Senior management approver * (not you)"><input style={input} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>}
          </>)}
          {(act.kind === "reopen" || act.kind === "decline") && <Field l={`Reason * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={act.a} onChange={e => setAct({ ...act, a: e.target.value })} /></Field>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button>
            <button style={primaryBtn} disabled={run.isPending || (act.kind === "screened" && !act.a) || (act.kind === "verify" && needsSenior(act.p) && !act.a.trim()) || ((act.kind === "reopen" || act.kind === "decline") && act.a.trim().length < MIN)} onClick={() => run.mutate(act)}>{titles[act.kind]}</button></div>
        </Modal>
      )}
    </div>
  )
}

// ═══ Escalations ═══
function Escalations() {
  const qc = useQueryClient()
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<{ partyId: string; description: string } | null>(null)
  const [act, setAct] = useState<{ e: Escalation; decision: string; note: string; ref: string } | null>(null)
  const [apiError, setApiError] = useState("")
  const { data: items = [], isLoading } = useQuery<Escalation[]>({ queryKey: ["lc-fica", "escalations"], queryFn: async () => unwrap(await apiClient.get(`${base}/escalations`)) })
  const { data: parties = [] } = useQuery<Party[]>({ queryKey: ["lc-fica", "parties"], enabled: !!form, queryFn: async () => unwrap(await apiClient.get(`${base}/parties`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-fica"] }); setApiError("") }
  const fail = (m: string) => (e: unknown) => setApiError(errMsg(e, m))
  const raise = useMutation({ mutationFn: (f: NonNullable<typeof form>) => apiClient.post(`${base}/escalations`, { partyId: f.partyId || null, description: f.description.trim() }), onSuccess: () => { refresh(); setForm(null) }, onError: fail("Failed to raise escalation") })
  const review = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/escalations/${id}/review`), onSuccess: refresh, onError: fail("Failed to start review") })
  const decide = useMutation({ mutationFn: (a: NonNullable<typeof act>) => apiClient.post(`${base}/escalations/${a.e.id}/decide`, { decision: a.decision, note: a.note.trim(), externalReference: a.ref.trim() || null }), onSuccess: () => { refresh(); setAct(null) }, onError: fail("Failed to record decision") })
  const STAT: Record<string, { label: string; color: string; bg: string }> = { RAISED: { label: "Raised", color: "#991B1B", bg: "#FEE2E2" }, UNDER_REVIEW: { label: "Under review", color: "#92400E", bg: "#FEF3C7" }, DECIDED: { label: "Decided", color: "#166534", bg: "#DCFCE7" } }

  return (
    <div>
      <button style={{ ...primaryBtn, marginBottom: 12 }} onClick={() => { setApiError(""); setForm({ partyId: "", description: "" }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Raise escalation</button>
      {apiError && !form && !act && <Err m={apiError} />}
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : items.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No escalations.</p> : items.map(e => {
        const open = expanded === e.id
        return (
          <div key={e.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : e.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1 }}><div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{e.reference}{e.partyLabel ? ` — ${e.partyLabel}` : ""}</div><div style={{ fontSize: 12, color: "#64748B" }}>Raised by {e.raisedByName}</div></div>
              {e.decision && <Badge text={pretty(e.decision)} color="#1D4ED8" bg="#DBEAFE" />}<Badge text={STAT[e.status].label} color={STAT[e.status].color} bg={STAT[e.status].bg} />{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                <p style={{ marginTop: 0, whiteSpace: "pre-wrap" }}>{e.description}</p>
                {e.decision && <p><b>Decision by {e.decidedByName} ({fmtDateTime(e.decidedAt)}):</b> {e.decisionNote}{e.externalReference ? ` · external reference ${e.externalReference}` : ""}</p>}
                {apiError && <Err m={apiError} />}
                <div style={{ display: "flex", gap: 8 }}>
                  {e.status === "RAISED" && <button style={primaryBtn} disabled={review.isPending} onClick={() => review.mutate(e.id)}>Start review</button>}
                  {e.status === "UNDER_REVIEW" && <button style={primaryBtn} onClick={() => { setApiError(""); setAct({ e, decision: "NOT_REPORTED", note: "", ref: "" }) }}>Record decision</button>}
                </div>
                {e.status === "UNDER_REVIEW" && <p style={{ fontSize: 12, color: "#64748B" }}>You cannot decide an escalation you raised.</p>}
              </div>
            )}
          </div>
        )
      })}
      {form && (
        <Modal title="Raise escalation" onClose={() => setForm(null)}>
          <Err m={apiError} />
          <Field l="Related party (optional)"><select style={input} value={form.partyId} onChange={e => setForm({ ...form, partyId: e.target.value })}><option value="">None</option>{parties.map(p => <option key={p.id} value={p.id}>{p.reference} — {p.partyName}</option>)}</select></Field>
          <Field l={`What was observed * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 90 }} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button><button style={primaryBtn} disabled={raise.isPending || form.description.trim().length < MIN} onClick={() => raise.mutate(form)}>Raise</button></div>
        </Modal>
      )}
      {act && (
        <Modal title={`Decision — ${act.e.reference}`} onClose={() => setAct(null)}>
          <Err m={apiError} />
          <Field l="Decision *"><select style={input} value={act.decision} onChange={e => setAct({ ...act, decision: e.target.value })}><option value="NOT_REPORTED">Not reported</option><option value="REPORTED">Reported through the proper channel</option></select></Field>
          <Field l={`Reasons * (at least ${MIN} characters)`}><textarea style={{ ...input, minHeight: 70 }} value={act.note} onChange={e => setAct({ ...act, note: e.target.value })} /></Field>
          {act.decision === "REPORTED" && <Field l="Reference you received (if any)"><input style={input} value={act.ref} onChange={e => setAct({ ...act, ref: e.target.value })} /></Field>}
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>This records your decision. Any report is made outside this system.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={() => setAct(null)}>Cancel</button><button style={primaryBtn} disabled={decide.isPending || act.note.trim().length < MIN} onClick={() => decide.mutate(act)}>Record decision</button></div>
        </Modal>
      )}
    </div>
  )
}
