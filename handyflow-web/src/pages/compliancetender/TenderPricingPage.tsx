// src/pages/compliancetender/TenderPricingPage.tsx
//
// Pricing for one of the tenant's own tenders (ADR-004): a cost-based price schedule, the markups and VAT, and the price they add up to. The server calculates; this shows it, and edits
// the schedule while the tender is still being prepared. Once the tender is submitted the page is read-only: the schedule is the record of what was priced.
import { useState } from "react"
import { useParams, Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { ArrowLeft, Calculator, Download, Lock, Pencil, Plus, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { COMPANY_SCOPE, CLIENT_SCOPE, usePricing, usePricingMutations, type PricingScope, type LineRequest, type PricingLine, type SettingsRequest, type TenderPricing } from "./pricing.api"
import {
  EMPTY_LINE, basisText, priceSegments, buildCsv, fmtZar, groupLines, lineToDraft, lockedText, marginText, parseLine, parseSettings, settingsChanged, settingsToDraft,
  type LineDraft, type SettingsDraft,
} from "./pricing.logic"
import LookupInput from "../../components/ui/LookupInput"
import { useRates } from "./rates.api"
import { CATEGORY_LABEL, DEFAULT_FILTER, filterRates, lineFromRate } from "./rates.logic"
import { UNITS } from "../../lookups/southAfrica"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 20, marginBottom: 16 }
const input: React.CSSProperties = { width: "100%", padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 14px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-sky-text-strong)" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-sky-text-strong)", color: "var(--hf-surface)", border: "1px solid transparent" }
const errText: React.CSSProperties = { fontSize: 11.5, color: "var(--hf-danger-text)", marginTop: 3 }
const th: React.CSSProperties = { textAlign: "left", fontSize: 10.5, fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--hf-text-faint)", padding: "6px 8px" }
const td: React.CSSProperties = { padding: "8px", fontSize: 13, color: "var(--hf-text)", verticalAlign: "top" }
const right: React.CSSProperties = { textAlign: "right", fontVariantNumeric: "tabular-nums" }

const apiMessage = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string } | null
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}

/** The company's own tenders. */
export default function TenderPricingPage() { return <PricingScreen scope={COMPANY_SCOPE} /> }

/** A client's tenders (complianceservices): the same screen on that module's addresses and permissions. */
export function ClientTenderPricingPage() { return <PricingScreen scope={CLIENT_SCOPE} /> }

function PricingScreen({ scope }: { scope: PricingScope }) {
  const { id } = useParams<{ id: string }>()
  const canManage = usePermission(scope.manage)
  const canAdmin = usePermission(scope.admin)
  const allowed = canManage || canAdmin
  const { data: tender } = useQuery<{ name: string; tenderNumber: string }>({
    queryKey: [scope === COMPANY_SCOPE ? "ct-tender" : "cs-tender", id], enabled: !!id,
    queryFn: async () => (await apiClient.get(`${scope.apiBase}/tenders/${id}`)).data,
  })
  const { data, isLoading, isError, refetch } = usePricing(id, allowed, scope)

  if (!allowed) {
    return <div style={{ maxWidth: 960, margin: "0 auto" }}><p role="alert" style={{ fontSize: 14, color: "var(--hf-text-muted)" }}>Pricing is only available to people who can manage tenders.</p></div>
  }

  return (
    <div style={{ maxWidth: 1100, margin: "0 auto" }}>
      <Link to={scope.tenderPage(id ?? "")} style={{ display: "inline-flex", alignItems: "center", gap: 6, color: "var(--hf-text-muted)", fontSize: 13, marginBottom: 18, textDecoration: "none" }}>
        <ArrowLeft size={15} /> Back to tender
      </Link>
      <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 20 }}>
        <div style={{ width: 46, height: 46, borderRadius: 12, background: "var(--hf-info-soft)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
          <Calculator size={21} style={{ color: "var(--hf-sky-text-strong)" }} aria-hidden="true" />
        </div>
        <div>
          <h1 style={{ margin: 0, fontSize: 20, fontWeight: 700, color: "var(--hf-text)" }}>Pricing</h1>
          <p style={{ margin: "2px 0 0", fontSize: 13, color: "var(--hf-text-faint)" }}>{tender ? `${tender.tenderNumber} · ${tender.name}` : " "}</p>
        </div>
      </div>

      {isLoading && <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading pricing…</p>}
      {isError && (
        <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>
          We couldn't load the pricing. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button>
        </p>
      )}
      {data && id && <PricingBody scope={scope} tenderId={id} pricing={data} tenderNumber={tender?.tenderNumber ?? "tender"} />}
    </div>
  )
}

