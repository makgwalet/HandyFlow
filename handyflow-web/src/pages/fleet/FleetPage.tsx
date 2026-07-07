// src/pages/fleet/FleetPage.tsx
import { useState } from "react"
import { Car, Route, Wrench, LayoutDashboard, Fuel, Shield, Users } from "lucide-react"
import FleetDashboard from "./FleetDashboard"
import VehiclesTab    from "./VehiclesTab"
import TripsTab       from "./TripsTab"
import ServicesTab    from "./ServicesTab"
import FuelTab        from "./FuelTab"
import ComplianceTab  from "./ComplianceTab"
import DriversTab     from "./DriversTab"

type Tab = "dashboard" | "vehicles" | "trips" | "services" | "fuel" | "compliance" | "drivers"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",  label: "Dashboard",      icon: LayoutDashboard },
  { id: "vehicles",   label: "Vehicles",        icon: Car             },
  { id: "drivers",    label: "Drivers",         icon: Users            },
  { id: "trips",      label: "Logbook",         icon: Route           },
  { id: "services",   label: "Service History", icon: Wrench          },
  { id: "fuel",       label: "Fuel Log",        icon: Fuel            },
  { id: "compliance", label: "Compliance",      icon: Shield          },
]

export function FleetPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Car size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Fleet Management</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Vehicle register · Drivers · Trip logbook · Service history · Fuel tracking · Licence & compliance
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
                  borderBottom: active ? "2px solid #1B3A6B" : "2px solid transparent",
                  color: active ? "#1B3A6B" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"  && <FleetDashboard onNavigate={setTab} />}
        {tab === "vehicles"   && <VehiclesTab />}
        {tab === "drivers"    && <DriversTab />}
        {tab === "trips"      && <TripsTab />}
        {tab === "services"   && <ServicesTab />}
        {tab === "fuel"       && <FuelTab />}
        {tab === "compliance" && <ComplianceTab />}
      </div>
    </div>
  )
}
