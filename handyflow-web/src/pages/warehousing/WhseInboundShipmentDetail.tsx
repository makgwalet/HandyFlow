// src/pages/warehousing/WhseInboundShipmentDetail.tsx
//
// Per-line receiving workflow. ReceiveLineRequest(qty, locationId) is
// directly confirmed via source. InboundShipmentLineResponse's exact
// field list was NOT directly read (the source snippet truncated before
// its toLineResponse() mapper) — inferred by this codebase's
// 100%-consistent "response mirrors entity getters" convention, same
// flagged-inference pattern as CollAgencyPlacementBatchesTab's
// PlacementBatchResponse in the previous module build. Cumulative
// multi-pass receiving means qtyReceived can be topped up across several
// receive calls before the line (and eventually the header) rolls to
// RECEIVED — confirmed via WhseInboundShipment's own header Javadoc.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, PackageCheck, XCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface InboundShipmentResponse {
  id: string; referenceNumber: string | null; expectedDate: string | null; receivedDate: string | null
  status: string; notes: string | null
}
interface InboundShipmentLineResponse {
  id: string; shipmentId: string; itemId: string; expectedQty: number; qtyReceived: number; notes: string | null
}
interface LocationResponse { id: string; code: string; active: boolean }
interface ItemLite { id: string; sku: string }

const inputStyle: React.CSSProperties = { padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12.5, boxSizing: "border-box", fontFamily: "inherit" }

export default function WhseInboundShipmentDetail({ shipmentId, clientId, items, onBack }: {
  shipmentId: string; clientId: string; items: ItemLite[]; onBack: () => void
}) {
  const qc = useQueryClient()
  const [receiveState, setReceiveState] = useState<Record<string, { qty: string; locationId: string }>>({})

  const { data: shipment } = useQuery<InboundShipmentResponse>({
    queryKey: ["whse-inbound-shipment", shipmentId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/inbound-shipments/${shipmentId}`)).data,
  })
  const { data: lines = [], isLoading } = useQuery<InboundShipmentLineResponse[]>({
    queryKey: ["whse-inbound-shipment-lines", shipmentId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/inbound-shipments/${shipmentId}/lines`)).data,
  })
  const { data: locations = [] } = useQuery<LocationResponse[]>({
    queryKey: ["whse-locations"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data,
  })

  const receive = useMutation({
    mutationFn: async ({ lineId, qty, locationId }: { lineId: string; qty: number; locationId: string }) =>
      apiClient.post(`/api/v1/warehousing/inbound-shipments/${shipmentId}/lines/${lineId}/receive`, { qty, locationId }),
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: ["whse-inbound-shipment-lines", shipmentId] })
      qc.invalidateQueries({ queryKey: ["whse-inbound-shipment", shipmentId] })
      qc.invalidateQueries({ queryKey: ["whse-inventory", clientId] })
      setReceiveState(s => ({ ...s, [vars.lineId]: { qty: "", locationId: "" } }))
    },
  })
  const cancelShipment = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/inbound-shipments/${shipmentId}/cancel`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-inbound-shipment", shipmentId] }); qc.invalidateQueries({ queryKey: ["whse-inbound-shipments", clientId] }) },
  })

  const skuOf = (itemId: string) => items.find(i => i.id === itemId)?.sku ?? itemId.slice(0, 8)
  const activeLocations = locations.filter(l => l.active)
  const terminal = shipment?.status === "RECEIVED" || shipment?.status === "CANCELLED"

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 14, padding: 0 }}>
        <ArrowLeft size={15} /> All shipments
      </button>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
        <div>
          <h3 style={{ fontSize: 16, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>{shipment?.referenceNumber ?? "Shipment"}</h3>
          <p style={{ fontSize: 12.5, color: "#94A3B8", margin: 0 }}>Status: {shipment?.status.replace(/_/g, " ")} {shipment?.expectedDate ? `· Expected ${shipment.expectedDate}` : ""}</p>
        </div>
        {!terminal && (
          <button onClick={() => { if (confirm("Cancel this shipment?")) cancelShipment.mutate() }}
            style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid #FECACA", color: "#DC2626", borderRadius: 8, padding: "8px 14px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}>
            <XCircle size={14} /> Cancel shipment
          </button>
        )}
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {lines.map((line, i) => {
            const state = receiveState[line.id] ?? { qty: "", locationId: "" }
            const outstanding = line.expectedQty - line.qtyReceived
            return (
              <div key={line.id} style={{ padding: "14px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: outstanding > 0 && !terminal ? 10 : 0 }}>
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{skuOf(line.itemId)}</p>
                    <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{line.qtyReceived} / {line.expectedQty} received{line.notes ? ` · ${line.notes}` : ""}</p>
                  </div>
                  {outstanding <= 0 && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: "#DCFCE7", color: "#166534" }}>FULLY RECEIVED</span>}
                </div>
                {outstanding > 0 && !terminal && (
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <input type="number" step="0.01" placeholder="Qty" style={{ ...inputStyle, width: 90 }}
                      value={state.qty} onChange={e => setReceiveState(s => ({ ...s, [line.id]: { ...state, qty: e.target.value } }))} />
                    <select style={{ ...inputStyle, flex: 1 }} value={state.locationId}
                      onChange={e => setReceiveState(s => ({ ...s, [line.id]: { ...state, locationId: e.target.value } }))}>
                      <option value="">Put-away location…</option>
                      {activeLocations.map(l => <option key={l.id} value={l.id}>{l.code}</option>)}
                    </select>
                    <button
                      onClick={() => receive.mutate({ lineId: line.id, qty: parseFloat(state.qty), locationId: state.locationId })}
                      disabled={!state.qty || !state.locationId || receive.isPending}
                      style={{ display: "flex", alignItems: "center", gap: 5, background: WHSE_ACCENT, color: "#fff", border: "none", borderRadius: 7, padding: "7px 12px", fontSize: 12, fontWeight: 600, cursor: "pointer", opacity: (!state.qty || !state.locationId) ? 0.5 : 1, whiteSpace: "nowrap" }}>
                      <PackageCheck size={13} /> Receive
                    </button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
