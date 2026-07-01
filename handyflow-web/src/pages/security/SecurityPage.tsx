// src/pages/security/SecurityPage.tsx
import { useState } from "react"
import {
  Shield, MapPin, Clock, AlertTriangle, LayoutDashboard, Radio,
  Crosshair, Siren, Camera, FileBarChart, Tablet, Lock, Users,
  DollarSign, GitBranch, Key,
} from "lucide-react"
import SecurityDashboard  from "./SecurityDashboard"
import GuardsTab          from "./GuardsTab"
import SitesTab           from "./SitesTab"
import ShiftsTab          from "./ShiftsTab"
import IncidentsTab       from "./IncidentsTab"
import LiveMapTab         from "./LiveMapTab"
import ArmouryTab         from "./ArmouryTab"
import ControlRoomTab     from "./ControlRoomTab"
import DeviceSessionsTab  from "./DeviceSessionsTab"
import CctvTab            from "./CctvTab"
import ReportsTab         from "./ReportsTab"
import CloseProtectionTab from "./CloseProtectionTab"
import PayrollTab         from "./PayrollTab"
import BranchesTab        from "./BranchesTab"
import PublicApiTab       from "./PublicApiTab"

type Module  = "ops" | "cp" | "admin"
type OpsTab  = "dashboard" | "guards" | "sites" | "shifts" | "incidents" | "live" | "control-room" | "armoury" | "cctv" | "sessions" | "reports"
type CpTab   = "cp-overview"
type ATab    = OpsTab | CpTab | "admin-overview" | "payroll" | "branches" | "public-api"

const OPS_TABS = [
  { id: "dashboard",    label: "Dashboard",    icon: LayoutDashboard },
  { id: "guards",       label: "Guards",       icon: Shield },
  { id: "sites",        label: "Sites",        icon: MapPin },
  { id: "shifts",       label: "Shifts",       icon: Clock },
  { id: "incidents",    label: "Incidents",    icon: AlertTriangle },
  { id: "control-room", label: "Control Room", icon: Siren,       badge: "LIVE" },
  { id: "armoury",      label: "Armoury",      icon: Crosshair },
  { id: "cctv",         label: "CCTV",         icon: Camera },
  { id: "sessions",     label: "Sessions",     icon: Tablet },
  { id: "live",         label: "Live Map",     icon: Radio },
  { id: "reports",      label: "Reports",      icon: FileBarChart },
] as const

const CP_TABS = [
  { id: "cp-overview",  label: "Close Protection", icon: Lock },
] as const

const ADMIN_TABS = [
  { id: "admin-overview", label: "Overview",   icon: Users },
  { id: "payroll",        label: "Payroll",     icon: DollarSign },
  { id: "branches",       label: "Branches",    icon: GitBranch },
  { id: "public-api",     label: "Public API",  icon: Key },
] as const

const MODULES = [
  { id: "ops"   as Module, label: "Operations",       accent: "#0D9488" },
  { id: "cp"    as Module, label: "Close Protection", accent: "#7C3AED" },
  { id: "admin" as Module, label: "Admin",            accent: "#2563EB" },
]

export function SecurityPage() {
  const [mod,  setMod]  = useState<Module>("ops")
  const [tab,  setTab]  = useState<ATab>("dashboard")

  const accent = MODULES.find(m => m.id === mod)!.accent

  function switchMod(m: Module) {
    setMod(m)
    setTab(m === "ops" ? "dashboard" : m === "cp" ? "cp-overview" : "admin-overview")
  }

  const tabs = mod === "ops" ? OPS_TABS : mod === "cp" ? CP_TABS : ADMIN_TABS

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Shield size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Security Operations</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Guard management · Checkpoint patrols · Control room · Close protection
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, overflow: "hidden" }}>
        {/* Module switcher */}
        <div style={{ display: "flex", borderBottom: "1px solid #E2E8F0", background: "#F8FAFC" }}>
          {MODULES.map(m => (
            <button key={m.id} onClick={() => switchMod(m.id)} style={{
              padding: "11px 22px", background: mod === m.id ? "#fff" : "none", border: "none",
              borderBottom: `2px solid ${mod === m.id ? m.accent : "transparent"}`,
              borderRight: "1px solid #E2E8F0",
              color: mod === m.id ? m.accent : "#64748B",
              fontWeight: mod === m.id ? 700 : 400, fontSize: 11, cursor: "pointer",
              letterSpacing: "0.06em", textTransform: "uppercase" as const, marginBottom: -1,
            }}>
              {m.label}
            </button>
          ))}
        </div>

        {/* Tab bar */}
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", padding: "0 24px", overflowX: "auto" as const, background: "#fff" }}>
          {tabs.map((t: any) => {
            const Icon = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id as ATab)} style={{
                display: "flex", alignItems: "center", gap: 6, whiteSpace: "nowrap" as const,
                padding: "10px 14px", background: "none", border: "none",
                borderBottom: `2px solid ${active ? accent : "transparent"}`,
                color: active ? accent : "#64748B", fontWeight: active ? 600 : 400,
                fontSize: 12, cursor: "pointer", marginBottom: -1,
              }}>
                <Icon size={13} />
                {t.label}
                {t.badge && (
                  <span style={{ fontSize: 9, fontWeight: 700, background: "#DC2626", color: "#fff", padding: "1px 5px", borderRadius: 4 }}>
                    {t.badge}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        {/* Content */}
        <div style={{ padding: 24 }}>
          {tab === "dashboard"     && <SecurityDashboard onNavigate={setTab as any} />}
          {tab === "guards"        && <GuardsTab />}
          {tab === "sites"         && <SitesTab />}
          {tab === "shifts"        && <ShiftsTab />}
          {tab === "incidents"     && <IncidentsTab />}
          {tab === "live"          && <LiveMapTab />}
          {tab === "control-room"  && <ControlRoomTab />}
          {tab === "armoury"       && <ArmouryTab />}
          {tab === "cctv"          && <CctvTab />}
          {tab === "sessions"      && <DeviceSessionsTab />}
          {tab === "reports"       && <ReportsTab />}
          {tab === "cp-overview"   && <CloseProtectionTab />}
          {tab === "admin-overview" && (
            <div style={{ textAlign: "center", padding: "60px 0", color: "#94A3B8" }}>
              <Users size={32} strokeWidth={1.5} style={{ margin: "0 auto 12px", display: "block" }} />
              <p style={{ margin: 0, fontWeight: 500, color: "#374151" }}>Select a section from the tabs above</p>
              <p style={{ margin: "4px 0 0", fontSize: 13 }}>Payroll · Branches · Public API & Webhooks</p>
            </div>
          )}
          {tab === "payroll"    && <PayrollTab />}
          {tab === "branches"   && <BranchesTab />}
          {tab === "public-api" && <PublicApiTab />}
        </div>
      </div>
    </div>
  )
}
