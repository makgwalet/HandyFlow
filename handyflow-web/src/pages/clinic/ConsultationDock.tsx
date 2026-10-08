// A consultation left open is never lost: this strip sits under every clinic screen and says exactly where it stopped.
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { dockCandidate, dockStages, workspacePath, type ConsultationLike } from "./workspace"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }
const MARK = { done: "✓", attention: "!", todo: "—" } as const

export default function ConsultationDock() {
  const navigate = useNavigate()
  const { data = [] } = useQuery<(ConsultationLike & { updatedAt?: string })[]>({
    queryKey: ["clinic-dock"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/consultations/drafts", { params: { mine: true } })),
    refetchInterval: 30_000,
  })
  const c = dockCandidate(data)
  if (!c) return null
  return (
    <div role="region" aria-label="Consultation in progress" style={{ position: "sticky", bottom: 12, zIndex: 20, marginTop: 16, display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap",
      padding: "10px 16px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderLeft: "4px solid var(--hf-danger)", borderRadius: 12, boxShadow: "0 6px 24px rgba(0,0,0,0.14)" }}>
      <div style={{ fontSize: 13, fontWeight: 800, color: "var(--hf-text)" }}>Consultation in progress · {c.patientName ?? "patient"}</div>
      <div style={{ flex: 1, fontSize: 12, color: "var(--hf-text-muted)" }}>
        {dockStages(c).map(s => `${s.label} ${MARK[s.state]}`).join(" · ")}
      </div>
      <button onClick={() => navigate(workspacePath(c.appointmentId!))}
        style={{ padding: "7px 16px", borderRadius: 8, border: "none", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Resume</button>
    </div>
  )
}
