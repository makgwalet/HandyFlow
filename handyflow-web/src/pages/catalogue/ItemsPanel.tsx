// src/pages/catalogue/ItemsPanel.tsx — the catalogue list: server-side search, filters, sorting and paging; add/edit, lifecycle changes, history and delete.
import { useEffect, useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient, keepPreviousData } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight, Coins, History, Layers, Package, Pencil, Plus, Power, Search, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { DeleteModal, ErrorBanner, Field, Modal, StatusBadge } from "./CatalogueKit"
import { inp, inpErr, sel } from "./catalogue.styles"
import PricingModal from "./PricingModal"
import VariantsModal from "./VariantsModal"
import {
  EMPTY_FILTERS, EMPTY_ITEM_FORM, applicableAttributes, attributeProblems, indented, matchUnit, ITEM_STATUSES, ITEM_TYPES, STATUS_LABEL, allowedMoves, duplicateField, errorMessage, fieldErrors, hasFilters, itemBody, itemProblems,
  listParams, marginText, moveLabel, moveNeedsReason, rand, rangeText, statusProblem, toItemForm, typeLabel,
  type AttributeDef, type CatalogueItem, type Category, type Unit, type Filters, type HistoryLine, type ItemForm, type ItemStatus, type PageOf,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const ghost: React.CSSProperties = { padding: "5px 10px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12, color: "var(--hf-text-secondary)", cursor: "pointer", fontWeight: 500, display: "inline-flex", alignItems: "center", gap: 5 }
const primary: React.CSSProperties = { padding: "9px 18px", background: "var(--hf-primary)", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, color: "var(--hf-text-on-solid)", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 6 }
const cancelBtn: React.CSSProperties = { padding: "9px 16px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-secondary)" }

export default function ItemsPanel() {
  const qc = useQueryClient()
  const canWrite = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_WRITE"])
  const canArchive = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_ARCHIVE"])
  const canSeeCost = usePermissions(["CATALOGUE_PRICE_READ"])
  const canWriteCost = usePermissions(["CATALOGUE_PRICE_WRITE"])

  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [qInput, setQInput] = useState("")
  const [editing, setEditing] = useState<CatalogueItem | "new" | null>(null)
  const [statusFor, setStatusFor] = useState<CatalogueItem | null>(null)
  const [historyFor, setHistoryFor] = useState<CatalogueItem | null>(null)
  const [pricingFor, setPricingFor] = useState<CatalogueItem | null>(null)
  const [variantsFor, setVariantsFor] = useState<CatalogueItem | null>(null)
  const [deleting, setDeleting] = useState<CatalogueItem | null>(null)
  const [deleteError, setDeleteError] = useState("")

  // Search as the user types, but wait for a pause so each keystroke is not a request.
  useEffect(() => {
    const t = setTimeout(() => setFilters(f => (f.q === qInput ? f : { ...f, q: qInput, page: 0 })), 300)
    return () => clearTimeout(t)
  }, [qInput])
  const set = (patch: Partial<Filters>) => setFilters(f => ({ ...f, page: 0, ...patch }))

  const categories = useQuery<Category[]>({ queryKey: ["catalogue", "categories"], queryFn: async () => inner<Category[]>(await apiClient.get("/api/v1/catalogue/categories")) ?? [] })
  const list = useQuery<PageOf<CatalogueItem>>({
    queryKey: ["catalogue", "items", filters], placeholderData: keepPreviousData,
    queryFn: async () => inner<PageOf<CatalogueItem>>(await apiClient.get("/api/v1/catalogue/items/search", { params: listParams(filters) })),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["catalogue", "items"] }); qc.invalidateQueries({ queryKey: ["catalogue", "categories"] }) }

  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/items/${id}`),
    onSuccess: () => { refresh(); setDeleting(null); setDeleteError("") },
    onError: e => setDeleteError(errorMessage(e, "Failed to delete item")),
  })

  const page = list.data
  const sortBy = (s: Filters["sort"]) => set({ sort: s, desc: filters.sort === s ? !filters.desc : false, page: filters.page })
  const arrow = (s: Filters["sort"]) => (filters.sort === s ? (filters.desc ? " ▼" : " ▲") : "")
  const th: React.CSSProperties = { padding: "10px 14px", textAlign: "left", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", whiteSpace: "nowrap" }
  const sortTh = (s: Filters["sort"], label: string) => (
    <th style={th} aria-sort={filters.sort === s ? (filters.desc ? "descending" : "ascending") : "none"}>
      <button type="button" onClick={() => sortBy(s)} style={{ all: "unset", cursor: "pointer" }}>{label}{arrow(s)}</button>
    </th>)

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 16 }}>
        <div style={{ position: "relative", flex: "1 1 240px", minWidth: 200 }}>
          <Search size={15} aria-hidden style={{ color: "var(--hf-text-faint)", position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)" }} />
          <input aria-label="Search items" value={qInput} onChange={e => setQInput(e.target.value)} placeholder="Search name, SKU, barcode or brand…" style={{ ...inp, paddingLeft: 38 }} />
        </div>
        <select aria-label="Filter by category" style={{ ...sel, width: "auto", minWidth: 150 }} value={filters.categoryId} onChange={e => set({ categoryId: e.target.value })}>
          <option value="">All categories</option>
          {(categories.data ?? []).map(c => <option key={c.id} value={c.id}>{indented(c)}</option>)}
        </select>
        <select aria-label="Filter by status" style={{ ...sel, width: "auto", minWidth: 130 }} value={filters.status} onChange={e => set({ status: e.target.value as Filters["status"] })}>
          <option value="">All statuses</option>
          {ITEM_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
        <select aria-label="Filter by type" style={{ ...sel, width: "auto", minWidth: 130 }} value={filters.type} onChange={e => set({ type: e.target.value as Filters["type"] })}>
          <option value="">All types</option>
          {ITEM_TYPES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
        </select>
        {hasFilters(filters) && <button type="button" style={ghost} onClick={() => { setQInput(""); setFilters(EMPTY_FILTERS) }}>Clear filters</button>}
        {canWrite && <button type="button" style={{ ...primary, marginLeft: "auto" }} onClick={() => setEditing("new")}><Plus size={15} /> Add item</button>}
      </div>

      {list.isLoading && <p role="status">Loading…</p>}
      {list.isError && <ErrorBanner message={errorMessage(list.error, "Couldn't load the catalogue.")} />}

      {page && page.totalElements === 0 && (
        <div style={{ border: "1px solid var(--hf-primary-border)", borderRadius: 14, padding: "56px 24px", textAlign: "center" }}>
          <Package size={36} aria-hidden style={{ color: "var(--hf-text-disabled)", marginBottom: 10 }} />
          {hasFilters(filters)
            ? <><p style={{ fontSize: 16, fontWeight: 700, color: "var(--hf-text-muted)", margin: "0 0 6px" }}>No items match</p><p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>Try a different search, or clear the filters.</p></>
            : <><p style={{ fontSize: 16, fontWeight: 700, color: "var(--hf-text-muted)", margin: "0 0 6px" }}>No items yet</p><p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>{canWrite ? "Add your first product or service." : "Nothing has been added to the catalogue yet."}</p></>}
        </div>
      )}

      {page && page.totalElements > 0 && (
        <div style={{ border: "1px solid var(--hf-primary-border)", borderRadius: 14, overflow: "hidden", opacity: list.isFetching ? 0.7 : 1 }}>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr style={{ background: "var(--hf-surface-muted)" }}>
                  {sortTh("name", "Item")}<th style={th}>Type</th><th style={th}>Category</th><th style={th}>Unit</th>
                  {sortTh("price", "Price")}{canSeeCost && <th style={th}>Margin</th>}<th style={th}>VAT</th>{sortTh("status", "Status")}<th style={th}><span className="sr-only" style={{ position: "absolute", width: 1, height: 1, overflow: "hidden" }}>Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((i, idx) => (
                  <tr key={i.id} style={{ borderTop: idx === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                    <td style={{ padding: "12px 14px", minWidth: 200 }}>
                      <p style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)", margin: 0 }}>{i.name}</p>
                      {(i.variantCount ?? 0) > 0 && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>{i.variantCount} {i.variantCount === 1 ? "variant" : "variants"}</p>}
                      {i.parentId && i.variantLabel && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>Variant: {i.variantLabel}</p>}
                      {(i.sku || i.barcode || i.brand) && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "2px 0 0" }}>{[i.sku && `SKU ${i.sku}`, i.barcode && `Barcode ${i.barcode}`, i.brand].filter(Boolean).join(" · ")}</p>}
                      {i.description && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "2px 0 0" }}>{i.description}</p>}
                    </td>
                    <td style={{ padding: "12px 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>{typeLabel(i.itemType)}</td>
                    <td style={{ padding: "12px 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>{i.categoryName ?? "—"}</td>
                    <td style={{ padding: "12px 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>{i.unit}</td>
                    <td style={{ padding: "12px 14px", fontSize: 14, fontWeight: 700, color: "var(--hf-text)", whiteSpace: "nowrap" }}>{rand(i.defaultPrice)}</td>
                    {canSeeCost && <td style={{ padding: "12px 14px", fontSize: 13, color: i.marginPercent != null && i.marginPercent < 0 ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }} title={i.costPrice != null ? `Cost ${rand(i.costPrice)}` : "No cost recorded"}>{marginText(i.marginPercent)}</td>}
                    <td style={{ padding: "12px 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>{i.vatExempt ? "Exempt" : `${i.vatRate}%`}</td>
                    <td style={{ padding: "12px 14px" }}><StatusBadge status={i.status} /></td>
                    <td style={{ padding: "12px 14px" }}>
                      <div style={{ display: "flex", gap: 6, justifyContent: "flex-end", flexWrap: "wrap" }}>
                        {canWrite && i.status !== "ARCHIVED" && <button type="button" style={ghost} aria-label={`Edit ${i.name}`} onClick={() => setEditing(i)}><Pencil size={12} /> Edit</button>}
                        {canWrite && allowedMoves(i.status, canArchive).length > 0 && <button type="button" style={ghost} aria-label={`Change status of ${i.name}`} onClick={() => setStatusFor(i)}><Power size={12} /> Status</button>}
                        {(canWrite || (i.variantCount ?? 0) > 0) && !i.parentId && i.status !== "ARCHIVED" && <button type="button" style={ghost} aria-label={`Variants of ${i.name}`} onClick={() => setVariantsFor(i)}><Layers size={12} /> Variants</button>}
                        {canSeeCost && i.status !== "ARCHIVED" && (i.variantCount ?? 0) === 0 && <button type="button" style={ghost} aria-label={`Pricing of ${i.name}`} onClick={() => setPricingFor(i)}><Coins size={12} /> Pricing</button>}
                        <button type="button" style={ghost} aria-label={`History of ${i.name}`} onClick={() => setHistoryFor(i)}><History size={12} /></button>
                        {canArchive && <button type="button" style={ghost} aria-label={`Delete ${i.name}`} onClick={() => { setDeleting(i); setDeleteError("") }}><Trash2 size={13} /></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
            <span aria-live="polite">{rangeText(page)}</span>
            <span style={{ display: "flex", gap: 6, alignItems: "center" }}>
              <button type="button" style={ghost} disabled={page.number === 0} onClick={() => setFilters(f => ({ ...f, page: f.page - 1 }))}><ChevronLeft size={13} /> Previous</button>
              <span>Page {page.number + 1} of {Math.max(1, page.totalPages)}</span>
              <button type="button" style={ghost} disabled={page.number + 1 >= page.totalPages} onClick={() => setFilters(f => ({ ...f, page: f.page + 1 }))}>Next <ChevronRight size={13} /></button>
            </span>
          </div>
        </div>
      )}

      {editing && <ItemFormModal item={editing === "new" ? null : editing} categories={categories.data ?? []} canSeeCost={canSeeCost} canWriteCost={canWriteCost}
        onClose={() => setEditing(null)} onSaved={() => { refresh(); setEditing(null) }} onStale={refresh} />}
      {statusFor && <StatusDialog item={statusFor} canArchive={canArchive} onClose={() => setStatusFor(null)} onDone={() => { refresh(); setStatusFor(null) }} onStale={refresh} />}
      {variantsFor && <VariantsModal item={variantsFor} canWrite={canWrite} canSeeCost={canSeeCost} canWriteCost={canWriteCost} onClose={() => setVariantsFor(null)} onChanged={refresh} />}
      {pricingFor && <PricingModal item={pricingFor} canWrite={canWriteCost} onClose={() => setPricingFor(null)} onChanged={refresh} />}
      {historyFor && <HistoryModal item={historyFor} onClose={() => setHistoryFor(null)} />}
      {deleting && (
        <DeleteModal title="Delete item?" badge={deleting.name} badgeIcon={<Package size={14} />} isPending={del.isPending} error={deleteError}
          description={<>The item is removed from the catalogue and from new quotes, invoices and sales. Documents that already use it keep it.</>}
          onConfirm={() => del.mutate(deleting.id)} onCancel={() => { setDeleting(null); setDeleteError("") }} />
      )}
    </div>
  )
}

// ── Add / edit ───────────────────────────────────────────────────────────────
function ItemFormModal({ item, categories, canSeeCost, canWriteCost, onClose, onSaved, onStale }: {
  item: CatalogueItem | null; categories: Category[]; canSeeCost: boolean; canWriteCost: boolean; onClose: () => void; onSaved: () => void; onStale: () => void
}) {
  const [form, setForm] = useState<ItemForm>(item ? toItemForm(item) : EMPTY_ITEM_FORM)
  const units = useQuery<Unit[]>({ queryKey: ["catalogue", "units"], queryFn: async () => inner<Unit[]>(await apiClient.get("/api/v1/catalogue/units")) ?? [] })
  const defs = useQuery<AttributeDef[]>({ queryKey: ["catalogue", "attributes"], queryFn: async () => inner<AttributeDef[]>(await apiClient.get("/api/v1/catalogue/attributes")) ?? [] })
  // The list does not carry attribute values, so an edit loads the single item to get them.
  const full = useQuery<CatalogueItem>({ queryKey: ["catalogue", "item", item?.id], enabled: !!item, queryFn: async () => inner<CatalogueItem>(await apiClient.get(`/api/v1/catalogue/items/${item!.id}`)) })
  const settings = useQuery<{ priceApprovalRequired: boolean }>({ queryKey: ["catalogue", "settings"], queryFn: async () => inner<{ priceApprovalRequired: boolean }>(await apiClient.get("/api/v1/catalogue/settings")) })
  // When price changes need approval, an existing item's price changes through Pricing, not through this form.
  const priceLocked = !!item && settings.data?.priceApprovalRequired === true
  const loadedAttrs = useRef(false)
  useEffect(() => {
    const a = full.data?.attributes
    if (!loadedAttrs.current && Array.isArray(a)) {
      loadedAttrs.current = true
      setForm(f => ({ ...f, attributes: { ...Object.fromEntries(a.map(v => [v.definitionId, v.value])), ...f.attributes } }))
    }
  }, [full.data])
  const applicable = applicableAttributes(Array.isArray(defs.data) ? defs.data : [], categories, form.categoryId || null)
  const unitList = Array.isArray(units.data) ? units.data : []
  const unitValue = matchUnit(unitList, form.unit)?.code ?? form.unit.trim()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const set = <K extends keyof ItemForm>(k: K, v: ItemForm[K]) => setForm(f => ({ ...f, [k]: v }))

  const save = useMutation({
    mutationFn: () => {
      const body = itemBody({ ...form, unit: unitValue }, { canWriteCost, version: item?.version, creating: !item, attributeIds: applicable.map(d => d.id) })
      return item ? apiClient.put(`/api/v1/catalogue/items/${item.id}`, body) : apiClient.post("/api/v1/catalogue/items", body)
    },
    onSuccess: onSaved,
    onError: e => {
      const msg = errorMessage(e, "Couldn't save the item. Please try again.")
      const server = fieldErrors(e)
      const dup = duplicateField(msg)
      if (dup) setErrors({ [dup]: msg }); else setErrors(server)
      setGeneral(dup || Object.keys(server).length ? "" : msg)
      if (/changed by someone else/.test(msg)) onStale()
    },
  })
  const submit = () => {
    const errs = { ...itemProblems(form, canSeeCost && canWriteCost), ...attributeProblems(applicable, form.attributes, item ? item.status !== "DRAFT" : !form.draft) }
    setErrors(errs); setGeneral("")
    if (Object.keys(errs).length === 0) save.mutate()
  }
  const s = (k: string) => (errors[k] ? inpErr : inp)

  return (
    <Modal title={item ? `Edit — ${item.name}` : "New catalogue item"} onClose={onClose}>
      <Field label="Item name" required error={errors.name} id="cat-name">
        <input id="cat-name" style={s("name")} value={form.name} onChange={e => set("name", e.target.value)} placeholder="e.g. Cement 50kg bag" autoFocus />
      </Field>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Field label="Type" id="cat-type">
          <select id="cat-type" style={sel} value={form.itemType} onChange={e => set("itemType", e.target.value as ItemForm["itemType"])}>
            {ITEM_TYPES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
          </select>
        </Field>
        <Field label="Category (optional)" id="cat-category">
          <select id="cat-category" style={sel} value={form.categoryId} onChange={e => set("categoryId", e.target.value)}>
            <option value="">— No category —</option>
            {categories.map(c => <option key={c.id} value={c.id}>{indented(c)}</option>)}
          </select>
        </Field>
        <Field label="SKU" error={errors.sku} id="cat-sku">
          <input id="cat-sku" style={s("sku")} value={form.sku} onChange={e => set("sku", e.target.value.toUpperCase())} placeholder="e.g. CEM-50" />
        </Field>
        <Field label="Barcode" error={errors.barcode} id="cat-barcode">
          <input id="cat-barcode" style={s("barcode")} value={form.barcode} onChange={e => set("barcode", e.target.value)} placeholder="Scan or type" inputMode="numeric" />
        </Field>
        <Field label="Brand" error={errors.brand} id="cat-brand">
          <input id="cat-brand" style={s("brand")} value={form.brand} onChange={e => set("brand", e.target.value)} />
        </Field>
        <Field label="Unit" required error={errors.unit} id="cat-unit">
          <select id="cat-unit" style={errors.unit ? { ...sel, borderColor: "var(--hf-danger)" } : sel} value={unitValue} onChange={e => set("unit", e.target.value)}>
            {unitValue === "" && <option value="">Select a unit…</option>}
            {unitList.filter(u => u.active).map(u => <option key={u.id} value={u.code}>{u.name && u.name !== u.code ? `${u.code} — ${u.name}` : u.code}</option>)}
            {unitValue !== "" && !unitList.some(u => u.active && u.code === unitValue) && <option value={unitValue}>{unitValue}</option>}
          </select>
        </Field>
        <Field label="Default price (R)" required error={errors.defaultPrice} id="cat-price">
          <input id="cat-price" style={s("defaultPrice")} value={form.defaultPrice} onChange={e => set("defaultPrice", e.target.value)} placeholder="0.00" inputMode="decimal"
            readOnly={priceLocked} title={priceLocked ? "Price changes need approval. Use Pricing on the item." : undefined} />
          {priceLocked && <span style={{ display: "block", fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>Price changes need approval. Use Pricing on the item.</span>}
        </Field>
        {canSeeCost && (
          <Field label="Cost price (R)" error={errors.costPrice} id="cat-cost">
            <input id="cat-cost" style={s("costPrice")} value={form.costPrice} onChange={e => set("costPrice", e.target.value)} placeholder="0.00" inputMode="decimal" readOnly={!canWriteCost} title={canWriteCost ? undefined : "You can see the cost but not change it"} />
          </Field>
        )}
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, alignItems: "end" }}>
        <Field label="VAT rate (%)" error={errors.vatRate} id="cat-vat">
          <select id="cat-vat" style={sel} value={form.vatExempt ? "0" : form.vatRate} disabled={form.vatExempt} onChange={e => set("vatRate", e.target.value)}>
            <option value="15">15% (Standard)</option>
            <option value="0">0% (Zero-rated)</option>
          </select>
        </Field>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 16 }}>
          <input type="checkbox" checked={form.vatExempt} onChange={e => set("vatExempt", e.target.checked)} /> VAT exempt
        </label>
      </div>
      <Field label="Description (optional)" id="cat-desc">
        <input id="cat-desc" style={inp} value={form.description} onChange={e => set("description", e.target.value)} placeholder="Brief description" />
      </Field>
      {applicable.length > 0 && (
        <fieldset style={{ border: "1px solid var(--hf-border-subtle)", borderRadius: 10, padding: "12px 14px 2px", margin: "0 0 16px" }}>
          <legend style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", padding: "0 6px", textTransform: "uppercase", letterSpacing: "0.04em" }}>Attributes</legend>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            {applicable.map(d => <AttributeField key={d.id} def={d} value={form.attributes[d.id] ?? ""} error={errors[`attr:${d.id}`]}
              onChange={v => setForm(f => ({ ...f, attributes: { ...f.attributes, [d.id]: v } }))} />)}
          </div>
        </fieldset>
      )}
      {!item && (
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 16 }}>
          <input type="checkbox" checked={form.draft} onChange={e => set("draft", e.target.checked)} /> Save as a draft (not available for sale yet)
        </label>
      )}
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primary} onClick={submit} disabled={save.isPending}>{save.isPending ? "Saving…" : item ? "Save changes" : "Create item"}</button>
      </div>
    </Modal>
  )
}

/** One input for one attribute, chosen by its type. */
function AttributeField({ def, value, error, onChange }: { def: AttributeDef; value: string; error?: string; onChange: (v: string) => void }) {
  const id = `attr-${def.id}`, style = error ? inpErr : inp
  return (
    <Field label={def.label} required={def.required} error={error} id={id}>
      {def.type === "LIST" ? (
        <select id={id} style={sel} value={value} onChange={e => onChange(e.target.value)}>
          <option value="">—</option>
          {def.options.map(o => <option key={o} value={o}>{o}</option>)}
        </select>
      ) : def.type === "BOOLEAN" ? (
        <select id={id} style={sel} value={value} onChange={e => onChange(e.target.value)}>
          <option value="">—</option><option value="true">Yes</option><option value="false">No</option>
        </select>
      ) : def.type === "DATE" ? (
        <input id={id} type="date" style={style} value={value} onChange={e => onChange(e.target.value)} />
      ) : (
        <input id={id} style={style} value={value} inputMode={def.type === "NUMBER" ? "decimal" : undefined} onChange={e => onChange(e.target.value)} />
      )}
    </Field>
  )
}

// ── Lifecycle ────────────────────────────────────────────────────────────────
function StatusDialog({ item, canArchive, onClose, onDone, onStale }: { item: CatalogueItem; canArchive: boolean; onClose: () => void; onDone: () => void; onStale: () => void }) {
  const moves = allowedMoves(item.status, canArchive)
  const [to, setTo] = useState<ItemStatus>(moves[0])
  const [reason, setReason] = useState("")
  const [shown, setShown] = useState(false)
  const [error, setError] = useState("")
  const problem = statusProblem(item.status, to, reason)
  const change = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/catalogue/items/${item.id}/status`, { status: to, reason: reason.trim() || undefined, version: item.version }),
    onSuccess: onDone,
    onError: e => { const m = errorMessage(e, "Couldn't change the status."); setError(m); if (/changed by someone else/.test(m)) onStale() },
  })
  return (
    <Modal title={`Status — ${item.name}`} onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 0 }}>Now: <StatusBadge status={item.status} />. Only active items can be picked for new quotes, invoices and sales.</p>
      <Field label="Change to" id="st-to">
        <select id="st-to" style={sel} value={to} onChange={e => { setTo(e.target.value as ItemStatus); setError("") }}>
          {moves.map(m => <option key={m} value={m}>{moveLabel(m)}</option>)}
        </select>
      </Field>
      {moveNeedsReason(to) && (
        <Field label="Reason" required error={shown && problem ? problem : undefined} id="st-reason">
          <input id="st-reason" style={shown && problem ? inpErr : inp} value={reason} onChange={e => setReason(e.target.value)} placeholder="e.g. Supplier stopped making it" maxLength={255} />
        </Field>
      )}
      {error && <ErrorBanner message={error} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primary} disabled={change.isPending} onClick={() => { setShown(true); if (!problem) change.mutate() }}>{change.isPending ? "Saving…" : moveLabel(to)}</button>
      </div>
    </Modal>
  )
}

