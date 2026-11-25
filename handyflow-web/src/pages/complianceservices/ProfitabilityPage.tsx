// src/pages/complianceservices/ProfitabilityPage.tsx
//
// Administrators only. Hours, labour cost and billing side by side. Cost is labour only, and hours with no cost rate are listed apart and
// never priced. Revenue is bills raised from agreements, without VAT.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { formatMinutes, isoDay, marginNote, money, monthToDate, pct, type CostRate, type ProfitReport } from "./timeRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12 }
const th: React.CSSProperties = { textAlign: "left", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-muted)", padding: "6px 8px", borderBottom: "1px solid var(--hf-border)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { fontSize: 13, padding: "8px", borderBottom: "1px solid var(--hf-border)", verticalAlign: "top" }
const num: React.CSSProperties = { textAlign: "right", whiteSpace: "nowrap" }
const B = "/api/v1/compliance-services/profitability"

type Staff = { id: string; firstName?: string; lastName?: string; email?: string }
const staffName = (s: Staff | undefined) => (s ? [s.firstName, s.lastName].filter(Boolean).join(" ") || s.email || "Unnamed person" : "Someone")

export default function ProfitabilityPage() {
  const isAdmin = usePermissions(["COMPLIANCE_SERVICES_ADMIN"])
  const [initial] = useState(() => monthToDate(isoDay(new Date())))
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const { data: staff = [] } = useQuery<Staff[]>({
    queryKey: ["cs-profit-staff"], enabled: isAdmin,
    queryFn: async () => { const d = unwrap<Staff[] | { content?: Staff[] }>(await apiClient.get("/api/v1/identity/users")); return Array.isArray(d) ? d : d?.content ?? [] },
  })
  const report = useQuery<ProfitReport>({ queryKey: ["cs-profit-report", from, to], enabled: isAdmin && !!from && !!to, queryFn: async () => unwrap<ProfitReport>(await apiClient.get(`${B}/report`, { params: { from, to } })) })

  if (!isAdmin) return <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Profitability shows what people cost the practice, so only administrators can see it.</div>
  const who = (id: string) => staffName(staff.find(s => s.id === id))
  const r = report.data
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Profitability</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Hours, labour cost and billing side by side. For administrators only.</div></div>
      <div style={{ display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap" }}>
        <div><label htmlFor="p-from" style={label}>From</label><input id="p-from" type="date" style={input} value={from} onChange={e => setFrom(e.target.value)} /></div>
        <div><label htmlFor="p-to" style={label}>To</label><input id="p-to" type="date" style={input} value={to} onChange={e => setTo(e.target.value)} /></div>
      </div>
      {report.isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {report.error != null && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text, #c0392b)" }}>{errText(report.error)}</div>}
      {r && (
        <>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{r.basis}</div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", fontSize: 13 }}>
            <span style={card}>Hours: <strong>{formatMinutes(r.minutes)}</strong></span>
            <span style={card}>Labour cost: <strong>{money(r.labourCost)}</strong></span>
            <span style={card}>Invoiced: <strong>{money(r.invoiced)}</strong></span>
            <span style={card}>Collected: <strong>{money(r.collected)}</strong></span>
          </div>
          {r.unratedMinutes > 0 && <div role="status" style={{ ...card, fontSize: 13 }}>{formatMinutes(r.unratedMinutes)} of these hours have no cost rate, so the cost and margin below are understated. Set a rate for each person under Cost rates.</div>}
          <h3 style={{ margin: 0, fontSize: 14 }}>By client</h3>
          {r.clients.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing was recorded or billed in this period.</div> : (
            <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Client</th><th style={{ ...th, ...num }}>Hours</th><th style={{ ...th, ...num }}>Rework</th><th style={{ ...th, ...num }}>Cost</th><th style={{ ...th, ...num }}>Invoiced</th><th style={{ ...th, ...num }}>Collected</th><th style={{ ...th, ...num }}>Margin</th><th style={th}>Note</th></tr></thead>
              <tbody>{r.clients.map(c => (
                <tr key={c.clientId}><td style={td}>{c.clientName}</td><td style={{ ...td, ...num }}>{formatMinutes(c.minutes)}</td><td style={{ ...td, ...num }}>{formatMinutes(c.reworkMinutes)}</td>
                  <td style={{ ...td, ...num }}>{money(c.labourCost)}</td><td style={{ ...td, ...num }}>{money(c.invoiced)}</td><td style={{ ...td, ...num }}>{money(c.collected)}</td>
                  <td style={{ ...td, ...num }}>{money(c.margin)} ({pct(c.marginPct)})</td><td style={td}>{marginNote(c)}</td></tr>
              ))}</tbody></table></div>
          )}
          <h3 style={{ margin: 0, fontSize: 14 }}>By service</h3>
          <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Service</th><th style={{ ...th, ...num }}>Hours</th><th style={{ ...th, ...num }}>Cost</th></tr></thead>
            <tbody>{r.services.map((s, i) => <tr key={i}><td style={td}>{s.serviceCode ?? "No service given"}</td><td style={{ ...td, ...num }}>{formatMinutes(s.minutes)}</td><td style={{ ...td, ...num }}>{money(s.labourCost)}</td></tr>)}</tbody></table></div>
          <h3 style={{ margin: 0, fontSize: 14 }}>By person</h3>
          <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Person</th><th style={{ ...th, ...num }}>Hours</th><th style={{ ...th, ...num }}>Billable share</th><th style={{ ...th, ...num }}>Cost</th></tr></thead>
            <tbody>{r.people.map(p => <tr key={p.userId}><td style={td}>{who(p.userId)}</td><td style={{ ...td, ...num }}>{formatMinutes(p.minutes)}</td><td style={{ ...td, ...num }}>{pct(p.utilisationPct)}</td><td style={{ ...td, ...num }}>{money(p.labourCost)}</td></tr>)}</tbody></table></div>
        </>
      )}
      <CostRates staff={staff} who={who} onChanged={() => void report.refetch()} />
    </div>
  )
}

function CostRates({ staff, who, onChanged }: { staff: Staff[]; who: (id: string) => string; onChanged: () => void }) {
  const qc = useQueryClient()
  const [today] = useState(() => isoDay(new Date()))
  const [userId, setUserId] = useState("")
  const [cost, setCost] = useState("")
  const [day, setDay] = useState(today)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data = [] } = useQuery<CostRate[]>({ queryKey: ["cs-cost-rates"], queryFn: async () => unwrap<CostRate[]>(await apiClient.get(`${B}/cost-rates`)) })
  const save = async () => {
    const n = Number(cost)
    if (!userId) { setError("Choose the person"); return }
    if (!Number.isFinite(n) || n <= 0) { setError("Enter what an hour costs, more than nothing"); return }
    if (!day) { setError("Say from which day this rate applies"); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${B}/cost-rates`, { userId, hourlyCost: n, effectiveFrom: day }); setCost(""); qc.invalidateQueries({ queryKey: ["cs-cost-rates"] }); onChanged() }
    catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <section style={{ display: "grid", gap: 8 }}>
      <h3 style={{ margin: 0, fontSize: 14 }}>Cost rates</h3>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>What an hour of each person costs the practice. A rate is never changed: add a new one with a later day and it takes over from then.</div>
      <div style={{ ...card, display: "flex", gap: 10, alignItems: "end", flexWrap: "wrap" }}>
        <div><label htmlFor="cr-user" style={label}>Person</label>
          <select id="cr-user" style={input} value={userId} onChange={e => setUserId(e.target.value)}><option value="">Choose a person</option>{staff.map(s => <option key={s.id} value={s.id}>{who(s.id)}</option>)}</select></div>
        <div><label htmlFor="cr-cost" style={label}>Cost of one hour (R)</label><input id="cr-cost" inputMode="decimal" style={{ ...input, width: 130 }} value={cost} onChange={e => setCost(e.target.value)} /></div>
        <div><label htmlFor="cr-day" style={label}>From</label><input id="cr-day" type="date" style={input} value={day} onChange={e => setDay(e.target.value)} /></div>
        <button style={solid} disabled={busy} onClick={save}>Add rate</button>
      </div>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}
      {data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No cost rates yet. Hours are counted, but not priced, until you add some.</div>}
      {data.map(r => <div key={r.id} style={{ fontSize: 13 }}>{who(r.userId)}: <strong>{money(r.hourlyCost)}</strong> an hour from {r.effectiveFrom}</div>)}
    </section>
  )
}
