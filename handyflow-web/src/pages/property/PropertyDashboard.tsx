// src/pages/property/PropertyDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Building2, TrendingUp, AlertTriangle, Calendar, ArrowRight, Users } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtR   = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

export default function PropertyDashboard({ onNavigate }: {
  onNavigate: (t: any, payload?: { leasesFilter?: string; paymentsLeaseId?: string }) => void
}) {
  const { data: properties = [] } = useQuery<any[]>({ queryKey: ["properties"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/properties?size=200")) })
  const { data: leases = [] } = useQuery<any[]>({ queryKey: ["leases"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/leases?size=200")) })
  const { data: outstanding = [] } = useQuery<any[]>({ queryKey: ["outstanding"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/payments/outstanding")) })

  const ps = properties as any[]
  const ls = leases as any[]
  const os = outstanding as any[]

  const totalUnits    = ps.reduce((s, p) => s + (p.totalUnits || 0), 0)
  const occupiedUnits = ps.reduce((s, p) => s + (p.occupiedUnits || 0), 0)
  const vacantUnits   = ps.reduce((s, p) => s + (p.vacantUnits || 0), 0)
  const occupancyPct  = totalUnits > 0 ? Math.round((occupiedUnits / totalUnits) * 100) : 0
  const monthlyIncome = ls.filter(l => l.status === "ACTIVE").reduce((s, l) => s + Number(l.monthlyRent || 0), 0)
  const arrears       = os.reduce((s, p) => s + (Number(p.amountDue) - Number(p.amountPaid)), 0)
  const expiringSoon  = ls.filter(l => l.expiringSoon && l.status === "ACTIVE")

  return (
    <div>
      {/* KPIs */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 28 }}>
        {[
          { label: "Occupancy rate",     value: `${occupancyPct}%`,    color: "#166534", bg: "#DCFCE7" },
          { label: "Occupied / total",   value: `${occupiedUnits} / ${totalUnits}`, color: "#1D4ED8", bg: "#EFF6FF" },
          { label: "Monthly rent roll",  value: fmtR(monthlyIncome),   color: "#1B3A6B", bg: "#EEF2FF" },
          { label: "Outstanding arrears",value: fmtR(arrears),         color: arrears > 0 ? "#DC2626" : "#166534", bg: arrears > 0 ? "#FEF2F2" : "#F0FDF4" },
        ].map(k => (
          <div key={k.label} style={{ background: k.bg, borderRadius: 12, padding: "16px 20px" }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
            <div style={{ fontSize: 11, color: k.color, marginTop: 4, opacity: 0.8 }}>{k.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 20 }}>
        <div>
          {/* Occupancy bar per property */}
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 14, display: "flex", alignItems: "center", gap: 7 }}>
              <Building2 size={14} color="#1B3A6B" /> Portfolio occupancy
            </div>
            {ps.length === 0 ? (
              <div style={{ padding: "24px", border: "1px dashed #E2E8F0", borderRadius: 10, color: "#94A3B8", fontSize: 13, textAlign: "center" as const }}>
                No properties yet. Add your first property to get started.
              </div>
            ) : ps.map(p => {
              const pct = p.totalUnits > 0 ? Math.round((p.occupiedUnits / p.totalUnits) * 100) : 0
              return (
                <div key={p.id} style={{ marginBottom: 12 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 5, fontSize: 13 }}>
                    <span style={{ fontWeight: 600, color: "#0F172A" }}>{p.name}</span>
                    <span style={{ color: "#64748B" }}>{p.occupiedUnits}/{p.totalUnits} units · {pct}%</span>
                  </div>
                  <div style={{ height: 7, background: "#E2E8F0", borderRadius: 99, overflow: "hidden" }}>
                    <div style={{ height: "100%", width: `${pct}%`, background: pct >= 80 ? "#16A34A" : pct >= 50 ? "#D97706" : "#DC2626", borderRadius: 99, transition: "width 0.4s" }} />
                  </div>
                </div>
              )
            })}
          </div>

          {/* Arrears list */}
          {os.length > 0 && (
            <div style={{ marginBottom: 24 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                  <AlertTriangle size={14} color="#DC2626" />
                  <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>Outstanding payments</span>
                </div>
                <button onClick={() => onNavigate("payments")}
                  style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                  View all <ArrowRight size={13} />
                </button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 7 }}>
                {os.slice(0, 5).map((p: any) => {
                  const balance = Number(p.amountDue) - Number(p.amountPaid)
                  const overdue = p.status === "OVERDUE"
                  return (
                    <div key={p.id} onClick={() => onNavigate("payments", { paymentsLeaseId: p.leaseId })}
                      style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", border: `1px solid ${overdue ? "#FECACA" : "#FDE68A"}`, borderLeft: `3px solid ${overdue ? "#DC2626" : "#D97706"}`, borderRadius: 8, background: overdue ? "#FFF5F5" : "#FFFBEB", cursor: "pointer" }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>
                          {p.periodMonth}/{p.periodYear}
                        </div>
                        <div style={{ fontSize: 11, color: "#64748B" }}>{p.status}</div>
                      </div>
                      <div style={{ fontWeight: 700, fontSize: 14, color: overdue ? "#DC2626" : "#D97706" }}>
                        {fmtR(balance)}
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          {/* Expiring leases */}
          {expiringSoon.length > 0 && (
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                  <Calendar size={14} color="#D97706" />
                  <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>Leases expiring soon</span>
                </div>
                {/* NEW: this is the exact case the original module review
                    flagged as unconfirmed — "we can't confirm whether View
                    expiring leases navigates correctly to LeasesTab
                    filtered to expiring, or just to the unfiltered tab."
                    Confirmed it was the latter; this closes it. */}
                <button onClick={() => onNavigate("leases", { leasesFilter: "EXPIRING_SOON" })}
                  style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                  View all <ArrowRight size={13} />
                </button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 7 }}>
                {expiringSoon.map((l: any) => (
                  <div key={l.id} onClick={() => onNavigate("leases", { leasesFilter: "EXPIRING_SOON" })}
                    style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", border: "1px solid #FDE68A", borderLeft: "3px solid #D97706", borderRadius: 8, background: "#FFFBEB", cursor: "pointer" }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{l.lesseeName}</div>
                      <div style={{ fontSize: 11, color: "#64748B" }}>Expires {l.endDate}</div>
                    </div>
                    <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706" }}>{fmtR(l.monthlyRent)}/mo</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 18 }}>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A", marginBottom: 14 }}>Portfolio summary</div>
            {[
              { l: "Properties",    v: ps.length                               },
              { l: "Total units",   v: totalUnits                              },
              { l: "Vacant",        v: vacantUnits, color: vacantUnits > 0 ? "#D97706" : "#166534" },
              { l: "Active leases", v: ls.filter(l => l.status === "ACTIVE").length },
            ].map(r => (
              <div key={r.l} style={{ display: "flex", justifyContent: "space-between", marginBottom: 10, fontSize: 13 }}>
                <span style={{ color: "#64748B" }}>{r.l}</span>
                <span style={{ fontWeight: 700, color: (r as any).color ?? "#0F172A" }}>{r.v}</span>
              </div>
            ))}
          </div>

          <div>
            <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Add property",    tab: "properties"  },
              { label: "Create lease",    tab: "leases"      },
              { label: "Record payment",  tab: "payments"    },
              { label: "Log inspection",  tab: "inspections" },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "10px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 9, fontSize: 13, fontWeight: 600, color: "#1B3A6B", cursor: "pointer", textAlign: "left" as const, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>

          {/* Rental health tip */}
          <div style={{ padding: "14px 16px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 10 }}>
            <div style={{ fontWeight: 700, fontSize: 12, color: "#1D4ED8", marginBottom: 6 }}>Rental Income Act</div>
            <div style={{ fontSize: 12, color: "#1E40AF", lineHeight: 1.6 }}>
              Under the Rental Housing Act 50 of 1999, rental deposits must be held in an interest-bearing account. Deposits must be refunded within 14 days of lease termination if no deductions apply.
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
