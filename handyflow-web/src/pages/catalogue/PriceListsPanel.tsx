// src/pages/catalogue/PriceListsPanel.tsx — the tenant's price lists and the switch for "price changes need approval".
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner, Field, Modal } from "./CatalogueKit"
import { cancelBtn, inp, inpErr, linkBtn, primaryBtn, td, th } from "./catalogue.styles"
import { EMPTY_PRICE_LIST_FORM, errorMessage, fieldErrors, priceListBody, priceListProblems, toPriceListForm, type PriceList, type PriceListForm } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
interface Settings { priceApprovalRequired: boolean; updatedAt: string | null }

export default function PriceListsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["CATALOGUE_ADMIN"])
  const [editing, setEditing] = useState<PriceList | "new" | null>(null)
  const [deleteError, setDeleteError] = useState("")
  const lists = useQuery<PriceList[]>({ queryKey: ["catalogue", "price-lists"], queryFn: async () => inner<PriceList[]>(await apiClient.get("/api/v1/catalogue/price-lists")) ?? [] })
  const settings = useQuery<Settings>({ queryKey: ["catalogue", "settings"], queryFn: async () => inner<Settings>(await apiClient.get("/api/v1/catalogue/settings")) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["catalogue", "price-lists"] })
  const toggle = useMutation({
    mutationFn: (required: boolean) => apiClient.put("/api/v1/catalogue/settings", { priceApprovalRequired: required }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["catalogue", "settings"] }),
  })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/price-lists/${id}`),
    onSuccess: () => { refresh(); setDeleteError("") },
    onError: e => setDeleteError(errorMessage(e, "Couldn't delete the price list.")),
  })
  const required = settings.data?.priceApprovalRequired ?? true

  return (
    <div>
      <section aria-label="Price approval" style={{ border: "1px solid var(--hf-primary-border)", borderRadius: 12, padding: 16, marginBottom: 20 }}>
        <label style={{ display: "flex", gap: 10, alignItems: "flex-start", fontSize: 14 }}>
          <input type="checkbox" checked={required} disabled={!canManage || settings.isLoading || toggle.isPending} onChange={e => toggle.mutate(e.target.checked)} style={{ marginTop: 3 }} />
          <span>
            <strong>Price changes need approval</strong>
            <span style={{ display: "block", fontSize: 13, color: "var(--hf-text-muted)", marginTop: 2 }}>
              {required ? "A price change waits until someone other than the person who proposed it approves it." : "Price changes take effect on their date without approval. Every change is still recorded."}
            </span>
          </span>
        </label>
        {toggle.isError && <ErrorBanner message={errorMessage(toggle.error, "Couldn't save the setting.")} />}
        {!canManage && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Only a catalogue administrator can change this.</p>}
      </section>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12, gap: 12 }}>
        <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>The default list is the price Invoicing, POS and Supply Chain use. Other lists (Wholesale, Trade) hold their own prices.</p>
        {canManage && <button type="button" style={primaryBtn} onClick={() => setEditing("new")}><Plus size={15} /> Add price list</button>}
      </div>
      {lists.isLoading && <p>Loading…</p>}
      {lists.isError && <ErrorBanner message={errorMessage(lists.error, "Couldn't load the price lists.")} />}
      {deleteError && <ErrorBanner message={deleteError} />}
      {lists.data && (
        <div style={{ overflowX: "auto", border: "1px solid var(--hf-primary-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Code</th><th style={th}>Name</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>
              {lists.data.map(l => (
                <tr key={l.id}>
                  <td style={{ ...td, fontWeight: 700 }}>{l.code}</td>
                  <td style={td}>{l.name}{l.defaultList && <span style={{ color: "var(--hf-text-faint)" }}> · default</span>}</td>
                  <td style={td}>{l.active ? "On" : "Off"}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    {canManage && <>
                      <button type="button" style={linkBtn} aria-label={`Edit ${l.code}`} onClick={() => setEditing(l)}>Edit</button>{" "}
                      {!l.defaultList && <button type="button" style={linkBtn} aria-label={`Delete ${l.code}`} onClick={() => { setDeleteError(""); del.mutate(l.id) }}>Delete</button>}
                    </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <PriceListModal list={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={() => { refresh(); setEditing(null) }} />}
    </div>
  )
}

function PriceListModal({ list, onClose, onSaved }: { list: PriceList | null; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<PriceListForm>(list ? toPriceListForm(list) : EMPTY_PRICE_LIST_FORM)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState("")
  const set = <K extends keyof PriceListForm>(k: K, v: PriceListForm[K]) => setForm(f => ({ ...f, [k]: v }))
  const save = useMutation({
    mutationFn: () => list ? apiClient.put(`/api/v1/catalogue/price-lists/${list.id}`, priceListBody(form)) : apiClient.post("/api/v1/catalogue/price-lists", priceListBody(form)),
    onSuccess: onSaved,
    onError: e => {
      const msg = errorMessage(e, "Couldn't save the price list."), server = fieldErrors(e)
      if (/code .* already exists/.test(msg)) { setErrors({ code: msg }); setGeneral("") } else if (/named .* already exists/.test(msg)) { setErrors({ name: msg }); setGeneral("") } else { setErrors(server); setGeneral(Object.keys(server).length ? "" : msg) }
    },
  })
  const submit = () => { const errs = priceListProblems(form, !list); setErrors(errs); setGeneral(""); if (Object.keys(errs).length === 0) save.mutate() }
  return (
    <Modal title={list ? `Edit — ${list.code}` : "New price list"} onClose={onClose}>
      <Field label="Code" required error={errors.code} id="pl-code">
        <input id="pl-code" style={errors.code ? inpErr : inp} value={form.code} readOnly={!!list} title={list ? "The code cannot change once created" : undefined} onChange={e => set("code", e.target.value)} placeholder="e.g. WHOLESALE" autoFocus={!list} />
      </Field>
      <Field label="Name" required error={errors.name} id="pl-name"><input id="pl-name" style={errors.name ? inpErr : inp} value={form.name} onChange={e => set("name", e.target.value)} placeholder="e.g. Wholesale" /></Field>
      <Field label="Order" error={errors.sortOrder} id="pl-order"><input id="pl-order" style={errors.sortOrder ? inpErr : inp} value={form.sortOrder} onChange={e => set("sortOrder", e.target.value)} inputMode="numeric" /></Field>
      {!list?.defaultList && (
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 16 }}>
          <input type="checkbox" checked={form.active} onChange={e => set("active", e.target.checked)} /> Available for pricing
        </label>
      )}
      {general && <ErrorBanner message={general} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
        <button type="button" style={cancelBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : list ? "Save changes" : "Create price list"}</button>
      </div>
    </Modal>
  )
}
