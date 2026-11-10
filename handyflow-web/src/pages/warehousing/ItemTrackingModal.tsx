// Switches an item's lot and expiry tracking. A lot-tracked item is received, stored and picked lot by lot; an item that expires also needs an
// expiry date on every receipt and is allocated first-expiry-first-out. The server refuses to switch tracking on or off while stock is on hand.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, ErrorLine, inputStyle, labelStyle, Modal } from "./whseUi"
import { buildTrackingPayload, type TrackingForm, validateTracking } from "./receiving"

export default function ItemTrackingModal({ clientId, item, onClose }: {
  clientId: string; item: { id: string; sku: string; lotTracked?: boolean; expiryTracked?: boolean; minShelfLifeDays?: number | null; serialTracked?: boolean }; onClose: () => void
}) {
  const qc = useQueryClient()
  const [f, setF] = useState<TrackingForm>({ lotTracked: !!item.lotTracked, expiryTracked: !!item.expiryTracked, minShelfLifeDays: item.minShelfLifeDays == null ? "" : String(item.minShelfLifeDays) })
  const [serial, setSerial] = useState(!!item.serialTracked)
  const problem = validateTracking(f)
  const save = useMutation({
    mutationFn: async () => {
      await apiClient.put(`/api/v1/warehousing/items/${item.id}/tracking`, buildTrackingPayload(f))
      if (serial !== !!item.serialTracked) await apiClient.put(`/api/v1/warehousing/items/${item.id}/serial-tracking`, { serialTracked: serial })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-items", clientId] }); onClose() },
  })
  return (
    <Modal title={`Lot tracking for ${item.sku}`} onClose={onClose} width={400}>
      <div style={{ display: "grid", gap: 12 }}>
        <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
          <input type="checkbox" checked={f.lotTracked} onChange={e => setF({ ...f, lotTracked: e.target.checked, expiryTracked: e.target.checked ? f.expiryTracked : false })} /> Track by lot (batch)
        </label>
        <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center", opacity: f.lotTracked ? 1 : 0.5 }}>
          <input type="checkbox" checked={f.expiryTracked} disabled={!f.lotTracked} onChange={e => setF({ ...f, expiryTracked: e.target.checked })} /> Has an expiry date
        </label>
        <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
          <input type="checkbox" checked={serial} onChange={e => setSerial(e.target.checked)} /> Track by serial number (one per unit)
        </label>
        <div><label style={labelStyle} htmlFor="whse-min-shelf">Minimum days of shelf life left when shipped</label>
          <input id="whse-min-shelf" inputMode="numeric" style={{ ...inputStyle, width: 120 }} disabled={!f.expiryTracked} value={f.minShelfLifeDays} onChange={e => setF({ ...f, minShelfLifeDays: e.target.value })} placeholder="none" /></div>
      </div>
      {problem && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "10px 0 0" }}>{problem}</p>}
      <ErrorLine error={save.error} fallback="Could not change tracking" />
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
        <button onClick={() => save.mutate()} disabled={!!problem || save.isPending} style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }}>{save.isPending ? "Saving…" : "Save"}</button>
      </div>
    </Modal>
  )
}
