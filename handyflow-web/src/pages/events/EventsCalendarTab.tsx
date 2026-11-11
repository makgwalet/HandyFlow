// src/pages/events/EventsCalendarTab.tsx
//
// Month calendar of all events (read access is enough). Multi-day events appear on every day they cover; cancelled ones can be hidden. Picking an event opens it like
// the list does. The server has no date filter yet, so the latest 500 events are loaded and placed client-side; the screen says so if there are more than that.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { apiClient } from "../../api/client"
import { btnGhost, card, ErrorLine, inputStyle } from "../warehousing/whseUi"
import { byDay, monthCount, monthGrid, monthTitle, shiftMonth, timeOf, WEEKDAYS, ymd, type CalEvent } from "./calendarOps"

const TONE: Record<string, string> = { DRAFT: "var(--hf-text-muted)", PUBLISHED: "var(--hf-success-text-strong)", SOLD_OUT: "var(--hf-warning-text)", LIVE: "var(--hf-danger-text)", COMPLETED: "var(--hf-sky-text)", CANCELLED: "var(--hf-text-faint)" }
const LIMIT = 500

export default function EventsCalendarTab({ onSelectEvent }: { onSelectEvent: (id: string, title: string) => void }) {
  const now = new Date(), today = ymd(now.getFullYear(), now.getMonth(), now.getDate())
  const [ym, setYm] = useState({ year: now.getFullYear(), month: now.getMonth() })
  const [hideCancelled, setHide] = useState(true)
  const [picked, setPicked] = useState<string | null>(null)
  const { data, isLoading, error } = useQuery<{ content: CalEvent[]; totalElements?: number }>({
    queryKey: ["events-calendar"],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events?size=${LIMIT}&sort=startDatetime,asc`); const p = r.data?.data ?? r.data; return Array.isArray(p) ? { content: p } : p },
  })
  const map = byDay(data?.content ?? [], hideCancelled)
  const cells = monthGrid(ym.year, ym.month, today)
  const shown = picked ?? (cells.some(c => c.date === today && c.inMonth) ? today : null)
  const list = shown ? map[shown] ?? [] : []
  const more = (data?.totalElements ?? 0) > LIMIT
  const go = (by: number) => { setYm(shiftMonth(ym.year, ym.month, by)); setPicked(null) }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <button type="button" style={btnGhost} aria-label="Previous month" onClick={() => go(-1)}><ChevronLeft size={15} /></button>
          <h3 style={{ margin: 0, fontSize: 16, minWidth: 150, textAlign: "center" }} aria-live="polite">{monthTitle(ym.year, ym.month)}</h3>
          <button type="button" style={btnGhost} aria-label="Next month" onClick={() => go(1)}><ChevronRight size={15} /></button>
          <button type="button" style={btnGhost} onClick={() => { setYm({ year: now.getFullYear(), month: now.getMonth() }); setPicked(null) }}>Today</button>
        </div>
        <label style={{ fontSize: 12.5, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={hideCancelled} onChange={e => setHide(e.target.checked)} /> Hide cancelled</label>
      </div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>{monthCount(map, ym.year, ym.month)} event(s) this month{more ? ` · showing the first ${LIMIT} of ${data?.totalElements} events` : ""}</p>
      <ErrorLine error={error} fallback="The calendar could not be loaded" />
      {isLoading ? <p>Loading…</p> : (
        <>
          <div role="grid" aria-label={monthTitle(ym.year, ym.month)} style={{ display: "grid", gridTemplateColumns: "repeat(7, minmax(0,1fr))", gap: 4 }}>
            {WEEKDAYS.map(w => <div key={w} role="columnheader" style={{ fontSize: 11.5, fontWeight: 700, color: "var(--hf-text-muted)", textAlign: "center" }}>{w}</div>)}
            {cells.map(c => {
              const evs = map[c.date] ?? []
              return (
                <div key={c.date} role="gridcell" aria-label={c.date} onClick={() => setPicked(c.date)} style={{ ...card, minHeight: 78, padding: 4, opacity: c.inMonth ? 1 : 0.45, outline: c.date === shown ? "2px solid var(--hf-primary)" : c.isToday ? "1px solid var(--hf-primary)" : undefined, cursor: "pointer", overflow: "hidden" }}>
                  <div style={{ fontSize: 11.5, fontWeight: c.isToday ? 800 : 500 }}>{Number(c.date.slice(8))}</div>
                  {evs.slice(0, 2).map(e => (
                    <button key={e.id} type="button" title={e.title} onClick={ev => { ev.stopPropagation(); onSelectEvent(e.id, e.title) }} style={{ display: "block", width: "100%", textAlign: "left", background: "none", border: "none", padding: 0, fontSize: 11, color: TONE[e.status] ?? "var(--hf-text)", textDecoration: e.status === "CANCELLED" ? "line-through" : "none", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", cursor: "pointer" }}>{e.title}</button>
                  ))}
                  {evs.length > 2 && <div style={{ fontSize: 10.5, color: "var(--hf-text-muted)" }}>+{evs.length - 2} more</div>}
                </div>
              )
            })}
          </div>
          {shown && (
            <section aria-label="Selected day" style={{ ...card, padding: 12, marginTop: 12 }}>
              <h4 style={{ margin: "0 0 6px", fontSize: 13.5 }}>{new Date(`${shown}T00:00:00Z`).toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" })}</h4>
              {list.length === 0 ? <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>No events.</p> : list.map(e => (
                <div key={e.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 13, padding: "3px 0" }}>
                  <span><strong style={{ textDecoration: e.status === "CANCELLED" ? "line-through" : "none" }}>{e.title}</strong> · {timeOf(e.startDatetime)}{e.venueName ? ` · ${e.venueName}` : ""} · <span style={{ color: TONE[e.status] }}>{e.status.toLowerCase().replace("_", " ")}</span></span>
                  <button type="button" style={{ ...inputStyle, cursor: "pointer" }} onClick={() => onSelectEvent(e.id, e.title)}>Open</button>
                </div>
              ))}
            </section>
          )}
        </>
      )}
    </div>
  )
}
