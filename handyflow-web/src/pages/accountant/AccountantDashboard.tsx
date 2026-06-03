// src/pages/accountant/AccountantDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { AlertTriangle, Calendar, Clock, ArrowRight, Users, TrendingUp, FileText } from "lucide-react"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const DEADLINE_COLOR: Record<string, string> = {
  VAT201: "#0D9488", ITR14: "#1D4ED8", ITR12: "#7C3AED",
  EMP201: "#D97706", EMP501: "#EA580C", IRP6_P1: "#166534",
  IRP6_P2: "#166534", CIPC_RETURN: "#64748B", OTHER: "#94A3B8",
}

export default function AccountantDashboard({ onNavigate }: { onNavigate: (t: any) => void }) {
  const { data: dash } = useQuery<any>({
    queryKey: ["accountant-dashboard"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/accountant/dashboard"); return r.data?.data ?? r.data },
  })

  if (!dash) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>

  const urgent    = (dash.urgentDeadlines ?? []) as any[]
  const invoices  = (dash.outstandingInvoices ?? []) as any[]

  return (
    <div>
      {/* Alert banner */}
      {dash.overdueFilings > 0 && (
        <div style={{ marginBottom: 20, padding: "14px 18px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={16} color="#DC2626" />
          <span style={{ fontWeight: 700, fontSize: 14, color: "#DC2626" }}>{dash.overdueFilings} overdue SARS filing{dash.overdueFilings !== 1 ? "s" : ""} — immediate action required</span>
          <button onClick={() => onNavigate("deadlines")} style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", background: "none", border: "none", cursor: "pointer", fontWeight: 700 }}>
            View <ArrowRight size={13} />
          </button>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 20 }}>
        <div>
          {/* Urgent deadlines — next 7 days */}
          <div style={{ marginBottom: 24 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                <Calendar size={14} color="#DC2626" />
                <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>Due within 7 days</span>
              </div>
              <button onClick={() => onNavigate("deadlines")}
                style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                All deadlines <ArrowRight size={13} />
              </button>
            </div>
            {urgent.length === 0 ? (
              <div style={{ padding: "20px", border: "1px dashed #E2E8F0", borderRadius: 10, color: "#94A3B8", fontSize: 13, textAlign: "center" as const }}>
                No filings due in the next 7 days
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {urgent.map((d: any) => {
                  const color = DEADLINE_COLOR[d.deadlineType] ?? "#64748B"
                  const overdue = d.daysUntilDue < 0
                  return (
                    <div key={d.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "11px 16px", border: `1px solid ${overdue ? "#FECACA" : "#E2E8F0"}`, borderLeft: `3px solid ${overdue ? "#DC2626" : color}`, borderRadius: 8, background: overdue ? "#FFF5F5" : "#fff" }}>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 3 }}>
                          <span style={{ background: `${color}18`, color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{d.deadlineType}</span>
                          <span style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{d.clientName}</span>
                        </div>
                        <div style={{ fontSize: 11, color: "#64748B" }}>
                          {d.periodMonth ? `${d.periodMonth}/${d.periodYear}` : d.periodYear} · Due {fmtD(d.adjustedDueDate)}
                        </div>
                      </div>
                      <div style={{ textAlign: "right" as const, flexShrink: 0, marginLeft: 12 }}>
                        <div style={{ fontWeight: 700, fontSize: 14, color: overdue ? "#DC2626" : "#D97706" }}>
                          {overdue ? `${Math.abs(d.daysUntilDue)}d overdue` : `${d.daysUntilDue}d`}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Outstanding invoices */}
          {invoices.length > 0 && (
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                  <FileText size={14} color="#1D4ED8" />
                  <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>Outstanding invoices</span>
                </div>
                <button onClick={() => onNavigate("billing")}
                  style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                  View all <ArrowRight size={13} />
                </button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 7 }}>
                {invoices.slice(0, 5).map((f: any) => (
                  <div key={f.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", border: "1px solid #BFDBFE", borderLeft: "3px solid #1D4ED8", borderRadius: 8, background: "#F8FBFF" }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{f.clientName}</div>
                      <div style={{ fontSize: 11, color: "#64748B" }}>{f.invoiceNumber} · Due {fmtD(f.dueDate)}{f.daysOverdue > 0 && ` · ${f.daysOverdue}d overdue`}</div>
                    </div>
                    <div style={{ fontWeight: 700, fontSize: 13, color: f.daysOverdue > 0 ? "#DC2626" : "#1D4ED8" }}>{fmtR(f.total)}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 18 }}>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A", marginBottom: 14, display: "flex", alignItems: "center", gap: 7 }}>
              <TrendingUp size={14} color="#1B3A6B" /> Practice summary
            </div>
            {[
              { l: "Total clients",          v: dash.totalClients },
              { l: "High-risk clients",      v: dash.highRiskClients,     color: dash.highRiskClients > 0 ? "#DC2626" : "#166534" },
              { l: "FICA incomplete",        v: dash.ficaIncompleteClients, color: dash.ficaIncompleteClients > 0 ? "#D97706" : "#166534" },
              { l: "Filings this month",     v: dash.deadlinesThisMonth },
              { l: "Pending (30 days)",      v: dash.pendingFilingsNext30Days },
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
              { label: "Add client",       tab: "clients"   },
              { label: "Log time",         tab: "time"      },
              { label: "View deadlines",   tab: "deadlines" },
              { label: "Create fee note",  tab: "billing"   },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "10px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 9, fontSize: 13, fontWeight: 600, color: "#1B3A6B", cursor: "pointer", textAlign: "left" as const, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>

          {/* SARS compliance note */}
          <div style={{ padding: "14px 16px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 10 }}>
            <div style={{ fontWeight: 700, fontSize: 12, color: "#1D4ED8", marginBottom: 6 }}>SARS penalty rates 2026</div>
            <div style={{ fontSize: 12, color: "#1E40AF", lineHeight: 1.7 }}>
              VAT201 late: 10% + interest at repo + 6.5%<br/>
              EMP201 late: 10% + up to 200% on PAYE<br/>
              ITR14 late: R250/month + 20% on tax due
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
