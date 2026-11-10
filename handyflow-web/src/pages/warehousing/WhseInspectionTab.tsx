// src/pages/warehousing/WhseInspectionTab.tsx
//
// Stock held for inspection at receiving. A person with the QC release permission passes it (it becomes available) or fails it (it is
// written off as damaged), with a reason. The server refuses the person who received the stock unless they are the only one who can release.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { btnPrimary, card, ErrorLine, fmtQty, inputStyle, labelStyle } from "./whseUi"
import { validateInspection } from "./receiving"

interface QueueRow {
  receiptId: string; shipmentId: string; shipmentReference: string | null; clientName: string; sku: string; description: string | null
  locationCode: string; heldPending: number; reason: string | null; receivedAt: string; lotNumber: string | null; expiryDate: string | null
}

function InspectRow({ row, canInspect }: { row: QueueRow; canInspect: boolean }) {
  const qc = useQueryClient()
  const [outcome, setOutcome] = useState<"PASS" | "FAIL">("PASS")
  const [qty, setQty] = useState(String(row.heldPending))
  const [reason, setReason] = useState("")
  const problem = validateInspection(qty, reason, row.heldPending)
  const inspect = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/receiving/receipts/${row.receiptId}/inspect`, { outcome, qty: Number(qty.replace(",", ".")), reason: reason.trim() }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-inspection-queue"] }); qc.invalidateQueries({ queryKey: ["whse-discrepancies", row.shipmentId] }); qc.invalidateQueries({ queryKey: ["whse-receipts", row.shipmentId] }) },
  })
  return (
    <div style={{ ...card, padding: 14, marginBottom: 10 }}>
      <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 2px", color: "var(--hf-text)" }}>{row.sku} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>· {row.clientName}</span></p>
      <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "0 0 10px" }}>
        {fmtQty(row.heldPending)} held at {row.locationCode} · {row.shipmentReference ?? "shipment"}{row.lotNumber ? ` · lot ${row.lotNumber}${row.expiryDate ? ` (exp ${row.expiryDate})` : ""}` : ""}{row.reason ? ` · ${row.reason}` : ""}
      </p>
      {canInspect ? (
        <>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
            <div><label style={labelStyle}>Outcome</label>
              <select aria-label="Outcome" style={inputStyle} value={outcome} onChange={e => setOutcome(e.target.value as "PASS" | "FAIL")}>
                <option value="PASS">Pass: make available</option><option value="FAIL">Fail: write off as damaged</option>
              </select></div>
            <div style={{ width: 90 }}><label style={labelStyle}>Quantity</label><input aria-label="Quantity inspected" inputMode="decimal" style={{ ...inputStyle, width: "100%" }} value={qty} onChange={e => setQty(e.target.value)} /></div>
            <div style={{ flex: 1, minWidth: 180 }}><label style={labelStyle}>Reason *</label><input aria-label="Inspection reason" style={{ ...inputStyle, width: "100%" }} value={reason} onChange={e => setReason(e.target.value)} /></div>
            <button onClick={() => inspect.mutate()} disabled={!!problem || inspect.isPending} style={{ ...btnPrimary, opacity: problem ? 0.5 : 1 }}>{inspect.isPending ? "Saving…" : "Record"}</button>
          </div>
          <ErrorLine error={inspect.error} fallback="Could not record this inspection" />
        </>
      ) : <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>You need the QC release permission to inspect stock.</p>}
    </div>
  )
}

export default function WhseInspectionTab() {
  const canInspect = usePermission("WAREHOUSING_QC_RELEASE")
  const { data: rows = [], isLoading } = useQuery<QueueRow[]>({ queryKey: ["whse-inspection-queue"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/receiving/inspection-queue")).data })
  return (
    <div>
      <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: "0 0 14px" }}>{rows.length} receipt{rows.length === 1 ? "" : "s"} waiting for inspection</p>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : rows.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Nothing is waiting for inspection.</p>
        : rows.map(r => <InspectRow key={r.receiptId} row={r} canInspect={canInspect} />)}
    </div>
  )
}
