// src/pages/supply-chain/TenderDetail.tsx — one tender: invitations, quotations (submission), side-by-side comparison, evaluation & award, history.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { inp, TH, TD, Field, ErrBox, Spinner, Banner, filterPill, fmtR, fmtDate } from "./scm.shared"
import {
  asList, asOne, errorText, rfqActions, TENDER_STEPS, tenderStep, responseProgress, bestIndex, quoteProblem, quoteBody, quoteTotals,
  technicalScoreProblem, overridesRecommendation, awardProblem, type QuoteForm, type RfqAction,
} from "./scmOps"
import { Panel, Pill, Stepper, StatCard, grid, type Tone } from "./scmUi"

export interface Rfq { id: string; rfqNumber: string; status: string; title: string; description: string | null; closesAt: string | null; requiredByDate: string | null; estimatedTotal: number | null; quotationCount: number
  awardedQuotationId: string | null; awardReason: string | null; overridesRecommendation: boolean; awardedByName: string | null; awardedAt: string | null; poId: string | null; createdByName: string | null; soleSourceJustification: string | null
  lines: { id: string; itemName: string; quantity: number; estimatedUnitCost: number | null }[]
  invitations: { supplierId: string; supplierName: string; status: string; emailed: boolean; declineReason: string | null; invitedAt: string | null; respondedAt: string | null }[] }
interface Quotation { id: string; supplierId: string; supplierName: string; status: string; statusReason: string | null; quoteReference: string | null; receivedAt: string; landedCost: number; totalInclVat: number; leadTimeDays: number
  technicalScore: number | null; technicalNotes: string | null; evaluatedByName: string | null; capturedByName: string | null
  lines: { rfqLineId: string; noBid: boolean; qtyOffered: number | null; netUnitPrice: number | null; lineTotal: number | null }[] }
interface Row { quotationId: string; supplierId: string; supplierName: string; status: string; landedCost: number; totalInclVat: number; leadTimeDays: number; warrantyMonths: number | null; paymentTermsDays: number | null; validUntil: string | null
  complete: boolean; expired: boolean; rank: number | null; priceScore: number | null; leadTimeScore: number | null; technicalScore: number | null; performanceScore: number | null; bbbeeScore: number | null; totalScore: number | null; estimatedSaving: number | null; flags: string[] }
interface Comparison { recommendedQuotationId: string | null; lowestPriceQuotationId: string | null; estimatedTotal: number | null; rows: Row[] }
interface Event { id: string; eventType: string; detail: string | null; actorName: string | null; createdAt: string }

const BASE = "/api/v1/supply-chain/rfqs"
type View = "invitations" | "quotations" | "comparison" | "award" | "history"
const VIEWS: [View, string][] = [["invitations", "Invitations"], ["quotations", "Quotations"], ["comparison", "Comparison"], ["award", "Evaluation & award"], ["history", "History"]]
const LABEL: Record<RfqAction, string> = { publish: "Publish & invite", close: "Close for quotations", extend: "Extend closing time", cancel: "Cancel tender", award: "Award", "create-po": "Create purchase order", capture: "Capture quotation" }
const STATUS_TONE: Record<string, Tone> = { DRAFT: "neutral", OPEN: "info", CLOSED: "warn", AWARDED: "ok", CANCELLED: "bad", INVITED: "neutral", RESPONDED: "ok", DECLINED: "bad", SUBMITTED: "info", WITHDRAWN: "neutral", DISQUALIFIED: "bad" }
const pill = (s: string) => <Pill tone={STATUS_TONE[s] ?? "neutral"}>{s[0] + s.slice(1).toLowerCase()}</Pill>
const rowStyle: React.CSSProperties = { borderTop: "1px solid var(--hf-border)" }
const best: React.CSSProperties = { background: "var(--hf-success-soft-strong)", fontWeight: 700 }

