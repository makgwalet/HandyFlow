// src/pages/warehousing/WhseInboundShipmentDetail.tsx
//
// Receiving with inspection and discrepancies. Each receipt splits what arrived into good, damaged and held-for-inspection, says where it
// is put away, and for lot-tracked items names the lot (and expiry date). The delivery's discrepancies (short, over, damaged, unexpected,
// held) are listed as they arise, and closing the delivery with any of them needs a note. Held stock waits in the Inspection section.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, PackageCheck, XCircle, Plus } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"
import { btnDanger, btnGhost, btnPrimary, Chip, ErrorLine, fmtQty, inputStyle, labelStyle, today } from "./whseUi"
import { buildReceiptPayload, emptyReceipt, type ReceiptForm, shelfLifeWarning, type Tracking, validateClose, validateReceipt } from "./receiving"

interface InboundShipmentResponse { id: string; referenceNumber: string | null; expectedDate: string | null; receivedDate: string | null; status: string; notes: string | null }
interface InboundShipmentLineResponse { id: string; shipmentId: string; itemId: string; expectedQty: number; qtyReceived: number; notes: string | null }
interface LocationResponse { id: string; code: string; active: boolean; locationType?: string }
interface ItemLite { id: string; sku: string; lotTracked?: boolean; expiryTracked?: boolean; minShelfLifeDays?: number | null }
interface ReceiptRow {
  id: string; lineId: string; sku: string; locationCode: string; qtyGood: number; qtyDamaged: number; qtyHeld: number; heldPending: number
  reason: string | null; receivedAt: string; lotNumber: string | null; expiryDate: string | null
}
interface LineDiscrepancy { lineId: string; sku: string; unexpected: boolean; expected: number; received: number; shortQty: number; overQty: number; heldPending: number; types: string[] }
interface DiscrepancyReport { closed: boolean; closeNote: string | null; needsSignOff: boolean; lines: LineDiscrepancy[] }

const TYPE_LABEL: Record<string, string> = { SHORT: "Short", OVER: "Over", DAMAGED: "Damaged", UNEXPECTED_ITEM: "Not on the order", HELD_FOR_INSPECTION: "Held for inspection" }
const trackingOf = (i?: ItemLite): Tracking => ({ lotTracked: !!i?.lotTracked, expiryTracked: !!i?.expiryTracked, minShelfLifeDays: i?.minShelfLifeDays })

