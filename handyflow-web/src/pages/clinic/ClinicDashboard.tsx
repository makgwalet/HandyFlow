// src/pages/clinic/ClinicDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Users, Calendar, FileText, Clock, CheckCircle, AlertCircle, ArrowRight, TrendingUp } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p }
const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const today   = new Date().toISOString().split("T")[0]

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  SCHEDULED:   { color: "#1D4ED8", bg: "#EFF6FF", label: "Scheduled" },
  CONFIRMED:   { color: "#7C3AED", bg: "#F5F3FF", label: "Confirmed" },
  IN_PROGRESS: { color: "#D97706", bg: "#FFFBEB", label: "In Progress" },
  COMPLETED:   { color: "#166534", bg: "#DCFCE7", label: "Completed" },
  CANCELLED:   { color: "#DC2626", bg: "#FEF2F2", label: "Cancelled" },
  NO_SHOW:     { color: "#64748B", bg: "#F8FAFC", label: "No Show" },
}

export default function ClinicDashboard({ onNavigate }: { onNavigate: (tab: any) => void }) {
  const { data: apptData } = useQuery({
    queryKey: ["clinic-appts-dashboard"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/appointments?size=100"); return unwrap(r) as any[] },
  })
  const { data: patientsData } = useQuery({
    queryKey: ["clinic-patients-dashboard"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/patients?size=1"); return (r.data?.data ?? r.data) },
  })
  const { data: practitioners = [] } = useQuery({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/practitioners/list"); return (r.data?.data ?? r.data) as any[] },
  })

  const appts    = apptData ?? []
  const todayA   = appts.filter((a: any) => a.scheduledAt?.startsWith(today))
  const upcoming = appts
    .filter((a: any) => ["SCHEDULED","CONFIRMED"].includes(a.status) && a.scheduledAt >= new Date().toISOString())
    .sort((a: any, b: any) => a.scheduledAt.localeCompare(b.scheduledAt))

  const kpis = [
    { label: "Today's appointments", value: todayA.length,
      color: "#1B3A6B", bg: "#EFF6FF", icon: Calendar, tab: "appointments" },
    { label: "Awaiting today",
      value: todayA.filter((a: any) => ["SCHEDULED","CONFIRMED"].includes(a.status)).length,
      color: "#D97706", bg: "#FFFBEB", icon: Clock, tab: "appointments" },
    { label: "Completed today",
      value: todayA.filter((a: any) => a.status === "COMPLETED").length,
      color: "#166534", bg: "#DCFCE7", icon: CheckCircle, tab: "appointments" },
    { label: "Total patients",
      value: (patientsData as any)?.totalElements ?? "—",
      color: "#7C3AED", bg: "#F5F3FF", icon: Users, tab: "patients" },
  ]

  return (
    <div>
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
              <k.icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 18 }}>
        {/* Today's schedule */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>
              Today — {new Date().toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long" })}
            </span>
            <button onClick={() => onNavigate("appointments")}
              style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#0D9488",
                background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              View all <ArrowRight size={13} />
            </button>
          </div>

          {todayA.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0",
              borderRadius: 12, color: "#94A3B8" }}>
              <Calendar size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No appointments today</div>
              <button onClick={() => onNavigate("appointments")}
                style={{ marginTop: 12, padding: "7px 16px", background: "#0D9488", color: "#fff",
                  border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
                Book appointment
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {todayA.sort((a: any, b: any) => a.scheduledAt.localeCompare(b.scheduledAt)).map((a: any) => {
                const s = STATUS_CFG[a.status] ?? STATUS_CFG.SCHEDULED
                return (
                  <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px",
                    border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff" }}>
                    <div style={{ textAlign: "center", minWidth: 44 }}>
                      <div style={{ fontSize: 15, fontWeight: 700, color: "#0F172A" }}>{fmtTime(a.scheduledAt)}</div>
                      <div style={{ fontSize: 10, color: "#94A3B8" }}>{a.durationMinutes}m</div>
                    </div>
                    <div style={{ width: 3, height: 36, borderRadius: 2, background: s.color, flexShrink: 0 }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{a.patientName}</span>
                        <span style={{ fontSize: 10, fontWeight: 600, background: s.bg, color: s.color,
                          padding: "1px 7px", borderRadius: 20 }}>{s.label}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "#64748B" }}>
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
          {upcoming[0] && (
            <div style={{ background: "#0D9488", borderRadius: 12, padding: 20, color: "#fff" }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 8,
                textTransform: "uppercase", letterSpacing: "0.06em" }}>Next up</div>
              <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 3 }}>{upcoming[0].patientName}</div>
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.75)", marginBottom: 12 }}>
                {upcoming[0].appointmentType?.replace("_"," ")}
              </div>
              <div style={{ fontSize: 22, fontWeight: 800 }}>{fmtTime(upcoming[0].scheduledAt)}</div>
              {upcoming[0].practitionerName && (
                <div style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", marginTop: 6 }}>
                  Dr. {upcoming[0].practitionerName}
                </div>
              )}
            </div>
          )}

          {/* Practitioners */}
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12 }}>Practitioners</div>
            {(practitioners as any[]).slice(0, 5).map((p: any) => (
              <div key={p.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "7px 0",
                borderBottom: "1px solid #F1F5F9" }}>
                <div style={{ width: 30, height: 30, borderRadius: "50%", background: "#E0F2FE",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontSize: 11, fontWeight: 700, color: "#0369A1", flexShrink: 0 }}>
                  {p.firstName?.[0]}{p.lastName?.[0]}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A",
                    overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{p.fullName}</div>
                  <div style={{ fontSize: 11, color: "#94A3B8" }}>{p.specialty}</div>
                </div>
              </div>
            ))}
            {practitioners.length === 0 && (
              <div style={{ fontSize: 13, color: "#94A3B8" }}>No practitioners added</div>
            )}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Register patient",    tab: "patients",      color: "#1B3A6B" },
              { label: "Book appointment",    tab: "appointments",  color: "#0D9488" },
              { label: "Record consultation", tab: "consultations", color: "#7C3AED" },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "#fff",
                  border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, fontWeight: 600,
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
