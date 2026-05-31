// src/pages/bookings/CalendarTab.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ChevronLeft, ChevronRight } from "lucide-react"

const STATUS_COLOR: Record<string, string> = {
  PENDING:     "#D97706",
  CONFIRMED:   "#1D4ED8",
  IN_PROGRESS: "#7C3AED",
  COMPLETED:   "#166534",
  CANCELLED:   "#DC2626",
  NO_SHOW:     "#64748B",
}

const HOURS = Array.from({ length: 13 }, (_, i) => i + 7) // 07:00 to 19:00

function getWeekDays(base: Date): Date[] {
  const mon = new Date(base)
  mon.setDate(base.getDate() - ((base.getDay() + 6) % 7))
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(mon); d.setDate(mon.getDate() + i); return d
  })
}

export default function CalendarTab() {
  const [baseDate, setBaseDate] = useState(new Date())
  const weekDays = getWeekDays(baseDate)
  const from = weekDays[0].toISOString().split("T")[0]
  const to   = weekDays[6].toISOString().split("T")[0]

  const { data: bookings = [] } = useQuery({
    queryKey: ["bookings-week", from],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/bookings?size=200`)
      const payload = res.data?.data ?? res.data
      const all = (payload.content ?? payload) as any[]
      return all.filter(b => b.bookingDate >= from && b.bookingDate <= to)
    },
  })

  const prevWeek = () => { const d = new Date(baseDate); d.setDate(d.getDate() - 7); setBaseDate(d) }
  const nextWeek = () => { const d = new Date(baseDate); d.setDate(d.getDate() + 7); setBaseDate(d) }
  const today    = new Date().toISOString().split("T")[0]

  const bookingsOnDay = (date: Date) => {
    const ds = date.toISOString().split("T")[0]
    return bookings.filter((b: any) => b.bookingDate === ds && b.status !== "CANCELLED")
  }

  const timeToY = (time: string): number => {
    const [h, m] = time.split(":").map(Number)
    return ((h - 7) * 60 + m) * (56 / 60) // 56px per hour
  }

  const durationToH = (mins: number) => mins * (56 / 60)

  return (
    <div>
      {/* Nav */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <button onClick={prevWeek} style={navBtn}><ChevronLeft size={18} /></button>
          <span style={{ fontSize: 15, fontWeight: 700, color: "#0F172A" }}>
            {weekDays[0].toLocaleDateString("en-ZA", { day: "numeric", month: "long" })} –
            {weekDays[6].toLocaleDateString("en-ZA", { day: "numeric", month: "long", year: "numeric" })}
          </span>
          <button onClick={nextWeek} style={navBtn}><ChevronRight size={18} /></button>
        </div>
        <button onClick={() => setBaseDate(new Date())}
          style={{ padding: "6px 14px", background: "#F1F5F9", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", color: "#374151", fontWeight: 600 }}>
          Today
        </button>
      </div>

      {/* Calendar grid */}
      <div style={{ display: "grid", gridTemplateColumns: "56px repeat(7, 1fr)", border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>

        {/* Header row */}
        <div style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }} />
        {weekDays.map((day, i) => {
          const ds      = day.toISOString().split("T")[0]
          const isToday = ds === today
          return (
            <div key={i} style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0", borderLeft: "1px solid #E2E8F0", padding: "10px 8px", textAlign: "center" }}>
              <div style={{ fontSize: 10, fontWeight: 600, color: isToday ? "#0D9488" : "#94A3B8", textTransform: "uppercase" as const }}>
                {day.toLocaleDateString("en-ZA", { weekday: "short" })}
              </div>
              <div style={{ fontSize: 18, fontWeight: 800, color: isToday ? "#0D9488" : "#0F172A", marginTop: 2 }}>
                {day.getDate()}
              </div>
              {bookingsOnDay(day).length > 0 && (
                <div style={{ width: 6, height: 6, borderRadius: "50%", background: isToday ? "#0D9488" : "#1B3A6B", margin: "4px auto 0" }} />
              )}
            </div>
          )
        })}

        {/* Time slots */}
        {HOURS.map(hour => (
          <>
            {/* Hour label */}
            <div key={`hour-${hour}`} style={{ padding: "4px 8px", fontSize: 11, color: "#94A3B8", borderBottom: "1px solid #F1F5F9", textAlign: "right", height: 56, boxSizing: "border-box" as const }}>
              {String(hour).padStart(2, "0")}:00
            </div>
            {/* Day columns */}
            {weekDays.map((day, di) => {
              const ds            = day.toISOString().split("T")[0]
              const dayBookings   = bookingsOnDay(day).filter((b: any) => {
                const bHour = parseInt(b.startTime?.split(":")[0] ?? "0")
                return bHour === hour
              })

              return (
                <div key={`${hour}-${di}`} style={{
                  borderLeft: "1px solid #E2E8F0", borderBottom: "1px solid #F1F5F9",
                  height: 56, position: "relative",
                  background: ds === today ? "rgba(13,148,136,0.02)" : "white",
                }}>
                  {dayBookings.map((b: any) => {
                    const startMins = parseInt(b.startTime?.split(":")[1] ?? "0")
                    const topOffset = startMins * (56 / 60)
                    const height    = Math.max(20, durationToH(b.durationMinutes))
                    const color     = STATUS_COLOR[b.status] ?? "#1B3A6B"
                    return (
                      <div key={b.id} title={`${b.clientName} — ${b.serviceName}`}
                        style={{
                          position: "absolute", left: 2, right: 2,
                          top: topOffset, height,
                          background: color + "20",
                          border: `1px solid ${color}40`,
                          borderLeft: `3px solid ${color}`,
                          borderRadius: 4, padding: "1px 4px",
                          overflow: "hidden", cursor: "pointer",
                          zIndex: 1,
                        }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color, lineHeight: 1.3 }}>{b.clientName}</div>
                        {height > 24 && <div style={{ fontSize: 9, color, opacity: 0.8 }}>{b.serviceName}</div>}
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </>
        ))}
      </div>

      {/* Legend */}
      <div style={{ display: "flex", gap: 16, marginTop: 14, flexWrap: "wrap" }}>
        {Object.entries(STATUS_COLOR).map(([status, color]) => (
          <div key={status} style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 11, color: "#64748B" }}>
            <div style={{ width: 10, height: 10, borderRadius: 2, background: color }} />
            {status.replace("_", " ")}
          </div>
        ))}
      </div>
    </div>
  )
}

const navBtn: React.CSSProperties = { background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, padding: "5px 8px", cursor: "pointer", display: "flex", alignItems: "center", color: "#374151" }