// src/pages/trainingprovider-portal/TrainProvPortalHomePage.tsx
//
// Unlike Warehousing/Collections Agency's portal (a home page listing
// multiple accessible client accounts, then drilling into one), this
// module's TrainProvPortalDataController exposes no client-list
// endpoint at all — every method (getMyDelegates/getMyEnrollments/
// getMyCertificates/getMyInvoices) resolves the caller's own single
// clientId server-side from their ACCEPTED grant and returns that
// client's data directly (confirmed via TrainProvPortalDataService's
// own Javadoc: "a portal user in this first pass is linked to exactly
// one client"). So there is no client-detail sub-page here — this IS
// the client's home, straight after login, with sub-tabs for each data
// area. GET /api/v1/training-provider/portal/me/{delegates,enrollments,
// certificates,invoices} — all confirmed via TrainProvPortalDataController.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { GraduationCap, LogOut, Users, ClipboardList, Award, FileText } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const ACCENT = "#B45309"
const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)

interface DelegateResponse { id: string; delegateNumber: string; fullName: string; jobTitle: string | null; email: string | null; status: string }
interface EnrollmentResponse { id: string; delegateNameSnapshot: string; status: string; enrolledAt: string; completedAt: string | null; score: number | null; passed: boolean | null }
interface CertificateResponse { id: string; delegateNameSnapshot: string; courseTitleSnapshot: string; certificateNumber: string; issueDate: string; expiryDate: string | null; status: string }
interface InvoiceResponse { id: string; invoiceNumber: string; periodStart: string; periodEnd: string; dueDate: string; total: number; amountPaid: number; balance: number; status: string }

type SubTab = "delegates" | "enrollments" | "certificates" | "invoices"
const SUB_TABS: { key: SubTab; label: string; icon: typeof Users }[] = [
  { key: "delegates", label: "Delegates", icon: Users },
  { key: "enrollments", label: "Enrollments", icon: ClipboardList },
  { key: "certificates", label: "Certificates", icon: Award },
  { key: "invoices", label: "Invoices", icon: FileText },
]

export function TrainProvPortalHomePage() {
  const navigate = useNavigate()
  const portalAuth = usePortalAuthStore() as any
  const [sub, setSub] = useState<SubTab>("delegates")

  const { data: delegates = [], isLoading: delegatesLoading } = useQuery<DelegateResponse[]>({
    queryKey: ["trainprov-portal-delegates"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/portal/me/delegates")).data,
    enabled: sub === "delegates",
  })
  const { data: enrollData, isLoading: enrollLoading } = useQuery<{ content: EnrollmentResponse[] }>({
    queryKey: ["trainprov-portal-enrollments"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/portal/me/enrollments", { params: { size: 100 } })).data,
    enabled: sub === "enrollments",
  })
  const { data: certData, isLoading: certLoading } = useQuery<{ content: CertificateResponse[] }>({
    queryKey: ["trainprov-portal-certificates"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/portal/me/certificates", { params: { size: 100 } })).data,
    enabled: sub === "certificates",
  })
  const { data: invoices = [], isLoading: invoicesLoading } = useQuery<InvoiceResponse[]>({
    queryKey: ["trainprov-portal-invoices"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/portal/me/invoices")).data,
    enabled: sub === "invoices",
  })

  const logout = () => { portalAuth.logout?.(); navigate("/training-provider/portal/login") }

  return (
    <div style={{ minHeight: "100vh", background: "#F1F5F9", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{ background: "#fff", borderBottom: "1px solid #E2E8F0", padding: "16px 32px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ width: 34, height: 34, borderRadius: 9, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <GraduationCap size={17} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 14, fontWeight: 800, color: "#0F172A", margin: 0 }}>Training Provider Portal</p>
            <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{portalAuth.user?.fullName ?? portalAuth.user?.email ?? ""}</p>
          </div>
        </div>
        <button onClick={logout} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "7px 14px", fontSize: 12.5, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
          <LogOut size={14} /> Sign out
        </button>
      </header>

      <main style={{ maxWidth: 860, margin: "0 auto", padding: "28px 24px" }}>
        <h1 style={{ fontSize: 20, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>Your training account</h1>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: "0 0 24px" }}>Delegates, enrollments, certificates and invoicing for your organization.</p>

        <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 20 }}>
          {SUB_TABS.map(t => {
            const Icon = t.icon
            const active = sub === t.key
            return (
              <button key={t.key} onClick={() => setSub(t.key)}
                style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 14px", border: "none", background: "none", cursor: "pointer", fontSize: 12.5, fontWeight: 600, color: active ? ACCENT : "#64748B", borderBottom: active ? `2px solid ${ACCENT}` : "2px solid transparent", marginBottom: -1 }}>
                <Icon size={13} /> {t.label}
              </button>
            )
          })}
        </div>

        {sub === "delegates" && (
          delegatesLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          delegates.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No delegates on file.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {delegates.map((d, i) => (
                <div key={d.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <div>
                    <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{d.fullName}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{d.delegateNumber}{d.jobTitle ? ` · ${d.jobTitle}` : ""}</p>
                  </div>
                  {d.status === "INACTIVE" && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: "#F1F5F9", color: "#64748B", alignSelf: "center" }}>INACTIVE</span>}
                </div>
              ))}
            </div>
          )
        )}

        {sub === "enrollments" && (
          enrollLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          (enrollData?.content ?? []).length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No enrollments yet.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {(enrollData?.content ?? []).map((e, i) => (
                <div key={e.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <span style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600 }}>{e.delegateNameSnapshot}</span>
                  <span style={{ fontSize: 11.5, color: "#64748B" }}>{e.status.replace(/_/g, " ")}{e.score != null ? ` · Score ${e.score}` : ""}</span>
                </div>
              ))}
            </div>
          )
        )}

        {sub === "certificates" && (
          certLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          (certData?.content ?? []).length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No certificates issued yet.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {(certData?.content ?? []).map((c, i) => (
                <div key={c.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <div>
                    <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{c.delegateNameSnapshot}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{c.courseTitleSnapshot} · {c.certificateNumber}</p>
                  </div>
                  <span style={{ fontSize: 11.5, color: c.status === "VALID" ? "#059669" : "#94A3B8", alignSelf: "center" }}>{c.status}</span>
                </div>
              ))}
            </div>
          )
        )}

        {sub === "invoices" && (
          invoicesLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          invoices.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No invoices yet.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {invoices.map((inv, i) => (
                <div key={inv.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <div>
                    <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{inv.invoiceNumber}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{inv.periodStart} → {inv.periodEnd} · Due {inv.dueDate}</p>
                  </div>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: 12.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{fmtMoney(inv.total)}</p>
                    <p style={{ fontSize: 11, color: inv.balance > 0 ? "#D97706" : "#059669", margin: 0 }}>{inv.balance > 0 ? `${fmtMoney(inv.balance)} due` : "Paid"}</p>
                  </div>
                </div>
              ))}
            </div>
          )
        )}
      </main>
    </div>
  )
}
