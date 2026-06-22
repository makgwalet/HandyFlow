// src/pages/clinic/ClinicPage.tsx
import { useState } from "react"
import { Users, Calendar, FileText, Stethoscope, LayoutDashboard, CreditCard, BarChart2 } from "lucide-react"
import ClinicDashboard   from "./ClinicDashboard"
import PatientsTab       from "./PatientsTab"
import ScheduleTab       from "./ScheduleTab"
import ConsultationsTab  from "./ConsultationsTab"
import PractitionersTab  from "./PractitionersTab"
import ClaimsTab         from "./ClaimsTab"
import BillingTab        from "./BillingTab"
import PatientFilePage   from "./PatientFilePage"

export type ClinicTab = "dashboard"|"patients"|"schedule"|"consultations"|"practitioners"|"claims"|"billing"

interface Patient { id: string; firstName: string; lastName: string; fullName: string; [key: string]: any }

const TABS: { id: ClinicTab; label: string; icon: React.ElementType }[] = [
  { id:"dashboard",     label:"Dashboard",     icon:LayoutDashboard },
  { id:"patients",      label:"Patients",      icon:Users           },
  { id:"schedule",      label:"Schedule",      icon:Calendar        },
  { id:"consultations", label:"Consultations", icon:FileText        },
  { id:"practitioners", label:"Practitioners", icon:Stethoscope     },
  { id:"claims",        label:"Claims",        icon:CreditCard      },
  { id:"billing",       label:"Billing",       icon:BarChart2       },
]

export function ClinicPage() {
  const [tab, setTab]             = useState<ClinicTab>("dashboard")
  const [openPatient, setOpenPatient] = useState<Patient|null>(null)
  const [sessionAppointment, setSessionAppointment] = useState<any|null>(null)

  return (
    <div style={{ fontFamily:"'Inter',system-ui,sans-serif" }}>
      {/* Page header */}
      <div style={{ marginBottom:24 }}>
        <div style={{ display:"flex", alignItems:"center", gap:10, marginBottom:4 }}>
          {openPatient ? (
            <button onClick={()=>setOpenPatient(null)}
              style={{ display:"flex", alignItems:"center", gap:6, background:"none", border:"none",
                cursor:"pointer", color:"#0D9488", fontSize:13, fontWeight:600, padding:0 }}>
              ← Back to Patients
            </button>
          ) : (
            <>
              <div style={{ width:36, height:36, borderRadius:10, background:"#0D9488",
                display:"flex", alignItems:"center", justifyContent:"center" }}>
                <Stethoscope size={18} color="#fff"/>
              </div>
              <h1 style={{ fontSize:24, fontWeight:800, color:"#0F172A", margin:0 }}>Clinic</h1>
            </>
          )}
        </div>
        {openPatient ? (
          <div style={{ fontSize:13, color:"#94A3B8" }}>
            Patient file — <strong style={{ color:"#0F172A" }}>{openPatient.fullName}</strong>
          </div>
        ) : (
          <p style={{ fontSize:13, color:"#94A3B8", margin:0, paddingLeft:46 }}>
            Patient records · Appointments · Consultations · Prescriptions
          </p>
        )}
      </div>

      {/* Card */}
      <div style={{ background:"#fff", border:"1px solid #E2E8F0", borderRadius:14, padding:24 }}>
        {!openPatient && (
          <div style={{ display:"flex", gap:2, borderBottom:"1px solid #E2E8F0", marginBottom:28, overflowX:"auto" }}>
            {TABS.map(t => {
              const Icon=t.icon; const active=tab===t.id
              return (
                <button key={t.id} onClick={()=>setTab(t.id)}
                  style={{ display:"flex", alignItems:"center", gap:6, padding:"10px 16px",
                    background:"none", border:"none", whiteSpace:"nowrap",
                    borderBottom:active?"2px solid #0D9488":"2px solid transparent",
                    color:active?"#0D9488":"#64748B",
                    fontWeight:active?600:400, fontSize:13, cursor:"pointer", marginBottom:-1 }}>
                  <Icon size={14}/>{t.label}
                </button>
              )
            })}
          </div>
        )}

        {openPatient ? (
          <PatientFilePage
            patient={openPatient}
            onClose={()=>setOpenPatient(null)}
            onNavigate={setTab}
            onOpenPatient={setOpenPatient}
            initialSession={sessionAppointment}
            onSessionClear={()=>setSessionAppointment(null)}/>
        ) : (
          <>
            {tab==="dashboard"     && <ClinicDashboard onNavigate={setTab}/>}
            {tab==="patients"      && <PatientsTab onOpenPatient={setOpenPatient}/>}
            {tab==="schedule"      && <ScheduleTab onStartSession={(appt, pat) => {
              setOpenPatient(pat)
              setSessionAppointment(appt)
              setTab("patients")
            }}/>}
            {tab==="consultations" && <ConsultationsTab/>}
            {tab==="practitioners" && <PractitionersTab/>}
            {tab==="claims"        && <ClaimsTab/>}
            {tab==="billing"       && <BillingTab/>}
          </>
        )}
      </div>
    </div>
  )
}
