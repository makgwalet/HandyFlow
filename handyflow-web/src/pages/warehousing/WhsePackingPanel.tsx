// src/pages/warehousing/WhsePackingPanel.tsx
//
// Packing on an outbound order: put what ships into numbered packages, weigh and close each one, print its label. Optional: an order with no
// packages can still be marked packed, but once it has one, everything that ships must be in a closed package.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Package, Printer, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, card, ErrorLine, fmtQty, inputStyle, labelStyle } from "./whseUi"
import { parseQty } from "./receiving"

export interface PackedItem { lineId: string; sku: string; description: string; qty: number }
export interface PackageRow {
  id: string; packageNo: number; type: string; lengthMm: number | null; widthMm: number | null; heightMm: number | null
  tareKg: number; grossKg: number | null; status: "OPEN" | "CLOSED"; labelPrintedAt: string | null; weightNote: string | null; items: PackedItem[]
}
export interface LineProgress { lineId: string; sku: string; description: string; toShip: number; packed: number; remaining: number }
export interface PackingSummary { packages: PackageRow[]; lines: LineProgress[]; totalGrossKg: number; unpackedUnits: number; blockers: string[] }

/** A weight typed by hand: blank or not above zero is not a weight. */
export function parseWeight(s: string): number | null {
  const n = parseQty(s)
  return n == null || n <= 0 ? null : n
}

const TYPES = ["BOX", "PALLET", "ENVELOPE", "CRATE", "OTHER"]