function PricingBody({ scope, tenderId, pricing, tenderNumber }: { scope: PricingScope; tenderId: string; pricing: TenderPricing; tenderNumber: string }) {
  const b = pricing.breakdown
  const m = usePricingMutations(tenderId, scope)
  const [error, setError] = useState("")
  const fail = (e: unknown) => setError(apiMessage(e))

  function exportCsv() {
    const url = URL.createObjectURL(new Blob([buildCsv(pricing, tenderNumber)], { type: "text/csv;charset=utf-8" }))
    const a = document.createElement("a"); a.href = url; a.download = `${tenderNumber}-pricing.csv`; a.click(); URL.revokeObjectURL(url)
  }

  return (
    <>
      {!pricing.editable && (
        <p role="status" style={{ display: "flex", alignItems: "center", gap: 8, margin: "0 0 16px", padding: "10px 14px", background: "var(--hf-surface-sunken)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-text-secondary)" }}>
          <Lock size={14} aria-hidden="true" /> {lockedText(pricing.tenderStatus)}
        </p>
      )}
      {error && <p role="alert" style={{ margin: "0 0 16px", padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</p>}

      <section aria-label="Price summary" style={card}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12 }}>
          <Tile label="Direct cost" value={fmtZar(b.directCost)} />
          <Tile label="Price excluding VAT" value={fmtZar(b.priceExVat)} />
          <Tile label={pricing.settings.vatApplies ? `VAT ${pricing.settings.vatRatePct}%` : "VAT"} value={pricing.settings.vatApplies ? fmtZar(b.vat) : "Not added"} />
          <Tile label="Price including VAT" value={fmtZar(b.priceInclVat)} strong />
        </div>
        <PriceBar breakdown={b} />
        <p style={{ margin: "14px 0 0", fontSize: 12.5, color: "var(--hf-text-secondary)" }}>{marginText(b.marginPct)}.</p>
        {pricing.estimatedValue != null && (
          <p style={{ margin: "4px 0 0", fontSize: 12.5, color: "var(--hf-text-secondary)" }}>
            The tender's estimated value is {fmtZar(pricing.estimatedValue)}. It doesn't say whether that includes VAT, so compare it with the price that matches how it was worked out.
          </p>
        )}
        <div style={{ marginTop: 14 }}>
          <button type="button" style={btn} onClick={exportCsv} disabled={pricing.lines.length === 0}><Download size={14} aria-hidden="true" /> Export CSV</button>
        </div>
      </section>

      <SettingsCard pricing={pricing} onSave={r => { setError(""); m.saveSettings.mutate(r, { onError: fail }) }} saving={m.saveSettings.isPending} />

      <section aria-label="Price schedule" style={card}>
        <h2 style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 12px" }}>Price schedule</h2>
        <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-faint)" }}>What each item costs you. Markups are added below, not here.</p>
        {pricing.lines.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: "0 0 12px" }}>No items yet.{pricing.editable ? " Add the first item below." : ""}</p>}
        {groupLines(pricing).map(g => (
          <div key={g.section} style={{ marginBottom: 16 }}>
            <h3 style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>{g.section}</h3>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 560 }}>
                <thead><tr><th style={th}>Ref</th><th style={th}>Description</th><th style={th}>Unit</th><th style={{ ...th, ...right }}>Qty</th><th style={{ ...th, ...right }}>Unit cost</th><th style={{ ...th, ...right }}>Total</th>{pricing.editable && <th style={th}><span className="sr-only">Actions</span></th>}</tr></thead>
                <tbody>
                  {g.lines.map(l => <LineRow key={l.id} line={l} editable={pricing.editable} busy={m.updateLine.isPending || m.deleteLine.isPending}
                    onSave={req => new Promise<boolean>(res => { setError(""); m.updateLine.mutate({ id: l.id, req }, { onSuccess: () => res(true), onError: e => { fail(e); res(false) } }) })}
                    onDelete={() => { setError(""); m.deleteLine.mutate(l.id, { onError: fail }) }} />)}
                </tbody>
                <tfoot><tr><td colSpan={5} style={{ ...td, ...right, fontWeight: 700 }}>{g.section} subtotal</td><td style={{ ...td, ...right, fontWeight: 700 }}>{fmtZar(g.subtotal)}</td>{pricing.editable && <td />}</tr></tfoot>
              </table>
            </div>
          </div>
        ))}
        {pricing.editable && scope.rates && <RatePicker sections={[...new Set(pricing.lines.map(l => l.section))]} busy={m.addLine.isPending}
          onAdd={req => new Promise<boolean>(res => { setError(""); m.addLine.mutate(req, { onSuccess: () => res(true), onError: e => { fail(e); res(false) } }) })} />}
        {pricing.editable && <LineForm title="Add an item" submitLabel="Add item" sections={[...new Set(pricing.lines.map(l => l.section))]}
          busy={m.addLine.isPending} resetOnSuccess
          onSubmit={req => new Promise<boolean>(res => { setError(""); m.addLine.mutate(req, { onSuccess: () => res(true), onError: e => { fail(e); res(false) } }) })} />}
      </section>

      <section aria-label="How the price is built" style={card}>
        <h2 style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 10px" }}>How the price is built</h2>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <tbody>
            <BuildRow label="Direct cost" value={b.directCost} />
            <BuildRow label={`Overhead ${pricing.settings.overheadPct}%`} value={b.overhead} />
            <BuildRow label={`Contingency ${pricing.settings.contingencyPct}%`} value={b.contingency} />
            <BuildRow label={`Profit ${pricing.settings.profitPct}%`} value={b.profit} />
            <BuildRow label="Price excluding VAT" value={b.priceExVat} strong />
            <BuildRow label={pricing.settings.vatApplies ? `VAT ${pricing.settings.vatRatePct}%` : "VAT (not added)"} value={b.vat} />
            <BuildRow label="Price including VAT" value={b.priceInclVat} strong />
          </tbody>
        </table>
        <p style={{ margin: "10px 0 0", fontSize: 12, color: "var(--hf-text-faint)" }}>{basisText(pricing.settings)}</p>
      </section>
    </>
  )
}

