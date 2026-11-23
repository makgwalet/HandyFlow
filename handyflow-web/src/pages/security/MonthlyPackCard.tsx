// src/pages/security/MonthlyPackCard.tsx
//
// The monthly service report of one site, for the client. Counts only; the server decides every figure.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight, FileText, Printer } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { useAuthStore } from "../../store/auth.store"
import { printSection } from "./printSection"
import { useCompanyZone } from "./companyZone"
import ReportRecipientsPanel from "./ReportRecipientsPanel"
import {
  PRINT_CSS, bookText, canGoNextMonth, coverDetail, coverText, incidentText, lastMonthSast, monthTitle, patrolText, printTitle, printedAt, shiftLines, shiftMonth, visitorText,
  type Pack,
} from "./monthlyPackLogic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginTop: 16 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const navBtn: React.CSSProperties = { padding: "4px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", display: "inline-flex", alignItems: "center" }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 12, width: 150, color: "var(--hf-text-muted)", verticalAlign: "top" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13 }

function rows(p: Pack): [string, string][] {
  const shifts = shiftLines(p.shifts)
  return [
    ["Cover", [coverText(p.cover), coverDetail(p.cover)].filter(Boolean).join(" ")],
    ["Shifts", shifts.length === 0 ? "No shifts in this period." : shifts.map(s => `${s.n} ${s.label.toLowerCase()}`).join(", ") + "."],
    ["Patrols", patrolText(p.patrols)],
    ["Incidents", incidentText(p.incidents)],
    ["Visitors", visitorText(p.visitors)],
    ["Alarms", p.alarms === 0 ? "No alarm events." : `${p.alarms} alarm ${p.alarms === 1 ? "event" : "events"}.`],
    ["Occurrence Book", bookText(p.occurrenceBook)],
  ]
}

export default function MonthlyPackCard({ siteId }: { siteId: string }) {
  const { ready: zoneReady } = useCompanyZone()
  const [picked, setPicked] = useState<string | null>(null)
  const month = picked ?? lastMonthSast()
  const setMonth = (f: (m: string) => string) => setPicked(f(month))
  const { data, isLoading, error } = useQuery<Pack>({
    queryKey: ["monthly-pack", siteId, month], enabled: zoneReady,
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/monthly-pack`, { params: { month } })).data?.data,
  })
  const canManage = useAuthStore(s => s.hasPermission("SECURITY_MANAGE"))
  const ok = !!data && !!data.cover && !!data.patrols && !!data.incidents && !!data.visitors && !!data.occurrenceBook && !!data.shifts

  return (
    <section aria-label="Monthly service report" style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700 }}><FileText size={16} /> Monthly service report</div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <button type="button" aria-label="Previous month" style={navBtn} onClick={() => setMonth(m => shiftMonth(m, -1))}><ChevronLeft size={14} /></button>
          <span style={{ fontSize: 13, minWidth: 110, textAlign: "center" }}>{monthTitle(month)}</span>
          <button type="button" aria-label="Next month" style={{ ...navBtn, opacity: canGoNextMonth(month) ? 1 : 0.4 }} disabled={!canGoNextMonth(month)} onClick={() => setMonth(m => shiftMonth(m, 1))}><ChevronRight size={14} /></button>
          <button type="button" aria-label="Print this report" style={{ ...navBtn, opacity: ok ? 1 : 0.4 }} disabled={!ok} onClick={() => printSection("mp")}><Printer size={14} /></button>
        </div>
      </div>

      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data && !ok)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The monthly report could not be loaded.</div>}
      {ok && data && (
        <>
          {data.inProgress && <div style={{ marginBottom: 6 }}><Chip tone="info">Month to date</Chip></div>}
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <tbody>{rows(data).map(([k, v]) => <tr key={k} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><th style={th} scope="row">{k}</th><td style={td}>{v}</td></tr>)}</tbody>
          </table>

          {canManage && <ReportRecipientsPanel siteId={siteId} month={month} monthTitle={monthTitle(month)} inProgress={data.inProgress} reportLoaded={ok} />}

          <div className="mp-print" aria-hidden="true" data-testid="mp-print">
            <style>{PRINT_CSS}</style>
            <h1 style={{ fontSize: 18, margin: "0 0 4px" }}>{printTitle(data)}</h1>
            <div style={{ marginBottom: 8 }}>{data.inProgress ? "Month to date. " : ""}Period {data.from} to {data.to}, South African time.</div>
            <table><tbody>{rows(data).map(([k, v]) => <tr key={k}><th>{k}</th><td>{v}</td></tr>)}</tbody></table>
            <div style={{ marginTop: 10, fontSize: 11 }}>{printedAt()}</div>
          </div>
        </>
      )}
    </section>
  )
}
