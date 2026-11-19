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
import { LayoutDashboard, ClipboardList, Gavel, Lock, FileSearch, CalendarDays, Scale, BookMarked, ListChecks, GitCompare, ShieldAlert, ShieldCheck, FlaskConical, Flag, ClipboardCheck, Wrench, ScrollText, FileSignature, Siren, ShieldHalf, Landmark, Building2, BadgeCheck, Gauge, Users, HeartHandshake, FileBarChart, Handshake, LifeBuoy, Leaf } from "lucide-react"
import LegalComplianceDashboard from "./LegalComplianceDashboard"
import ObligationsTab from "./ObligationsTab"
import RegulatoryRegisterTab from "./RegulatoryRegisterTab"
import ApplicabilityTab from "./ApplicabilityTab"
import RegulatoryChangesTab from "./RegulatoryChangesTab"
import RiskRegisterTab from "./RiskRegisterTab"
import ControlsTab from "./ControlsTab"
import ControlTestsTab from "./ControlTestsTab"
import FindingsTab from "./FindingsTab"
import AuditsTab from "./AuditsTab"
import CapaTab from "./CapaTab"
import PoliciesTab from "./PoliciesTab"
import AttestationsTab from "./AttestationsTab"
import IncidentsTab from "./IncidentsTab"
import PrivacyTab from "./PrivacyTab"
import FicaTab from "./FicaTab"
import ThirdPartiesTab from "./ThirdPartiesTab"
import LicencesTab from "./LicencesTab"
import CommitteesTab from "./CommitteesTab"
import EthicsTab from "./EthicsTab"
import ExecutiveTab from "./ExecutiveTab"
import ReportsTab from "./ReportsTab"
import ProvidersTab from "./ProvidersTab"
import ContinuityTab from "./ContinuityTab"
import EsgTab from "./EsgTab"
import { usePermission } from "../../hooks/usePermission"
import LitigationTab from "./LitigationTab"
import PopiaTab from "./PopiaTab"
import DsarTab from "./DsarTab"
import CalendarTab from "./CalendarTab"

type Tab = "dashboard" | "executive" | "reports" | "providers" | "continuity" | "esg" | "licences" | "committees" | "ethics" | "regulatory" | "applicability" | "changes" | "risks" | "controls" | "testing" | "findings" | "audits" | "capa" | "policies" | "attestations" | "incidents" | "privacy" | "fica" | "thirdparties" | "obligations" | "litigation" | "popia" | "dsar" | "calendar"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",   label: "Dashboard",           icon: LayoutDashboard },
  { id: "executive",   label: "Executive Overview",  icon: Gauge           },
  { id: "reports",      label: "Reports",             icon: FileBarChart    },
  { id: "providers",    label: "Clients & Providers", icon: Handshake       },
  { id: "regulatory",   label: "Regulatory Register", icon: BookMarked      },
  { id: "applicability",label: "Applicability",       icon: ListChecks      },
  { id: "changes",      label: "Regulatory Changes",  icon: GitCompare      },
  { id: "risks",        label: "Risk Register",       icon: ShieldAlert     },
  { id: "controls",    label: "Controls",            icon: ShieldCheck     },
  { id: "testing",      label: "Control Testing",     icon: FlaskConical    },
  { id: "findings",     label: "Findings",            icon: Flag            },
  { id: "audits",       label: "Audits",              icon: ClipboardCheck  },
  { id: "capa",         label: "Corrective Actions",  icon: Wrench          },
  { id: "policies",     label: "Policies",            icon: ScrollText      },
  { id: "attestations", label: "Attestations",        icon: FileSignature   },
  { id: "incidents",    label: "Incidents",           icon: Siren           },
  { id: "privacy",      label: "Privacy & PAIA",      icon: ShieldHalf      },
  { id: "thirdparties", label: "Third Parties",       icon: Building2       },
  { id: "continuity",   label: "Business Continuity", icon: LifeBuoy        },
  { id: "esg",          label: "ESG",                 icon: Leaf            },
  { id: "licences",     label: "Licences & Permits",  icon: BadgeCheck      },
  { id: "committees",   label: "Committees",          icon: Users           },
  { id: "ethics",       label: "Conflicts & Ethics",  icon: HeartHandshake  },
  { id: "fica",         label: "FICA",                icon: Landmark        },
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
  // FICA is restricted (escalations must not be visible to people who do not need them) and applies only to accountable institutions.
  const hasFica = usePermission("LEGALCOMPLIANCE_FICA_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  // Provider tools are for consultancies (and for administrators, who also manage which providers may see their own organisation).
  const hasProviderManage = usePermission("LEGALCOMPLIANCE_PROVIDER_MANAGE")
  const hasProviderConsult = usePermission("LEGALCOMPLIANCE_PROVIDER_CONSULT")
  const showProviders = hasProviderManage || hasProviderConsult || hasAdmin
  const tabs = TABS.filter(t => (t.id !== "fica" || hasFica || hasAdmin) && (t.id !== "providers" || showProviders))

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
          Regulatory library · Risks and controls · Audits and corrective actions · Policies and attestations · Incidents · Privacy and PAIA · Third parties · Licences · Committees · Conflicts and ethics · FICA · Executive overview · Reports · Clients and providers · Business continuity · ESG
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {tabs.map(t => {
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
        {tab === "executive"   && <ExecutiveTab onNavigate={t => setTab(t as Tab)} />}
        {tab === "reports"      && <ReportsTab />}
        {tab === "providers"    && showProviders && <ProvidersTab />}
        {tab === "continuity"   && <ContinuityTab />}
        {tab === "esg"          && <EsgTab />}
        {tab === "regulatory"   && <RegulatoryRegisterTab />}
        {tab === "applicability" && <ApplicabilityTab />}
        {tab === "changes"      && <RegulatoryChangesTab />}
        {tab === "risks"        && <RiskRegisterTab />}
        {tab === "controls"     && <ControlsTab />}
        {tab === "testing"      && <ControlTestsTab />}
        {tab === "findings"     && <FindingsTab />}
        {tab === "audits"       && <AuditsTab />}
        {tab === "capa"         && <CapaTab />}
        {tab === "policies"     && <PoliciesTab />}
        {tab === "attestations" && <AttestationsTab />}
        {tab === "incidents"    && <IncidentsTab />}
        {tab === "privacy"      && <PrivacyTab />}
        {tab === "thirdparties" && <ThirdPartiesTab />}
        {tab === "licences"     && <LicencesTab />}
        {tab === "committees"   && <CommitteesTab />}
        {tab === "ethics"       && <EthicsTab />}
        {tab === "fica"         && (hasFica || hasAdmin) && <FicaTab />}
        {tab === "obligations" && <ObligationsTab />}
        {tab === "litigation"  && <LitigationTab />}
        {tab === "popia"       && <PopiaTab />}
        {tab === "dsar"        && <DsarTab />}
        {tab === "calendar"    && <CalendarTab />}
      </div>
    </div>
  )
}
