// src/pages/fuel/FuelPage.tsx
import { useState } from "react"
import { Droplets, Fuel, Truck, Users, LayoutDashboard, ArrowDownToLine, TrendingUp } from "lucide-react"
import FuelDashboard  from "./FuelDashboard"
import TanksTab       from "./TanksTab"
import ReceiptsTab    from "./ReceiptsTab"
import DispatchesTab  from "./DispatchesTab"
import DeliveriesTab  from "./DeliveriesTab"
import SuppliersTab   from "./SuppliersTab"
import MarginTab      from "./MarginTab"
import { usePermission } from "../../hooks/usePermission"

type Tab = "dashboard" | "tanks" | "receipts" | "dispatches" | "deliveries" | "suppliers" | "margin"

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
  // Client-side mirror of the server-side FUEL_MARGIN_READ gate — see
  // usePermission's own doc comment for why this doesn't replace the
  // real check, it just avoids showing a tab whose click would 403.
  const canViewMargin = usePermission("FUEL_MARGIN_READ")
  const visibleTabs = canViewMargin
    ? [...TABS, { id: "margin" as Tab, label: "Cost & Margin", icon: TrendingUp }]
    : TABS

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "var(--hf-accent)", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Droplets size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Fuel & Logistics</h1>
        </div>
        <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0, paddingLeft: 46 }}>
          Tank inventory · Stock receipts · Dispatch log · Deliveries · Reconciliation
        </p>
      </div>

      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid var(--hf-border)", marginBottom: 28, overflowX: "auto" }}>
          {visibleTabs.map(t => {
            const Icon = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 16px",
                  background: "none", border: "none", whiteSpace: "nowrap",
                  borderBottom: active ? "2px solid var(--hf-accent)" : "2px solid transparent",
                  color: active ? "var(--hf-accent-text)" : "var(--hf-text-muted)",
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
        {tab === "margin"     && canViewMargin && <MarginTab />}
      </div>
    </div>
  )
}
