// src/pages/fuel/FuelPage.tsx
import { useState } from "react"
import { Droplets, Fuel, Truck, Users, LayoutDashboard, ArrowDownToLine } from "lucide-react"
import FuelDashboard  from "./FuelDashboard"
import TanksTab       from "./TanksTab"
import ReceiptsTab    from "./ReceiptsTab"
import DispatchesTab  from "./DispatchesTab"
import DeliveriesTab  from "./DeliveriesTab"
import SuppliersTab   from "./SuppliersTab"

type Tab = "dashboard" | "tanks" | "receipts" | "dispatches" | "deliveries" | "suppliers"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",  label: "Dashboard",    icon: LayoutDashboard },
  { id: "tanks",      label: "Tanks",        icon: Droplets        },
  { id: "receipts",   label: "Stock In",     icon: ArrowDownToLine },
  { id: "dispatches", label: "Dispatches",   icon: Fuel            },
  { id: "deliveries", label: "Deliveries",   icon: Truck           },
  { id: "suppliers",  label: "Suppliers",    icon: Users           },
]

export function FuelPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#0D9488", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Droplets size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Fuel & Logistics</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Tank inventory · Stock receipts · Dispatch log · Deliveries · Reconciliation
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
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer", marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"  && <FuelDashboard onNavigate={setTab} />}
        {tab === "tanks"      && <TanksTab />}
        {tab === "receipts"   && <ReceiptsTab />}
        {tab === "dispatches" && <DispatchesTab />}
        {tab === "deliveries" && <DeliveriesTab />}
        {tab === "suppliers"  && <SuppliersTab />}
      </div>
    </div>
  )
}