const SEGMENT_COLOURS: Record<string, string> = {
  direct: "var(--hf-sky-solid-strong)", overhead: "var(--hf-info-text, var(--hf-sky-text-strong))", contingency: "var(--hf-warning-text)", profit: "var(--hf-success-text-strong)", vat: "var(--hf-text-faint)",
}

/** One bar showing what the price is made of; the legend carries the numbers so colour is never the only signal. */
function PriceBar({ breakdown }: { breakdown: TenderPricing["breakdown"] }) {
  const segs = priceSegments(breakdown)
  if (segs.length === 0) return null
  return (
    <div style={{ marginTop: 16 }}>
      <div aria-hidden="true" style={{ display: "flex", height: 10, borderRadius: 999, overflow: "hidden", background: "var(--hf-surface-sunken)" }}>
        {segs.map(g => <div key={g.key} style={{ width: `${g.pct}%`, background: SEGMENT_COLOURS[g.key] }} />)}
      </div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "6px 16px", marginTop: 8 }}>
        {segs.map(g => (
          <span key={g.key} style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--hf-text-secondary)" }}>
            <span aria-hidden="true" style={{ width: 9, height: 9, borderRadius: 3, background: SEGMENT_COLOURS[g.key] }} />{g.label} {g.pct}%
          </span>
        ))}
      </div>
    </div>
  )
}

