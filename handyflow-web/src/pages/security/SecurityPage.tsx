// src/pages/security/SecurityPage.tsx
import { useState } from "react"
import { Shield, MapPin, Clock, AlertTriangle, LayoutDashboard, Radio } from "lucide-react"
import SecurityDashboard from "./SecurityDashboard"
import GuardsTab        from "./GuardsTab"
import SitesTab         from "./SitesTab"
import ShiftsTab        from "./ShiftsTab"
import IncidentsTab     from "./IncidentsTab"
import LiveMapTab       from "./LiveMapTab"

type Tab = "dashboard" | "guards" | "sites" | "shifts" | "incidents" | "live"

const tabs: { id: Tab; label: string; icon: React.ElementType; badge?: string }[] = [
  { id: "dashboard", label: "Dashboard",  icon: LayoutDashboard },
  { id: "guards",    label: "Guards",     icon: Shield },
  { id: "sites",     label: "Sites",      icon: MapPin },
  { id: "shifts",    label: "Shifts",     icon: Clock },
  { id: "incidents", label: "Incidents",  icon: AlertTriangle },
  { id: "live",      label: "Live",       icon: Radio, badge: "LIVE" },
]

export function SecurityPage() {
  const [activeTab, setActiveTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Shield size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>
            Security Operations
          </h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Guard management · Site deployments · QR/NFC checkpoint patrols · Incident reporting
        </p>
      </div>

      {/* Main card */}
      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>

        {/* Tab bar */}
        <div style={{
          display: "flex", gap: 2,
          borderBottom: "1px solid #E2E8F0",
          marginBottom: 28, paddingBottom: 0,
          overflowX: "auto",
        }}>
          {tabs.map(tab => {
            const Icon   = tab.icon
            const active = activeTab === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6,
                  whiteSpace: "nowrap" as const,
                  padding: "10px 16px",
                  background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400,
                  fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                  transition: "color 0.15s, border-color 0.15s",
                }}>
                <Icon size={14} />
                {tab.label}
                {tab.badge && (
                  <span style={{
                    fontSize: 9, fontWeight: 700,
                    background: "#DC2626", color: "#fff",
                    padding: "1px 5px", borderRadius: 4,
                  }}>
                    {tab.badge}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        {/* Tab content */}
        {activeTab === "dashboard" && <SecurityDashboard onNavigate={setActiveTab} />}
        {activeTab === "guards"    && <GuardsTab />}
        {activeTab === "sites"     && <SitesTab />}
        {activeTab === "shifts"    && <ShiftsTab />}
        {activeTab === "incidents" && <IncidentsTab />}
        {activeTab === "live"      && <LiveMapTab />}
      </div>
    </div>
  )
}
