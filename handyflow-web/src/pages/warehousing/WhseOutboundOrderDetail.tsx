// src/pages/warehousing/WhseOutboundOrderDetail.tsx
//
// Order-level workflow buttons gated by current status (PENDING ->
// startPicking -> PICKING -> markPacked -> PACKED -> markShipped ->
// SHIPPED; cancel available from PENDING/PICKING only — all confirmed via
// WhseOutboundOrder entity's own guard methods). Per-line "mark picked" is
// informational progress tracking only (confirmed: markShipped always
// fulfils the full ordered qty regardless of qtyPicked) — flagged to
// staff via the helper line below the line list, not silently hidden.
// Packing-slip PDF download uses the same blob+anchor pattern as
// CollAgencyDebtorAccountDetail's demand-letter download.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, PackageCheck, XCircle, Download, Truck } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface OutboundOrderResponse {
  id: string; orderReference: string | null; shipToName: string | null; status: string
  requestedShipDate: string | null; shippedDate: string | null; carrier: string | null; trackingNumber: string | null
}
interface OutboundOrderLineResponse { id: string; orderId: string; itemId: string; locationId: string | null; qtyOrdered: number; qtyPicked: number; notes: string | null }
interface ItemLite { id: string; sku: string }

const inputStyle: React.CSSProperties = { padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12.5, boxSizing: "border-box", fontFamily: "inherit" }
const btnStyle: React.CSSProperties = { display: "flex", alignItems: "center", gap: 6, border: "none", borderRadius: 8, padding: "8px 14px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }

export default function WhseOutboundOrderDetail({ orderId, clientId, items, onBack }: {
  orderId: string; clientId: string; items: ItemLite[]; onBack: () => void
}) {
  const qc = useQueryClient()
  const [pickQty, setPickQty] = useState<Record<string, string>>({})
  const [carrier, setCarrier] = useState("")
  const [trackingNumber, setTrackingNumber] = useState("")
  const [showShipForm, setShowShipForm] = useState(false)

  const { data: order } = useQuery<OutboundOrderResponse>({
    queryKey: ["whse-outbound-order", orderId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/outbound-orders/${orderId}`)).data,
  })
  const { data: lines = [], isLoading } = useQuery<OutboundOrderLineResponse[]>({
    queryKey: ["whse-outbound-order-lines", orderId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/outbound-orders/${orderId}/lines`)).data,
  })

  const invalidateAll = () => {
    qc.invalidateQueries({ queryKey: ["whse-outbound-order", orderId] })
    qc.invalidateQueries({ queryKey: ["whse-outbound-order-lines", orderId] })
    qc.invalidateQueries({ queryKey: ["whse-outbound-orders", clientId] })
    qc.invalidateQueries({ queryKey: ["whse-inventory", clientId] })
  }

  const startPicking = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/outbound-orders/${orderId}/start-picking`), onSuccess: invalidateAll })
  const markPacked = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/outbound-orders/${orderId}/pack`), onSuccess: invalidateAll })
  const markShipped = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/outbound-orders/${orderId}/ship`, { carrier: carrier || null, trackingNumber: trackingNumber || null }),
    onSuccess: () => { invalidateAll(); setShowShipForm(false) },
  })
  const cancelOrder = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/outbound-orders/${orderId}/cancel`), onSuccess: invalidateAll })
  const markPicked = useMutation({
    mutationFn: async ({ lineId, qty }: { lineId: string; qty: number }) => apiClient.post(`/api/v1/warehousing/outbound-orders/${orderId}/lines/${lineId}/pick`, { qty }),
    onSuccess: (_d, vars) => { qc.invalidateQueries({ queryKey: ["whse-outbound-order-lines", orderId] }); setPickQty(s => ({ ...s, [vars.lineId]: "" })) },
  })

  const downloadPackingSlip = async () => {
    const res = await apiClient.get(`/api/v1/warehousing/outbound-orders/${orderId}/packing-slip/pdf`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement("a")
    a.href = url
    a.download = `packing-slip-${order?.orderReference ?? orderId.slice(0, 8)}.pdf`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  const skuOf = (itemId: string) => items.find(i => i.id === itemId)?.sku ?? itemId.slice(0, 8)
  const status = order?.status

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 14, padding: 0 }}>
        <ArrowLeft size={15} /> All orders
      </button>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div>
          <h3 style={{ fontSize: 16, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>{order?.orderReference ?? "Order"}</h3>
          <p style={{ fontSize: 12.5, color: "#94A3B8", margin: 0 }}>
            Status: {status} {order?.shipToName ? `· Ship to ${order.shipToName}` : ""}
            {order?.carrier ? ` · ${order.carrier}${order.trackingNumber ? ` #${order.trackingNumber}` : ""}` : ""}
          </p>
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button onClick={downloadPackingSlip} style={{ ...btnStyle, background: "none", border: "1px solid #E2E8F0", color: "#64748B" }}>
            <Download size={13} /> Packing slip
          </button>
          {status === "PENDING" && (
            <>
              <button onClick={() => startPicking.mutate()} style={{ ...btnStyle, background: WHSE_ACCENT, color: "#fff" }}>Start picking</button>
              <button onClick={() => { if (confirm("Cancel this order?")) cancelOrder.mutate() }} style={{ ...btnStyle, background: "none", border: "1px solid #FECACA", color: "#DC2626" }}>
                <XCircle size={13} /> Cancel
              </button>
            </>
          )}
          {status === "PICKING" && (
            <>
              <button onClick={() => markPacked.mutate()} style={{ ...btnStyle, background: WHSE_ACCENT, color: "#fff" }}>Mark packed</button>
              <button onClick={() => { if (confirm("Cancel this order? Any allocated stock will be released.")) cancelOrder.mutate() }} style={{ ...btnStyle, background: "none", border: "1px solid #FECACA", color: "#DC2626" }}>
                <XCircle size={13} /> Cancel
              </button>
            </>
          )}
          {status === "PACKED" && (
            <button onClick={() => setShowShipForm(true)} style={{ ...btnStyle, background: WHSE_ACCENT, color: "#fff" }}>
              <Truck size={13} /> Mark shipped
            </button>
          )}
        </div>
      </div>

      {showShipForm && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 18, display: "flex", gap: 10, alignItems: "flex-end", flexWrap: "wrap" }}>
          <div><label style={{ fontSize: 11.5, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }}>Carrier</label><input style={inputStyle} value={carrier} onChange={e => setCarrier(e.target.value)} /></div>
          <div><label style={{ fontSize: 11.5, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }}>Tracking number</label><input style={inputStyle} value={trackingNumber} onChange={e => setTrackingNumber(e.target.value)} /></div>
          <button onClick={() => markShipped.mutate()} disabled={markShipped.isPending} style={{ ...btnStyle, background: WHSE_ACCENT, color: "#fff" }}>{markShipped.isPending ? "Shipping…" : "Confirm shipped"}</button>
          <button onClick={() => setShowShipForm(false)} style={{ ...btnStyle, background: "none", border: "1px solid #E2E8F0", color: "#64748B" }}>Cancel</button>
        </div>
      )}

      <p style={{ fontSize: 12.5, fontWeight: 700, color: "#0F172A", margin: "0 0 10px" }}>Lines</p>
      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {lines.map((line, i) => (
            <div key={line.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div>
                <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{skuOf(line.itemId)}</p>
                <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{line.qtyPicked} / {line.qtyOrdered} picked{line.notes ? ` · ${line.notes}` : ""}</p>
              </div>
              {status === "PICKING" && line.qtyPicked < line.qtyOrdered && (
                <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                  <input type="number" step="0.01" placeholder="Qty" style={{ ...inputStyle, width: 80 }}
                    value={pickQty[line.id] ?? ""} onChange={e => setPickQty(s => ({ ...s, [line.id]: e.target.value }))} />
                  <button onClick={() => markPicked.mutate({ lineId: line.id, qty: parseFloat(pickQty[line.id]) })}
                    disabled={!pickQty[line.id] || markPicked.isPending}
                    style={{ ...btnStyle, background: WHSE_ACCENT, color: "#fff", opacity: !pickQty[line.id] ? 0.5 : 1 }}>
                    <PackageCheck size={12} /> Pick
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {status === "PICKING" && (
        <p style={{ fontSize: 11, color: "#94A3B8", marginTop: 8 }}>
          "Pick" tracks picking progress for staff — shipping the order always fulfils the full ordered quantity on every line, regardless of what's recorded picked here.
        </p>
      )}
    </div>
  )
}
