// src/pages/clinic/ClinicPage.tsx
//
// Sections are routes (/clinic/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts and components/shell/SectionedModulePage).
//
// The patient file is NOT a separate route: it is carried in the history
// entry's router state ({ openPatient, sessionAppointment }) on
// /clinic/patients. That gives the behaviour the old in-page state had, plus:
//   - the browser Back button closes the file;
//   - a refresh keeps it open (history state survives a reload);
//   - clicking any section in the sidebar closes it (a plain link has no state).
// "Start session" from the Schedule opens the patient's file with an
// appointment to begin; that is a ONE-SHOT: the file clears it from the
// history entry as soon as it has consumed it, so a refresh cannot restart it.
import { useLocation, useNavigate, useParams } from "react-router-dom"
import ClinicDashboard   from "./ClinicDashboard"
import PatientsTab       from "./PatientsTab"
import ScheduleTab       from "./ScheduleTab"
import ConsultationsTab  from "./ConsultationsTab"
import PractitionersTab  from "./PractitionersTab"
import ClaimsTab         from "./ClaimsTab"
import BillingTab        from "./BillingTab"
import PatientFilePage   from "./PatientFilePage"
import RecallsTab        from "./RecallsTab"
import WaitlistTab       from "./WaitlistTab"
import HandoffQueueTab   from "./HandoffQueueTab"
import DraftsTab         from "./DraftsTab"
import AccessLogTab      from "./AccessLogTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { CLINIC_SECTIONS } from "../../navigation/moduleSections"

export type ClinicTab = "dashboard"|"patients"|"schedule"|"consultations"|"practitioners"|"claims"|"billing"|"recalls"|"waitlist"|"handoff"|"drafts"|"access-log"

interface Patient { id: string; firstName: string; lastName: string; fullName: string; [key: string]: any }
interface FileState { openPatient?: Patient | null; sessionAppointment?: unknown }

export function ClinicPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { section } = useParams<{ section?: string }>()
  const base = CLINIC_SECTIONS.basePath

  const state = (location.state ?? {}) as FileState
  // The file only ever belongs to the Patients section.
  const openPatient = section === "patients" ? (state.openPatient ?? null) : null
  const sessionAppointment = openPatient ? (state.sessionAppointment ?? null) : null

  const openFile = (patient: Patient, appointment?: unknown) =>
    navigate(`${base}/patients`, { state: { openPatient: patient, sessionAppointment: appointment ?? null } satisfies FileState })
  const closeFile = () => navigate(`${base}/patients`, { replace: true })
  // One-shot: drop the appointment from this history entry once consumed.
  const clearSession = () =>
    navigate(location.pathname, { replace: true, state: { openPatient } satisfies FileState })

  const backButton = openPatient ? (
    <button onClick={closeFile}
      style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none",
        cursor: "pointer", color: "var(--hf-accent-text)", fontSize: 13, fontWeight: 600, padding: 0 }}>
      ← Back to Patients
    </button>
  ) : undefined

  return (
    <SectionedModulePage config={CLINIC_SECTIONS}
      action={backButton}
      subtitle="Patient records · Appointments · Consultations · Prescriptions"
      detail={openPatient ? { label: openPatient.fullName, subtitle: "Patient file" } : undefined}
      render={(id, goTo) => {
        if (id === "patients" && openPatient) {
          return (
            <PatientFilePage
              patient={openPatient}
              onClose={closeFile}
              onNavigate={goTo}
              onOpenPatient={p => openFile(p)}
              initialSession={sessionAppointment}
              onSessionClear={clearSession} />
          )
        }
        switch (id) {
          case "dashboard":     return <ClinicDashboard onNavigate={goTo} />
          case "patients":      return <PatientsTab onOpenPatient={p => openFile(p)} />
          case "schedule":      return <ScheduleTab onStartSession={(appt: unknown, pat: Patient) => openFile(pat, appt)} />
          case "consultations": return <ConsultationsTab />
          case "recalls":       return <RecallsTab />
          case "waitlist":      return <WaitlistTab />
          case "handoff":       return <HandoffQueueTab />
          case "drafts":        return <DraftsTab onResume={(pat: Patient, appt: unknown) => openFile(pat, appt)} />
          case "access-log":    return <AccessLogTab />
          case "practitioners": return <PractitionersTab />
          case "claims":        return <ClaimsTab />
          case "billing":       return <BillingTab />
          default:              return null
        }
      }} />
  )
}