export function TenderDetail({ id }: { id: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_RFQ_MANAGE", "SCM_ADMIN"])
  const canPo = usePermissions(["SCM_ORDER", "SCM_PO_CREATE", "SCM_ADMIN"])
  const [view, setView] = useState<View>("invitations")
  const [err, setErr] = useState("")
  const [form, setForm] = useState<QuoteForm | null>(null)
  const [quoteErr, setQuoteErr] = useState("")

  const rfqQ = useQuery({ queryKey: ["scm-rfq", id], queryFn: () => apiClient.get(`${BASE}/${id}`).then(r => asOne<Rfq>(r)) })
  const rfq = rfqQ.data
  const quotesQ = useQuery({ queryKey: ["scm-rfq-quotes", id, rfq?.status, rfq?.quotationCount], enabled: !!rfq && rfq.quotationCount > 0, retry: false, queryFn: () => apiClient.get(`${BASE}/${id}/quotations`).then(asList<Quotation>) })
  const cmpQ = useQuery({ queryKey: ["scm-rfq-comparison", id, rfq?.status, rfq?.quotationCount], enabled: !!rfq && rfq.quotationCount > 0, retry: false, queryFn: () => apiClient.get(`${BASE}/${id}/comparison`).then(r => asOne<Comparison>(r)) })
  const eventsQ = useQuery({ queryKey: ["scm-rfq-events", id, rfq?.status], enabled: view === "history", queryFn: () => apiClient.get(`${BASE}/${id}/events`).then(asList<Event>) })

  const refresh = () => qc.invalidateQueries({ predicate: q => String(q.queryKey[0]).startsWith("scm-rfq") })
  const act = useMutation({ mutationFn: ({ path, body }: { path: string; body?: unknown }) => apiClient.post(`${BASE}/${id}/${path}`, body ?? {}), onSuccess: () => { setErr(""); refresh() }, onError: e => setErr(errorText(e)) })
  const capture = useMutation({ mutationFn: (q: QuoteForm) => apiClient.post(`${BASE}/${id}/quotations`, quoteBody(q)), onSuccess: () => { setForm(null); setQuoteErr(""); refresh() }, onError: e => setQuoteErr(errorText(e)) })
  const sub = useMutation({ mutationFn: ({ qid, path, body }: { qid: string; path: string; body: unknown }) => apiClient.post(`${BASE}/${id}/quotations/${qid}/${path}`, body), onSuccess: () => { setErr(""); refresh() }, onError: e => setErr(errorText(e)) })

  if (rfqQ.isLoading) return <Spinner />
  if (rfqQ.isError || !rfq) return <div style={{ padding: 20 }}><ErrBox msg={errorText(rfqQ.error, "Could not load the tender")} /></div>
  const progress = responseProgress(rfq.invitations)
  const sealed = cmpQ.isError && rfq.status === "OPEN"
  const actions = rfqActions(rfq.status, !!rfq.poId).filter(a => a !== "award" && (canManage || (a === "create-po" && canPo)))

  const startCapture = () => { setQuoteErr(""); setForm({ supplierId: "", quoteReference: "", validUntil: "", leadTimeDays: "", warrantyMonths: "", paymentTermsDays: "", deliveryCost: "", otherCharges: "",
    lines: rfq.lines.map(l => ({ rfqLineId: l.id, noBid: false, quantity: String(l.quantity), unitPrice: "", discountPct: "" })) }); setView("quotations") }
  const run = (a: RfqAction) => {
    if (a === "capture") return startCapture()
    if (a === "extend") { const d = window.prompt("New closing time (YYYY-MM-DDTHH:mm)"); if (d) act.mutate({ path: "extend", body: { closesAt: new Date(d).toISOString(), reason: "Extended from the tender screen" } }); return }
    if (a === "cancel") { const r = window.prompt("Why is this tender cancelled?"); if (r) act.mutate({ path: "cancel", body: { reason: r } }); return }
    act.mutate({ path: a === "publish" ? "publish" : a, body: {} })
  }

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <div style={{ flex: 1, minWidth: 220 }}><h3 style={{ margin: 0 }}>{rfq.rfqNumber} · {rfq.title}</h3>
          <div style={{ fontSize: 12, color: "var(--hf-text-tertiary)" }}>Created by {rfq.createdByName ?? "—"} · closes {rfq.closesAt ? fmtDate(rfq.closesAt) : "not set"}</div></div>
        {actions.map(a => <button key={a} style={filterPill(a === "publish" || a === "create-po")} disabled={act.isPending} onClick={() => run(a)}>{LABEL[a]}</button>)}
      </div>
      {tenderStep(rfq.status, !!rfq.poId) >= 0 ? <Stepper steps={TENDER_STEPS} current={tenderStep(rfq.status, !!rfq.poId)} /> : <Banner variant="error">This tender was cancelled.</Banner>}
      {err && <ErrBox msg={err} />}
      <div style={grid(170)}>
        <StatCard label="Invited" value={rfq.invitations.length} sub={`${progress.answered} answered (${progress.pct}%)`} />
        <StatCard label="Quotations" value={rfq.quotationCount} />
        <StatCard label="Estimate" value={rfq.estimatedTotal == null ? "—" : fmtR(rfq.estimatedTotal)} />
        <StatCard label="Status" value={pill(rfq.status)} />
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>{VIEWS.map(([v, l]) => <button key={v} style={filterPill(view === v)} onClick={() => setView(v)}>{l}</button>)}</div>

      {view === "invitations" && <Panel title="Supplier invitations">
        {rfq.invitations.length === 0 ? <div style={{ fontSize: 13 }}>No suppliers invited.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={TH}>Supplier</th><th style={TH}>Status</th><th style={TH}>Email</th><th style={TH}>Invited</th><th style={TH}>Answered</th><th style={TH}>Note</th></tr></thead>
            <tbody>{rfq.invitations.map(i => <tr key={i.supplierId} style={rowStyle}><td style={TD}><strong>{i.supplierName}</strong></td><td style={TD}>{pill(i.status)}</td>
              <td style={TD}>{i.emailed ? "Sent" : rfq.status === "DRAFT" ? "On publish" : "Not emailed"}</td><td style={TD}>{fmtDate(i.invitedAt)}</td><td style={TD}>{fmtDate(i.respondedAt)}</td><td style={TD}>{i.declineReason ?? "—"}</td></tr>)}</tbody></table>)}
      </Panel>}

      {view === "quotations" && <>
        {sealed && <Banner variant="info">Quotations are sealed until the tender is closed.</Banner>}
        {form ? (
          <Panel title="Capture a supplier's quotation">
            {quoteErr && <ErrBox msg={quoteErr} />}
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12 }}>
              <Field label="Supplier"><select style={inp} aria-label="Supplier" value={form.supplierId} onChange={e => setForm({ ...form, supplierId: e.target.value })}><option value="">Choose…</option>{rfq.invitations.map(i => <option key={i.supplierId} value={i.supplierId}>{i.supplierName}</option>)}</select></Field>
              <Field label="Their reference"><input style={inp} value={form.quoteReference} onChange={e => setForm({ ...form, quoteReference: e.target.value })} /></Field>
              <Field label="Lead time (days)"><input style={inp} aria-label="Lead time" value={form.leadTimeDays} onChange={e => setForm({ ...form, leadTimeDays: e.target.value })} /></Field>
              <Field label="Valid until"><input style={inp} type="date" value={form.validUntil} onChange={e => setForm({ ...form, validUntil: e.target.value })} /></Field>
              <Field label="Warranty (months)"><input style={inp} value={form.warrantyMonths} onChange={e => setForm({ ...form, warrantyMonths: e.target.value })} /></Field>
              <Field label="Payment terms (days)"><input style={inp} value={form.paymentTermsDays} onChange={e => setForm({ ...form, paymentTermsDays: e.target.value })} /></Field>
              <Field label="Delivery cost"><input style={inp} value={form.deliveryCost} onChange={e => setForm({ ...form, deliveryCost: e.target.value })} /></Field>
              <Field label="Other charges"><input style={inp} value={form.otherCharges} onChange={e => setForm({ ...form, otherCharges: e.target.value })} /></Field>
            </div>
            <h4 style={{ margin: "14px 0 6px" }}>Prices</h4>
            {form.lines.map((l, i) => {
              const line = rfq.lines.find(x => x.id === l.rfqLineId)
              const set = (patch: Partial<typeof l>) => setForm({ ...form, lines: form.lines.map((x, j) => j === i ? { ...x, ...patch } : x) })
              return (<div key={l.rfqLineId} style={{ display: "grid", gridTemplateColumns: "3fr 1fr 1fr 1fr auto", gap: 8, marginBottom: 6, alignItems: "center" }}>
                <span>{line?.itemName}{line?.estimatedUnitCost != null && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}> · est. {fmtR(line.estimatedUnitCost)}</span>}</span>
                <input style={inp} disabled={l.noBid} aria-label={`Quantity ${i + 1}`} value={l.quantity} onChange={e => set({ quantity: e.target.value })} />
                <input style={inp} disabled={l.noBid} aria-label={`Unit price ${i + 1}`} placeholder="Unit price" value={l.unitPrice} onChange={e => set({ unitPrice: e.target.value })} />
                <input style={inp} disabled={l.noBid} aria-label={`Discount ${i + 1}`} placeholder="Disc %" value={l.discountPct} onChange={e => set({ discountPct: e.target.value })} />
                <label style={{ fontSize: 12 }}><input type="checkbox" checked={l.noBid} onChange={e => set({ noBid: e.target.checked })} /> No bid</label></div>)
            })}
            <div style={{ fontSize: 13, margin: "10px 0" }}>Subtotal <strong>{fmtR(quoteTotals(form).subtotal)}</strong> · landed cost before VAT <strong>{fmtR(quoteTotals(form).landed)}</strong>{rfq.estimatedTotal != null && <> · estimate {fmtR(rfq.estimatedTotal)}</>}</div>
            <div style={{ display: "flex", gap: 8 }}><button style={filterPill(false)} onClick={() => setForm(null)}>Cancel</button>
              <button style={filterPill(true)} disabled={capture.isPending} onClick={() => { const p = quoteProblem(form); if (p) { setQuoteErr(p); return } setQuoteErr(""); capture.mutate(form) }}>Save quotation</button></div>
          </Panel>
        ) : (
          <Panel title="Quotations received" right={canManage && ["OPEN", "CLOSED"].includes(rfq.status) ? <button style={filterPill(true)} onClick={startCapture}>Capture quotation</button> : undefined}>
            {(quotesQ.data ?? []).length === 0 ? <div style={{ fontSize: 13 }}>{sealed ? "Sealed." : "No quotations yet."}</div> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={TH}>Supplier</th><th style={TH}>Reference</th><th style={TH}>Landed</th><th style={TH}>Incl. VAT</th><th style={TH}>Lead</th><th style={TH}>Status</th><th style={TH} /></tr></thead>
                <tbody>{(quotesQ.data ?? []).map(q => <tr key={q.id} style={rowStyle}><td style={TD}><strong>{q.supplierName}</strong><div style={{ fontSize: 11 }}>Captured by {q.capturedByName ?? "—"}</div></td><td style={TD}>{q.quoteReference ?? "—"}</td>
                  <td style={TD}>{fmtR(q.landedCost)}</td><td style={TD}>{fmtR(q.totalInclVat)}</td><td style={TD}>{q.leadTimeDays} d</td><td style={TD}>{pill(q.status)}</td>
                  <td style={{ ...TD, textAlign: "right" }}>{canManage && q.status === "SUBMITTED" && rfq.status !== "AWARDED" && <button style={filterPill(false)} onClick={() => { const r = window.prompt("Why is this quotation withdrawn?"); if (r) sub.mutate({ qid: q.id, path: "withdraw", body: { reason: r } }) }}>Withdraw</button>}</td></tr>)}</tbody></table>)}
          </Panel>)}
      </>}

      {view === "comparison" && <Comparison rfq={rfq} cmp={cmpQ.data} quotes={quotesQ.data ?? []} sealed={sealed} error={cmpQ.isError && !sealed ? errorText(cmpQ.error, "Could not load the comparison") : ""} />}

      {view === "award" && <AwardPanel rfq={rfq} cmp={cmpQ.data} quotes={quotesQ.data ?? []} sealed={sealed}
        onEvaluate={(qid, score, notes) => sub.mutate({ qid, path: "evaluate", body: { technicalScore: Number(score), notes } })}
        onDisqualify={(qid, why) => sub.mutate({ qid, path: "disqualify", body: { reason: why } })}
        onAward={(qid, reason) => act.mutate({ path: "award", body: { quotationId: qid, reason } })} busy={act.isPending || sub.isPending} />}

      {view === "history" && <Panel title="History">
        {eventsQ.isLoading ? <Spinner /> : (eventsQ.data ?? []).length === 0 ? <div style={{ fontSize: 13 }}>Nothing recorded yet.</div> : (
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8, fontSize: 13 }}>{(eventsQ.data ?? []).map(e => <li key={e.id}><strong>{e.eventType.replace(/_/g, " ").toLowerCase()}</strong> · {e.actorName ?? "system"} · {fmtDate(e.createdAt)}{e.detail ? <div style={{ color: "var(--hf-text-tertiary)" }}>{e.detail}</div> : null}</li>)}</ul>)}
      </Panel>}
    </div>
  )
}

