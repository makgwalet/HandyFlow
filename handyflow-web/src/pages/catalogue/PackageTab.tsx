// src/pages/catalogue/PackageTab.tsx — the parts of a package (bundle), what they are worth, and the saving. Also "used in" for ordinary items.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ErrorBanner } from "./CatalogueKit"
import { linkBtn, primaryBtn } from "./catalogue.styles"
import {
  bundleBody, bundleProblem, errorMessage, rand, savingText,
  type BundleDraftLine, type BundleView, type CatalogueItem, type PageOf, type UsedIn,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", borderBottom: "1px solid var(--hf-border-subtle)" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)" }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const field: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }

export function UsedInList({ itemId }: { itemId: string }) {
  const q = useQuery<UsedIn[]>({ queryKey: ["catalogue", "used-in", itemId], queryFn: async () => inner<UsedIn[]>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/used-in`)) })
  if (!q.data || q.data.length === 0) return null
  return (
    <section aria-label="Used in packages" style={{ marginTop: 16 }}>
      <h4 style={{ margin: "0 0 8px", fontSize: 13, color: "var(--hf-text)" }}>Used in packages</h4>
      <ul style={{ margin: 0, paddingLeft: 18, ...muted }}>{q.data.map(u => <li key={u.bundleId}>{u.name} ({Number(u.quantity)} each){u.status !== "ACTIVE" ? ` · ${u.status.toLowerCase()}` : ""}</li>)}</ul>
    </section>
  )
}

export default function PackageTab({ itemId, canWrite, onChanged }: { itemId: string; canWrite: boolean; onChanged: () => void }) {
  const qc = useQueryClient()
  const key = ["catalogue", "bundle", itemId]
  const q = useQuery<BundleView>({ queryKey: key, queryFn: async () => inner<BundleView>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/bundle`)) })
  const [editing, setEditing] = useState(false)
  const [lines, setLines] = useState<BundleDraftLine[]>([])
  const [names, setNames] = useState<Record<string, string>>({})
  const [search, setSearch] = useState("")
  const [error, setError] = useState("")

  const found = useQuery<PageOf<CatalogueItem>>({
    queryKey: ["catalogue", "bundle-search", search], enabled: editing && search.trim().length >= 2,
    queryFn: async () => inner<PageOf<CatalogueItem>>(await apiClient.get("/api/v1/catalogue/items/search", { params: { q: search.trim(), size: 8 } })),
  })

  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/catalogue/items/${itemId}/bundle`, bundleBody(lines)),
    onSuccess: () => { setEditing(false); qc.invalidateQueries({ queryKey: key }); onChanged() },
    onError: e => setError(errorMessage(e, "Couldn't save the package.")),
  })

  const startEdit = () => {
    const parts = q.data?.components ?? []
    setLines(parts.map(p => ({ itemId: p.itemId, quantity: String(Number(p.quantity)) })))
    setNames(Object.fromEntries(parts.map(p => [p.itemId, p.name])))
    setSearch(""); setError(""); setEditing(true)
  }
  const add = (i: CatalogueItem) => {
    if (lines.some(l => l.itemId === i.id)) { setError("That item is already in the package."); return }
    setError(""); setNames(n => ({ ...n, [i.id]: i.name })); setLines(l => [...l, { itemId: i.id, quantity: "1" }]); setSearch("")
  }
  const problem = editing ? bundleProblem(lines, itemId) : ""
  const candidates = (found.data?.content ?? []).filter(i => i.itemType !== "PACKAGE" && (i.variantCount ?? 0) === 0 && i.status !== "ARCHIVED" && i.id !== itemId)

  return (
    <div>
      {q.isLoading && <p role="status">Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the package.")} />}
      {error && <ErrorBanner message={error} />}
      {q.data && !editing && (
        <>
          {q.data.components.length === 0 && <p style={muted}>No parts yet. Add the items this package is made of.</p>}
          {q.data.components.length > 0 && (
            <>
              <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 12 }}>
                <thead><tr><th style={th}>Part</th><th style={th}>SKU</th><th style={th}>Qty</th><th style={th}>Price</th><th style={th}>Total</th></tr></thead>
                <tbody>{q.data.components.map(c => <tr key={c.itemId}><td style={td}>{c.name}</td><td style={td}>{c.sku ?? "—"}</td><td style={td}>{Number(c.quantity)}</td><td style={td}>{rand(c.unitPrice)}</td><td style={td}>{rand(c.lineTotal)}</td></tr>)}</tbody>
              </table>
              {q.data.partsTotal !== null && (
                <p style={muted}>Parts together: <strong>{rand(q.data.partsTotal)}</strong> · Package price: <strong>{rand(q.data.price)}</strong>. {savingText(q.data.saving, q.data.unpricedParts)}</p>
              )}
            </>
          )}
          {canWrite && <button type="button" style={primaryBtn} onClick={startEdit}>{q.data.components.length === 0 ? "Add parts" : "Edit parts"}</button>}
        </>
      )}
      {editing && (
        <div>
          <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }} htmlFor="pkg-search">Find an item to add</label>
          <input id="pkg-search" style={{ ...field, width: "100%", maxWidth: 360 }} value={search} onChange={e => setSearch(e.target.value)} placeholder="Name, SKU or barcode" />
          {candidates.length > 0 && (
            <ul style={{ listStyle: "none", margin: "6px 0 0", padding: 0, border: "1px solid var(--hf-border-subtle)", borderRadius: 8, maxWidth: 360 }}>
              {candidates.map(i => <li key={i.id}><button type="button" style={{ ...linkBtn, border: "none", width: "100%", textAlign: "left" }} onClick={() => add(i)}>{i.name}{i.sku ? ` · ${i.sku}` : ""}</button></li>)}
            </ul>
          )}
          <table style={{ width: "100%", borderCollapse: "collapse", margin: "14px 0" }}>
            <thead><tr><th style={th}>Part</th><th style={th}>Quantity</th><th style={th}><span style={{ position: "absolute", left: -9999 }}>Remove</span></th></tr></thead>
            <tbody>
              {lines.map((l, idx) => (
                <tr key={l.itemId}>
                  <td style={td}>{names[l.itemId] ?? l.itemId}</td>
                  <td style={td}><input aria-label={`Quantity of ${names[l.itemId] ?? "part"}`} inputMode="decimal" style={{ ...field, width: 90 }} value={l.quantity} onChange={e => setLines(ls => ls.map((x, i) => i === idx ? { ...x, quantity: e.target.value } : x))} /></td>
                  <td style={td}><button type="button" style={linkBtn} onClick={() => setLines(ls => ls.filter((_, i) => i !== idx))} aria-label={`Remove ${names[l.itemId] ?? "part"}`}>Remove</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          {problem && lines.length > 0 && <p role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" style={primaryBtn} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Saving…" : "Save parts"}</button>
            <button type="button" style={linkBtn} onClick={() => { setEditing(false); setSearch(""); setError("") }}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}
