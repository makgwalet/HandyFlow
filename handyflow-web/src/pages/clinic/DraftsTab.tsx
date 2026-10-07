// src/pages/clinic/DraftsTab.tsx
// Drafts tray: consultations that were started and autosaved but not yet handed over or signed
// (DRAFT, NURSE_IN_PROGRESS, RETURNED_TO_NURSE). Resuming reopens the patient's file on the same
// appointment, where the session finds the draft and continues it. A draft with no appointment
// cannot be resumed from here yet; it can still be discarded.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

interface Draft {
  id: string; patientId: string; patientName: string; appointmentId?: string | null
  chiefComplaint?: string; status: string; createdAt: string; updatedAt?: string
}
interface Props { onResume: (patient: any, appointment: any) => void }

const unwrap = (r: any) => r.data?.data ?? r.data
const fmt = (iso?: string) => iso ? new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : ""
const STATUS: Record<string, string> = { DRAFT: "Draft", NURSE_IN_PROGRESS: "Nurse in progress", RETURNED_TO_NURSE: "Returned by doctor" }

export default function DraftsTab({ onResume }: Props) {
  const qc = useQueryClient()
  const canWrite = usePermission("CLINIC_CLINICAL_WRITE")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState<string | null>(null)
  const [mine, setMine] = useState(true)

  const { data: drafts = [], isLoading } = useQuery<Draft[]>({
    queryKey: ["clinic-drafts", mine],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/consultations/drafts", { params: { mine } })) ?? [],
  })

  const discard = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/clinic/consultations/${id}/abandon`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clinic-drafts"] }),
    onError: (e: any) => setError(e?.response?.data?.message || "Could not discard the draft."),
  })

  const resume = async (d: Draft) => {
    setError(""); setBusy(d.id)
    try {
      const [patient, appts] = await Promise.all([
        apiClient.get(`/api/v1/clinic/patients/${d.patientId}`).then(unwrap),
        apiClient.get(`/api/v1/clinic/patients/${d.patientId}/appointments`).then(unwrap),
      ])
      const appt = (appts ?? []).find((a: any) => a.id === d.appointmentId)
      if (!appt) { setError("The appointment for this draft could not be found."); return }
      onResume(patient, appt)
    } catch { setError("Could not open the patient file.") }
    finally { setBusy(null) }
  }

  const btn = (primary = false): React.CSSProperties => ({
    padding: "7px 14px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
    border: primary ? "none" : "1px solid var(--hf-border)",
    background: primary ? "var(--hf-accent)" : "var(--hf-surface)",
    color: primary ? "var(--hf-on-accent, #fff)" : "var(--hf-text)",
  })

  return (
    <div>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>
        Consultations that have not been handed over or signed. Nothing here is lost: drafts autosave as you work.
      </p>
      <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13, color: "var(--hf-text)", marginBottom: 10 }}>
        <input type="checkbox" checked={mine} onChange={e => setMine(e.target.checked)} />
        Only drafts I started
      </label>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {isLoading && <div style={{ color: "var(--hf-text-muted)" }}>Loading drafts…</div>}
      {!isLoading && drafts.length === 0 && (
        <div style={{ padding: 32, textAlign: "center", color: "var(--hf-text-muted)",
          border: "1px dashed var(--hf-border)", borderRadius: 12 }}>
          {mine ? "You have no open drafts. Drafts from before authors were recorded only show when the box above is unticked." : "No open drafts."}
        </div>
      )}
      <div style={{ display: "grid", gap: 10 }}>
        {drafts.map(d => (
          <div key={d.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14,
            background: "var(--hf-surface)", display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ flex: "1 1 220px", minWidth: 0 }}>
              <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{d.patientName}</div>
              <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
                {d.chiefComplaint || "No complaint recorded"} · last saved {fmt(d.updatedAt ?? d.createdAt)}
              </div>
              <div style={{ fontSize: 12, marginTop: 4, color: "var(--hf-accent-text)", fontWeight: 600 }}>
                {STATUS[d.status] ?? d.status}
              </div>
            </div>
            {canWrite && (
              <div style={{ display: "flex", gap: 8 }}>
                <button style={btn(true)} disabled={!d.appointmentId || busy === d.id}
                  title={d.appointmentId ? "" : "This draft has no appointment, so it cannot be resumed from here."}
                  onClick={() => resume(d)}>Resume</button>
                <button style={btn()} disabled={discard.isPending}
                  onClick={() => { if (window.confirm("Discard this draft? It cannot be recovered.")) discard.mutate(d.id) }}>Discard</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
