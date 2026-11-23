// src/pages/security/PayPremiumPanel.tsx
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  DAY_FAILED, PREMIUM_NOTE, SAVE_FAILED, addDayError, failureText, holidayDate, multiplierText, multiplierValid, rateExample, rowAction, settingsError, sourceLabel,
  toSaveRequest, yearChoices, type HolidayRow, type PremiumSettings,
} from "./payPremiumLogic"

const inp = { padding: "8px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", fontSize: 13, outline: "none", boxSizing: "border-box" as const }
const btn = { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 13, cursor: "pointer", fontWeight: 600 }
const sbtn = { padding: "6px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontSize: 12, cursor: "pointer", color: "var(--hf-text-secondary)" }
const SAMPLE_RATE_CENTS = 10000

export default function PayPremiumPanel({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient()
  // What the person has typed, or null while they have changed nothing: then the saved settings are shown.
  const [draft, setDraft] = useState<{ enabled: boolean; sunday: string; holiday: string } | null>(null)
  const [err, setErr] = useState("")
  const [saved, setSaved] = useState(false)
  const [year, setYear] = useState(new Date().getFullYear())
  const [dayDate, setDayDate] = useState("")
  const [dayName, setDayName] = useState("")
  const [dayErr, setDayErr] = useState("")

  const settings = useQuery<PremiumSettings>({
    queryKey: ["pay-premiums"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/payroll/premiums"); return (r.data?.data ?? r.data) as PremiumSettings },
  })
  const holidays = useQuery<HolidayRow[]>({
    queryKey: ["pay-premium-holidays", year],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/payroll/premiums/holidays?year=${year}`); return (r.data?.data ?? r.data) as HolidayRow[] },
  })

  const shown = draft ?? (settings.data
    ? { enabled: settings.data.enabled, sunday: multiplierText(Number(settings.data.sundayMultiplier)), holiday: multiplierText(Number(settings.data.holidayMultiplier)) }
    : { enabled: false, sunday: "1.50", holiday: "2.00" })
  const { enabled, sunday, holiday } = shown
  const edit = (change: Partial<typeof shown>) => { setDraft({ ...shown, ...change }); setSaved(false) }

  const save = useMutation({
    mutationFn: (b: object) => apiClient.put("/api/v1/security/payroll/premiums", b),
    onSuccess: (r) => { setErr(""); setSaved(true); setDraft(null); qc.setQueryData(["pay-premiums"], r.data?.data ?? r.data) },
    onError: (e) => { setSaved(false); setErr(failureText(e, SAVE_FAILED)) },
  })
  const addDay = useMutation({
    mutationFn: (b: object) => apiClient.post("/api/v1/security/payroll/premiums/holidays", b),
    onSuccess: () => { setDayErr(""); setDayDate(""); setDayName(""); qc.invalidateQueries({ queryKey: ["pay-premium-holidays"] }) },
    onError: (e) => setDayErr(failureText(e, DAY_FAILED)),
  })
  const takeOff = useMutation({
    mutationFn: (date: string) => apiClient.delete(`/api/v1/security/payroll/premiums/holidays?date=${date}`),
    onSuccess: () => { setDayErr(""); qc.invalidateQueries({ queryKey: ["pay-premium-holidays"] }) },
    onError: (e) => setDayErr(failureText(e, DAY_FAILED)),
  })
  // A national day that was taken off is put back by adding it again.
  const putBack = useMutation({
    mutationFn: (row: HolidayRow) => apiClient.post("/api/v1/security/payroll/premiums/holidays", { date: row.date, name: row.name }),
    onSuccess: () => { setDayErr(""); qc.invalidateQueries({ queryKey: ["pay-premium-holidays"] }) },
    onError: (e) => setDayErr(failureText(e, DAY_FAILED)),
  })

  // Until the saved settings arrive the fields would show made-up defaults, and a late answer would overwrite what was typed.
  const locked = !canManage || settings.isLoading
  const problem = settingsError(sunday, holiday)
  const onSave = () => {
    setSaved(false)
    if (problem) { setErr(problem); return }
    setErr("")
    save.mutate(toSaveRequest(enabled, sunday, holiday))
  }
  const onAdd = () => {
    const e = addDayError(dayDate, dayName)
    if (e) { setDayErr(e); return }
    setDayErr("")
    addDay.mutate({ date: dayDate, name: dayName.trim() })
  }

  if (settings.isError) {
    return <div style={{ padding: "24px 0", color: "var(--hf-text-muted)", fontSize: 14 }}>{failureText(settings.error, "Sunday and public-holiday pay is not available to you.")}</div>
  }

  const sundayNum = Number(sunday.replace(",", "."))
  const holidayNum = Number(holiday.replace(",", "."))

  return (
    <div data-testid="pay-premium-panel" style={{ display: "grid", gap: 20 }}>
      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 20 }}>
        <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Sunday and public-holiday pay</h3>
        <p style={{ margin: "4px 0 14px", fontSize: 12, color: "var(--hf-text-faint)" }}>{PREMIUM_NOTE}</p>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, color: "var(--hf-text)", marginBottom: 12 }}>
          <input type="checkbox" aria-label="Pay Sunday and public-holiday rates" checked={enabled} disabled={locked} onChange={(e) => edit({ enabled: e.target.checked })} />
          Pay Sunday and public-holiday rates
        </label>
        <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
          <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Sunday rate (× ordinary)
            <input style={{ ...inp, display: "block", width: 120, marginTop: 4 }} aria-label="Sunday rate" value={sunday} disabled={locked}
              onChange={(e) => edit({ sunday: e.target.value })} />
            {multiplierValid(sunday) && <span data-testid="sunday-example" style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{rateExample(SAMPLE_RATE_CENTS, sundayNum)}</span>}
          </label>
          <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Public holiday rate (× ordinary)
            <input style={{ ...inp, display: "block", width: 120, marginTop: 4 }} aria-label="Public holiday rate" value={holiday} disabled={locked}
              onChange={(e) => edit({ holiday: e.target.value })} />
            {multiplierValid(holiday) && <span data-testid="holiday-example" style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{rateExample(SAMPLE_RATE_CENTS, holidayNum)}</span>}
          </label>
        </div>
        {err && <div role="alert" style={{ marginTop: 12, color: "var(--hf-danger-text-strong)", fontSize: 13 }}>{err}</div>}
        {saved && <div role="status" style={{ marginTop: 12, color: "var(--hf-success-text-strong)", fontSize: 13 }}>Saved. It applies to periods approved from now on.</div>}
        {canManage
          ? <button style={{ ...btn, marginTop: 14, opacity: save.isPending ? 0.6 : 1 }} disabled={save.isPending || settings.isLoading} onClick={onSave}>Save</button>
          : <p style={{ marginTop: 12, fontSize: 12, color: "var(--hf-text-faint)" }}>You can see these settings but not change them.</p>}
      </div>

      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 20 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Public holidays</h3>
          <select aria-label="Year" style={inp} value={year} onChange={(e) => setYear(Number(e.target.value))}>
            {yearChoices(new Date()).map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        </div>
        {dayErr && <div role="alert" style={{ marginBottom: 10, color: "var(--hf-danger-text-strong)", fontSize: 13 }}>{dayErr}</div>}
        {holidays.isLoading ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading…</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <tbody>
              {(holidays.data ?? []).map((h) => {
                const a = rowAction(h.source)
                return (
                  <tr key={h.date} data-testid={`holiday-${h.date}`} style={{ borderBottom: "1px solid var(--hf-border-subtle)", opacity: h.source === "REMOVED" ? 0.6 : 1 }}>
                    <td style={{ padding: "8px 10px", color: "var(--hf-text)" }}>{holidayDate(h.date)}</td>
                    <td style={{ padding: "8px 10px", color: "var(--hf-text-secondary)", textDecoration: h.source === "REMOVED" ? "line-through" : "none" }}>{h.name}</td>
                    <td style={{ padding: "8px 10px", color: "var(--hf-text-faint)" }}>{sourceLabel(h.source)}</td>
                    <td style={{ padding: "8px 10px", textAlign: "right" }}>
                      {canManage && <button style={sbtn} onClick={() => (a.kind === "RESTORE" ? putBack.mutate(h) : takeOff.mutate(h.date))}>{a.label}</button>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        {canManage && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 14, alignItems: "flex-end" }}>
            <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Add a day
              <input type="date" aria-label="Day to add" style={{ ...inp, display: "block", marginTop: 4 }} value={dayDate} onChange={(e) => setDayDate(e.target.value)} />
            </label>
            <label style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>Name
              <input aria-label="Day name" maxLength={100} style={{ ...inp, display: "block", marginTop: 4, width: 220 }} value={dayName} onChange={(e) => setDayName(e.target.value)} />
            </label>
            <button style={btn} onClick={onAdd} disabled={addDay.isPending}>Add day</button>
          </div>
        )}
        <p style={{ marginTop: 12, fontSize: 11, color: "var(--hf-text-faint)" }}>
          National days are worked out for you. Add a day if government declares an extra public holiday.
        </p>
      </div>
    </div>
  )
}
