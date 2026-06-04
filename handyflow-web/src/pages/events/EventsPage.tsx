// src/pages/events/EventsPage.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Calendar, Users, Truck, BarChart2, PartyPopper,
} from "lucide-react"
import EventsListTab   from "./EventsListTab"
import GuestsTab       from "./GuestsTab"
import VendorsTab      from "./VendorsTab"
import EventAnalytics  from "./EventAnalytics"

type Tab = "events" | "guests" | "vendors" | "analytics"

const TABS = [
  { id: "events"    as Tab, label: "Events",    icon: Calendar   },
  { id: "guests"    as Tab, label: "Guests",    icon: Users      },
  { id: "vendors"   as Tab, label: "Vendors",   icon: Truck      },
  { id: "analytics" as Tab, label: "Analytics", icon: BarChart2  },
]

export function EventsPage() {
  const [activeTab, setActiveTab]             = useState<Tab>("events")
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)
  const [selectedEventTitle, setSelectedEventTitle] = useState("")

  const selectEvent = (id: string, title: string) => {
    setSelectedEventId(id)
    setSelectedEventTitle(title)
    setActiveTab("guests")
  }

  // KPI strip — across all events
  const { data: allEvents = [] } = useQuery<any[]>({
    queryKey: ["events-kpi"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/events?size=200")
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const live      = (allEvents as any[]).filter(e => e.status === "LIVE").length
  const published = (allEvents as any[]).filter(e => e.status === "PUBLISHED").length
  const upcoming  = (allEvents as any[]).filter(e =>
    ["DRAFT","PUBLISHED","LIVE"].includes(e.status)).length
  const total     = (allEvents as any[]).length

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#0284C7", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <PartyPopper size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Events</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Ticketing · QR check-in · Vendor coordination · Analytics
        </p>
      </div>

      {/* KPI strip */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22, flexWrap: "wrap" }}>
        {[
          { label: "Total events",   value: total,     color: "#0284C7", bg: "#F0F9FF" },
          { label: "Live now",       value: live,      color: live > 0 ? "#DC2626" : "#64748B",      bg: live > 0 ? "#FEF2F2" : "#F8FAFC" },
          { label: "Open for reg",   value: published, color: "#166534", bg: "#DCFCE7" },
          { label: "Upcoming",       value: upcoming,  color: "#D97706", bg: "#FFFBEB" },
        ].map(k => (
          <div key={k.label} style={{ background: k.bg, borderRadius: 10, padding: "12px 18px", minWidth: 130 }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
            <div style={{ fontSize: 11, color: k.color, marginTop: 2, opacity: 0.8 }}>{k.label}</div>
          </div>
        ))}
      </div>

      {/* Main card */}
      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(tab => {
            const Icon   = tab.icon
            const active = activeTab === tab.id
            return (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6,
                  padding: "10px 18px", background: "none", border: "none",
                  whiteSpace: "nowrap" as const,
                  borderBottom: active ? "2px solid #0284C7" : "2px solid transparent",
                  color: active ? "#0284C7" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 14, cursor: "pointer", marginBottom: -1,
                }}>
                <Icon size={15} />{tab.label}
                {tab.id !== "events" && tab.id !== "analytics" && selectedEventId && (
                  <span style={{ background: "#E0F2FE", color: "#0284C7", fontSize: 10, padding: "1px 7px", borderRadius: 10, fontWeight: 700 }}>
                    {selectedEventTitle.length > 14 ? selectedEventTitle.slice(0, 14) + "…" : selectedEventTitle}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        {activeTab === "events"    && <EventsListTab onSelectEvent={selectEvent} />}
        {activeTab === "guests"    && <GuestsTab eventId={selectedEventId} eventTitle={selectedEventTitle} onChangeEvent={() => setActiveTab("events")} />}
        {activeTab === "vendors"   && <VendorsTab eventId={selectedEventId} eventTitle={selectedEventTitle} onChangeEvent={() => setActiveTab("events")} />}
        {activeTab === "analytics" && <EventAnalytics eventId={selectedEventId} />}
      </div>
    </div>
  )
}
