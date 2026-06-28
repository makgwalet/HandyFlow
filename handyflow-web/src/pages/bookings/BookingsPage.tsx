// src/pages/bookings/BookingsPage.tsx
//
// WHY does the tab bar fetch pending count here and not inside BookingsTab?
// The badge (pending count pill on the "Bookings" tab) must be visible even
// when the user is on the Dashboard tab.  The data must live one level up,
// at the page level, so every tab can see it without prop-drilling.
// We use a lightweight query (just the count, no full booking payloads) so
// switching tabs doesn't trigger a full reload.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import {
  Calendar, Briefcase, Users, LayoutDashboard,
  Clock, Settings,
} from "lucide-react"
import { apiClient } from "../../api/client"
import BookingsDashboard  from "./BookingsDashboard"
import BookingsTab        from "./BookingsTab"
import CalendarTab        from "./CalendarTab"
import ServicesTab        from "./ServicesTab"
import StaffTab           from "./StaffTab"
import AvailabilityTab    from "./AvailabilityTab"

type Tab = "dashboard" | "calendar" | "bookings" | "services" | "staff" | "availability"

export function BookingsPage() {
  const [activeTab, setActiveTab] = useState<Tab>("dashboard")

  // Pending count — refetches every 60 seconds so the badge stays current
  // without hammering the server on every keystroke.
  // WHY size=1? We only need the total count from the Page<> wrapper,
  // not the booking payloads.  size=1 minimises data transfer.
  const { data: pendingCount = 0 } = useQuery<number>({
    queryKey: ["bookings-pending-count"],
    queryFn: async () => {
      const res     = await apiClient.get("/api/v1/bookings?status=PENDING&size=1")
      const payload = res.data?.data ?? res.data
      return (payload?.totalElements ?? 0) as number
    },
    refetchInterval: 60_000,
  })

  const tabs: { id: Tab; label: string; icon: React.ElementType; badge?: number }[] = [
    { id: "dashboard",    label: "Dashboard",    icon: LayoutDashboard },
    { id: "calendar",     label: "Calendar",     icon: Calendar },
    { id: "bookings",     label: "Bookings",     icon: Clock, badge: pendingCount },
    { id: "services",     label: "Services",     icon: Briefcase },
    { id: "staff",        label: "Staff",        icon: Users },
    { id: "availability", label: "Availability", icon: Settings },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>
          Bookings &amp; Appointments
        </h1>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>
          Schedule appointments, manage staff and track your calendar
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        {/* Tab bar */}
        <div style={{
          display: "flex", gap: 2,
          borderBottom: "1px solid #E2E8F0",
          marginBottom: 28, overflowX: "auto",
        }}>
          {tabs.map(tab => {
            const Icon   = tab.icon
            const active = activeTab === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 7,
                  whiteSpace: "nowrap", padding: "10px 16px",
                  background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13,
                  cursor: "pointer", marginBottom: -1, transition: "all 0.15s",
                }}>
                <Icon size={14} />
                {tab.label}
                {/* Pending badge — only show when there are pending bookings
                    and the user is NOT already on the Bookings tab
                    (no need to remind them what they're already looking at) */}
                {tab.badge != null && tab.badge > 0 && activeTab !== "bookings" && (
                  <span style={{
                    background: "#DC2626", color: "#fff",
                    fontSize: 10, fontWeight: 700,
                    padding: "1px 6px", borderRadius: 20,
                    lineHeight: 1.5, minWidth: 18, textAlign: "center",
                  }}>
                    {tab.badge > 99 ? "99+" : tab.badge}
                  </span>
                )}
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
