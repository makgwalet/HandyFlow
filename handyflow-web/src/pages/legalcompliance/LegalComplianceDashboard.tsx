// src/pages/legalcompliance/LegalComplianceDashboard.tsx
//
// The Legal & Compliance dashboard. Three reads, all existing endpoints:
//  - GET /api/v1/legalcompliance/executive   (counts of obligations, risks and findings; "attention" items)
//  - GET /api/v1/legalcompliance/calendar?days=62   (deadlines and the month calendar)
//  - GET /api/v1/legalcompliance/reports/AUDIT_TRAIL   (recent activity; administrators only, the report is admin-only)
// The donut is the split of obligations by status, not a composite score. A percentage shows "—" when there is nothing to measure. All the arithmetic is in dashboardLogic.ts.
import { useMemo } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { one, fmtDate, fmtDateTime } from "./lcHelpers"
import { Badge } from "./LcUi"
import { ArrowRight, CheckCircle, AlertTriangle } from "lucide-react"
import {
  statCards, donut, arcs, riskBars, deadlines, monthGrid, activityRows, greeting, isoDay, sourceColour, SOURCE_COLOURS, pctText,
  type Metrics, type CalendarEntry, type StatCard,
} from "./dashboardLogic"

interface Overview { generatedAt: string; metrics: Metrics; attention: { key: string; severity: "CRITICAL" | "WARNING"; text: string; count: number; tab: string }[] }

