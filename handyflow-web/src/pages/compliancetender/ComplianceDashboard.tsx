// src/pages/compliancetender/ComplianceDashboard.tsx
//
// Pulls from the same endpoints the tabs already use
// (registrations/all, deadlines, tenders) rather than a dedicated
// dashboard endpoint — no such endpoint exists on the backend yet, and
// building one wasn't necessary given how small these lists are for a
// single tenant.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ShieldCheck, CalendarClock, Briefcase, AlertTriangle } from "lucide-react"

interface Registration { status: string; expiringSoon: boolean }
interface Deadline { dueDate: string; status: string; dueSoon: boolean }
interface Tender { status: string }

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }

export default function ComplianceDashboard({ onNavigate }: { onNavigate: (tab: string) => void }) {
  const { data: registrations = [] } = useQuery<Registration[]>({
    queryKey: ["ct-dashboard", "registrations"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/registrations/all")),
  })
  const { data: deadlines = [] } = useQuery<Deadline[]>({
    queryKey: ["ct-dashboard", "deadlines"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/deadlines")),
  })
  const { data: tenders = [] } = useQuery<Tender[]>({
    queryKey: ["ct-dashboard", "tenders"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/tenders?size=200")),
  })

  const expiringSoon = registrations.filter(r => r.expiringSoon).length
  const expired = registrations.filter(r => r.status === "EXPIRED").length
  const overdueDeadlines = deadlines.filter(d => d.dueDate < new Date().toISOString().slice(0, 10)).length
  const activeTenders = tenders.filter(t => !["AWARDED", "UNSUCCESSFUL", "WITHDRAWN"].includes(t.status)).length

  const cards = [
    { label: "Registrations", value: registrations.length, icon: ShieldCheck, color: "#0369A1", bg: "#E0F2FE", tab: "registrations" },
    { label: "Pending Deadlines", value: deadlines.length, icon: CalendarClock, color: "#D97706", bg: "#FFFBEB", tab: "deadlines" },
    { label: "Active Tenders", value: activeTenders, icon: Briefcase, color: "#166534", bg: "#DCFCE7", tab: "tenders" },
  ]

  const alerts = [
    ...(expired > 0 ? [{ text: `${expired} registration${expired === 1 ? "" : "s"} EXPIRED`, tab: "registrations", severity: "critical" }] : []),
    ...(expiringSoon > 0 ? [{ text: `${expiringSoon} registration${expiringSoon === 1 ? "" : "s"} expiring within 30 days`, tab: "registrations", severity: "warning" }] : []),
    ...(overdueDeadlines > 0 ? [{ text: `${overdueDeadlines} deadline${overdueDeadlines === 1 ? "" : "s"} overdue`, tab: "deadlines", severity: "critical" }] : []),
  ]

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 14, marginBottom: 28 }}>
        {cards.map(c => {
          const Icon = c.icon
          return (
            <button key={c.label} onClick={() => onNavigate(c.tab)}
              style={{ textAlign: "left", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 20, cursor: "pointer" }}>
              <div style={{ width: 38, height: 38, borderRadius: 10, background: c.bg, display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 12 }}>
                <Icon size={18} color={c.color} />
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: "#0F172A" }}>{c.value}</div>
              <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{c.label}</div>
            </button>
          )
        })}
      </div>

      {alerts.length > 0 && (
        <div>
          <div style={{ fontSize: 12, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.05em", marginBottom: 10 }}>Attention Required</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {alerts.map((a, i) => (
              <button key={i} onClick={() => onNavigate(a.tab)}
                style={{ display: "flex", alignItems: "center", gap: 10, textAlign: "left", width: "100%", padding: "12px 16px", borderRadius: 10, cursor: "pointer", border: "none",
                  background: a.severity === "critical" ? "#FEF2F2" : "#FFFBEB" }}>
                <AlertTriangle size={15} color={a.severity === "critical" ? "#DC2626" : "#D97706"} />
                <span style={{ fontSize: 13, fontWeight: 600, color: a.severity === "critical" ? "#DC2626" : "#D97706" }}>{a.text}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {alerts.length === 0 && (registrations.length > 0 || deadlines.length > 0 || tenders.length > 0) && (
        <div style={{ padding: "24px 20px", textAlign: "center", color: "#166534", background: "#DCFCE7", borderRadius: 12, fontSize: 13, fontWeight: 600 }}>
          Nothing needs attention right now.
        </div>
      )}
    </div>
  )
}
