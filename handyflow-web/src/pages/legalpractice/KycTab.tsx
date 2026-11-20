// Client due diligence (FICA intake): who the client is, who owns and controls them, how risky they are, and when it is next checked.
// A client is prepared, submitted, then approved by a second person. Senior management approve high-risk and politically exposed clients.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Field, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, inp, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import {
  KYC_LEVELS, KYC_METHODS, KYC_PEP, KYC_SANCTIONS, KYC_STATUS, assessmentProblem, assessmentRequest, canOpenReview, emptyKycItem, emptyKycParty, isoDay, kycActions, kycApproveProblem,
  kycItemProblem, kycItemRequest, kycKindLabel, kycKindsFor, kycNextStep, kycPartyProblem, kycPartyRequest, kycReturnProblem, kycRoleLabel, kycRolesFor, kycSettingProblem, kycSubjectLabel,
  kycSubjectsFor, levelLabel, liveRisk, needsSource, profileToAssessment, reviewText, riskTone, splitCycles,
  type KycAssessmentForm, type KycItemForm, type KycOverviewRow, type KycPartyForm, type KycProfile, type KycSettings,
} from "./legal.logic"

const URL = `${BASE}/kyc`
const unwrap = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const levelWord = (l: string | null) => (l ? l.charAt(0) + l.slice(1).toLowerCase() : "")

export default function KycTab() {
  const [page, setPage] = useState(0), [open, setOpen] = useState<KycOverviewRow | null>(null)
  const canSettings = usePermissions(["LEGALPRACTICE_KYC_SETTINGS", "LEGALPRACTICE_KYC_APPROVE", "LEGALPRACTICE_ADMIN"])
  const list = useQuery({ queryKey: ["lp-kyc-overview", page], queryFn: async () => pageOf<KycOverviewRow>(await apiClient.get(`${URL}/overview`, { params: { page, size: 20 } })) })
  const today = isoDay(new Date())
  const rows = list.data?.content ?? []
  if (open) return <ClientView row={open} onBack={() => setOpen(null)} />
  return (
    <div>
      <SectionTitle>Client due diligence</SectionTitle>
      <div style={{ ...muted, marginBottom: 10 }}>The law asks the firm to know who it acts for: identity, address, who owns and controls a company or trust, whether anyone is politically exposed, and a risk rating that sets how often it is checked again. One person prepares it and a different person approves it.</div>
      {canSettings && <Enforcement />}
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <Empty>Could not load the clients.</Empty> : list.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No clients yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Client</th><th style={th}>Status</th><th style={th}>Risk</th><th style={th}>Review</th><th style={th} /></tr></thead>
            <tbody>{rows.map(r => {
              const s = KYC_STATUS[r.status] ?? { label: r.status, tone: "neutral" as const }
              return (
                <tr key={r.clientId}>
                  <td style={td}><b>{r.clientName}</b><div style={muted}>{r.clientType === "INDIVIDUAL" ? "Individual" : "Company, trust or partnership"}</div></td>
                  <td style={td}><Pill tone={s.tone}>{s.label}</Pill>{r.reviewOpen && <div style={muted}>Review in progress</div>}</td>
                  <td style={td}>{r.riskRating ? <Pill tone={riskTone(r.riskRating)}>{levelWord(r.riskRating)}</Pill> : <span style={muted}>—</span>}</td>
                  <td style={td}>{r.validUntil ? <>{day(r.validUntil)}<div style={muted}>{reviewText(r.validUntil, today)}</div></> : <span style={muted}>—</span>}</td>
                  <td style={{ ...td, textAlign: "right" }}><Btn small onClick={() => setOpen(r)}>{r.status === "NOT_STARTED" ? "Start" : "Open"}</Btn></td>
                </tr>)
            })}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />
    </div>
  )
}

// ---- the firm's decision to block work until a client is verified -----------------------------------------------------

