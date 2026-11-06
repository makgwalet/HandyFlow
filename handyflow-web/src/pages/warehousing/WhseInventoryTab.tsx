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
import { WHSE_ACCENT, WHSE_ACCENT_TEXT } from "./constants"

interface InventoryResponse { id: string; clientId: string; itemId: string; locationId: string; qtyOnHand: number; qtyAllocated: number; available: number; status?: string }
interface ItemResponse { id: string; sku: string }
interface LocationResponse { id: string; code: string }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }

function AdjustModal({ inv, itemLabel, onClose }: { inv: InventoryResponse; itemLabel: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [delta, setDelta] = useState("")
  const [reason, setReason] = useState("")

  // When the warehouse has set an approval threshold, a larger adjustment is requested rather than applied.
  const { data: rule } = useQuery<{ thresholdQty: number | null }>({
    queryKey: ["whse-adjust-threshold"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/adjustment-approval-threshold")).data,
  })
  const amount = parseFloat(delta)
  const needsApproval = rule?.thresholdQty != null && !Number.isNaN(amount) && Math.abs(amount) > rule.thresholdQty
  const [sent, setSent] = useState(false)

  const adjust = useMutation({
    mutationFn: async () => needsApproval
      ? apiClient.post(`/api/v1/warehousing/inventory/${inv.id}/adjustment-requests`, { delta: amount, reason })
      : apiClient.post(`/api/v1/warehousing/inventory/${inv.id}/adjust`, { delta: amount, reason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["whse-inventory", inv.clientId] })
      qc.invalidateQueries({ queryKey: ["whse-adjustment-requests"] })
      if (needsApproval) setSent(true); else onClose()
    },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 400 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Adjust stock</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} style={{ color: 'var(--hf-text-faint)' }} /></button>
        </div>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginBottom: 14 }}>{itemLabel} — currently {inv.qtyOnHand} on hand</p>
        <div style={{ display: "grid", gap: 12 }}>
          <div>
            <label style={labelStyle}>Adjustment (+/-) *</label>
            <input type="number" step="0.01" style={inputStyle} value={delta} onChange={e => setDelta(e.target.value)} placeholder="e.g. -5 for a damage write-off" />
          </div>
          <div><label style={labelStyle}>Reason *</label><textarea style={{ ...inputStyle, minHeight: 60, resize: "vertical" }} value={reason} onChange={e => setReason(e.target.value)} placeholder="Count correction, damage write-off, etc." /></div>
        </div>
        {needsApproval && !sent && <p style={{ fontSize: 12, color: "var(--hf-warning-text)", marginTop: 12 }}>Adjustments of more than {rule?.thresholdQty} units need a second person. This will be sent for approval and the stock will not change until it is approved.</p>}
        {sent && <p style={{ fontSize: 12.5, color: "var(--hf-success-text-strong)", marginTop: 12 }}>Sent for approval. The stock will change once another person approves it.</p>}
        {adjust.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 12 }}>{(adjust.error as any)?.response?.data?.message ?? "Could not adjust this stock position"}</p>}
        <button onClick={() => (sent ? onClose() : adjust.mutate())} disabled={!sent && (!delta || !reason || adjust.isPending)}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!sent && (!delta || !reason || adjust.isPending)) ? 0.6 : 1 }}>
          {sent ? "Close" : adjust.isPending ? "Sending…" : needsApproval ? "Send for approval" : "Record adjustment"}
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
      <p style={{ fontSize: 13, color: "var(--hf-text-faint)", marginBottom: 16 }}>{inventory.length} stock position{inventory.length === 1 ? "" : "s"} across all locations</p>

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
      ) : inventory.length === 0 ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No stock on hand for this client yet — receive an inbound shipment to create a position.</p>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr 0.8fr 0.8fr 0.8fr 0.6fr", padding: "9px 16px", background: "var(--hf-surface-muted)", fontSize: 10.5, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>
            <span>Item</span><span>Location</span><span>On hand</span><span>Allocated</span><span>Available</span><span></span>
          </div>
          {inventory.map((inv, i) => (
            <div key={inv.id} style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr 0.8fr 0.8fr 0.8fr 0.6fr", alignItems: "center", padding: "11px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Boxes size={14} style={{ color: WHSE_ACCENT_TEXT }} />
                <span style={{ fontSize: 12.5, fontWeight: 600, color: "var(--hf-text)" }}>{skuOf(inv.itemId)}</span>
              </div>
              <span style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>{codeOf(inv.locationId)}</span>
              <span style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600 }}>{inv.qtyOnHand}</span>
              <span style={{ fontSize: 12.5, color: "var(--hf-text-faint)" }}>{inv.qtyAllocated}</span>
              <span style={{ fontSize: 12.5, color: inv.available > 0 ? "var(--hf-success-text)" : "var(--hf-danger-text)", fontWeight: 600 }}>{inv.available}{inv.status && inv.status !== "AVAILABLE" && <span style={{ marginLeft: 6, fontSize: 10.5, fontWeight: 700, color: "var(--hf-danger-text)" }}>{inv.status}</span>}</span>
              <button onClick={() => setAdjusting(inv)} title="Adjust"
                style={{ display: "flex", alignItems: "center", justifyContent: "center", background: "none", border: "1px solid var(--hf-border)", borderRadius: 6, padding: 5, cursor: "pointer", width: "fit-content" }}>
                <SlidersHorizontal size={12} style={{ color: 'var(--hf-text-muted)' }} />
              </button>
            </div>
          ))}
        </div>
      )}

      {adjusting && <AdjustModal inv={adjusting} itemLabel={`${skuOf(adjusting.itemId)} @ ${codeOf(adjusting.locationId)}`} onClose={() => setAdjusting(null)} />}
    </div>
  )
}
