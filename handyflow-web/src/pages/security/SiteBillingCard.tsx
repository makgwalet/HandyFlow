// src/pages/security/SiteBillingCard.tsx
//
// What a site is billed: its contract rates, the billing preview for a month, and invoice drafts saved from that preview.
// SECURITY_ADMIN only (the server enforces it too). Nothing here is an invoice and VAT is not calculated.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight, Receipt } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import InvoiceDraftDetail from "./InvoiceDraftDetail"
import { useCompanyZone, getCompanyZone, dayIn } from "./companyZone"
import { canGoNextMonth, lastMonthSast, monthTitle, shiftMonth } from "./monthlyPackLogic"
import {
  BASIS_LABEL, DRAFT_LABEL, DRAFT_TONE, RATE_STATE_LABEL, acknowledgementText, approveBlock, billingErrorText, canVoid, createBlock, formatCents, hoursText,
  needsAcknowledgement, periodText, rateBody, rateFormError, rateState, rateText, reasonError,
  type BillingPreview, type InvoiceDraft, type RateForm, type SiteRate,
} from "./billingLogic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginTop: 16 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const sub: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, margin: "16px 0 8px" }
const navBtn: React.CSSProperties = { padding: "4px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", display: "inline-flex", alignItems: "center" }
const btn: React.CSSProperties = { padding: "6px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0 }
const row: React.CSSProperties = { display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }
const alertStyle: React.CSSProperties = { fontSize: 13, color: "var(--hf-danger-text)", marginTop: 6 }

const unwrap = <T,>(r: { data?: { data?: T } }): T => r.data?.data as T
const today = () => dayIn(getCompanyZone())

export default function SiteBillingCard({ siteId }: { siteId: string }) {
  const canAdmin = usePermission("SECURITY_ADMIN")
  const { ready } = useCompanyZone()
  const [picked, setPicked] = useState<string | null>(null)
  const [basis, setBasis] = useState<"SCHEDULED" | "ACTUAL">("SCHEDULED")
  if (!canAdmin) return null
  const month = picked ?? lastMonthSast()
  return (
    <section aria-label="Billing" style={card}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700 }}><Receipt size={16} /> Billing</div>
      <div style={{ ...muted, marginTop: 4 }}>Contract rates and what they add up to. These are drafts for checking, not invoices, and VAT is not calculated.</div>
      <Rates siteId={siteId} ready={ready} />
      <Preview siteId={siteId} month={month} basis={basis} ready={ready}
        onMonth={d => setPicked(shiftMonth(month, d))} onBasis={setBasis} />
      <Drafts siteId={siteId} month={month} basis={basis} ready={ready} />
    </section>
  )
}

