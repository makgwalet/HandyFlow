// src/pages/supply-chain/SupplyChainPage.tsx
// Single-page shell — matches ClinicPage pattern exactly
import { useState } from "react"
import { Truck, LayoutDashboard, Users, ShoppingCart, Package, FileText } from "lucide-react"
import { ScmDashboard }       from "./ScmDashboard"
import { SuppliersTab }       from "./SuppliersTab"
import { PurchaseOrdersTab }  from "./PurchaseOrdersTab"
import { InventoryTab }       from "./InventoryTab"
import { InvoicesTab }        from "./InvoicesTab"

export type ScmTab = "dashboard" | "suppliers" | "purchase-orders" | "inventory" | "invoices"
const ACCENT = "#D97706"  // amber — SCM accent

const TABS: { id: ScmTab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",       label: "Dashboard",         icon: LayoutDashboard },
  { id: "suppliers",       label: "Suppliers",          icon: Users           },
  { id: "purchase-orders", label: "Purchase Orders",    icon: ShoppingCart    },
  { id: "inventory",       label: "Inventory",          icon: Package         },
  { id: "invoices",        label: "Supplier Invoices",  icon: FileText        },
]

export function SupplyChainPage() {
  const [tab, setTab] = useState<ScmTab>("dashboard")
  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Page header */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#FEF3C7",
            display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Truck size={18} color={ACCENT} />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Supply Chain</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Suppliers · Purchase orders · Inventory · Supplier invoices
        </p>
      </div>

      {/* Card */}
      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        {/* Tab bar */}
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{ display: "flex", alignItems: "center", gap: 6,
                  padding: "10px 16px", background: "none", border: "none", whiteSpace: "nowrap",
                  borderBottom: active ? `2px solid ${ACCENT}` : "2px solid transparent",
                  color: active ? ACCENT : "#64748B", fontWeight: active ? 600 : 400,
                  fontSize: 13, cursor: "pointer", marginBottom: -1 }}>
                <t.icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {/* Content */}
        {tab === "dashboard"       && <ScmDashboard      onNav={setTab} />}
        {tab === "suppliers"       && <SuppliersTab      />}
        {tab === "purchase-orders" && <PurchaseOrdersTab />}
        {tab === "inventory"       && <InventoryTab      />}
        {tab === "invoices"        && <InvoicesTab       />}
      </div>
    </div>
  )
}
