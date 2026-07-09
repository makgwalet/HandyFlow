// src/pages/property/PropertyPage.tsx
import { useState } from "react"
import { Building2, LayoutGrid, FileText, CreditCard, ClipboardList, BarChart2 } from "lucide-react"
import PropertyDashboard  from "./PropertyDashboard"
import PropertiesTab      from "./PropertiesTab"
import LeasesTab          from "./LeasesTab"
import PaymentsTab        from "./PaymentsTab"
import InspectionsTab     from "./InspectionsTab"

type Tab = "dashboard" | "properties" | "leases" | "payments" | "inspections"

const TABS = [
  { id: "dashboard"   as Tab, label: "Dashboard",   icon: BarChart2      },
  { id: "properties"  as Tab, label: "Properties",  icon: Building2      },
  { id: "leases"      as Tab, label: "Leases",      icon: FileText       },
  { id: "payments"    as Tab, label: "Payments",    icon: CreditCard     },
  { id: "inspections" as Tab, label: "Inspections", icon: ClipboardList  },
]

export function PropertyPage() {
  const [tab, setTab] = useState<Tab>("dashboard")
  // NEW: carries an optional filter/lease-id handed off by the Dashboard's
  // own navigation (e.g. "Leases expiring soon" -> Leases tab pre-filtered,
  // or a specific outstanding payment -> Payments tab with that lease
  // pre-selected). Plain tab-bar clicks below always clear this — only
  // Dashboard-originated navigation should ever set it, so switching tabs
  // manually never inherits a stale filter from an earlier Dashboard click.
  const [navPayload, setNavPayload] = useState<{ leasesFilter?: string; paymentsLeaseId?: string }>({})

  const goToTab = (id: Tab) => { setTab(id); setNavPayload({}) }
  const navigateFromDashboard = (id: Tab, payload?: { leasesFilter?: string; paymentsLeaseId?: string }) => {
    setTab(id)
    setNavPayload(payload ?? {})
  }

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Building2 size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Property</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Portfolio management · Leases · Rent tracking · Inspections
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon   = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => goToTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 18px",
                  background: "none", border: "none", whiteSpace: "nowrap" as const,
                  borderBottom: active ? "2px solid #1B3A6B" : "2px solid transparent",
                  color: active ? "#1B3A6B" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 14, cursor: "pointer", marginBottom: -1,
                }}>
                <Icon size={15} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"   && <PropertyDashboard onNavigate={navigateFromDashboard} />}
        {tab === "properties"  && <PropertiesTab />}
        {tab === "leases"      && <LeasesTab initialFilter={navPayload.leasesFilter} />}
        {tab === "payments"    && <PaymentsTab initialLeaseId={navPayload.paymentsLeaseId} />}
        {tab === "inspections" && <InspectionsTab />}
      </div>
    </div>
  )
}
