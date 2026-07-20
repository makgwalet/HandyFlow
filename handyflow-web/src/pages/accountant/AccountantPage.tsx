// src/pages/accountant/AccountantPage.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Briefcase, BarChart2, Users, Calendar, Clock, FileText, X, Settings, BookOpen } from "lucide-react"
import AccountantDashboard from "./AccountantDashboard"
import ClientsTab          from "./ClientsTab"
import DeadlinesTab        from "./DeadlinesTab"
import TimeTab             from "./TimeTab"
import BillingTab          from "./BillingTab"
// NEW: closes the #2 must-fix gap from the accountant module audit —
// journals were fully modeled and postable but had no tab at all.
import JournalsTab         from "./JournalsTab"

type Tab = "dashboard" | "clients" | "deadlines" | "time" | "billing" | "journals"

const TABS = [
  { id: "dashboard" as Tab, label: "Dashboard",  icon: BarChart2 },
  { id: "clients"   as Tab, label: "Clients",    icon: Users     },
  { id: "deadlines" as Tab, label: "Compliance", icon: Calendar  },
  { id: "time"      as Tab, label: "Time",       icon: Clock     },
  { id: "billing"   as Tab, label: "Billing",    icon: FileText  },
  { id: "journals"  as Tab, label: "Journals",   icon: BookOpen  },
]

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

