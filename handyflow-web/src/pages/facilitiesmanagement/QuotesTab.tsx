// Quotes for clients: drafted here, sent to the client, and decided by them in their portal.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, ReasonDialog, Row, SectionTitle, card, day, errorText, fail, money, muted, pageOf, td, th, when } from "./kit"
import { BASE, useLookups, type Lookups } from "./lookups"
import { Where } from "./WorkOrderDialogs"
import { QUOTE_STATUS, emptyQuote, emptyQuoteLine, lineTotal, quoteActions, quoteBody, quoteProblem, quoteSubtotal, quoteValidity, sendProblem, type QuoteForm } from "./fm.logic"

interface Line { lineNo: number; description: string; quantity: number; unitPrice: number; lineTotal: number }
interface Quote { id: string; quoteNumber: string; clientId: string; siteId: string; assetId: string | null; workOrderId: string | null; title: string; description: string | null; status: string; validUntil: string | null; internalNote: string | null; subtotal: number; vatRate: number | null; vatAmount: number; total: number; sentAt: string | null; decidedAt: string | null; decisionNote: string | null; lines: Line[] }
const todayIso = () => new Date().toISOString().slice(0, 10)

export default function QuotesTab() {
  const qc = useQueryClient(), lookups = useLookups()
  const canWrite = usePermission("FACILITIESMANAGEMENT_MANAGE") || usePermission("FACILITIESMANAGEMENT_ADMIN")
  const [page, setPage] = useState(0), [status, setStatus] = useState(""), [clientId, setClientId] = useState("")
  const [editing, setEditing] = useState<Quote | "new" | null>(null), [open, setOpen] = useState<Quote | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["fm-quotes", page, status, clientId], queryFn: async () => pageOf<Quote>(await apiClient.get(`${BASE}/quotes`, { params: { page, size: 25, status: status || undefined, clientId: clientId || undefined } })) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fm-quotes"] })
  const rows = data?.content ?? [], today = todayIso()
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>New quote</Btn>}>Quotes</SectionTitle>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "0 12px", maxWidth: 560 }}>
        <Pick label="Status" value={status} onChange={v => { setStatus(v); setPage(0) }} blank="All" options={Object.entries(QUOTE_STATUS).map(([k, v]) => [k, v.label] as const)} />
        <Pick label="Client" value={clientId} onChange={v => { setClientId(v); setPage(0) }} blank="All clients" options={lookups.clients.map(c => [c.id, c.tradingName] as const)} />
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the quotes. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No quotes found.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Quote</th><th style={th}>Client</th><th style={th}>Valid until</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={th}>Status</th></tr></thead>
            <tbody>{rows.map(q => {
              const s = QUOTE_STATUS[q.status] ?? { label: q.status, tone: "neutral" as const }, v = quoteValidity(q.status, q.validUntil, today)
              return (
                <tr key={q.id} onClick={() => setOpen(q)} style={{ cursor: "pointer" }}>
                  <td style={td}><b>{q.quoteNumber}</b><div style={muted}>{q.title}</div></td>
                  <td style={td}>{lookups.clientName(q.clientId) || "—"}<div style={muted}>{lookups.siteName(q.siteId)}</div></td>
                  <td style={td}>{q.validUntil ? day(q.validUntil) : "—"}</td>
                  <td style={{ ...td, textAlign: "right" }}>{money(q.total)}</td>
                  <td style={td}><Pill tone={s.tone}>{s.label}</Pill>{v && <div style={{ marginTop: 4 }}><Pill tone={v.tone}>{v.text}</Pill></div>}</td>
                </tr>)
            })}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {open && <QuoteView quote={open} lookups={lookups} canWrite={canWrite} onClose={() => setOpen(null)} onEdit={() => { setEditing(open); setOpen(null) }} onChanged={q => { setOpen(q); refresh() }} />}
      {editing && <QuoteDialog quote={editing === "new" ? null : editing} lookups={lookups} onClose={() => setEditing(null)} onSaved={q => { refresh(); setOpen(q) }} />}
    </div>
  )
}

