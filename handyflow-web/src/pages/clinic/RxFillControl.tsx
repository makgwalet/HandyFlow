// src/pages/clinic/RxFillControl.tsx
// Fills of a prescription: the original supply plus each authorised repeat. Recording a fill is logged on the
// server with who did it; the server refuses once every authorised fill is used.
import { useState } from "react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

export interface RxFillInfo { id: string; repeats: number; dispensed: boolean; fillsUsed?: number; fillsRemaining?: number }

export function fillSummary(rx: RxFillInfo): { used: number; total: number; remaining: number } {
  const total = 1 + Math.max(0, rx.repeats ?? 0)
  const used = rx.fillsUsed ?? (rx.dispensed ? total : 0)
  const remaining = rx.fillsRemaining ?? Math.max(0, total - used)
  return { used, total, remaining }
}

export default function RxFillControl({ rx, onRecorded }: { rx: RxFillInfo; onRecorded: () => void }) {
  const canFill = usePermission("CLINIC_PRESCRIPTION_WRITE")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const { used, total, remaining } = fillSummary(rx)

  const record = async () => {
    setBusy(true); setError("")
    try { await apiClient.post(`/api/v1/clinic/prescriptions/${rx.id}/fills`, {}); onRecorded() }
    catch (e: any) { setError(e?.response?.data?.message ?? "Could not record the fill.") }
    finally { setBusy(false) }
  }

  return (
    <div style={{ marginTop: 6, fontSize: 12, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
      <span style={{ color: "var(--hf-text-muted)" }}>
        {used} of {total} fill{total === 1 ? "" : "s"} used{remaining > 0 ? `, ${remaining} left` : ""}
      </span>
      {canFill && remaining > 0 && (
        <button onClick={record} disabled={busy}
          style={{ padding: "3px 10px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)",
            color: "var(--hf-text)", fontSize: 12, fontWeight: 600, cursor: busy ? "default" : "pointer" }}>
          {busy ? "Recording…" : used === 0 ? "Record fill" : "Record repeat"}
        </button>
      )}
      {error && <span role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</span>}
    </div>
  )
}
