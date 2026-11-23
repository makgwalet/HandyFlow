// src/pages/agriculture/AgCalendarTab.tsx
//
// The farm's own calendar: what is due on this farm, by day or by week. Read only. GET /api/v1/agriculture/farms/{id}/calendar
// builds it from dates the farm already records (health reminders and scheduled treatments, the day each withdrawal period
// ends, births expected, plantings planned, harvests expected, scouting follow-ups). Opening an item goes to its animal,
// group or the crop cycles.
import type React from "react"
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, btnGhost, inp } from "./constants"
import { EmptyNote, QueryError } from "./agUi"
import { todayISO } from "./agCrops.logic"
import {
  KIND_FILTERS, dayLabel, filterItems, groupByDay, kindLabel, step, summaryLine, windowFor, windowTitle,
  type CalendarItem, type CalendarView, type KindFilter,
} from "./agCalendar.logic"

export default function AgCalendarTab({ farmId, onOpenAnimal, onOpenGroup, onOpenCrops }: {
  farmId: string; onOpenAnimal: (id: string) => void; onOpenGroup: (id: string) => void; onOpenCrops: () => void
}) {
  const today = todayISO()
  const [view, setView] = useState<CalendarView>("week")
  const [anchor, setAnchor] = useState(today)
  const [filter, setFilter] = useState<KindFilter>("all")
  const { from, to } = windowFor(view, anchor)

  const { data, isLoading, isError, refetch } = useQuery<CalendarItem[]>({
    queryKey: ["ag-calendar", farmId, from, to],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/calendar`, { params: { from, to } })).data ?? [],
  })
  const items = filterItems(data ?? [], filter)
  const days = groupByDay(items, from, to)

  const open = (i: CalendarItem) => {
    if (i.targetType === "ANIMAL") onOpenAnimal(i.targetId)
    else if (i.targetType === "GROUP") onOpenGroup(i.targetId)
    else onOpenCrops()
  }

  return (
    <div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
        <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
          <button onClick={() => setAnchor(a => step(view, a, -1))} aria-label={view === "day" ? "Previous day" : "Previous week"} style={iconBtn}><ChevronLeft size={14} /></button>
          <button onClick={() => setAnchor(today)} style={btnGhost}>Today</button>
          <button onClick={() => setAnchor(a => step(view, a, 1))} aria-label={view === "day" ? "Next day" : "Next week"} style={iconBtn}><ChevronRight size={14} /></button>
          <h3 style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 0 8px" }}>{windowTitle(view, from, to)}</h3>
        </div>
        <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
          <select aria-label="Show" value={filter} onChange={e => setFilter(e.target.value as KindFilter)} style={{ ...inp, width: "auto" }}>
            {KIND_FILTERS.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
          </select>
          <select aria-label="View" value={view} onChange={e => setView(e.target.value as CalendarView)} style={{ ...inp, width: "auto" }}>
            <option value="week">Week</option>
            <option value="day">Day</option>
          </select>
        </div>
      </div>

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} what="the calendar" /> : (
        <>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{summaryLine(items)}</p>
          {items.length === 0 && view === "day" ? <EmptyNote>Nothing due on this day.</EmptyNote> : (
            <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
              {days.map((d, n) => (
                <div key={d.date} style={{ borderTop: n === 0 ? "none" : "1px solid var(--hf-border-subtle)", padding: "8px 14px", background: d.date === today ? "var(--hf-surface-muted)" : "transparent" }}>
                  <p style={{ fontSize: 12, fontWeight: 700, color: d.date === today ? AG_ACCENT_TEXT : "var(--hf-text-muted)", margin: "0 0 4px" }}>
                    {dayLabel(d.date)}{d.date === today ? " · today" : ""}
                  </p>
                  {d.items.length === 0 ? <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Nothing due.</p> : d.items.map((i, k) => (
                    <button key={`${i.kind}-${i.targetId}-${k}`} onClick={() => open(i)} style={row}>
                      <span style={{ ...kindTag, color: i.overdue ? "var(--hf-danger-text)" : AG_ACCENT_TEXT }}>{kindLabel(i.kind)}</span>
                      <span style={{ flex: 1, minWidth: 0 }}>
                        <span style={{ display: "block", fontSize: 12.5, fontWeight: 600, color: "var(--hf-text)" }}>{i.title}{i.overdue ? " · overdue" : ""}</span>
                        {i.detail && <span style={{ display: "block", fontSize: 11.5, color: "var(--hf-text-faint)" }}>{i.detail}</span>}
                      </span>
                    </button>
                  ))}
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
const row: React.CSSProperties = { display: "flex", gap: 10, alignItems: "flex-start", width: "100%", textAlign: "left", background: "transparent", border: "none", padding: "5px 0", cursor: "pointer" }
const kindTag: React.CSSProperties = { fontSize: 11, fontWeight: 700, width: 104, flexShrink: 0, paddingTop: 1 }