function Tile({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div style={{ background: strong ? "var(--hf-info-soft)" : "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 14px" }}>
      <div style={{ fontSize: 10.5, fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--hf-text-faint)", marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: strong ? 22 : 18, fontWeight: 800, color: strong ? "var(--hf-sky-text-strong)" : "var(--hf-text)", fontVariantNumeric: "tabular-nums" }}>{value}</div>
    </div>
  )
}

function BuildRow({ label, value, strong }: { label: string; value: number; strong?: boolean }) {
  return (
    <tr style={{ borderTop: strong ? "1px solid var(--hf-border)" : undefined }}>
      <td style={{ ...td, fontWeight: strong ? 700 : 500 }}>{label}</td>
      <td style={{ ...td, ...right, fontWeight: strong ? 700 : 500 }}>{fmtZar(value)}</td>
    </tr>
  )
}

function SettingsCard({ pricing, onSave, saving }: { pricing: TenderPricing; onSave: (r: SettingsRequest) => void; saving: boolean }) {
  const [draft, setDraft] = useState<SettingsDraft>(() => settingsToDraft(pricing.settings))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const set = (patch: Partial<SettingsDraft>) => setDraft(d => ({ ...d, ...patch }))
  const changed = settingsChanged(draft, pricing.settings)
  const ro = !pricing.editable
  const field = (key: "overheadPct" | "contingencyPct" | "profitPct", label: string) => (
    <div>
      <label htmlFor={`pr-${key}`} style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>{label} (%)</label>
      <input id={`pr-${key}`} inputMode="decimal" value={draft[key]} disabled={ro} onChange={e => set({ [key]: e.target.value })} aria-invalid={!!errors[key]} style={input} />
      {errors[key] && <div role="alert" style={errText}>{errors[key]}</div>}
    </div>
  )
  return (
    <section aria-label="Markups and VAT" style={card}>
      <h2 style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 12px" }}>Markups and VAT</h2>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 14 }}>
        {field("overheadPct", "Overhead")}
        {field("contingencyPct", "Contingency")}
        {field("profitPct", "Profit")}
        <div>
          <span style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>VAT</span>
          <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--hf-text)", padding: "6px 0" }}>
            <input type="checkbox" checked={draft.vatApplies} disabled={ro} onChange={e => set({ vatApplies: e.target.checked })} />
            Add VAT at {pricing.settings.vatRatePct}%
          </label>
        </div>
      </div>
      <label htmlFor="pr-notes" style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", margin: "14px 0 4px" }}>Notes</label>
      <textarea id="pr-notes" rows={2} value={draft.notes} disabled={ro} onChange={e => set({ notes: e.target.value })} style={{ ...input, resize: "vertical" }} />
      {errors.notes && <div role="alert" style={errText}>{errors.notes}</div>}
      {!ro && (
        <div style={{ marginTop: 12 }}>
          <button type="button" style={primary} disabled={saving || !changed}
            onClick={() => { const p = parseSettings(draft); if (!p.ok) { setErrors(p.errors); return } setErrors({}); onSave(p.value) }}>
            {saving ? "Saving…" : "Save markups"}
          </button>
        </div>
      )}
    </section>
  )
}
function LineRow({ line, editable, busy, onSave, onDelete }: { line: PricingLine; editable: boolean; busy: boolean; onSave: (r: LineRequest) => Promise<boolean>; onDelete: () => void }) {
  const [editing, setEditing] = useState(false)
  if (editing) {
    return (
      <tr><td colSpan={7} style={{ padding: "8px 0" }}>
        <LineForm title={`Change ${line.description}`} submitLabel="Save item" initial={lineToDraft(line)} sections={[]} busy={busy}
          onCancel={() => setEditing(false)}
          onSubmit={async req => { const ok = await onSave(req); if (ok) setEditing(false); return ok }} />
      </td></tr>
    )
  }
  return (
    <tr style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
      <td style={td}>{line.itemRef ?? ""}</td>
      <td style={td}>{line.description}</td>
      <td style={td}>{line.unit ?? ""}</td>
      <td style={{ ...td, ...right }}>{line.quantity.toLocaleString("en-ZA", { maximumFractionDigits: 3 })}</td>
      <td style={{ ...td, ...right }}>{fmtZar(line.unitCost)}</td>
      <td style={{ ...td, ...right, fontWeight: 600 }}>{fmtZar(line.lineTotal)}</td>
      {editable && (
        <td style={{ ...td, whiteSpace: "nowrap" }}>
          <button type="button" aria-label={`Change ${line.description}`} onClick={() => setEditing(true)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)" }}><Pencil size={14} /></button>
          <button type="button" aria-label={`Remove ${line.description}`} disabled={busy} onClick={() => { if (window.confirm(`Remove "${line.description}" from the schedule?`)) onDelete() }} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={14} /></button>
        </td>
      )}
    </tr>
  )
}

