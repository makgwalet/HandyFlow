// src/pages/complianceservices/BillingEventRow.tsx
//
// One scheduled bill, with the actions a person may take on it. Used inside an agreement and on the Billing page.
// Raising makes a DRAFT invoice in Invoicing; nothing is issued from here.
import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { formatMoney } from "./pricingRules"
import { actionsFor, noteProblem, invoiceText, stageLabel, type EventAction } from "./billingRules"

export interface BillingEvent {
  id: string; agreementId: string; agreementNumber: string | null; clientId: string; clientName: string | null; dueDate: string; amount: number
  description: string; status: string; stage: string; daysLate: number; invoiceId: string | null; invoiceNumber: string | null
  invoice: { status: string; total: number | null; paid: number | null; credited: number | null } | null
  skipReason: string | null; releaseNote: string | null; actions: string[]; blocked: string | null
}

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "6px 12px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }

export default function BillingEventRow({ event, canManage, showClient, onChanged }: { event: BillingEvent; canManage: boolean; showClient?: boolean; onChanged: () => void }) {
  const [active, setActive] = useState<EventAction | null>(null)
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const base = `/api/v1/compliance-services/clients/${event.clientId}/billing-events/${event.id}`
  const run = useMutation({
    mutationFn: async (a: EventAction) => unwrap<BillingEvent>(await apiClient.post(`${base}/${a.id}`, a.noteLabel ? { reason: note } : {})),
    onSuccess: () => { setError(""); setActive(null); setNote(""); onChanged() },
    onError: (e: unknown) => setError(errText(e)),
  })
  const actions = actionsFor(event.actions, canManage)
  const problem = active ? noteProblem(active, note) : null
  const late = event.stage === "OVERDUE_TO_RAISE"

  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 4 }}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
        <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{event.dueDate}</span>
        {showClient && <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{event.clientName ?? "Unknown client"} · {event.agreementNumber}</span>}
        <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{formatMoney(event.amount)} before VAT</span>
        <span style={{ flex: 1 }} />
        <span style={{ fontSize: 12, color: late ? "var(--hf-warning-text, #8a5a00)" : "var(--hf-text-muted)" }}>
          {stageLabel(event.stage)}{late ? ` · ${event.daysLate} day${event.daysLate === 1 ? "" : "s"} late` : ""}
        </span>
      </div>
      {event.invoiceNumber && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Invoice {invoiceText(event.invoiceNumber, event.invoice)}</div>}
      {event.skipReason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Skipped: {event.skipReason}</div>}
      {event.releaseNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Earlier invoice released: {event.releaseNote}</div>}
      {event.blocked && event.status === "SCHEDULED" && canManage && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{event.blocked}</div>}
      {error && <div role="alert" style={bad}>{error}</div>}
      {actions.length > 0 && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {actions.map(a => a.noteLabel
            ? <button key={a.id} style={active?.id === a.id ? button : ghost} onClick={() => { setActive(active?.id === a.id ? null : a); setNote(""); setError("") }}>{a.label}</button>
            : <button key={a.id} style={a.id === "raise" ? button : ghost} disabled={run.isPending} onClick={() => run.mutate(a)}>{a.label}</button>)}
        </div>
      )}
      {active && (
        <div style={{ display: "grid", gap: 6 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }} htmlFor={`note-${event.id}`}>{active.noteLabel}</label>
          <textarea id={`note-${event.id}`} style={input} rows={2} value={note} onChange={e => setNote(e.target.value)} />
          {problem && <div style={bad}>{problem}</div>}
          <div><button style={{ ...button, opacity: problem || run.isPending ? 0.5 : 1 }} disabled={!!problem || run.isPending} onClick={() => run.mutate(active)}>Confirm: {active.label.toLowerCase()}</button></div>
        </div>
      )}
    </div>
  )
}
