// src/pages/warehousing/WarehousingPage.tsx
//
// Thin tab-shell, same shape as DebtCollectionPage.tsx (the reference
// file for this whole build session): useState<Tab>, inline styles,
// single accent constant (imported from ./constants, not declared here
// — see that file's own header comment for why), Lucide icons,
// delegates to per-tab components.
import { useState } from "react"
import { LayoutDashboard, Building2, MapPin, Warehouse } from "lucide-react"
import { WHSE_ACCENT } from "./constants"
import WhseDashboard from "./WhseDashboard"
import WhseClientsTab from "./WhseClientsTab"
import WhseLocationsTab from "./WhseLocationsTab"

type Tab = "dashboard" | "clients" | "locations"

const TABS: { key: Tab; label: string; icon: typeof LayoutDashboard }[] = [
  { key: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { key: "clients", label: "Clients", icon: Building2 },
  { key: "locations", label: "Locations", icon: MapPin },
]

export default function WarehousingPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "28px 24px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: 11, background: WHSE_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Warehouse size={20} color="#fff" />
          </div>
          <div>
            <h1 style={{ fontSize: 19, fontWeight: 800, color: "#0F172A", margin: 0 }}>Warehousing</h1>
            <p style={{ fontSize: 12.5, color: "#94A3B8", margin: 0 }}>3PL / public warehousing operations</p>
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
                  color: active ? WHSE_ACCENT : "#64748B",
                  borderBottom: active ? `2px solid ${WHSE_ACCENT}` : "2px solid transparent",
                  marginBottom: -1,
                }}>
                <Icon size={15} /> {t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard" && <WhseDashboard />}
        {tab === "clients" && <WhseClientsTab />}
        {tab === "locations" && <WhseLocationsTab />}
      </div>
    </div>
  )
}
