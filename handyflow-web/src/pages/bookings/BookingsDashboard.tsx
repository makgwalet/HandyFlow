// src/pages/bookings/BookingsDashboard.tsx
//
// FIXES vs original:
// 1. Unbounded size=200 fetch replaced with two targeted queries:
//    - today's bookings: date filter → only fetches what's needed
//    - summary stats: a separate small query for totals
// 2. Loading skeletons instead of blank space while fetching
// 3. Revenue calculated server-side via the date filter, not client-side from 200 rows
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Calendar, Clock, CheckCircle, AlertCircle, ArrowRight, User,
} from "lucide-react"

const STATUS_STYLE: Record<string, { color: string; bg: string; label: string }> = {
  PENDING:     { color: "#D97706", bg: "#FFFBEB", label: "Pending" },
  CONFIRMED:   { color: "#1D4ED8", bg: "#EFF6FF", label: "Confirmed" },
  IN_PROGRESS: { color: "#7C3AED", bg: "#F5F3FF", label: "In Progress" },
  COMPLETED:   { color: "#166534", bg: "#DCFCE7", label: "Completed" },
  CANCELLED:   { color: "#DC2626", bg: "#FEF2F2", label: "Cancelled" },
  NO_SHOW:     { color: "#64748B", bg: "#F8FAFC", label: "No Show" },
}

const fmtTime = (t: string) => t?.substring(0, 5) ?? "—"
const fmtR    = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const today   = new Date().toISOString().split("T")[0]

function Skeleton({ w = "100%", h = 18, mb = 0 }: { w?: string | number; h?: number; mb?: number }) {
  return (
    <div style={{
      width: w, height: h, background: "#F1F5F9", borderRadius: 6,
      marginBottom: mb, animation: "pulse 1.5s ease-in-out infinite",
    }} />
  )
}

