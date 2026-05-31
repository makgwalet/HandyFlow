// src/pages/clinic/ClinicPage.tsx
import { useState } from "react"
import { Users, Calendar, FileText, Stethoscope, LayoutDashboard } from "lucide-react"
import ClinicDashboard    from "./ClinicDashboard"
import PatientsTab        from "./PatientsTab"
import AppointmentsTab    from "./AppointmentsTab"
import ConsultationsTab   from "./ConsultationsTab"
import PractitionersTab   from "./PractitionersTab"

type Tab = "dashboard" | "patients" | "appointments" | "consultations" | "practitioners"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",     label: "Dashboard",     icon: LayoutDashboard },
  { id: "patients",      label: "Patients",      icon: Users },
  { id: "appointments",  label: "Appointments",  icon: Calendar },
  { id: "consultations", label: "Consultations", icon: FileText },
  { id: "practitioners", label: "Practitioners", icon: Stethoscope },
]

export function ClinicPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#0D9488", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Stethoscope size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Clinic</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Patient records · Appointments · Consultations · Prescriptions
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, paddingBottom: 0, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 16px",
                  background: "none", border: "none", whiteSpace: "nowrap",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"     && <ClinicDashboard onNavigate={setTab} />}
        {tab === "patients"      && <PatientsTab />}
        {tab === "appointments"  && <AppointmentsTab />}
        {tab === "consultations" && <ConsultationsTab />}
        {tab === "practitioners" && <PractitionersTab />}
      </div>
    </div>
  )
}
