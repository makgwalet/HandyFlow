// src/pages/legalcompliance/LegalComplianceDashboard.tsx
//
// Aggregates across all 4 registers. Every number here is computed
// client-side from the same list endpoints the tabs themselves use — there
// is no dedicated dashboard/summary endpoint on the backend for this
// module (confirmed: LegalComplianceCalendarController is the only
// cross-aggregate endpoint, and it only aggregates dates, not counts).
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  ClipboardList, Gavel, Lock, FileSearch, AlertTriangle, CheckCircle,
  ArrowRight, ShieldAlert, Globe2,
} from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const daysUntil = (d: string) => Math.ceil((new Date(d).getTime() - Date.now()) / 86400000)

const SOURCE_CFG: Record<string, { color: string; bg: string; label: string }> = {
  OBLIGATION:       { color: "#4338CA", bg: "#E0E7FF", label: "Obligation" },
  LITIGATION:       { color: "#BE123C", bg: "#FFE4E6", label: "Litigation" },
  CONTRACT_RENEWAL: { color: "#0D9488", bg: "#F0FDFA", label: "Contract"   },
}

const OPEN_LITIGATION_STATUSES = new Set(["OPEN", "IN_PROGRESS"])
const OPEN_DSAR_STATUSES = new Set(["RECEIVED", "IN_PROGRESS"])

type Tab = "dashboard" | "obligations" | "litigation" | "popia" | "dsar" | "calendar"

