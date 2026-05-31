// src/pages/bookings/BookingsPage.tsx
import { useState } from "react"
import {
  Calendar, Briefcase, Users, LayoutDashboard,
  Clock, Settings,
} from "lucide-react"
import BookingsDashboard  from "./BookingsDashboard"
import BookingsTab        from "./BookingsTab"
import CalendarTab        from "./CalendarTab"
import ServicesTab        from "./ServicesTab"
import StaffTab           from "./StaffTab"
import AvailabilityTab    from "./AvailabilityTab"

type Tab = "dashboard" | "calendar" | "bookings" | "services" | "staff" | "availability"

const tabs: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",    label: "Dashboard",    icon: LayoutDashboard },
  { id: "calendar",     label: "Calendar",     icon: Calendar },
  { id: "bookings",     label: "Bookings",     icon: Clock },
  { id: "services",     label: "Services",     icon: Briefcase },
  { id: "staff",        label: "Staff",        icon: Users },
  { id: "availability", label: "Availability", icon: Settings },
]

export function BookingsPage() {
  const [activeTab, setActiveTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>
          Bookings & Appointments
        </h1>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>
          Schedule appointments, manage staff and track your calendar
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, paddingBottom: 0, overflowX: "auto" }}>
          {tabs.map(tab => {
            const Icon   = tab.icon
            const active = activeTab === tab.id
            return (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 7, whiteSpace: "nowrap",
                  padding: "10px 16px", background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1, transition: "all 0.15s",
                }}>
                <Icon size={14} />{tab.label}
              </button>
            )
          })}
        </div>

        {activeTab === "dashboard"    && <BookingsDashboard onNavigate={setActiveTab} />}
        {activeTab === "calendar"     && <CalendarTab />}
        {activeTab === "bookings"     && <BookingsTab />}
        {activeTab === "services"     && <ServicesTab />}
        {activeTab === "staff"        && <StaffTab />}
        {activeTab === "availability" && <AvailabilityTab />}
      </div>
    </div>
  )
}