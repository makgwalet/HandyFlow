// src/pages/complianceservices/ClientQuotesTab.tsx
//
// Quotes for one client. The server prices every line from the price book version pinned when the quote was
// started. A discount above the consultant limit goes to an administrator, who is not the author unless they record
// why. Sending and the client's answer are recorded by a person: nothing here emails anyone.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { formatMoney, UNIT_NAMES } from "./pricingRules"
import {
  APPROVER_LABELS, statusLabel, emptyQuoteLine, draftProblem, missingToSubmit, toBody, actionsFor, approveNeedsReason, actionProblem, expiryText,
  type Action, type QuoteDraft, type QuoteLineDraft, type ServiceBasis,
} from "./quoteRules"

interface Summary { id: string; clientId: string; clientName: string | null; quoteNumber: string; title: string | null; status: string; validUntil: string | null; total: number; requiredApprover: string; maxDiscountPct: number; createdAt: string }
interface Part { label: string; amount: number }
interface QLine {
  id: string; serviceCode: string; serviceName: string | null; serviceVersion: number | null; pricingModel: string; description: string | null; units: number | null; hours: number | null
  complexityPct: number | null; urgent: boolean; discountPct: number; beforeDiscount: number; discount: number; professional: number; disbursements: number; vat: number; total: number
  parts: Part[]; extras: { description: string; quantity: number; unitPrice: number }[]; disbursementItems: { description: string; amount: number; vatable: boolean }[]
}
interface Quote {
  id: string; clientId: string; quoteNumber: string; title: string | null; status: string; priceBookVersion: number; validUntil: string | null; notes: string | null; terms: string | null
  requiredApprover: string; maxDiscountPct: number; professional: number; discountTotal: number; disbursements: number; vat: number; vatRatePct: number; total: number
  createdBy: string | null; submittedBy: string | null; approvedBy: string | null; approvalNote: string | null; approvalExceptionReason: string | null; sentAt: string | null
  decisionNote: string | null; withdrawnReason: string | null; lines: QLine[]
}
interface BookLine { serviceCode: string; serviceName: string | null; pricingModel: string; hourlyGrade: string | null }
interface Book { inForce: boolean; versionNumber: number; lines: BookLine[] }

const ACCENT = "var(--hf-success-solid-strong)"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px" }
const lab: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const bad: React.CSSProperties = { fontSize: 11, color: "var(--hf-danger-text, #c0392b)" }
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 8 }
const row: React.CSSProperties = { display: "flex", justifyContent: "space-between", gap: 12 }
const today = () => new Date().toISOString().slice(0, 10)

const toDraft = (q: Quote): QuoteDraft => ({
  title: q.title ?? "", validUntil: q.validUntil ?? "", notes: q.notes ?? "", terms: q.terms ?? "",
  lines: q.lines.map(l => ({
    serviceCode: l.serviceCode, description: l.description ?? "", units: l.units?.toString() ?? "", hours: l.hours?.toString() ?? "", complexityPct: l.complexityPct?.toString() ?? "",
    urgent: l.urgent, discountPct: l.discountPct ? l.discountPct.toString() : "",
    extras: l.extras.map(e => ({ description: e.description, quantity: e.quantity.toString(), unitPrice: e.unitPrice.toString() })),
    disbursements: l.disbursementItems.map(d => ({ description: d.description, amount: d.amount.toString(), vatable: d.vatable })),
  })),
})