function Comparison({ rfq, cmp, quotes, sealed, error }: { rfq: Rfq; cmp?: Comparison; quotes: Quotation[]; sealed: boolean; error: string }) {
  if (sealed) return <Banner variant="info">Quotations are sealed until the tender is closed. The comparison appears then.</Banner>
  if (error) return <ErrBox msg={error} />
  if (!cmp || cmp.rows.length === 0) return <Panel title="Comparison"><div style={{ fontSize: 13 }}>Nothing to compare yet.</div></Panel>
  const rows = cmp.rows
  const metric = (label: string, vals: (number | null)[], fmt: (n: number) => string, better: "low" | "high") => {
    const b = bestIndex(vals, better)
    return <tr key={label} style={rowStyle}><th scope="row" style={{ ...TH, textTransform: "none" }}>{label}</th>{vals.map((v, i) => <td key={i} style={{ ...TD, ...(b === i ? best : {}) }}>{v == null ? "—" : fmt(v)}</td>)}</tr>
  }
  const priceOf = (r: Row, lineId: string) => quotes.find(q => q.id === r.quotationId)?.lines.find(l => l.rfqLineId === lineId)
  return (
    <Panel title="Side-by-side comparison" right={<span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Best value in each row is highlighted</span>}>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 420 }}>
          <thead><tr><th style={TH} />{rows.map(r => <th key={r.quotationId} style={{ ...TH, textTransform: "none", fontSize: 13 }}>{r.supplierName}
            <div>{r.quotationId === cmp.recommendedQuotationId && <Pill tone="ok">★ Recommended</Pill>} {r.quotationId === cmp.lowestPriceQuotationId && <Pill tone="info">Lowest price</Pill>} {r.status === "DISQUALIFIED" && <Pill tone="bad">Disqualified</Pill>}</div></th>)}</tr></thead>
          <tbody>
            {metric("Rank", rows.map(r => r.rank), n => `#${n}`, "low")}
            {metric("Total score", rows.map(r => r.totalScore), n => n.toFixed(1), "high")}
            {metric("Landed cost", rows.map(r => r.landedCost), fmtR, "low")}
            {metric("Total incl. VAT", rows.map(r => r.totalInclVat), fmtR, "low")}
            {metric("Saving vs estimate", rows.map(r => r.estimatedSaving), fmtR, "high")}
            {metric("Lead time", rows.map(r => r.leadTimeDays), n => `${n} days`, "low")}
            {metric("Warranty", rows.map(r => r.warrantyMonths), n => `${n} months`, "high")}
            {metric("Payment terms", rows.map(r => r.paymentTermsDays), n => `${n} days`, "high")}
            {metric("Price score", rows.map(r => r.priceScore), n => n.toFixed(1), "high")}
            {metric("Lead time score", rows.map(r => r.leadTimeScore), n => n.toFixed(1), "high")}
            {metric("Technical score", rows.map(r => r.technicalScore), n => n.toFixed(1), "high")}
            {metric("Performance score", rows.map(r => r.performanceScore), n => n.toFixed(1), "high")}
            {metric("B-BBEE score", rows.map(r => r.bbbeeScore), n => n.toFixed(1), "high")}
            <tr style={rowStyle}><th scope="row" style={{ ...TH, textTransform: "none" }}>Valid until</th>{rows.map(r => <td key={r.quotationId} style={TD}>{fmtDate(r.validUntil)}{r.expired && <> <Pill tone="bad">Expired</Pill></>}</td>)}</tr>
            <tr style={rowStyle}><th scope="row" style={{ ...TH, textTransform: "none" }}>Flags</th>{rows.map(r => <td key={r.quotationId} style={TD}>{r.flags.length ? r.flags.map(f => f.replace(/_/g, " ").toLowerCase()).join(", ") : "—"}{!r.complete && <div><Pill tone="warn">Not every line priced</Pill></div>}</td>)}</tr>
            {rfq.lines.map(l => { const vals = rows.map(r => priceOf(r, l.id)?.netUnitPrice ?? null); const b = bestIndex(vals, "low")
              return <tr key={l.id} style={rowStyle}><th scope="row" style={{ ...TH, textTransform: "none" }}>{l.itemName} × {l.quantity}<div style={{ fontWeight: 400 }}>unit price{l.estimatedUnitCost != null ? ` · est. ${fmtR(l.estimatedUnitCost)}` : ""}</div></th>
                {vals.map((v, i) => <td key={i} style={{ ...TD, ...(b === i ? best : {}) }}>{v == null ? "No bid" : fmtR(v)}</td>)}</tr> })}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}

