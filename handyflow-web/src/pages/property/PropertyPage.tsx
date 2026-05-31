import { useState } from "react"
import { Building2, Home, FileText, CreditCard, ClipboardCheck } from "lucide-react"
import PropertiesTab from "./PropertiesTab"
import LeasesTab from "./LeasesTab"
import PaymentsTab from "./PaymentsTab"
import InspectionsTab from "./InspectionsTab"

type Tab = "properties" | "leases" | "payments" | "inspections"

const tabs = [
  { id: "properties"  as Tab, label: "Properties",  icon: Building2      },
  { id: "leases"      as Tab, label: "Leases",       icon: FileText       },
  { id: "payments"    as Tab, label: "Payments",     icon: CreditCard     },
  { id: "inspections" as Tab, label: "Inspections",  icon: ClipboardCheck },
]

export function PropertyPage() {
  const [activeTab, setActiveTab] = useState<Tab>("properties")

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>
          Property Management
        </h1>
        <p style={{ fontSize: 14, color: "#64748B", margin: 0 }}>
          Properties, units, leases, rent collection and inspections
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

        {activeTab === "properties"  && <PropertiesTab />}
        {activeTab === "leases"      && <LeasesTab />}
        {activeTab === "payments"    && <PaymentsTab />}
        {activeTab === "inspections" && <InspectionsTab />}
      </div>
    </div>
  )
}
