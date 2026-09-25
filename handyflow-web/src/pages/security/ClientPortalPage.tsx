// src/pages/security/ClientPortalPage.tsx
// Route: /portal/:token  (public — no auth required)
// Add to your router: <Route path="/portal/:token" element={<ClientPortalPage />} />

import { useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import axios from "axios"
import { Shield, AlertTriangle, CheckCircle, Clock, Calendar, MapPin, Activity } from "lucide-react"

const SEV_CONFIG: Record<string, { color: string; bg: string }> = {
  CRITICAL: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  HIGH:     { color: "var(--hf-orange-text)", bg: "var(--hf-orange-soft)" },
  MEDIUM:   { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
  LOW:      { color: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)" },
}

const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  SCHEDULED:  { color: "var(--hf-info-text)", label: "Scheduled" },
  ACTIVE:     { color: "var(--hf-success-text-strong)", label: "On duty" },
  COMPLETED:  { color: "var(--hf-accent-text)", label: "Completed" },
  MISSED:     { color: "var(--hf-danger-text)", label: "Missed" },
  CANCELLED:  { color: "var(--hf-text-faint)", label: "Cancelled" },
}

const CONTRACT_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:        { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", label: "Active contract" },
  EXPIRING_SOON: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Contract expiring soon" },
  EXPIRED:       { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Contract expired" },
  TERMINATED:    { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", label: "Contract terminated" },
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
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "var(--hf-surface-muted)" }}>
      <div style={{ textAlign: "center", color: "var(--hf-text-faint)" }}>
        <Shield size={40} style={{ marginBottom: 16, opacity: 0.4 }} />
        <div style={{ fontWeight: 600, fontSize: 15 }}>Loading portal...</div>
      </div>
    </div>
  )

  if (isError || !data) return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "var(--hf-surface-muted)" }}>
      <div style={{ textAlign: "center", maxWidth: 400, padding: 32 }}>
        <div style={{ width: 64, height: 64, borderRadius: "50%", background: "var(--hf-danger-soft)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 20px" }}>
          <Shield size={28} style={{ color: 'var(--hf-danger-text)' }} />
        </div>
        <h2 style={{ fontSize: 20, fontWeight: 700, color: "var(--hf-text)", marginBottom: 8 }}>Portal not found</h2>
        <p style={{ color: "var(--hf-text-muted)", fontSize: 14, lineHeight: 1.6 }}>
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
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-muted)", fontFamily: "'Inter', 'Arial', sans-serif" }}>

      {/* Header */}
      <div style={{ background: "var(--hf-primary)", padding: "20px 32px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: "rgba(255,255,255,0.15)", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Shield size={20} style={{ color: 'var(--hf-text-on-solid)' }} />
          </div>
          <div>
            <div style={{ color: "var(--hf-text-on-solid)", fontSize: 17, fontWeight: 700 }}>{data.siteName}</div>
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
          <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "18px 20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase" as const, letterSpacing: "0.05em" }}>Guards on duty</div>
              <Activity size={14} style={{ color: data.activeGuardsNow > 0 ? "var(--hf-success-text-strong)" : "var(--hf-text-faint)" }} />
            </div>
            <div style={{ fontSize: 30, fontWeight: 800, color: data.activeGuardsNow > 0 ? "var(--hf-success-text-strong)" : "var(--hf-text-faint)" }}>{data.activeGuardsNow}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>right now</div>
          </div>

          <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "18px 20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase" as const, letterSpacing: "0.05em" }}>Open incidents</div>
              <AlertTriangle size={14} style={{ color: openCount > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }} />
            </div>
            <div style={{ fontSize: 30, fontWeight: 800, color: openCount > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>{openCount}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>requiring attention</div>
          </div>

          <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "18px 20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase" as const, letterSpacing: "0.05em" }}>Checkpoint scans</div>
              <MapPin size={14} style={{ color: 'var(--hf-accent-text)' }} />
            </div>
            <div style={{ fontSize: 30, fontWeight: 800, color: "var(--hf-accent-text)" }}>{data.weeklyCheckpointScans}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>this week</div>
          </div>
        </div>

        {/* Open incidents */}
        {openCount > 0 && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
              <AlertTriangle size={14} style={{ color: 'var(--hf-danger-text)' }} />
              Open incidents
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {data.openIncidents.map((inc: any) => {
                const sev = SEV_CONFIG[inc.severity] ?? SEV_CONFIG.LOW
                return (
                  <div key={inc.id} style={{ background: "var(--hf-surface)", border: `1px solid ${sev.bg}`, borderLeft: `4px solid ${sev.color}`, borderRadius: 10, padding: "14px 18px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{inc.title}</span>
                      <span style={{ fontSize: 10, fontWeight: 700, background: sev.bg, color: sev.color, padding: "2px 8px", borderRadius: 20 }}>{inc.severity}</span>
                      <span style={{ fontSize: 11, fontWeight: 600, background: inc.status === "ACKNOWLEDGED" ? "var(--hf-warning-soft)" : "var(--hf-danger-soft)", color: inc.status === "ACKNOWLEDGED" ? "var(--hf-warning-text)" : "var(--hf-danger-text)", padding: "2px 8px", borderRadius: 20 }}>
                        {inc.status === "ACKNOWLEDGED" ? "Being handled" : "Awaiting response"}
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
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
          <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
            <Calendar size={14} style={{ color: 'var(--hf-primary-text)' }} />
            Today's shifts
          </div>
          {todayShifts.length === 0 ? (
            <div style={{ padding: "20px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, color: "var(--hf-text-faint)", fontSize: 13, textAlign: "center" }}>
              No shifts scheduled for today
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {todayShifts.map((shift: any) => {
                const sts = STATUS_CONFIG[shift.status] ?? STATUS_CONFIG.SCHEDULED
                const isActive = shift.status === "ACTIVE"
                return (
                  <div key={shift.id} style={{ background: "var(--hf-surface)", border: `1px solid ${isActive ? "var(--hf-success-border)" : "var(--hf-border)"}`, borderRadius: 10, padding: "14px 18px", display: "flex", alignItems: "center", gap: 14 }}>
                    <div style={{ position: "relative", flexShrink: 0 }}>
                      <div style={{ width: 36, height: 36, borderRadius: "50%", background: isActive ? "var(--hf-success-soft-strong)" : "var(--hf-surface-muted)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                        <Shield size={16} style={{ color: isActive ? "var(--hf-success-text-strong)" : "var(--hf-text-faint)" }} />
                      </div>
                      {isActive && <div style={{ position: "absolute", bottom: 0, right: 0, width: 10, height: 10, borderRadius: "50%", background: "var(--hf-success)", border: "2px solid var(--hf-surface)" }} />}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: 14, color: "var(--hf-text)", marginBottom: 2 }}>
                        {shift.guard_name ?? "Guard"}
                        {shift.grade && <span style={{ marginLeft: 6, fontSize: 11, color: "var(--hf-text-faint)" }}>Grade {shift.grade}</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{fmtTime(shift.start_at)} – {fmtTime(shift.end_at)}</div>
                    </div>
                    <span style={{ fontSize: 12, fontWeight: 600, color: sts.color, background: `color-mix(in srgb, ${sts.color} 9%, transparent)`, padding: "4px 12px", borderRadius: 20 }}>{sts.label}</span>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Upcoming shifts */}
        {upcomingShifts.length > 0 && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
              <Clock size={14} style={{ color: 'var(--hf-info-text)' }} />
              Upcoming (next 7 days)
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {upcomingShifts.slice(0, 8).map((shift: any) => (
                <div key={shift.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 18px", display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-info-text)", minWidth: 80 }}>{fmtDate(shift.start_at)}</div>
                  <div style={{ flex: 1, fontSize: 13, color: "var(--hf-text)" }}>
                    {shift.guard_name ?? "Guard"}
                    <span style={{ color: "var(--hf-text-faint)", marginLeft: 6 }}>{fmtTime(shift.start_at)} – {fmtTime(shift.end_at)}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Completed shifts summary */}
        {pastShifts.length > 0 && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
              <CheckCircle size={14} style={{ color: 'var(--hf-accent-text)' }} />
              Recent completed shifts (past 7 days)
            </div>
            <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
              {pastShifts.slice(0, 10).map((shift: any, i: number) => {
                const sts = STATUS_CONFIG[shift.status] ?? STATUS_CONFIG.COMPLETED
                return (
                  <div key={shift.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 18px", borderBottom: i < pastShifts.slice(0, 10).length - 1 ? "1px solid var(--hf-border-subtle)" : "none" }}>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)", minWidth: 80 }}>{fmtDate(shift.start_at)}</div>
                    <div style={{ flex: 1, fontSize: 13, color: "var(--hf-text-tertiary)" }}>
                      {shift.guard_name ?? "Guard"}
                      <span style={{ color: "var(--hf-text-disabled)", marginLeft: 6 }}>{fmtTime(shift.start_at)} – {fmtTime(shift.end_at)}</span>
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
          <div style={{ padding: "14px 18px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, fontSize: 13, color: "var(--hf-text-muted)", display: "flex", gap: 20 }}>
            <div><span style={{ fontWeight: 600, color: "var(--hf-text)" }}>Contract: </span>{contract.label}</div>
            {data.contractStart && <div><span style={{ fontWeight: 600, color: "var(--hf-text)" }}>Start: </span>{data.contractStart}</div>}
            {data.contractEnd   && <div><span style={{ fontWeight: 600, color: "var(--hf-text)" }}>End: </span>{data.contractEnd}</div>}
          </div>
        )}

        {/* Footer */}
        <div style={{ marginTop: 32, textAlign: "center", color: "var(--hf-text-disabled)", fontSize: 11 }}>
          Powered by HandyFlow · Security Management Platform · Data refreshes every 60 seconds
        </div>
      </div>
    </div>
  )
}