function AwardPanel({ rfq, cmp, quotes, sealed, onEvaluate, onDisqualify, onAward, busy }: { rfq: Rfq; cmp?: Comparison; quotes: Quotation[]; sealed: boolean; busy: boolean
  onEvaluate: (qid: string, score: string, notes: string) => void; onDisqualify: (qid: string, why: string) => void; onAward: (qid: string, reason: string) => void }) {
  const canManage = usePermissions(["SCM_RFQ_MANAGE", "SCM_ADMIN"])
  const [scores, setScores] = useState<Record<string, string>>({}); const [notes, setNotes] = useState<Record<string, string>>({})
  const [choice, setChoice] = useState(""); const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  if (sealed) return <Banner variant="info">Evaluation opens when the tender is closed.</Banner>
  const live = quotes.filter(q => q.status === "SUBMITTED")
  const awarded = rfq.status === "AWARDED"
  const submit = () => { const p = awardProblem(choice, reason); if (p) { setErr(p); return } setErr(""); onAward(choice, reason.trim()) }

  return (
    <div style={{ display: "grid", gap: 14 }}>
      {err && <ErrBox msg={err} />}
      <Panel title="Technical evaluation" right={<span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Scored by evaluators, 0–100</span>}>
        {live.length === 0 ? <div style={{ fontSize: 13 }}>No live quotations to evaluate.</div> : (
          <div style={{ display: "grid", gap: 10 }}>{live.map(q => {
            const s = scores[q.id] ?? (q.technicalScore != null ? String(q.technicalScore) : ""); const p = scores[q.id] !== undefined ? technicalScoreProblem(s) : null
            return (<div key={q.id} style={{ display: "grid", gridTemplateColumns: "2fr 90px 3fr auto auto", gap: 8, alignItems: "center", fontSize: 13 }}>
              <span><strong>{q.supplierName}</strong>{q.evaluatedByName && <div style={{ fontSize: 11 }}>Scored by {q.evaluatedByName}</div>}</span>
              <input style={inp} aria-label={`Technical score for ${q.supplierName}`} disabled={!canManage || awarded} value={s} onChange={e => setScores({ ...scores, [q.id]: e.target.value })} />
              <input style={inp} aria-label={`Notes for ${q.supplierName}`} placeholder="Notes" disabled={!canManage || awarded} value={notes[q.id] ?? q.technicalNotes ?? ""} onChange={e => setNotes({ ...notes, [q.id]: e.target.value })} />
              {canManage && !awarded && <button style={filterPill(false)} disabled={busy} onClick={() => { const pr = technicalScoreProblem(s); if (pr) { setErr(pr); return } setErr(""); onEvaluate(q.id, s, notes[q.id] ?? q.technicalNotes ?? "") }}>Save score</button>}
              {canManage && !awarded && <button style={filterPill(false)} onClick={() => { const w = window.prompt("Why is this quotation disqualified?"); if (w) onDisqualify(q.id, w) }}>Disqualify</button>}
              {p && <span style={{ gridColumn: "1 / -1", color: "var(--hf-danger-text)", fontSize: 12 }}>{p}</span>}
            </div>)
          })}</div>)}
      </Panel>

      <Panel title="Award approval">
        {awarded ? <Banner variant="info">Awarded{rfq.awardedByName ? ` by ${rfq.awardedByName}` : ""}{rfq.overridesRecommendation ? ", overriding the recommendation" : ""}: {rfq.awardReason}</Banner> : rfq.status !== "CLOSED" ? <div style={{ fontSize: 13 }}>Close the tender for quotations to award it.</div> : !cmp ? <Spinner /> : (
          <div style={{ display: "grid", gap: 10 }}>
            <Banner variant="info">A second person must award this tender. {rfq.createdByName ? `${rfq.createdByName} created it, so they cannot award it` : "The creator cannot award it"} unless nobody else can.</Banner>
            <div style={{ display: "grid", gap: 6 }}>{cmp.rows.filter(r => r.status !== "DISQUALIFIED" && r.status !== "WITHDRAWN").map(r => (
              <label key={r.quotationId} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 10px", border: `1.5px solid ${choice === r.quotationId ? "var(--hf-primary)" : "var(--hf-border)"}`, borderRadius: 8, fontSize: 13 }}>
                <input type="radio" name="award" aria-label={`Award to ${r.supplierName}`} disabled={!canManage} checked={choice === r.quotationId} onChange={() => setChoice(r.quotationId)} />
                <span style={{ flex: 1 }}><strong>{r.supplierName}</strong> · {fmtR(r.totalInclVat)} incl. VAT · {r.leadTimeDays} days · score {r.totalScore == null ? "—" : r.totalScore.toFixed(1)}</span>
                {r.quotationId === cmp.recommendedQuotationId && <Pill tone="ok">★ Recommended</Pill>}</label>))}</div>
            {overridesRecommendation(choice, cmp.recommendedQuotationId) && <Banner variant="warning">This is not the recommended quotation. It will be recorded as overriding the recommendation, so the reason matters.</Banner>}
            <Field label="Why this supplier (required)"><textarea style={inp} rows={3} aria-label="Award reason" disabled={!canManage} value={reason} onChange={e => setReason(e.target.value)} /></Field>
            {canManage && <div><button style={filterPill(true)} disabled={busy} onClick={submit}>Award tender</button></div>}
          </div>)}
      </Panel>
    </div>
  )
}
