// src/pages/warehousing/WhseOutboundOrdersTab.tsx
//
// List + create for a client's outbound fulfilment orders. Confirmed
// workflow (WhseOutboundOrderService's own class Javadoc): create ->
// startPicking (server auto-allocates stock, one location per line, no
// manual location choice) -> markLinePicked (progress only) -> markPacked
// -> markShipped -> or cancel (releases any allocation). Drills into
// WhseOutboundOrderDetail for the line-level workflow.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, PackageCheck, X, ChevronRight, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"
import WhseOutboundOrderDetail from "./WhseOutboundOrderDetail"

export interface OutboundOrderResponse {
  id: string; clientId: string; orderReference: string | null; shipToName: string | null; shipToAddress: string | null
  requestedShipDate: string | null; shippedDate: string | null
  status: "PENDING" | "PICKING" | "PACKED" | "SHIPPED" | "CANCELLED"
  carrier: string | null; trackingNumber: string | null; notes: string | null
}
interface ItemResponse { id: string; sku: string; description: string | null }
interface OrderPage { content: OutboundOrderResponse[] }

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  PENDING: { bg: "#F1F5F9", fg: "#475569" }, PICKING: { bg: "#DBEAFE", fg: "#1D4ED8" },
  PACKED: { bg: "#FEF3C7", fg: "#92400E" }, SHIPPED: { bg: "#DCFCE7", fg: "#166534" },
  CANCELLED: { bg: "#F1F5F9", fg: "#94A3B8" },
}
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function NewOrderModal({ clientId, items, onClose }: { clientId: string; items: ItemResponse[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [orderReference, setOrderReference] = useState("")
  const [shipToName, setShipToName] = useState("")
  const [shipToAddress, setShipToAddress] = useState("")
  const [requestedShipDate, setRequestedShipDate] = useState("")
  const [notes, setNotes] = useState("")
  const [lines, setLines] = useState([{ itemId: "", qtyOrdered: "", notes: "" }])

  const addLine = () => setLines([...lines, { itemId: "", qtyOrdered: "", notes: "" }])
  const removeLine = (i: number) => setLines(lines.filter((_, idx) => idx !== i))
  const updateLine = (i: number, field: string, value: string) => setLines(lines.map((l, idx) => idx === i ? { ...l, [field]: value } : l))

  const create = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/clients/${clientId}/outbound-orders`, {
      orderReference: orderReference || null, shipToName: shipToName || null, shipToAddress: shipToAddress || null,
      requestedShipDate: requestedShipDate || null, notes: notes || null,
      lines: lines.filter(l => l.itemId && l.qtyOrdered).map(l => ({ itemId: l.itemId, qtyOrdered: parseFloat(l.qtyOrdered), notes: l.notes || null })),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-outbound-orders", clientId] }); onClose() },
  })

  const valid = lines.some(l => l.itemId && l.qtyOrdered)

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "85vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>New outbound order</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 12 }}>
          <div><label style={labelStyle}>Order reference</label><input style={inputStyle} value={orderReference} onChange={e => setOrderReference(e.target.value)} /></div>
          <div><label style={labelStyle}>Requested ship date</label><input type="date" style={inputStyle} value={requestedShipDate} onChange={e => setRequestedShipDate(e.target.value)} /></div>
        </div>
        <div style={{ marginBottom: 12 }}><label style={labelStyle}>Ship to</label><input style={inputStyle} value={shipToName} onChange={e => setShipToName(e.target.value)} placeholder="End customer name" /></div>
        <div style={{ marginBottom: 12 }}><label style={labelStyle}>Ship-to address</label><textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={shipToAddress} onChange={e => setShipToAddress(e.target.value)} /></div>
        <div><label style={labelStyle}>Notes</label><textarea style={{ ...inputStyle, minHeight: 40, resize: "vertical" }} value={notes} onChange={e => setNotes(e.target.value)} /></div>

        <p style={{ fontSize: 12.5, fontWeight: 700, color: "#0F172A", margin: "16px 0 8px" }}>Ordered lines</p>
        {lines.map((line, i) => (
          <div key={i} style={{ display: "flex", gap: 8, marginBottom: 8, alignItems: "center" }}>
            <select style={{ ...inputStyle, flex: 2 }} value={line.itemId} onChange={e => updateLine(i, "itemId", e.target.value)}>
              <option value="">Select item…</option>
              {items.map(it => <option key={it.id} value={it.id}>{it.sku}{it.description ? ` — ${it.description}` : ""}</option>)}
            </select>
            <input type="number" step="0.01" placeholder="Qty" style={{ ...inputStyle, flex: 1 }} value={line.qtyOrdered} onChange={e => updateLine(i, "qtyOrdered", e.target.value)} />
            {lines.length > 1 && (
              <button onClick={() => removeLine(i)} style={{ background: "none", border: "none", cursor: "pointer", padding: 4 }}><Trash2 size={14} color="#DC2626" /></button>
            )}
          </div>
        ))}
        <button onClick={addLine} style={{ background: "none", border: "1px dashed #CBD5E1", borderRadius: 8, padding: "7px 12px", fontSize: 12, fontWeight: 600, color: "#64748B", cursor: "pointer", marginTop: 4 }}>
          + Add line
        </button>

        {create.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(create.error as any)?.response?.data?.message ?? "Could not create this order"}</p>}

        <button onClick={() => create.mutate()} disabled={!valid || create.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!valid || create.isPending) ? 0.6 : 1 }}>
          {create.isPending ? "Creating…" : "Create order"}
        </button>
      </div>
    </div>
  )
}

export default function WhseOutboundOrdersTab({ clientId }: { clientId: string }) {
  const [showForm, setShowForm] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data, isLoading } = useQuery<OrderPage | OutboundOrderResponse[]>({
    queryKey: ["whse-outbound-orders", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/outbound-orders?size=50`)).data,
  })
  const orders = Array.isArray(data) ? data : data?.content ?? []

  const { data: items = [] } = useQuery<ItemResponse[]>({
    queryKey: ["whse-items", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data,
  })

  if (selectedId) return <WhseOutboundOrderDetail orderId={selectedId} clientId={clientId} items={items} onBack={() => setSelectedId(null)} />

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{orders.length} order{orders.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowForm(true)} disabled={items.length === 0} title={items.length === 0 ? "Add an item to this client's catalogue first" : undefined}
          style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer", opacity: items.length === 0 ? 0.5 : 1 }}>
          <Plus size={15} /> New order
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : orders.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No outbound orders yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {orders.map((o, i) => {
            const colors = STATUS_COLORS[o.status] ?? { bg: "#F1F5F9", fg: "#64748B" }
            return (
              <button key={o.id} onClick={() => setSelectedId(o.id)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9", background: "none", border: "none", cursor: "pointer", textAlign: "left" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <PackageCheck size={15} color={WHSE_ACCENT} />
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{o.orderReference ?? `Order ${o.id.slice(0, 8)}`}</p>
                      <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{o.status}</span>
                    </div>
                    <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{o.shipToName ?? "No ship-to set"}{o.requestedShipDate ? ` · Requested ${o.requestedShipDate}` : ""}</p>
                  </div>
                </div>
                <ChevronRight size={16} color="#CBD5E1" />
              </button>
            )
          })}
        </div>
      )}

      {showForm && <NewOrderModal clientId={clientId} items={items} onClose={() => setShowForm(false)} />}
    </div>
  )
}
