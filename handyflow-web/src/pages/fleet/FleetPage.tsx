import { useState } from "react"
import { Car, Route, Wrench } from "lucide-react"
import VehiclesTab from "./VehiclesTab"
import TripsTab from "./TripsTab"
import ServicesTab from "./ServicesTab"

type Tab = "vehicles" | "trips" | "services"

const tabs = [
  { id: "vehicles" as Tab, label: "Vehicles",        icon: Car   },
  { id: "trips"    as Tab, label: "Trip Log",         icon: Route },
  { id: "services" as Tab, label: "Service History",  icon: Wrench},
]

export function FleetPage() {
  const [activeTab, setActiveTab] = useState<Tab>("vehicles")

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>
          Fleet Management
        </h1>
        <p style={{ fontSize: 14, color: "#64748B", margin: 0 }}>
          Vehicle register, trip log and service history
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: 24 }}>
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
                  padding: "10px 18px", background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400,
                  fontSize: 14, cursor: "pointer",
                  marginBottom: -1, transition: "all 0.15s",
                }}
              >
                <Icon size={15} />{tab.label}
              </button>
            )
          })}
        </div>

        {activeTab === "vehicles" && <VehiclesTab />}
        {activeTab === "trips"    && <TripsTab />}
        {activeTab === "services" && <ServicesTab />}
      </div>
    </div>
  )
}