export default function ClientQuotesTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canApprove = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const { user } = useAuth()
  const base = `/api/v1/compliance-services/clients/${clientId}/quotes`
  const [selected, setSelected] = useState<string | null>(null)
  const [error, setError] = useState("")

  const { data: list = [] } = useQuery<Summary[]>({ queryKey: ["cs-quotes", clientId], queryFn: async () => unwrap<Summary[]>(await apiClient.get(base)) })
  const create = useMutation({
    mutationFn: async () => unwrap<Quote>(await apiClient.post(base, { title: null, validUntil: null, notes: null, terms: null, lines: [] })),
    onSuccess: (q: Quote) => { setError(""); setSelected(q.id); qc.setQueryData(["cs-quote", q.id], q); qc.invalidateQueries({ queryKey: ["cs-quotes", clientId] }) },
    onError: (e: unknown) => setError(errText(e)),
  })

  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Quotes for this client, priced from the price book. A deeper discount needs an administrator to approve it. Recording that a quote was sent, and the client's answer, is done by a person here.
      </div>
      {error && <div role="alert" style={{ ...bad, fontSize: 12 }}>{error}</div>}
      {list.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No quotes yet.</div>}
      {list.map(s => (
        <div key={s.id}>
          <button onClick={() => setSelected(selected === s.id ? null : s.id)} style={{ ...card, width: "100%", textAlign: "left", background: "transparent", cursor: "pointer", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{s.quoteNumber}</span>
            <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{s.title ?? "Untitled quote"}</span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{statusLabel(s.status)} · {formatMoney(s.total)}</span>
          </button>
          {selected === s.id && <QuoteDetail clientId={clientId} id={s.id} canManage={canManage} canApprove={canApprove} userId={user?.userId} />}
        </div>
      ))}
      {canManage && <div><button style={{ ...button, opacity: create.isPending ? 0.5 : 1 }} disabled={create.isPending} onClick={() => create.mutate()}>New quote</button></div>}
    </div>
  )
}

