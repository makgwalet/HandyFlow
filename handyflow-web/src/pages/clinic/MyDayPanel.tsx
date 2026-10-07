// src/pages/clinic/MyDayPanel.tsx
// What is waiting on the signed-in clinician: their own unfinished drafts and the handoff queue. Each number links to
// the screen where the work is done. A count that cannot be loaded shows "—" rather than 0, so a failure never reads
// as "nothing to do".
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

const unwrap = (r: any) => r.data?.data ?? r.data

export interface QueueItem { status: string }
export const countStatus = (items: QueueItem[] | undefined, status: string) =>
  items ? items.filter(i => i.status === status).length : undefined

export default function MyDayPanel({ onNavigate }: { onNavigate: (section: string) => void }) {
  const canWrite = usePermission("CLINIC_CLINICAL_WRITE")
  const drafts = useQuery<any[]>({
    queryKey: ["clinic-myday-drafts"], enabled: canWrite, retry: false,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/consultations/drafts?mine=true")) ?? [],
  })
  const queue = useQuery<QueueItem[]>({
    queryKey: ["clinic-myday-queue"], enabled: canWrite, retry: false,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/consultations/handoff-queue")) ?? [],
  })
  if (!canWrite) return null

  const tiles: { label: string; value: number | undefined; section: string }[] = [
    { label: "My unfinished drafts", value: drafts.data?.length, section: "drafts" },
    { label: "Waiting for a doctor", value: countStatus(queue.data, "READY_FOR_DOCTOR"), section: "handoff" },
    { label: "Under doctor review", value: countStatus(queue.data, "DOCTOR_REVIEWING"), section: "handoff" },
    { label: "Returned to nurse", value: countStatus(queue.data, "RETURNED_TO_NURSE"), section: "handoff" },
  ]
  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.06em", marginBottom: 6 }}>MY DAY</div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        {tiles.map(t => (
          <button key={t.label} type="button" onClick={() => onNavigate(t.section)}
            style={{ flex: "1 1 160px", textAlign: "left", cursor: "pointer", padding: "10px 14px", borderRadius: 10,
              border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)" }}>
            <div style={{ fontSize: 22, fontWeight: 800 }}>{t.value ?? "—"}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{t.label}</div>
          </button>
        ))}
      </div>
    </div>
  )
}
