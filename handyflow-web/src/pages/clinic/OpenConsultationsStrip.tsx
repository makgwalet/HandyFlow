// The consultations this clinician has open, as tabs along the top of the consultation screen (workspace, patch 0184).
// Switching saves the one on screen first (the parent does that), and each patient's draft is its own screen, so nothing
// typed for one patient can land in another's record. The patient on screen is marked and always named in the consultation bar.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { openTabs, type ConsultationLike, type OpenTab } from "./workspace"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }
const MARK = { done: "✓", attention: "!", todo: "—" } as const

export function useOpenTabs(currentAppointmentId?: string): OpenTab[] {
  const { data = [] } = useQuery<(ConsultationLike & { createdAt?: string })[]>({
    queryKey: ["clinic-dock"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/consultations/drafts", { params: { mine: true } })),
    refetchInterval: 30_000,
  })
  return openTabs(data, currentAppointmentId)
}

export default function OpenConsultationsStrip({ currentAppointmentId, onSwitch }: { currentAppointmentId: string; onSwitch: (appointmentId: string) => void }) {
  const tabs = useOpenTabs(currentAppointmentId)
  if (tabs.filter(t => !t.current).length === 0) return null      // nothing else open: no strip
  return (
    <nav aria-label="Open consultations" style={{ display: "flex", gap: 6, flexWrap: "wrap", margin: "0 0 10px" }}>
      {tabs.map(t => (
        <button key={t.appointmentId} type="button" aria-current={t.current ? "page" : undefined}
          onClick={() => { if (!t.current) onSwitch(t.appointmentId) }}
          title={t.stages.map(s => `${s.label} ${MARK[s.state]}`).join(" · ")}
          style={{ padding: "6px 12px", borderRadius: 10, fontSize: 12, cursor: t.current ? "default" : "pointer",
            fontWeight: t.current ? 800 : 600,
            border: t.current ? "2px solid var(--hf-accent)" : "1px solid var(--hf-border)",
            background: t.current ? "var(--hf-surface)" : "var(--hf-surface-muted)", color: "var(--hf-text)" }}>
          {t.patientName}
        </button>
      ))}
    </nav>
  )
}
