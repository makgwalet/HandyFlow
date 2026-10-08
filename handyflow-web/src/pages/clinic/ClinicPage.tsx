// src/pages/clinic/ClinicPage.tsx
//
// Sections are routes (/clinic/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts and components/shell/SectionedModulePage).
//
// The patient file is NOT a separate route: it is carried in the history
// entry's router state ({ openPatient }) on /clinic/patients. That gives:
//   - the browser Back button closes the file;
//   - a refresh keeps it open (history state survives a reload);
//   - clicking any section in the sidebar closes it (a plain link has no state).
// A consultation is its own full-screen route, /clinic/consult/:appointmentId (ConsultationWorkspacePage).
// Starting or resuming one from the Schedule, the drafts tray or a patient file navigates there; the
// ConsultationDock under every section shows a consultation left open and resumes it.
import { useState } from "react"
import { apiClient } from "../../api/client"
import { useLocation, useNavigate, useParams } from "react-router-dom"
import ConsultationDock from "./ConsultationDock"
import { workspacePath } from "./workspace"
import ClinicDashboard   from "./ClinicDashboard"
import PatientsTab       from "./PatientsTab"
import ScheduleTab       from "./ScheduleTab"
import PractitionersTab  from "./PractitionersTab"
import ClaimsTab         from "./ClaimsTab"
import BillingTab        from "./BillingTab"
import PatientFilePage   from "./PatientFilePage"
import RecallsTab, { type RecallBookRequest } from "./RecallsTab"
import WaitlistTab       from "./WaitlistTab"
import HandoffQueueTab   from "./HandoffQueueTab"
import DraftsTab         from "./DraftsTab"
import AccessLogTab      from "./AccessLogTab"
import WaitingRoomTab    from "./WaitingRoomTab"
import TimeOffTab        from "./TimeOffTab"
import WorkingHoursTab   from "./WorkingHoursTab"
import ClosuresTab       from "./ClosuresTab"
import RoomsTab          from "./RoomsTab"
import LabInboxTab       from "./LabInboxTab"
import QuestionLibraryAdminTab from "./QuestionLibraryAdminTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { CLINIC_SECTIONS } from "../../navigation/moduleSections"

export type ClinicTab = "dashboard"|"patients"|"schedule"|"practitioners"|"claims"|"billing"|"recalls"|"waitlist"|"handoff"|"drafts"|"access-log"|"waiting-room"|"time-off"|"working-hours"|"closures"|"rooms"|"lab-inbox"|"question-library"

interface Patient { id: string; firstName: string; lastName: string; fullName: string; [key: string]: any }
interface FileState { openPatient?: Patient | null }

export function ClinicPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { section } = useParams<{ section?: string }>()
  const base = CLINIC_SECTIONS.basePath
  // A booking asked for from another screen (Recalls); the Schedule opens its form with it filled in.
  const [bookRequest, setBookRequest] = useState<RecallBookRequest | null>(null)

  const state = (location.state ?? {}) as FileState
  // The file only ever belongs to the Patients section.
  const openPatient = section === "patients" ? (state.openPatient ?? null) : null

  const openFile = (patient: Patient) =>
    navigate(`${base}/patients`, { state: { openPatient: patient } satisfies FileState })
  const openConsultation = (appointment: { id?: string } | null | undefined) => { if (appointment?.id) navigate(workspacePath(appointment.id)) }
  // The dashboard knows a patient's id only; fetch the record, then open the file.
  const openPatientById = async (id: string) => {
    try {
      const r = await apiClient.get(`/api/v1/clinic/patients/${id}`)
      openFile((r.data?.data ?? r.data) as Patient)
    } catch { /* stays on the dashboard; the patient list is one click away */ }
  }
  const closeFile = () => navigate(`${base}/patients`, { replace: true })

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
              patient={openPatient as any}
              onClose={closeFile}
              onNavigate={goTo}
              onOpenPatient={p => openFile(p)} />
          )
        }
        switch (id) {
          case "dashboard":     return <ClinicDashboard onNavigate={goTo} onOpenPatient={openPatientById} onResume={id => openConsultation({ id })} />
          case "patients":      return <PatientsTab onOpenPatient={p => openFile(p)} />
          case "schedule":      return <ScheduleTab onStartSession={(appt: any) => openConsultation(appt)} prefill={bookRequest} onPrefillUsed={() => setBookRequest(null)} />
          case "recalls":       return <RecallsTab onBook={r => { setBookRequest(r); goTo("schedule") }} />
          case "waitlist":      return <WaitlistTab />
          case "handoff":       return <HandoffQueueTab />
          case "drafts":        return <DraftsTab onResume={(_pat: Patient, appt: any) => openConsultation(appt)} />
          case "question-library": return <QuestionLibraryAdminTab />
          case "waiting-room":  return <WaitingRoomTab />
          case "time-off":      return <TimeOffTab />
          case "working-hours": return <WorkingHoursTab />
          case "closures":      return <ClosuresTab />
          case "rooms":         return <RoomsTab />
          case "lab-inbox":     return <LabInboxTab />
          case "access-log":    return <AccessLogTab />
          case "practitioners": return <PractitionersTab />
          case "claims":        return <ClaimsTab />
          case "billing":       return <BillingTab />
          default:              return null
        }
      }}>
      <ConsultationDock />
    </SectionedModulePage>
  )
}
