// The client portal's working panels: service requests, quotes, invoices and sites.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { fmPortalApi, type FmPortalQuote, type FmPortalWo } from "../../api/fmPortal.api"
import { color, space } from "../accountant-portal/portal-theme"
import { PORTAL_CATEGORIES, PORTAL_STATUS, QUOTE_STATUS, INVOICE_STATUS, addressLine, canWithdraw, declineProblem, dueNote, emptyRaise, labelOf, raiseBody, raiseProblem, slaNote, type RaiseForm } from "../facilitiesmanagement/fm.logic"
import { Search } from "lucide-react"
import { Chip, Field, Modal, btn, day, empty, errText, inputStyle, money, panel, sub, td, th } from "./portalKit"
import { MapLink, TrackModal } from "./FmPortalExtras"
import { REQUEST_STATUS_FILTERS, filterRequests, nextInvoiceDue, owingInvoices } from "./portalStats"

const today = () => new Date().toISOString().slice(0, 10)
const useSites = (cid: string) => useQuery({ queryKey: ["fm-portal-sites", cid], queryFn: () => fmPortalApi.sites(cid) })
const Err = ({ text }: { text: string }) => text ? <div role="alert" style={{ margin: `${space(2)} 0`, padding: "8px 12px", background: color.redBg, color: color.redText, borderRadius: 8, fontSize: 13 }}>{text}</div> : null

export function RequestsPanel({ cid }: { cid: string }) {
  const qc = useQueryClient(), [page, setPage] = useState(0), [search, setSearch] = useState(""), [siteF, setSiteF] = useState(""), [groupF, setGroupF] = useState(""), [raising, setRaising] = useState(false), [withdrawing, setWithdrawing] = useState<FmPortalWo | null>(null), [tracking, setTracking] = useState<FmPortalWo | null>(null), [err, setErr] = useState("")
  const sites = useSites(cid)
  const q = useQuery({ queryKey: ["fm-portal-orders", cid, page], queryFn: () => fmPortalApi.orders(cid, page) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-portal-orders", cid] }); qc.invalidateQueries({ queryKey: ["fm-portal-summary", cid] }) }
  const withdraw = useMutation({ mutationFn: (w: FmPortalWo) => fmPortalApi.withdraw(cid, w.id), onSuccess: () => { setWithdrawing(null); refresh() }, onError: e => { setErr(errText(e, "Could not withdraw the request")); setWithdrawing(null) } })
  const siteName = (id: string) => sites.data?.content.find(s => s.id === id)?.name ?? ""
  const all = q.data?.content ?? [], now = new Date()
  const rows = filterRequests(all, { search, siteId: siteF, group: groupF }), filtered = !!(search.trim() || siteF || groupF)
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: space(3), marginBottom: space(3), flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: space(2), flexWrap: "wrap", flex: 1 }}>
          <label style={{ position: "relative", flex: "1 1 220px", maxWidth: 320 }}><Search size={15} style={{ position: "absolute", left: 10, top: 11, color: "var(--hf-text-faint)" }} /><input aria-label="Search requests" placeholder="Search requests…" value={search} onChange={e => setSearch(e.target.value)} style={{ ...inputStyle, paddingLeft: 32 }} /></label>
          <select aria-label="Site" value={siteF} onChange={e => setSiteF(e.target.value)} style={{ ...inputStyle, width: "auto" }}><option value="">All sites</option>{(sites.data?.content ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          <select aria-label="Status" value={groupF} onChange={e => setGroupF(e.target.value)} style={{ ...inputStyle, width: "auto" }}><option value="">All statuses</option>{REQUEST_STATUS_FILTERS.map(f => <option key={f.key} value={f.key}>{f.label}</option>)}</select>
        </div>
        <button style={btn(true)} onClick={() => setRaising(true)}>New request</button>
      </div>
      <Err text={err} />
      <div style={panel}>
        {q.isError ? <p style={empty}>We could not load your requests.</p> : q.isLoading ? <p style={empty}>Loading…</p> : rows.length === 0 ? <p style={empty}>{filtered ? "No requests match. Clear the search or filters to see them all." : "You have not reported anything yet."}</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Reference</th><th style={th}>Problem</th><th style={th}>Site</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(w => {
              const s = PORTAL_STATUS[w.status] ?? { label: w.status, tone: "neutral" as const }, sl = slaNote(w.resolutionDueAt, w.resolutionSla, ["COMPLETED", "CANCELLED", "REJECTED"].includes(w.status), now)
              return (
                <tr key={w.id}>
                  <td style={td}><b>{w.workOrderNumber}</b><div style={sub}>{day(w.createdAt)}</div></td>
                  <td style={{ ...td, maxWidth: 320 }}>{w.description}{w.declineMessage && <div style={{ ...sub, color: color.redText }}>Declined: {w.declineMessage}</div>}</td>
                  <td style={td}>{siteName(w.siteId) || "—"}<div style={sub}>{labelOf(PORTAL_CATEGORIES, w.category)}{w.scheduledDate ? ` · booked ${day(w.scheduledDate)}` : ""}</div></td>
                  <td style={td}><Chip tone={s.tone}>{s.label}</Chip>{w.status !== "COMPLETED" && w.status !== "CANCELLED" && w.status !== "REJECTED" && sl && <div style={{ marginTop: 4 }}><Chip tone={sl.tone}>{sl.text}</Chip></div>}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}><button style={btn()} aria-label={`Track ${w.workOrderNumber}`} onClick={() => setTracking(w)}>Track</button>{canWithdraw(w.status) && <button style={{ ...btn(false, true), marginLeft: 6 }} onClick={() => setWithdrawing(w)}>Withdraw</button>}</td>
                </tr>)
            })}</tbody>
          </table>)}
      </div>
      {(q.data?.totalPages ?? 0) > 1 && <div style={{ display: "flex", gap: 8, justifyContent: "center", marginTop: 12 }}><button style={btn()} disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><button style={btn()} disabled={page + 1 >= (q.data?.totalPages ?? 0)} onClick={() => setPage(page + 1)}>Next</button></div>}
      {tracking && <TrackModal cid={cid} woId={tracking.id} title={`${tracking.workOrderNumber} · progress`} onClose={() => setTracking(null)} />}
      {raising && <RaiseModal cid={cid} onClose={() => setRaising(false)} onDone={refresh} />}
      {withdrawing && <Modal title="Withdraw this request?" onClose={() => setWithdrawing(null)} footer={<><button style={btn()} onClick={() => setWithdrawing(null)}>Keep it</button><button style={btn(true)} disabled={withdraw.isPending} onClick={() => withdraw.mutate(withdrawing)}>{withdraw.isPending ? "Working…" : "Withdraw"}</button></>}>
        <p style={{ margin: 0, lineHeight: 1.5 }}>{withdrawing.workOrderNumber} will be cancelled. You can only do this while the team has not yet picked it up.</p></Modal>}
    </div>
  )
}

