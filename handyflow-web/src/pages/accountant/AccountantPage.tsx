// src/pages/accountant/AccountantPage.tsx
import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { X, Settings } from "lucide-react"
import AccountantDashboard from "./AccountantDashboard"
import ClientsTab          from "./ClientsTab"
import DeadlinesTab        from "./DeadlinesTab"
import TimeTab             from "./TimeTab"
import BillingTab          from "./BillingTab"
// NEW: closes the #2 must-fix gap from the accountant module audit —
// journals were fully modeled and postable but had no tab at all.
import JournalsTab         from "./JournalsTab"
// NEW: closes the "larger workpaper system" gap.
import WorkpapersTab       from "./WorkpapersTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { ACCOUNTANT_SECTIONS } from "../../navigation/moduleSections"

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

export function AccountantPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  // "View all" from a client's workspace opens Time or Billing filtered to
  // that client (see ClientsTab's own onNavigate comment). The filter lives
  // in the URL (?client=<id>) so it survives refresh and can be shared.
  const [searchParams] = useSearchParams()
  const clientId = searchParams.get("client") ?? undefined
  const [showSetup, setShowSetup] = useState(false)
  const [profileForm, setProfileForm] = useState({
    firmName: "", practiceNumber: "", vatNumber: "",
    contactEmail: "", contactPhone: "", defaultHourlyRate: "850", yearEndMonth: "2",
    addressStreet: "", addressSuburb: "", addressCity: "", addressProvince: "", addressPostalCode: "",
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

  const goToSection = (id: string, clientId?: string) =>
    navigate(`${ACCOUNTANT_SECTIONS.basePath}/${id}${clientId ? `?client=${encodeURIComponent(clientId)}` : ""}`)

  const settingsButton = (
              <button onClick={() => {
                if (profile) {
                  setProfileForm({
                    firmName: profile.firmName ?? "", practiceNumber: profile.practiceNumber ?? "",
                    vatNumber: profile.vatNumber ?? "", contactEmail: profile.contactEmail ?? "",
                    contactPhone: profile.contactPhone ?? "",
                    defaultHourlyRate: String(profile.defaultHourlyRate ?? "850"),
                    yearEndMonth: String(profile.yearEndMonth ?? "2"),
                    addressStreet: profile.addressStreet ?? "", addressSuburb: profile.addressSuburb ?? "",
                    addressCity: profile.addressCity ?? "", addressProvince: profile.addressProvince ?? "",
                    addressPostalCode: profile.addressPostalCode ?? "",
                  })
                }
                setShowSetup(true)
              }}
                style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 13, color: "var(--hf-text-muted)", cursor: "pointer", fontWeight: 600 }}>
                <Settings size={13} /> {profile ? "Practice settings" : "Set up practice"}
              </button>
  )

  const banner = (
    <>
            {!profile && (
              <div style={{ marginBottom: 20, padding: "14px 18px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ fontSize: 14, color: "var(--hf-warning-text-deep)" }}>
                  ⚠️ Set up your practice profile first — it's used for email signatures, invoice footers, and SARS deadline reminders.
                </span>
                <button onClick={() => setShowSetup(true)}
                  style={{ marginLeft: "auto", padding: "6px 14px", background: "var(--hf-warning)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 13, fontWeight: 700, cursor: "pointer", flexShrink: 0 }}>
                  Set up now
                </button>
              </div>
            )}

            {dashboard && (
              <div style={{ display: "flex", gap: 12, marginBottom: 22, flexWrap: "wrap" }}>
                {[
                  { label: "Active clients",      value: dashboard.totalClients,              color: "var(--hf-primary-text)", bg: "var(--hf-indigo-soft)" },
                  { label: "Overdue filings",     value: dashboard.overdueFilings,            color: dashboard.overdueFilings > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)", bg: dashboard.overdueFilings > 0 ? "var(--hf-danger-soft)" : "var(--hf-success-soft)" },
                  { label: "Due next 30 days",    value: dashboard.pendingFilingsNext30Days,  color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
                  { label: "Unbilled WIP",        value: `R ${Number(dashboard.totalWip ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)" },
                  { label: "Outstanding invoices",value: `R ${Number(dashboard.totalOutstandingInvoices ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
                ].map(k => (
                  <div key={k.label} style={{ background: k.bg, borderRadius: 10, padding: "12px 18px", minWidth: 140 }}>
                    <div style={{ fontSize: 20, fontWeight: 800, color: k.color }}>{k.value}</div>
                    <div style={{ fontSize: 11, color: k.color, marginTop: 2, opacity: 0.8 }}>{k.label}</div>
                  </div>
                ))}
              </div>
            )}
    </>
  )

  return (
    <SectionedModulePage
      config={ACCOUNTANT_SECTIONS}
      action={settingsButton}
      subtitle={profile ? profile.firmName : "Client portfolio · SARS compliance · Time tracking · Billing"}
      banner={banner}
      render={id => {
        switch (id) {
          case "dashboard":  return <AccountantDashboard onNavigate={t => goToSection(t)} />
          case "clients":    return <ClientsTab onNavigate={(t, clientId) => goToSection(t, clientId)} />
          case "deadlines":  return <DeadlinesTab />
          case "time":       return <TimeTab key={clientId ?? "all"} initialClientId={clientId} />
          case "billing":    return <BillingTab key={clientId ?? "all"} initialClientId={clientId} />
          case "journals":   return <JournalsTab />
          case "workpapers": return <WorkpapersTab />
          default:           return null
        }
      }}
    >
      {showSetup && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 560, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Practice Profile</h3>
                <p style={{ margin: "4px 0 0", fontSize: 13, color: "var(--hf-text-muted)" }}>Your firm details — used on invoices and SARS communications</p>
              </div>
              <button onClick={() => setShowSetup(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={20} /></button>
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
                                <select value={profileForm.yearEndMonth} onChange={e => pf("yearEndMonth", e.target.value)} style={{ ...inp, background: "var(--hf-surface)" }}>
                  {Array.from({ length: 12 }, (_, i) => (
                    <option key={i+1} value={i+1}>{new Date(0, i).toLocaleString("en", { month: "long" })}</option>
                  ))}
                </select>
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Street address</label>
                <input value={profileForm.addressStreet} onChange={e => pf("addressStreet", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Suburb</label>
                <input value={profileForm.addressSuburb} onChange={e => pf("addressSuburb", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>City</label>
                <input value={profileForm.addressCity} onChange={e => pf("addressCity", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Province</label>
                <input value={profileForm.addressProvince} onChange={e => pf("addressProvince", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Postal code</label>
                <input value={profileForm.addressPostalCode} onChange={e => pf("addressPostalCode", e.target.value)} style={inp} />
              </div>
            </div>

            {profileError && (
              <div style={{ marginTop: 14, padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>
                {profileError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowSetup(false)} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer" }}>Cancel</button>
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
                  addressStreet: profileForm.addressStreet || null,
                  addressSuburb: profileForm.addressSuburb || null,
                  addressCity: profileForm.addressCity || null,
                  addressProvince: profileForm.addressProvince || null,
                  addressPostalCode: profileForm.addressPostalCode || null,
                })}
                style={{ padding: "9px 22px", background: !profileForm.firmName ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {saveProfile.isPending ? "Saving..." : "Save Profile"}
              </button>
            </div>
          </div>
        </div>
      )}
    </SectionedModulePage>
  )
}
