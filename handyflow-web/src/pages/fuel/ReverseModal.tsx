// Shared reason dialog for reversing a fuel receipt, dispatch or delivery.
// A reversal restores stock and keeps the original row for audit, so a
// reason of at least 5 characters is mandatory (the server enforces it too).
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"

export const MIN_REASON = 5

export interface ReverseModalProps {
  title: string
  summary: string
  endpoint: string            // e.g. /api/v1/fuel/receipts/{id}/reverse
  invalidate: string[][]      // query keys to refresh afterwards
  onClose: () => void
}

export default function ReverseModal({ title, summary, endpoint, invalidate, onClose }: ReverseModalProps) {
  const qc = useQueryClient()
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")

  const reverse = useMutation({
    mutationFn: () => apiClient.post(endpoint, { reason: reason.trim() }),
    onSuccess: () => {
      invalidate.forEach(k => qc.invalidateQueries({ queryKey: k }))
      onClose()
    },
    onError: (e: any) => setError(e?.response?.data?.message || "Could not reverse this entry."),
  })

  const tooShort = reason.trim().length < MIN_REASON

  return (
    <div role="dialog" aria-label={title} style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 24, width: 460, maxWidth: "92vw", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
          <button aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={20} /></button>
        </div>
        <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>{summary}</p>
        <label htmlFor="reverse-reason" style={{ display: "block", fontSize: 13, fontWeight: 500, color: "var(--hf-text-secondary)", marginBottom: 5 }}>Reason (required)</label>
        <textarea
          id="reverse-reason"
          value={reason}
          maxLength={500}
          rows={3}
          onChange={e => { setReason(e.target.value); setError("") }}
          placeholder="e.g. Captured against the wrong tank"
          style={{ width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" }}
        />
        {error && <div role="alert" style={{ marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Keep it</button>
          <button
            onClick={() => reverse.mutate()}
            disabled={tooShort || reverse.isPending}
            style={{ padding: "9px 20px", background: "var(--hf-danger-text)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: tooShort ? "not-allowed" : "pointer", opacity: tooShort ? 0.6 : 1 }}
          >
            {reverse.isPending ? "Reversing..." : "Reverse entry"}
          </button>
        </div>
      </div>
    </div>
  )
}