function RaiseModal({ cid, onClose, onDone }: { cid: string; onClose: () => void; onDone: () => void }) {
  const sites = useSites(cid), [f, setF] = useState<RaiseForm>(emptyRaise), [err, setErr] = useState(""), [step, setStep] = useState(0)
  const problem = raiseProblem(f)
  const descBad = f.description.trim().length < 10, siteBad = !f.siteId
  const go = useMutation({ mutationFn: () => fmPortalApi.raise(cid, raiseBody(f)), onSuccess: () => { onDone(); onClose() }, onError: e => setErr(errText(e, "Could not send your request")) })
  const siteName = (sites.data?.content ?? []).find(s => s.id === f.siteId)?.name ?? ""
  const STEPS = ["Details", "Location", "Review"]
  const stepper = (
    <div aria-label={`Step ${step + 1} of 3`} style={{ display: "flex", gap: space(2), marginBottom: space(4) }}>
      {STEPS.map((l, i) => <div key={l} style={{ flex: 1 }}><div style={{ height: 4, borderRadius: 4, background: i <= step ? color.navy : color.border }} /><div style={{ fontSize: 12, marginTop: 5, fontWeight: i === step ? 800 : 600, color: i === step ? color.ink : color.muted }}>{i + 1}. {l}</div></div>)}
    </div>)
  const next = () => { if (step === 0 && descBad) return; if (step === 1 && siteBad) return; setStep(step + 1) }
  return (
    <Modal title="New service request" onClose={onClose} footer={<>
      <button style={btn()} onClick={step === 0 ? onClose : () => { setStep(step - 1); setErr("") }}>{step === 0 ? "Cancel" : "Back"}</button>
      {step < 2 ? <button style={btn(true)} disabled={(step === 0 && descBad) || (step === 1 && siteBad)} onClick={next}>Next</button>
        : <button style={btn(true)} disabled={!!problem || go.isPending} onClick={() => { setErr(""); go.mutate() }}>{go.isPending ? "Sending…" : "Send request"}</button>}</>}>
      {stepper}
      {step === 0 && <>
        <Field label="What kind of problem?"><select value={f.category} onChange={e => setF(s => ({ ...s, category: e.target.value }))} style={inputStyle}>{PORTAL_CATEGORIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></Field>
        <Field label="Describe it *" hint="Say what is wrong and where, in at least 10 characters."><textarea rows={4} value={f.description} onChange={e => setF(s => ({ ...s, description: e.target.value }))} maxLength={2000} style={inputStyle} /></Field>
        {f.description !== "" && descBad && <div role="alert" style={{ ...sub, color: color.redText, marginBottom: space(2) }}>Please describe the problem in a little more detail.</div>}
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13.5 }}><input type="checkbox" checked={f.safetyImpact} onChange={e => setF(s => ({ ...s, safetyImpact: e.target.checked }))} />Someone could get hurt, or it is a safety risk</label>
      </>}
      {step === 1 && <>
        <Field label="Where is it? *"><select value={f.siteId} onChange={e => setF(s => ({ ...s, siteId: e.target.value }))} style={inputStyle}><option value="">Choose a site…</option>{(sites.data?.content ?? []).filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
        <Field label="Who should we call? (optional)"><input value={f.contactName} onChange={e => setF(s => ({ ...s, contactName: e.target.value }))} maxLength={100} style={inputStyle} /></Field>
      </>}
      {step === 2 && <div style={{ display: "grid", gap: space(3), fontSize: 14 }}>
        <div><div style={sub}>Kind of problem</div><b>{PORTAL_CATEGORIES.find(c => c[0] === f.category)?.[1]}</b></div>
        <div><div style={sub}>Description</div><div style={{ whiteSpace: "pre-wrap" }}>{f.description.trim()}</div></div>
        <div><div style={sub}>Site</div><b>{siteName || "—"}</b></div>
        {f.contactName.trim() && <div><div style={sub}>Contact</div><b>{f.contactName.trim()}</b></div>}
        {f.safetyImpact && <div><Chip tone="danger">Safety risk</Chip></div>}
        <div style={sub}>The team reviews every request and sets its priority. For something dangerous right now, phone them as well.</div>
      </div>}
      <Err text={err} />
    </Modal>
  )
}

