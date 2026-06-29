// src/pages/security/ClientPortalPage.tsx
// Route: /portal/:token  (public — no auth required)
// Add to your router: <Route path="/portal/:token" element={<ClientPortalPage />} />

import { useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import axios from "axios"
import { Shield, AlertTriangle, CheckCircle, Clock, Calendar, MapPin, Activity } from "lucide-react"

const SEV_CONFIG: Record<string, { color: string; bg: string }> = {
  CRITICAL: { color: "#DC2626", bg: "#FEF2F2" },
  HIGH:     { color: "#EA580C", bg: "#FFF7ED" },
  MEDIUM:   { color: "#D97706", bg: "#FFFBEB" },
  LOW:      { color: "#64748B", bg: "#F8FAFC" },
}

const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  SCHEDULED:  { color: "#1D4ED8", label: "Scheduled" },
  ACTIVE:     { color: "#166534", label: "On duty" },
  COMPLETED:  { color: "#0D9488", label: "Completed" },
  MISSED:     { color: "#DC2626", label: "Missed" },
  CANCELLED:  { color: "#94A3B8", label: "Cancelled" },
}

const CONTRACT_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:        { color: "#166534", bg: "#DCFCE7", label: "Active contract" },
  EXPIRING_SOON: { color: "#D97706", bg: "#FFFBEB", label: "Contract expiring soon" },
  EXPIRED:       { color: "#DC2626", bg: "#FEF2F2", label: "Contract expired" },
  TERMINATED:    { color: "#64748B", bg: "#F1F5F9", label: "Contract terminated" },
}

function fmtDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
}
function fmtTime(iso: string) {
  return new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
}
function isToday(iso: string) {
  return new Date(iso).toDateString() === new Date().toDateString()
}
function isFuture(iso: string) {
  return new Date(iso) > new Date()
}

