// The working hours, days and public holidays that the "business hours" service clock counts.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Empty, ErrorLine, Input, Pill, SectionTitle, card, day, errorText, fail, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { WEEKDAYS, calendarBody, calendarProblem, daysText, holidayProblem, trimTime, type CalendarForm } from "./fm.logic"

interface Calendar { configured: boolean; startTime: string | null; endTime: string | null; workingDays: string[]; timeZone: string | null; holidays: { date: string; name: string | null }[] }
const URL = `${BASE}/sla/calendar`

export default function SlaCalendarTab() {
  const qc = useQueryClient(), key = ["fm-sla-calendar"]
  const canEdit = usePermission("FACILITIESMANAGEMENT_ADMIN") || usePermission("FACILITIESMANAGEMENT_SLA_MANAGE")
  const { data, isLoading, isError } = useQuery({ queryKey: key, queryFn: async () => (await apiClient.get<Calendar>(URL)).data })
  const [f, setF] = useState<CalendarForm>({ startTime: "08:00", endTime: "17:00", workingDays: ["MON", "TUE", "WED", "THU", "FRI"], timeZone: "Africa/Johannesburg" })
  const [err, setErr] = useState(""), [hDate, setHDate] = useState(""), [hName, setHName] = useState(""), [hErr, setHErr] = useState("")
  useEffect(() => { if (data?.configured) setF({ startTime: trimTime(data.startTime), endTime: trimTime(data.endTime), workingDays: data.workingDays, timeZone: data.timeZone ?? "Africa/Johannesburg" }) }, [data])
  const problem = calendarProblem(f), holidays = [...(data?.holidays ?? [])].sort((a, b) => a.date.localeCompare(b.date))
  const save = useMutation({
    mutationFn: () => apiClient.put(URL, calendarBody(f)),
    onSuccess: () => { toast.success("Working hours saved"); qc.invalidateQueries({ queryKey: key }) }, onError: async e => setErr(await errorText(e, "Could not save the working hours")),
  })
  const addH = useMutation({
    mutationFn: () => apiClient.post(`${URL}/holidays`, { date: hDate, name: hName.trim() || null }),
    onSuccess: () => { setHDate(""); setHName(""); qc.invalidateQueries({ queryKey: key }) }, onError: async e => setHErr(await errorText(e, "Could not add the holiday")),
  })
  const delH = useMutation({
    mutationFn: (d: string) => apiClient.delete(`${URL}/holidays/${d}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: key }), onError: e => { void fail(e, "Could not remove the holiday") },
  })
  if (isError) return <Empty>Could not load the working hours. Please try again.</Empty>
  if (isLoading) return <Empty>Loading…</Empty>
  const hProblem = holidayProblem(hDate, holidays.map(h => h.date))
  return (
    <div>
      <SectionTitle>Working hours and holidays</SectionTitle>
      {!data?.configured && <div style={{ ...card, marginBottom: 12 }}>No working hours are set yet, so agreements that use the business-hours clock cannot count time. {canEdit ? "Set them below." : "Ask an administrator to set them."}</div>}
      <div style={{ ...card, marginBottom: 16 }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "0 12px" }}>
          <Input label="Opens" type="time" value={f.startTime} onChange={v => setF(s => ({ ...s, startTime: v }))} disabled={!canEdit} />
          <Input label="Closes" type="time" value={f.endTime} onChange={v => setF(s => ({ ...s, endTime: v }))} disabled={!canEdit} />
          <Input label="Time zone" value={f.timeZone} onChange={v => setF(s => ({ ...s, timeZone: v }))} disabled={!canEdit} hint="For example Africa/Johannesburg" />
        </div>
        <div style={{ fontSize: 13, fontWeight: 700, margin: "4px 0 6px" }}>Working days</div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: "6px 16px" }}>
          {WEEKDAYS.map(([v, l]) => <label key={v} style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}><input type="checkbox" disabled={!canEdit} checked={f.workingDays.includes(v)} onChange={() => setF(s => ({ ...s, workingDays: s.workingDays.includes(v) ? s.workingDays.filter(x => x !== v) : [...s.workingDays, v] }))} />{l}</label>)}
        </div>
        <div style={{ ...muted, marginTop: 8 }}>{!problem ? `Counted: ${daysText(f.workingDays)}, ${f.startTime} – ${f.endTime}, and not on the holidays below.` : problem}</div>
        {err && <ErrorLine text={err} />}
        {canEdit && <div style={{ marginTop: 10 }}><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save working hours"}</Btn></div>}
        {!canEdit && <div style={{ ...muted, marginTop: 8 }}>Only administrators and SLA managers can change these.</div>}
      </div>
      <SectionTitle>Public holidays</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {holidays.length === 0 ? <Empty>No holidays recorded. The clock runs on every working day.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Name</th><th /></tr></thead>
            <tbody>{holidays.map(h => <tr key={h.date}><td style={td}>{day(h.date)}</td><td style={td}>{h.name || <Pill tone="neutral">Holiday</Pill>}</td>
              <td style={{ ...td, textAlign: "right" }}>{canEdit && <Btn small disabled={delH.isPending} onClick={() => delH.mutate(h.date)}>Remove</Btn>}</td></tr>)}</tbody>
          </table>)}
      </div>
      {canEdit && (
        <div style={{ ...card, marginTop: 12, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr)) auto", gap: "0 12px", alignItems: "end" }}>
          <Input label="Date" type="date" value={hDate} onChange={v => { setHDate(v); setHErr("") }} />
          <Input label="Name" value={hName} onChange={setHName} maxLength={100} hint="Optional" />
          <div style={{ marginBottom: 12 }}><Btn kind="primary" disabled={!!hProblem || addH.isPending} onClick={() => { setHErr(""); addH.mutate() }}>Add holiday</Btn></div>
          {(hErr || (hDate && hProblem)) && <div style={{ gridColumn: "1 / -1" }}><ErrorLine text={hErr || hProblem || ""} /></div>}
        </div>)}
    </div>
  )
}