export function QuotesPanel({ cid }: { cid: string }) {
  const qc = useQueryClient(), [open, setOpen] = useState<FmPortalQuote | null>(null)
  const q = useQuery({ queryKey: ["fm-portal-quotes", cid], queryFn: () => fmPortalApi.quotes(cid) })
  const rows = q.data ?? [], t = today()
  return (
    <div>
      <div style={panel}>
        {q.isError ? <p style={empty}>We could not load your quotes.</p> : q.isLoading ? <p style={empty}>Loading…</p> : rows.length === 0 ? <p style={empty}>No quotes have been sent to you.</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Quote</th><th style={th}>Valid until</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(x => { const s = QUOTE_STATUS[x.status] ?? { label: x.status, tone: "neutral" as const }
              return <tr key={x.id}><td style={td}><b>{x.quoteNumber}</b><div style={sub}>{x.title}</div></td><td style={td}>{x.validUntil ? day(x.validUntil) : "—"}{x.canRespond && x.validUntil && x.validUntil < t ? <div style={{ ...sub, color: color.amberText }}>Past its date</div> : null}</td>
                <td style={{ ...td, textAlign: "right" }}>{money(x.total)}</td><td style={td}><Chip tone={s.tone}>{x.canRespond ? "Needs your answer" : s.label}</Chip></td>
                <td style={{ ...td, textAlign: "right" }}><button style={btn(x.canRespond)} onClick={() => setOpen(x)}>{x.canRespond ? "Review" : "View"}</button></td></tr> })}</tbody>
          </table>)}
      </div>
      {open && <QuoteModal cid={cid} quote={open} onClose={() => setOpen(null)} onDone={() => { qc.invalidateQueries({ queryKey: ["fm-portal-quotes", cid] }); qc.invalidateQueries({ queryKey: ["fm-portal-orders", cid] }) }} />}
    </div>
  )
}

