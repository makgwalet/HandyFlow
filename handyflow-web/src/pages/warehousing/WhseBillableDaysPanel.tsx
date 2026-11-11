// src/pages/warehousing/WhseBillableDaysPanel.tsx
//
// Which calendar days storage bills. The standard bills every day; a contract may replace that for one client or for the whole warehouse.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, today } from "./whseUi"
import { billableDaysInStay, holidaysMissing, ruleProblem, scopeLabel, STANDARD_TEXT, type DayRuleView } from "./billableDays"

const BASE = "/api/v1/warehousing/billing-day-rules"

export default function WhseBillableDaysPanel() {
  const qc = useQueryClient()
  const { data } = useQuery<DayRuleView>({ queryKey: ["whse-day-rules"], queryFn: async () => (await apiClient.get(BASE)).data })
  const { data: clientPage } = useQuery<{ content?: { id: string; tradingName: string }[] }>({ queryKey: ["whse-clients-lite"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/clients?size=100")).data })
  const [clientId, setClientId] = useState("")
  const [dayRule, setDayRule] = useState("WEEKDAYS_ONLY")
  const [basis, setBasis] = useState("CALENDAR_DAYS")
  const [from, setFrom] = useState(today())
  const [note, setNote] = useState("")
  const [hDate, setHDate] = useState("")
  const [hName, setHName] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["whse-day-rules"] })
  const addRule = useMutation({ mutationFn: async () => apiClient.post(BASE, { clientId: clientId || null, dayRule, minimumBasis: basis, effectiveFrom: from, note: note || null }), onSuccess: () => { setNote(""); refresh() } })
  const addHoliday = useMutation({ mutationFn: async () => apiClient.post(`${BASE}/holidays`, { date: hDate, name: hName }), onSuccess: () => { setHDate(""); setHName(""); refresh() } })
  const addSa = useMutation({ mutationFn: async (year: number) => apiClient.post(`${BASE}/holidays/south-africa?year=${year}`), onSuccess: refresh })
  const removeHoliday = useMutation({ mutationFn: async (d: string) => apiClient.delete(`${BASE}/holidays/${d}`), onSuccess: refresh })

  if (!data || !Array.isArray(data.rules) || !Array.isArray(data.holidays) || !Array.isArray(data.options)) return null
  const clients = Array.isArray(clientPage?.content) ? clientPage!.content! : []
  const problem = ruleProblem(dayRule, from, today())
  const thisYear = new Date().getFullYear()
  // Friday to Monday, the example the standard is explained with.
  const example = (rule: string) => billableDaysInStay("2026-11-13", "2026-11-16", rule, data.holidays.map(h => h.date))

  return (
    <div style={{ ...card, padding: 14, marginTop: 16 }}>
      <h3 style={{ fontSize: 14, fontWeight: 700, margin: "0 0 4px" }}>Billable days</h3>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{STANDARD_TEXT} A contract may change this; the change applies from the date you choose and days already billed are never rewritten.</p>

      {data.rules.length === 0 ? <p style={{ fontSize: 12.5, margin: "0 0 10px" }}>No contract overrides: every account uses the standard.</p> : (
        <ul style={{ listStyle: "none", padding: 0, margin: "0 0 10px", display: "grid", gap: 4 }}>
          {data.rules.map(r => <li key={r.id} style={{ fontSize: 12.5 }}><strong>{scopeLabel(r)}</strong>: {r.label}, from {r.effectiveFrom}{r.minimumBasis === "BILLABLE_DAYS" ? "; minimum pro-rated by billable days" : ""}{r.note ? ` (${r.note})` : ""}</li>)}
        </ul>
      )}

      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
        <div><label style={labelStyle} htmlFor="bd-scope">Applies to</label>
          <select id="bd-scope" style={inputStyle} value={clientId} onChange={e => setClientId(e.target.value)}>
            <option value="">Whole warehouse (default)</option>
            {clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
          </select></div>
        <div><label style={labelStyle} htmlFor="bd-rule">Rule</label>
          <select id="bd-rule" style={inputStyle} value={dayRule} onChange={e => setDayRule(e.target.value)}>
            {data.options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select></div>
        <div><label style={labelStyle} htmlFor="bd-basis">Minimum charge is pro-rated by</label>
          <select id="bd-basis" style={inputStyle} value={basis} onChange={e => setBasis(e.target.value)}>
            {(data.minimumBases ?? [{ value: "CALENDAR_DAYS", label: "Calendar days in the invoice period (standard)" }]).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select></div>
        <div><label style={labelStyle} htmlFor="bd-from">Starts</label><input id="bd-from" type="date" style={inputStyle} value={from} min={today()} onChange={e => setFrom(e.target.value)} /></div>
        <div style={{ flex: 1, minWidth: 160 }}><label style={labelStyle} htmlFor="bd-note">Contract reference</label><input id="bd-note" style={{ ...inputStyle, width: "100%" }} value={note} maxLength={300} onChange={e => setNote(e.target.value)} /></div>
        <button style={btnPrimary} disabled={!!problem || addRule.isPending} onClick={() => addRule.mutate()}>Add rule</button>
      </div>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "6px 0 0" }}>Goods in on Friday, out on Monday: {example("ALL_DAYS")} billable days under the standard, {example(dayRule)} under the chosen rule.</p>
      {problem && <p style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "4px 0 0" }}>{problem}</p>}
      <ErrorLine error={addRule.error} />

      <h4 style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 4px" }}>Public holidays</h4>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 6px" }}>Only rules that skip public holidays use this list. Keep it up to date; HandyFlow does not guess the dates.</p>
      {holidaysMissing(data.rules, data.holidays, thisYear) && (
        <p role="alert" style={{ fontSize: 12, color: "var(--hf-warning-text-deep)", margin: "0 0 6px" }}>A rule skips public holidays, but none are listed for {thisYear}, so it currently skips none. Add them below.</p>
      )}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "0 0 8px" }}>
        {[thisYear, thisYear + 1].map(y => (
          <button key={y} style={btnGhost} disabled={addSa.isPending} onClick={() => addSa.mutate(y)}>Add South African public holidays for {y}</button>
        ))}
      </div>
      <ErrorLine error={addSa.error} />
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 6px" }}>These are the twelve statutory holidays, with a Sunday holiday moved to the next free weekday. A holiday the President declares for one year (such as an election day) is added by hand. Holidays you have already entered are not changed.</p>
      {data.holidays.length > 0 && (
        <ul style={{ listStyle: "none", padding: 0, margin: "0 0 8px", display: "grid", gap: 4 }}>
          {data.holidays.map(h => (
            <li key={h.date} style={{ fontSize: 12.5, display: "flex", gap: 8, alignItems: "center" }}>
              <span>{h.date} {h.name}</span>
              <button style={{ ...btnGhost, padding: "2px 8px" }} aria-label={`Remove ${h.name}`} onClick={() => removeHoliday.mutate(h.date)}>Remove</button>
            </li>
          ))}
        </ul>
      )}
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
        <div><label style={labelStyle} htmlFor="bd-hdate">Date</label><input id="bd-hdate" type="date" style={inputStyle} value={hDate} onChange={e => setHDate(e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="bd-hname">Name</label><input id="bd-hname" style={inputStyle} value={hName} maxLength={80} onChange={e => setHName(e.target.value)} /></div>
        <button style={btnGhost} disabled={!hDate || !hName.trim() || addHoliday.isPending} onClick={() => addHoliday.mutate()}>Add holiday</button>
      </div>
      <ErrorLine error={addHoliday.error} />
    </div>
  )
}
