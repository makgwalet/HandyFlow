import { useState } from "react"
import { Fuel, Truck, Package, Users } from "lucide-react"
import TanksTab from "./TanksTab"
import DispatchesTab from "./DispatchesTab"
import DeliveriesTab from "./DeliveriesTab"
import SuppliersTab from "./SuppliersTab"

type Tab = "tanks" | "dispatches" | "deliveries" | "suppliers"

const tabs = [
  { id: "tanks"      as Tab, label: "Tanks",      icon: Package },
  { id: "dispatches" as Tab, label: "Dispatches",  icon: Fuel    },
  { id: "deliveries" as Tab, label: "Deliveries",  icon: Truck   },
  { id: "suppliers"  as Tab, label: "Suppliers",   icon: Users   },
]

export function FuelPage() {
  const [activeTab, setActiveTab] = useState<Tab>("tanks")

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>
          Fuel & Logistics
        </h1>
        <p style={{ fontSize: 14, color: "#64748B", margin: 0 }}>
          Tank inventory, fuel dispatch, deliveries and supplier management
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: 24 }}>
        {/* Tab bar */}
        <div style={{
          display: "flex", gap: 4,
          borderBottom: "1px solid #E2E8F0",
          marginBottom: 24, paddingBottom: 0,
        }}>
          {tabs.map(tab => {
            const Icon = tab.icon
            const active = activeTab === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 7,
                  padding: "10px 18px",
                  background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400,
                  fontSize: 14, cursor: "pointer",
                  marginBottom: -1, transition: "all 0.15s",
                }}
              >
                <Icon size={15} />
                {tab.label}
              </button>
            )
          })}
        </div>

        {activeTab === "tanks"      && <TanksTab />}
        {activeTab === "dispatches" && <DispatchesTab />}
        {activeTab === "deliveries" && <DeliveriesTab />}
        {activeTab === "suppliers"  && <SuppliersTab />}
      </div>
    </div>
  )
}
