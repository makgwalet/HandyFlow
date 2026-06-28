// src/pages/bookings/CalendarTab.tsx
//
// FIXES vs original:
// 1. Minute-offset bug: bookings at e.g. 09:30 were invisible because the original
//    code only rendered bookings where bHour === hour (exactly on the hour).
//    FIX: Each cell renders bookings where the booking's hour matches AND the
//    topOffset is calculated from the slot start within that hour row.
//    The approach: render ALL day's bookings in the 7:00 row using absolute
//    positioning relative to the full day column height, not per-hour cells.
// 2. Time range extended to 07:00–21:00 matching realistic SA business hours.
// 3. Fetch uses date range instead of size=200 + client-side filter.
import { useState, useRef } from "react"
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

// 07:00 to 21:00 = 14 hours
const HOUR_START  = 7
const HOUR_END    = 21
const HOURS       = Array.from({ length: HOUR_END - HOUR_START }, (_, i) => i + HOUR_START)
const HOUR_HEIGHT = 60  // px per hour in the grid

function getWeekDays(base: Date): Date[] {
  const mon = new Date(base)
  mon.setDate(base.getDate() - ((base.getDay() + 6) % 7))
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(mon); d.setDate(mon.getDate() + i); return d
  })
}

// Convert "HH:MM" to fractional hours from HOUR_START
// WHY fractional hours and not minutes?
// top = fractionalHoursFromStart × HOUR_HEIGHT gives pixel position
// e.g. "09:30" → (9.5 - 7) × 60 = 150px from top of grid
function timeToY(time: string): number {
  const [h, m] = time.split(":").map(Number)
  return ((h + m / 60) - HOUR_START) * HOUR_HEIGHT
}

function durationToH(mins: number): number {
  return (mins / 60) * HOUR_HEIGHT
}

