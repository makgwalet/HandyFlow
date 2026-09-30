// src/pages/events/EventsPage.tsx
//
// Sections are routes (/events/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts and components/shell/SectionedModulePage).
// The event picked in the list is shared by Guests, Vendors and Analytics.
// It lives here, and this component stays mounted while the section changes,
// so the selection survives moving between sections (it is reset by leaving
// the module or reloading, as before). It is shown under the page title, where
// the old tab chips used to show it.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import EventsListTab   from "./EventsListTab"
import GuestsTab       from "./GuestsTab"
import VendorsTab      from "./VendorsTab"
import EventAnalytics  from "./EventAnalytics"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { EVENTS_SECTIONS } from "../../navigation/moduleSections"

export function EventsPage() {
  const [selectedEventId, setSelectedEventId]       = useState<string | null>(null)
  const [selectedEventTitle, setSelectedEventTitle] = useState("")

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

  const banner = (
    <div style={{ display: "flex", gap: 12, marginBottom: 22, flexWrap: "wrap" }}>
      {[
        { label: "Total events",   value: total,     color: "var(--hf-sky-text)", bg: "var(--hf-sky-soft)" },
        { label: "Live now",       value: live,      color: live > 0 ? "var(--hf-danger-text)" : "var(--hf-text-muted)", bg: live > 0 ? "var(--hf-danger-soft)" : "var(--hf-surface-muted)" },
        { label: "Open for reg",   value: published, color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
        { label: "Upcoming",       value: upcoming,  color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
      ].map(k => (
        <div key={k.label} style={{ background: k.bg, borderRadius: 10, padding: "12px 18px", minWidth: 130 }}>
          <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
          <div style={{ fontSize: 11, color: k.color, marginTop: 2, opacity: 0.8 }}>{k.label}</div>
        </div>
      ))}
    </div>
  )

  return (
    <SectionedModulePage config={EVENTS_SECTIONS} banner={banner}
      subtitle={selectedEventId
        ? `Selected event: ${selectedEventTitle}`
        : "Ticketing · QR check-in · Vendor coordination · Analytics"}
      render={(id, goTo) => {
        switch (id) {
          case "events":
            return <EventsListTab onSelectEvent={(eid: string, title: string) => {
              setSelectedEventId(eid); setSelectedEventTitle(title); goTo("guests")
            }} />
          case "guests":    return <GuestsTab eventId={selectedEventId} eventTitle={selectedEventTitle} onChangeEvent={() => goTo("events")} />
          case "vendors":   return <VendorsTab eventId={selectedEventId} eventTitle={selectedEventTitle} onChangeEvent={() => goTo("events")} />
          case "analytics": return <EventAnalytics eventId={selectedEventId} />
          default:          return null
        }
      }} />
  )
}
