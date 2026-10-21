// src/pages/catalogue/VariantsModal.tsx — the variants of one item (Size, Colour, ...): see them, edit SKU/barcode/price, add more.
import { useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ErrorBanner, Field, Modal, StatusBadge } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn } from "./catalogue.styles"
import {
  MAX_VARIANT_OPTIONS, buildMatrix, comboLabel, errorMessage, matrixProblems, optionProblem, rand, toVariantEditForm, variantEditBody, variantEditProblems, variantsBody,
  type CatalogueItem, type MatrixRow, type OptionDraft, type VariantEditForm, type VariantRow, type VariantsView,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", borderBottom: "1px solid var(--hf-border-subtle)" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)", verticalAlign: "middle" }
const cell: React.CSSProperties = { ...inp, padding: "6px 8px", fontSize: 13 }

export default function VariantsModal({ item, canWrite, canSeeCost, canWriteCost, onClose, onChanged }: {
  item: CatalogueItem; canWrite: boolean; canSeeCost: boolean; canWriteCost: boolean; onClose: () => void; onChanged: () => void
}) {
  const view = useQuery<VariantsView>({ queryKey: ["catalogue", "variants", item.id], queryFn: async () => inner<VariantsView>(await apiClient.get(`/api/v1/catalogue/items/${item.id}/variants`)) })
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<VariantRow | null>(null)
  const v = view.data
  const changed = () => { view.refetch(); onChanged() }

  return (
    <Modal title={`Variants — ${item.name}`} onClose={onClose}>
      {view.isLoading && <p>Loading…</p>}
      {view.isError && <ErrorBanner message={errorMessage(view.error, "Couldn't load the variants.")} />}
      {v && !adding && (
        <>
          <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>
            Each variant is an item of its own with its own SKU, barcode and price. {v.variants.length > 0 && `${item.name} itself is no longer sold; its variants are.`}
          </p>
          {v.variants.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No variants yet.</p>}
          {v.variants.length > 0 && (
            <div style={{ overflowX: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 10, marginBottom: 14 }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>{v.options.map(o => o.name).join(" / ") || "Variant"}</th><th style={th}>SKU</th><th style={th}>Barcode</th><th style={th}>Price</th><th style={th}>Status</th><th style={th} /></tr></thead>
                <tbody>
                  {v.variants.map(r => (
                    <tr key={r.id}>
                      <td style={{ ...td, fontWeight: 600 }}>{r.label}</td>
                      <td style={td}>{r.sku ?? "—"}</td>
                      <td style={td}>{r.barcode ?? "—"}</td>
                      <td style={td}>{rand(r.defaultPrice)}</td>
                      <td style={td}><StatusBadge status={r.status} /></td>
                      <td style={{ ...td, textAlign: "right" }}>{canWrite && <button type="button" style={linkBtn} aria-label={`Edit ${r.label}`} onClick={() => setEditing(r)}>Edit</button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {canWrite && <button type="button" style={primaryBtn} onClick={() => setAdding(true)}>{v.variants.length === 0 ? "Add variants" : "Add more variants"}</button>}
        </>
      )}
      {v && adding && <AddVariants item={item} view={v} onBack={() => setAdding(false)} onDone={() => { setAdding(false); changed() }} />}
      {editing && <EditVariant parentId={item.id} variant={editing} canSeeCost={canSeeCost} canWriteCost={canWriteCost} onClose={() => setEditing(null)} onDone={() => { setEditing(null); changed() }} />}
    </Modal>
  )
}

function AddVariants({ item, view, onBack, onDone }: { item: CatalogueItem; view: VariantsView; onBack: () => void; onDone: () => void }) {
  const locked = view.variants.length > 0 ? view.options.map(o => o.name) : []
  const [opts, setOpts] = useState<OptionDraft[]>(view.variants.length > 0
    ? view.options.map(o => ({ name: o.name, values: o.values.join(", ") }))
    : [{ name: "Size", values: "" }])
  const [rows, setRows] = useState<MatrixRow[] | null>(null)
  const [problem, setProblem] = useState("")
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const setOpt = (i: number, patch: Partial<OptionDraft>) => { setOpts(o => o.map((x, j) => (j === i ? { ...x, ...patch } : x))); setRows(null) }
  const setRow = (key: string, patch: Partial<MatrixRow>) => setRows(rs => (rs ?? []).map(r => (r.key === key ? { ...r, ...patch } : r)))
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/catalogue/items/${item.id}/variants`, variantsBody(opts, rows ?? [])),
    onSuccess: onDone,
    onError: e => setGeneral(errorMessage(e, "Couldn't create the variants.")),
  })
  const build = () => {
    const p = optionProblem(opts, locked)
    setProblem(p ?? ""); setErrors({}); setGeneral("")
    if (!p) setRows(buildMatrix(opts, item.sku, item.defaultPrice, view.variants))
  }
  const submit = () => {
    const errs = matrixProblems(rows ?? [])
    setErrors(errs); setGeneral("")
    if (Object.keys(errs).length === 0) save.mutate()
  }
  return (
    <div>
      <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>Name the options (Size, Colour) and list their values separated by commas. You then choose which combinations to create.</p>
      {opts.map((o, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: "1fr 2fr auto", gap: 8, marginBottom: 8, alignItems: "end" }}>
          <Field label={`Option ${i + 1}`} id={`vo-name-${i}`}><input id={`vo-name-${i}`} style={inp} value={o.name} readOnly={locked.length > 0} onChange={e => setOpt(i, { name: e.target.value })} placeholder="e.g. Size" /></Field>
          <Field label="Values" id={`vo-values-${i}`}><input id={`vo-values-${i}`} style={inp} value={o.values} onChange={e => setOpt(i, { values: e.target.value })} placeholder="e.g. S, M, L" /></Field>
          {locked.length === 0 && opts.length > 1 && <button type="button" style={{ ...linkBtn, marginBottom: 16 }} aria-label={`Remove option ${i + 1}`} onClick={() => { setOpts(os => os.filter((_, j) => j !== i)); setRows(null) }}>Remove</button>}
        </div>
      ))}
      {locked.length === 0 && opts.length < MAX_VARIANT_OPTIONS && <button type="button" style={{ ...linkBtn, marginBottom: 12 }} onClick={() => { setOpts(os => [...os, { name: "", values: "" }]); setRows(null) }}>+ Add option</button>}
      {problem && <ErrorBanner message={problem} />}
      <div style={{ display: "flex", gap: 10, marginBottom: 14 }}>
        <button type="button" style={cancelBtn} onClick={build}>Show combinations</button>
      </div>
      {rows && rows.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Every combination of these values already exists.</p>}
      {rows && rows.length > 0 && (
        <>
          <div style={{ overflowX: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 10, marginBottom: 12 }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th} /><th style={th}>Variant</th><th style={th}>SKU</th><th style={th}>Barcode</th><th style={th}>Price (R)</th></tr></thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.key}>
                    <td style={td}><input type="checkbox" aria-label={`Create ${comboLabel(r.values)}`} checked={r.include} onChange={e => setRow(r.key, { include: e.target.checked })} /></td>
                    <td style={{ ...td, fontWeight: 600, whiteSpace: "nowrap" }}>{comboLabel(r.values)}</td>
                    <td style={td}><input aria-label={`SKU for ${comboLabel(r.values)}`} style={errors[`${r.key}:sku`] ? { ...cell, ...inpErr } : cell} value={r.sku} onChange={e => setRow(r.key, { sku: e.target.value.toUpperCase() })} />{errors[`${r.key}:sku`] && <small role="alert" style={{ color: "var(--hf-danger-text)" }}>{errors[`${r.key}:sku`]}</small>}</td>
                    <td style={td}><input aria-label={`Barcode for ${comboLabel(r.values)}`} style={errors[`${r.key}:barcode`] ? { ...cell, ...inpErr } : cell} value={r.barcode} onChange={e => setRow(r.key, { barcode: e.target.value })} />{errors[`${r.key}:barcode`] && <small role="alert" style={{ color: "var(--hf-danger-text)" }}>{errors[`${r.key}:barcode`]}</small>}</td>
                    <td style={td}><input aria-label={`Price for ${comboLabel(r.values)}`} style={errors[`${r.key}:price`] ? { ...cell, ...inpErr } : cell} value={r.price} onChange={e => setRow(r.key, { price: e.target.value })} inputMode="decimal" />{errors[`${r.key}:price`] && <small role="alert" style={{ color: "var(--hf-danger-text)" }}>{errors[`${r.key}:price`]}</small>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {errors.general && <ErrorBanner message={errors.general} />}
        </>
      )}
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onBack}>Back</button>
        {rows && rows.length > 0 && <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>{save.isPending ? "Creating…" : "Create variants"}</button>}
      </div>
    </div>
  )
}

function EditVariant({ parentId, variant, canSeeCost, canWriteCost, onClose, onDone }: {
  parentId: string; variant: VariantRow; canSeeCost: boolean; canWriteCost: boolean; onClose: () => void; onDone: () => void
}) {
  const [form, setForm] = useState<VariantEditForm>(toVariantEditForm(variant))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const set = <K extends keyof VariantEditForm>(k: K, v: VariantEditForm[K]) => setForm(f => ({ ...f, [k]: v }))
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/catalogue/items/${parentId}/variants/${variant.id}`, variantEditBody(form, variant.version, canWriteCost)),
    onSuccess: onDone,
    onError: e => {
      const msg = errorMessage(e, "Couldn't save the variant.")
      if (/SKU/.test(msg)) { setErrors({ sku: msg }); setGeneral("") } else if (/barcode/.test(msg)) { setErrors({ barcode: msg }); setGeneral("") } else setGeneral(msg)
    },
  })
  const submit = () => { const errs = variantEditProblems(form, canSeeCost && canWriteCost); setErrors(errs); setGeneral(""); if (Object.keys(errs).length === 0) save.mutate() }
  return (
    <Modal title={`Edit — ${variant.label}`} onClose={onClose}>
      <Field label="SKU" error={errors.sku} id="ve-sku"><input id="ve-sku" style={errors.sku ? inpErr : inp} value={form.sku} onChange={e => set("sku", e.target.value.toUpperCase())} /></Field>
      <Field label="Barcode" error={errors.barcode} id="ve-barcode"><input id="ve-barcode" style={errors.barcode ? inpErr : inp} value={form.barcode} onChange={e => set("barcode", e.target.value)} /></Field>
      <Field label="Price (R)" error={errors.price} id="ve-price"><input id="ve-price" style={errors.price ? inpErr : inp} value={form.price} onChange={e => set("price", e.target.value)} inputMode="decimal" /></Field>
      {canSeeCost && <Field label="Cost price (R)" error={errors.cost} id="ve-cost"><input id="ve-cost" style={errors.cost ? inpErr : inp} value={form.cost} readOnly={!canWriteCost} onChange={e => set("cost", e.target.value)} inputMode="decimal" /></Field>}
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : "Save changes"}</button>
      </div>
    </Modal>
  )
}
