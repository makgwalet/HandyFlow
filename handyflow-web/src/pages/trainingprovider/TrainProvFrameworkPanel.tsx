// src/pages/trainingprovider/TrainProvFrameworkPanel.tsx
//
// The provider's authorities in order, with each accreditation record: current, transitional or historical, the dates and source the authority
// gave, and the evidence behind it. This shows what was recorded. It never says the provider is accredited; the recorded status, scope and
// dates decide that elsewhere. Flags are prompts to look, not findings.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { statusLabel } from "./accreditationRules"
import {
  CURRENCY, EVIDENCE_KINDS, EVIDENCE_STATUS, FLAG, SOURCE_TYPES, currencyBody, currencyForm, currencyProblem, emptyEvidence, evidenceBody,
  evidenceDecisionProblem, evidenceProblem, frameworkBody, frameworkForm, frameworkProblem,
  type CurrencyForm, type Evidence, type EvidenceForm, type FrameworkAuthority, type FrameworkForm, type FrameworkRow,
} from "./frameworkRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { ACC } from "./tpApi"

const fmt = (d: string | null) => d ?? "not recorded"

function EvidenceList({ row, canManage }: { row: FrameworkRow; canManage: boolean }) {
  const qc = useQueryClient()
  const { data = [] } = useQuery<Evidence[]>({ queryKey: ["tp-acc-evidence", row.id], queryFn: async () => unwrap<Evidence[]>(await apiClient.get(`${ACC}/accreditations/${row.id}/evidence`)) })
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<EvidenceForm>(emptyEvidence(todayIso()))
  const [decide, setDecide] = useState<{ id: string; to: string } | null>(null)
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const refresh = () => Promise.all([qc.invalidateQueries({ queryKey: ["tp-acc-evidence", row.id] }), qc.invalidateQueries({ queryKey: ["tp-framework"] })])
  const add = async () => {
    const p = evidenceProblem(f, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${ACC}/accreditations/${row.id}/evidence`, evidenceBody(f)); setAdding(false); setF(emptyEvidence(todayIso())); await refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const send = async () => {
    if (!decide) return
    const p = evidenceDecisionProblem(decide.to, note); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${ACC}/evidence/${decide.id}/decide`, { toStatus: decide.to, note: note.trim() || null }); setDecide(null); setNote(""); await refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 6, marginTop: 6 }}>
      <div style={muted}>Evidence waiting to be checked is not verified evidence. A second person should verify it.</div>
      {data.length === 0 ? <div style={muted}>No evidence recorded.</div> : data.map(e => (
        <div key={e.id} style={{ ...card, display: "grid", gap: 4 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div>{EVIDENCE_KINDS[e.kind] ?? e.kind}: {e.description}<div style={muted}>Received {e.receivedOn}{e.documentDate ? ` · dated ${e.documentDate}` : ""}{e.reference ? ` · ${e.reference}` : ""}</div></div>
            <Badge text={EVIDENCE_STATUS[e.status] ?? e.status} tone={e.status === "VERIFIED" ? "good" : e.status === "PENDING" ? "warn" : "neutral"} />
          </div>
          {e.decisionNote && <div style={muted}>{e.decisionNote}</div>}
          {canManage && decide?.id !== e.id && (e.status === "PENDING" || e.status === "VERIFIED") && (
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {e.status === "PENDING" && <button style={ghost} onClick={() => { setDecide({ id: e.id, to: "VERIFIED" }); setNote(""); setError("") }}>Verify</button>}
              {e.status === "PENDING" && <button style={ghost} onClick={() => { setDecide({ id: e.id, to: "REJECTED" }); setNote(""); setError("") }}>Reject</button>}
              <button style={ghost} onClick={() => { setDecide({ id: e.id, to: "WITHDRAWN" }); setNote(""); setError("") }}>Withdraw</button>
            </div>
          )}
          {decide?.id === e.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <input aria-label="Evidence note" style={input} placeholder={decide.to === "VERIFIED" ? "What did you check? Needed if you added it yourself" : "Why?"} value={note} onChange={x => setNote(x.target.value)} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={send}>{decide.to === "VERIFIED" ? "Verify" : decide.to === "REJECTED" ? "Reject" : "Withdraw"}</button><button style={ghost} onClick={() => setDecide(null)}>Back</button></div>
            </div>
          )}
        </div>
      ))}
      {canManage && !adding && <div><button style={ghost} onClick={() => { setAdding(true); setError("") }}>Add evidence</button></div>}
      {adding && (
        <div style={{ display: "grid", gap: 6 }}>
          <select aria-label="Evidence kind" style={input} value={f.kind} onChange={e => setF({ ...f, kind: e.target.value })}>{Object.entries(EVIDENCE_KINDS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <input aria-label="Evidence description" style={input} placeholder="What is it? For example the approval letter" value={f.description} onChange={e => setF({ ...f, description: e.target.value })} />
          <input aria-label="Evidence reference" style={input} placeholder="Reference or where it is kept (optional)" value={f.reference} onChange={e => setF({ ...f, reference: e.target.value })} />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <Field label="Document date"><input aria-label="Evidence document date" type="date" style={input} value={f.documentDate} onChange={e => setF({ ...f, documentDate: e.target.value })} /></Field>
            <Field label="Received on"><input aria-label="Evidence received on" type="date" style={input} value={f.receivedOn} onChange={e => setF({ ...f, receivedOn: e.target.value })} /></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={add}>Record as waiting to be checked</button><button style={ghost} onClick={() => setAdding(false)}>Cancel</button></div>
        </div>
      )}
      <Notice text={error} />
    </div>
  )
}

function DatesForm({ row, onDone }: { row: FrameworkRow; onDone: () => void }) {
  const [f, setF] = useState<FrameworkForm>(frameworkForm(row))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const p = frameworkProblem(f, row, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.put(`${ACC}/accreditations/${row.id}/framework`, frameworkBody(f)); onDone() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 6 }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <Field label="Takes effect on"><input aria-label="Takes effect on" type="date" style={input} value={f.effectiveFrom} onChange={e => setF({ ...f, effectiveFrom: e.target.value })} /></Field>
        <Field label="Review due on"><input aria-label="Review due on" type="date" style={input} value={f.reviewDueOn} onChange={e => setF({ ...f, reviewDueOn: e.target.value })} /></Field>
      </div>
      <div style={muted}>Where this came from (optional, but complete if given)</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 8 }}>
        <select aria-label="Source type" style={input} value={f.sourceType} onChange={e => setF({ ...f, sourceType: e.target.value })}><option value="">No source recorded</option>{Object.entries(SOURCE_TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <input aria-label="Source reference" style={input} placeholder="Document or reference" value={f.sourceReference} onChange={e => setF({ ...f, sourceReference: e.target.value })} />
        <Field label="Version or publication date"><input aria-label="Source version date" type="date" style={input} value={f.sourceVersionDate} onChange={e => setF({ ...f, sourceVersionDate: e.target.value })} /></Field>
        <input aria-label="Source location" style={{ ...input, alignSelf: "end" }} placeholder="Section or page" value={f.sourceLocation} onChange={e => setF({ ...f, sourceLocation: e.target.value })} />
      </div>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Save dates and source</button><button style={ghost} onClick={onDone}>Cancel</button></div>
    </div>
  )
}

