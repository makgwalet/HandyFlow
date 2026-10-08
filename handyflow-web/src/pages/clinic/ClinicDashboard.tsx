// src/pages/clinic/ClinicDashboard.tsx
import MyDayPanel from "./MyDayPanel"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Users, Calendar, Clock, CheckCircle, ArrowRight } from "lucide-react"

const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })

/** The clinic-day (South African) date of an instant, yyyy-mm-dd. */
export const dayOf = (iso: string) => new Date(iso).toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export interface DashItem {
  id: string; patientName: string; practitionerName?: string | null; scheduledAt: string
  durationMinutes: number; appointmentType?: string | null; status: string
}
export interface DashSummary {
  date: string; todayTotal: number; awaiting: number; inProgress: number; completed: number
  cancelled: number; noShow: number; totalPatients: number; today: DashItem[]; next: DashItem | null
}

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  SCHEDULED:   { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", label: "Scheduled" },
  CONFIRMED:   { color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", label: "Confirmed" },
  CHECKED_IN:  { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", label: "Checked in" },
  TRIAGED:     { color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", label: "Triaged" },
  IN_PROGRESS: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "In Progress" },
  COMPLETED:   { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", label: "Completed" },
  CANCELLED:   { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Cancelled" },
  NO_SHOW:     { color: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)", label: "No Show" },
}

export default function ClinicDashboard({ onNavigate }: { onNavigate: (tab: any) => void }) {
  const { data: summary } = useQuery({
    queryKey: ["clinic-dashboard-summary"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/dashboard/summary"); return (r.data?.data ?? r.data) as DashSummary },
    refetchInterval: 60000,
  })
  const { data: practitioners = [] } = useQuery({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/practitioners/list"); return (r.data?.data ?? r.data) as any[] },
  })

  const todayA = summary?.today ?? []
  const next   = summary?.next ?? null
  const n = (v: number | undefined) => (v === undefined ? "—" : v)

  const kpis = [
    { label: "Today's appointments", value: n(summary?.todayTotal),
      color: "var(--hf-primary-text)", bg: "var(--hf-info-soft)", icon: Calendar, tab: "schedule" },
    { label: "Awaiting today", value: n(summary?.awaiting),
      color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", icon: Clock, tab: "waiting-room" },
    { label: "Completed today", value: n(summary?.completed),
      color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", icon: CheckCircle, tab: "schedule" },
    { label: "Total patients", value: n(summary?.totalPatients),
      color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", icon: Users, tab: "patients" },
  ]

  return (
    <div>
      <MyDayPanel onNavigate={onNavigate} />
      {/* KPI cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => onNavigate(k.tab)}
            style={{ background: k.bg, borderRadius: 12, padding: "18px 20px", cursor: "pointer",
              border: `1px solid ${k.bg}` }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" }}>{k.label}</div>
              <k.icon size={16} style={{ color: k.color }} />
            </div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 18 }}>
        {/* Today's schedule */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>
              Today — {new Date().toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long" })}
            </span>
            <button onClick={() => onNavigate("schedule")}
              style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-accent-text)",
                background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              View all <ArrowRight size={13} />
            </button>
          </div>

          {todayA.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed var(--hf-border)",
              borderRadius: 12, color: "var(--hf-text-faint)" }}>
              <Calendar size={32} style={{ color: 'var(--hf-text-disabled)', marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No appointments today</div>
              <button onClick={() => onNavigate("schedule")}
                style={{ marginTop: 12, padding: "7px 16px", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)",
                  border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
                Book appointment
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {todayA.map((a) => {
                const s = STATUS_CFG[a.status] ?? STATUS_CFG.SCHEDULED
                return (
                  <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px",
                    border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
                    <div style={{ textAlign: "center", minWidth: 44 }}>
                      <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>{fmtTime(a.scheduledAt)}</div>
                      <div style={{ fontSize: 10, color: "var(--hf-text-faint)" }}>{a.durationMinutes}m</div>
                    </div>
                    <div style={{ width: 3, height: 36, borderRadius: 2, background: s.color, flexShrink: 0 }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{a.patientName}</span>
                        <span style={{ fontSize: 10, fontWeight: 600, background: s.bg, color: s.color,
                          padding: "1px 7px", borderRadius: 20 }}>{s.label}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                        {a.appointmentType?.replace("_"," ")}
                        {a.practitionerName ? ` · ${a.practitionerName}` : ""}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Next up */}
          {next && (
            <div style={{ background: "var(--hf-accent)", borderRadius: 12, padding: 20, color: "var(--hf-text-on-solid)" }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 8,
                textTransform: "uppercase", letterSpacing: "0.06em" }}>Next up</div>
              <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 3 }}>{next.patientName}</div>
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.75)", marginBottom: 12 }}>
                {next.appointmentType?.replace("_"," ")}
              </div>
              <div style={{ fontSize: 22, fontWeight: 800 }}>{fmtTime(next.scheduledAt)}</div>
              {dayOf(next.scheduledAt) !== summary?.date && (
                <div style={{ fontSize: 12, marginTop: 2 }}>{dayOf(next.scheduledAt)}</div>
              )}
              {next.practitionerName && (
                <div style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", marginTop: 6 }}>
                  Dr. {next.practitionerName}
                </div>
              )}
            </div>
          )}

          {/* Practitioners */}
          <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12 }}>Practitioners</div>
            {(practitioners as any[]).slice(0, 5).map((p: any) => (
              <div key={p.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "7px 0",
                borderBottom: "1px solid var(--hf-border-subtle)" }}>
                <div style={{ width: 30, height: 30, borderRadius: "50%", background: "var(--hf-sky-soft-strong)",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontSize: 11, fontWeight: 700, color: "var(--hf-sky-text-strong)", flexShrink: 0 }}>
                  {p.firstName?.[0]}{p.lastName?.[0]}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)",
                    overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{p.fullName}</div>
                  <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{p.specialty}</div>
                </div>
              </div>
            ))}
            {practitioners.length === 0 && (
              <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No practitioners added</div>
            )}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Register patient",    tab: "patients",      color: "var(--hf-primary-text)" },
              { label: "Book appointment",    tab: "schedule",  color: "var(--hf-accent-text)" },
              { label: "Find a patient to consult", tab: "patients", color: "var(--hf-violet-text)" },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "var(--hf-surface)",
                  border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, fontWeight: 600,
                  color: a.color, cursor: "pointer", textAlign: "left", display: "flex",
                  alignItems: "center", justifyContent: "space-between" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
