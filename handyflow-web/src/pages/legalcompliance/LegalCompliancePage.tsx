// src/pages/legalcompliance/LegalCompliancePage.tsx
//
// Module 1 (of the Track 7 build order) — internal-only, no portal.
// Confirmed against the real synced backend (za.co.handyflow.platform.legalcompliance)
// after the legalcompliance code was pushed to GitHub main and the project
// resync picked it up — 5 real controllers, all DTOs and enums read directly,
// not inferred. See each tab file's header comment for its specific endpoints.
//
// Tab shell follows the same convention as FleetPage.tsx / BookingsPage.tsx:
// a colored icon badge + title/subtitle header, then a card with an
// underline tab strip and the active tab's component below it.
import { useState } from "react"
import { LayoutDashboard, ClipboardList, Gavel, Lock, FileSearch, CalendarDays, Scale } from "lucide-react"
import LegalComplianceDashboard from "./LegalComplianceDashboard"
import ObligationsTab from "./ObligationsTab"
import LitigationTab from "./LitigationTab"
import PopiaTab from "./PopiaTab"
import DsarTab from "./DsarTab"
import CalendarTab from "./CalendarTab"

type Tab = "dashboard" | "obligations" | "litigation" | "popia" | "dsar" | "calendar"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",   label: "Dashboard",           icon: LayoutDashboard },
  { id: "obligations", label: "Obligations",         icon: ClipboardList   },
  { id: "litigation",  label: "Litigation",          icon: Gavel           },
  { id: "popia",       label: "POPIA Register",      icon: Lock            },
  { id: "dsar",        label: "DSAR Requests",       icon: FileSearch      },
  { id: "calendar",    label: "Calendar",            icon: CalendarDays    },
]

// Module accent colour — kept distinct from every other module's tab-shell
// colour (Fleet #1B3A6B, Security #0D9488, HR #9D174D, ...) so the
// Legal/Compliance module reads as its own thing at a glance.
const ACCENT = "#4338CA"

export function LegalCompliancePage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Scale size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Legal &amp; Compliance</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Regulatory obligations · Litigation register · POPIA processing activities · Data subject access requests
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

        {tab === "dashboard"   && <LegalComplianceDashboard onNavigate={setTab} />}
        {tab === "obligations" && <ObligationsTab />}
        {tab === "litigation"  && <LitigationTab />}
        {tab === "popia"       && <PopiaTab />}
        {tab === "dsar"        && <DsarTab />}
        {tab === "calendar"    && <CalendarTab />}
      </div>
    </div>
  )
}
