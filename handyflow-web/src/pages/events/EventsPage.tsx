import { useState } from "react"
import { Calendar, Users, ShoppingBag, BarChart2 } from "lucide-react"
import EventsListTab from "./EventsListTab"
import GuestsTab from "./GuestsTab"
import VendorsTab from "./VendorsTab"

type Tab = "events" | "guests" | "vendors"

const tabs = [
  { id: "events"  as Tab, label: "Events",  icon: Calendar },
  { id: "guests"  as Tab, label: "Guests",  icon: Users },
  { id: "vendors" as Tab, label: "Vendors", icon: ShoppingBag },
]

export function EventsPage() {
  const [activeTab, setActiveTab] = useState<Tab>("events")
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)
  const [selectedEventTitle, setSelectedEventTitle] = useState("")

  const selectEvent = (id: string, title: string) => {
    setSelectedEventId(id)
    setSelectedEventTitle(title)
    setActiveTab("guests")
  }

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>Events</h1>
        <p style={{ fontSize: 14, color: "#64748B", margin: 0 }}>
          Event management, ticketing, QR check-in and vendor coordination
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: 24 }}>
        <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 24, paddingBottom: 0 }}>
          {tabs.map(tab => {
            const Icon = tab.icon
            const active = activeTab === tab.id
            return (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 7,
                  padding: "10px 18px", background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 14, cursor: "pointer", marginBottom: -1,
                }}>
                <Icon size={15} />{tab.label}
                {tab.id !== "events" && selectedEventId && (
                  <span style={{ background: "#F0FDF4", color: "#0D9488", fontSize: 10, padding: "1px 6px", borderRadius: 10 }}>
                    {selectedEventTitle.length > 12 ? selectedEventTitle.slice(0, 12) + "…" : selectedEventTitle}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        {activeTab === "events"  && <EventsListTab onSelectEvent={selectEvent} />}
        {activeTab === "guests"  && <GuestsTab eventId={selectedEventId} />}
        {activeTab === "vendors" && <VendorsTab eventId={selectedEventId} />}
      </div>
    </div>
  )
}