/** Copies a rate from the library onto the schedule. The line keeps the cost it had when added; later changes to the rate do not move it. */
function RatePicker({ sections, busy, onAdd }: { sections: string[]; busy: boolean; onAdd: (r: LineRequest) => Promise<boolean> }) {
  const { data: rates = [] } = useRates(true)
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState("")
  const [section, setSection] = useState("")
  const [qty, setQty] = useState<Record<string, string>>({})
  const [msg, setMsg] = useState("")
  if (rates.filter(r => r.active).length === 0) return null
  const matches = filterRates(rates, { ...DEFAULT_FILTER, search }).slice(0, 12)
  async function add(id: string) {
    const rate = rates.find(r => r.id === id)!
    const req = lineFromRate(rate, qty[id] ?? "", section)
    if (!req) { setMsg("Enter the quantity first (a number, up to 3 decimal places)."); return }
    setMsg("")
    if (await onAdd(req)) { setQty(q => ({ ...q, [id]: "" })); setMsg(`Added ${rate.description}.`) }
  }
  return (
    <div style={{ marginBottom: 12 }}>
      <button type="button" style={btn} aria-expanded={open} onClick={() => setOpen(o => !o)}>{open ? "Hide rates library" : "Add from rates library"}</button>
      {open && (
        <div aria-label="Rates library" style={{ marginTop: 10, padding: 14, border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface-sunken)" }}>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 10 }}>
            <input aria-label="Search the rates library" placeholder="Search rates" value={search} onChange={e => setSearch(e.target.value)} style={{ ...input, flex: "1 1 200px", width: "auto" }} />
            <input aria-label="Section for added rates" list="pr-rate-sections" placeholder="Section (General)" value={section} onChange={e => setSection(e.target.value)} style={{ ...input, flex: "0 1 200px", width: "auto" }} />
            <datalist id="pr-rate-sections">{sections.map(s => <option key={s} value={s} />)}</datalist>
          </div>
          {matches.length === 0 && <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-faint)" }}>No rates match.</p>}
          {matches.map(r => (
            <div key={r.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", flexWrap: "wrap" }}>
              <div style={{ flex: "1 1 220px", fontSize: 13, color: "var(--hf-text)" }}>
                <strong>{r.description}</strong>
                <span style={{ color: "var(--hf-text-faint)" }}> · {CATEGORY_LABEL[r.category]}{r.supplier ? ` · ${r.supplier}` : ""}</span>
              </div>
              <div style={{ fontSize: 13, fontVariantNumeric: "tabular-nums" }}>{fmtZar(r.unitCost)}{r.unit ? ` / ${r.unit}` : ""}</div>
              <input aria-label={`Quantity of ${r.description}`} inputMode="decimal" placeholder="Qty" value={qty[r.id] ?? ""} onChange={e => setQty(q => ({ ...q, [r.id]: e.target.value }))} style={{ ...input, width: 80 }} />
              <button type="button" style={primary} disabled={busy} aria-label={`Add ${r.description} to the schedule`} onClick={() => add(r.id)}><Plus size={13} aria-hidden="true" /> Add</button>
            </div>
          ))}
          {msg && <p role="status" style={{ margin: "8px 0 0", fontSize: 12.5, color: "var(--hf-text-secondary)" }}>{msg}</p>}
        </div>
      )}
    </div>
  )
}

function LineForm({ title, submitLabel, initial, sections, busy, resetOnSuccess, onSubmit, onCancel }: {
  title: string; submitLabel: string; initial?: LineDraft; sections: string[]; busy: boolean; resetOnSuccess?: boolean
  onSubmit: (r: LineRequest) => Promise<boolean>; onCancel?: () => void
}) {
  const [d, setD] = useState<LineDraft>(initial ?? EMPTY_LINE)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const set = (patch: Partial<LineDraft>) => setD(x => ({ ...x, ...patch }))
  const listId = `pr-sections-${title.replace(/\W+/g, "-")}`
  const f = (key: keyof LineDraft, label: string, extra?: React.InputHTMLAttributes<HTMLInputElement>) => (
    <div>
      <label htmlFor={`${listId}-${key}`} style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>{label}</label>
      {key === "unit"
        ? <LookupInput id={`${listId}-${key}`} value={d[key]} options={UNITS} list="UNITS" onChange={v => set({ [key]: v })} aria-invalid={!!errors[key]} style={input} placeholder={extra?.placeholder} />
        : <input id={`${listId}-${key}`} value={d[key]} onChange={e => set({ [key]: e.target.value })} aria-invalid={!!errors[key]} style={input} {...extra} />}
      {errors[key] && <div role="alert" style={errText}>{errors[key]}</div>}
    </div>
  )
  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const p = parseLine(d)
    if (!p.ok) { setErrors(p.errors); return }
    setErrors({})
    const ok = await onSubmit(p.value)
    if (ok && resetOnSuccess) setD(x => ({ ...EMPTY_LINE, section: x.section }))   // keep the section: items are usually entered a section at a time
  }
  return (
    <form onSubmit={submit} aria-label={title} style={{ padding: 14, border: "1px dashed var(--hf-border)", borderRadius: 10, background: "var(--hf-surface-sunken)" }}>
      <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 10, display: "flex", alignItems: "center", gap: 6 }}>{!initial && <Plus size={14} aria-hidden="true" />}{title}</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 10 }}>
        {f("section", "Section", { list: listId, placeholder: "General" })}
        {f("itemRef", "Ref")}
        {f("description", "Description")}
        {f("unit", "Unit", { placeholder: "m, no, sum" })}
        {f("quantity", "Quantity", { inputMode: "decimal" })}
        {f("unitCost", "Unit cost (R)", { inputMode: "decimal" })}
      </div>
      <datalist id={listId}>{sections.map(s => <option key={s} value={s} />)}</datalist>
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button type="submit" style={primary} disabled={busy}>{busy ? "Saving…" : submitLabel}</button>
        {onCancel && <button type="button" style={btn} onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  )
}
