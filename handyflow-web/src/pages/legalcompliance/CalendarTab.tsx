// src/pages/legalcompliance/CalendarTab.tsx
//
// Aggregated calendar — confirmed against the real
// LegalComplianceCalendarController: a single GET /api/v1/legalcompliance
// /calendar?days=N (default 30) returning CalendarEntryResponse[]
// {date, sourceType, sourceId, title, detail}, sourceType one of
// "OBLIGATION" | "LITIGATION" | "CONTRACT_RENEWAL". This is a read-time
// projection recomputed on every call (LegalComplianceCalendarService's own
// Javadoc is explicit that nothing is persisted here) — folding in
// regulatory obligation review dates, litigation matter key dates, and
// contract renewal/expiry dates (read-only, via ContractingFacade) into one
// chronological view. There is no per-entity detail page in this module to
// deep-link sourceId into yet, so entries are display-only.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CalendarDays, ClipboardList, Gavel, FileText, CheckCircle } from "lucide-react"

interface CalendarEntry { date: string; sourceType: string; sourceId: string; title: string; detail: string }

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short", year: "numeric" })
const daysUntil = (d: string) => Math.ceil((new Date(d).getTime() - Date.now()) / 86400000)

const SOURCE_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  OBLIGATION:       { color: "#4338CA", bg: "#EEF2FF", border: "#C7D2FE", label: "Obligation", icon: ClipboardList },
  LITIGATION:       { color: "#BE123C", bg: "#FFE4E6", border: "#FECDD3", label: "Litigation", icon: Gavel         },
  CONTRACT_RENEWAL: { color: "#0D9488", bg: "#F0FDFA", border: "#99F6E4", label: "Contract",   icon: FileText      },
}

const DAY_OPTIONS = [7, 30, 60, 90]

export default function CalendarTab() {
  const [days, setDays] = useState(30)

  const { data: entries = [], isLoading } = useQuery<CalendarEntry[]>({
    queryKey: ["lc-calendar", days],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/legalcompliance/calendar?days=${days}`)),
  })

  // Group by calendar date so multiple entries on the same day sit together.
  const grouped = entries.reduce<Record<string, CalendarEntry[]>>((acc, e) => {
    (acc[e.date] = acc[e.date] ?? []).push(e)
    return acc
  }, {})
  const sortedDates = Object.keys(grouped).sort()

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", gap: 6 }}>
          {DAY_OPTIONS.map(d => (
            <button key={d} onClick={() => setDays(d)}
              style={{ padding: "6px 14px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: days === d ? 600 : 400,
                background: days === d ? "#4338CA" : "#F1F5F9", color: days === d ? "#fff" : "#64748B" }}>
              Next {d} days
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 12, fontSize: 12 }}>
          {Object.entries(SOURCE_CFG).map(([k, cfg]) => (
            <div key={k} style={{ display: "flex", alignItems: "center", gap: 5 }}>
              <span style={{ width: 8, height: 8, borderRadius: "50%", background: cfg.color, display: "inline-block" }} />
              <span style={{ color: "#64748B" }}>{cfg.label}</span>
            </div>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading calendar...</div>
      ) : entries.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <CheckCircle size={40} style={{ marginBottom: 12, opacity: 0.4, color: "#86EFAC" }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Nothing due in the next {days} days</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          {sortedDates.map(date => {
            const dEntries = grouped[date]
            const d = daysUntil(date)
            return (
              <div key={date}>
                <div style={{ display: "flex", alignItems: "baseline", gap: 10, marginBottom: 8 }}>
                  <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>{fmtDate(date)}</span>
                  <span style={{ fontSize: 11, fontWeight: 600, color: d < 0 ? "#DC2626" : d === 0 ? "#D97706" : "#94A3B8" }}>
                    {d < 0 ? `${Math.abs(d)} days ago` : d === 0 ? "Today" : `in ${d} days`}
                  </span>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {dEntries.map((e, i) => {
                    const cfg = SOURCE_CFG[e.sourceType] ?? SOURCE_CFG.OBLIGATION
                    const Icon = cfg.icon
                    return (
                      <div key={`${e.sourceId}-${i}`} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: `1px solid ${cfg.border}`, borderRadius: 10, background: cfg.bg }}>
                        <div style={{ width: 36, height: 36, borderRadius: 9, background: "#fff", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                          <Icon size={16} color={cfg.color} />
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{e.title}</div>
                          <div style={{ fontSize: 12, color: "#475569" }}>{e.detail}</div>
                        </div>
                        <span style={{ fontSize: 10, fontWeight: 700, color: cfg.color, background: "#fff", padding: "3px 9px", borderRadius: 20, flexShrink: 0 }}>{cfg.label}</span>
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
