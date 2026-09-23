// src/pages/compliancetender/CompliancePage.tsx
//
// Tab shell for the compliancetender module — Business Compliance &
// Tender. Follows LegalCompliancePage.tsx's exact convention (colored
// icon badge + title/subtitle header, underline tab strip, active tab's
// component below). ACCENT (#0369A1) checked against every other
// module's own ACCENT constant to confirm no collision — see
// RegistrationsTab.tsx and siblings for the same colour reused
// consistently across this module's own components.
import { useState } from "react"
import { LayoutDashboard, ShieldCheck, FileText, CalendarClock, Briefcase, ClipboardCheck } from "lucide-react"
import ComplianceDashboard from "./ComplianceDashboard"
import RegistrationsTab from "./RegistrationsTab"
import DocumentsTab from "./DocumentsTab"
import DeadlinesTab from "./DeadlinesTab"
import TendersTab from "./TendersTab"

type Tab = "dashboard" | "registrations" | "documents" | "deadlines" | "tenders"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",     label: "Dashboard",     icon: LayoutDashboard },
  { id: "registrations", label: "Registrations", icon: ShieldCheck     },
  { id: "documents",     label: "Documents",     icon: FileText        },
  { id: "deadlines",     label: "Deadlines",     icon: CalendarClock   },
  { id: "tenders",       label: "Tenders",       icon: Briefcase       },
]

const ACCENT = "#0369A1"

export default function CompliancePage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <ClipboardCheck size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Business Compliance &amp; Tender</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          CIPC, SARS, UIF, PSIRA, CSD, cidb &amp; NHBRC registrations · Document vault · Compliance calendar · Tender workspace
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 16px",
                  background: "none", border: "none", whiteSpace: "nowrap",
                  borderBottom: active ? `2px solid ${ACCENT}` : "2px solid transparent",
                  color: active ? ACCENT : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"     && <ComplianceDashboard onNavigate={(t) => setTab(t as Tab)} />}
        {tab === "registrations" && <RegistrationsTab />}
        {tab === "documents"     && <DocumentsTab />}
        {tab === "deadlines"     && <DeadlinesTab />}
        {tab === "tenders"       && <TendersTab />}
      </div>
    </div>
  )
}
