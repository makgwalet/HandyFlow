// src/pages/debtcollection/DebtCollectionDashboard.tsx
//
// Every number here is computed client-side from GET /cases?size=200 — no
// dedicated summary endpoint exists on the real DebtCollectionCaseController
// (only GET /count, which returns a single total, not a status breakdown).
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Landmark, AlertTriangle, ArrowRight, TrendingDown, CalendarClock, CheckCircle,
} from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "R 0.00"
const daysUntil = (d: string) => Math.ceil((new Date(d).getTime() - Date.now()) / 86400000)

const TERMINAL = new Set(["SETTLED", "WRITTEN_OFF", "CLOSED"])

type Tab = "dashboard" | "cases"

export default function DebtCollectionDashboard({ onNavigate }: { onNavigate: (t: Tab) => void }) {
  const { data: cases = [] } = useQuery<any[]>({
    queryKey: ["dc-cases-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/debtcollection/cases?size=200")),
  })

  const cs = cases as any[]
  const openCases = cs.filter(c => !TERMINAL.has(c.status))
  const totalOutstanding = openCases.reduce((s, c) => s + (c.totalOutstanding ?? 0), 0)
  const overdueAction = openCases.filter(c => c.nextActionDate && daysUntil(c.nextActionDate) < 0)
  const dueSoonAction = openCases.filter(c => c.nextActionDate && daysUntil(c.nextActionDate) >= 0 && daysUntil(c.nextActionDate) <= 7)
  const onPlan = cs.filter(c => c.status === "PAYMENT_PLAN_ACTIVE")
  const disputed = cs.filter(c => c.status === "DISPUTED")
  const writtenOffTotal = cs.filter(c => c.status === "WRITTEN_OFF").reduce((s, c) => s + (c.writeOffAmount ?? 0), 0)

  const kpis = [
    { label: "Open cases",        value: openCases.length,            color: "#9A3412", bg: "#FFEDD5", icon: Landmark },
    { label: "Outstanding (open)",value: fmtR(totalOutstanding),      color: "#9A3412", bg: "#FFEDD5", icon: TrendingDown },
    { label: "Next action overdue", value: overdueAction.length,      color: overdueAction.length > 0 ? "#DC2626" : "#166534", bg: overdueAction.length > 0 ? "#FEF2F2" : "#DCFCE7", icon: AlertTriangle },
    { label: "On payment plan",   value: onPlan.length,               color: "#1D4ED8", bg: "#EFF6FF", icon: CheckCircle },
  ]

  const followUps = [...overdueAction, ...dueSoonAction]
    .sort((a, b) => new Date(a.nextActionDate).getTime() - new Date(b.nextActionDate).getTime())
    .slice(0, 8)

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => onNavigate("cases")}
            style={{ background: k.bg, borderRadius: 12, padding: "18px 20px", cursor: "pointer" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" as const }}>{k.label}</div>
              <k.icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 24, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 18 }}>
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Follow-ups due</span>
            <button onClick={() => onNavigate("cases")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#9A3412", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              All cases <ArrowRight size={13} />
            </button>
          </div>

          {followUps.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
              <CheckCircle size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>Nothing due in the next 7 days</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {followUps.map((c: any) => {
                const days = daysUntil(c.nextActionDate)
                return (
                  <div key={c.id} onClick={() => onNavigate("cases")} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", cursor: "pointer" }}>
                    <div style={{ width: 36, height: 36, borderRadius: 9, background: "#FFEDD5", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <CalendarClock size={16} color="#9A3412" />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{c.caseNumber} — {c.debtorName}</div>
                      <div style={{ fontSize: 12, color: "#64748B" }}>{c.status.replace(/_/g, " ")} · {fmtR(c.totalOutstanding)}</div>
                    </div>
                    <div style={{ textAlign: "right" as const, flexShrink: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 12, color: days < 0 ? "#DC2626" : "#D97706" }}>
                        {days < 0 ? `${Math.abs(days)}d overdue` : days === 0 ? "Today" : `in ${days}d`}
                      </div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtDate(c.nextActionDate)}</div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div style={{ background: "#9A3412", borderRadius: 12, padding: 20, color: "#fff" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>At a glance</div>
            {[
              { label: "Total cases",        value: cs.length },
              { label: "Disputed",           value: disputed.length },
              { label: "On payment plan",    value: onPlan.length },
              { label: "Written off (total)",value: fmtR(writtenOffTotal) },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>{s.value}</span>
              </div>
            ))}
          </div>

          {disputed.length > 0 && (
            <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 12, padding: 16 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#92400E", marginBottom: 6 }}>{disputed.length} case{disputed.length === 1 ? "" : "s"} disputed</div>
              <div style={{ fontSize: 12, color: "#92400E" }}>Collection activity is paused pending resolution.</div>
              <button onClick={() => onNavigate("cases")} style={{ marginTop: 10, width: "100%", padding: "7px", background: "#fff", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                Review disputed cases →
              </button>
            </div>
          )}

          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick action</div>
            <button onClick={() => onNavigate("cases")}
              style={{ width: "100%", padding: "9px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, fontWeight: 600, color: "#9A3412", cursor: "pointer", textAlign: "left" as const, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              Open a case <ArrowRight size={13} />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
