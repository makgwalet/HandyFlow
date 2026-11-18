// src/pages/supply-chain/InspectionRecorder.tsx — start an inspection of a posted goods receipt, record pass/fail per line, complete it.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inp, TH, TD, Modal, ModalFooter, ErrBox, Spinner, Banner, fmtDate } from "./scm.shared"
import { asList, asOne, errorText, inspectionProblem, inspectionBody, inspectionOutcome, type InspectLineForm } from "./scmOps"

export interface InspectionDto { id: string; inspectionNumber: string; status: string; goodsReceiptId: string; lines: { id: string; grLineId: string; itemName: string; qtyAcceptedOnReceipt: number; qtyPassed: number; qtyFailed: number; defectCode: string | null; notes: string | null }[] }
interface Receipt { id: string; receiptNumber: string; status: string; receivedDate: string }
const BASE = "/api/v1/supply-chain"

/** Start: choose a posted receipt. Record: enter results for an open inspection. */
export function StartInspection({ onClose, onStarted }: { onClose: () => void; onStarted: (i: InspectionDto) => void }) {
  const [receiptId, setReceiptId] = useState(""); const [err, setErr] = useState("")
  const receipts = useQuery({ queryKey: ["scm-receipts-posted"], queryFn: () => apiClient.get(`${BASE}/goods-receipts`, { params: { size: 50 } }).then(asList<Receipt>) })
  const start = useMutation({ mutationFn: () => apiClient.post(`${BASE}/quality-inspections`, { goodsReceiptId: receiptId }).then(r => asOne<InspectionDto>(r)), onSuccess: i => { if (i) onStarted(i) }, onError: e => setErr(errorText(e)) })
  const posted = (receipts.data ?? []).filter(r => r.status === "POSTED")
  return (
    <Modal title="Start an inspection" onClose={onClose}>
      {receipts.isLoading ? <Spinner /> : posted.length === 0 ? <Banner variant="info">No posted goods receipts to inspect.</Banner> : (
        <label style={{ fontSize: 12, fontWeight: 600 }}>Goods receipt
          <select style={{ ...inp, marginTop: 5 }} aria-label="Goods receipt" value={receiptId} onChange={e => setReceiptId(e.target.value)}>
            <option value="">Choose…</option>{posted.map(r => <option key={r.id} value={r.id}>{r.receiptNumber} · {fmtDate(r.receivedDate)}</option>)}</select></label>)}
      {err && <ErrBox msg={err} />}
      <ModalFooter onCancel={onClose} label="Start inspection" loading={start.isPending} onConfirm={() => { if (!receiptId) { setErr("Choose the receipt to inspect"); return } setErr(""); start.mutate() }} />
    </Modal>
  )
}

export function RecordInspection({ inspection, onClose }: { inspection: InspectionDto; onClose: () => void }) {
  const qc = useQueryClient()
  const [lines, setLines] = useState<InspectLineForm[]>(() => inspection.lines.map(l => ({ lineId: l.id, itemName: l.itemName, accepted: l.qtyAcceptedOnReceipt, passed: l.qtyPassed ? String(l.qtyPassed) : "", failed: l.qtyFailed ? String(l.qtyFailed) : "", defectCode: l.defectCode ?? "", notes: l.notes ?? "" })))
  const [notes, setNotes] = useState(""); const [raise, setRaise] = useState(true); const [err, setErr] = useState("")
  const done = () => { qc.invalidateQueries({ queryKey: ["scm-inspections"] }); qc.invalidateQueries({ queryKey: ["scm-quarantine"] }); qc.invalidateQueries({ queryKey: ["scm-returns"] }); onClose() }
  const save = useMutation({ mutationFn: () => apiClient.put(`${BASE}/quality-inspections/${inspection.id}/results`, inspectionBody(lines, notes)), onError: e => setErr(errorText(e)) })
  const complete = useMutation({
    mutationFn: async () => { await apiClient.put(`${BASE}/quality-inspections/${inspection.id}/results`, inspectionBody(lines, notes)); return apiClient.post(`${BASE}/quality-inspections/${inspection.id}/complete`, { raiseReturnForFailures: raise }) },
    onSuccess: done, onError: e => setErr(errorText(e)),
  })
  const out = inspectionOutcome(lines)
  const check = () => { const p = inspectionProblem(lines); if (p) { setErr(p); return false } setErr(""); return true }
  const set = (i: number, patch: Partial<InspectLineForm>) => setLines(ls => ls.map((l, j) => j === i ? { ...l, ...patch } : l))
  return (
    <Modal title={`Inspection ${inspection.inspectionNumber}`} wide onClose={onClose}>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr><th style={TH}>Item</th><th style={TH}>Received</th><th style={TH}>Passed</th><th style={TH}>Failed</th><th style={TH}>Defect</th></tr></thead>
        <tbody>{lines.map((l, i) => <tr key={l.lineId} style={{ borderTop: "1px solid var(--hf-border)" }}>
          <td style={TD}><strong>{l.itemName}</strong></td><td style={TD}>{l.accepted}</td>
          <td style={TD}><input style={{ ...inp, width: 80 }} aria-label={`Passed ${l.itemName}`} inputMode="decimal" value={l.passed} onChange={e => set(i, { passed: e.target.value })} /></td>
          <td style={TD}><input style={{ ...inp, width: 80 }} aria-label={`Failed ${l.itemName}`} inputMode="decimal" value={l.failed} onChange={e => set(i, { failed: e.target.value })} /></td>
          <td style={TD}><input style={inp} aria-label={`Defect ${l.itemName}`} placeholder="e.g. cracked" value={l.defectCode} onChange={e => set(i, { defectCode: e.target.value })} /></td></tr>)}</tbody>
      </table>
      <div style={{ fontSize: 13, margin: "10px 0" }}>{out.passed + out.failed > 0 ? out.text : "Enter the quantities that passed and failed."}</div>
      <textarea style={inp} rows={2} placeholder="Notes (optional)" aria-label="Inspection notes" value={notes} onChange={e => setNotes(e.target.value)} />
      {out.failed > 0 && <label style={{ display: "block", fontSize: 13, marginTop: 10 }}><input type="checkbox" checked={raise} onChange={e => setRaise(e.target.checked)} /> Raise a supplier return for the failed units (they are held in quarantine either way)</label>}
      {err && <ErrBox msg={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
        <button onClick={onClose} style={{ padding: "8px 16px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", cursor: "pointer" }}>Close</button>
        <button disabled={save.isPending} onClick={() => { if (check()) save.mutate(undefined, { onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: ["scm-inspections"] }) } }) }} style={{ padding: "8px 16px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", cursor: "pointer" }}>Save results</button>
        <button disabled={complete.isPending} onClick={() => { if (check()) complete.mutate() }} style={{ padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontWeight: 600, cursor: "pointer" }}>Complete inspection</button>
      </div>
    </Modal>
  )
}
