// src/pages/property-portal/PropPortalHomePage.tsx
//
// Per the agreed design (3 pages total, not 4 — no separate lease-detail
// route the way Warehousing/Collections Agency/etc. have a separate
// /clients/:clientId page): this single page does double duty. Zero
// leases -> empty state. Exactly one -> shows its full detail
// immediately, no extra click. More than one (agreed design decision 1
// — "query all leases") -> a picker list, selecting one shows its detail
// inline via local state, no route change.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { Home, LogOut, ChevronRight, ChevronLeft, Calendar, Wallet, ClipboardCheck } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const ACCENT = "#0D9488"

interface LeaseSummary {
  leaseId: string; propertyName: string | null; propertyAddress: Record<string, string> | null
  unitNumber: string | null; lesseeName: string; startDate: string; endDate: string | null
  monthlyRent: number; depositAmount: number; depositPaid: boolean
  paymentDay: number; escalationRate: number; status: string
}
interface Payment {
  id: string; periodYear: number; periodMonth: number; amountDue: number; amountPaid: number
  dueDate: string; paidDate: string | null; status: string
}
interface InspectionReport {
  id: string; type: string; inspectedAt: string; inspectedBy: string | null
  overallCondition: string; notes: string | null; photoUrls: string[] | null
}

const fmtR = (n: number) => `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDate = (s: string | null) => s ? new Date(s).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const MONTHS = ["", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

const STATUS_COLOR: Record<string, { bg: string; color: string }> = {
  PENDING: { bg: "var(--hf-warning-soft)", color: "var(--hf-warning-text-strong)" }, PARTIAL: { bg: "var(--hf-warning-soft)", color: "var(--hf-warning-text-strong)" },
  PAID: { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" }, OVERDUE: { bg: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" },
}

export function PropPortalHomePage() {
  const navigate = useNavigate()
  const logout = usePortalAuthStore(s => s.logout)
  const user = usePortalAuthStore(s => s.user)
  const [selectedLeaseId, setSelectedLeaseId] = useState<string | null>(null)

  const { data: leases = [], isLoading } = useQuery<LeaseSummary[]>({
    queryKey: ["prop-portal-my-leases"],
    queryFn: async () => (await apiClient.get("/api/v1/property/portal/leases")).data,
  })

  // Exactly one lease — go straight to its detail, no extra click.
  const activeLeaseId = selectedLeaseId ?? (leases.length === 1 ? leases[0].leaseId : null)
  const activeLease = leases.find(l => l.leaseId === activeLeaseId) ?? null

  const doLogout = () => {
    logout()
    navigate("/property/portal/login")
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{ background: "var(--hf-surface)", borderBottom: "1px solid var(--hf-border)", padding: "16px 32px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ width: 34, height: 34, borderRadius: 9, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Home size={17} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Tenant Portal</p>
            <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{user?.fullName ?? user?.email ?? ""}</p>
          </div>
        </div>
        <button onClick={doLogout} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "7px 14px", fontSize: 12.5, fontWeight: 600, color: "var(--hf-text-muted)", cursor: "pointer" }}>
          <LogOut size={14} /> Sign out
        </button>
      </header>

      <main style={{ maxWidth: 720, margin: "0 auto", padding: "32px 24px" }}>
        {isLoading ? (
          <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
        ) : leases.length === 0 ? (
          <>
            <h1 style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>Your lease</h1>
            <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>You don't have access to any leases yet.</p>
          </>
        ) : !activeLease ? (
          <>
            <h1 style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>Your leases</h1>
            <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: "0 0 24px" }}>Select a lease to view its details, payment history, and inspection reports.</p>
            <div style={{ display: "grid", gap: 10 }}>
              {leases.map(l => (
                <button key={l.leaseId} onClick={() => setSelectedLeaseId(l.leaseId)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "18px 20px", cursor: "pointer", textAlign: "left" }}>
                  <div>
                    <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 2px" }}>{l.propertyName ?? "Property"} {l.unitNumber ? `— Unit ${l.unitNumber}` : ""}</p>
                    <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>{fmtDate(l.startDate)} – {fmtDate(l.endDate)} · {l.status}</p>
                  </div>
                  <ChevronRight size={18} color="#CBD5E1" />
                </button>
              ))}
            </div>
          </>
        ) : (
          <LeaseDetail lease={activeLease} onBack={leases.length > 1 ? () => setSelectedLeaseId(null) : undefined} />
        )}
      </main>
    </div>
  )
}

function LeaseDetail({ lease, onBack }: { lease: LeaseSummary; onBack?: () => void }) {
  const { data: payments = [] } = useQuery<Payment[]>({
    queryKey: ["prop-portal-payments", lease.leaseId],
    queryFn: async () => (await apiClient.get(`/api/v1/property/portal/leases/${lease.leaseId}/payments`)).data,
  })
  const { data: inspections = [] } = useQuery<InspectionReport[]>({
    queryKey: ["prop-portal-inspections", lease.leaseId],
    queryFn: async () => (await apiClient.get(`/api/v1/property/portal/leases/${lease.leaseId}/inspections`)).data,
  })

  const outstanding = payments.filter(p => p.status !== "PAID").reduce((s, p) => s + (p.amountDue - p.amountPaid), 0)

  return (
    <div>
      {onBack && (
        <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 4, background: "none", border: "none", color: ACCENT, fontSize: 12.5, fontWeight: 600, cursor: "pointer", padding: 0, marginBottom: 16 }}>
          <ChevronLeft size={14} /> All leases
        </button>
      )}

      <h1 style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>
        {lease.propertyName ?? "Property"} {lease.unitNumber ? `— Unit ${lease.unitNumber}` : ""}
      </h1>
      <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: "0 0 24px" }}>
        {lease.propertyAddress ? Object.values(lease.propertyAddress).filter(Boolean).join(", ") : ""}
      </p>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 10, marginBottom: 24 }}>
        <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "16px 18px" }}>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", fontWeight: 600, marginBottom: 4 }}>MONTHLY RENT</div>
          <div style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>{fmtR(lease.monthlyRent)}</div>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>Due on day {lease.paymentDay} of each month</div>
        </div>
        <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "16px 18px" }}>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", fontWeight: 600, marginBottom: 4 }}>OUTSTANDING BALANCE</div>
          <div style={{ fontSize: 20, fontWeight: 800, color: outstanding > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>{fmtR(outstanding)}</div>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>{outstanding > 0 ? "Please settle via your usual payment method" : "All paid up"}</div>
        </div>
      </div>

      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "16px 18px", marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
          <Calendar size={14} color={ACCENT} />
          <span style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", textTransform: "uppercase" as const, letterSpacing: 0.4 }}>Lease Details</span>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 12, fontSize: 13 }}>
          <div><span style={{ color: "var(--hf-text-faint)" }}>Start date</span><br /><strong>{fmtDate(lease.startDate)}</strong></div>
          <div><span style={{ color: "var(--hf-text-faint)" }}>End date</span><br /><strong>{fmtDate(lease.endDate)}</strong></div>
          <div><span style={{ color: "var(--hf-text-faint)" }}>Deposit</span><br /><strong>{fmtR(lease.depositAmount)} {lease.depositPaid ? "(Paid)" : "(Not yet paid)"}</strong></div>
          <div><span style={{ color: "var(--hf-text-faint)" }}>Annual escalation</span><br /><strong>{lease.escalationRate}%</strong></div>
        </div>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
        <Wallet size={14} color={ACCENT} />
        <span style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", textTransform: "uppercase" as const, letterSpacing: 0.4 }}>Payment History</span>
      </div>
      {payments.length === 0 ? (
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 24 }}>No payment records yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 24 }}>
          {payments.map(p => {
            const sc = STATUS_COLOR[p.status] ?? STATUS_COLOR.PENDING
            return (
              <div key={p.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "10px 14px", fontSize: 13 }}>
                <span>{MONTHS[p.periodMonth]} {p.periodYear}</span>
                <span style={{ color: "var(--hf-text-faint)" }}>Due {fmtDate(p.dueDate)}</span>
                <span style={{ fontWeight: 700 }}>{fmtR(p.amountDue)}</span>
                <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 8px", borderRadius: 20, background: sc.bg, color: sc.color }}>{p.status}</span>
              </div>
            )
          })}
        </div>
      )}

      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
        <ClipboardCheck size={14} color={ACCENT} />
        <span style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", textTransform: "uppercase" as const, letterSpacing: 0.4 }}>Inspection Reports</span>
      </div>
      {inspections.length === 0 ? (
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No inspection reports yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {inspections.map(i => (
            <div key={i.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>{i.type.replace(/_/g, " ")}</span>
                <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{fmtDate(i.inspectedAt)}</span>
              </div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Condition: {i.overallCondition} {i.inspectedBy ? `· By ${i.inspectedBy}` : ""}</div>
              {i.notes && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>{i.notes}</div>}
              {i.photoUrls && i.photoUrls.length > 0 && (
                <div style={{ display: "flex", gap: 6, marginTop: 8, flexWrap: "wrap" }}>
                  {i.photoUrls.map((url, idx) => (
                    <img key={idx} src={url} alt="Inspection photo" style={{ width: 64, height: 64, borderRadius: 6, objectFit: "cover", border: "1px solid var(--hf-border)" }} />
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