export default function LegalComplianceDashboard({ onNavigate }: { onNavigate: (t: Tab) => void }) {
  const { data: obligations = [] } = useQuery<any[]>({
    queryKey: ["lc-obligations-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/obligations?size=200")),
  })

  const { data: matters = [] } = useQuery<any[]>({
    queryKey: ["lc-matters-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/matters?size=200")),
  })

  const { data: popiaActivities = [] } = useQuery<any[]>({
    queryKey: ["lc-popia-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/popia-activities")),
  })

  const { data: dsarRequests = [] } = useQuery<any[]>({
    queryKey: ["lc-dsar-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/dsar-requests?size=200")),
  })

  const { data: calendar = [] } = useQuery<any[]>({
    queryKey: ["lc-calendar-30"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/calendar?days=30")),
  })

  const obs = obligations as any[]
  const nonCompliant = obs.filter(o => o.status === "NON_COMPLIANT")
  const overdue = obs.filter(o => o.status === "OVERDUE")
  const dueSoon = obs.filter(o => o.status === "DUE_SOON")

  const mts = matters as any[]
  const openMatters = mts.filter(m => OPEN_LITIGATION_STATUSES.has(m.status))
  const totalExposure = openMatters.reduce((s, m) => s + (m.estimatedExposure ?? 0), 0)

  const activities = popiaActivities as any[]
  const activeActivities = activities.filter(a => a.active)
  const crossBorder = activeActivities.filter(a => a.crossBorderTransfer)

  const dsars = dsarRequests as any[]
  const openDsars = dsars.filter(d => OPEN_DSAR_STATUSES.has(d.status))
  const overdueDsars = dsars.filter(d => d.overdue)

  const kpis = [
    {
      label: "Non-compliant / overdue", value: nonCompliant.length + overdue.length,
      color: (nonCompliant.length + overdue.length) > 0 ? "#DC2626" : "#166534",
      bg: (nonCompliant.length + overdue.length) > 0 ? "#FEF2F2" : "#DCFCE7",
      icon: ShieldAlert, tab: "obligations" as Tab,
    },
    {
      label: "Open litigation matters", value: openMatters.length,
      color: "#BE123C", bg: "#FFE4E6", icon: Gavel, tab: "litigation" as Tab,
    },
    {
      label: "Cross-border transfers", value: crossBorder.length,
      color: crossBorder.length > 0 ? "#D97706" : "#166534",
      bg: crossBorder.length > 0 ? "#FFFBEB" : "#DCFCE7",
      icon: Globe2, tab: "popia" as Tab,
    },
    {
      label: "DSAR overdue", value: overdueDsars.length,
      color: overdueDsars.length > 0 ? "#DC2626" : "#166534",
      bg: overdueDsars.length > 0 ? "#FEF2F2" : "#DCFCE7",
      icon: FileSearch, tab: "dsar" as Tab,
    },
  ]

  const upcoming = (calendar as any[]).slice(0, 8)

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => onNavigate(k.tab)}
            style={{ background: k.bg, borderRadius: 12, padding: "18px 20px", cursor: "pointer" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" as const }}>{k.label}</div>
              <k.icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 18 }}>
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Upcoming (next 30 days)</span>
            <button onClick={() => onNavigate("calendar")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#4338CA", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              Full calendar <ArrowRight size={13} />
            </button>
          </div>

          {upcoming.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
              <CheckCircle size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>Nothing due in the next 30 days</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {upcoming.map((e: any, i: number) => {
                const cfg = SOURCE_CFG[e.sourceType] ?? SOURCE_CFG.OBLIGATION
                const days = daysUntil(e.date)
                return (
                  <div key={`${e.sourceType}-${e.sourceId}-${i}`} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff" }}>
                    <div style={{ padding: "3px 9px", borderRadius: 20, background: cfg.bg, color: cfg.color, fontSize: 10, fontWeight: 700, flexShrink: 0 }}>{cfg.label}</div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{e.title}</div>
                      <div style={{ fontSize: 12, color: "#64748B" }}>{e.detail}</div>
                    </div>
                    <div style={{ textAlign: "right" as const, flexShrink: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 12, color: days < 0 ? "#DC2626" : days <= 7 ? "#D97706" : "#0F172A" }}>
                        {days < 0 ? `${Math.abs(days)}d overdue` : days === 0 ? "Today" : `${days}d`}
                      </div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtDate(e.date)}</div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {(nonCompliant.length > 0 || overdue.length > 0) && (
            <div style={{ marginTop: 24 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 14 }}>
                <AlertTriangle size={15} color="#DC2626" />
                <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Needs attention</span>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {[...nonCompliant, ...overdue].slice(0, 6).map(o => (
                  <div key={o.id} onClick={() => onNavigate("obligations")} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 16px", border: "1px solid #FECACA", borderRadius: 10, background: "#FEF2F2", cursor: "pointer" }}>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{o.title}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{o.category} · Review date {fmtDate(o.reviewDate)}</div>
                    </div>
                    <span style={{ fontSize: 10, fontWeight: 700, color: "#DC2626", background: "#FFF", padding: "3px 9px", borderRadius: 20, border: "1px solid #FECACA" }}>{o.status.replace("_", " ")}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div style={{ background: "#4338CA", borderRadius: 12, padding: 20, color: "#fff" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>At a glance</div>
            {[
              { label: "Regulatory obligations", value: obs.length },
              { label: "Litigation matters",      value: mts.length },
              { label: "Exposure (open matters)", value: fmtR(totalExposure) },
              { label: "POPIA activities (active)", value: activeActivities.length },
              { label: "Open DSAR requests",      value: openDsars.length },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>{s.value}</span>
              </div>
            ))}
          </div>

          {dueSoon.length > 0 && (
            <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 12, padding: 16 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#92400E", marginBottom: 6 }}>{dueSoon.length} obligation{dueSoon.length === 1 ? "" : "s"} due soon</div>
              <div style={{ fontSize: 12, color: "#92400E" }}>Review before they roll to overdue.</div>
              <button onClick={() => onNavigate("obligations")} style={{ marginTop: 10, width: "100%", padding: "7px", background: "#fff", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                Review obligations →
              </button>
            </div>
          )}

          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "New obligation",   tab: "obligations" as Tab, color: "#4338CA", icon: ClipboardList },
              { label: "Open matter",      tab: "litigation"  as Tab, color: "#BE123C", icon: Gavel         },
              { label: "Register activity",tab: "popia"       as Tab, color: "#0D9488", icon: Lock          },
              { label: "Log DSAR",         tab: "dsar"        as Tab, color: "#D97706", icon: FileSearch    },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, fontWeight: 600, color: a.color, cursor: "pointer", textAlign: "left" as const, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
