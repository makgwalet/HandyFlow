// What the doctor sees right after signing: the visit is done, here is what was produced, and who is next.
// The next patient is already chosen from today's queue, so the doctor never goes hunting for them.
import { useQuery } from "@tanstack/react-query"
import { CheckCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { todayRangeUrl } from "./WaitingRoomTab"
import { nextUp, type QueueAppt } from "./workspace"
import { downloadPdf } from "./patientFile.shared"
import { primaryBtn, cancelBtn, TEAL } from "./consultationSession.shared"

export interface SignedSummary { consultationId: string; diagnosis: string; rxCount: number; followUpDays: string; durationMinutes: number }

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }
const hhmm = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })

export default function NextPatientPanel({ patientName, summary, appointmentId, practitionerId, onOpenNext, onToday, onOpenFile }: {
  patientName: string; summary: SignedSummary; appointmentId: string; practitionerId?: string
  onOpenNext: (a: QueueAppt) => void; onToday: () => void; onOpenFile: () => void
}) {
  const { data = [], isLoading } = useQuery<QueueAppt[]>({
    queryKey: ["clinic-next-up", appointmentId],
    queryFn: async () => unwrap(await apiClient.get(todayRangeUrl(new Date()))),
  })
  const up = nextUp(data, { mine: practitionerId || null, excludeId: appointmentId })
  const next = up.ready

  return (
    <div style={{ maxWidth: 720, margin: "24px auto", display: "flex", flexDirection: "column", gap: 16 }}>
      <section aria-label="Consultation completed" style={{ textAlign: "center", padding: 24, background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14 }}>
        <CheckCircle size={40} aria-hidden="true" style={{ color: "var(--hf-success-text-strong)" }} />
        <h1 style={{ margin: "8px 0 2px", fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>Consultation signed</h1>
        <div style={{ fontSize: 14, color: "var(--hf-text-muted)" }}>{patientName} · {summary.durationMinutes} min</div>
        <div style={{ display: "flex", gap: 8, justifyContent: "center", flexWrap: "wrap", marginTop: 14, fontSize: 13, color: "var(--hf-text)" }}>
          {summary.diagnosis && <span><b>Diagnosis:</b> {summary.diagnosis}</span>}
          <span><b>Prescriptions:</b> {summary.rxCount}</span>
          <span><b>Follow-up:</b> {summary.followUpDays.trim() ? `in ${summary.followUpDays.trim()} days` : "none set"}</span>
        </div>
        <div style={{ display: "flex", gap: 8, justifyContent: "center", flexWrap: "wrap", marginTop: 14 }}>
          <button style={cancelBtn} onClick={() => downloadPdf(`/api/v1/clinic/consultations/${summary.consultationId}/summary-pdf`, `visit-summary-${summary.consultationId}.pdf`).catch(() => {})}>Visit summary (PDF)</button>
          {summary.rxCount > 0 && <button style={cancelBtn} onClick={() => downloadPdf(`/api/v1/clinic/consultations/${summary.consultationId}/prescription-pdf`, `rx-${summary.consultationId}.pdf`).catch(() => {})}>Prescription (PDF)</button>}
          <button style={cancelBtn} onClick={onOpenFile}>Open patient file</button>
        </div>
      </section>

      <section aria-label="Next patient" style={{ padding: 20, background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14 }}>
        <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--hf-text-muted)", marginBottom: 8 }}>Next patient</div>
        {isLoading ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Checking today's queue…</div>
          : next ? (
            <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
              <div style={{ flex: "1 1 220px" }}>
                <div style={{ fontSize: 16, fontWeight: 800, color: "var(--hf-text)" }}>{next.patientName}</div>
                <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
                  {hhmm(next.scheduledAt)} · {next.status === "TRIAGED" ? "Triaged, ready to be seen" : "Checked in, waiting"}{next.reason ? ` · ${next.reason}` : ""}
                </div>
                {up.waiting > 1 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{up.waiting - 1} more waiting after them</div>}
              </div>
              <button style={{ ...primaryBtn, background: TEAL }} onClick={() => onOpenNext(next)}>Open next patient</button>
            </div>
          ) : (
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
              Nobody is waiting.{up.upcoming ? ` Next expected: ${up.upcoming.patientName} at ${hhmm(up.upcoming.scheduledAt)}.` : " Nothing else is booked for today."}
            </div>
          )}
        <div style={{ marginTop: 14 }}><button style={cancelBtn} onClick={onToday}>Back to Today</button></div>
      </section>
    </div>
  )
}
