// src/pages/compliancetender/RatesTab.tsx
//
// The company's rates library: costs it can copy onto a tender's price schedule. Add by hand, or import a supplier's price list (CSV): "Check the file" shows what would change
// (new rates, new prices, rows it cannot read) before anything is saved. Costs are commercially sensitive, so the whole screen needs MANAGE or ADMIN.
import { useMemo, useState } from "react"
import { Coins, Download, Pencil, Plus, Search, Trash2, Upload, X } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import LookupInput from "../../components/ui/LookupInput"
import { UNITS } from "../../lookups/southAfrica"
import { fmtZar } from "./pricing.logic"
import { useRateMutations, useRates, type ImportResult, type Rate, type RateCategory, type RateRequest } from "./rates.api"
import {
  CATEGORIES, CATEGORY_LABEL, DEFAULT_FILTER, EMPTY_RATE, changeText, filterRates, importSummary, importable, parseRate, rateToDraft, suppliersOf,
  type RateDraft, type RateFilter,
} from "./rates.logic"

const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 14px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-sky-text-strong)" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-sky-text-strong)", color: "var(--hf-surface)", border: "1px solid transparent" }
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const lbl: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const th: React.CSSProperties = { textAlign: "left", fontSize: 10.5, fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--hf-text-faint)", padding: "6px 8px" }
const td: React.CSSProperties = { padding: "9px 8px", fontSize: 13, color: "var(--hf-text)", verticalAlign: "top", borderTop: "1px solid var(--hf-border)" }
const errText: React.CSSProperties = { fontSize: 11.5, color: "var(--hf-danger-text)", marginTop: 3 }

const apiMessage = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string } | null
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}