function QuoteDetail({ clientId, id, canManage, canApprove, userId }: { clientId: string; id: string; canManage: boolean; canApprove: boolean; userId: string | null | undefined }) {
  const qc = useQueryClient()
  const base = `/api/v1/compliance-services/clients/${clientId}/quotes/${id}`
  const { data: q } = useQuery<Quote>({ queryKey: ["cs-quote", id], queryFn: async () => unwrap<Quote>(await apiClient.get(base)) })
  const { data: books = [] } = useQuery<Book[]>({ queryKey: ["cs-pricebook"], queryFn: async () => unwrap<Book[]>(await apiClient.get("/api/v1/compliance-services/price-book")) })
  const [draft, setDraft] = useState<QuoteDraft | null>(null)
  const [error, setError] = useState("")
  const [active, setActive] = useState<Action | null>(null)
  const [note, setNote] = useState("")
  const [reason, setReason] = useState("")

  const done = (v: Quote) => { setError(""); setDraft(null); setActive(null); setNote(""); setReason(""); qc.setQueryData(["cs-quote", id], v); qc.invalidateQueries({ queryKey: ["cs-quotes", clientId] }) }
  const fail = (e: unknown) => setError(errText(e))
  const save = useMutation({ mutationFn: async (d: QuoteDraft) => unwrap<Quote>(await apiClient.put(base, toBody(d))), onSuccess: done, onError: fail })
  const act = useMutation({ mutationFn: async (v: { path: string; payload?: object }) => unwrap<Quote>(await apiClient.post(`${base}${v.path}`, v.payload ?? {})), onSuccess: done, onError: fail })

  if (!q) return <div style={{ padding: 12, color: "var(--hf-text-faint)" }}>Loading...</div>

  const bookLines = (books.find(b => b.inForce)?.lines ?? [])
  const bases: ServiceBasis[] = bookLines.map(l => ({ serviceCode: l.serviceCode, pricingModel: l.pricingModel, hourlyGrade: l.hourlyGrade }))
  const actions = actionsFor(q.status, canManage, canApprove)
  const needsReason = approveNeedsReason(q, userId)
  const problem = active ? actionProblem(active, note, needsReason, reason) : null

  const run = (a: Action) => {
    if (a.id === "submit") return act.mutate({ path: "/submit" })
    if (a.id === "sent") return act.mutate({ path: "/sent" })
    if (a.id === "approve") return act.mutate({ path: "/approve", payload: { note: note.trim() || null, exceptionReason: needsReason ? reason : null } })
    if (a.id === "reject") return act.mutate({ path: "/reject", payload: { reason: note } })
    if (a.id === "accept") return act.mutate({ path: "/accept", payload: { note } })
    if (a.id === "decline") return act.mutate({ path: "/decline", payload: { note } })
    return act.mutate({ path: "/withdraw", payload: { reason: note } })
  }
  const choose = (a: Action) => { if (a.id === "submit" || a.id === "sent") run(a); else { setActive(active?.id === a.id ? null : a); setNote(""); setReason("") } }

  return (
    <div style={{ margin: "8px 0 14px 14px", display: "grid", gap: 10 }}>
      {error && <div role="alert" style={{ ...bad, fontSize: 12 }}>{error}</div>}
      <div style={card}>
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <strong style={{ fontSize: 13 }}>{q.quoteNumber}{q.title ? ` · ${q.title}` : ""}</strong>
          <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{statusLabel(q.status)}</span>
          <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{expiryText(q.validUntil, today(), q.status)}</span>
          <span style={{ flex: 1 }} />
          {canManage && q.status === "DRAFT" && !draft && <button style={ghost} onClick={() => setDraft(toDraft(q))}>Edit</button>}
        </div>
        <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 4 }}>Priced on price book version {q.priceBookVersion}. Prices are before VAT unless a total is shown.</div>
        {q.status === "DRAFT" && q.approvalNote && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)", marginTop: 6 }}>Sent back: {q.approvalNote}</div>}

        {!draft && (
          <div style={{ marginTop: 10, display: "grid", gap: 8 }}>
            {q.lines.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No services on this quote yet.</div>}
            {q.lines.map(l => (
              <div key={l.id} style={{ fontSize: 12, color: "var(--hf-text-muted)", display: "grid", gap: 2 }}>
                <div style={row}><strong style={{ color: "var(--hf-text)" }}>{l.serviceCode}{l.serviceName ? ` · ${l.serviceName}` : ""}</strong><span>{formatMoney(l.total)}</span></div>
                {l.description && <div>{l.description}</div>}
                {l.parts.map((p, i) => <div key={i} style={{ ...row, paddingLeft: 12 }}><span>{p.label}</span><span>{formatMoney(p.amount)}</span></div>)}
              </div>
            ))}
            <div style={{ borderTop: "1px solid var(--hf-border)", paddingTop: 8, fontSize: 13, display: "grid", gap: 2 }} aria-label="Quote totals">
              <div style={row}><span>Professional fees</span><span>{formatMoney(q.professional)}</span></div>
              {q.discountTotal > 0 && <div style={row}><span>Includes discount of</span><span>{formatMoney(q.discountTotal)}</span></div>}
              {q.disbursements > 0 && <div style={row}><span>Disbursements at cost</span><span>{formatMoney(q.disbursements)}</span></div>}
              <div style={row}><span>VAT at {q.vatRatePct}%</span><span>{formatMoney(q.vat)}</span></div>
              <div style={{ ...row, fontWeight: 700 }}><span>Total</span><span>{formatMoney(q.total)}</span></div>
            </div>
            {q.maxDiscountPct > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Largest discount {q.maxDiscountPct}%. {APPROVER_LABELS[q.requiredApprover] ?? q.requiredApprover} (proposed limits, not yet approved policy).</div>}
            {q.approvalExceptionReason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Approved by the person who wrote it. Reason: {q.approvalExceptionReason}</div>}
            {q.sentAt && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Marked as sent {q.sentAt.slice(0, 10)}.</div>}
            {q.decisionNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Client's answer: {q.decisionNote}</div>}
            {q.withdrawnReason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Withdrawn: {q.withdrawnReason}</div>}
            {q.notes && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Notes: {q.notes}</div>}
            {q.terms && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Terms: {q.terms}</div>}
          </div>
        )}

        {draft && <Editor d={draft} onChange={setDraft} bookLines={bookLines} bases={bases} saving={save.isPending} onSave={() => save.mutate(draft)} onCancel={() => setDraft(null)} />}

        {!draft && actions.length > 0 && (
          <div style={{ display: "grid", gap: 8, marginTop: 10 }}>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {actions.map(a => <button key={a.id} style={a.id === "submit" || a.id === "approve" ? { ...button, opacity: act.isPending ? 0.5 : 1 } : ghost} disabled={act.isPending} onClick={() => choose(a)}>{a.label}</button>)}
            </div>
            {q.status === "DRAFT" && missingToSubmit(toDraft(q), today()).length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Before it can be submitted it needs: {missingToSubmit(toDraft(q), today()).join(", ")}.</div>}
            {active && (
              <div style={{ display: "grid", gap: 8 }}>
                {active.noteLabel && <div><label style={lab}>{active.noteLabel}</label><input style={input} aria-label={active.noteLabel} value={note} onChange={e => setNote(e.target.value)} /></div>}
                {active.id === "approve" && needsReason && <div><label style={lab}>You wrote or submitted this. Why can no one else approve the discount?</label><input style={input} placeholder="Reason, at least 15 characters" value={reason} onChange={e => setReason(e.target.value)} /></div>}
                {problem && (note || reason) && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{problem}</div>}
                <div><button style={{ ...button, opacity: problem || act.isPending ? 0.5 : 1 }} disabled={!!problem || act.isPending} onClick={() => run(active)}>Confirm: {active.label}</button></div>
              </div>
            )}
          </div>
        )}
        {!canApprove && q.status === "PENDING_APPROVAL" && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 8 }}>Waiting for an administrator to approve the discount.</div>}
      </div>
    </div>
  )
}