const TONES: Record<StatCard["tone"], { color: string; bar: string }> = {
  neutral: { color: "#0F172A", bar: "#4338CA" }, good: { color: "#166534", bar: "#16A34A" }, warn: { color: "#B45309", bar: "#F59E0B" }, bad: { color: "#B91C1C", bar: "#DC2626" },
}
const DEADLINE_BADGE = { bad: { color: "#991B1B", bg: "#FEE2E2" }, warn: { color: "#92400E", bg: "#FEF3C7" }, info: { color: "#3730A3", bg: "#E0E7FF" }, neutral: { color: "#475569", bg: "#E2E8F0" } } as const
const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
const card: React.CSSProperties = { background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 18, minWidth: 0 }
const heading: React.CSSProperties = { fontSize: 14, fontWeight: 800, color: "#0F172A", margin: 0 }
const link: React.CSSProperties = { display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#4338CA", background: "none", border: "none", cursor: "pointer", fontWeight: 600, padding: 0 }

export default function LegalComplianceDashboard({ onNavigate }: { onNavigate: (tab: string) => void }) {
  const isAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const firstName = useAuthStore(s => s.user?.firstName)
  const now = useMemo(() => new Date(), [])
  const todayIso = isoDay(now)

  const { data: ov, isLoading, isError } = useQuery<Overview>({ queryKey: ["lc-exec", "overview"], queryFn: async () => one(await apiClient.get("/api/v1/legalcompliance/executive")) })
  const { data: entries = [] } = useQuery<CalendarEntry[]>({ queryKey: ["lc-calendar-62"], queryFn: async () => one(await apiClient.get("/api/v1/legalcompliance/calendar?days=62")) ?? [] })
  const { data: trail } = useQuery<{ rows: string[][] }>({ queryKey: ["lc-dashboard-activity"], enabled: isAdmin, staleTime: 60_000, queryFn: async () => one(await apiClient.get("/api/v1/legalcompliance/reports/AUDIT_TRAIL")) })

  const cards = statCards(ov?.metrics)
  const split = donut(ov?.metrics)
  const ring = arcs(split)
  const bars = riskBars(ov?.metrics)
  const upcoming = deadlines(entries, todayIso, 5)
  const weeks = monthGrid(now.getFullYear(), now.getMonth(), entries, todayIso)
  const activity = activityRows(trail?.rows, 6)
  const attention = ov?.attention ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", gap: 12, flexWrap: "wrap", marginBottom: 18 }}>
        <div>
          <h2 style={{ fontSize: 22, fontWeight: 800, color: "#0F172A", margin: 0 }}>{greeting(now.getHours())}{firstName ? `, ${firstName}` : ""}</h2>
          <p style={{ fontSize: 13, color: "#64748B", margin: "4px 0 0" }}>Here’s your compliance overview for today.</p>
        </div>
        <div style={{ textAlign: "right", fontSize: 12, color: "#64748B" }}>
          <div style={{ fontWeight: 700, color: "#334155" }}>{fmtDate(todayIso)}</div>
          {ov?.generatedAt && <div>Last updated {fmtDateTime(ov.generatedAt)}</div>}
        </div>
      </div>

      {isError && <p style={{ color: "#B91C1C", fontSize: 13 }}>Could not load the overview. Try again in a moment.</p>}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 16 }}>
        {cards.map(c => (
          <button key={c.key} onClick={() => onNavigate(c.tab)} aria-label={`${c.label}: ${c.value}. ${c.note}`}
            style={{ ...card, textAlign: "left", cursor: "pointer", borderLeft: `4px solid ${TONES[c.tone].bar}`, opacity: isLoading ? 0.6 : 1 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase", letterSpacing: "0.04em" }}>{c.label}</div>
            <div style={{ fontSize: 30, fontWeight: 800, color: TONES[c.tone].color, margin: "6px 0 2px" }}>{c.value}</div>
            <div style={{ fontSize: 12, color: "#64748B" }}>{c.note}</div>
          </button>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 12, marginBottom: 12 }}>
        <section style={card} aria-label="Compliance status">
          <h3 style={heading}>Compliance status</h3>
          {split.total === 0 ? <p style={{ fontSize: 13, color: "#64748B" }}>No obligations recorded yet.</p> : (
            <div style={{ display: "flex", alignItems: "center", gap: 18, marginTop: 12, flexWrap: "wrap" }}>
              <svg viewBox="0 0 42 42" width={130} height={130} role="img" aria-label={`${pctText(split.compliantPercent)} of ${split.total} obligations are compliant`}>
                <circle cx={21} cy={21} r={15.9155} fill="none" stroke="#F1F5F9" strokeWidth={5} />
                {ring.map(a => <circle key={a.key} cx={21} cy={21} r={15.9155} fill="none" stroke={a.color} strokeWidth={5} pathLength={100} strokeDasharray={`${a.dash} ${100 - a.dash}`} strokeDashoffset={a.offset} transform="rotate(-90 21 21)" />)}
                <text x={21} y={21.5} textAnchor="middle" fontSize={8} fontWeight={800} fill="#0F172A">{pctText(split.compliantPercent)}</text>
                <text x={21} y={27} textAnchor="middle" fontSize={3.4} fill="#64748B">compliant</text>
              </svg>
              <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 13, display: "flex", flexDirection: "column", gap: 6 }}>
                {split.segments.filter(s => s.count > 0 || s.key !== "other").map(s => (
                  <li key={s.key} style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <span aria-hidden style={{ width: 10, height: 10, borderRadius: 3, background: s.color, display: "inline-block" }} />
                    <span style={{ color: "#475569", minWidth: 70 }}>{s.label}</span><strong>{s.count}</strong>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>

        <section style={card} aria-label="Upcoming deadlines">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={heading}>Upcoming deadlines</h3>
            <button style={link} onClick={() => onNavigate("calendar")}>View all <ArrowRight size={12} /></button>
          </div>
          {upcoming.length === 0 ? (
            <div style={{ textAlign: "center", padding: "24px 0", color: "#94A3B8" }}><CheckCircle size={26} color="#CBD5E1" /><div style={{ fontSize: 13, marginTop: 6 }}>Nothing due soon</div></div>
          ) : (
            <ul style={{ listStyle: "none", margin: "10px 0 0", padding: 0, display: "flex", flexDirection: "column", gap: 10 }}>
              {upcoming.map(d => {
                const b = DEADLINE_BADGE[d.tone]
                return (
                  <li key={d.key} style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center" }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{d.title}</div>
                      <div style={{ fontSize: 11, color: "#64748B" }}>{sourceColour(d.sourceType).label} · {fmtDate(d.date)}</div>
                    </div>
                    <Badge text={d.label} color={b.color} bg={b.bg} />
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        <section style={card} aria-label="Risk overview">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={heading}>Risk overview</h3>
            <button style={link} onClick={() => onNavigate("risks")}>Risk register <ArrowRight size={12} /></button>
          </div>
          <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-around", height: 130, marginTop: 14 }}>
            {bars.map(b => (
              <div key={b.key} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4, flex: 1 }}>
                <strong style={{ fontSize: 13 }}>{b.count}</strong>
                <div title={`${b.label}: ${b.count}`} style={{ width: 34, height: `${Math.max(b.heightPercent, 0) * 0.8}px`, background: b.color, borderRadius: "6px 6px 0 0", minHeight: b.count > 0 ? 4 : 0 }} />
              </div>
            ))}
          </div>
          <div style={{ display: "flex", justifyContent: "space-around", borderTop: "1px solid #E2E8F0", paddingTop: 6 }}>
            {bars.map(b => <span key={b.key} style={{ fontSize: 11, color: "#64748B", flex: 1, textAlign: "center" }}>{b.label}</span>)}
          </div>
        </section>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 12 }}>
        <section style={card} aria-label={isAdmin ? "Recent activity" : "Needs attention"}>
          {isAdmin ? (
            <>
              <h3 style={heading}>Recent activity</h3>
              {activity.length === 0 ? <p style={{ fontSize: 13, color: "#64748B" }}>No recent activity.</p> : (
                <ul style={{ listStyle: "none", margin: "10px 0 0", padding: 0, display: "flex", flexDirection: "column", gap: 10 }}>
                  {activity.map((a, i) => (
                    <li key={i} style={{ display: "flex", justifyContent: "space-between", gap: 10, fontSize: 13 }}>
                      <span style={{ minWidth: 0 }}><strong style={{ color: "#0F172A" }}>{a.event}</strong> <span style={{ color: "#64748B" }}>· {a.area}{a.by ? ` · ${a.by}` : ""}</span></span>
                      <span style={{ fontSize: 11, color: "#94A3B8", whiteSpace: "nowrap" }}>{a.when}</span>
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : (
            <>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}><AlertTriangle size={14} color="#B45309" /><h3 style={heading}>Needs attention</h3></div>
              {attention.length === 0 ? <p style={{ fontSize: 13, color: "#64748B" }}>Nothing needs attention right now.</p> : (
                <ul style={{ listStyle: "none", margin: "10px 0 0", padding: 0, display: "flex", flexDirection: "column", gap: 8 }}>
                  {attention.slice(0, 6).map(a => (
                    <li key={a.key}><button onClick={() => onNavigate(a.tab)} style={{ ...link, color: "#0F172A", fontSize: 13, fontWeight: 500, textAlign: "left", gap: 8 }}>
                      <Badge text={a.severity === "CRITICAL" ? "Critical" : "Warning"} color={a.severity === "CRITICAL" ? "#991B1B" : "#92400E"} bg={a.severity === "CRITICAL" ? "#FEE2E2" : "#FEF3C7"} />{a.text}
                    </button></li>
                  ))}
                </ul>
              )}
            </>
          )}
        </section>

        <section style={card} aria-label="Compliance calendar">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={heading}>Compliance calendar · {MONTHS[now.getMonth()]} {now.getFullYear()}</h3>
            <button style={link} onClick={() => onNavigate("calendar")}>Open <ArrowRight size={12} /></button>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 8, textAlign: "center", fontSize: 12 }}>
            <thead><tr>{["M", "T", "W", "T", "F", "S", "S"].map((d, i) => <th key={i} style={{ color: "#94A3B8", fontWeight: 600, padding: 3 }}>{d}</th>)}</tr></thead>
            <tbody>
              {weeks.map((w, i) => (
                <tr key={i}>{w.map((c, j) => (
                  <td key={j} style={{ padding: 2 }}>
                    {c && (
                      <div title={c.types.map(t => sourceColour(t).label).join(", ") || undefined} aria-label={c.types.length ? `${c.day}: ${c.types.map(t => sourceColour(t).label).join(", ")}` : undefined}
                        style={{ margin: "0 auto", width: 26, height: 26, lineHeight: "26px", borderRadius: "50%", background: c.today ? "#4338CA" : "transparent", color: c.today ? "#fff" : "#334155", fontWeight: c.today ? 800 : 500, position: "relative" }}>
                        {c.day}
                        {c.types.length > 0 && <span aria-hidden style={{ position: "absolute", left: "50%", bottom: -1, transform: "translateX(-50%)", display: "flex", gap: 2 }}>
                          {c.types.slice(0, 3).map(t => <i key={t} style={{ width: 4, height: 4, borderRadius: "50%", background: sourceColour(t).color, display: "block" }} />)}
                        </span>}
                      </div>
                    )}
                  </td>
                ))}</tr>
              ))}
            </tbody>
          </table>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginTop: 10 }}>
            {Object.entries(SOURCE_COLOURS).map(([k, v]) => <span key={k} style={{ fontSize: 11, color: "#64748B", display: "flex", alignItems: "center", gap: 5 }}><i style={{ width: 8, height: 8, borderRadius: "50%", background: v.color, display: "inline-block" }} />{v.label}</span>)}
          </div>
        </section>
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 14 }}>
        {[["Litigation & legal cases", "litigation"], ["POPIA & privacy", "popia"], ["DSAR requests", "dsar"], ["Executive overview", "executive"]].map(([label, tab]) => (
          <button key={tab} onClick={() => onNavigate(tab)} style={{ ...link, padding: "6px 12px", border: "1px solid #E2E8F0", borderRadius: 20, background: "#fff" }}>{label} <ArrowRight size={12} /></button>
        ))}
      </div>
    </div>
  )
}
