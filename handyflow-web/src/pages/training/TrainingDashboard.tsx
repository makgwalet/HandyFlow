// The Training overview. Every figure comes from one server call that counts in the database, so it stays right however many records there are.
import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { GraduationCap, CalendarDays, Users, Award, TriangleAlert } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINING_ACCENT_TEXT } from "./constants"

type Summary = {
  activeCourses: number; scheduledSessions: number; liveEnrollments: number; expiringSoon: number; lapsed: number
  upcoming: { id: string; courseTitle: string; startDate: string; endDate: string; enrolled: number; capacity: number | null }[]
  expiring: { id: string; employeeName: string | null; courseTitle: string | null; expiryDate: string; daysLeft: number }[]
}
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: "18px 20px" }
const label: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: 0.4, textTransform: "uppercase", margin: "0 0 8px" }
const value: React.CSSProperties = { fontSize: 26, fontWeight: 800, color: "var(--hf-text)", margin: 0 }
const row: React.CSSProperties = { display: "flex", justifyContent: "space-between", gap: 10, padding: "12px 16px" }

function Stat({ title, n, icon, warn }: { title: string; n: number | undefined; icon: React.ReactNode; warn?: boolean }) {
  return (
    <div style={card}>
      <p style={label}>{title}</p>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <p style={{ ...value, color: warn && (n ?? 0) > 0 ? "var(--hf-warning-text)" : "var(--hf-text)" }}>{n ?? "—"}</p>
        <span style={{ color: warn && (n ?? 0) > 0 ? "var(--hf-warning-text)" : TRAINING_ACCENT_TEXT }}>{icon}</span>
      </div>
    </div>
  )
}

export default function TrainingDashboard() {
  const q = useQuery<Summary>({ queryKey: ["training-summary"], queryFn: async () => data(await apiClient.get("/api/v1/training/summary")), retry: false })
  const s = q.data
  const needAttention = (s?.expiringSoon ?? 0) + (s?.lapsed ?? 0)

  return (
    <div>
      {q.isError && <div role="alert" style={{ color: "var(--hf-danger-text)", marginBottom: 12 }}>The dashboard could not be loaded.</div>}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 14, marginBottom: 20 }}>
        <Stat title="Active courses" n={s?.activeCourses} icon={<GraduationCap size={22} />} />
        <Stat title="Scheduled sessions" n={s?.scheduledSessions} icon={<CalendarDays size={22} />} />
        <Stat title="Live enrollments" n={s?.liveEnrollments} icon={<Users size={22} />} />
        <Stat title="Certificates expiring (30d)" n={s?.expiringSoon} icon={<Award size={22} />} warn />
        <Stat title="Certificates lapsed" n={s?.lapsed} icon={<TriangleAlert size={22} />} warn />
      </div>

      {s && needAttention > 0 && (
        <div style={{ background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 12, padding: "14px 18px", marginBottom: 20 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <TriangleAlert size={18} style={{ color: "var(--hf-warning-text)" }} />
            <div>
              <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-warning-text-deep)", margin: 0 }}>
                {s.lapsed > 0 && `${s.lapsed} certificate${s.lapsed === 1 ? " has" : "s have"} lapsed`}{s.lapsed > 0 && s.expiringSoon > 0 && ", "}
                {s.expiringSoon > 0 && `${s.expiringSoon} ${s.expiringSoon === 1 ? "expires" : "expire"} within 30 days`}
              </p>
              <p style={{ fontSize: 11.5, color: "var(--hf-warning-text-strong)", margin: 0 }}>Refresher training may be needed. <Link to="/training/certificates">See the Certificates tab</Link>.</p>
            </div>
          </div>
          <div style={{ marginTop: 8, fontSize: 12 }}>
            {s.expiring.map(c => (
              <div key={c.id}>
                {c.employeeName ?? "Employee"}: {c.courseTitle ?? "certificate"}, {c.daysLeft < 0 ? `lapsed ${-c.daysLeft} day${c.daysLeft === -1 ? "" : "s"} ago` : c.daysLeft === 0 ? "expires today" : `expires in ${c.daysLeft} day${c.daysLeft === 1 ? "" : "s"}`} ({c.expiryDate})
              </div>
            ))}
            {needAttention > s.expiring.length && <div style={{ color: "var(--hf-text-muted)" }}>and {needAttention - s.expiring.length} more.</div>}
          </div>
        </div>
      )}

      <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 10px" }}>Upcoming sessions</p>
      {!s || s.upcoming.length === 0 ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>{q.isLoading ? "Loading..." : "Nothing scheduled."}</p>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {s.upcoming.map((u, i) => (
            <div key={u.id} style={{ ...row, borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <p style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)", margin: 0 }}>{u.courseTitle}</p>
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                {u.startDate} → {u.endDate} · {u.enrolled}{u.capacity ? ` of ${u.capacity}` : ""} enrolled
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