export default function CalendarTab() {
  const [baseDate, setBaseDate] = useState(new Date())
  const weekDays = getWeekDays(baseDate)
  const from     = weekDays[0].toISOString().split("T")[0]
  const to       = weekDays[6].toISOString().split("T")[0]
  const today    = new Date().toISOString().split("T")[0]

  // WHY fetch with date range instead of size=200 + client-filter?
  // size=200 loads all bookings every time — if a tenant has 500 bookings,
  // they wait for 500 rows to load just to see 7 days.
  // The correct approach: the backend already accepts a date filter.
  // We fetch Monday's bookings, then Tuesday's, … or ideally one call with
  // a range param.  Since the current API only supports a single date param,
  // we fetch size=200 for the week range — this is scoped to 7 days so
  // the count is always manageable.  A future enhancement adds date range
  // support to the backend (/api/v1/bookings?from=X&to=Y).
  // WHY dateFrom+dateTo instead of size=200?
  // size=200 loads every booking the tenant ever made, filters client-side to
  // the visible week, and silently drops bookings once the tenant exceeds 200/week.
  // The backend now accepts dateFrom+dateTo (BETWEEN inclusive) so we fetch
  // only the 7 days visible in the current week view — bounded and correct.
  const { data: bookings = [], isLoading } = useQuery({
    queryKey: ["bookings-week", from, to],
    queryFn: async () => {
      const params = new URLSearchParams({
        dateFrom: from,
        dateTo:   to,
        size:     "500",   // generous ceiling for one week; a tenant doing 500 bookings/week
                           // is a scaling conversation, not a silent data-loss bug
      })
      const res     = await apiClient.get(`/api/v1/bookings?${params}`)
      const payload = res.data?.data ?? res.data
      const all     = (payload?.content ?? payload ?? []) as any[]
      // Server already filtered by date range; no client-side filter needed
      return all.filter((b: any) => b.status !== "CANCELLED")
    },
    staleTime: 30_000,
  })

  const prevWeek = () => { const d = new Date(baseDate); d.setDate(d.getDate() - 7); setBaseDate(d) }
  const nextWeek = () => { const d = new Date(baseDate); d.setDate(d.getDate() + 7); setBaseDate(d) }

  const bookingsOnDay = (date: Date) => {
    const ds = date.toISOString().split("T")[0]
    return bookings.filter((b: any) => b.bookingDate === ds)
  }

  const totalDayHeight = HOURS.length * HOUR_HEIGHT

  return (
    <div>
      {/* ── Nav ─────────────────────────────────────────────────────────── */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <button onClick={prevWeek} style={navBtn}><ChevronLeft size={18} /></button>
          <span style={{ fontSize: 15, fontWeight: 700, color: "#0F172A" }}>
            {weekDays[0].toLocaleDateString("en-ZA", { day: "numeric", month: "long" })} –{" "}
            {weekDays[6].toLocaleDateString("en-ZA", { day: "numeric", month: "long", year: "numeric" })}
          </span>
          <button onClick={nextWeek} style={navBtn}><ChevronRight size={18} /></button>
        </div>
        <button
          onClick={() => setBaseDate(new Date())}
          style={{ padding: "6px 14px", background: "#F1F5F9", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", color: "#374151", fontWeight: 600 }}>
          Today
        </button>
      </div>

      {/* ── Calendar grid ────────────────────────────────────────────────── */}
      <div style={{
        display: "grid",
        gridTemplateColumns: "48px repeat(7, 1fr)",
        border: "1px solid #E2E8F0",
        borderRadius: 12, overflow: "hidden",
      }}>
        {/* Header row */}
        <div style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0", height: 52 }} />
        {weekDays.map((day, i) => {
          const ds      = day.toISOString().split("T")[0]
          const isToday = ds === today
          const count   = bookingsOnDay(day).length
          return (
            <div key={i} style={{
              background: "#F8FAFC", borderBottom: "1px solid #E2E8F0",
              borderLeft: "1px solid #E2E8F0", padding: "10px 8px", textAlign: "center", height: 52,
            }}>
              <div style={{ fontSize: 10, fontWeight: 600, color: isToday ? "#0D9488" : "#94A3B8", textTransform: "uppercase" }}>
                {day.toLocaleDateString("en-ZA", { weekday: "short" })}
              </div>
              <div style={{ fontSize: 18, fontWeight: 800, color: isToday ? "#0D9488" : "#0F172A", marginTop: 1 }}>
                {day.getDate()}
              </div>
              {count > 0 && (
                <div style={{ width: 6, height: 6, borderRadius: "50%", background: isToday ? "#0D9488" : "#1B3A6B", margin: "3px auto 0" }} />
              )}
            </div>
          )
        })}

        {/* Time labels + day columns
            WHY one tall relative-positioned column per day rather than per-hour rows?
            Per-hour rows can't show a booking that spans multiple hours (e.g. 2h massage)
            without complex row-spanning logic.  One tall column with absolute-positioned
            bookings inside handles all durations and any start minute correctly. */}
        <div style={{ display: "contents" }}>
          {/* Time labels on the left — one per hour */}
          <div style={{ position: "relative", height: totalDayHeight }}>
            {HOURS.map(hour => (
              <div
                key={hour}
                style={{
                  position: "absolute", top: (hour - HOUR_START) * HOUR_HEIGHT,
                  right: 6, fontSize: 10, color: "#94A3B8", lineHeight: `${HOUR_HEIGHT}px`,
                }}>
                {String(hour).padStart(2, "0")}:00
              </div>
            ))}
          </div>

          {/* Day columns */}
          {weekDays.map((day, di) => {
            const ds      = day.toISOString().split("T")[0]
            const isToday = ds === today
            const dayBkgs = bookingsOnDay(day)

            return (
              <div
                key={di}
                style={{
                  borderLeft: "1px solid #E2E8F0",
                  position: "relative", height: totalDayHeight,
                  background: isToday ? "rgba(13,148,136,0.02)" : "white",
                }}>
                {/* Horizontal hour grid lines */}
                {HOURS.map(hour => (
                  <div
                    key={hour}
                    style={{
                      position: "absolute", top: (hour - HOUR_START) * HOUR_HEIGHT,
                      left: 0, right: 0, height: 1,
                      background: hour === HOUR_START ? "transparent" : "#F1F5F9",
                    }}
                  />
                ))}

                {/* Booking blocks — absolutely positioned by time
                    WHY top = timeToY(startTime)?
                    timeToY converts "HH:MM" → pixel offset from the top of the column.
                    A booking at 09:30 in a grid starting at 07:00 gets:
                    top = (9 + 30/60 - 7) × 60px = 150px  ✓
                    The original code only showed bookings at exact hours because it
                    used bHour === hour to put bookings into per-hour rows.
                    A 09:30 booking has bHour=9 which matched the 09:00 row,
                    but topOffset = 30 × (56/60) = 28px pushed it into the 10:00 row visually. */}
                {dayBkgs.map((b: any) => {
                  const top    = timeToY(b.startTime)
                  const height = Math.max(22, durationToH(b.durationMinutes))
                  const color  = STATUS_COLOR[b.status] ?? "#1B3A6B"

                  // Don't render if outside our time window
                  if (top < 0 || top > totalDayHeight) return null

                  return (
                    <div
                      key={b.id}
                      title={`${b.clientName} — ${b.serviceName} (${b.startTime?.substring(0, 5)}–${b.endTime?.substring(0, 5)})`}
                      style={{
                        position: "absolute", left: 2, right: 2,
                        top, height: Math.min(height, totalDayHeight - top),
                        background: color + "18",
                        border: `1px solid ${color}35`,
                        borderLeft: `3px solid ${color}`,
                        borderRadius: 4, padding: "2px 5px",
                        overflow: "hidden", cursor: "pointer", zIndex: 1,
                      }}>
                      <div style={{ fontSize: 10, fontWeight: 700, color, lineHeight: 1.3, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {b.startTime?.substring(0, 5)} {b.clientName}
                      </div>
                      {height > 28 && (
                        <div style={{ fontSize: 9, color, opacity: 0.8, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                          {b.serviceName}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )
          })}
        </div>
      </div>

      {/* ── Legend ────────────────────────────────────────────────────────── */}
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

const navBtn: React.CSSProperties = {
  background: "#F8FAFC", border: "1px solid #E2E8F0",
  borderRadius: 7, padding: "5px 8px", cursor: "pointer",
  display: "flex", alignItems: "center", color: "#374151",
}
