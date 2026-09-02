// src/pages/warehousing/WhseInventoryTab.tsx
//
// Live stock positions — GET /clients/{clientId}/inventory (confirmed via
// WhseInventoryController). Item SKU/location code are joined client-side
// from the Items tab's own list + the operator's locations list (same
// itemsById-map-then-lookup pattern already used server-side by
// WhseOutboundOrderController's packing-slip export).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Boxes, SlidersHorizontal, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface InventoryResponse { id: string; clientId: string; itemId: string; locationId: string; qtyOnHand: number; qtyAllocated: number; available: number }
interface ItemResponse { id: string; sku: string }
interface LocationResponse { id: string; code: string }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function AdjustModal({ inv, itemLabel, onClose }: { inv: InventoryResponse; itemLabel: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [delta, setDelta] = useState("")
  const [reason, setReason] = useState("")

  const adjust = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/inventory/${inv.id}/adjust`, { delta: parseFloat(delta), reason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-inventory", inv.clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 400 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Adjust stock</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12.5, color: "#64748B", marginBottom: 14 }}>{itemLabel} — currently {inv.qtyOnHand} on hand</p>
        <div style={{ display: "grid", gap: 12 }}>
          <div>
            <label style={labelStyle}>Adjustment (+/-) *</label>
            <input type="number" step="0.01" style={inputStyle} value={delta} onChange={e => setDelta(e.target.value)} placeholder="e.g. -5 for a damage write-off" />
          </div>
          <div><label style={labelStyle}>Reason *</label><textarea style={{ ...inputStyle, minHeight: 60, resize: "vertical" }} value={reason} onChange={e => setReason(e.target.value)} placeholder="Count correction, damage write-off, etc." /></div>
        </div>
        {adjust.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(adjust.error as any)?.response?.data?.message ?? "Could not adjust this stock position"}</p>}
        <button onClick={() => adjust.mutate()} disabled={!delta || !reason || adjust.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!delta || !reason || adjust.isPending) ? 0.6 : 1 }}>
          {adjust.isPending ? "Adjusting…" : "Record adjustment"}
        </button>
      </div>
    </div>
  )
}

export default function WhseInventoryTab({ clientId }: { clientId: string }) {
  const [adjusting, setAdjusting] = useState<InventoryResponse | null>(null)

  const { data: inventory = [], isLoading } = useQuery<InventoryResponse[]>({
    queryKey: ["whse-inventory", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/inventory`)).data,
  })
  const { data: items = [] } = useQuery<ItemResponse[]>({
    queryKey: ["whse-items", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data,
  })
  const { data: locations = [] } = useQuery<LocationResponse[]>({
    queryKey: ["whse-locations"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data,
  })

  const skuOf = (itemId: string) => items.find(i => i.id === itemId)?.sku ?? itemId.slice(0, 8)
  const codeOf = (locationId: string) => locations.find(l => l.id === locationId)?.code ?? locationId.slice(0, 8)

  return (
    <div>
      <p style={{ fontSize: 13, color: "#94A3B8", marginBottom: 16 }}>{inventory.length} stock position{inventory.length === 1 ? "" : "s"} across all locations</p>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : inventory.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No stock on hand for this client yet — receive an inbound shipment to create a position.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr 0.8fr 0.8fr 0.8fr 0.6fr", padding: "9px 16px", background: "#F8FAFC", fontSize: 10.5, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" }}>
            <span>Item</span><span>Location</span><span>On hand</span><span>Allocated</span><span>Available</span><span></span>
          </div>
          {inventory.map((inv, i) => (
            <div key={inv.id} style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr 0.8fr 0.8fr 0.8fr 0.6fr", alignItems: "center", padding: "11px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Boxes size={14} color={WHSE_ACCENT} />
                <span style={{ fontSize: 12.5, fontWeight: 600, color: "#0F172A" }}>{skuOf(inv.itemId)}</span>
              </div>
              <span style={{ fontSize: 12.5, color: "#64748B" }}>{codeOf(inv.locationId)}</span>
              <span style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600 }}>{inv.qtyOnHand}</span>
              <span style={{ fontSize: 12.5, color: "#94A3B8" }}>{inv.qtyAllocated}</span>
              <span style={{ fontSize: 12.5, color: inv.available > 0 ? "#059669" : "#DC2626", fontWeight: 600 }}>{inv.available}</span>
              <button onClick={() => setAdjusting(inv)} title="Adjust"
                style={{ display: "flex", alignItems: "center", justifyContent: "center", background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer", width: "fit-content" }}>
                <SlidersHorizontal size={12} color="#64748B" />
              </button>
            </div>
          ))}
        </div>
      )}

      {adjusting && <AdjustModal inv={adjusting} itemLabel={`${skuOf(adjusting.itemId)} @ ${codeOf(adjusting.locationId)}`} onClose={() => setAdjusting(null)} />}
    </div>
  )
}