function ReceiveForm({ shipmentId, lineId, item, locations, onDone }: { shipmentId: string; lineId: string; item?: ItemLite; locations: LocationResponse[]; onDone: () => void }) {
  const [f, setF] = useState<ReceiptForm>(emptyReceipt)
  const t = trackingOf(item)
  const set = (k: keyof ReceiptForm, v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = validateReceipt(f, t)
  const warn = t.expiryTracked ? shelfLifeWarning(f.expiryDate, today(), t.minShelfLifeDays) : null
  const receive = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/inbound-shipments/${shipmentId}/lines/${lineId}/receipts`, buildReceiptPayload(f, t)),
    onSuccess: () => { setF(emptyReceipt); onDone() },
  })
  const num = (k: keyof ReceiptForm, label: string) => (
    <div style={{ width: 84 }}><label style={labelStyle}>{label}</label><input aria-label={label} inputMode="decimal" style={{ ...inputStyle, width: "100%" }} value={f[k]} onChange={e => set(k, e.target.value)} /></div>
  )
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
        {num("qtyGood", "Good")}{num("qtyDamaged", "Damaged")}{num("qtyHeld", "Hold for QC")}
        <div style={{ flex: 1, minWidth: 150 }}>
          <label style={labelStyle}>Put away at</label>
          <select aria-label="Put away at" style={{ ...inputStyle, width: "100%" }} value={f.locationId} onChange={e => set("locationId", e.target.value)}>
            <option value="">Choose…</option>{locations.map(l => <option key={l.id} value={l.id}>{l.code}</option>)}
          </select>
        </div>
      </div>
      {t.lotTracked && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <div style={{ width: 130 }}><label style={labelStyle}>Lot number *</label><input aria-label="Lot number" style={{ ...inputStyle, width: "100%" }} value={f.lotNumber} onChange={e => set("lotNumber", e.target.value)} /></div>
          {t.expiryTracked && <div><label style={labelStyle}>Expiry date *</label><input aria-label="Expiry date" type="date" style={inputStyle} value={f.expiryDate} onChange={e => set("expiryDate", e.target.value)} /></div>}
          <div><label style={labelStyle}>Made on</label><input aria-label="Manufactured date" type="date" style={inputStyle} value={f.manufacturedDate} onChange={e => set("manufacturedDate", e.target.value)} /></div>
        </div>
      )}
      <div><label style={labelStyle}>What is wrong (damaged or held)</label><input aria-label="Reason" style={{ ...inputStyle, width: "100%" }} value={f.reason} onChange={e => set("reason", e.target.value)} /></div>
      {warn && <p style={{ fontSize: 12, color: "var(--hf-warning-text-deep)", margin: 0 }}>{warn}</p>}
      <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
        <button onClick={() => receive.mutate()} disabled={!!problem || receive.isPending} style={{ ...btnPrimary, opacity: problem ? 0.5 : 1 }}><PackageCheck size={13} /> {receive.isPending ? "Receiving…" : "Receive"}</button>
        {problem && (f.qtyGood || f.qtyDamaged || f.qtyHeld) && <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{problem}</span>}
      </div>
      <ErrorLine error={receive.error} fallback="Could not record this receipt" />
    </div>
  )
}

export default function WhseInboundShipmentDetail({ shipmentId, clientId, items, onBack }: { shipmentId: string; clientId: string; items: ItemLite[]; onBack: () => void }) {
  const qc = useQueryClient()
  const [openLine, setOpenLine] = useState<string | null>(null)
  const [closeNote, setCloseNote] = useState("")
  const [unexpected, setUnexpected] = useState({ itemId: "", note: "" })

  const base = `/api/v1/warehousing/inbound-shipments/${shipmentId}`
  const { data: shipment } = useQuery<InboundShipmentResponse>({ queryKey: ["whse-inbound-shipment", shipmentId], queryFn: async () => (await apiClient.get(base)).data })
  const { data: lines = [], isLoading } = useQuery<InboundShipmentLineResponse[]>({ queryKey: ["whse-inbound-shipment-lines", shipmentId], queryFn: async () => (await apiClient.get(`${base}/lines`)).data })
  const { data: receipts = [] } = useQuery<ReceiptRow[]>({ queryKey: ["whse-receipts", shipmentId], queryFn: async () => (await apiClient.get(`${base}/receipts`)).data })
  const { data: report } = useQuery<DiscrepancyReport>({ queryKey: ["whse-discrepancies", shipmentId], queryFn: async () => (await apiClient.get(`${base}/discrepancies`)).data })
  const { data: locations = [] } = useQuery<LocationResponse[]>({ queryKey: ["whse-locations"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data })

  const refresh = () => {
    for (const k of ["whse-inbound-shipment-lines", "whse-inbound-shipment", "whse-receipts", "whse-discrepancies"]) qc.invalidateQueries({ queryKey: [k, shipmentId] })
    qc.invalidateQueries({ queryKey: ["whse-inventory", clientId] }); qc.invalidateQueries({ queryKey: ["whse-inspection-queue"] })
    qc.invalidateQueries({ queryKey: ["whse-inbound-shipments", clientId] })
  }
  const cancelShipment = useMutation({ mutationFn: async () => apiClient.post(`${base}/cancel`), onSuccess: refresh })
  const addUnexpected = useMutation({
    mutationFn: async () => apiClient.post(`${base}/unexpected-lines`, { itemId: unexpected.itemId, note: unexpected.note.trim() || null }),
    onSuccess: () => { setUnexpected({ itemId: "", note: "" }); refresh() },
  })
  const closeDelivery = useMutation({ mutationFn: async () => apiClient.post(`${base}/close`, { note: closeNote.trim() || null }), onSuccess: () => { setCloseNote(""); refresh() } })

  const itemOf = (id: string) => items.find(i => i.id === id)
  const skuOf = (id: string) => itemOf(id)?.sku ?? id.slice(0, 8)
  const closed = !!report?.closed
  const terminal = shipment?.status === "CANCELLED" || closed
  const closeProblem = report ? validateClose(report.needsSignOff, closeNote) : null
  const flagged = (report?.lines ?? []).filter(l => l.types.length > 0)
  const activeLocations = locations.filter(l => l.active)

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", fontSize: 13, marginBottom: 14, padding: 0 }}>
        <ArrowLeft size={15} /> All shipments
      </button>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18, gap: 10, flexWrap: "wrap" }}>
        <div>
          <h3 style={{ fontSize: 16, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>{shipment?.referenceNumber ?? "Shipment"}</h3>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>
            Status: {closed ? "CLOSED" : shipment?.status.replace(/_/g, " ")} {shipment?.expectedDate ? `· Expected ${shipment.expectedDate}` : ""}
          </p>
        </div>
        {!terminal && (
          <button onClick={() => { if (confirm("Cancel this shipment?")) cancelShipment.mutate() }} style={btnDanger}><XCircle size={14} /> Cancel shipment</button>
        )}
      </div>
      <ErrorLine error={cancelShipment.error} />

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {lines.map((line, i) => {
            const d = report?.lines.find(x => x.lineId === line.id)
            return (
              <div key={line.id} style={{ padding: "14px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>
                      {skuOf(line.itemId)} {itemOf(line.itemId)?.lotTracked && <span style={{ fontSize: 10.5, color: "var(--hf-text-faint)", fontWeight: 600 }}> · lot tracked{itemOf(line.itemId)?.expiryTracked ? ", expires" : ""}</span>}
                    </p>
                    <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{fmtQty(line.qtyReceived)} / {fmtQty(line.expectedQty)} received{line.notes ? ` · ${line.notes}` : ""}</p>
                  </div>
                  <div style={{ display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap" }}>
                    {(d?.types ?? []).map(tp => <Chip key={tp} bg="var(--hf-warning-soft-strong)" fg="var(--hf-warning-text-deep)">{TYPE_LABEL[tp] ?? tp}</Chip>)}
                    {!terminal && <button onClick={() => setOpenLine(openLine === line.id ? null : line.id)} style={btnGhost}>{openLine === line.id ? "Close" : "Receive"}</button>}
                  </div>
                </div>
                {openLine === line.id && !terminal && (
                  <div style={{ marginTop: 12 }}>
                    <ReceiveForm shipmentId={shipmentId} lineId={line.id} item={itemOf(line.itemId)} locations={activeLocations} onDone={() => { refresh(); setOpenLine(null) }} />
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {!terminal && (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end", flexWrap: "wrap", marginTop: 14 }}>
          <div><label style={labelStyle}>An item that arrived but is not on the order</label>
            <select aria-label="Unexpected item" style={{ ...inputStyle, minWidth: 190 }} value={unexpected.itemId} onChange={e => setUnexpected(u => ({ ...u, itemId: e.target.value }))}>
              <option value="">Choose item…</option>{items.map(i => <option key={i.id} value={i.id}>{i.sku}</option>)}
            </select></div>
          <div style={{ flex: 1, minWidth: 140 }}><label style={labelStyle}>Note</label><input aria-label="Unexpected item note" style={{ ...inputStyle, width: "100%" }} value={unexpected.note} onChange={e => setUnexpected(u => ({ ...u, note: e.target.value }))} /></div>
          <button onClick={() => addUnexpected.mutate()} disabled={!unexpected.itemId || addUnexpected.isPending} style={{ ...btnGhost, opacity: unexpected.itemId ? 1 : 0.5 }}><Plus size={13} /> Add line</button>
        </div>
      )}
      <ErrorLine error={addUnexpected.error} />

      {receipts.length > 0 && (
        <div style={{ marginTop: 22 }}>
          <p style={{ fontSize: 12.5, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 8px" }}>Receipts</p>
          <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
            {receipts.map((r, i) => (
              <div key={r.id} style={{ padding: "10px 14px", fontSize: 12.5, borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <strong>{r.sku}</strong> → {r.locationCode}: {fmtQty(r.qtyGood)} good{r.qtyDamaged > 0 ? `, ${fmtQty(r.qtyDamaged)} damaged` : ""}{r.qtyHeld > 0 ? `, ${fmtQty(r.qtyHeld)} held (${fmtQty(r.heldPending)} still waiting)` : ""}
                {r.lotNumber ? ` · lot ${r.lotNumber}${r.expiryDate ? ` (exp ${r.expiryDate})` : ""}` : ""}
                {r.reason && <span style={{ color: "var(--hf-text-faint)" }}> · {r.reason}</span>}
              </div>
            ))}
          </div>
        </div>
      )}

      {report && (
        <div style={{ marginTop: 22, ...{ border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14 } }}>
          <p style={{ fontSize: 12.5, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 8px" }}>Discrepancies</p>
          {flagged.length === 0 ? <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>None so far.</p> : flagged.map(l => (
            <p key={l.lineId} style={{ fontSize: 12.5, margin: "0 0 4px", color: "var(--hf-text)" }}>
              <strong>{l.sku}</strong>: {l.types.map(tp => TYPE_LABEL[tp] ?? tp).join(", ")}
              {l.shortQty > 0 ? ` · ${fmtQty(l.shortQty)} short` : ""}{l.overQty > 0 ? ` · ${fmtQty(l.overQty)} over` : ""}{l.heldPending > 0 ? ` · ${fmtQty(l.heldPending)} waiting for inspection` : ""}
            </p>
          ))}
          {closed ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>Delivery closed{report.closeNote ? `: ${report.closeNote}` : "."}</p> : !terminal && (
            <div style={{ marginTop: 12 }}>
              <label style={labelStyle}>{report.needsSignOff ? "Note explaining the discrepancies *" : "Closing note"}</label>
              <input aria-label="Closing note" style={{ ...inputStyle, width: "100%" }} value={closeNote} onChange={e => setCloseNote(e.target.value)} />
              <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 10 }}>
                <button onClick={() => closeDelivery.mutate()} disabled={!!closeProblem || closeDelivery.isPending} style={{ ...btnPrimary, background: WHSE_ACCENT, opacity: closeProblem ? 0.5 : 1 }}>Close delivery</button>
                {closeProblem && <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{closeProblem}</span>}
              </div>
              <ErrorLine error={closeDelivery.error} fallback="Could not close the delivery" />
            </div>
          )}
        </div>
      )}
    </div>
  )
}
