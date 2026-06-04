// src/pages/events/EventAnalytics.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BarChart2, Users, DollarSign, TrendingUp, Calendar, ChevronLeft } from "lucide-react"

const fmtR  = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDT = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

interface Props { eventId: string | null }

export default function EventAnalytics({ eventId }: Props) {
  const { data: events = [] } = useQuery<any[]>({
    queryKey: ["events", "ALL", "ALL"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/events?size=200")
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const { data: stats } = useQuery<any>({
    queryKey: ["event-stats", eventId],
    queryFn: async () => {
      if (!eventId) return null
      const r = await apiClient.get(`/api/v1/events/${eventId}/stats`)
      return r.data?.data ?? r.data
    },
    enabled: !!eventId,
    refetchInterval: 15000,
  })

  const { data: guests = [] } = useQuery<any[]>({
    queryKey: ["event-guests", eventId, "ALL"],
    queryFn: async () => {
      if (!eventId) return []
      const r = await apiClient.get(`/api/v1/events/${eventId}/guests?size=500`)
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
    enabled: !!eventId,
  })

  const { data: tiers = [] } = useQuery<any[]>({
    queryKey: ["event-tiers", eventId],
    queryFn: async () => {
      if (!eventId) return []
      const r = await apiClient.get(`/api/v1/events/${eventId}/tiers`)
      return r.data?.data ?? r.data ?? []
    },
    enabled: !!eventId,
  })

  // Compute analytics
  const selectedEvent = (events as any[]).find(e => e.id === eventId)
  const totalRevenue  = (guests as any[]).reduce((s: number, g: any) => s + parseFloat(g.amountPaid ?? 0), 0)
  const paidGuests    = (guests as any[]).filter(g => g.paymentStatus === "PAID").length
  const freeGuests    = (guests as any[]).filter(g => g.paymentStatus === "FREE").length
  const noShows       = (guests as any[]).filter(g => g.status === "NO_SHOW").length
  const cancelled     = (guests as any[]).filter(g => g.status === "CANCELLED").length
  const checkedIn     = (guests as any[]).filter(g => g.status === "CHECKED_IN").length
  const registered    = (guests as any[]).filter(g => !["CANCELLED","NO_SHOW"].includes(g.status)).length
  const attendanceRate = registered > 0 ? Math.round(checkedIn / registered * 100) : 0

  // Dietary breakdown
  const dietaryMap = (guests as any[]).reduce((acc: any, g: any) => {
    if (g.dietaryRequirements) acc[g.dietaryRequirements] = (acc[g.dietaryRequirements] ?? 0) + 1
    return acc
  }, {})

  // Portfolio-level stats
  const totalEvents    = (events as any[]).length
  const liveNow        = (events as any[]).filter(e => e.status === "LIVE").length
  const upcoming       = (events as any[]).filter(e => ["PUBLISHED","DRAFT"].includes(e.status)).length
  const completed      = (events as any[]).filter(e => e.status === "COMPLETED").length

  return (
    <div>
      {/* Portfolio overview — always shown */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 12 }}>Portfolio overview</div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
          {[
            { label: "Total events",  value: totalEvents, color: "#0284C7", bg: "#E0F2FE", icon: Calendar },
            { label: "Live now",      value: liveNow,     color: liveNow > 0 ? "#DC2626" : "#64748B", bg: liveNow > 0 ? "#FEF2F2" : "#F8FAFC", icon: TrendingUp },
            { label: "Upcoming",      value: upcoming,    color: "#D97706", bg: "#FFFBEB", icon: Calendar },
            { label: "Completed",     value: completed,   color: "#166534", bg: "#DCFCE7", icon: BarChart2 },
          ].map(s => {
            const Icon = s.icon
            return (
              <div key={s.label} style={{ background: s.bg, borderRadius: 10, padding: "14px 18px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 24, fontWeight: 800, color: s.color }}>{s.value}</div>
                  <div style={{ fontSize: 11, color: s.color, opacity: 0.8, marginTop: 2 }}>{s.label}</div>
                </div>
                <Icon size={22} color={s.color} style={{ opacity: 0.4 }} />
              </div>
            )
          })}
        </div>
      </div>

      {/* Event-specific analytics */}
      {!eventId ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          <BarChart2 size={36} style={{ marginBottom: 10, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select an event for detailed analytics</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Go to Events tab and click on any event to load per-event stats here.</div>
        </div>
      ) : (
        <div>
          <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 12 }}>
            {selectedEvent?.title ?? "Event analytics"}
          </div>

          {/* KPIs */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12, marginBottom: 22 }}>
            {[
              { label: "Registered",     value: registered,   color: "#0284C7", bg: "#E0F2FE" },
              { label: "Checked in",     value: checkedIn,    color: "#166534", bg: "#DCFCE7" },
              { label: "Attendance rate",value: `${attendanceRate}%`, color: attendanceRate >= 70 ? "#166534" : "#D97706", bg: attendanceRate >= 70 ? "#DCFCE7" : "#FFFBEB" },
              { label: "No-shows",       value: noShows,      color: noShows > 0 ? "#DC2626" : "#64748B", bg: noShows > 0 ? "#FEF2F2" : "#F8FAFC" },
              { label: "Cancellations",  value: cancelled,    color: "#64748B", bg: "#F8FAFC" },
              { label: "Total revenue",  value: fmtR(totalRevenue), color: "#0D9488", bg: "#F0FDF9" },
            ].map(k => (
              <div key={k.label} style={{ background: k.bg, borderRadius: 10, padding: "14px 18px" }}>
                <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
                <div style={{ fontSize: 11, color: k.color, opacity: 0.8, marginTop: 2 }}>{k.label}</div>
              </div>
            ))}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
            {/* Ticket tier breakdown */}
            {(tiers as any[]).length > 0 && (
              <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 14 }}>Ticket tier breakdown</div>
                {(tiers as any[]).map((t: any) => {
                  const pct = t.quantity > 0 ? Math.round(t.quantitySold / t.quantity * 100) : 0
                  return (
                    <div key={t.id} style={{ marginBottom: 14 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 5, fontSize: 13 }}>
                        <span style={{ fontWeight: 600, color: "#0F172A" }}>{t.name}</span>
                        <span style={{ color: "#64748B" }}>{t.quantitySold} / {t.quantity} sold</span>
                      </div>
                      <div style={{ height: 7, background: "#F1F5F9", borderRadius: 10, overflow: "hidden" }}>
                        <div style={{ height: "100%", width: `${pct}%`, background: pct >= 90 ? "#EF4444" : pct >= 60 ? "#F59E0B" : "#22C55E", borderRadius: 10, transition: "width 0.5s" }} />
                      </div>
                      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#94A3B8", marginTop: 3 }}>
                        <span>{pct}% sold · {t.available} remaining</span>
                        <span>{t.quantityCheckedIn} checked in</span>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}

            {/* Payment breakdown */}
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
              <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 14 }}>Payment breakdown</div>
              {[
                { label: "Paid tickets",     value: paidGuests, color: "#166534", pct: registered > 0 ? Math.round(paidGuests / registered * 100) : 0 },
                { label: "Free admission",   value: freeGuests, color: "#0284C7", pct: registered > 0 ? Math.round(freeGuests / registered * 100) : 0 },
              ].map(r => (
                <div key={r.label} style={{ marginBottom: 14 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 5, fontSize: 13 }}>
                    <span style={{ fontWeight: 600, color: "#0F172A" }}>{r.label}</span>
                    <span style={{ color: "#64748B" }}>{r.value} ({r.pct}%)</span>
                  </div>
                  <div style={{ height: 7, background: "#F1F5F9", borderRadius: 10, overflow: "hidden" }}>
                    <div style={{ height: "100%", width: `${r.pct}%`, background: r.color, borderRadius: 10 }} />
                  </div>
                </div>
              ))}
              {totalRevenue > 0 && (
                <div style={{ marginTop: 16, padding: "12px 14px", background: "#F0FDF9", border: "1px solid #99F6E4", borderRadius: 9, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontSize: 13, color: "#0D9488", fontWeight: 600 }}>Total revenue collected</span>
                  <span style={{ fontSize: 18, fontWeight: 800, color: "#0D9488" }}>{fmtR(totalRevenue)}</span>
                </div>
              )}
            </div>

            {/* Dietary requirements */}
            {Object.keys(dietaryMap).length > 0 && (
              <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 14 }}>Dietary requirements</div>
                {Object.entries(dietaryMap).map(([diet, count]: any) => (
                  <div key={diet} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid #F1F5F9", fontSize: 13 }}>
                    <span style={{ color: "#374151" }}>{diet}</span>
                    <span style={{ fontWeight: 700, color: "#D97706" }}>{count} guest{count > 1 ? "s" : ""}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Upcoming events list */}
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
              <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 14 }}>All events overview</div>
              {(events as any[]).slice(0, 6).map((e: any) => {
                const cfg: Record<string, string> = { LIVE: "#DC2626", PUBLISHED: "#166534", DRAFT: "#94A3B8", COMPLETED: "#0284C7", CANCELLED: "#CBD5E1", SOLD_OUT: "#D97706" }
                return (
                  <div key={e.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "7px 0", borderBottom: "1px solid #F1F5F9", fontSize: 12 }}>
                    <div>
                      <div style={{ fontWeight: 600, color: e.id === eventId ? "#0284C7" : "#0F172A" }}>{e.title}</div>
                      <div style={{ color: "#94A3B8" }}>{fmtDT(e.startDatetime)}</div>
                    </div>
                    <span style={{ background: `${cfg[e.status] ?? "#94A3B8"}18`, color: cfg[e.status] ?? "#94A3B8", padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{e.status}</span>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