function QuoteModal({ cid, quote, onClose, onDone }: { cid: string; quote: FmPortalQuote; onClose: () => void; onDone: () => void }) {
  const [mode, setMode] = useState<"view" | "accept" | "decline">("view"), [note, setNote] = useState(""), [err, setErr] = useState("")
  const dp = mode === "decline" ? declineProblem(note) : null
  const go = useMutation({ mutationFn: () => mode === "accept" ? fmPortalApi.accept(cid, quote.id, note.trim() || null) : fmPortalApi.decline(cid, quote.id, note.trim()), onSuccess: () => { onDone(); onClose() }, onError: e => setErr(errText(e, "Could not record your answer")) })
  return (
    <Modal title={`${quote.quoteNumber} · ${quote.title}`} onClose={onClose} footer={mode === "view"
      ? <><button style={btn()} onClick={onClose}>Close</button>{quote.canRespond && <><button style={btn(false, true)} onClick={() => setMode("decline")}>Decline</button><button style={btn(true)} onClick={() => setMode("accept")}>Accept</button></>}</>
      : <><button style={btn()} onClick={() => { setMode("view"); setErr("") }}>Back</button><button style={btn(true)} disabled={!!dp || go.isPending} onClick={() => { setErr(""); go.mutate() }}>{go.isPending ? "Sending…" : mode === "accept" ? "Confirm acceptance" : "Confirm decline"}</button></>}>
      {quote.description && <p style={{ marginTop: 0, lineHeight: 1.5 }}>{quote.description}</p>}
      <table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>{quote.lines.map(l => <tr key={l.lineNo}><td style={td}>{l.description}<div style={sub}>{l.quantity} × {money(l.unitPrice)}</div></td><td style={{ ...td, textAlign: "right" }}>{money(l.lineTotal)}</td></tr>)}</tbody></table>
      <div style={{ textAlign: "right", margin: `${space(3)} 0`, lineHeight: 1.7 }}><div style={sub}>Subtotal {money(quote.subtotal)}</div><div style={sub}>VAT {money(quote.vatAmount)}</div><div style={{ fontWeight: 800, fontSize: 17 }}>Total {money(quote.total)}</div></div>
      {quote.decidedAt && <div style={sub}>Answered {day(quote.decidedAt)}{quote.decisionNote ? ` — “${quote.decisionNote}”` : ""}</div>}
      {mode !== "view" && <Field label={mode === "accept" ? "A note for the team (optional)" : "Why are you declining?"}><textarea rows={3} value={note} onChange={e => setNote(e.target.value)} maxLength={1000} style={inputStyle} /></Field>}
      {mode === "accept" && <div style={sub}>Accepting tells the team to go ahead with this work at this price.</div>}
      {dp && note !== "" && <div style={sub}>{dp}</div>}
      <Err text={err} />
    </Modal>
  )
}

