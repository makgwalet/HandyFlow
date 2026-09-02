// src/pages/trainingprovider/TrainProvPage.tsx
//
// Module 4b (Training Provider — a standalone accredited training
// company running courses for external client organizations). Sibling
// of Module 4a (training, internal-only) but a genuinely separate
// module: no dependency in either direction (no hr, no training).
// Confirmed against the real synced backend:
// za.co.handyflow.platform.trainingprovider — 10 controllers, 9
// domain entities, every contract read directly from source before
// this was written.
//
// Same shell pattern as every other provider module this session
// (WarehousingPage.tsx / CollectionsAgencyPage.tsx): thin tab shell,
// inline styles, one ACCENT constant, each tab's real content in its
// own file.
import { useState } from "react"
import { LayoutDashboard, Building2, GraduationCap, CalendarDays, Award, Landmark } from "lucide-react"
import { TRAINPROV_ACCENT } from "./constants"
import TrainProvDashboard from "./TrainProvDashboard"
import TrainProvClientsTab from "./TrainProvClientsTab"
import TrainProvCoursesTab from "./TrainProvCoursesTab"
import TrainProvSessionsTab from "./TrainProvSessionsTab"
import TrainProvCertificatesTab from "./TrainProvCertificatesTab"
import TrainProvProfileTab from "./TrainProvProfileTab"

type Tab = "dashboard" | "clients" | "courses" | "sessions" | "certificates" | "profile"

const TABS: { key: Tab; label: string; icon: typeof LayoutDashboard }[] = [
  { key: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { key: "clients", label: "Clients", icon: Building2 },
  { key: "courses", label: "Courses", icon: GraduationCap },
  { key: "sessions", label: "Sessions", icon: CalendarDays },
  { key: "certificates", label: "Certificates", icon: Award },
  { key: "profile", label: "Academy Profile", icon: Landmark },
]

export default function TrainProvPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "28px 24px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: 11, background: TRAINPROV_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <GraduationCap size={20} color="#fff" />
          </div>
          <div>
            <h1 style={{ fontSize: 19, fontWeight: 800, color: "#0F172A", margin: 0 }}>Training Provider</h1>
            <p style={{ fontSize: 12.5, color: "#94A3B8", margin: 0 }}>Client portfolio · Accredited courses · Sessions & delegates · Certification · Billing</p>
          </div>
        </div>

        <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 24, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon = t.icon
            const active = tab === t.key
            return (
              <button key={t.key} onClick={() => setTab(t.key)}
                style={{
                  display: "flex", alignItems: "center", gap: 7, padding: "10px 16px", border: "none",
                  background: "none", cursor: "pointer", fontSize: 13, fontWeight: 600, whiteSpace: "nowrap",
                  color: active ? TRAINPROV_ACCENT : "#64748B",
                  borderBottom: active ? `2px solid ${TRAINPROV_ACCENT}` : "2px solid transparent",
                  marginBottom: -1,
                }}>
                <Icon size={15} /> {t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard" && <TrainProvDashboard />}
        {tab === "clients" && <TrainProvClientsTab />}
        {tab === "courses" && <TrainProvCoursesTab />}
        {tab === "sessions" && <TrainProvSessionsTab />}
        {tab === "certificates" && <TrainProvCertificatesTab />}
        {tab === "profile" && <TrainProvProfileTab />}
      </div>
    </div>
  )
}
