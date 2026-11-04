// Court dates and deadlines across every matter, by month. A day's list lets people mark things seen, done or missed.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Btn, Empty, Pill, SectionTitle, card, day, fail, muted } from "./kit"
import { BASE } from "./lookups"
import { DATE_TYPES, KEYDATE_STATUS, MONTHS, dateTone, groupByDay, gridRange, isoDay, keyDateActions, keyDateNote, labelOf, monthGrid, shiftMonth, type Tone } from "./legal.logic"

export interface FirmKeyDate { id: string; matterId: string; matterNumber: string; matterName: string; matterStatus: string; dateType: string; dueDate: string; description: string; acknowledged: boolean; status: string }
const TONE_BG: Record<Tone, string> = { neutral: "var(--hf-surface-sunken)", info: "var(--hf-info-soft-strong)", success: "var(--hf-success-soft-strong)", warning: "var(--hf-warning-soft-strong)", danger: "var(--hf-danger-soft-strong, #fde2e2)" }
const TONE_FG: Record<Tone, string> = { neutral: "var(--hf-text-tertiary)", info: "var(--hf-info-text)", success: "var(--hf-success-text-strong)", warning: "var(--hf-warning-text-deep)", danger: "var(--hf-danger-text)" }
const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

export default function CalendarTab() {
  const qc = useQueryClient(), canWrite = usePermission("LEGALPRACTICE_MANAGE") || usePermission("LEGALPRACTICE_ADMIN")
  const now = new Date(), todayIso = isoDay(now)
  const [ym, setYm] = useState({ year: now.getFullYear(), month: now.getMonth() }), [sel, setSel] = useState(todayIso)
  const range = gridRange(ym.year, ym.month), weeks = monthGrid(ym.year, ym.month)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-calendar", range.from, range.to], queryFn: async () => { const r: any = await apiClient.get(`${BASE}/key-dates`, { params: range }); const d = r?.data?.data ?? r?.data; return (Array.isArray(d) ? d : []) as FirmKeyDate[] } })
  const act = useMutation({
    mutationFn: ({ k, path }: { k: FirmKeyDate; path: string }) => apiClient.post(`${BASE}/matters/${k.matterId}/key-dates/${k.id}/${path}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lp-calendar"] }); qc.invalidateQueries({ queryKey: ["lp-dash-dates"] }); qc.invalidateQueries({ queryKey: ["lp-keydates"] }) }, onError: e => { void fail(e, "That did not work") },
  })
  const byDay = groupByDay(data ?? []), picked = byDay[sel] ?? []
  const move = (d: number) => { const n = shiftMonth(ym.year, ym.month, d); setYm(n); setSel(isoDay(new Date(n.year, n.month, 1))) }
  return (
    <div>
      <SectionTitle right={<><Btn small onClick={() => move(-1)} title="Previous month">‹ Previous</Btn><Btn small onClick={() => { setYm({ year: now.getFullYear(), month: now.getMonth() }); setSel(todayIso) }}>Today</Btn><Btn small onClick={() => move(1)} title="Next month">Next ›</Btn></>}>{MONTHS[ym.month]} {ym.year}</SectionTitle>
      {isError ? <Empty>Could not load the calendar. Please try again.</Empty> : (
        <div style={{ ...card, padding: 8, overflowX: "auto" }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(7, minmax(44px, 1fr))", gap: 2, minWidth: 320 }}>
            {WEEKDAYS.map(w => <div key={w} style={{ ...muted, textAlign: "center", padding: 4, fontWeight: 700 }}>{w}</div>)}
            {weeks.flat().map(c => {
              const items = byDay[c.date] ?? [], selected = c.date === sel, isToday = c.date === todayIso
              return (
                <button key={c.date} onClick={() => setSel(c.date)} aria-label={`${day(c.date)}${items.length ? `, ${items.length} deadline${items.length === 1 ? "" : "s"}` : ""}`} aria-pressed={selected}
                  style={{ minHeight: 64, textAlign: "left", padding: 4, borderRadius: 8, cursor: "pointer", font: "inherit", opacity: c.inMonth ? 1 : 0.45, background: "var(--hf-surface)", border: selected ? "2px solid var(--hf-primary)" : isToday ? "2px solid var(--hf-text-muted)" : "1px solid var(--hf-border)" }}>
                  <div style={{ fontSize: 12, fontWeight: isToday ? 800 : 600 }}>{Number(c.date.slice(8))}</div>
                  {items.slice(0, 2).map(k => { const tone = dateTone(k.status, k.dueDate, now); return <div key={k.id} style={{ marginTop: 2, fontSize: 10, padding: "1px 4px", borderRadius: 4, background: TONE_BG[tone], color: TONE_FG[tone], overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{k.matterNumber}</div> })}
                  {items.length > 2 && <div style={{ fontSize: 10, color: "var(--hf-text-muted)" }}>+{items.length - 2} more</div>}
                </button>)
            })}
          </div>
        </div>)}
      {isLoading && <div style={muted}>Loading…</div>}
      <div style={{ ...card, marginTop: 12 }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>{day(sel)}</div>
        {picked.length === 0 ? <div style={muted}>Nothing due on this day.</div> : picked.map(k => {
          const st = KEYDATE_STATUS[k.status] ?? { label: k.status, tone: "neutral" as const }, note = keyDateNote(k.status, k.dueDate, now), acts = keyDateActions(k.status, k.acknowledged, canWrite)
          return (
            <div key={k.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap", padding: "8px 0", borderTop: "1px solid var(--hf-border)" }}>
              <div><b>{k.description}</b><div style={muted}>{k.matterNumber} · {k.matterName} · {labelOf(DATE_TYPES, k.dateType)}</div>
                <div style={{ marginTop: 4 }}><Pill tone={st.tone}>{st.label}</Pill>{note && <>{" "}<Pill tone={note.tone}>{note.text}</Pill></>}{k.status === "PENDING" && k.acknowledged && <span style={muted}> Seen</span>}</div></div>
              <div style={{ display: "flex", gap: 6, alignItems: "flex-start" }}>
                {acts.includes("acknowledge") && <Btn small disabled={act.isPending} onClick={() => act.mutate({ k, path: "acknowledge" })}>Seen</Btn>}
                {acts.includes("complete") && <Btn small disabled={act.isPending} onClick={() => act.mutate({ k, path: "complete" })}>Done</Btn>}
                {acts.includes("mark-missed") && <Btn small disabled={act.isPending} onClick={() => act.mutate({ k, path: "mark-missed" })}>Missed</Btn>}
              </div>
            </div>)
        })}
      </div>
      <div style={{ ...muted, marginTop: 8 }}>Add or change deadlines on the matter itself, under Key dates.</div>
    </div>
  )
}