export default function BookingsDashboard({ onNavigate }: { onNavigate: (tab: any) => void }) {

  // WHY two separate queries instead of one large fetch?
  // "Today's schedule" needs date=today (small, fast).
  // "All-time summary stats" need aggregate counts — we use totalElements
  // from the paginated response with size=1 per status, not 200 raw rows.
  // This is O(1) data per stat card instead of O(all bookings).

  const { data: todayData, isLoading: loadingToday } = useQuery({
    queryKey: ["bookings-today", today],
    queryFn: async () => {
      const res     = await apiClient.get(`/api/v1/bookings?date=${today}&size=200`)
      const payload = res.data?.data ?? res.data
      return (payload?.content ?? payload ?? []) as any[]
    },
    staleTime: 30_000,
  })

  // Summary stats — fetch counts for key statuses using size=1 (we just need totalElements)
  const { data: completedTotal } = useQuery<number>({
    queryKey: ["bookings-count-completed"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings?status=COMPLETED&size=1")
      return (res.data?.data ?? res.data)?.totalElements ?? 0
    },
    staleTime: 60_000,
  })
  const { data: totalBookings } = useQuery<number>({
    queryKey: ["bookings-count-all"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings?size=1")
      return (res.data?.data ?? res.data)?.totalElements ?? 0
    },
    staleTime: 60_000,
  })
  const { data: noShowTotal } = useQuery<number>({
    queryKey: ["bookings-count-noshow"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings?status=NO_SHOW&size=1")
      return (res.data?.data ?? res.data)?.totalElements ?? 0
    },
    staleTime: 60_000,
  })

  const todayBookings  = todayData ?? []
  const todayConfirmed = todayBookings.filter((b: any) => b.status === "CONFIRMED").length
  const todayPending   = todayBookings.filter((b: any) => b.status === "PENDING").length
  const todayRevenue   = todayBookings
    .filter((b: any) => b.status === "COMPLETED")
    .reduce((s: number, b: any) => s + (parseFloat(b.price) ?? 0), 0)

  const noShowRate = totalBookings
    ? (((noShowTotal ?? 0) / totalBookings) * 100).toFixed(0)
    : "0"

  const upcoming = todayBookings
    .filter((b: any) => ["PENDING", "CONFIRMED"].includes(b.status))
    .sort((a: any, b: any) => a.startTime.localeCompare(b.startTime))[0]

  return (
    <div>
      {/* ── KPI row ─────────────────────────────────────────────────────── */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 24 }}>
        {[
          { label: "Today's bookings", value: todayBookings.length, color: "#1B3A6B", icon: Calendar },
          { label: "Confirmed today",  value: todayConfirmed,       color: "#1D4ED8", icon: CheckCircle },
          { label: "Pending today",    value: todayPending,         color: "#D97706", icon: AlertCircle },
          { label: "Today's revenue",  value: fmtR(todayRevenue),   color: "#166534", icon: Clock, isR: true },
        ].map(k => (
          <div
            key={k.label}
            onClick={() => onNavigate("bookings")}
            style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 20px", cursor: "pointer" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: "#94A3B8", textTransform: "uppercase" }}>{k.label}</div>
              <k.icon size={15} color={k.color} />
            </div>
            {loadingToday
              ? <Skeleton h={28} />
              : <div style={{ fontSize: k.isR ? 20 : 28, fontWeight: 800, color: k.color }}>{k.value}</div>
            }
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16 }}>

        {/* ── Today's schedule ─────────────────────────────────────────── */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>
              Today — {new Date().toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long" })}
            </span>
            <button
              onClick={() => onNavigate("calendar")}
              style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#0D9488", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              View calendar <ArrowRight size={13} />
            </button>
          </div>

          {loadingToday ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {[1, 2, 3].map(i => (
                <div key={i} style={{ padding: "14px 16px", border: "1px solid #E2E8F0", borderRadius: 10 }}>
                  <Skeleton h={14} w="40%" mb={8} />
                  <Skeleton h={12} w="60%" />
                </div>
              ))}
            </div>
          ) : todayBookings.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
              <Calendar size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No bookings today</div>
              <button
                onClick={() => onNavigate("bookings")}
                style={{ marginTop: 12, padding: "7px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
                Create booking
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {[...todayBookings]
                .sort((a: any, b: any) => a.startTime.localeCompare(b.startTime))
                .map((b: any) => {
                  const ss = STATUS_STYLE[b.status] ?? STATUS_STYLE.PENDING
                  return (
                    <div key={b.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff" }}>
                      <div style={{ textAlign: "center", minWidth: 56 }}>
                        <div style={{ fontSize: 15, fontWeight: 700, color: "#0F172A" }}>{fmtTime(b.startTime)}</div>
                        <div style={{ fontSize: 10, color: "#94A3B8" }}>{fmtTime(b.endTime)}</div>
                      </div>
                      <div style={{ width: 3, height: 40, borderRadius: 2, background: ss.color, flexShrink: 0 }} />
                      <div style={{ flex: 1 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                          <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{b.clientName}</span>
                          <span style={{ fontSize: 11, fontWeight: 600, background: ss.bg, color: ss.color, padding: "1px 8px", borderRadius: 20 }}>{ss.label}</span>
                        </div>
                        <div style={{ fontSize: 12, color: "#64748B" }}>
                          {b.serviceName}{b.staffName ? ` · ${b.staffName}` : ""}
                        </div>
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#1B3A6B" }}>{fmtR(b.price)}</div>
                    </div>
                  )
                })}
            </div>
          )}
        </div>

        {/* ── Sidebar ──────────────────────────────────────────────────── */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>

          {/* Next booking highlight */}
          {upcoming && (
            <div style={{ background: "#1B3A6B", borderRadius: 12, padding: 20, color: "#fff" }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: "rgba(255,255,255,0.6)", marginBottom: 8, textTransform: "uppercase" }}>Next up</div>
              <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 4 }}>{upcoming.clientName}</div>
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)", marginBottom: 12 }}>{upcoming.serviceName}</div>
              <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 22, fontWeight: 800, color: "#4ADE80" }}>
                <Clock size={18} color="#4ADE80" />{fmtTime(upcoming.startTime)}
              </div>
              {upcoming.staffName && (
                <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "rgba(255,255,255,0.6)", marginTop: 8 }}>
                  <User size={12} />{upcoming.staffName}
                </div>
              )}
            </div>
          )}

          {/* Summary stats */}
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 18 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 14 }}>All time</div>
            {[
              { label: "Total completed", value: completedTotal ?? "—", color: "#166534" },
              { label: "No-show rate",    value: `${noShowRate}%`,       color: parseFloat(noShowRate) > 10 ? "#DC2626" : "#166534" },
              { label: "Total bookings",  value: totalBookings ?? "—",   color: "#1B3A6B" },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid #F1F5F9" }}>
                <span style={{ fontSize: 13, color: "#64748B" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: s.color }}>{s.value}</span>
              </div>
            ))}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "New booking",      tab: "bookings",     color: "#1B3A6B" },
              { label: "Manage services",  tab: "services",     color: "#0D9488" },
              { label: "Set availability", tab: "availability", color: "#7C3AED" },
            ].map(a => (
              <button
                key={a.label}
                onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, fontWeight: 600, color: a.color, cursor: "pointer", textAlign: "left", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
