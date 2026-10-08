// src/pages/clinic/ClinicDashboard.tsx
import MyDayPanel from "./MyDayPanel"
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { myPractitionerId } from "./currentPractitioner"
import { displayName, greeting, groupQueue, inBuilding, rowAction } from "./dashboardView"
import { Users, Calendar, Clock, CheckCircle, ArrowRight, Search, UserPlus, CalendarPlus, CalendarClock, DoorOpen, AlertTriangle } from "lucide-react"

const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })

/** The clinic-day (South African) date of an instant, yyyy-mm-dd. */
export const dayOf = (iso: string) => new Date(iso).toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export interface DashItem {
  id: string; patientId?: string; patientName: string; practitionerId?: string | null; practitionerName?: string | null; scheduledAt: string
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

interface Props {
  onNavigate: (tab: any) => void
  /** Opens a patient's file (its Overview starts or resumes the consultation). */
  onOpenPatient?: (patientId: string) => void
}

export default function ClinicDashboard({ onNavigate, onOpenPatient }: Props) {
  const qc = useQueryClient()
  const user = useAuthStore(st => st.user)
  const clinician = usePermission("CLINIC_CLINICAL_WRITE")
  const canCheckIn = usePermission("CLINIC_WRITE")
  const { data: summary } = useQuery({
    queryKey: ["clinic-dashboard-summary"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/dashboard/summary"); return (r.data?.data ?? r.data) as DashSummary },
    refetchInterval: 30000,
  })
  const { data: practitioners = [] } = useQuery({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/practitioners/list"); return (r.data?.data ?? r.data) as any[] },
  })
  const { data: recalls } = useQuery({
    queryKey: ["clinic-recalls-overview"], retry: false,
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/recalls?size=1&filter=ALL"); return (r.data?.data ?? r.data)?.counts as { open: number; overdue: number; notContacted: number } | undefined },
  })

  const myId = myPractitionerId(practitioners as any[], user?.email)
  const me = (practitioners as any[]).find(p => p.id === myId)
  const [everyone, setEveryone] = useState(false)
  const scope = clinician && myId && !everyone ? myId : null

  const checkIn = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/clinic/appointments/${id}/check_in`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["clinic-dashboard-summary"] }); qc.invalidateQueries({ queryKey: ["schedule-appts"] }) },
  })

  const todayA = summary?.today ?? []
  const next   = summary?.next ?? null
  const groups = groupQueue(todayA, scope)
  const n = (v: number | undefined) => (v === undefined ? "—" : v)
  const waitingNow = summary ? inBuilding(scope ? todayA.filter(i => i.practitionerId === scope) : todayA) : undefined
  const name = displayName(user, me?.fullName, clinician)

  const tiles = [
    { label: scope ? "My patients waiting" : "Waiting now", value: n(waitingNow), color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", icon: Clock, tab: "waiting-room" },
    { label: "Appointments today", value: n(summary?.todayTotal), color: "var(--hf-primary-text)", bg: "var(--hf-info-soft)", icon: Calendar, tab: "schedule" },
    { label: "Follow-ups overdue", value: n(recalls?.overdue), color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", icon: AlertTriangle, tab: "recalls" },
    { label: "Seen today", value: n(summary?.completed), color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", icon: CheckCircle, tab: "schedule" },
  ]
  const actions = [
    { label: "Find a patient", icon: Search, tab: "patients" },
    { label: "Register patient", icon: UserPlus, tab: "patients" },
    { label: "Book appointment", icon: CalendarPlus, tab: "schedule" },
    { label: "Today's queue", icon: DoorOpen, tab: "waiting-room" },
    { label: "Follow-ups", icon: CalendarClock, tab: "recalls" },
  ]

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 10, marginBottom: 18 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 22, fontWeight: 800, color: "var(--hf-text)" }}>{greeting(new Date())}{name ? `, ${name}` : ""}</h2>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
            {new Date().toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "Africa/Johannesburg" })}
          </div>
        </div>
        {clinician && myId && (
          <div role="group" aria-label="Whose patients" style={{ display: "flex", gap: 4 }}>
            {[{ id: false, label: "My patients" }, { id: true, label: "Everyone" }].map(o => (
              <button key={o.label} aria-pressed={everyone === o.id} onClick={() => setEveryone(o.id)}
                style={{ padding: "6px 14px", borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: "pointer", border: "1px solid var(--hf-border)",
                  background: everyone === o.id ? "var(--hf-primary-text)" : "var(--hf-surface)", color: everyone === o.id ? "var(--hf-surface)" : "var(--hf-text)" }}>{o.label}</button>
            ))}
          </div>
        )}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(190px,1fr))", gap: 14, marginBottom: 18 }}>
        {tiles.map(k => (
          <button key={k.label} type="button" onClick={() => onNavigate(k.tab)}
            style={{ textAlign: "left", background: k.bg, borderRadius: 12, padding: "16px 18px", cursor: "pointer", border: `1px solid ${k.bg}` }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" }}>{k.label}</span>
              <k.icon size={16} style={{ color: k.color }} />
            </div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color }}>{k.value}</div>
          </button>
        ))}
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 22 }}>
        {actions.map(a => (
          <button key={a.label} onClick={() => onNavigate(a.tab)}
            style={{ display: "inline-flex", alignItems: "center", gap: 7, padding: "9px 14px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)",
              borderRadius: 9, fontSize: 13, fontWeight: 600, color: "var(--hf-text)", cursor: "pointer" }}>
            <a.icon size={14} /> {a.label}
          </button>
        ))}
      </div>

      <MyDayPanel onNavigate={onNavigate} />

      <div style={{ display: "grid", gridTemplateColumns: "minmax(0,1fr) 300px", gap: 18 }}>
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Today's queue{scope ? " — my patients" : ""}</span>
            <button onClick={() => onNavigate("schedule")}
              style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-accent-text)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              Open schedule <ArrowRight size={13} />
            </button>
          </div>

          {summary && groups.every(g => g.items.length === 0) ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
              <Calendar size={32} style={{ color: "var(--hf-text-disabled)", marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>{scope ? "No patients for you today" : "No appointments today"}</div>
              <button onClick={() => onNavigate("schedule")}
                style={{ marginTop: 12, padding: "7px 16px", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
                Book appointment
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              {groups.filter(g => g.items.length > 0).map(g => (
                <section key={g.id} aria-label={g.title}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", letterSpacing: "0.05em", marginBottom: 6 }}>{g.title.toUpperCase()} · {g.items.length}</div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {g.items.map(a => {
                      const st = STATUS_CFG[a.status] ?? STATUS_CFG.SCHEDULED
                      const action = rowAction(a.status, clinician, canCheckIn)
                      return (
                        <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "10px 14px", border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
                          <div style={{ textAlign: "center", minWidth: 44 }}>
                            <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>{fmtTime(a.scheduledAt)}</div>
                            <div style={{ fontSize: 10, color: "var(--hf-text-faint)" }}>{a.durationMinutes}m</div>
                          </div>
                          <div style={{ width: 3, height: 36, borderRadius: 2, background: st.color, flexShrink: 0 }} />
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                              {onOpenPatient && a.patientId
                                ? <button onClick={() => onOpenPatient(a.patientId!)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{a.patientName}</button>
                                : <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{a.patientName}</span>}
                              <span style={{ fontSize: 10, fontWeight: 600, background: st.bg, color: st.color, padding: "1px 7px", borderRadius: 20 }}>{st.label}</span>
                            </div>
                            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                              {a.appointmentType?.replace("_", " ")}{a.practitionerName ? ` · ${a.practitionerName}` : ""}
                            </div>
                          </div>
                          {action?.kind === "check_in" && (
                            <button disabled={checkIn.isPending} onClick={() => checkIn.mutate(a.id)}
                              style={{ padding: "6px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontSize: 12, fontWeight: 600, cursor: "pointer", color: "var(--hf-text)" }}>{action.label}</button>
                          )}
                          {action?.kind === "open" && onOpenPatient && a.patientId && (
                            <button onClick={() => onOpenPatient(a.patientId!)}
                              style={{ padding: "6px 12px", borderRadius: 8, border: "none", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>{action.label}</button>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </section>
              ))}
              {checkIn.isError && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>Could not check the patient in. Please try again.</div>}
            </div>
          )}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {next && (
            <div style={{ background: "var(--hf-accent)", borderRadius: 12, padding: 20, color: "var(--hf-text-on-solid)" }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.06em" }}>Next up</div>
              <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 3 }}>{next.patientName}</div>
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.75)", marginBottom: 12 }}>{next.appointmentType?.replace("_", " ")}</div>
              <div style={{ fontSize: 22, fontWeight: 800 }}>{fmtTime(next.scheduledAt)}</div>
              {dayOf(next.scheduledAt) !== summary?.date && <div style={{ fontSize: 12, marginTop: 2 }}>{dayOf(next.scheduledAt)}</div>}
              {next.practitionerName && <div style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", marginTop: 6 }}>Dr. {next.practitionerName}</div>}
            </div>
          )}

          <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 10 }}>Needs attention</div>
            <button onClick={() => onNavigate("recalls")} style={attnStyle}>
              <span>Follow-ups overdue</span><strong>{n(recalls?.overdue)}</strong>
            </button>
            <button onClick={() => onNavigate("recalls")} style={attnStyle}>
              <span>Follow-ups not yet called</span><strong>{n(recalls?.notContacted)}</strong>
            </button>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--hf-text-muted)", padding: "8px 0 0" }}>
              <span><Users size={12} style={{ verticalAlign: "-2px" }} /> Patients on file</span><span>{n(summary?.totalPatients)}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

const attnStyle: React.CSSProperties = { width: "100%", display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0", background: "none", border: "none",
  borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 13, color: "var(--hf-text)", cursor: "pointer", textAlign: "left" }