function Rates({ siteId, ready }: { siteId: string; ready: boolean }) {
  const qc = useQueryClient()
  const key = ["site-rates", siteId]
  const { data, isLoading, error } = useQuery<SiteRate[]>({ queryKey: key, enabled: ready, queryFn: async () => unwrap(await apiClient.get(`/api/v1/security/sites/${siteId}/rates`)) })
  const [open, setOpen] = useState(false)
  const blank: RateForm = { type: "HOURLY", amount: "", from: "", to: "", note: "" }
  const [f, setF] = useState<RateForm>(blank)
  const [vat, setVat] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const [ending, setEnding] = useState<string | null>(null)
  const [lastDay, setLastDay] = useState("")
  const done = () => { qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["site-billing-preview", siteId] }) }
  const add = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/security/sites/${siteId}/rates`, rateBody(f, vat)),
    onSuccess: () => { done(); setOpen(false); setF(blank); setVat(false); setProblem(null) },
    onError: e => setProblem(billingErrorText(e, "The rate could not be saved.")),
  })
  const end = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/security/sites/${siteId}/rates/${id}/end`, { lastDay }),
    onSuccess: () => { done(); setEnding(null); setLastDay(""); setProblem(null) },
    onError: e => setProblem(billingErrorText(e, "The rate could not be ended.")),
  })
  const submit = () => { const bad = rateFormError(f); setProblem(bad); if (!bad) add.mutate() }
  const t = today()
  const set = <K extends keyof RateForm>(k: K, v: RateForm[K]) => setF(p => ({ ...p, [k]: v }))

  return (
    <div>
      <div style={sub}>Contract rates</div>
      {isLoading && <div style={muted}>Loading…</div>}
      {error && <div role="alert" style={alertStyle}>The rates could not be loaded.</div>}
      {data && data.length === 0 && <div style={muted}>No rates yet. Without a rate the billing preview prices nothing.</div>}
      {data?.map(r => {
        const st = rateState(r, t)
        return (
          <div key={r.id} style={row}>
            <strong>{rateText(r)}</strong>
            <Chip tone={st === "CURRENT" ? "ok" : st === "FUTURE" ? "info" : "neutral"}>{RATE_STATE_LABEL[st]}</Chip>
            <Chip tone="neutral">{r.vatInclusive ? "Includes VAT" : "Excludes VAT"}</Chip>
            <span style={muted}>{periodText(r)}{r.note ? ` · ${r.note}` : ""}</span>
            {st !== "ENDED" && r.effectiveTo == null && ending !== r.id && <button type="button" style={{ ...btn, marginLeft: "auto" }} onClick={() => { setEnding(r.id); setLastDay(""); setProblem(null) }}>End this rate</button>}
            {ending === r.id && (
              <span style={{ marginLeft: "auto", display: "inline-flex", gap: 6, alignItems: "center" }}>
                <label style={muted}>Last day <input type="date" aria-label="Last day of this rate" style={field} value={lastDay} onChange={e => setLastDay(e.target.value)} /></label>
                <button type="button" style={btn} disabled={!lastDay || end.isPending} onClick={() => end.mutate(r.id)}>Confirm</button>
                <button type="button" style={btn} onClick={() => setEnding(null)}>Cancel</button>
              </span>
            )}
          </div>
        )
      })}
      {!open && <button type="button" style={{ ...btn, marginTop: 8 }} onClick={() => setOpen(true)}>Add a rate</button>}
      {open && (
        <div style={{ ...row, borderTop: "1px solid var(--hf-border-subtle)", marginTop: 8 }}>
          <select aria-label="Rate type" style={field} value={f.type} onChange={e => set("type", e.target.value as RateForm["type"])}>
            <option value="HOURLY">Per hour</option><option value="MONTHLY_FIXED">Fixed per month</option>
          </select>
          <input aria-label="Amount in rand" inputMode="decimal" placeholder="Amount, e.g. 185.50" style={{ ...field, width: 150 }} value={f.amount} onChange={e => set("amount", e.target.value)} />
          <label style={muted}>First day <input type="date" aria-label="First day" style={field} value={f.from} onChange={e => set("from", e.target.value)} /></label>
          <label style={muted}>Last day (optional) <input type="date" aria-label="Last day (optional)" style={field} value={f.to} onChange={e => set("to", e.target.value)} /></label>
          <label style={muted}><input type="checkbox" checked={vat} onChange={e => setVat(e.target.checked)} /> Amount includes VAT</label>
          <input aria-label="Note" placeholder="Note (optional)" style={{ ...field, flex: 1, minWidth: 160 }} value={f.note} onChange={e => set("note", e.target.value)} />
          <button type="button" style={btn} disabled={add.isPending} onClick={submit}>Save rate</button>
          <button type="button" style={btn} onClick={() => { setOpen(false); setProblem(null) }}>Cancel</button>
        </div>
      )}
      {problem && <div role="alert" style={alertStyle}>{problem}</div>}
    </div>
  )
}

