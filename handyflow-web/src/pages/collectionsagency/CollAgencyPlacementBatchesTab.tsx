// src/pages/collectionsagency/CollAgencyPlacementBatchesTab.tsx
//
// PlacementBatchResponse's exact field list wasn't directly visible in
// the controller excerpt read (toResponse() body was truncated in the
// source snippet returned), so it's inferred here from
// CollAgencyPlacementBatch's own getters — every other *Response record
// in this module mirrors its entity's getters 1:1 in declared order
// (confirmed for Client/Profile/Collector/DebtorAccount/ContactLog/
// PaymentPlan/TrustTransaction/CommissionInvoice, no exceptions found),
// so this is a high-confidence inference, not a guess from nothing. If
// your build fails on a field name mismatch here, check
// CollAgencyPlacementController.toResponse() directly — it's the one
// DTO shape in this module not read from source verbatim.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, Trash2, CheckCircle2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"

interface PlacementBatchResponse {
  id: string; clientId: string; batchReference: string | null; placedDate: string
  totalAccounts: number; totalPlacedValue: number; acknowledgedAt: string | null; acknowledgedBy: string | null
  notes: string | null; createdAt: string
}
interface DebtorLine {
  accountReference: string; debtorName: string; debtorIdNumber: string; debtorEmail: string; debtorPhone: string
  debtorAddress: string; originalCreditorName: string; originalDebtDate: string; originalDebtAmount: string
}

const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)
const inputStyle: React.CSSProperties = { width: "100%", padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12.5, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 3, display: "block" }

const emptyLine = (): DebtorLine => ({ accountReference: "", debtorName: "", debtorIdNumber: "", debtorEmail: "", debtorPhone: "", debtorAddress: "", originalCreditorName: "", originalDebtDate: "", originalDebtAmount: "" })

