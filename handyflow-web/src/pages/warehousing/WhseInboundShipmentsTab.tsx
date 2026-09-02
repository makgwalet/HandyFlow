// src/pages/warehousing/WhseInboundShipmentsTab.tsx
//
// List + create for a client's inbound shipments (ASNs). Create posts
// dynamic item/expectedQty/notes line rows — same add/remove-row pattern
// as CollAgencyPlacementBatchesTab's NewBatchModal. Drills into
// WhseInboundShipmentDetail for the per-line receiving workflow.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Truck, X, ChevronRight, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"
import WhseInboundShipmentDetail from "./WhseInboundShipmentDetail"

export interface InboundShipmentResponse {
  id: string; clientId: string; referenceNumber: string | null; expectedDate: string | null
  receivedDate: string | null; status: "EXPECTED" | "PARTIALLY_RECEIVED" | "RECEIVED" | "CANCELLED"; notes: string | null
}
interface ItemResponse { id: string; sku: string; description: string | null }
interface ShipmentPage { content: InboundShipmentResponse[] }

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  EXPECTED: { bg: "#F1F5F9", fg: "#475569" }, PARTIALLY_RECEIVED: { bg: "#FEF3C7", fg: "#92400E" },
  RECEIVED: { bg: "#DCFCE7", fg: "#166534" }, CANCELLED: { bg: "#F1F5F9", fg: "#94A3B8" },
}
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function NewShipmentModal({ clientId, items, onClose }: { clientId: string; items: ItemResponse[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [referenceNumber, setReferenceNumber] = useState("")
  const [expectedDate, setExpectedDate] = useState("")
  const [notes, setNotes] = useState("")
  const [lines, setLines] = useState([{ itemId: "", expectedQty: "", notes: "" }])

  const addLine = () => setLines([...lines, { itemId: "", expectedQty: "", notes: "" }])
  const removeLine = (i: number) => setLines(lines.filter((_, idx) => idx !== i))
  const updateLine = (i: number, field: string, value: string) => setLines(lines.map((l, idx) => idx === i ? { ...l, [field]: value } : l))

  const create = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/clients/${clientId}/inbound-shipments`, {
      referenceNumber: referenceNumber || null, expectedDate: expectedDate || null, notes: notes || null,
      lines: lines.filter(l => l.itemId && l.expectedQty).map(l => ({ itemId: l.itemId, expectedQty: parseFloat(l.expectedQty), notes: l.notes || null })),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-inbound-shipments", clientId] }); onClose() },
  })

  const valid = lines.some(l => l.itemId && l.expectedQty)

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "85vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>New inbound shipment</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 14 }}>
          <div><label style={labelStyle}>Reference / ASN number</label><input style={inputStyle} value={referenceNumber} onChange={e => setReferenceNumber(e.target.value)} /></div>
          <div><label style={labelStyle}>Expected date</label><input type="date" style={inputStyle} value={expectedDate} onChange={e => setExpectedDate(e.target.value)} /></div>
        </div>
        <div><label style={labelStyle}>Notes</label><textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={notes} onChange={e => setNotes(e.target.value)} /></div>

        <p style={{ fontSize: 12.5, fontWeight: 700, color: "#0F172A", margin: "16px 0 8px" }}>Expected lines</p>
        {lines.map((line, i) => (
          <div key={i} style={{ display: "flex", gap: 8, marginBottom: 8, alignItems: "center" }}>
            <select style={{ ...inputStyle, flex: 2 }} value={line.itemId} onChange={e => updateLine(i, "itemId", e.target.value)}>
              <option value="">Select item…</option>
              {items.map(it => <option key={it.id} value={it.id}>{it.sku}{it.description ? ` — ${it.description}` : ""}</option>)}
            </select>
            <input type="number" step="0.01" placeholder="Qty" style={{ ...inputStyle, flex: 1 }} value={line.expectedQty} onChange={e => updateLine(i, "expectedQty", e.target.value)} />
            {lines.length > 1 && (
              <button onClick={() => removeLine(i)} style={{ background: "none", border: "none", cursor: "pointer", padding: 4 }}><Trash2 size={14} color="#DC2626" /></button>
            )}
          </div>
        ))}
        <button onClick={addLine} style={{ background: "none", border: "1px dashed #CBD5E1", borderRadius: 8, padding: "7px 12px", fontSize: 12, fontWeight: 600, color: "#64748B", cursor: "pointer", marginTop: 4 }}>
          + Add line
        </button>

        {create.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(create.error as any)?.response?.data?.message ?? "Could not create this shipment"}</p>}

        <button onClick={() => create.mutate()} disabled={!valid || create.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!valid || create.isPending) ? 0.6 : 1 }}>
          {create.isPending ? "Creating…" : "Create shipment"}
        </button>
      </div>
    </div>
  )
}

export default function WhseInboundShipmentsTab({ clientId }: { clientId: string }) {
  const [showForm, setShowForm] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data, isLoading } = useQuery<ShipmentPage | InboundShipmentResponse[]>({
    queryKey: ["whse-inbound-shipments", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/inbound-shipments?size=50`)).data,
  })
  const shipments = Array.isArray(data) ? data : data?.content ?? []

  const { data: items = [] } = useQuery<ItemResponse[]>({
    queryKey: ["whse-items", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data,
  })

  if (selectedId) return <WhseInboundShipmentDetail shipmentId={selectedId} clientId={clientId} items={items} onBack={() => setSelectedId(null)} />

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{shipments.length} shipment{shipments.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowForm(true)} disabled={items.length === 0} title={items.length === 0 ? "Add an item to this client's catalogue first" : undefined}
          style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer", opacity: items.length === 0 ? 0.5 : 1 }}>
          <Plus size={15} /> New shipment
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : shipments.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No inbound shipments yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {shipments.map((s, i) => {
            const colors = STATUS_COLORS[s.status] ?? { bg: "#F1F5F9", fg: "#64748B" }
            return (
              <button key={s.id} onClick={() => setSelectedId(s.id)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9", background: "none", border: "none", cursor: "pointer", textAlign: "left" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <Truck size={15} color={WHSE_ACCENT} />
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{s.referenceNumber ?? `Shipment ${s.id.slice(0, 8)}`}</p>
                      <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{s.status.replace(/_/g, " ")}</span>
                    </div>
                    <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{s.expectedDate ? `Expected ${s.expectedDate}` : "No expected date set"}</p>
                  </div>
                </div>
                <ChevronRight size={16} color="#CBD5E1" />
              </button>
            )
          })}
        </div>
      )}

      {showForm && <NewShipmentModal clientId={clientId} items={items} onClose={() => setShowForm(false)} />}
    </div>
  )
}