function Editor({ d, onChange, bookLines, bases, saving, onSave, onCancel }: {
  d: QuoteDraft; onChange: (d: QuoteDraft) => void; bookLines: BookLine[]; bases: ServiceBasis[]; saving: boolean; onSave: () => void; onCancel: () => void
}) {
  const problem = draftProblem(d, bases, today())
  const setLine = (i: number, patch: Partial<QuoteLineDraft>) => onChange({ ...d, lines: d.lines.map((l, j) => (j === i ? { ...l, ...patch } : l)) })
  return (
    <div style={{ display: "grid", gap: 10, marginTop: 10 }}>
      <div style={grid}>
        <div><label style={lab}>Title</label><input style={input} aria-label="Quote title" value={d.title} onChange={e => onChange({ ...d, title: e.target.value })} /></div>
        <div><label style={lab}>Stands until</label><input style={input} type="date" aria-label="Stands until" value={d.validUntil} onChange={e => onChange({ ...d, validUntil: e.target.value })} /></div>
      </div>
      {d.lines.map((l, i) => {
        const basis = bases.find(b => b.serviceCode === l.serviceCode)
        const needsUnits = !!basis && (basis.pricingModel in UNIT_NAMES || basis.pricingModel === "HYBRID")
        const needsHours = !!basis && (basis.pricingModel === "HOURLY" || (basis.pricingModel === "HYBRID" && !!basis.hourlyGrade))
        return (
          <div key={i} style={{ ...card, display: "grid", gap: 8 }}>
            <div style={grid}>
              <select style={input} aria-label="Service" value={l.serviceCode} onChange={e => setLine(i, { serviceCode: e.target.value })}>
                <option value="">Choose a service</option>
                {bookLines.map(b => <option key={b.serviceCode} value={b.serviceCode}>{b.serviceCode}{b.serviceName ? ` · ${b.serviceName}` : ""}</option>)}
              </select>
              {needsUnits && <input style={input} aria-label={`Number of ${UNIT_NAMES[basis!.pricingModel] ?? "units"}`} inputMode="decimal" placeholder={`Number of ${UNIT_NAMES[basis!.pricingModel] ?? "units"}`} value={l.units} onChange={e => setLine(i, { units: e.target.value })} />}
              {needsHours && <input style={input} aria-label="Hours" inputMode="decimal" placeholder="Hours" value={l.hours} onChange={e => setLine(i, { hours: e.target.value })} />}
              <input style={input} aria-label="Complexity percent" inputMode="decimal" placeholder="Complexity %" value={l.complexityPct} onChange={e => setLine(i, { complexityPct: e.target.value })} />
              <input style={input} aria-label="Discount percent" inputMode="decimal" placeholder="Discount %" value={l.discountPct} onChange={e => setLine(i, { discountPct: e.target.value })} />
              <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 6 }}><input type="checkbox" checked={l.urgent} onChange={e => setLine(i, { urgent: e.target.checked })} /> Urgent</label>
            </div>
            <input style={input} aria-label="Line description" placeholder="Description for the client (optional)" value={l.description} onChange={e => setLine(i, { description: e.target.value })} />
            {l.extras.map((x, k) => (
              <div key={k} style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                <input style={{ ...input, flex: 1, minWidth: 160 }} aria-label="Extra description" placeholder="Extra" value={x.description} onChange={e => setLine(i, { extras: l.extras.map((y, j) => (j === k ? { ...y, description: e.target.value } : y)) })} />
                <input style={{ ...input, width: 80 }} aria-label="Extra quantity" placeholder="Qty" value={x.quantity} onChange={e => setLine(i, { extras: l.extras.map((y, j) => (j === k ? { ...y, quantity: e.target.value } : y)) })} />
                <input style={{ ...input, width: 110 }} aria-label="Extra price" placeholder="Price each" value={x.unitPrice} onChange={e => setLine(i, { extras: l.extras.map((y, j) => (j === k ? { ...y, unitPrice: e.target.value } : y)) })} />
                <button style={ghost} onClick={() => setLine(i, { extras: l.extras.filter((_, j) => j !== k) })}>Remove</button>
              </div>
            ))}
            {l.disbursements.map((x, k) => (
              <div key={k} style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
                <input style={{ ...input, flex: 1, minWidth: 160 }} aria-label="Disbursement description" placeholder="Disbursement at cost" value={x.description} onChange={e => setLine(i, { disbursements: l.disbursements.map((y, j) => (j === k ? { ...y, description: e.target.value } : y)) })} />
                <input style={{ ...input, width: 110 }} aria-label="Disbursement amount" placeholder="Amount" value={x.amount} onChange={e => setLine(i, { disbursements: l.disbursements.map((y, j) => (j === k ? { ...y, amount: e.target.value } : y)) })} />
                <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 4 }}><input type="checkbox" checked={x.vatable} onChange={e => setLine(i, { disbursements: l.disbursements.map((y, j) => (j === k ? { ...y, vatable: e.target.checked } : y)) })} /> Carries VAT</label>
                <button style={ghost} onClick={() => setLine(i, { disbursements: l.disbursements.filter((_, j) => j !== k) })}>Remove</button>
              </div>
            ))}
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button style={ghost} onClick={() => setLine(i, { extras: [...l.extras, { description: "", quantity: "1", unitPrice: "" }] })}>Add an extra</button>
              <button style={ghost} onClick={() => setLine(i, { disbursements: [...l.disbursements, { description: "", amount: "", vatable: false }] })}>Add a disbursement</button>
              <button style={ghost} onClick={() => onChange({ ...d, lines: d.lines.filter((_, j) => j !== i) })}>Remove service</button>
            </div>
          </div>
        )
      })}
      <div><button style={ghost} onClick={() => onChange({ ...d, lines: [...d.lines, emptyQuoteLine()] })}>Add a service</button></div>
      <div><label style={lab}>Notes for the client</label><textarea style={input} rows={2} value={d.notes} onChange={e => onChange({ ...d, notes: e.target.value })} /></div>
      <div><label style={lab}>Terms</label><textarea style={input} rows={2} value={d.terms} onChange={e => onChange({ ...d, terms: e.target.value })} /></div>
      {problem && <div style={bad}>{problem}</div>}
      <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>The totals are worked out by the server when you save.</div>
      <div style={{ display: "flex", gap: 8 }}>
        <button style={{ ...button, opacity: problem || saving ? 0.5 : 1 }} disabled={!!problem || saving} onClick={onSave}>{saving ? "Saving..." : "Save draft"}</button>
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}