function NewBatchModal({ clientId, onClose }: { clientId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [batchReference, setBatchReference] = useState("")
  const [placedDate, setPlacedDate] = useState(new Date().toISOString().slice(0, 10))
  const [notes, setNotes] = useState("")
  const [lines, setLines] = useState<DebtorLine[]>([emptyLine()])

  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/collections-agency/clients/${clientId}/placement-batches`, {
      batchReference: batchReference || null, placedDate,
      lines: lines.map(l => ({ ...l, originalDebtDate: l.originalDebtDate || null, originalDebtAmount: parseFloat(l.originalDebtAmount) || 0, originalCreditorName: l.originalCreditorName || null })),
      notes: notes || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-batches", clientId] }); onClose() },
  })

  const valid = lines.length > 0 && lines.every(l => l.debtorName && l.originalDebtAmount)

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 26, width: 720, maxHeight: "88vh", overflowY: "auto" }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Place a new batch of debtor accounts</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 16 }}>
          <div><label style={labelStyle}>Batch reference (client's own, optional)</label><input style={inputStyle} value={batchReference} onChange={e => setBatchReference(e.target.value)} /></div>
          <div><label style={labelStyle}>Placed date</label><input type="date" style={inputStyle} value={placedDate} onChange={e => setPlacedDate(e.target.value)} /></div>
        </div>

        <p style={{ fontSize: 12, fontWeight: 700, color: "#0F172A", margin: "0 0 8px" }}>Debtor accounts in this batch ({lines.length})</p>
        <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 10px" }}>Leave "original creditor" blank to default to this client's own trading name — the usual case when the client IS the original creditor.</p>

        {lines.map((line, idx) => (
          <div key={idx} style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: 12, marginBottom: 10, position: "relative" }}>
            {lines.length > 1 && (
              <button onClick={() => setLines(lines.filter((_, i) => i !== idx))} style={{ position: "absolute", top: 8, right: 8, background: "none", border: "none", cursor: "pointer" }}>
                <Trash2 size={13} color="#DC2626" />
              </button>
            )}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
              <div><label style={labelStyle}>Debtor name *</label><input style={inputStyle} value={line.debtorName} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, debtorName: e.target.value } : l))} /></div>
              <div><label style={labelStyle}>Account reference</label><input style={inputStyle} value={line.accountReference} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, accountReference: e.target.value } : l))} /></div>
              <div><label style={labelStyle}>ID number</label><input style={inputStyle} value={line.debtorIdNumber} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, debtorIdNumber: e.target.value } : l))} /></div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 8 }}>
              <div><label style={labelStyle}>Email</label><input style={inputStyle} value={line.debtorEmail} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, debtorEmail: e.target.value } : l))} /></div>
              <div><label style={labelStyle}>Phone</label><input style={inputStyle} value={line.debtorPhone} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, debtorPhone: e.target.value } : l))} /></div>
            </div>
            <div><label style={labelStyle}>Address</label><input style={inputStyle} value={line.debtorAddress} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, debtorAddress: e.target.value } : l))} /></div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginTop: 8 }}>
              <div><label style={labelStyle}>Original creditor (optional)</label><input style={inputStyle} value={line.originalCreditorName} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, originalCreditorName: e.target.value } : l))} /></div>
              <div><label style={labelStyle}>Original debt date</label><input type="date" style={inputStyle} value={line.originalDebtDate} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, originalDebtDate: e.target.value } : l))} /></div>
              <div><label style={labelStyle}>Original amount *</label><input type="number" step="0.01" style={inputStyle} value={line.originalDebtAmount} onChange={e => setLines(lines.map((l, i) => i === idx ? { ...l, originalDebtAmount: e.target.value } : l))} /></div>
            </div>
          </div>
        ))}

        <button onClick={() => setLines([...lines, emptyLine()])} style={{ display: "flex", alignItems: "center", gap: 5, background: "none", border: "1px dashed #CBD5E1", borderRadius: 8, padding: "8px 14px", fontSize: 12, fontWeight: 600, color: "#64748B", cursor: "pointer", marginBottom: 16 }}>
          <Plus size={13} /> Add another debtor
        </button>

        <div><label style={labelStyle}>Batch notes</label><textarea style={{ ...inputStyle, minHeight: 44 }} value={notes} onChange={e => setNotes(e.target.value)} /></div>

        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not place this batch"}</p>}

        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={!valid || save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: valid ? CA_ACCENT : "#CBD5E1", color: "#fff", fontSize: 13, fontWeight: 600, cursor: valid ? "pointer" : "not-allowed" }}>
            {save.isPending ? "Placing…" : `Place batch (${lines.length} account${lines.length === 1 ? "" : "s"})`}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function CollAgencyPlacementBatchesTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const [showNew, setShowNew] = useState(false)

  const { data: batches = [], isLoading } = useQuery<PlacementBatchResponse[]>({
    queryKey: ["ca-batches", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/clients/${clientId}/placement-batches`)).data,
  })
  const acknowledge = useMutation({
    mutationFn: async (batchId: string) => apiClient.post(`/api/v1/collections-agency/clients/${clientId}/placement-batches/${batchId}/acknowledge`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-batches", clientId] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{batches.length} batch{batches.length === 1 ? "" : "es"} placed</p>
        <button onClick={() => setShowNew(true)} style={{ display: "flex", alignItems: "center", gap: 6, background: CA_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "8px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Place new batch
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : batches.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No batches placed yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {batches.map((b, i) => (
            <div key={b.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div>
                <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: "0 0 2px" }}>
                  {b.batchReference || `Batch placed ${b.placedDate}`} — {b.totalAccounts} account{b.totalAccounts === 1 ? "" : "s"}
                </p>
                <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>Placed {b.placedDate} · Total value {fmtMoney(b.totalPlacedValue)}</p>
              </div>
              {b.acknowledgedAt ? (
                <span style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 11.5, fontWeight: 700, color: "#166534" }}><CheckCircle2 size={14} /> Acknowledged</span>
              ) : (
                <button onClick={() => acknowledge.mutate(b.id)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "6px 12px", fontSize: 12, fontWeight: 600, color: CA_ACCENT, cursor: "pointer" }}>
                  Acknowledge receipt
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {showNew && <NewBatchModal clientId={clientId} onClose={() => setShowNew(false)} />}
    </div>
  )
}