function Enforcement() {
  const qc = useQueryClient()
  const canChange = usePermissions(["LEGALPRACTICE_KYC_SETTINGS", "LEGALPRACTICE_ADMIN"])
  const [changing, setChanging] = useState(false), [showHistory, setShowHistory] = useState(false)
  const q = useQuery({ queryKey: ["lp-kyc-settings"], queryFn: async () => unwrap<KycSettings>(await apiClient.get(`${URL}/settings`)) })
  const s = q.data
  return (
    <div style={{ ...card, marginBottom: 12, padding: 12 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
        <div>
          <b>Blocking until a client is verified: {q.isLoading ? "…" : s?.enforce ? "on" : "off"}</b>
          <div style={muted}>{s?.enforce ? "A matter cannot be opened, and money cannot be received into trust, for a client who is not verified or is past their review date." : "Matters can be opened and trust money received for clients who are not verified. Switch this on once your firm's risk management and compliance programme requires it."}</div>
        </div>
        <div style={{ display: "flex", gap: 6 }}>
          <Btn small onClick={() => setShowHistory(v => !v)}>{showHistory ? "Hide history" : "History"}</Btn>
          {canChange && s && <Btn small kind="primary" onClick={() => setChanging(true)}>{s.enforce ? "Switch off" : "Switch on"}</Btn>}
        </div>
      </div>
      {showHistory && (s?.history.length ? s.history.map((h, i) => (
        <div key={i} style={{ ...muted, marginTop: 6 }}>{day(h.changedAt)}: switched {h.enforce ? "on" : "off"} by {h.changedByName ?? "—"}. {h.reason}</div>)) : <div style={{ ...muted, marginTop: 6 }}>No changes yet.</div>)}
      {changing && s && <SettingDialog current={s.enforce} onClose={() => setChanging(false)} onDone={() => { qc.invalidateQueries({ queryKey: ["lp-kyc-settings"] }); setChanging(false) }} />}
    </div>
  )
}

function SettingDialog({ current, onClose, onDone }: { current: boolean; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState(""), [err, setErr] = useState("")
  const next = !current, problem = kycSettingProblem(next, current, reason)
  const save = useMutation({
    mutationFn: () => apiClient.put(`${URL}/settings`, { enforce: next, reason: reason.trim() }),
    onSuccess: () => { toast.success(next ? "Blocking switched on" : "Blocking switched off"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not save the setting")),
  })
  return (
    <Dialog title={next ? "Switch blocking on" : "Switch blocking off"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <div style={muted}>{next ? "From now on a matter cannot be opened, and money cannot be received into trust, for a client who is not verified or is past their review date. Payments and refunds out of trust are not affected." : "Matters and trust receipts will no longer be held back for due diligence."} The change and your reason are kept permanently.</div>
      <div style={{ marginTop: 10 }}><Input label="Why" value={reason} onChange={setReason} hint="For example: resolved by the partners on 1 December. At least 10 characters." /></div>
      {problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ---- one client ----------------------------------------------------------------------------------------------------------

function ClientView({ row, onBack }: { row: KycOverviewRow; onBack: () => void }) {
  const qc = useQueryClient()
  const canPrepare = usePermissions(["LEGALPRACTICE_KYC_PREPARE", "LEGALPRACTICE_ADMIN"])
  const [starting, setStarting] = useState(false), [err, setErr] = useState("")
  const q = useQuery({ queryKey: ["lp-kyc-client", row.clientId], queryFn: async () => unwrap<KycProfile[]>(await apiClient.get(`${URL}/clients/${row.clientId}`)) })
  const cycles = q.data ?? []
  const { current, earlier } = splitCycles(cycles)
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lp-kyc-client", row.clientId] }); qc.invalidateQueries({ queryKey: ["lp-kyc-overview"] }) }
  const review = useMutation({
    mutationFn: () => apiClient.post(`${URL}/clients/${row.clientId}/review`),
    onSuccess: () => { toast.success("Review opened"); refresh() },
    onError: async e => setErr(await errorText(e, "Could not open a review")),
  })
  return (
    <div>
      <SectionTitle right={<Btn small onClick={onBack}>← All clients</Btn>}>{row.clientName}</SectionTitle>
      {q.isError ? <Empty>Could not load this client's due diligence.</Empty> : q.isLoading ? <Empty>Loading…</Empty> : !current ? (
        <div style={card}>
          <div style={{ marginBottom: 8 }}>Due diligence has not been started for this client.</div>
          {canPrepare ? <Btn kind="primary" onClick={() => setStarting(true)}>Start due diligence</Btn> : <div style={muted}>You do not have permission to start it.</div>}
        </div>
      ) : (
        <>
          {canOpenReview(cycles, canPrepare) && <div style={{ marginBottom: 10 }}><Btn onClick={() => { setErr(""); review.mutate() }} disabled={review.isPending}>Open a review</Btn><span style={{ ...muted, marginLeft: 8 }}>Starts a new cycle from this one. Every check is done again.</span></div>}
          {current.status === "VERIFIED" && current.validUntil && current.validUntil < isoDay(new Date()) && canPrepare && <div style={{ marginBottom: 10 }}><Btn kind="primary" onClick={() => setStarting(true)}>Start the review</Btn></div>}
          {err && <ErrorLine text={err} />}
          <Cycle key={current.id} profile={current} onChanged={refresh} />
          {earlier.length > 0 && (
            <div style={{ marginTop: 18 }}>
              <div style={{ fontWeight: 700, marginBottom: 6 }}>Earlier cycles</div>
              <div style={{ ...card, padding: 0, overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr><th style={th}>Started</th><th style={th}>Risk</th><th style={th}>Approved by</th><th style={th}>Valid until</th></tr></thead>
                  <tbody>{earlier.map(p => (
                    <tr key={p.id}><td style={td}>{day(p.startedAt)}</td><td style={td}>{p.riskRating ? levelWord(p.riskRating) : "—"}</td><td style={td}>{p.approvedByName ?? "—"}{p.selfApproved ? " (own work)" : ""}</td><td style={td}>{p.validUntil ? day(p.validUntil) : "—"}</td></tr>))}</tbody>
                </table>
              </div>
            </div>)}
        </>
      )}
      {starting && <StartDialog row={row} onClose={() => setStarting(false)} onDone={() => { setStarting(false); refresh() }} />}
    </div>
  )
}

function StartDialog({ row, onClose, onDone }: { row: KycOverviewRow; onClose: () => void; onDone: () => void }) {
  const kinds = kycSubjectsFor(row.clientType)
  const [kind, setKind] = useState(kinds[0][0]), [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(URL, { clientId: row.clientId, subjectKind: kind }),
    onSuccess: () => { toast.success("Due diligence started"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not start")),
  })
  return (
    <Dialog title="Start due diligence" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Starting…" : "Start"}</Btn></>}>
      <div style={muted}>If this client was verified before, the new cycle starts from the last one and every check is done again.</div>
      {kinds.length > 1 ? <Pick label="This client is a" value={kind} onChange={setKind} options={kinds} /> : <div style={{ marginTop: 8 }}>Verified as: <b>{kycSubjectLabel(kind)}</b></div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ---- one cycle ---------------------------------------------------------------------------------------------------------

function Cycle({ profile: p, onChanged }: { profile: KycProfile; onChanged: () => void }) {
  const me = useAuthStore(s => s.user?.userId)
  const canPrepare = usePermissions(["LEGALPRACTICE_KYC_PREPARE", "LEGALPRACTICE_ADMIN"])
  const canApprove = usePermissions(["LEGALPRACTICE_KYC_APPROVE", "LEGALPRACTICE_KYC_APPROVE_SENIOR", "LEGALPRACTICE_ADMIN"])
  const canSenior = usePermissions(["LEGALPRACTICE_KYC_APPROVE_SENIOR", "LEGALPRACTICE_ADMIN"])
  const today = isoDay(new Date())
  const a = kycActions(p, { prepare: canPrepare, approve: canApprove, senior: canSenior }, me)
  const next = kycNextStep(p, today)
  const [dialog, setDialog] = useState<"party" | "item" | "approve" | "return" | null>(null), [err, setErr] = useState("")
  const call = (fn: () => Promise<unknown>, ok: string) => async () => { setErr(""); try { await fn(); toast.success(ok); onChanged() } catch (e) { setErr(await errorText(e, "That did not work")) } }
  const submit = useMutation({ mutationFn: () => apiClient.post(`${URL}/${p.id}/submit`), onSuccess: () => { toast.success("Submitted for approval"); onChanged() }, onError: async e => setErr(await errorText(e, "Could not submit")) })
  const status = KYC_STATUS[p.status] ?? { label: p.status, tone: "neutral" as const }
  const roles = kycRolesFor(p.subjectKind)
  return (
    <div>
      <div style={{ ...card, marginBottom: 12 }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <Pill tone={status.tone}>{status.label}</Pill>
          <Pill tone="neutral">{kycSubjectLabel(p.subjectKind)}</Pill>
          {p.riskRating && <Pill tone={riskTone(p.riskRating)}>{levelWord(p.riskRating)} risk</Pill>}
          {p.riskRating && <span style={muted}>{levelLabel(p.dueDiligenceLevel)}, reviewed every {p.reviewMonths} months</span>}
        </div>
        <div style={{ marginTop: 8 }}><Pill tone={next.tone}>{next.text}</Pill></div>
        {p.status === "VERIFIED" && <div style={{ ...muted, marginTop: 6 }}>Approved by {p.approvedByName ?? "—"} on {day(p.approvedAt)}{p.seniorApproved ? " (senior management)" : ""}{p.selfApproved ? ". Approved by the person who prepared it, with this reason: " + (p.approvalNote ?? "") : p.approvalNote ? `. ${p.approvalNote}` : ""}</div>}
        {p.status === "SUBMITTED" && <div style={{ ...muted, marginTop: 6 }}>Submitted by {p.submittedByName ?? "—"} on {day(p.submittedAt)}.</div>}
        {a.approveHint && <div style={{ ...muted, marginTop: 6 }}>{a.approveHint}</div>}
      </div>
      {err && <ErrorLine text={err} />}

      {p.status === "DRAFT" && p.missing.length > 0 && (
        <div style={{ ...card, marginBottom: 12 }}>
          <div style={{ fontWeight: 700, marginBottom: 4 }}>Still to do</div>
          <ul style={{ margin: 0, paddingLeft: 18 }}>{p.missing.map((m, i) => <li key={i} style={{ fontSize: 13 }}>{m}</li>)}</ul>
        </div>)}

      <Assessment profile={p} editable={a.canEdit} onSaved={onChanged} today={today} />

      {roles.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <SectionTitle right={a.canEdit && <Btn small onClick={() => setDialog("party")}>Add a person</Btn>}>People</SectionTitle>
          <div style={{ ...card, padding: 0, overflowX: "auto" }}>
            {p.parties.length === 0 ? <Empty>No one recorded yet.</Empty> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Name</th><th style={th}>Role</th><th style={th}>Identity verified</th><th style={th} /></tr></thead>
                <tbody>{p.parties.map(x => (
                  <tr key={x.id}>
                    <td style={td}><b>{x.name}</b>{x.idNumber && <div style={muted}>{x.idNumber}</div>}</td>
                    <td style={td}>{kycRoleLabel(x.role)}{x.role === "BENEFICIAL_OWNER" && <div style={muted}>{x.method === "OWNERSHIP" ? `Holds ${x.percent}%` : x.method === "CONTROL" ? "Found by control" : "Found by management"}</div>}</td>
                    <td style={td}>{x.verifiedOn ? <>{day(x.verifiedOn)}<div style={muted}>{x.verifiedByName ?? ""}</div></> : <Pill tone="warning">Not yet</Pill>}</td>
                    <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{a.canEdit && <>
                      {!x.verifiedOn && <Btn small onClick={call(() => apiClient.post(`${URL}/${p.id}/parties/${x.id}/verify`, { verifiedOn: today }), "Marked as verified today")}>Verified today</Btn>}{" "}
                      <Btn small onClick={call(() => apiClient.delete(`${URL}/${p.id}/parties/${x.id}`), "Removed")}>Remove</Btn></>}</td>
                  </tr>))}</tbody>
              </table>)}
          </div>
        </div>)}

      <div style={{ marginTop: 16 }}>
        <SectionTitle right={a.canEdit && <Btn small onClick={() => setDialog("item")}>Record a document</Btn>}>Documents checked</SectionTitle>
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {p.items.length === 0 ? <Empty>Nothing recorded yet.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Document</th><th style={th}>For</th><th style={th}>Checked</th><th style={th} /></tr></thead>
              <tbody>{p.items.map(i => (
                <tr key={i.id}>
                  <td style={td}><b>{kycKindLabel(i.kind)}</b><div style={muted}>{i.description}{i.documentRef ? ` (${i.documentRef})` : ""}</div></td>
                  <td style={td}>{i.partyId ? p.parties.find(x => x.id === i.partyId)?.name ?? "—" : "The client"}</td>
                  <td style={td}>{day(i.verifiedOn)}<div style={muted}>{i.verifiedByName ?? ""}</div></td>
                  <td style={{ ...td, textAlign: "right" }}>{a.canEdit && <Btn small onClick={call(() => apiClient.delete(`${URL}/${p.id}/items/${i.id}`), "Removed")}>Remove</Btn>}</td>
                </tr>))}</tbody>
            </table>)}
        </div>
      </div>

      <div style={{ marginTop: 16, display: "flex", gap: 8, flexWrap: "wrap" }}>
        {a.canEdit && <Btn kind="primary" disabled={!a.canSubmit || submit.isPending} title={a.canSubmit ? undefined : "Finish the list above first"} onClick={() => { setErr(""); submit.mutate() }}>{submit.isPending ? "Submitting…" : "Submit for approval"}</Btn>}
        {a.canApprove && <Btn kind="primary" onClick={() => setDialog("approve")}>Approve</Btn>}
        {a.canReturn && <Btn onClick={() => setDialog("return")}>Return to preparer</Btn>}
      </div>

      {dialog === "party" && <PartyDialog profile={p} today={today} onClose={() => setDialog(null)} onDone={() => { setDialog(null); onChanged() }} />}
      {dialog === "item" && <ItemDialog profile={p} today={today} onClose={() => setDialog(null)} onDone={() => { setDialog(null); onChanged() }} />}
      {dialog === "approve" && <ApproveDialog profile={p} selfSubmitted={a.selfSubmitted} onClose={() => setDialog(null)} onDone={() => { setDialog(null); onChanged() }} />}
      {dialog === "return" && <ReturnDialog profile={p} onClose={() => setDialog(null)} onDone={() => { setDialog(null); onChanged() }} />}
    </div>
  )
}

// ---- the assessment: risk, politically exposed, sanctions, business, source of funds ----------------------------------------

function Assessment({ profile: p, editable, onSaved, today }: { profile: KycProfile; editable: boolean; onSaved: () => void; today: string }) {
  const [f, setF] = useState<KycAssessmentForm>(() => profileToAssessment(p)), [err, setErr] = useState("")
  const set = (k: keyof KycAssessmentForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const rating = liveRisk(f), problem = assessmentProblem(f, today)
  const save = useMutation({
    mutationFn: () => apiClient.put(`${URL}/${p.id}/assessment`, assessmentRequest(f)),
    onSuccess: () => { toast.success("Saved"); onSaved() },
    onError: async e => setErr(await errorText(e, "Could not save")),
  })
  const lock = !editable
  const levels = [...KYC_LEVELS] as [string, string][]
  const sel = (label: string, k: keyof KycAssessmentForm) => <Pick label={label} value={f[k]} onChange={set(k)} blank={lock ? "—" : "Choose"} options={levels} />
  return (
    <div style={card}>
      <div style={{ fontWeight: 700, marginBottom: 8 }}>Risk assessment {rating && <Pill tone={riskTone(rating)}>{levelWord(rating)}</Pill>}</div>
      <fieldset disabled={lock} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "0 14px" }}>
          {sel("Country the client or funds are linked to", "geography")}
          {sel("Service the firm provides", "service")}
          {sel("The client themselves", "client")}
          {sel("How the client deals with the firm", "channel")}
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0 14px" }}>
          <Pick label="Politically exposed person" value={f.pep} onChange={set("pep")} blank="Choose" options={KYC_PEP} />
          <Input label="Date checked" type="date" value={f.pepCheckedOn} onChange={set("pepCheckedOn")} max={today} />
          <Pick label="Sanctions screening" value={f.sanctionsResult} onChange={set("sanctionsResult")} blank="Choose" options={KYC_SANCTIONS} />
          <Input label="Date screened" type="date" value={f.sanctionsCheckedOn} onChange={set("sanctionsCheckedOn")} max={today} />
        </div>
        <Input label="Note on the politically exposed check" value={f.pepNote} onChange={set("pepNote")} hint="Include family members and close associates." />
        <Field label="Business of the client, or the purpose of the relationship"><textarea style={{ ...inp, minHeight: 56 }} value={f.businessNature} onChange={e => set("businessNature")(e.target.value)} /></Field>
        {needsSource(f) && (
          <>
            <div style={{ ...muted, marginBottom: 6 }}>A high-risk or politically exposed client needs the source of funds and the source of wealth.</div>
            <Field label="Source of funds"><textarea style={{ ...inp, minHeight: 48 }} value={f.sourceOfFunds} onChange={e => set("sourceOfFunds")(e.target.value)} /></Field>
            <Field label="Source of wealth"><textarea style={{ ...inp, minHeight: 48 }} value={f.sourceOfWealth} onChange={e => set("sourceOfWealth")(e.target.value)} /></Field>
          </>)}
      </fieldset>
      {problem && editable && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
      {editable && <div style={{ marginTop: 8 }}><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save assessment"}</Btn></div>}
    </div>
  )
}

// ---- dialogs ----------------------------------------------------------------------------------------------------------------

function PartyDialog({ profile, today, onClose, onDone }: { profile: KycProfile; today: string; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState<KycPartyForm>(emptyKycParty(profile.subjectKind)), [err, setErr] = useState("")
  const set = (k: keyof KycPartyForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = kycPartyProblem(f, today)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${profile.id}/parties`, kycPartyRequest(f)),
    onSuccess: () => { toast.success("Person added"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not add")),
  })
  return (
    <Dialog title="Add a person" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Add"}</Btn></>}>
      <Pick label="Role" value={f.role} onChange={set("role")} options={kycRolesFor(profile.subjectKind)} />
      <Input label="Full name" value={f.name} onChange={set("name")} />
      <Input label="Identity or passport number" value={f.idNumber} onChange={set("idNumber")} />
      {f.role === "BENEFICIAL_OWNER" && (
        <>
          <Pick label="How was this owner identified" value={f.method} onChange={set("method")} options={KYC_METHODS} />
          {f.method === "OWNERSHIP" && <Input label="Percentage held" type="number" value={f.percent} onChange={set("percent")} hint="Anyone holding 5% or more is treated as a beneficial owner. If nobody does, identify who controls the entity instead and keep a note of the steps taken." />}
        </>)}
      <Input label="Identity verified on (leave blank if not yet)" type="date" value={f.verifiedOn} onChange={set("verifiedOn")} max={today} />
      <Input label="Note" value={f.note} onChange={set("note")} />
      {problem && (f.name || f.percent) && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ItemDialog({ profile, today, onClose, onDone }: { profile: KycProfile; today: string; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState<KycItemForm>(emptyKycItem(profile.subjectKind, today)), [err, setErr] = useState("")
  const set = (k: keyof KycItemForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = kycItemProblem(f, today)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${profile.id}/items`, kycItemRequest(f)),
    onSuccess: () => { toast.success("Document recorded"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not record")),
  })
  return (
    <Dialog title="Record a document checked" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Record"}</Btn></>}>
      <Pick label="Kind of document" value={f.kind} onChange={set("kind")} options={kycKindsFor(profile.subjectKind)} />
      {profile.parties.length > 0 && <Pick label="Whose" value={f.partyId} onChange={set("partyId")} blank="The client" options={profile.parties.map(x => [x.id, x.name] as const)} />}
      <Input label="What was checked" value={f.description} onChange={set("description")} hint="For example: green ID book, original seen and copied." />
      <Input label="Where the copy is kept" value={f.documentRef} onChange={set("documentRef")} />
      <Input label="Date checked" type="date" value={f.verifiedOn} onChange={set("verifiedOn")} max={today} />
      {problem && f.description && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ApproveDialog({ profile, selfSubmitted, onClose, onDone }: { profile: KycProfile; selfSubmitted: boolean; onClose: () => void; onDone: () => void }) {
  const [note, setNote] = useState(""), [err, setErr] = useState("")
  const problem = kycApproveProblem(selfSubmitted, note)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${profile.id}/approve`, { note: note.trim() || null }),
    onSuccess: () => { toast.success("Due diligence verified"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not approve")),
  })
  return (
    <Dialog title="Approve due diligence" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Approve"}</Btn></>}>
      <div style={muted}>You are confirming that the identity and ownership checks are complete and the risk rating is right. It will be valid for {profile.reviewMonths} months. After that the client must be reviewed.</div>
      <div style={{ marginTop: 10 }}><Input label={selfSubmitted ? "Why you are approving your own work" : "Note (optional)"} value={note} onChange={setNote} /></div>
      {problem && note.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ReturnDialog({ profile, onClose, onDone }: { profile: KycProfile; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState(""), [err, setErr] = useState("")
  const problem = kycReturnProblem(reason)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${profile.id}/return`, { reason: reason.trim() }),
    onSuccess: () => { toast.success("Returned to the preparer"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not return")),
  })
  return (
    <Dialog title="Return to the preparer" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Return"}</Btn></>}>
      <Input label="What needs fixing" value={reason} onChange={setReason} />
      {problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
