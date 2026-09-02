// src/pages/training/TrainingPage.tsx
//
// Thin tab-shell, same shape as WarehousingPage.tsx (the reference file
// for this build). Module 4a (Internal L&D) — no client portfolio, no
// external client portal (that's 4b Training Provider, a separate module).
import { useState } from "react"
import { LayoutDashboard, GraduationCap, CalendarDays, Award } from "lucide-react"
import { TRAINING_ACCENT } from "./constants"
import TrainingDashboard from "./TrainingDashboard"
import TrainingCoursesTab from "./TrainingCoursesTab"
import TrainingSessionsTab from "./TrainingSessionsTab"
import TrainingCertificatesTab from "./TrainingCertificatesTab"

type Tab = "dashboard" | "courses" | "sessions" | "certificates"

const TABS: { key: Tab; label: string; icon: typeof LayoutDashboard }[] = [
  { key: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { key: "courses", label: "Courses", icon: GraduationCap },
  { key: "sessions", label: "Sessions", icon: CalendarDays },
  { key: "certificates", label: "Certificates", icon: Award },
]

export default function TrainingPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "28px 24px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: 11, background: TRAINING_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <GraduationCap size={20} color="#fff" />
          </div>
          <div>
            <h1 style={{ fontSize: 19, fontWeight: 800, color: "#0F172A", margin: 0 }}>Training &amp; L&amp;D</h1>
            <p style={{ fontSize: 12.5, color: "#94A3B8", margin: 0 }}>Course catalogue, sessions, enrollments and certifications for your own team</p>
          </div>
        </div>

        <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 24 }}>
          {TABS.map(t => {
            const Icon = t.icon
            const active = tab === t.key
            return (
              <button key={t.key} onClick={() => setTab(t.key)}
                style={{
                  display: "flex", alignItems: "center", gap: 7, padding: "10px 16px", border: "none",
                  background: "none", cursor: "pointer", fontSize: 13, fontWeight: 600,
                  color: active ? TRAINING_ACCENT : "#64748B",
                  borderBottom: active ? `2px solid ${TRAINING_ACCENT}` : "2px solid transparent",
                  marginBottom: -1,
                }}>
                <Icon size={15} /> {t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard" && <TrainingDashboard />}
        {tab === "courses" && <TrainingCoursesTab />}
        {tab === "sessions" && <TrainingSessionsTab />}
        {tab === "certificates" && <TrainingCertificatesTab />}
      </div>
    </div>
  )
}