function CurrencyEditor({ row, siblings, onDone }: { row: FrameworkRow; siblings: FrameworkRow[]; onDone: () => void }) {
  const [f, setF] = useState<CurrencyForm>(currencyForm(row))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const p = currencyProblem(f, row.approvalDate, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${ACC}/accreditations/${row.id}/currency`, currencyBody(f)); onDone() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 6 }}>
      <select aria-label="Currency" style={input} value={f.currency} onChange={e => setF({ ...f, currency: e.target.value })}>{Object.entries(CURRENCY).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
      {f.currency !== "CURRENT" && <input aria-label="Currency note" style={input} placeholder={f.currency === "TRANSITIONAL" ? "What is the transitional arrangement?" : "Why did this stop applying?"} value={f.note} onChange={e => setF({ ...f, note: e.target.value })} />}
      {f.currency === "HISTORICAL" && <>
        <Field label="Stopped applying on"><input aria-label="Ended on" type="date" style={input} value={f.endedOn} onChange={e => setF({ ...f, endedOn: e.target.value })} /></Field>
        <select aria-label="Replaced by" style={input} value={f.supersededById} onChange={e => setF({ ...f, supersededById: e.target.value })}><option value="">Replaced by (optional)</option>{siblings.filter(s => s.id !== row.id).map(s => <option key={s.id} value={s.id}>{s.title}</option>)}</select>
      </>}
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Save</button><button style={ghost} onClick={onDone}>Cancel</button></div>
    </div>
  )
}

function RowCard({ row, siblings, canManage, onChanged }: { row: FrameworkRow; siblings: FrameworkRow[]; canManage: boolean; onChanged: () => void }) {
  const [panel, setPanel] = useState<"" | "dates" | "currency" | "evidence">("")
  const done = () => { setPanel(""); onChanged() }
  return (
    <div style={{ ...card, display: "grid", gap: 6, opacity: row.currency === "HISTORICAL" ? 0.8 : 1 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{row.title}</strong><div style={muted}>{row.accreditationNumber ?? "No number recorded"} · {row.liveScopes} programme{row.liveScopes === 1 ? "" : "s"} at {row.sites} site{row.sites === 1 ? "" : "s"}</div></div>
        <div style={{ display: "grid", justifyItems: "end", gap: 4 }}>
          <Badge text={statusLabel(row.effectiveStatus)} tone={row.effectiveStatus === "APPROVED" ? "good" : row.effectiveStatus === "EXPIRED" || row.effectiveStatus === "SUSPENDED" ? "bad" : "neutral"} />
          <Badge text={CURRENCY[row.currency] ?? row.currency} tone={row.currency === "CURRENT" ? "good" : row.currency === "TRANSITIONAL" ? "warn" : "neutral"} />
        </div>
      </div>
      <div style={muted}>
        Takes effect {fmt(row.effectiveFrom)} · approved {fmt(row.approvalDate)} · expires {fmt(row.expiryDate)} · review due {fmt(row.reviewDueOn)} · last verified {fmt(row.lastVerifiedOn)}
      </div>
      {row.currencyNote && <div style={muted}>{CURRENCY[row.currency]}: {row.currencyNote}{row.endedOn ? ` (stopped applying ${row.endedOn})` : ""}</div>}
      <div style={muted}>{row.sourceType ? `Source: ${SOURCE_TYPES[row.sourceType] ?? row.sourceType}, ${row.sourceReference}${row.sourceVersionDate ? `, dated ${row.sourceVersionDate}` : ""}${row.sourceLocation ? `, ${row.sourceLocation}` : ""}` : "No source recorded"}</div>
      <div style={muted}>Evidence: {row.verifiedEvidence} verified, {row.pendingEvidence} waiting to be checked</div>
      {row.flags.length > 0 && <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>{row.flags.map(fl => <Badge key={fl} text={FLAG[fl] ?? fl} tone="warn" />)}</div>}
      {canManage && panel === "" && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button style={ghost} onClick={() => setPanel("dates")}>Dates and source</button>
          <button style={ghost} onClick={() => setPanel("currency")}>Current, transitional or historical</button>
          <button style={ghost} onClick={() => setPanel("evidence")}>Evidence</button>
        </div>
      )}
      {!canManage && panel === "" && <div><button style={ghost} onClick={() => setPanel("evidence")}>Evidence</button></div>}
      {panel === "dates" && <DatesForm row={row} onDone={done} />}
      {panel === "currency" && <CurrencyEditor row={row} siblings={siblings} onDone={done} />}
      {panel === "evidence" && <><EvidenceList row={row} canManage={canManage} /><div><button style={ghost} onClick={() => setPanel("")}>Close evidence</button></div></>}
    </div>
  )
}

export default function TrainProvFrameworkPanel({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient()
  const { data = [], isLoading } = useQuery<FrameworkAuthority[]>({ queryKey: ["tp-framework"], queryFn: async () => unwrap<FrameworkAuthority[]>(await apiClient.get(`${ACC}/framework`)) })
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-framework"] }); void qc.invalidateQueries({ queryKey: ["tp-accreditations"] }) }
  if (isLoading) return <div style={muted}>Loading…</div>
  if (data.length === 0) return <div style={muted}>No authorities recorded yet. Add the ones that apply on the Authorities tab.</div>
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={muted}>Authorities in the provider's own order. Each record shows what was recorded, not whether the provider may deliver: that depends on the status, the programme and site in scope, and the dates. Current, transitional and historical records are kept apart.</div>
      {data.map(a => (
        <div key={a.id} style={{ display: "grid", gap: 8 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap", alignItems: "baseline" }}>
            <h3 style={{ margin: 0, fontSize: 15 }}>{a.name}{a.status === "RETIRED" ? " (retired)" : ""}</h3>
            <span style={muted}>{a.liveObligations} reporting obligation{a.liveObligations === 1 ? "" : "s"}</span>
          </div>
          {a.accreditations.length === 0 ? <div style={muted}>Nothing recorded for this authority.</div> : a.accreditations.map(r => (
            <RowCard key={r.id} row={r} siblings={a.accreditations} canManage={canManage} onChanged={changed} />
          ))}
        </div>
      ))}
    </div>
  )
}