export function InvoicesPanel({ cid }: { cid: string }) {
  const inv = useQuery({ queryKey: ["fm-portal-invoices", cid], queryFn: () => fmPortalApi.invoices(cid) })
  const cn = useQuery({ queryKey: ["fm-portal-credits", cid], queryFn: () => fmPortalApi.credits(cid) })
  const [err, setErr] = useState("")
  const dl = async (f: () => Promise<void>) => { setErr(""); try { await f() } catch (e) { setErr(errText(e, "Could not download the file")) } }
  const rows = inv.data ?? [], credits = cn.data ?? [], t = today()
  const owing = owingInvoices(rows), next = nextInvoiceDue(rows), totalOwing = owing.reduce((n, i) => n + i.balance, 0), latest = rows[0]
  return (
    <div>
      <Err text={err} />
      {rows.length > 0 && <div style={{ ...panel, padding: `${space(4)} ${space(5)}`, marginBottom: space(4), display: "flex", gap: space(6), alignItems: "center", flexWrap: "wrap" }}>
        <div><div style={{ ...sub, textTransform: "uppercase", fontWeight: 700, letterSpacing: "0.04em", fontSize: 11 }}>Outstanding</div><div style={{ fontSize: 26, fontWeight: 800, color: owing.some(i => (i.dueDate ?? "9") < t) ? color.redText : color.ink }}>{money(totalOwing)}</div><div style={sub}>{owing.length === 0 ? "You are up to date" : `${owing.length} invoice${owing.length === 1 ? "" : "s"} owing`}</div></div>
        {next && <div><div style={{ ...sub, textTransform: "uppercase", fontWeight: 700, letterSpacing: "0.04em", fontSize: 11 }}>Next due</div><div style={{ fontSize: 17, fontWeight: 800 }}>{day(next.dueDate)}</div><div style={sub}>{next.invoiceNumber} · {money(next.balance)}</div></div>}
        {latest && <button style={{ ...btn(), marginLeft: "auto" }} onClick={() => dl(() => fmPortalApi.invoicePdf(cid, latest.id, `${latest.invoiceNumber}.pdf`))}>Download latest invoice (PDF)</button>}
      </div>}
      <div style={panel}>
        {inv.isError ? <p style={empty}>We could not load your invoices.</p> : inv.isLoading ? <p style={empty}>Loading…</p> : rows.length === 0 ? <p style={empty}>No invoices yet.</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Invoice</th><th style={th}>Due</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Owing</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(i => { const s = INVOICE_STATUS[i.status] ?? { label: i.status, tone: "neutral" as const }, d = dueNote(i.status, i.balance, i.dueDate, t)
              return <tr key={i.id}><td style={td}><b>{i.invoiceNumber}</b><div style={sub}>{day(i.periodStart)} – {day(i.periodEnd)}</div></td><td style={td}>{i.dueDate ? day(i.dueDate) : "—"}{d && <div><Chip tone={d.tone}>{d.text}</Chip></div>}</td>
                <td style={{ ...td, textAlign: "right" }}>{money(i.total)}</td><td style={{ ...td, textAlign: "right" }}>{money(i.balance)}</td><td style={td}><Chip tone={s.tone}>{s.label}</Chip></td>
                <td style={{ ...td, textAlign: "right" }}><button style={btn()} onClick={() => dl(() => fmPortalApi.invoicePdf(cid, i.id, `${i.invoiceNumber}.pdf`))}>PDF</button></td></tr> })}</tbody>
          </table>)}
      </div>
      {credits.length > 0 && <>
        <div style={{ margin: `${space(5)} 0 ${space(2)}`, fontWeight: 700, color: color.ink }}>Credit notes</div>
        <div style={panel}><table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>{credits.map(c => <tr key={c.id}><td style={td}><b>{c.creditNoteNumber}</b><div style={sub}>{day(c.issueDate)} · against {c.invoiceNumber} · {c.reason}</div></td><td style={{ ...td, textAlign: "right" }}>{money(c.total)}</td>
          <td style={{ ...td, textAlign: "right" }}><button style={btn()} onClick={() => dl(() => fmPortalApi.creditPdf(cid, c.id, `${c.creditNoteNumber}.pdf`))}>PDF</button></td></tr>)}</tbody></table></div></>}
    </div>
  )
}

export function SitesPanel({ cid }: { cid: string }) {
  const q = useSites(cid), rows = q.data?.content ?? []
  return (
    <div style={panel}>
      {q.isError ? <p style={empty}>We could not load your sites.</p> : q.isLoading ? <p style={empty}>Loading…</p> : rows.length === 0 ? <p style={empty}>No sites yet.</p> : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={th}>Site</th><th style={th}>Address</th><th style={th}>Status</th></tr></thead>
          <tbody>{rows.map(s => <tr key={s.id}><td style={td}><b>{s.name}</b><div style={sub}>{labelOf([], s.siteType)}</div></td><td style={td}>{addressLine(s.address) || "—"}<div><MapLink address={s.address} /></div></td><td style={td}><Chip tone={s.status === "ACTIVE" ? "success" : "neutral"}>{s.status === "ACTIVE" ? "Active" : "Inactive"}</Chip></td></tr>)}</tbody></table>)}
    </div>
  )
}