function PackageCard({ orderId, pkg, lines, editable }: { orderId: string; pkg: PackageRow; lines: LineProgress[]; editable: boolean }) {
  const qc = useQueryClient()
  const [lineId, setLineId] = useState("")
  const [qty, setQty] = useState("")
  const [gross, setGross] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["whse-packing", orderId] })
  const open = pkg.status === "OPEN"
  const addable = lines.filter(l => l.remaining > 0 || pkg.items.some(i => i.lineId === l.lineId))
  const chosen = lineId || addable[0]?.lineId || ""
  const setItem = useMutation({
    mutationFn: async (v: { lineId: string; qty: number }) => apiClient.put(`/api/v1/warehousing/packing/packages/${pkg.id}/items`, v),
    onSuccess: () => { setQty(""); refresh() },
  })
  const close = useMutation({ mutationFn: async (kg: number) => apiClient.post(`/api/v1/warehousing/packing/packages/${pkg.id}/close`, { grossKg: kg }), onSuccess: refresh })
  const reopen = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/packing/packages/${pkg.id}/reopen`), onSuccess: refresh })
  const del = useMutation({ mutationFn: async () => apiClient.delete(`/api/v1/warehousing/packing/packages/${pkg.id}`), onSuccess: refresh })
  const label = useMutation({
    mutationFn: async () => {
      const res = await apiClient.get(`/api/v1/warehousing/packing/packages/${pkg.id}/label/pdf`, { responseType: "blob" })
      const url = window.URL.createObjectURL(new Blob([res.data]))
      const a = document.createElement("a")
      a.href = url; a.download = `label-package-${pkg.packageNo}.pdf`
      document.body.appendChild(a); a.click(); a.remove(); window.URL.revokeObjectURL(url)
    },
    onSuccess: refresh,
  })
  const qtyN = parseWeight(qty)
  const grossN = parseWeight(gross)
  return (
    <div style={{ padding: "10px 14px", borderTop: "1px solid var(--hf-border-subtle)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: 13 }}>
        <strong>Package {pkg.packageNo} · {pkg.type.toLowerCase()}{pkg.lengthMm && pkg.widthMm && pkg.heightMm ? ` · ${pkg.lengthMm}×${pkg.widthMm}×${pkg.heightMm} mm` : ""}</strong>
        <span style={{ fontWeight: 600, color: open ? "var(--hf-warning-text-deep)" : "var(--hf-success-text-strong)" }}>
          {open ? "Open" : `Closed · ${fmtQty(pkg.grossKg)} kg`}
        </span>
      </div>
      {pkg.items.length === 0 && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "6px 0" }}>Empty.</p>}
      {pkg.items.map(i => (
        <div key={i.lineId} style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, padding: "3px 0" }}>
          <span>{i.sku} <span style={{ color: "var(--hf-text-faint)" }}>{i.description}</span></span>
          <span>{fmtQty(i.qty)}
            {editable && open && <button aria-label={`Remove ${i.sku}`} onClick={() => setItem.mutate({ lineId: i.lineId, qty: 0 })} style={{ background: "none", border: "none", cursor: "pointer", marginLeft: 6 }}><Trash2 size={12} /></button>}
          </span>
        </div>
      ))}
      {pkg.weightNote && <p style={{ fontSize: 11.5, color: "var(--hf-warning-text-deep)", margin: "4px 0" }}>{pkg.weightNote}</p>}
      {editable && open && (
        <>
          {addable.length > 0 && (
            <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 8, flexWrap: "wrap" }}>
              <div><label style={labelStyle} htmlFor={`pl-${pkg.id}`}>Item</label>
                <select id={`pl-${pkg.id}`} aria-label={`Item for package ${pkg.packageNo}`} style={inputStyle} value={chosen} onChange={e => setLineId(e.target.value)}>
                  {addable.map(l => <option key={l.lineId} value={l.lineId}>{l.sku} ({fmtQty(l.remaining)} left)</option>)}
                </select></div>
              <div><label style={labelStyle} htmlFor={`pq-${pkg.id}`}>Quantity</label>
                <input id={`pq-${pkg.id}`} aria-label={`Quantity for package ${pkg.packageNo}`} style={{ ...inputStyle, width: 90 }} value={qty} onChange={e => setQty(e.target.value)} /></div>
              <button style={btnGhost} disabled={!chosen || qtyN == null || setItem.isPending} onClick={() => setItem.mutate({ lineId: chosen, qty: qtyN! })}>Put in package</button>
            </div>
          )}
          <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 8, flexWrap: "wrap" }}>
            <div><label style={labelStyle} htmlFor={`pg-${pkg.id}`}>Weighed gross weight (kg)</label>
              <input id={`pg-${pkg.id}`} aria-label={`Gross weight for package ${pkg.packageNo}`} style={{ ...inputStyle, width: 120 }} value={gross} onChange={e => setGross(e.target.value)} /></div>
            <button style={btnPrimary} disabled={grossN == null || pkg.items.length === 0 || close.isPending} onClick={() => close.mutate(grossN!)}>Close package</button>
            <button style={btnGhost} disabled={del.isPending} onClick={() => del.mutate()}>Delete</button>
          </div>
        </>
      )}
      {!open && (
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
          <button style={btnGhost} disabled={label.isPending} onClick={() => label.mutate()}><Printer size={13} /> {pkg.labelPrintedAt ? "Print label again" : "Print label"}</button>
          {editable && <button style={btnGhost} disabled={reopen.isPending} onClick={() => reopen.mutate()}>Reopen</button>}
        </div>
      )}
      <ErrorLine error={setItem.error ?? close.error ?? reopen.error ?? del.error ?? label.error} fallback="That could not be done" />
    </div>
  )
}

/** Shown while the order is being picked (editable) or once packed (read only, so the labels can still be printed). */
export default function PackingPanel({ orderId, status }: { orderId: string; status: string }) {
  const qc = useQueryClient()
  const [type, setType] = useState("BOX")
  const show = status === "PICKING" || status === "PACKED" || status === "SHIPPED"
  const { data } = useQuery<PackingSummary>({
    queryKey: ["whse-packing", orderId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/packing/orders/${orderId}`)).data,
    enabled: show,
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["whse-packing", orderId] })
  const create = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/packing/orders/${orderId}/packages`, { type }), onSuccess: refresh })
  const packAll = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/packing/orders/${orderId}/pack-remaining`, { type }), onSuccess: refresh })
  if (!show || !data) return null
  const editable = status === "PICKING"
  if (!editable && data.packages.length === 0) return null
  return (
    <div style={{ ...card, marginTop: 16, overflow: "hidden" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 14px", flexWrap: "wrap", gap: 8 }}>
        <p style={{ fontSize: 13, fontWeight: 700, margin: 0, display: "flex", alignItems: "center", gap: 6 }}><Package size={14} /> Packing
          {data.totalGrossKg > 0 && <span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}> · {fmtQty(data.totalGrossKg)} kg in {data.packages.length} package{data.packages.length === 1 ? "" : "s"}</span>}
        </p>
        {editable && (
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <select aria-label="Package type" style={inputStyle} value={type} onChange={e => setType(e.target.value)}>{TYPES.map(t => <option key={t} value={t}>{t.toLowerCase()}</option>)}</select>
            {data.unpackedUnits > 0 && <button style={btnGhost} disabled={packAll.isPending} onClick={() => packAll.mutate()}>Pack everything left</button>}
            <button style={btnPrimary} disabled={create.isPending} onClick={() => create.mutate()}>New package</button>
          </div>
        )}
      </div>
      {editable && data.packages.length === 0 && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "0 14px 12px" }}>Optional. Without packages the order can still be marked packed.</p>}
      {data.packages.length > 0 && (
        <div style={{ padding: "0 14px 8px" }}>
          {data.lines.map(l => (
            <span key={l.lineId} style={{ display: "inline-block", fontSize: 11.5, marginRight: 12, color: l.remaining > 0 ? "var(--hf-warning-text-deep)" : "var(--hf-success-text-strong)" }}>
              {l.sku}: {fmtQty(l.packed)} of {fmtQty(l.toShip)} packed
            </span>
          ))}
        </div>
      )}
      {data.packages.map(p => <PackageCard key={p.id} orderId={orderId} pkg={p} lines={data.lines} editable={editable} />)}
      {editable && data.blockers.length > 0 && <p style={{ fontSize: 11.5, color: "var(--hf-warning-text-deep)", margin: "0 14px 12px" }}>{data.blockers.join(" ")}</p>}
      <ErrorLine error={create.error ?? packAll.error} fallback="Could not make the package" />
    </div>
  )
}
