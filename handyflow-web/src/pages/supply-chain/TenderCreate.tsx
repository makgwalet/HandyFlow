// src/pages/supply-chain/TenderCreate.tsx — new tender: details → items → invite suppliers → scoring → review.
import { useState } from "react"
import { useQuery, useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inp, Field, ErrBox, Spinner, filterPill, fmtR } from "./scm.shared"
import { asList, asOne, errorText, emptyRfq, rfqBody, weightsTotal, CREATE_STEPS, createStepProblem, type RfqForm, type Weights } from "./scmOps"
import { Panel, Stepper, Pill } from "./scmUi"

interface SupplierLite { id: string; name: string; status: string; bbbeeLevel: number | null; onTimeRate: number | null; city: string | null; contactEmail: string | null }
const WEIGHT_KEYS: [keyof Weights, string][] = [["price", "Price"], ["leadTime", "Lead time"], ["technical", "Technical"], ["performance", "Past performance"], ["bbbee", "B-BBEE"]]
const BASE = "/api/v1/supply-chain/rfqs"

export function TenderCreate({ onDone, onCancel }: { onDone: (id: string) => void; onCancel: () => void }) {
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<RfqForm>(emptyRfq())
  const [err, setErr] = useState("")
  const [search, setSearch] = useState("")
  const suppliers = useQuery({ queryKey: ["scm-suppliers-lite"], queryFn: () => apiClient.get("/api/v1/supply-chain/suppliers").then(asList<SupplierLite>) })
  const create = useMutation({
    mutationFn: () => apiClient.post(BASE, rfqBody(form)).then(r => asOne<{ id: string }>(r)),
    onSuccess: r => { if (r?.id) onDone(r.id) }, onError: e => setErr(errorText(e)),
  })

  const next = () => { const p = createStepProblem(step, form); if (p) { setErr(p); return } setErr(""); setStep(step + 1) }
  const save = () => { const p = createStepProblem(4, form); if (p) { setErr(p); return } setErr(""); create.mutate() }
  const setLine = (i: number, patch: Partial<RfqForm["lines"][0]>) => setForm(f => ({ ...f, lines: f.lines.map((x, j) => j === i ? { ...x, ...patch } : x) }))
  const list = (suppliers.data ?? []).filter(s => s.status === "ACTIVE" && s.name.toLowerCase().includes(search.toLowerCase()))
  const total = weightsTotal(form.weights)
  const estimate = form.lines.reduce((s, l) => s + (Number(l.quantity) || 0) * (Number(l.estimatedUnitCost) || 0), 0)

  return (
    <div style={{ padding: 20, display: "grid", gap: 14, maxWidth: 960 }}>
      <Stepper steps={CREATE_STEPS} current={step} />
      {err && <ErrBox msg={err} />}

      {step === 0 && <Panel title="Tender details"><div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Field label="Title" span={2}><input style={inp} aria-label="Title" value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></Field>
        <Field label="Description" span={2}><textarea style={inp} rows={3} aria-label="Description" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></Field>
        <Field label="Quotations close"><input style={inp} type="datetime-local" aria-label="Closes at" value={form.closesAt} onChange={e => setForm({ ...form, closesAt: e.target.value })} /></Field>
        <Field label="Goods needed by"><input style={inp} type="date" aria-label="Required by" value={form.requiredByDate} onChange={e => setForm({ ...form, requiredByDate: e.target.value })} /></Field>
      </div></Panel>}

      {step === 1 && <Panel title="What you are asking for" right={<span style={{ fontSize: 12, color: "var(--hf-text-tertiary)" }}>Estimate {fmtR(estimate)}</span>}>
        {form.lines.map((l, i) => (
          <div key={i} style={{ display: "grid", gridTemplateColumns: "3fr 1fr 1fr auto", gap: 8, marginBottom: 6 }}>
            <input style={inp} placeholder="Item" aria-label={`Item ${i + 1}`} value={l.itemName} onChange={e => setLine(i, { itemName: e.target.value })} />
            <input style={inp} placeholder="Qty" aria-label={`Quantity ${i + 1}`} value={l.quantity} onChange={e => setLine(i, { quantity: e.target.value })} />
            <input style={inp} placeholder="Est. unit cost" aria-label={`Estimate ${i + 1}`} value={l.estimatedUnitCost} onChange={e => setLine(i, { estimatedUnitCost: e.target.value })} />
            <button type="button" aria-label={`Remove line ${i + 1}`} disabled={form.lines.length === 1} onClick={() => setForm(f => ({ ...f, lines: f.lines.filter((_, j) => j !== i) }))}>✕</button>
          </div>))}
        <button type="button" style={filterPill(false)} onClick={() => setForm(f => ({ ...f, lines: [...f.lines, { itemName: "", quantity: "1", estimatedUnitCost: "" }] }))}>Add line</button>
        <p style={{ fontSize: 12, color: "var(--hf-text-tertiary)" }}>Estimates let the comparison show what each quotation saves. Leave one blank and savings for that line are not measured.</p>
      </Panel>}

      {step === 2 && <Panel title={`Invite suppliers (${form.supplierIds.length} chosen)`} right={<input style={{ ...inp, width: 200 }} placeholder="Search suppliers" aria-label="Search suppliers" value={search} onChange={e => setSearch(e.target.value)} />}>
        {suppliers.isLoading ? <Spinner /> : list.length === 0 ? <div style={{ fontSize: 13 }}>No active suppliers match.</div> : (
          <div style={{ display: "grid", gap: 6 }}>{list.map(s => (
            <label key={s.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13 }}>
              <input type="checkbox" aria-label={`Invite ${s.name}`} checked={form.supplierIds.includes(s.id)} onChange={e => setForm(f => ({ ...f, supplierIds: e.target.checked ? [...f.supplierIds, s.id] : f.supplierIds.filter(x => x !== s.id) }))} />
              <span style={{ flex: 1 }}><strong>{s.name}</strong>{s.city ? ` · ${s.city}` : ""}</span>
              {s.bbbeeLevel != null && <Pill tone="info">B-BBEE {s.bbbeeLevel}</Pill>}
              {s.onTimeRate != null && <Pill tone={s.onTimeRate >= 90 ? "ok" : "warn"}>{Math.round(s.onTimeRate)}% on time</Pill>}
              {!s.contactEmail && <Pill tone="warn">No email — capture their quote by hand</Pill>}
            </label>))}</div>)}
      </Panel>}

      {step === 3 && <Panel title="How quotations are scored" right={<Pill tone={total === 100 ? "ok" : "bad"}>{total} / 100</Pill>}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: 10 }}>
          {WEIGHT_KEYS.map(([k, label]) => <Field key={k} label={label}><input style={inp} inputMode="numeric" aria-label={`${label} weight`} value={form.weights[k]} onChange={e => setForm(f => ({ ...f, weights: { ...f.weights, [k]: Number(e.target.value) } }))} /></Field>)}
        </div>
        <p style={{ fontSize: 12, color: "var(--hf-text-tertiary)" }}>Weights are fixed once the tender opens, so the scoring cannot be changed after prices are seen.</p>
      </Panel>}

      {step === 4 && <Panel title="Review">
        <dl style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: 6, fontSize: 13, margin: 0 }}>
          <dt>Title</dt><dd>{form.title}</dd><dt>Closes</dt><dd>{form.closesAt || "Not set"}</dd>
          <dt>Items</dt><dd>{form.lines.map(l => `${l.quantity} × ${l.itemName}`).join(", ")}</dd><dt>Estimate</dt><dd>{fmtR(estimate)}</dd>
          <dt>Suppliers</dt><dd>{(suppliers.data ?? []).filter(s => form.supplierIds.includes(s.id)).map(s => s.name).join(", ")}</dd>
          <dt>Scoring</dt><dd>{WEIGHT_KEYS.map(([k, l]) => `${l} ${form.weights[k]}`).join(" · ")}</dd>
        </dl>
        <p style={{ fontSize: 12, color: "var(--hf-text-tertiary)" }}>Saved as a draft. Suppliers are invited when you publish it.</p>
      </Panel>}

      <div style={{ display: "flex", gap: 8 }}>
        <button style={filterPill(false)} onClick={step === 0 ? onCancel : () => { setErr(""); setStep(step - 1) }}>{step === 0 ? "Cancel" : "Back"}</button>
        <div style={{ flex: 1 }} />
        {step < 4 ? <button style={filterPill(true)} onClick={next}>Continue</button> : <button style={filterPill(true)} disabled={create.isPending} onClick={save}>Save draft</button>}
      </div>
    </div>
  )
}
