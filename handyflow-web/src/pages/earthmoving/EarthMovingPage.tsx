// src/pages/earthmoving/EarthMovingPage.tsx
import { useState } from "react"
import { Truck, Wrench, Users, LayoutDashboard, AlertTriangle, MapPin } from "lucide-react"
import EarthDashboard   from "./EarthDashboard"
import AssetsTab        from "./AssetsTab"
import MaintenanceTab   from "./MaintenanceTab"
import OperatorLogsTab  from "./OperatorLogsTab"
import DeploymentsTab   from "./DeploymentsTab"
import IncidentsTab     from "./IncidentsTab"

type Tab = "dashboard" | "assets" | "maintenance" | "operators" | "deployments" | "incidents"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",   label: "Dashboard",    icon: LayoutDashboard },
  { id: "assets",      label: "Fleet",        icon: Truck },
  { id: "deployments", label: "Deployments",  icon: MapPin },
  { id: "maintenance", label: "Maintenance",  icon: Wrench },
  { id: "operators",   label: "Operator Logs",icon: Users },
  { id: "incidents",   label: "Incidents",    icon: AlertTriangle },
]

export function EarthMovingPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "var(--hf-warning)", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Truck size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Earthmoving</h1>
        </div>
        <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0, paddingLeft: 46 }}>
          Fleet management · Deployment tracking · Maintenance scheduling · Operator logs · Incident reporting
        </p>
      </div>

      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid var(--hf-border)", marginBottom: 28, paddingBottom: 0, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 16px",
                  background: "none", border: "none", whiteSpace: "nowrap",
                  borderBottom: active ? "2px solid var(--hf-warning)" : "2px solid transparent",
                  color: active ? "var(--hf-warning-text)" : "var(--hf-text-muted)",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"   && <EarthDashboard onNavigate={setTab} />}
        {tab === "assets"      && <AssetsTab />}
        {tab === "deployments" && <DeploymentsTab />}
        {tab === "maintenance" && <MaintenanceTab />}
        {tab === "operators"   && <OperatorLogsTab />}
        {tab === "incidents"   && <IncidentsTab />}
      </div>
    </div>
  )
}
