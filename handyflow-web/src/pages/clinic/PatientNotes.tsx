// src/pages/clinic/PatientNotes.tsx
// Sticky notes and alerts on a patient's file. An alert shows as a banner on every tab of the file; the full list
// (with add and resolve) lives on the overview. A resolved note stays on record and is simply no longer shown here.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

export interface PatientNote {
  id: string; kind: "NOTE" | "ALERT"; severity?: "INFO" | "WARNING" | "CRITICAL" | null; body: string
  createdAt?: string; resolvedAt?: string | null
}
const unwrap = (r: any) => r.data?.data ?? r.data
const url = (patientId: string) => `/api/v1/clinic/patients/${patientId}/notes`

function useNotes(patientId: string) {
  return useQuery<PatientNote[]>({
    queryKey: ["clinic-patient-notes", patientId],
    queryFn: async () => unwrap(await apiClient.get(url(patientId))) ?? [],
  })
}

const SEV_STYLE: Record<string, { bg: string; fg: string; border: string }> = {
  CRITICAL: { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)", border: "var(--hf-danger-border)" },
  WARNING: { bg: "var(--hf-warning-soft)", fg: "var(--hf-warning-text)", border: "var(--hf-warning-border)" },
  INFO: { bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)", border: "var(--hf-info-border)" },
}
const sev = (n: PatientNote) => SEV_STYLE[n.severity ?? "WARNING"] ?? SEV_STYLE.WARNING

/** Open alerts only, read-only, for the top of every tab. Renders nothing when there are none or they cannot be loaded. */
export function PatientAlertBanner({ patientId }: { patientId: string }) {
  const { data } = useNotes(patientId)
  const alerts = (data ?? []).filter(n => n.kind === "ALERT")
  if (alerts.length === 0) return null
  return (
    <div role="alert" aria-label="Patient alerts" style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 14 }}>
      {alerts.map(a => {
        const s = sev(a)
        return (
          <div key={a.id} style={{ padding: "8px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600,
            background: s.bg, color: s.fg, border: `1px solid ${s.border}` }}>
            {a.severity === "CRITICAL" ? "⚠ " : ""}{a.body}
          </div>
        )
      })}
    </div>
  )
}

export default function PatientNotesPanel({ patientId }: { patientId: string }) {
  const qc = useQueryClient()
  const canWrite = usePermission("CLINIC_CLINICAL_WRITE")
  const { data, isError } = useNotes(patientId)
  const [kind, setKind] = useState<"NOTE" | "ALERT">("NOTE")
  const [severity, setSeverity] = useState("WARNING")
  const [body, setBody] = useState("")
  const [error, setError] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["clinic-patient-notes", patientId] })
  const fail = (e: any) => setError(e?.response?.data?.message ?? e?.message ?? "Could not save.")

  const add = useMutation({
    mutationFn: () => apiClient.post(url(patientId), { kind, severity: kind === "ALERT" ? severity : null, body: body.trim() }),
    onSuccess: () => { setBody(""); setError(""); refresh() },
    onError: fail,
  })
  const resolve = useMutation({
    mutationFn: (id: string) => apiClient.post(`${url(patientId)}/${id}/resolve`),
    onSuccess: () => { setError(""); refresh() },
    onError: fail,
  })

  const notes = data ?? []
  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.06em", marginBottom: 6 }}>NOTES AND ALERTS</div>
      {isError && <div style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>Notes could not be loaded.</div>}
      {!isError && notes.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No open notes or alerts.</div>}
      {notes.map(n => (
        <div key={n.id} style={{ display: "flex", gap: 8, alignItems: "baseline", padding: "3px 0", fontSize: 13 }}>
          <span style={{ fontSize: 10, fontWeight: 700, padding: "1px 7px", borderRadius: 20,
            background: n.kind === "ALERT" ? sev(n).bg : "var(--hf-border)", color: n.kind === "ALERT" ? sev(n).fg : "var(--hf-text-muted)" }}>
            {n.kind === "ALERT" ? `ALERT · ${n.severity ?? "WARNING"}` : "NOTE"}
          </span>
          <span style={{ flex: 1 }}>{n.body}</span>
          {canWrite && (
            <button type="button" onClick={() => resolve.mutate(n.id)} disabled={resolve.isPending}
              style={{ background: "none", border: "none", cursor: "pointer", fontSize: 11, color: "var(--hf-accent-text)" }}>
              Resolve
            </button>
          )}
        </div>
      ))}
      {canWrite && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8 }}>
          <select aria-label="Kind" value={kind} onChange={e => setKind(e.target.value as "NOTE" | "ALERT")}>
            <option value="NOTE">Note</option><option value="ALERT">Alert</option>
          </select>
          {kind === "ALERT" && (
            <select aria-label="Severity" value={severity} onChange={e => setSeverity(e.target.value)}>
              <option value="INFO">Info</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option>
            </select>
          )}
          <input aria-label="Note text" value={body} maxLength={1000} placeholder="e.g. Needs an interpreter"
            onChange={e => setBody(e.target.value)} style={{ flex: 1, minWidth: 180 }} />
          <button type="button" onClick={() => add.mutate()} disabled={!body.trim() || add.isPending}>Add</button>
        </div>
      )}
      {error && <div role="status" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
    </div>
  )
}
