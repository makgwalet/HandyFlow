// The consultation as a place, not a pop-up: /clinic/consult/:appointmentId.
// Opening it makes sure the appointment is in progress (checking the patient in if needed), loads the patient, runs the
// workspace, and when the doctor signs shows the result and who is next. Leaving keeps everything saved (see the dock).
import { useEffect, useRef, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuthStore } from "../../store/auth.store"
import ConsultationSession from "./ConsultationSession"
import NextPatientPanel, { type SignedSummary } from "./NextPatientPanel"
import { myPractitionerId } from "./currentPractitioner"
import { startSteps, workspacePath } from "./workspace"
import { cancelBtn } from "./consultationSession.shared"

const one = (r: any) => r.data?.data ?? r.data
const list = (r: any) => { const p = one(r); return Array.isArray(p) ? p : (p?.content ?? []) }

export default function ConsultationWorkspacePage() {
  const { appointmentId = "" } = useParams<{ appointmentId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const email = useAuthStore(s => s.user?.email)
  const [signed, setSigned] = useState<SignedSummary | null>(null)
  const [ready, setReady] = useState(false)
  const [problem, setProblem] = useState("")
  const started = useRef(false)

  const { data: appt, isError: apptError } = useQuery<any>({
    queryKey: ["workspace-appointment", appointmentId], retry: false,
    queryFn: async () => one(await apiClient.get(`/api/v1/clinic/appointments/${appointmentId}`)),
  })
  const { data: patient } = useQuery<any>({
    queryKey: ["workspace-patient", appt?.patientId], enabled: !!appt?.patientId,
    queryFn: async () => one(await apiClient.get(`/api/v1/clinic/patients/${appt.patientId}`)),
  })
  const { data: practitioners = [] } = useQuery<any[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => list(await apiClient.get("/api/v1/clinic/practitioners/list")),
  })

  // Make sure the appointment is in progress before the notes open; resuming one already in progress does nothing.
  useEffect(() => {
    if (!appt || started.current) return
    started.current = true
    const steps = startSteps(appt.status)
    if (steps === null) { setProblem(appt.status === "COMPLETED" ? "This consultation is already complete." : `This appointment is ${String(appt.status).toLowerCase().replace(/_/g, " ")}, so it cannot be opened.`); return }
    ;(async () => {
      try {
        for (const step of steps) await apiClient.post(`/api/v1/clinic/appointments/${appt.id}/${step}`)
        qc.invalidateQueries({ queryKey: ["clinic-appts-dashboard"] })
        setReady(true)
      } catch (e: any) { setProblem(e?.response?.data?.message ?? "Could not start the consultation.") }
    })()
  }, [appt, qc])

  const back = () => navigate("/clinic/dashboard")
  // Back to wherever the doctor came from; straight after a refresh there is nowhere to go back to.
  const leave = () => (window.history.state?.idx > 0 ? navigate(-1) : back())
  const openFile = () => navigate("/clinic/patients", { state: { openPatient: patient } })

  if (apptError) return <Message text="This appointment could not be found." onBack={back} />
  if (problem) return <Message text={problem} onBack={back} onFile={patient ? openFile : undefined} />
  if (!appt || !patient || !ready) return <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-muted)" }}>Opening the consultation…</div>

  if (signed) return (
    <NextPatientPanel patientName={patient.fullName} summary={signed} appointmentId={appt.id} practitionerId={myPractitionerId(practitioners, email) || undefined}
      onOpenNext={a => { setSigned(null); navigate(workspacePath(a.id)) }} onToday={back} onOpenFile={openFile} />
  )

  return (
    <div style={{ maxWidth: 1400, margin: "0 auto" }}>
      <ConsultationSession patient={patient} appointment={appt}
        onComplete={(_id, summary) => setSigned(summary)}
        onMinimise={leave}
        onCancel={() => { qc.invalidateQueries({ queryKey: ["clinic-dock"] }); back() }} />
    </div>
  )
}

function Message({ text, onBack, onFile }: { text: string; onBack: () => void; onFile?: () => void }) {
  return (
    <div role="alert" style={{ maxWidth: 520, margin: "48px auto", textAlign: "center", padding: 24, background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14 }}>
      <div style={{ fontSize: 15, color: "var(--hf-text)", marginBottom: 14 }}>{text}</div>
      <div style={{ display: "flex", gap: 8, justifyContent: "center" }}>
        <button style={cancelBtn} onClick={onBack}>Back to Today</button>
        {onFile && <button style={cancelBtn} onClick={onFile}>Open patient file</button>}
      </div>
    </div>
  )
}
