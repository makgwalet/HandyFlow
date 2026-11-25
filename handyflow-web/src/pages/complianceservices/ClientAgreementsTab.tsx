// src/pages/complianceservices/ClientAgreementsTab.tsx
//
// Agreements for one client. An agreement follows an accepted quote: the scope is copied from the quote, and a person
// sets the term and how the client is billed. Activating records how the client confirmed. The billing dates shown are
// a preview: nothing is invoiced from here yet.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { formatMoney } from "./pricingRules"
import BillingEventRow, { type BillingEvent } from "./BillingEventRow"
import { byUrgency } from "./billingRules"
import {
  FREQUENCIES, defaultTerms, frequencyLabel, statusLabel, termsProblem, previewTotal, toBody, actionsFor, actionProblem, schedule,
  type Action, type TermsDraft,
} from "./agreementRules"

interface Summary { id: string; clientId: string; quoteNumber: string | null; agreementNumber: string; title: string | null; status: string; startDate: string; endDate: string | null; billingFrequency: string; billingAmount: number; nextBillingDate: string | null; attention: string | null }
interface Option { id: string; quoteNumber: string; title: string | null; professional: number; total: number }
interface ALine { id: string; serviceCode: string; serviceName: string | null; pricingModel: string; description: string | null; professional: number }
interface Agreement {
  id: string; clientId: string; quoteNumber: string | null; agreementNumber: string; title: string | null; status: string; startDate: string; endDate: string | null
  billingFrequency: string; billingAmount: number; firstBillingDate: string; autoRenew: boolean; noticeDays: number; notes: string | null; terms: string | null
  quotedProfessional: number; quotedTotal: number; contractValue: number | null; comparisonNote: string | null; attention: string | null; nextBillingDate: string | null
  scheduleTruncated: boolean; schedule: { date: string; amount: number }[]; lines: ALine[]; actions: string[]; canEdit: boolean
  activationNote: string | null; suspendedReason: string | null; endReason: string | null
}

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
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 8 }
const today = () => new Date().toISOString().slice(0, 10)

const toDraft = (a: Agreement): TermsDraft => ({
  title: a.title ?? "", startDate: a.startDate, endDate: a.endDate ?? "", billingFrequency: a.billingFrequency, billingAmount: String(a.billingAmount),
  firstBillingDate: a.firstBillingDate, autoRenew: a.autoRenew, noticeDays: String(a.noticeDays), notes: a.notes ?? "", terms: a.terms ?? "",
})

