// The learner's own records, read-only: their enrolments with attendance counts and recorded outcome, and their upcoming sessions.
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { GraduationCap, LogOut } from "lucide-react"
import { trainProvPortalClient } from "../../api/trainProvPortal.client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import PortalEnrolmentCard from "./PortalEnrolmentCard"
import type { LearnerMe } from "./learnerPortalRules"

const muted = { color: "var(--hf-text-faint)", fontSize: 13 } as const

export function LearnerPortalHomePage() {
  const navigate = useNavigate()
  const logout = usePortalAuthStore(s => s.logout)
  const { data, isLoading, isError } = useQuery<LearnerMe>({
    queryKey: ["trainprov-learner-portal-me"],
    queryFn: async () => (await trainProvPortalClient.get("/api/v1/training-provider/portal/learner/me")).data,
  })
  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{ background: "var(--hf-surface)", borderBottom: "1px solid var(--hf-border)", padding: "16px 32px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}><GraduationCap size={20} /><strong>Learner portal</strong></div>
        <button onClick={() => { logout(); navigate("/training-provider/portal/learner/login") }} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "7px 14px", cursor: "pointer" }}><LogOut size={14} /> Sign out</button>
      </header>
      <main style={{ maxWidth: 800, margin: "0 auto", padding: "28px 24px", display: "grid", gap: 16 }}>
        {isLoading && <p style={muted}>Loading…</p>}
        {isError && <p role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>Could not load your records. Try again later.</p>}
        {data && (
          <>
            <div><h1 style={{ fontSize: 20, margin: 0 }}>{data.fullName}</h1><p style={muted}>{data.learnerNo}</p><p style={muted}>{data.note}</p></div>
            <h2 style={{ fontSize: 14, margin: 0 }}>Your programmes</h2>
            {data.enrolments.length === 0 ? <p style={muted}>No enrolments recorded yet.</p> : data.enrolments.map(e => <PortalEnrolmentCard key={e.enrolmentNo} e={e} />)}
            <h2 style={{ fontSize: 14, margin: 0 }}>Upcoming sessions</h2>
            {data.upcoming.length === 0 ? <p style={muted}>No upcoming sessions are scheduled.</p> : (
              <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
                {data.upcoming.map((s, i) => (
                  <div key={`${s.date}-${s.startTime}-${i}`} style={{ padding: "10px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                    <strong>{s.date}</strong> {s.startTime.slice(0, 5)} to {s.endTime.slice(0, 5)}{s.title ? ` · ${s.title}` : ""}{s.cohortName ? ` · ${s.cohortName}` : ""}
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </main>
    </div>
  )
}