// ── History ──────────────────────────────────────────────────────────────────
function HistoryModal({ item, onClose }: { item: CatalogueItem; onClose: () => void }) {
  const h = useQuery<PageOf<HistoryLine>>({ queryKey: ["catalogue", "history", item.id], queryFn: async () => inner<PageOf<HistoryLine>>(await apiClient.get(`/api/v1/catalogue/items/${item.id}/history`, { params: { size: 50 } })) })
  return (
    <Modal title={`History — ${item.name}`} onClose={onClose}>
      {h.isLoading && <p role="status">Loading…</p>}
      {h.isError && <ErrorBanner message="Couldn't load the history." />}
      {h.data && h.data.content.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing recorded yet.</p>}
      {h.data && h.data.content.length > 0 && (
        <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {h.data.content.map(l => (
            <li key={l.id} style={{ padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}><strong>{actionText(l.action)}</strong><span style={{ color: "var(--hf-text-faint)" }}>{new Date(l.at).toLocaleString("en-ZA")}</span></div>
              {l.detail && <div style={{ color: "var(--hf-text-muted)" }}>{l.detail}</div>}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
const actionText = (a: string) => ({
  CREATED: "Created", UPDATED: "Edited", PRICE_CHANGED: "Price changed", STATUS_CHANGED: "Status changed", DELETED: "Deleted",
  PRICE_PROPOSED: "Price change proposed", PRICE_APPROVED: "Price change approved", PRICE_REJECTED: "Price change rejected", PRICE_CANCELLED: "Price change cancelled", SETTING_CHANGED: "Setting changed",
}[a] ?? a)
