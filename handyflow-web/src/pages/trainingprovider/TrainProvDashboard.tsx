// src/pages/trainingprovider/TrainProvDashboard.tsx
//
// KPIs from list-endpoint totals only — same "no N+1, no invented
// cross-entity endpoint" discipline as every other dashboard in this
// engagement: active clients, active courses, upcoming sessions
// (SCHEDULED, next 5, both PUBLIC and CLOSED), live enrollments, and
// certificates expiring within 30 days (client-side filter over up to
// 200 VALID certificates — no dedicated "expiring soon" endpoint
// confirmed, same simplification Module 4a's own dashboard uses).
import { useQuery } from "@tanstack/react-query"
import { GraduationCap, CalendarDays, Users, Award, Building2, TriangleAlert, Globe, Lock } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"
import type { SessionResponse } from "./TrainProvSessionsTab"

interface Page<T> { content: T[]; totalElements: number }
interface CertificateResponse { id: string; delegateNameSnapshot: string; clientNameSnapshot: string; courseTitleSnapshot: string; expiryDate: string | null; status: string }

const card: React.CSSProperties = { background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: "18px 20px" }
const label: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: "#94A3B8", letterSpacing: 0.4, textTransform: "uppercase", margin: "0 0 8px" }
const value: React.CSSProperties = { fontSize: 26, fontWeight: 800, color: "#0F172A", margin: 0 }

export default function TrainProvDashboard() {
  const { data: clients } = useQuery<Page<unknown>>({
    queryKey: ["trainprov-clients", "ACTIVE", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/clients", { params: { status: "ACTIVE", size: 1 } })).data,
  })
  const { data: courses } = useQuery<Page<unknown>>({
    queryKey: ["trainprov-courses", "ACTIVE", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/courses", { params: { status: "ACTIVE", size: 1 } })).data,
  })
  const { data: scheduledSessions } = useQuery<Page<SessionResponse>>({
    queryKey: ["trainprov-sessions", "SCHEDULED", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/sessions", { params: { status: "SCHEDULED", size: 5 } })).data,
  })
  const { data: liveEnrollments } = useQuery<Page<unknown>>({
    queryKey: ["trainprov-enrollments", "ENROLLED", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/enrollments", { params: { status: "ENROLLED", size: 1 } })).data,
  })
  const { data: validCertificates } = useQuery<Page<CertificateResponse>>({
    queryKey: ["trainprov-certificates", "VALID", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/certificates", { params: { status: "VALID", size: 200 } })).data,
  })

  const today = new Date()
  const in30 = new Date(today.getTime() + 30 * 86400000)
  const expiringSoon = (validCertificates?.content ?? []).filter(c => c.expiryDate && new Date(c.expiryDate) <= in30)

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 14, marginBottom: 20 }}>
        <div style={card}>
          <p style={label}>Active clients</p>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <p style={value}>{clients?.totalElements ?? "—"}</p>
            <Building2 size={22} color={TRAINPROV_ACCENT} />
          </div>
        </div>
        <div style={card}>
          <p style={label}>Active courses</p>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <p style={value}>{courses?.totalElements ?? "—"}</p>
            <GraduationCap size={22} color={TRAINPROV_ACCENT} />
          </div>
        </div>
        <div style={card}>
          <p style={label}>Scheduled sessions</p>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <p style={value}>{scheduledSessions?.totalElements ?? "—"}</p>
            <CalendarDays size={22} color={TRAINPROV_ACCENT} />
          </div>
        </div>
        <div style={card}>
          <p style={label}>Live enrollments</p>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <p style={value}>{liveEnrollments?.totalElements ?? "—"}</p>
            <Users size={22} color={TRAINPROV_ACCENT} />
          </div>
        </div>
        <div style={card}>
          <p style={label}>Certificates expiring (30d)</p>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <p style={{ ...value, color: expiringSoon.length > 0 ? "#D97706" : "#0F172A" }}>{expiringSoon.length}</p>
            <Award size={22} color={expiringSoon.length > 0 ? "#D97706" : TRAINPROV_ACCENT} />
          </div>
        </div>
      </div>

      {expiringSoon.length > 0 && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 12, padding: "14px 18px", marginBottom: 20 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <TriangleAlert size={18} color="#D97706" />
            <div>
              <p style={{ fontSize: 13, fontWeight: 700, color: "#92400E", margin: 0 }}>{expiringSoon.length} certificate{expiringSoon.length === 1 ? "" : "s"} expiring within 30 days</p>
              <p style={{ fontSize: 11.5, color: "#B45309", margin: 0 }}>Refresher training may be needed — see the Certificates tab.</p>
            </div>
          </div>
        </div>
      )}

      <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: "0 0 10px" }}>Upcoming sessions</p>
      {!scheduledSessions || scheduledSessions.content.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Nothing scheduled.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {scheduledSessions.content.map((s, i) => (
            <div key={s.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{s.courseTitle}</p>
                <span style={{ display: "flex", alignItems: "center", gap: 3, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: s.sessionType === "PUBLIC" ? "#EFF6FF" : "#F5F3FF", color: s.sessionType === "PUBLIC" ? "#1D4ED8" : "#6D28D9" }}>
                  {s.sessionType === "PUBLIC" ? <Globe size={10} /> : <Lock size={10} />} {s.sessionType}
                </span>
              </div>
              <span style={{ fontSize: 12, color: "#64748B" }}>{s.startDate} → {s.endDate}{s.clientName ? ` · ${s.clientName}` : ""} · {s.enrolledCount} enrolled</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