export default function RatesTab() {
  const manage = usePermission("COMPLIANCE_MANAGE")
  const admin = usePermission("COMPLIANCE_ADMIN")
  const allowed = manage || admin
  const { data: rates = [], isLoading, isError, refetch } = useRates(allowed)
  const m = useRateMutations()
  const [filter, setFilter] = useState<RateFilter>(DEFAULT_FILTER)
  const [editing, setEditing] = useState<Rate | "new" | null>(null)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState("")

  const shown = useMemo(() => filterRates(rates, filter), [rates, filter])
  const suppliers = useMemo(() => suppliersOf(rates), [rates])

  if (!allowed) return <p role="alert" style={{ fontSize: 14, color: "var(--hf-text-muted)" }}>The rates library is only available to people who can manage tenders.</p>

  const active = rates.filter(r => r.active).length
  const moved = rates.filter(r => changeText(r) !== null).length

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        <StatTile label="Rates" value={active} hint={rates.length > active ? `${rates.length - active} switched off` : undefined} icon={<Coins size={15} aria-hidden="true" />} />
        <StatTile label="Suppliers" value={suppliers.length} />
        <StatTile label="Price changed" value={moved} tone={moved > 0 ? "warn" : "neutral"} hint="since the previous cost" />
      </div>

      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <div style={{ position: "relative", flex: "1 1 220px", minWidth: 180 }}>
          <Search size={14} aria-hidden="true" style={{ position: "absolute", left: 10, top: 10, color: "var(--hf-text-faint)" }} />
          <input aria-label="Search rates" placeholder="Search description, code or supplier" value={filter.search} onChange={e => setFilter({ ...filter, search: e.target.value })} style={{ ...input, paddingLeft: 30 }} />
        </div>
        <select aria-label="Category" value={filter.category} onChange={e => setFilter({ ...filter, category: e.target.value as RateCategory | "ALL" })} style={{ ...input, width: 150 }}>
          <option value="ALL">All categories</option>
          {CATEGORIES.map(c => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
        </select>
        <select aria-label="Supplier" value={filter.supplier} onChange={e => setFilter({ ...filter, supplier: e.target.value })} style={{ ...input, width: 170 }}>
          <option value="ALL">All suppliers</option>
          {suppliers.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <label style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12.5, color: "var(--hf-text-secondary)" }}>
          <input type="checkbox" checked={filter.showInactive} onChange={e => setFilter({ ...filter, showInactive: e.target.checked })} /> Show switched off
        </label>
        <div style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
          <button type="button" style={btn} onClick={() => setImporting(true)}><Upload size={14} aria-hidden="true" /> Import price list</button>
          <button type="button" style={primary} onClick={() => { setError(""); setEditing("new") }}><Plus size={14} aria-hidden="true" /> Add rate</button>
        </div>
      </div>

      {error && <div role="alert" style={{ marginBottom: 12, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      {isLoading && <p style={{ color: "var(--hf-text-faint)" }}>Loading rates…</p>}
      {isError && <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load the rates. <button type="button" style={btn} onClick={() => refetch()}>Try again</button></p>}

      {!isLoading && !isError && rates.length === 0 && (
        <div style={{ padding: 28, textAlign: "center", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-muted)", fontSize: 13.5 }}>
          No rates yet. Add one, or import a supplier's price list saved as CSV. Rates you add here can be copied onto any tender's price schedule.
        </div>
      )}

      {shown.length > 0 && (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 720 }}>
            <thead><tr><th style={th}>Category</th><th style={th}>Description</th><th style={th}>Unit</th><th style={th}>Supplier</th><th style={{ ...th, textAlign: "right" }}>Unit cost</th><th style={th}><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {shown.map(r => {
                const ch = changeText(r)
                return (
                  <tr key={r.id} style={{ opacity: r.active ? 1 : 0.55 }}>
                    <td style={td}><Chip tone="neutral">{CATEGORY_LABEL[r.category]}</Chip></td>
                    <td style={td}>
                      <div style={{ fontWeight: 600 }}>{r.description}</div>
                      {(r.itemRef || r.notes) && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{[r.itemRef, r.notes].filter(Boolean).join(" · ")}</div>}
                      {!r.active && <Chip tone="neutral">Switched off</Chip>}
                    </td>
                    <td style={td}>{r.unit || "—"}</td>
                    <td style={td}>{r.supplier || "—"}</td>
                    <td style={{ ...td, textAlign: "right", fontVariantNumeric: "tabular-nums" }}>
                      <div style={{ fontWeight: 600 }}>{fmtZar(r.unitCost)}</div>
                      {ch && <div style={{ fontSize: 11.5, color: "var(--hf-warning-text)" }}>{ch}</div>}
                    </td>
                    <td style={{ ...td, whiteSpace: "nowrap", textAlign: "right" }}>
                      <button type="button" aria-label={`Edit ${r.description}`} title="Edit" style={{ ...btn, padding: "5px 8px" }} onClick={() => { setError(""); setEditing(r) }}><Pencil size={13} aria-hidden="true" /></button>{" "}
                      <button type="button" aria-label={`Delete ${r.description}`} title="Delete" style={{ ...btn, padding: "5px 8px", color: "var(--hf-danger-text)" }}
                        onClick={() => { if (window.confirm(`Delete "${r.description}"? Price schedules that already use it are not changed.`)) { setError(""); m.remove.mutate(r.id, { onError: e => setError(apiMessage(e)) }) } }}><Trash2 size={13} aria-hidden="true" /></button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {rates.length > 0 && shown.length === 0 && <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No rates match.</p>}

      {editing && <RateModal rate={editing === "new" ? null : editing} suppliers={suppliers} busy={m.create.isPending || m.update.isPending} onClose={() => setEditing(null)}
        onSave={(req, done) => {
          const opts = { onSuccess: () => { setEditing(null); done(null) }, onError: (e: unknown) => done(apiMessage(e)) }
          if (editing === "new") m.create.mutate(req, opts)
          else m.update.mutate({ id: editing.id, req }, opts)
        }} />}
      {importing && <ImportModal onClose={() => setImporting(false)} run={(r) => m.importCsv.mutateAsync(r)} />}
    </div>
  )
}

function Modal({ title, onClose, children, width = 560 }: { title: string; onClose: () => void; children: React.ReactNode; width?: number }) {
  return (
    <div role="dialog" aria-modal="true" aria-label={title} style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 16 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 24, width, maxWidth: "calc(100vw - 32px)", maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
          <button type="button" aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

function RateModal({ rate, suppliers, busy, onClose, onSave }: { rate: Rate | null; suppliers: string[]; busy: boolean; onClose: () => void; onSave: (r: RateRequest, done: (err: string | null) => void) => void }) {
  const [d, setD] = useState<RateDraft>(rate ? rateToDraft(rate) : EMPTY_RATE)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [serverError, setServerError] = useState("")
  const set = (p: Partial<RateDraft>) => setD(x => ({ ...x, ...p }))
  function submit(e: React.FormEvent) {
    e.preventDefault()
    const p = parseRate(d)
    if (!p.ok) { setErrors(p.errors); return }
    setErrors({}); setServerError("")
    onSave(p.value, err => { if (err) setServerError(err) })
  }
  return (
    <Modal title={rate ? "Edit rate" : "Add a rate"} onClose={onClose}>
      <form onSubmit={submit}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 }}>
          <div><label htmlFor="rt-cat" style={lbl}>Category</label>
            <select id="rt-cat" value={d.category} onChange={e => set({ category: e.target.value as RateCategory })} style={input}>{CATEGORIES.map(c => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}</select></div>
          <div><label htmlFor="rt-ref" style={lbl}>Code</label><input id="rt-ref" value={d.itemRef} onChange={e => set({ itemRef: e.target.value })} style={input} /></div>
          <div style={{ gridColumn: "1 / -1" }}><label htmlFor="rt-desc" style={lbl}>Description</label>
            <input id="rt-desc" value={d.description} onChange={e => set({ description: e.target.value })} aria-invalid={!!errors.description} style={input} />
            {errors.description && <div role="alert" style={errText}>{errors.description}</div>}</div>
          <div><label htmlFor="rt-unit" style={lbl}>Unit</label>
            <LookupInput id="rt-unit" value={d.unit} options={UNITS} list="UNITS" onChange={v => set({ unit: v })} style={input} placeholder="m, no, bag" /></div>
          <div><label htmlFor="rt-cost" style={lbl}>Unit cost (R)</label>
            <input id="rt-cost" inputMode="decimal" value={d.unitCost} onChange={e => set({ unitCost: e.target.value })} aria-invalid={!!errors.unitCost} style={input} />
            {errors.unitCost && <div role="alert" style={errText}>{errors.unitCost}</div>}</div>
          <div><label htmlFor="rt-sup" style={lbl}>Supplier</label>
            <input id="rt-sup" list="rt-suppliers" value={d.supplier} onChange={e => set({ supplier: e.target.value })} style={input} placeholder="Optional" />
            <datalist id="rt-suppliers">{suppliers.map(s => <option key={s} value={s} />)}</datalist></div>
          <div><label htmlFor="rt-notes" style={lbl}>Notes</label><input id="rt-notes" value={d.notes} onChange={e => set({ notes: e.target.value })} style={input} /></div>
          {rate && <label style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--hf-text-secondary)" }}>
            <input type="checkbox" checked={d.active} onChange={e => set({ active: e.target.checked })} /> In use (untick to switch it off without deleting it)</label>}
        </div>
        {serverError && <div role="alert" style={{ marginTop: 12, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{serverError}</div>}
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 16 }}>
          <button type="button" style={btn} onClick={onClose}>Cancel</button>
          <button type="submit" style={primary} disabled={busy}>{busy ? "Saving…" : rate ? "Save changes" : "Add rate"}</button>
        </div>
      </form>
    </Modal>
  )
}
const SAMPLE = "Description,Unit,Unit Cost\nCement 50kg,bag,125.50\nBuilding sand,m3,310.00\n"

function ImportModal({ onClose, run }: { onClose: () => void; run: (r: { csv: string; supplier: string; defaultCategory: string; dryRun: boolean }) => Promise<ImportResult> }) {
  const [csv, setCsv] = useState("")
  const [fileName, setFileName] = useState("")
  const [supplier, setSupplier] = useState("")
  const [category, setCategory] = useState<RateCategory>("MATERIAL")
  const [result, setResult] = useState<ImportResult | undefined>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  async function pick(f: File | undefined) {
    setResult(undefined); setError("")
    if (!f) { setCsv(""); setFileName(""); return }
    if (f.size > 2_500_000) { setError("That file is over 2.5 MB. Split the price list into smaller files."); return }
    setFileName(f.name); setCsv(await f.text())
  }
  async function go(dryRun: boolean) {
    setBusy(true); setError("")
    try { setResult(await run({ csv, supplier, defaultCategory: category, dryRun })) } catch (e) { setError(apiMessage(e)) } finally { setBusy(false) }
  }
  function downloadSample() {
    const url = URL.createObjectURL(new Blob([SAMPLE], { type: "text/csv" }))
    const a = document.createElement("a"); a.href = url; a.download = "rates-sample.csv"; a.click(); URL.revokeObjectURL(url)
  }
  const done = result && !result.dryRun
  return (
    <Modal title="Import a supplier's price list" onClose={onClose} width={680}>
      <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
        Save the price list as CSV from Excel. The first line names the columns: Description and a cost column (Unit Cost, Cost, Rate or Price) are needed; Unit, Category, Supplier, Code and Notes are optional.
        Importing the same list again updates the costs instead of adding duplicates. <button type="button" onClick={downloadSample} style={{ background: "none", border: "none", padding: 0, color: "var(--hf-sky-text-strong)", cursor: "pointer", fontSize: 12.5, textDecoration: "underline" }}><Download size={11} aria-hidden="true" /> Sample file</button>
      </p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 }}>
        <div style={{ gridColumn: "1 / -1" }}><label htmlFor="im-file" style={lbl}>Price list (.csv)</label>
          <input id="im-file" type="file" accept=".csv,text/csv,text/plain" onChange={e => pick(e.target.files?.[0])} style={{ fontSize: 13 }} />
          {fileName && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 3 }}>{fileName}</div>}</div>
        <div><label htmlFor="im-sup" style={lbl}>Supplier (for rows without one)</label><input id="im-sup" value={supplier} onChange={e => { setSupplier(e.target.value); setResult(undefined) }} style={input} placeholder="e.g. BuildIt" /></div>
        <div><label htmlFor="im-cat" style={lbl}>Category (for rows without one)</label>
          <select id="im-cat" value={category} onChange={e => { setCategory(e.target.value as RateCategory); setResult(undefined) }} style={input}>{CATEGORIES.map(c => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}</select></div>
      </div>
      {error && <div role="alert" style={{ marginTop: 12, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      {result && (
        <div aria-label="Import result" style={{ marginTop: 14, padding: 12, border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface-sunken)", fontSize: 13 }}>
          <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{importSummary(result)}</div>
          {result.priceChanges.length > 0 && (
            <div style={{ marginTop: 8 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)" }}>Price changes</div>
              {result.priceChanges.map((c, i) => <div key={i} style={{ color: "var(--hf-text-muted)" }}>· {c.description}{c.supplier ? ` (${c.supplier})` : ""}: {fmtZar(c.from)} to {fmtZar(c.to)}</div>)}
            </div>
          )}
          {result.problems.length > 0 && (
            <div style={{ marginTop: 8 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-danger-text)" }}>{result.problems.length} problem{result.problems.length === 1 ? "" : "s"}</div>
              {result.problems.map((p, i) => <div key={i} style={{ color: "var(--hf-text-muted)" }}>· {p.line > 0 ? `Line ${p.line}: ` : ""}{p.message}</div>)}
            </div>
          )}
        </div>
      )}
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 16 }}>
        <button type="button" style={btn} onClick={onClose}>{done ? "Close" : "Cancel"}</button>
        {!done && <button type="button" style={btn} disabled={!csv || busy} onClick={() => go(true)}>{busy ? "Checking…" : "Check the file"}</button>}
        {!done && <button type="button" style={primary} disabled={!importable(result) || busy} onClick={() => go(false)}>Import</button>}
      </div>
    </Modal>
  )
}
