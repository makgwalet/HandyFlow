// Accreditation records: list, create, and the detail view with scope, verification, status changes and history.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  STATUS, emptyScope, emptyTransition, expiryNote, expiringWithin, isApproval, reasonProblem, scopeBody, scopeProblem, statusLabel, statusTone,
  transitionBody, transitionProblem, verifyProblem, type Accreditation, type AccreditationDetail, type ScopeForm, type TransitionForm,
} from "./accreditationRules"
import { Badge, Field, Notice } from "./tpUi"
import { errText, fmtDate, ghost, input, muted, solid, card, todayIso, unwrap } from "./tpStyle"
import { ACC, useAuthorities, useSites } from "./tpApi"

function Detail({ id, canManage, isAdmin, onBack }: { id: string; canManage: boolean; isAdmin: boolean; onBack: () => void }) {
  const qc = useQueryClient()
  const { data: sites = [] } = useSites()
  const { data, isLoading } = useQuery<AccreditationDetail>({ queryKey: ["tp-accreditation", id], queryFn: async () => unwrap<AccreditationDetail>(await apiClient.get(`${ACC}/accreditations/${id}`)) })
  const [panel, setPanel] = useState<"" | "status" | "verify" | "scope">("")
  const [tf, setTf] = useState<TransitionForm>(emptyTransition())
  const [verify, setVerify] = useState({ source: "", on: todayIso() })
  const [sf, setSf] = useState<ScopeForm>(emptyScope)
  const [withdrawing, setWithdrawing] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const refresh = () => { qc.invalidateQueries({ queryKey: ["tp-accreditation", id] }); qc.invalidateQueries({ queryKey: ["tp-accreditations"] }) }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setPanel(""); setWithdrawing(null); setReason(""); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  const a = data.accreditation
  const allowed = a.nextStatuses.filter(s => isAdmin || !isApproval(s))
  const lapsed = a.effectiveStatus !== a.status
  const set = <K extends keyof TransitionForm>(k: K, v: TransitionForm[K]) => setTf(f => ({ ...f, [k]: v }))

  const submitStatus = () => {
    const p = transitionProblem(tf, a.status, allowed, todayIso()); if (p) { setError(p); return }
    run(() => apiClient.post(`${ACC}/accreditations/${id}/transition`, transitionBody(tf)))
  }
  const submitVerify = () => { const p = verifyProblem(verify.source); if (p) { setError(p); return } run(() => apiClient.post(`${ACC}/accreditations/${id}/verify`, { verificationSource: verify.source.trim(), verifiedOn: verify.on || null })) }
  const submitScope = () => { const p = scopeProblem(sf); if (p) { setError(p); return } run(() => apiClient.post(`${ACC}/accreditations/${id}/scopes`, scopeBody(sf))) }
  const submitWithdraw = (scopeId: string) => { const p = reasonProblem(reason); if (p) { setError(p); return } run(() => apiClient.post(`${ACC}/scopes/${scopeId}/withdraw`, { reason: reason.trim() })) }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><button style={ghost} onClick={onBack}>← All accreditations</button></div>
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
          <div><strong style={{ fontSize: 16 }}>{a.title}</strong><div style={muted}>{a.authorityName}{a.accreditationNumber ? ` · No. ${a.accreditationNumber}` : " · no number recorded"}</div></div>
          <div style={{ display: "flex", gap: 6, alignItems: "center" }}><Badge text={statusLabel(a.effectiveStatus)} tone={statusTone(a.effectiveStatus)} /></div>
        </div>
        {lapsed && <div style={{ ...muted, marginTop: 6 }}>Recorded as {statusLabel(a.status).toLowerCase()}, but its expiry date has passed, so it no longer counts as in force.</div>}
        <div style={{ ...muted, marginTop: 6 }}>
          Approved {fmtDate(a.approvalDate)} · Expiry {fmtDate(a.expiryDate)} · {expiryNote(a.daysToExpiry)}
          {expiringWithin(a.daysToExpiry, 90) ? " · consider starting renewal" : ""}
        </div>
        <div style={muted}>{a.verificationSource ? `Verified at: ${a.verificationSource}${a.lastVerifiedOn ? ` on ${a.lastVerifiedOn}` : ""}` : "Not yet verified against the authority's own record"}</div>
        {a.conditions && <div style={{ marginTop: 6, fontSize: 13 }}><strong>Conditions:</strong> {a.conditions}</div>}
        {a.statusReason && <div style={muted}>Reason recorded: {a.statusReason}</div>}
        {canManage && (
          <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
            {allowed.length > 0 && <button style={solid} onClick={() => { setPanel("status"); setTf(emptyTransition(allowed[0])); setError("") }}>Change status</button>}
            <button style={ghost} onClick={() => { setPanel("verify"); setError("") }}>Record a verification</button>
            <button style={ghost} onClick={() => { setPanel("scope"); setSf(emptyScope); setError("") }}>Add to scope</button>
          </div>
        )}
        {a.nextStatuses.some(isApproval) && !isAdmin && <div style={{ ...muted, marginTop: 6 }}>Only an administrator can record an approval.</div>}
      </div>

      {panel === "status" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <Field label="New status"><select aria-label="New status" style={input} value={tf.toStatus} onChange={e => setTf(emptyTransition(e.target.value))}>{allowed.map(s => <option key={s} value={s}>{STATUS[s] ?? s}</option>)}</select></Field>
          {isApproval(tf.toStatus) && (<>
            <Field label="Accreditation number given by the authority"><input aria-label="Accreditation number" style={input} value={tf.accreditationNumber} onChange={e => set("accreditationNumber", e.target.value)} /></Field>
            <Field label="Approval date (from the letter or register)"><input aria-label="Approval date" type="date" style={input} value={tf.approvalDate} onChange={e => set("approvalDate", e.target.value)} /></Field>
            <Field label="Expiry date (leave blank if the approval states none)"><input aria-label="Expiry date" type="date" style={input} value={tf.expiryDate} onChange={e => set("expiryDate", e.target.value)} /></Field>
            <Field label="Where this was verified"><input aria-label="Verified at" style={input} placeholder="For example, the authority's approval letter or online register" value={tf.verificationSource} onChange={e => set("verificationSource", e.target.value)} /></Field>
            {tf.toStatus === "CONDITIONAL_APPROVAL" && <Field label="Conditions, as written by the authority"><textarea aria-label="Conditions" style={{ ...input, minHeight: 60 }} value={tf.conditions} onChange={e => set("conditions", e.target.value)} /></Field>}
            <Field label="If you created this record yourself and no one else can confirm it, say why" hint="A second person should confirm an approval. The server decides whether this is needed."><input aria-label="Override reason" style={input} value={tf.overrideReason} onChange={e => set("overrideReason", e.target.value)} /></Field>
          </>)}
          <Field label="Reason or note"><input aria-label="Reason" style={input} value={tf.reason} onChange={e => set("reason", e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submitStatus}>Record</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      {panel === "verify" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <Field label="Where it was checked"><input aria-label="Checked at" style={input} value={verify.source} onChange={e => setVerify({ ...verify, source: e.target.value })} /></Field>
          <Field label="Checked on"><input type="date" style={input} value={verify.on} onChange={e => setVerify({ ...verify, on: e.target.value })} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submitVerify}>Save verification</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      {panel === "scope" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <Field label="Site"><select aria-label="Scope site" style={input} value={sf.siteId} onChange={e => setSf({ ...sf, siteId: e.target.value })}><option value="">Choose a site</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
          <Field label="Programme or qualification, as named in the approval"><input aria-label="Programme title" style={input} value={sf.programmeTitle} onChange={e => setSf({ ...sf, programmeTitle: e.target.value })} /></Field>
          <Field label="Qualification reference (as shown by the authority)"><input aria-label="Qualification reference" style={input} value={sf.qualificationRef} onChange={e => setSf({ ...sf, qualificationRef: e.target.value })} /></Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <input aria-label="NQF level" style={input} placeholder="NQF level" value={sf.nqfLevel} onChange={e => setSf({ ...sf, nqfLevel: e.target.value })} />
            <input aria-label="Credits" style={input} placeholder="Credits" value={sf.credits} onChange={e => setSf({ ...sf, credits: e.target.value })} />
            <input aria-label="Valid from" type="date" style={input} value={sf.validFrom} onChange={e => setSf({ ...sf, validFrom: e.target.value })} />
            <input aria-label="Valid to" type="date" style={input} value={sf.validTo} onChange={e => setSf({ ...sf, validTo: e.target.value })} />
          </div>
          <input aria-label="Scope notes" style={input} placeholder="Notes (optional)" value={sf.notes} onChange={e => setSf({ ...sf, notes: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={submitScope}>Add to scope</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}

      <div><strong>Scope</strong> <span style={muted}>(what the approval covers, site by site)</span></div>
      {data.scopes.length === 0 ? <div style={muted}>Nothing in scope yet. Without a scope, nothing is covered.</div> : data.scopes.map(s => (
        <div key={s.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
            <div><strong>{s.programmeTitle}</strong> <span style={muted}>at {s.siteName}</span>
              <div style={muted}>{[s.qualificationRef, s.nqfLevel != null ? `NQF ${s.nqfLevel}` : null, s.credits != null ? `${s.credits} credits` : null].filter(Boolean).join(" · ") || "No reference recorded"}
                {s.validFrom || s.validTo ? ` · valid ${fmtDate(s.validFrom)} to ${fmtDate(s.validTo)}` : ""}</div></div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <Badge text={s.status === "ACTIVE" ? "In scope" : "Withdrawn"} tone={s.status === "ACTIVE" ? "good" : "neutral"} />
              {canManage && s.status === "ACTIVE" && <button style={ghost} onClick={() => { setWithdrawing(s.id); setError("") }}>Withdraw</button>}
            </div>
          </div>
          {s.withdrawnReason && <div style={muted}>Withdrawn: {s.withdrawnReason}</div>}
          {withdrawing === s.id && (
            <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
              <input aria-label="Withdraw reason" style={input} placeholder="Why is this leaving scope?" value={reason} onChange={e => setReason(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => submitWithdraw(s.id)}>Withdraw</button><button style={ghost} onClick={() => setWithdrawing(null)}>Cancel</button></div>
            </div>
          )}
        </div>
      ))}

      <div><strong>History</strong></div>
      {data.history.length === 0 ? <div style={muted}>No changes recorded.</div> : data.history.map(h => (
        <div key={h.id} style={{ fontSize: 13 }}>
          <span style={muted}>{h.actedAt.slice(0, 16).replace("T", " ")}</span> · {h.fromStatus ? `${statusLabel(h.fromStatus)} → ` : ""}{statusLabel(h.toStatus)}
          {h.reason ? ` · ${h.reason}` : ""}{h.overrideReason ? ` · confirmed without a second person because: ${h.overrideReason}` : ""}
        </div>
      ))}
    </div>
  )
}

export default function TrainProvAccreditationList({ canManage, isAdmin }: { canManage: boolean; isAdmin: boolean }) {
  const qc = useQueryClient()
  const { data: authorities = [] } = useAuthorities()
  const [status, setStatus] = useState("")
  const [openId, setOpenId] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ authorityId: "", title: "", accreditationNumber: "" })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data = [], isLoading } = useQuery<Accreditation[]>({ queryKey: ["tp-accreditations", status], queryFn: async () => unwrap<Accreditation[]>(await apiClient.get(`${ACC}/accreditations`, { params: { status: status || undefined } })) })
  if (openId) return <Detail id={openId} canManage={canManage} isAdmin={isAdmin} onBack={() => setOpenId(null)} />
  const create = async () => {
    if (!form.authorityId) { setError("Choose the authority"); return }
    if (form.title.trim().length < 3) { setError("Give the record a title of at least 3 characters"); return }
    setBusy(true); setError("")
    try {
      const v = unwrap<Accreditation>(await apiClient.post(`${ACC}/accreditations`, { authorityId: form.authorityId, title: form.title.trim(), accreditationNumber: form.accreditationNumber.trim() || null }))
      setAdding(false); setForm({ authorityId: "", title: "", accreditationNumber: "" }); qc.invalidateQueries({ queryKey: ["tp-accreditations"] }); setOpenId(v.id)
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <select aria-label="Filter by status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All statuses</option>{Object.entries(STATUS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        {canManage && !adding && <button style={solid} onClick={() => { setAdding(true); setError("") }}>New accreditation record</button>}
      </div>
      {adding && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <select aria-label="Authority" style={input} value={form.authorityId} onChange={e => setForm({ ...form, authorityId: e.target.value })}>
            <option value="">Choose the authority</option>{authorities.filter(a => a.status === "ACTIVE").map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <input aria-label="Title" style={input} placeholder="Title, for example the application or approval it relates to" value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
          <input aria-label="Number" style={input} placeholder="Authority's number, if you have one" value={form.accreditationNumber} onChange={e => setForm({ ...form, accreditationNumber: e.target.value })} />
          <div style={muted}>A new record starts as a draft. A number typed here does not make the provider accredited: only a recorded approval does.</div>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={create}>Create</button><button style={ghost} onClick={() => setAdding(false)}>Cancel</button></div>
        </div>
      )}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No accreditation records yet.</div> : data.map(a => (
        <button key={a.id} onClick={() => setOpenId(a.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{a.title}</strong><div style={muted}>{a.authorityName}{a.accreditationNumber ? ` · No. ${a.accreditationNumber}` : ""} · {a.liveScopes} in scope</div></div>
            <div style={{ display: "grid", justifyItems: "end", gap: 4 }}><Badge text={statusLabel(a.effectiveStatus)} tone={statusTone(a.effectiveStatus)} /><span style={muted}>{expiryNote(a.daysToExpiry)}</span></div>
          </div>
        </button>
      ))}
    </div>
  )
}
