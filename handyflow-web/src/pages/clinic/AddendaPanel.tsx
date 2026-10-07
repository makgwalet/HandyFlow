// src/pages/clinic/AddendaPanel.tsx
// Append-only notes on a signed consultation (S1-1). The original record is never edited.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

interface Addendum { id: string; text: string; createdAt: string }

export default function AddendaPanel({ consultationId, status }: { consultationId: string; status?: string }) {
  const qc = useQueryClient()
  const canWrite = usePermission("CLINIC_CLINICAL_WRITE")
  const [text, setText] = useState("")
  const [error, setError] = useState("")

  const { data: addenda = [] } = useQuery<Addendum[]>({
    queryKey: ["clinic-addenda", consultationId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/clinic/consultations/${consultationId}/addenda`)
      const d = r.data?.data ?? r.data
      return Array.isArray(d) ? d : []
    },
  })
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/clinic/consultations/${consultationId}/addenda`, { text }),
    onSuccess: () => { setText(""); setError(""); qc.invalidateQueries({ queryKey: ["clinic-addenda", consultationId] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not add the addendum"),
  })
  const canAdd = canWrite && (status === undefined || status === "SIGNED" || status === "LOCKED")

  if (addenda.length === 0 && !canAdd) return null
  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.06em", marginBottom: 8 }}>
        ADDENDA
      </div>
      {addenda.map(a => (
        <div key={a.id} style={{ padding: "8px 10px", marginBottom: 6, borderRadius: 8,
          background: "var(--hf-surface-muted)", fontSize: 13, color: "var(--hf-text)" }}>
          <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginBottom: 2 }}>
            {new Date(a.createdAt).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })}
          </div>
          <div style={{ whiteSpace: "pre-wrap" }}>{a.text}</div>
        </div>
      ))}
      {canAdd && (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
          <textarea value={text} onChange={e => setText(e.target.value)} rows={2} maxLength={5000}
            placeholder="Add a dated note. The original record stays as signed."
            style={{ flex: 1, padding: 8, borderRadius: 8, border: "1px solid var(--hf-border)", fontSize: 13 }} />
          <button disabled={add.isPending || !text.trim()} onClick={() => add.mutate()}
            style={{ padding: "8px 14px", borderRadius: 8, border: "none", fontWeight: 600, fontSize: 13,
              cursor: "pointer", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)" }}>
            Add
          </button>
        </div>
      )}
      {error && <div role="alert" style={{ marginTop: 6, fontSize: 12, color: "var(--hf-danger-text)" }}>{error}</div>}
    </div>
  )
}