export default function ClientAgreementsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const base = `/api/v1/compliance-services/clients/${clientId}/agreements`
  const [selected, setSelected] = useState<string | null>(null)
  const [quoteId, setQuoteId] = useState("")
  const [error, setError] = useState("")

  const { data: list = [] } = useQuery<Summary[]>({ queryKey: ["cs-agreements", clientId], queryFn: async () => unwrap<Summary[]>(await apiClient.get(base)) })
  const { data: options = [] } = useQuery<Option[]>({ queryKey: ["cs-agreement-options", clientId], queryFn: async () => unwrap<Option[]>(await apiClient.get(`${base}/available-quotes`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["cs-agreements", clientId] }); qc.invalidateQueries({ queryKey: ["cs-agreement-options", clientId] }) }

  const create = useMutation({
    mutationFn: async (id: string) => {
      const o = options.find(x => x.id === id)
      const t = defaultTerms(o?.title ?? null, o?.professional ?? 0, today())
      return unwrap<Agreement>(await apiClient.post(base, { quoteId: id, terms: toBody(t) }))
    },
    onSuccess: (a: Agreement) => { setError(""); setQuoteId(""); setSelected(a.id); qc.setQueryData(["cs-agreement", a.id], a); refresh() },
    onError: (e: unknown) => setError(errText(e)),
  })

  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        An agreement follows an accepted quote. It keeps the scope as quoted and records the term and how the client is billed. The billing dates are a preview: nothing is invoiced from here yet.
      </div>
      {error && <div role="alert" style={bad}>{error}</div>}

      {canManage && (
        <div style={{ ...card, display: "flex", gap: 8, alignItems: "end", flexWrap: "wrap" }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            <label style={lab} htmlFor="agreement-quote">Start an agreement from an accepted quote</label>
            <select id="agreement-quote" style={input} value={quoteId} onChange={e => setQuoteId(e.target.value)} disabled={options.length === 0}>
              <option value="">{options.length === 0 ? "No accepted quotes waiting" : "Choose a quote"}</option>
              {options.map(o => <option key={o.id} value={o.id}>{o.quoteNumber}{o.title ? ` · ${o.title}` : ""} · {formatMoney(o.professional)} fees</option>)}
            </select>
          </div>
          <button style={{ ...button, opacity: quoteId && !create.isPending ? 1 : 0.5 }} disabled={!quoteId || create.isPending} onClick={() => create.mutate(quoteId)}>Start agreement</button>
        </div>
      )}

      {list.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No agreements yet.</div>}
      {list.map(s => (
        <div key={s.id} style={card}>
          <button onClick={() => setSelected(selected === s.id ? null : s.id)} aria-expanded={selected === s.id}
            style={{ all: "unset", cursor: "pointer", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", width: "100%" }}>
            <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{s.agreementNumber}</span>
            <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{s.title ?? "Untitled"}</span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
              {statusLabel(s.status)} · {formatMoney(s.billingAmount)} {frequencyLabel(s.billingFrequency).toLowerCase()}{s.nextBillingDate ? ` · next ${s.nextBillingDate}` : ""}
            </span>
          </button>
          {s.attention && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)", marginTop: 4 }}>{s.attention}</div>}
          {selected === s.id && <AgreementPanel clientId={clientId} id={s.id} canManage={canManage} onChanged={refresh} />}
        </div>
      ))}
    </div>
  )
}

function AgreementPanel({ clientId, id, canManage, onChanged }: { clientId: string; id: string; canManage: boolean; onChanged: () => void }) {
  const qc = useQueryClient()
  const url = `/api/v1/compliance-services/clients/${clientId}/agreements/${id}`
  const [draft, setDraft] = useState<TermsDraft | null>(null)
  const [active, setActive] = useState<Action | null>(null)
  const [note, setNote] = useState("")
  const [endDate, setEndDate] = useState("")
  const [error, setError] = useState("")
  const [madeBills, setMadeBills] = useState<number | null>(null)

  const { data: a } = useQuery<Agreement>({ queryKey: ["cs-agreement", id], queryFn: async () => unwrap<Agreement>(await apiClient.get(url)) })
  const { data: bills = [] } = useQuery<BillingEvent[]>({
    queryKey: ["cs-billing", id], queryFn: async () => unwrap<BillingEvent[]>(await apiClient.get(`${url}/billing-events`)),
  })
  const billsChanged = () => { qc.invalidateQueries({ queryKey: ["cs-billing", id] }); qc.invalidateQueries({ queryKey: ["cs-billing-all"] }) }
  const generate = useMutation({
    mutationFn: async () => unwrap<{ created: number }>(await apiClient.post(`${url}/billing-events/generate`, {})),
    onSuccess: (g: { created: number }) => { setError(""); setMadeBills(g.created); billsChanged() }, onError: (e: unknown) => setError(errText(e)),
  })
  const done = (v: Agreement) => { setError(""); setDraft(null); setActive(null); setNote(""); setEndDate(""); qc.setQueryData(["cs-agreement", id], v); onChanged() }
  const fail = (e: unknown) => setError(errText(e))
  const save = useMutation({ mutationFn: async (d: TermsDraft) => unwrap<Agreement>(await apiClient.put(url, toBody(d))), onSuccess: done, onError: fail })
  const act = useMutation({
    mutationFn: async (x: Action) => {
      const body = x.id === "end" ? { endDate, reason: note } : x.id === "activate" ? { note } : { reason: note }
      return unwrap<Agreement>(await apiClient.post(`${url}/${x.id}`, x.id === "resume" ? {} : body))
    },
    onSuccess: done, onError: fail,
  })

  if (!a) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)", marginTop: 8 }}>Loading...</div>
  const actions = actionsFor(a.actions, canManage)
  const problem = active ? actionProblem(active, note, endDate, a.startDate) : null
  const editing = draft !== null && a.canEdit
  const draftProblem = draft ? termsProblem(draft) : null
  const dates = draft && !draftProblem ? schedule(draft.firstBillingDate, draft.endDate, draft.billingFrequency) : null
  const total = draft && !draftProblem ? previewTotal(draft) : null

  return (
    <div style={{ display: "grid", gap: 10, marginTop: 10, borderTop: "1px solid var(--hf-border)", paddingTop: 10 }}>
      {error && <div role="alert" style={bad}>{error}</div>}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        From quote {a.quoteNumber ?? "(not found)"} · quoted fees {formatMoney(a.quotedProfessional)} before VAT · {formatMoney(a.quotedTotal)} with VAT and disbursements
      </div>

      <div>
        <div style={{ ...lab, marginBottom: 6 }}>Scope as quoted</div>
        {a.lines.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No services were copied.</div>}
        {a.lines.map(l => (
          <div key={l.id} style={{ display: "flex", justifyContent: "space-between", gap: 12, fontSize: 13, color: "var(--hf-text)" }}>
            <span>{l.serviceCode}{l.serviceName ? ` · ${l.serviceName}` : ""}{l.description ? ` · ${l.description}` : ""}</span>
            <span>{formatMoney(l.professional)}</span>
          </div>
        ))}
      </div>

      {!editing && (
        <div style={{ fontSize: 13, color: "var(--hf-text)", display: "grid", gap: 2 }}>
          <div>Runs from {a.startDate}{a.endDate ? ` to ${a.endDate}` : " with no end date"}{a.autoRenew ? `, renews automatically (${a.noticeDays} days' notice)` : ""}</div>
          <div>Billed {formatMoney(a.billingAmount)} {frequencyLabel(a.billingFrequency).toLowerCase()} from {a.firstBillingDate}, before VAT</div>
          {a.contractValue !== null && <div>Over the term: {formatMoney(a.contractValue)} before VAT</div>}
          {a.notes && <div style={{ color: "var(--hf-text-muted)" }}>Notes: {a.notes}</div>}
          {a.terms && <div style={{ color: "var(--hf-text-muted)" }}>Terms: {a.terms}</div>}
          {a.activationNote && <div style={{ color: "var(--hf-text-muted)" }}>Confirmed: {a.activationNote}</div>}
          {a.suspendedReason && <div style={{ color: "var(--hf-text-muted)" }}>Suspended: {a.suspendedReason}</div>}
          {a.endReason && <div style={{ color: "var(--hf-text-muted)" }}>{a.status === "CANCELLED" ? "Cancelled" : "Ended"}: {a.endReason}</div>}
        </div>
      )}
      {a.comparisonNote && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)" }}>{a.comparisonNote}</div>}
      {a.attention && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)" }}>{a.attention}</div>}

      {editing && draft && (
        <div style={{ display: "grid", gap: 8 }}>
          <div><label style={lab} htmlFor="ag-title">Title</label><input id="ag-title" style={input} value={draft.title} onChange={e => setDraft({ ...draft, title: e.target.value })} /></div>
          <div style={grid}>
            <div><label style={lab} htmlFor="ag-start">Starts</label><input id="ag-start" type="date" style={input} value={draft.startDate} onChange={e => setDraft({ ...draft, startDate: e.target.value })} /></div>
            <div><label style={lab} htmlFor="ag-end">Ends (optional)</label><input id="ag-end" type="date" style={input} value={draft.endDate} onChange={e => setDraft({ ...draft, endDate: e.target.value })} /></div>
            <div>
              <label style={lab} htmlFor="ag-freq">Billed</label>
              <select id="ag-freq" style={input} value={draft.billingFrequency} onChange={e => setDraft({ ...draft, billingFrequency: e.target.value })}>
                {FREQUENCIES.map(f => <option key={f[0]} value={f[0]}>{f[1]}</option>)}
              </select>
            </div>
            <div><label style={lab} htmlFor="ag-amount">Amount each time, before VAT</label><input id="ag-amount" inputMode="decimal" style={input} value={draft.billingAmount} onChange={e => setDraft({ ...draft, billingAmount: e.target.value })} /></div>
            <div><label style={lab} htmlFor="ag-first">First billing date</label><input id="ag-first" type="date" style={input} value={draft.firstBillingDate} onChange={e => setDraft({ ...draft, firstBillingDate: e.target.value })} /></div>
            <div><label style={lab} htmlFor="ag-notice">Notice (days)</label><input id="ag-notice" inputMode="numeric" style={input} value={draft.noticeDays} onChange={e => setDraft({ ...draft, noticeDays: e.target.value })} /></div>
          </div>
          <label style={{ fontSize: 13, color: "var(--hf-text)", display: "flex", gap: 6, alignItems: "center" }}>
            <input type="checkbox" checked={draft.autoRenew} onChange={e => setDraft({ ...draft, autoRenew: e.target.checked })} /> Renews automatically unless notice is given
          </label>
          <div><label style={lab} htmlFor="ag-notes">Notes</label><textarea id="ag-notes" style={input} rows={2} value={draft.notes} onChange={e => setDraft({ ...draft, notes: e.target.value })} /></div>
          <div><label style={lab} htmlFor="ag-terms">Terms</label><textarea id="ag-terms" style={input} rows={2} value={draft.terms} onChange={e => setDraft({ ...draft, terms: e.target.value })} /></div>
          {draftProblem && <div style={bad}>{draftProblem}</div>}
          {dates && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{dates.length} billing date{dates.length === 1 ? "" : "s"}{total !== null ? ` · ${formatMoney(total)} over the term before VAT` : ""}</div>}
          <div style={{ display: "flex", gap: 8 }}>
            <button style={{ ...button, opacity: draftProblem || save.isPending ? 0.5 : 1 }} disabled={!!draftProblem || save.isPending} onClick={() => save.mutate(draft)}>Save draft</button>
            <button style={ghost} onClick={() => { setDraft(null); setError("") }}>Discard changes</button>
          </div>
        </div>
      )}
      {!editing && canManage && a.canEdit && <div><button style={ghost} onClick={() => setDraft(toDraft(a))}>Edit terms</button></div>}

      <div>
        <div style={{ ...lab, marginBottom: 6 }}>Billing dates (preview)</div>
        {a.schedule.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No billing dates fall inside the term.</div>}
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {a.schedule.map(e => <span key={e.date} style={{ fontSize: 12, border: "1px solid var(--hf-border)", borderRadius: 6, padding: "2px 8px", color: "var(--hf-text-muted)" }}>{e.date} · {formatMoney(e.amount)}</span>)}
        </div>
        {a.scheduleTruncated && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>This agreement has no end date, so only the next dates are shown.</div>}
      </div>

      {(a.status === "ACTIVE" || bills.length > 0) && (
        <div style={{ display: "grid", gap: 6 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ ...lab, marginBottom: 0 }}>Bills</div>
            {canManage && a.status === "ACTIVE" && <button style={ghost} disabled={generate.isPending} onClick={() => generate.mutate()}>Make the next bills</button>}
            {madeBills !== null && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{madeBills === 0 ? "Nothing new to make" : `${madeBills} bill${madeBills === 1 ? "" : "s"} made`}</span>}
          </div>
          {bills.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No bills made yet. They are made up to about two months ahead.</div>}
          {byUrgency(bills).map(b => <BillingEventRow key={b.id} event={b} canManage={canManage} onChanged={billsChanged} />)}
        </div>
      )}

      {actions.length > 0 && !editing && (
        <div style={{ display: "grid", gap: 8 }}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {actions.map(x => x.noteLabel || x.needsDate
              ? <button key={x.id} style={active?.id === x.id ? { ...button } : ghost} onClick={() => { setActive(active?.id === x.id ? null : x); setNote(""); setError("") }}>{x.label}</button>
              : <button key={x.id} style={button} disabled={act.isPending} onClick={() => act.mutate(x)}>{x.label}</button>)}
          </div>
          {active && (
            <div style={{ display: "grid", gap: 6 }}>
              {active.needsDate && (
                <div><label style={lab} htmlFor="ag-enddate">Ends on</label><input id="ag-enddate" type="date" style={input} value={endDate} onChange={e => setEndDate(e.target.value)} /></div>
              )}
              <div><label style={lab} htmlFor="ag-note">{active.noteLabel}</label><textarea id="ag-note" style={input} rows={2} value={note} onChange={e => setNote(e.target.value)} /></div>
              {problem && <div style={bad}>{problem}</div>}
              <div><button style={{ ...button, opacity: problem || act.isPending ? 0.5 : 1 }} disabled={!!problem || act.isPending} onClick={() => act.mutate(active)}>Confirm: {active.label.toLowerCase()}</button></div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