export function AccountantPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<Tab>("dashboard")
  const [showSetup, setShowSetup] = useState(false)
  const [profileForm, setProfileForm] = useState({
    firmName: "", practiceNumber: "", vatNumber: "",
    contactEmail: "", contactPhone: "", defaultHourlyRate: "850", yearEndMonth: "2",
  })
  const pf = (k: string, v: string) => setProfileForm(p => ({ ...p, [k]: v }))
  const [profileError, setProfileError] = useState("")

  const { data: dashboard } = useQuery<any>({
    queryKey: ["accountant-dashboard"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/accountant/dashboard")
      return r.data?.data ?? r.data
    },
  })

  const { data: profile } = useQuery<any>({
    queryKey: ["accountant-profile"],
    queryFn: async () => {
      try {
        const r = await apiClient.get("/api/v1/accountant/profile")
        return r.data?.data ?? r.data
      } catch { return null }
    },
  })

  const saveProfile = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/accountant/profile", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["accountant-profile"] })
      setShowSetup(false)
      setProfileError("")
    },
    onError: (e: any) => setProfileError(e.response?.data?.message ?? "Failed to save profile"),
  })

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <Briefcase size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Accountant</h1>
          </div>
          <button onClick={() => {
            if (profile) {
              setProfileForm({
                firmName: profile.firmName ?? "", practiceNumber: profile.practiceNumber ?? "",
                vatNumber: profile.vatNumber ?? "", contactEmail: profile.contactEmail ?? "",
                contactPhone: profile.contactPhone ?? "",
                defaultHourlyRate: String(profile.defaultHourlyRate ?? "850"),
                yearEndMonth: String(profile.yearEndMonth ?? "2"),
              })
            }
            setShowSetup(true)
          }}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, color: "#64748B", cursor: "pointer", fontWeight: 600 }}>
            <Settings size={13} /> {profile ? "Practice settings" : "Set up practice"}
          </button>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          {profile ? profile.firmName : "Client portfolio · SARS compliance · Time tracking · Billing"}
        </p>
      </div>

      {/* No profile warning banner */}
      {!profile && (
        <div style={{ marginBottom: 20, padding: "14px 18px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontSize: 14, color: "#92400E" }}>
            ⚠️ Set up your practice profile first — it's used for email signatures, invoice footers, and SARS deadline reminders.
          </span>
          <button onClick={() => setShowSetup(true)}
            style={{ marginLeft: "auto", padding: "6px 14px", background: "#D97706", color: "#fff", border: "none", borderRadius: 7, fontSize: 13, fontWeight: 700, cursor: "pointer", flexShrink: 0 }}>
            Set up now
          </button>
        </div>
      )}

      {/* KPI strip */}
      {dashboard && (
        <div style={{ display: "flex", gap: 12, marginBottom: 22, flexWrap: "wrap" }}>
          {[
            { label: "Active clients",      value: dashboard.totalClients,              color: "#1B3A6B", bg: "#EEF2FF" },
            { label: "Overdue filings",     value: dashboard.overdueFilings,            color: dashboard.overdueFilings > 0 ? "#DC2626" : "#166534", bg: dashboard.overdueFilings > 0 ? "#FEF2F2" : "#F0FDF4" },
            { label: "Due next 30 days",    value: dashboard.pendingFilingsNext30Days,  color: "#D97706", bg: "#FFFBEB" },
            { label: "Unbilled WIP",        value: `R ${Number(dashboard.totalWip ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "#0D9488", bg: "#F0FDF9" },
            { label: "Outstanding invoices",value: `R ${Number(dashboard.totalOutstandingInvoices ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "#1D4ED8", bg: "#EFF6FF" },
          ].map(k => (
            <div key={k.label} style={{ background: k.bg, borderRadius: 10, padding: "12px 18px", minWidth: 140 }}>
              <div style={{ fontSize: 20, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 11, color: k.color, marginTop: 2, opacity: 0.8 }}>{k.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Tabs */}
      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon   = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 18px", background: "none", border: "none", whiteSpace: "nowrap" as const, borderBottom: active ? "2px solid #1B3A6B" : "2px solid transparent", color: active ? "#1B3A6B" : "#64748B", fontWeight: active ? 600 : 400, fontSize: 14, cursor: "pointer", marginBottom: -1 }}>
                <Icon size={15} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard" && <AccountantDashboard onNavigate={setTab} />}
        {tab === "clients"   && <ClientsTab onNavigate={setTab} />}
        {tab === "deadlines" && <DeadlinesTab />}
        {tab === "time"      && <TimeTab />}
        {tab === "billing"   && <BillingTab />}
        {tab === "journals"  && <JournalsTab />}
      </div>

      {/* Practice profile modal */}
      {showSetup && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Practice Profile</h3>
                <p style={{ margin: "4px 0 0", fontSize: 13, color: "#64748B" }}>Your firm details — used on invoices and SARS communications</p>
              </div>
              <button onClick={() => setShowSetup(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Firm name *</label>
                <input autoFocus value={profileForm.firmName} onChange={e => pf("firmName", e.target.value)} placeholder="Modise & Associates Inc" style={inp} />
              </div>
              <div>
                <label style={lbl}>Practice number (SAIPA/SAICA)</label>
                <input value={profileForm.practiceNumber} onChange={e => pf("practiceNumber", e.target.value)} placeholder="SAIPA-2019-001234" style={inp} />
              </div>
              <div>
                <label style={lbl}>VAT number</label>
                <input value={profileForm.vatNumber} onChange={e => pf("vatNumber", e.target.value)} placeholder="4123456789" style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact email *</label>
                <input type="email" value={profileForm.contactEmail} onChange={e => pf("contactEmail", e.target.value)} placeholder="admin@firm.co.za" style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact phone</label>
                <input value={profileForm.contactPhone} onChange={e => pf("contactPhone", e.target.value)} placeholder="+27 11 234 5678" style={inp} />
              </div>
              <div>
                <label style={lbl}>Default hourly rate (R)</label>
                <input type="number" value={profileForm.defaultHourlyRate} onChange={e => pf("defaultHourlyRate", e.target.value)} placeholder="850" style={inp} />
              </div>
              <div>
                <label style={lbl}>Firm year-end month</label>
                <select value={profileForm.yearEndMonth} onChange={e => pf("yearEndMonth", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {Array.from({ length: 12 }, (_, i) => (
                    <option key={i+1} value={i+1}>{new Date(0, i).toLocaleString("en", { month: "long" })}</option>
                  ))}
                </select>
              </div>
            </div>

            {profileError && (
              <div style={{ marginTop: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>
                {profileError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowSetup(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button
                disabled={!profileForm.firmName || !profileForm.contactEmail || saveProfile.isPending}
                onClick={() => saveProfile.mutate({
                  firmName: profileForm.firmName,
                  practiceNumber: profileForm.practiceNumber || null,
                  vatNumber: profileForm.vatNumber || null,
                  contactEmail: profileForm.contactEmail,
                  contactPhone: profileForm.contactPhone || null,
                  defaultHourlyRate: parseFloat(profileForm.defaultHourlyRate),
                  yearEndMonth: parseInt(profileForm.yearEndMonth),
                })}
                style={{ padding: "9px 22px", background: !profileForm.firmName ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {saveProfile.isPending ? "Saving..." : "Save Profile"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