export default function ClientPortalPage() {
  const { token } = useParams<{ token: string }>()

  const { data, isLoading, isError } = useQuery({
    queryKey: ["portal", token],
    queryFn: async () => {
      const r = await axios.get(`/api/v1/portal/${token}`)
      return r.data?.data ?? r.data
    },
    refetchInterval: 60_000, // refresh every minute
    retry: false,
  })

  if (isLoading) return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "#F8FAFC" }}>
      <div style={{ textAlign: "center", color: "#94A3B8" }}>
        <Shield size={40} style={{ marginBottom: 16, opacity: 0.4 }} />
        <div style={{ fontWeight: 600, fontSize: 15 }}>Loading portal...</div>
      </div>
    </div>
  )

  if (isError || !data) return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "#F8FAFC" }}>
      <div style={{ textAlign: "center", maxWidth: 400, padding: 32 }}>
        <div style={{ width: 64, height: 64, borderRadius: "50%", background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 20px" }}>
          <Shield size={28} color="#DC2626" />
        </div>
        <h2 style={{ fontSize: 20, fontWeight: 700, color: "#0F172A", marginBottom: 8 }}>Portal not found</h2>
        <p style={{ color: "#64748B", fontSize: 14, lineHeight: 1.6 }}>
          This security portal link is invalid or has been disabled. Please contact your security provider for a new link.
        </p>
      </div>
    </div>
  )

  const contract = CONTRACT_CONFIG[data.contractStatus] ?? CONTRACT_CONFIG.ACTIVE
  const todayShifts   = (data.shifts ?? []).filter((s: any) => isToday(s.start_at))
  const upcomingShifts = (data.shifts ?? []).filter((s: any) => isFuture(s.start_at) && !isToday(s.start_at))
  const pastShifts    = (data.shifts ?? []).filter((s: any) => !isFuture(s.start_at) && !isToday(s.start_at))
  const openCount     = (data.openIncidents ?? []).length

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', 'Arial', sans-serif" }}>

      {/* Header */}
      <div style={{ background: "#1B3A6B", padding: "20px 32px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: "rgba(255,255,255,0.15)", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Shield size={20} color="#fff" />
          </div>
          <div>
            <div style={{ color: "#fff", fontSize: 17, fontWeight: 700 }}>{data.siteName}</div>
            <div style={{ color: "rgba(255,255,255,0.6)", fontSize: 12 }}>Security Dashboard</div>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontSize: 11, fontWeight: 600, background: contract.bg, color: contract.color, padding: "4px 12px", borderRadius: 20 }}>
            {contract.label}
          </span>
          <span style={{ fontSize: 11, color: "rgba(255,255,255,0.5)" }}>
            Updated {new Date().toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })}
          </span>
        </div>
      </div>

      <div style={{ maxWidth: 900, margin: "0 auto", padding: "28px 20px" }}>

        {/* KPI cards */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14, marginBottom: 28 }}>
          <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.05em" }}>Guards on duty</div>
              <Activity size={14} color={data.activeGuardsNow > 0 ? "#166534" : "#94A3B8"} />
            </div>
            <div style={{ fontSize: 30, fontWeight: 800, color: data.activeGuardsNow > 0 ? "#166534" : "#94A3B8" }}>{data.activeGuardsNow}</div>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>right now</div>
          </div>

          <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.05em" }}>Open incidents</div>
              <AlertTriangle size={14} color={openCount > 0 ? "#DC2626" : "#166534"} />
            </div>
            <div style={{ fontSize: 30, fontWeight: 800, color: openCount > 0 ? "#DC2626" : "#166534" }}>{openCount}</div>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>requiring attention</div>
          </div>

          <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.05em" }}>Checkpoint scans</div>
              <MapPin size={14} color="#0D9488" />
            </div>
            <div style={{ fontSize: 30, fontWeight: 800, color: "#0D9488" }}>{data.weeklyCheckpointScans}</div>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>this week</div>
          </div>
        </div>

        {/* Open incidents */}
        {openCount > 0 && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
              <AlertTriangle size={14} color="#DC2626" />
              Open incidents
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {data.openIncidents.map((inc: any) => {
                const sev = SEV_CONFIG[inc.severity] ?? SEV_CONFIG.LOW
                return (
                  <div key={inc.id} style={{ background: "#fff", border: `1px solid ${sev.bg}`, borderLeft: `4px solid ${sev.color}`, borderRadius: 10, padding: "14px 18px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{inc.title}</span>
                      <span style={{ fontSize: 10, fontWeight: 700, background: sev.bg, color: sev.color, padding: "2px 8px", borderRadius: 20 }}>{inc.severity}</span>
                      <span style={{ fontSize: 11, fontWeight: 600, background: inc.status === "ACKNOWLEDGED" ? "#FFFBEB" : "#FEF2F2", color: inc.status === "ACKNOWLEDGED" ? "#D97706" : "#DC2626", padding: "2px 8px", borderRadius: 20 }}>
                        {inc.status === "ACKNOWLEDGED" ? "Being handled" : "Awaiting response"}
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>
                      Reported {fmtDate(inc.created_at)}
                      {inc.acknowledged_at && ` · Acknowledged ${fmtTime(inc.acknowledged_at)}`}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* Today's shifts */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
            <Calendar size={14} color="#1B3A6B" />
            Today's shifts
          </div>
          {todayShifts.length === 0 ? (
            <div style={{ padding: "20px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, color: "#94A3B8", fontSize: 13, textAlign: "center" }}>
              No shifts scheduled for today
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {todayShifts.map((shift: any) => {
                const sts = STATUS_CONFIG[shift.status] ?? STATUS_CONFIG.SCHEDULED
                const isActive = shift.status === "ACTIVE"
                return (
                  <div key={shift.id} style={{ background: "#fff", border: `1px solid ${isActive ? "#86EFAC" : "#E2E8F0"}`, borderRadius: 10, padding: "14px 18px", display: "flex", alignItems: "center", gap: 14 }}>
                    <div style={{ position: "relative", flexShrink: 0 }}>
                      <div style={{ width: 36, height: 36, borderRadius: "50%", background: isActive ? "#DCFCE7" : "#F8FAFC", display: "flex", alignItems: "center", justifyContent: "center" }}>
                        <Shield size={16} color={isActive ? "#166534" : "#94A3B8"} />
                      </div>
                      {isActive && <div style={{ position: "absolute", bottom: 0, right: 0, width: 10, height: 10, borderRadius: "50%", background: "#22C55E", border: "2px solid #fff" }} />}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: 14, color: "#0F172A", marginBottom: 2 }}>
                        {shift.guard_name ?? "Guard"}
                        {shift.grade && <span style={{ marginLeft: 6, fontSize: 11, color: "#94A3B8" }}>Grade {shift.grade}</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>{fmtTime(shift.start_at)} – {fmtTime(shift.end_at)}</div>
                    </div>
                    <span style={{ fontSize: 12, fontWeight: 600, color: sts.color, background: `${sts.color}18`, padding: "4px 12px", borderRadius: 20 }}>{sts.label}</span>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Upcoming shifts */}
        {upcomingShifts.length > 0 && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
              <Clock size={14} color="#1D4ED8" />
              Upcoming (next 7 days)
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {upcomingShifts.slice(0, 8).map((shift: any) => (
                <div key={shift.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 18px", display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: "#1D4ED8", minWidth: 80 }}>{fmtDate(shift.start_at)}</div>
                  <div style={{ flex: 1, fontSize: 13, color: "#0F172A" }}>
                    {shift.guard_name ?? "Guard"}
                    <span style={{ color: "#94A3B8", marginLeft: 6 }}>{fmtTime(shift.start_at)} – {fmtTime(shift.end_at)}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Completed shifts summary */}
        {pastShifts.length > 0 && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
              <CheckCircle size={14} color="#0D9488" />
              Recent completed shifts (past 7 days)
            </div>
            <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
              {pastShifts.slice(0, 10).map((shift: any, i: number) => {
                const sts = STATUS_CONFIG[shift.status] ?? STATUS_CONFIG.COMPLETED
                return (
                  <div key={shift.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 18px", borderBottom: i < pastShifts.slice(0, 10).length - 1 ? "1px solid #F1F5F9" : "none" }}>
                    <div style={{ fontSize: 12, color: "#94A3B8", minWidth: 80 }}>{fmtDate(shift.start_at)}</div>
                    <div style={{ flex: 1, fontSize: 13, color: "#475569" }}>
                      {shift.guard_name ?? "Guard"}
                      <span style={{ color: "#CBD5E1", marginLeft: 6 }}>{fmtTime(shift.start_at)} – {fmtTime(shift.end_at)}</span>
                    </div>
                    <span style={{ fontSize: 11, color: sts.color }}>{sts.label}</span>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* Contract info */}
        {(data.contractStart || data.contractEnd) && (
          <div style={{ padding: "14px 18px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, fontSize: 13, color: "#64748B", display: "flex", gap: 20 }}>
            <div><span style={{ fontWeight: 600, color: "#0F172A" }}>Contract: </span>{contract.label}</div>
            {data.contractStart && <div><span style={{ fontWeight: 600, color: "#0F172A" }}>Start: </span>{data.contractStart}</div>}
            {data.contractEnd   && <div><span style={{ fontWeight: 600, color: "#0F172A" }}>End: </span>{data.contractEnd}</div>}
          </div>
        )}

        {/* Footer */}
        <div style={{ marginTop: 32, textAlign: "center", color: "#CBD5E1", fontSize: 11 }}>
          Powered by HandyFlow · Security Management Platform · Data refreshes every 60 seconds
        </div>
      </div>
    </div>
  )
}
