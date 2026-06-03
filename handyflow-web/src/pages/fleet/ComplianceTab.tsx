// src/pages/fleet/ComplianceTab.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Shield, CheckCircle, AlertTriangle, Clock, AlertCircle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const daysUntil = (d: string | null) => d ? Math.ceil((new Date(d).getTime() - Date.now()) / 86400000) : 999

const ICONS: Record<string, string> = { SEDAN:"🚗", SUV:"🚙", BAKKIE:"🛻", TRUCK:"🚛", MINIBUS:"🚐", VAN:"🚌", MOTORCYCLE:"🏍️", OTHER:"🚘" }

function ExpiryStatus({ days, date }: { days: number; date: string | null }) {
  if (!date) return <span style={{ color: "#94A3B8", fontSize: 13 }}>Not set</span>
  if (days < 0) return <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, fontWeight: 700, color: "#DC2626" }}><AlertCircle size={12} />Expired {Math.abs(days)}d ago</span>
  if (days <= 7)  return <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, fontWeight: 700, color: "#DC2626" }}><AlertTriangle size={12} />{days} days — Critical</span>
  if (days <= 30) return <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, fontWeight: 700, color: "#D97706" }}><AlertTriangle size={12} />{days} days</span>
  if (days <= 60) return <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, fontWeight: 600, color: "#64748B" }}><Clock size={12} />{days} days</span>
  return <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#166534" }}><CheckCircle size={12} />Valid — {days}d</span>
}

function DocRow({ label, date }: { label: string; date: string | null }) {
  const days = daysUntil(date)
  const urgent = date && days <= 30
  return (
    <div style={{ display: "grid", gridTemplateColumns: "140px 1fr 1fr", gap: 12, padding: "10px 0", borderBottom: "1px solid #F1F5F9", alignItems: "center" }}>
      <div style={{ fontSize: 12, fontWeight: 600, color: "#64748B" }}>{label}</div>
      <div style={{ fontSize: 13, color: urgent ? "#D97706" : "#0F172A", fontWeight: urgent ? 600 : 400 }}>{fmtDate(date)}</div>
      <ExpiryStatus days={days} date={date} />
    </div>
  )
}

export default function ComplianceTab() {
  const { data: vehicles = [], isLoading } = useQuery<any[]>({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/vehicles?size=200")),
  })

  const vs = vehicles as any[]

  const expired = vs.filter(v => [v.licenceDiscExpiry, v.roadworthyExpiry, v.insuranceExpiry].some(d => d && daysUntil(d) < 0))
  const critical = vs.filter(v => !expired.includes(v) && [v.licenceDiscExpiry, v.roadworthyExpiry, v.insuranceExpiry].some(d => d && daysUntil(d) <= 7))
  const warning  = vs.filter(v => !expired.includes(v) && !critical.includes(v) && [v.licenceDiscExpiry, v.roadworthyExpiry, v.insuranceExpiry].some(d => d && daysUntil(d) <= 30))
  const upcoming = vs.filter(v => !expired.includes(v) && !critical.includes(v) && !warning.includes(v) && [v.licenceDiscExpiry, v.roadworthyExpiry, v.insuranceExpiry].some(d => d && daysUntil(d) <= 60))
  const current  = vs.filter(v => !expired.includes(v) && !critical.includes(v) && !warning.includes(v) && !upcoming.includes(v))

  const summaryStats = [
    { label: "Expired",      value: expired.length,  color: "#DC2626", bg: "#FEF2F2" },
    { label: "Critical (7d)",value: critical.length,  color: "#DC2626", bg: "#FEF2F2" },
    { label: "Expiring 30d", value: warning.length,   color: "#D97706", bg: "#FFFBEB" },
    { label: "All current",  value: current.length + upcoming.length, color: "#166534", bg: "#DCFCE7" },
  ]

  const AlertGroup = ({ title, vList, color }: { title: string; vList: any[]; color: string }) => {
    if (vList.length === 0) return null
    return (
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>{title} ({vList.length})</div>
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {vList.map(v => (
            <div key={v.id} style={{ border: `1px solid ${color === "#DC2626" ? "#FECACA" : color === "#D97706" ? "#FDE68A" : "#E2E8F0"}`, borderRadius: 12, padding: "16px 20px", background: "#fff" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
                <div style={{ fontSize: 24 }}>{ICONS[v.vehicleType] ?? "🚘"}</div>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{v.registration}</div>
                  <div style={{ fontSize: 12, color: "#64748B" }}>{v.make} {v.model}{v.year ? ` (${v.year})` : ""}</div>
                </div>
              </div>
              <DocRow label="Licence Disc"   date={v.licenceDiscExpiry} />
              <DocRow label="Roadworthy"     date={v.roadworthyExpiry} />
              <DocRow label="Insurance"      date={v.insuranceExpiry} />
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div>
      {/* Summary */}
      <div style={{ display: "flex", gap: 12, marginBottom: 24 }}>
        {summaryStats.map(s => (
          <div key={s.label} style={{ flex: 1, background: s.bg, borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: s.color, fontWeight: 600, marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : vs.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Shield size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No vehicles registered</div>
        </div>
      ) : (
        <>
          {(expired.length === 0 && critical.length === 0 && warning.length === 0) && (
            <div style={{ marginBottom: 20, padding: "16px 20px", background: "#DCFCE7", border: "1px solid #86EFAC", borderRadius: 12, display: "flex", alignItems: "center", gap: 12 }}>
              <CheckCircle size={22} color="#166534" />
              <div>
                <div style={{ fontWeight: 700, color: "#166534" }}>All documents current</div>
                <div style={{ fontSize: 13, color: "#166534" }}>No licences, roadworthy certificates, or insurance policies are expiring within 30 days.</div>
              </div>
            </div>
          )}

          <AlertGroup title="Expired"          vList={expired}  color="#DC2626" />
          <AlertGroup title="Expiring this week" vList={critical} color="#DC2626" />
          <AlertGroup title="Expiring this month" vList={warning} color="#D97706" />
          <AlertGroup title="Expiring within 60 days" vList={upcoming} color="#64748B" />

          {current.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "#166534", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Fully current ({current.length})</div>
              <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                {current.map(v => (
                  <div key={v.id} style={{ display: "flex", alignItems: "center", gap: 8, padding: "8px 12px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8 }}>
                    <span style={{ fontSize: 16 }}>{ICONS[v.vehicleType] ?? "🚘"}</span>
                    <span style={{ fontSize: 13, fontWeight: 600, color: "#166534" }}>{v.registration}</span>
                    <CheckCircle size={13} color="#166534" />
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