function Preview({ siteId, month, basis, ready, onMonth, onBasis }:
  { siteId: string; month: string; basis: "SCHEDULED" | "ACTUAL"; ready: boolean; onMonth: (d: number) => void; onBasis: (b: "SCHEDULED" | "ACTUAL") => void }) {
  const { data, isLoading, error } = useQuery<BillingPreview>({
    queryKey: ["site-billing-preview", siteId, month, basis], enabled: ready,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/security/sites/${siteId}/billing-preview`, { params: { month, basis } })),
  })
  const ok = !!data && Array.isArray(data.lines) && typeof data.totalCents === "number"
  return (
    <div>
      <div style={{ ...sub, display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
        <span>Billing preview</span>
        <button type="button" aria-label="Previous billing month" style={navBtn} onClick={() => onMonth(-1)}><ChevronLeft size={14} /></button>
        <span style={{ textTransform: "none", fontSize: 13, minWidth: 110, textAlign: "center" }}>{monthTitle(month)}</span>
        <button type="button" aria-label="Next billing month" style={{ ...navBtn, opacity: canGoNextMonth(month) ? 1 : 0.4 }} disabled={!canGoNextMonth(month)} onClick={() => onMonth(1)}><ChevronRight size={14} /></button>
        <select aria-label="Hours to bill" style={field} value={basis} onChange={e => onBasis(e.target.value as "SCHEDULED" | "ACTUAL")}>
          {Object.entries(BASIS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </div>
      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data && !ok)) && <div role="alert" style={alertStyle}>{billingErrorText(error, "The billing preview could not be loaded.")}</div>}
      {ok && data && (
        <>
          {data.inProgress && <div style={{ marginBottom: 6 }}><Chip tone="info">Month to date</Chip></div>}
          {data.lines.length === 0 ? <div style={muted}>No rate covers this month, so nothing is priced.</div> : (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)", fontSize: 12 }}><th scope="col">Rate</th><th scope="col">Hours</th><th scope="col">Days in force</th><th scope="col" style={{ textAlign: "right" }}>Amount</th></tr></thead>
              <tbody>{data.lines.map(l => (
                <tr key={l.rateId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td>{rateText({ type: l.type as SiteRate["type"], amountCents: l.rateCents, currency: data.currency })}</td>
                  <td>{l.type === "HOURLY" ? hoursText(l.hours) : "Fixed"}</td><td>{l.daysInForce}</td>
                  <td style={{ textAlign: "right" }}>{formatCents(l.amountCents, data.currency)}</td>
                </tr>))}</tbody>
            </table>
          )}
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: 8, fontWeight: 700 }}>
            <span>Total ({hoursText(data.billedHours)} billed)</span><span data-testid="preview-total">{formatCents(data.totalCents, data.currency)}</span>
          </div>
          {data.unpricedHours > 0 && <div style={{ ...muted, color: "var(--hf-warning-text)" }}>{hoursText(data.unpricedHours)} fall on days with no rate and are not priced.</div>}
          <ul style={{ ...muted, margin: "6px 0 0", paddingLeft: 18 }}>{data.warnings.map(w => <li key={w}>{w}</li>)}</ul>
        </>
      )}
    </div>
  )
}

function Drafts({ siteId, month, basis, ready }: { siteId: string; month: string; basis: "SCHEDULED" | "ACTUAL"; ready: boolean }) {
  const qc = useQueryClient()
  const key = ["site-invoice-drafts", siteId]
  const { data, isLoading, error } = useQuery<InvoiceDraft[]>({ queryKey: key, enabled: ready, queryFn: async () => unwrap(await apiClient.get(`/api/v1/security/sites/${siteId}/invoice-drafts`)) })
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState<string | null>(null)
  const [acked, setAcked] = useState<Record<string, boolean>>({})
  const [voiding, setVoiding] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [opened, setOpened] = useState<string | null>(null)
  const base = `/api/v1/security/sites/${siteId}/invoice-drafts`
  const fail = (fallback: string) => (e: unknown) => setProblem(billingErrorText(e, fallback))
  const ok = () => { setProblem(null); qc.invalidateQueries({ queryKey: key }) }
  const create = useMutation({ mutationFn: async () => apiClient.post(base, { month, basis, note: note.trim() || null }), onSuccess: () => { ok(); setNote("") }, onError: fail("The draft could not be saved.") })
  const approve = useMutation({ mutationFn: async (d: InvoiceDraft) => apiClient.post(`${base}/${d.id}/approve`, { acknowledgeWarnings: !!acked[d.id] }), onSuccess: ok, onError: fail("The draft could not be approved.") })
  const voidIt = useMutation({ mutationFn: async (id: string) => apiClient.post(`${base}/${id}/void`, { reason: reason.trim() }), onSuccess: () => { ok(); setVoiding(null); setReason("") }, onError: fail("The draft could not be voided.") })
  const block = createBlock(month, data ?? [])

  return (
    <div>
      <div style={sub}>Invoice drafts</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <input aria-label="Draft note" placeholder="Note (optional)" style={{ ...field, flex: 1, minWidth: 160 }} value={note} onChange={e => setNote(e.target.value)} />
        <button type="button" style={{ ...btn, opacity: block ? 0.5 : 1 }} disabled={!!block || create.isPending || !data} onClick={() => create.mutate()}>
          Save {monthTitle(month)} ({BASIS_LABEL[basis].toLowerCase()}) as a draft
        </button>
      </div>
      {block && <div style={muted}>{block}</div>}
      {isLoading && <div style={muted}>Loading…</div>}
      {error && <div role="alert" style={alertStyle}>The invoice drafts could not be loaded.</div>}
      {data && data.length === 0 && <div style={{ ...muted, marginTop: 6 }}>No drafts yet.</div>}
      {data?.map(d => {
        const stop = approveBlock(d)
        const needTick = needsAcknowledgement(d)
        return (
          <div key={d.id} style={{ ...row, alignItems: "flex-start" }}>
            <div style={{ flex: 1, minWidth: 200 }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <strong>{monthTitle(d.month)}</strong><span>{formatCents(d.totalCents, d.currency)}</span>
                <Chip tone={DRAFT_TONE[d.status]}>{DRAFT_LABEL[d.status]}</Chip>
                <span style={muted}>{BASIS_LABEL[d.basis] ?? d.basis}</span>
              </div>
              {d.note && <div style={muted}>{d.note}</div>}
              {d.status === "VOID" && d.voidReason && <div style={muted}>Voided: {d.voidReason}</div>}
              {d.status === "DRAFT" && stop && <div style={muted}>{stop}</div>}
              {d.status === "DRAFT" && !stop && needTick && (
                <label style={{ ...muted, display: "block", marginTop: 4 }}>
                  <input type="checkbox" checked={!!acked[d.id]} onChange={e => setAcked(p => ({ ...p, [d.id]: e.target.checked }))} /> {acknowledgementText(d)}
                </label>
              )}
              {opened === d.id && <InvoiceDraftDetail siteId={siteId} draftId={d.id} />}
              {voiding === d.id && (
                <div style={{ display: "flex", gap: 6, marginTop: 6, flexWrap: "wrap" }}>
                  <input aria-label="Reason for voiding" placeholder="Why is this being voided?" style={{ ...field, flex: 1, minWidth: 180 }} value={reason} onChange={e => setReason(e.target.value)} />
                  <button type="button" style={btn} disabled={!!reasonError(reason) || voidIt.isPending} onClick={() => voidIt.mutate(d.id)}>Void it</button>
                  <button type="button" style={btn} onClick={() => setVoiding(null)}>Cancel</button>
                </div>
              )}
            </div>
            <div style={{ display: "flex", gap: 6 }}>
              <button type="button" style={btn} aria-expanded={opened === d.id} aria-label={`${opened === d.id ? "Hide" : "Show"} details of the ${monthTitle(d.month)} draft`} onClick={() => setOpened(o => o === d.id ? null : d.id)}>{opened === d.id ? "Hide details" : "Details"}</button>
              {d.status === "DRAFT" && <button type="button" style={{ ...btn, opacity: stop || (needTick && !acked[d.id]) ? 0.5 : 1 }} disabled={!!stop || (needTick && !acked[d.id]) || approve.isPending} onClick={() => approve.mutate(d)}>Approve</button>}
              {canVoid(d) && voiding !== d.id && <button type="button" style={btn} onClick={() => { setVoiding(d.id); setReason(""); setProblem(null) }}>Void</button>}
            </div>
          </div>
        )
      })}
      {problem && <div role="alert" style={alertStyle}>{problem}</div>}
    </div>
  )
}