function QuoteView({ quote, lookups, canWrite, onClose, onEdit, onChanged }: { quote: Quote; lookups: Lookups; canWrite: boolean; onClose: () => void; onEdit: () => void; onChanged: (q: Quote) => void }) {
  const [withdrawing, setWithdrawing] = useState(false)
  const s = QUOTE_STATUS[quote.status] ?? { label: quote.status, tone: "neutral" as const }
  const actions = quoteActions(quote.status, canWrite), block = sendProblem(quote.validUntil, quote.total, todayIso())
  const send = useMutation({
    mutationFn: async () => (await apiClient.post<Quote>(`${BASE}/quotes/${quote.id}/send`)).data,
    onSuccess: q => { toast.success("Quote sent to the client"); onChanged(q ?? { ...quote, status: "SENT" }) }, onError: e => { void fail(e, "Could not send the quote") },
  })
  return (
    <Dialog title={<>{quote.quoteNumber} <Pill tone={s.tone}>{s.label}</Pill></>} onClose={onClose} width={680}
      footer={<><Btn onClick={onClose}>Close</Btn>
        {actions.includes("withdraw") && <Btn kind="danger" onClick={() => setWithdrawing(true)}>Withdraw</Btn>}
        {actions.includes("edit") && <Btn onClick={onEdit}>Edit</Btn>}
        {actions.includes("send") && <Btn kind="primary" disabled={!!block || send.isPending} onClick={() => send.mutate()}>{send.isPending ? "Sending…" : "Send to client"}</Btn>}</>}>
      <div style={{ fontWeight: 700, fontSize: 15 }}>{quote.title}</div>
      {quote.description && <div style={{ margin: "4px 0 10px", lineHeight: 1.5, whiteSpace: "pre-wrap" }}>{quote.description}</div>}
      <Row k="Client">{lookups.clientName(quote.clientId)} · {lookups.siteName(quote.siteId)}</Row>
      <Row k="Valid until">{quote.validUntil ? day(quote.validUntil) : "—"}</Row>
      {quote.sentAt && <Row k="Sent">{when(quote.sentAt)}</Row>}
      {quote.decidedAt && <Row k="Decided">{when(quote.decidedAt)}{quote.decisionNote ? ` — “${quote.decisionNote}”` : ""}</Row>}
      {quote.internalNote && <Row k="Internal note">{quote.internalNote}</Row>}
      <div style={{ ...card, padding: 0, overflowX: "auto", margin: "12px 0" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Item</th><th style={{ ...th, textAlign: "right" }}>Qty</th><th style={{ ...th, textAlign: "right" }}>Price</th><th style={{ ...th, textAlign: "right" }}>Amount</th></tr></thead>
          <tbody>{quote.lines.map((l, n) => <tr key={n}><td style={td}>{l.description}</td><td style={{ ...td, textAlign: "right" }}>{l.quantity}</td><td style={{ ...td, textAlign: "right" }}>{money(l.unitPrice)}</td><td style={{ ...td, textAlign: "right" }}>{money(l.lineTotal)}</td></tr>)}</tbody>
        </table>
      </div>
      <div style={{ textAlign: "right", lineHeight: 1.7 }}>
        <div style={muted}>Subtotal {money(quote.subtotal)}</div>
        <div style={muted}>VAT{quote.vatRate != null ? ` (${Math.round(quote.vatRate * 100 * 100) / 100}%)` : ""} {money(quote.vatAmount)}</div>
        <div style={{ fontWeight: 700, fontSize: 16 }}>Total {money(quote.total)}</div>
      </div>
      {quote.status === "DRAFT" && block && actions.includes("send") && <div style={{ ...muted, marginTop: 8 }}>Cannot be sent yet: {block}.</div>}
      {quote.status === "SENT" && <div style={{ ...muted, marginTop: 8 }}>Waiting for the client to accept or decline in their portal.</div>}
      {withdrawing && <ReasonDialog title="Withdraw this quote?" label="Reason" confirm="Withdraw" danger intro="The client can no longer accept it. Say why, so the record shows it." onClose={() => setWithdrawing(false)}
        onSubmit={async reason => { const r = await apiClient.post<Quote>(`${BASE}/quotes/${quote.id}/withdraw`, { reason }); toast.success("Quote withdrawn"); onChanged(r.data ?? { ...quote, status: "WITHDRAWN" }) }} />}
    </Dialog>
  )
}

function QuoteDialog({ quote, lookups, onClose, onSaved }: { quote: Quote | null; lookups: Lookups; onClose: () => void; onSaved: (q: Quote) => void }) {
  const [f, setF] = useState<QuoteForm>(() => quote ? { clientId: quote.clientId, siteId: quote.siteId, assetId: quote.assetId ?? "", title: quote.title, description: quote.description ?? "", validUntil: quote.validUntil ?? "", internalNote: quote.internalNote ?? "", lines: quote.lines.map(l => ({ description: l.description, quantity: String(l.quantity), unitPrice: String(l.unitPrice) })) } : emptyQuote())
  const [err, setErr] = useState("")
  const creating = !quote, problem = quoteProblem(f, creating)
  const set = (k: keyof QuoteForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const setWhere = (k: "clientId" | "siteId" | "assetId") => (v: string) => setF(s => ({ ...s, [k]: v, ...(k === "clientId" ? { siteId: "", assetId: "" } : k === "siteId" ? { assetId: "" } : {}) }))
  const patch = (i: number, p: Partial<QuoteForm["lines"][number]>) => setF(s => ({ ...s, lines: s.lines.map((l, j) => j === i ? { ...l, ...p } : l) }))
  const save = useMutation({
    mutationFn: async () => (creating ? await apiClient.post<Quote>(`${BASE}/quotes`, quoteBody(f, true)) : await apiClient.put<Quote>(`${BASE}/quotes/${quote.id}`, quoteBody(f, false))).data,
    onSuccess: q => { toast.success(creating ? "Quote drafted" : "Quote saved"); onSaved(q); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the quote")),
  })
  return (
    <Dialog title={creating ? "New quote" : `Edit ${quote.quoteNumber}`} onClose={onClose} width={720}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save draft"}</Btn></>}>
      {creating ? <Where lookups={lookups} f={f} set={setWhere} /> : <div style={{ ...muted, marginBottom: 10 }}>{lookups.clientName(f.clientId)} · {lookups.siteName(f.siteId)} (fixed once the quote exists)</div>}
      <Input label="Title" value={f.title} onChange={set("title")} maxLength={200} />
      <Input label="Description" value={f.description} onChange={set("description")} maxLength={2000} hint="What the client sees under the title" />
      <div style={{ fontSize: 13, fontWeight: 700, margin: "6px 0" }}>Lines</div>
      {f.lines.map((l, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))", gap: "0 8px", alignItems: "end", borderTop: i ? "1px solid var(--hf-border-subtle, #e5e5e5)" : undefined, paddingTop: i ? 8 : 0 }}>
          <div style={{ gridColumn: "span 2" }}><Input label="Item" value={l.description} onChange={v => patch(i, { description: v })} maxLength={500} aria-label={`Line ${i + 1} item`} /></div>
          <Input label="Qty" type="number" min="0.001" step="any" value={l.quantity} onChange={v => patch(i, { quantity: v })} aria-label={`Line ${i + 1} quantity`} />
          <Input label="Price (R)" type="number" min="0" step="0.01" value={l.unitPrice} onChange={v => patch(i, { unitPrice: v })} aria-label={`Line ${i + 1} price`} />
          <div style={{ marginBottom: 12, display: "flex", gap: 8, alignItems: "center", justifyContent: "space-between" }}>
            <span style={{ ...muted, whiteSpace: "nowrap" }}>{money(lineTotal(l))}</span>
            <Btn small disabled={f.lines.length === 1} onClick={() => setF(s => ({ ...s, lines: s.lines.filter((_, j) => j !== i) }))}>Remove</Btn>
          </div>
        </div>))}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "2px 0 10px" }}>
        <Btn small disabled={f.lines.length >= 50} onClick={() => setF(s => ({ ...s, lines: [...s.lines, emptyQuoteLine()] }))}>Add a line</Btn>
        <span style={muted}>Subtotal {money(quoteSubtotal(f.lines))} · VAT is added when the quote is saved</span>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "0 12px" }}>
        <Input label="Valid until" type="date" value={f.validUntil} onChange={set("validUntil")} hint="Needed before it can be sent" />
        <Input label="Internal note" value={f.internalNote} onChange={set("internalNote")} maxLength={1000} hint="The client does not see this" />
      </div>
      {problem && f.title.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
