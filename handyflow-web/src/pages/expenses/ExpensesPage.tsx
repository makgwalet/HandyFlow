// src/pages/expenses/ExpensesPage.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, CheckCircle, XCircle, DollarSign, Receipt,
  Search, AlertTriangle, Download, TrendingUp, Clock,
  ChevronRight, User, Calendar, Tag, FileText, Fuel,
  Utensils, Car, Package, Phone, BookOpen, Wrench,
  Briefcase, MoreHorizontal, BarChart2, Filter,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────
interface Claim {
  id: string; claimNumber: string
  employeeId: string | null; employeeName: string
  claimDate: string; category: string
  description: string; amount: number; currency: string
  receiptUrl: string | null; status: string
  rejectionReason: string | null; journalEntryId: string | null
  notes: string | null; approvedAt: string | null; reimbursedAt: string | null
  createdAt: string
}
interface Employee { id: string; fullName: string }

// ── Constants ──────────────────────────────────────────────────────────────
const STATUS: Record<string, { color: string; bg: string; border: string; dot: string; label: string }> = {
  PENDING:    { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", dot: "#F59E0B", label: "Pending" },
  APPROVED:   { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", dot: "#22C55E", label: "Approved" },
  REJECTED:   { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", dot: "#EF4444", label: "Rejected" },
  REIMBURSED: { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", dot: "#3B82F6", label: "Reimbursed" },
}

const CATEGORIES: Record<string, { label: string; icon: any; color: string }> = {
  TRAVEL:         { label: "Travel",         icon: Car,       color: "#1D4ED8" },
  MEALS:          { label: "Meals",          icon: Utensils,  color: "#D97706" },
  ACCOMMODATION:  { label: "Accommodation",  icon: Briefcase, color: "#7C3AED" },
  FUEL:           { label: "Fuel",           icon: Fuel,      color: "#DC2626" },
  EQUIPMENT:      { label: "Equipment",      icon: Package,   color: "#0D9488" },
  OFFICE_SUPPLIES:{ label: "Office",         icon: FileText,  color: "#64748B" },
  MARKETING:      { label: "Marketing",      icon: TrendingUp,color: "#D97706" },
  ENTERTAINMENT:  { label: "Entertainment",  icon: MoreHorizontal, color: "#9333EA" },
  TELEPHONE:      { label: "Telephone",      icon: Phone,     color: "#0369A1" },
  OTHER:          { label: "Other",          icon: Tag,       color: "#94A3B8" },
}

// ── Helpers ────────────────────────────────────────────────────────────────
const fmtR    = (n: any) => n != null ? `R\u00A0${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: string | null) => d ? new Date(d + (d.includes("T") ? "" : "T00:00:00")).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtDT   = (d: string | null) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 700, color: "#6B7280", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }
const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const btnSecondary: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "9px 16px", border: "1.5px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151", fontWeight: 500 }

// ── Confirm Modal ──────────────────────────────────────────────────────────
function ConfirmModal({ title, message, confirmLabel, danger, onConfirm, onCancel }: {
  title: string; message: string; confirmLabel: string; danger?: boolean
  onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 400, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
        <div style={{ display: "flex", alignItems: "flex-start", gap: 14, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: "50%", background: danger ? "#FEF2F2" : "#DCFCE7", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
            {danger ? <AlertTriangle size={18} color="#DC2626" /> : <CheckCircle size={18} color="#166534" />}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A", marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: "#64748B", lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <button onClick={onCancel} style={btnSecondary}>Cancel</button>
          <button onClick={onConfirm} style={{ ...btnPrimary, background: danger ? "#DC2626" : "#166534" }}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}

// ── Main Component ─────────────────────────────────────────────────────────
export function ExpensesPage() {
  const qc = useQueryClient()
  const [statusFilter, setStatus]   = useState("")
  const [search,       setSearch]   = useState("")
  const [catFilter,    setCat]      = useState("")
  const [empFilter,    setEmp]      = useState("")
  const [showCreate,   setShowCreate] = useState(false)
  const [selected,     setSelected]   = useState<Claim | null>(null)
  const [showReject,   setShowReject] = useState(false)
  const [showApproveConfirm, setShowApproveConfirm] = useState(false)
  const [showReimburseConfirm, setShowReimburseConfirm] = useState(false)
  const [rejectReason, setRejectReason] = useState("")
  const [error,        setError]      = useState("")

  const today = new Date().toISOString().split("T")[0]
  const initForm = () => ({
    employeeName: "", employeeId: "",
    claimDate: today, category: "TRAVEL",
    description: "", amount: "", receiptUrl: "", notes: "",
  })
  const [form, setForm] = useState(initForm())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  // ── Queries ──────────────────────────────────────────────────────────────
  const { data: page, isLoading } = useQuery({
    queryKey: ["expenses", statusFilter, empFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "200" })
      if (statusFilter) params.set("status", statusFilter)
      if (empFilter)    params.set("employeeId", empFilter)
      const r = await apiClient.get(`/api/v1/expenses?${params}`)
      return r.data?.data ?? r.data
    },
  })

  const { data: monthlyTotal } = useQuery<number>({
    queryKey: ["expenses-monthly"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/expenses/summary/monthly")
      return r.data?.data ?? r.data ?? 0
    },
  })

  const { data: employees = [] } = useQuery<Employee[]>({
    queryKey: ["hr-employees-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/hr/employees?size=200")
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  // ── Mutations ─────────────────────────────────────────────────────────────
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["expenses"] })
    qc.invalidateQueries({ queryKey: ["expenses-monthly"] })
  }

  const submitClaim = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/expenses", body),
    onSuccess: () => { invalidate(); setShowCreate(false); setForm(initForm()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to submit claim"),
  })

  const approveClaim = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/expenses/${id}/approve`),
    onSuccess: (r: any) => {
      invalidate(); setShowApproveConfirm(false)
      setSelected(r.data?.data ?? r.data)
    },
    onError: (e: any) => setError(e.response?.data?.message || "Approval failed"),
  })

  const rejectClaim = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/expenses/${id}/reject`, { reason }),
    onSuccess: (r: any) => {
      invalidate(); setShowReject(false); setRejectReason(""); setError("")
      setSelected(r.data?.data ?? r.data)
    },
    onError: (e: any) => setError(e.response?.data?.message || "Rejection failed"),
  })

  const reimburse = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/expenses/${id}/reimburse`),
    onSuccess: (r: any) => {
      invalidate(); setShowReimburseConfirm(false)
      setSelected(r.data?.data ?? r.data)
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to mark reimbursed"),
  })

  // ── Derived data ──────────────────────────────────────────────────────────
  const allClaims: Claim[] = page?.content ?? page ?? []

  const filtered = allClaims.filter(c => {
    if (search && !c.employeeName.toLowerCase().includes(search.toLowerCase()) &&
        !c.description.toLowerCase().includes(search.toLowerCase()) &&
        !c.claimNumber.toLowerCase().includes(search.toLowerCase())) return false
    if (catFilter && c.category !== catFilter) return false
    return true
  })

  const pendingTotal = allClaims.filter(c => c.status === "PENDING").reduce((s, c) => s + Number(c.amount), 0)
  const pendingCount = allClaims.filter(c => c.status === "PENDING").length
  const approvedCount = allClaims.filter(c => c.status === "APPROVED").length
  const reimbursedCount = allClaims.filter(c => c.status === "REIMBURSED").length

  // Category spending breakdown
  const byCategory = allClaims.filter(c => ["APPROVED","REIMBURSED"].includes(c.status))
    .reduce((acc: Record<string, number>, c) => {
      acc[c.category] = (acc[c.category] ?? 0) + Number(c.amount)
      return acc
    }, {})
  const topCategory = Object.entries(byCategory).sort((a, b) => b[1] - a[1])[0]

  // Export CSV
  const exportCSV = () => {
    const headers = ["Claim #","Employee","Date","Category","Description","Amount","Status","Approved At","Reimbursed At"]
    const rows = filtered.map(c => [
      c.claimNumber, c.employeeName, fmtDate(c.claimDate), c.category,
      `"${c.description}"`, Number(c.amount).toFixed(2), c.status,
      c.approvedAt ? fmtDate(c.approvedAt) : "",
      c.reimbursedAt ? fmtDate(c.reimbursedAt) : "",
    ])
    const csv = [headers, ...rows].map(r => r.join(",")).join("\n")
    const a = document.createElement("a")
    a.href = "data:text/csv;charset=utf-8," + encodeURIComponent(csv)
    a.download = `expenses-${today}.csv`; a.click()
  }

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: "#D97706", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <Receipt size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Expenses</h1>
          </div>
          <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
            Staff expense claims · Approval workflow · Accounting integration
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={exportCSV} style={btnSecondary}><Download size={13} /> Export CSV</button>
          <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={14} /> Submit Claim</button>
        </div>
      </div>

      {/* KPI strip */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Pending approval", value: fmtR(pendingTotal), sub: `${pendingCount} claim${pendingCount !== 1 ? "s" : ""}`, color: "#D97706", bg: "#FFFBEB", icon: <Clock size={16} /> },
          { label: "Approved this month", value: fmtR(monthlyTotal ?? 0), sub: `${approvedCount} approved`, color: "#166534", bg: "#DCFCE7", icon: <CheckCircle size={16} /> },
          { label: "Reimbursed", value: `${reimbursedCount}`, sub: "claims paid out", color: "#1D4ED8", bg: "#EFF6FF", icon: <DollarSign size={16} /> },
          { label: "Total claims", value: `${allClaims.length}`, sub: "all time", color: "#1B3A6B", bg: "#EEF2FF", icon: <Receipt size={16} /> },
          { label: "Top category", value: topCategory ? CATEGORIES[topCategory[0]]?.label ?? topCategory[0] : "—", sub: topCategory ? fmtR(topCategory[1]) : "No data", color: "#0D9488", bg: "#F0FDF9", icon: <BarChart2 size={16} /> },
        ].map(k => (
          <div key={k.label} style={{ background: "#fff", border: "1px solid #E5E7EB", borderRadius: 12, padding: "14px 18px", display: "flex", alignItems: "center", gap: 12 }}>
            <div style={{ width: 36, height: 36, borderRadius: 9, background: k.bg, display: "flex", alignItems: "center", justifyContent: "center", color: k.color, flexShrink: 0 }}>{k.icon}</div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 800, color: k.color, letterSpacing: "-0.02em" }}>{k.value}</div>
              <div style={{ fontSize: 10, color: "#9CA3AF", marginTop: 1 }}>{k.label}</div>
              <div style={{ fontSize: 10, color: k.color, opacity: 0.7 }}>{k.sub}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Main card */}
      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        {/* Toolbar */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            {/* Status filters */}
            {["", "PENDING", "APPROVED", "REJECTED", "REIMBURSED"].map(s => {
              const cfg = STATUS[s]
              const active = statusFilter === s
              return (
                <button key={s} onClick={() => setStatus(s)}
                  style={{ padding: "6px 13px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: active ? 700 : 500, border: `1.5px solid ${active && cfg ? cfg.border : "#E2E8F0"}`, background: active && cfg ? cfg.bg : "#fff", color: active && cfg ? cfg.color : "#64748B", display: "flex", alignItems: "center", gap: 5 }}>
                  {s && cfg && <span style={{ width: 6, height: 6, borderRadius: "50%", background: cfg.dot }} />}
                  {s ? cfg.label : "All claims"}
                </button>
              )
            })}

            {/* Search */}
            <div style={{ position: "relative" as const }}>
              <Search size={13} style={{ position: "absolute" as const, left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search claims..."
                style={{ paddingLeft: 28, padding: "7px 10px 7px 28px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", width: 180 }} />
            </div>

            {/* Category filter */}
            <select value={catFilter} onChange={e => setCat(e.target.value)}
              style={{ padding: "7px 10px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
              <option value="">All categories</option>
              {Object.entries(CATEGORIES).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
            </select>

            {/* Employee filter */}
            {employees.length > 0 && (
              <select value={empFilter} onChange={e => setEmp(e.target.value)}
                style={{ padding: "7px 10px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
                <option value="">All employees</option>
                {employees.map((e: Employee) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
              </select>
            )}

            {(search || catFilter || empFilter) && (
              <button onClick={() => { setSearch(""); setCat(""); setEmp("") }}
                style={{ display: "flex", alignItems: "center", gap: 4, padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12, background: "#F8FAFC", color: "#64748B", cursor: "pointer" }}>
                <X size={11} /> Clear
              </button>
            )}
          </div>
          <div style={{ fontSize: 12, color: "#94A3B8" }}>{filtered.length} claim{filtered.length !== 1 ? "s" : ""}</div>
        </div>

        {/* Category spending bar */}
        {Object.keys(byCategory).length > 0 && (
          <div style={{ marginBottom: 18, padding: "12px 16px", background: "#F8FAFC", borderRadius: 10, border: "1px solid #E2E8F0" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 10 }}>Approved spend by category</div>
            <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
              {Object.entries(byCategory).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([cat, total]) => {
                const cfg = CATEGORIES[cat]
                const Icon = cfg?.icon ?? Tag
                return (
                  <div key={cat} style={{ display: "flex", alignItems: "center", gap: 7 }}>
                    <div style={{ width: 24, height: 24, borderRadius: 6, background: `${cfg?.color ?? "#64748B"}20`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                      <Icon size={12} color={cfg?.color ?? "#64748B"} />
                    </div>
                    <div>
                      <div style={{ fontSize: 10, color: "#9CA3AF" }}>{cfg?.label ?? cat}</div>
                      <div style={{ fontSize: 12, fontWeight: 700, color: "#0F172A" }}>{fmtR(total)}</div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* Claims table */}
        {isLoading ? (
          <div style={{ textAlign: "center", padding: 48, color: "#94A3B8" }}>Loading claims...</div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
            <Receipt size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
            <div style={{ fontWeight: 700, color: "#475569", fontSize: 15, marginBottom: 6 }}>No expense claims found</div>
            <div style={{ fontSize: 13, marginBottom: 20 }}>Submit your first claim or adjust the filters above.</div>
            <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} /> Submit Claim</button>
          </div>
        ) : (
          <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" as const, fontSize: 13 }}>
              <thead>
                <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                  {["Claim", "Employee", "Category", "Date", "Amount", "Status", ""].map(h => (
                    <th key={h} style={{ padding: "10px 16px", textAlign: "left" as const, fontSize: 11, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em", whiteSpace: "nowrap" as const }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((c, i) => {
                  const sc  = STATUS[c.status] ?? STATUS.PENDING
                  const catCfg = CATEGORIES[c.category]
                  const Icon   = catCfg?.icon ?? Tag
                  return (
                    <tr key={c.id}
                      style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", cursor: "pointer", transition: "background 0.1s" }}
                      onClick={() => setSelected(c)}
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = "#F0F9FF"}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? "#fff" : "#FAFAFA"}>
                      <td style={{ padding: "12px 16px" }}>
                        <div style={{ fontWeight: 700, color: "#0F172A" }}>{c.description}</div>
                        <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 1 }}>#{c.claimNumber}</div>
                      </td>
                      <td style={{ padding: "12px 16px" }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                          <div style={{ width: 26, height: 26, borderRadius: "50%", background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                            <span style={{ fontSize: 10, color: "#fff", fontWeight: 700 }}>{c.employeeName.charAt(0).toUpperCase()}</span>
                          </div>
                          <span style={{ color: "#374151" }}>{c.employeeName}</span>
                        </div>
                      </td>
                      <td style={{ padding: "12px 16px" }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                          <div style={{ width: 22, height: 22, borderRadius: 5, background: `${catCfg?.color ?? "#94A3B8"}18`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                            <Icon size={11} color={catCfg?.color ?? "#94A3B8"} />
                          </div>
                          <span style={{ fontSize: 12, color: "#64748B" }}>{catCfg?.label ?? c.category}</span>
                        </div>
                      </td>
                      <td style={{ padding: "12px 16px", color: "#64748B", fontSize: 12 }}>{fmtDate(c.claimDate)}</td>
                      <td style={{ padding: "12px 16px", fontWeight: 800, color: "#0F172A", fontSize: 14 }}>{fmtR(c.amount)}</td>
                      <td style={{ padding: "12px 16px" }}>
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <span style={{ width: 5, height: 5, borderRadius: "50%", background: sc.dot }} />{sc.label}
                        </span>
                      </td>
                      <td style={{ padding: "12px 16px" }}>
                        <div style={{ display: "flex", gap: 5 }}>
                          {c.status === "PENDING" && (
                            <>
                              <button onClick={e => { e.stopPropagation(); setSelected(c); setShowApproveConfirm(true) }}
                                style={{ display: "flex", alignItems: "center", gap: 3, padding: "4px 10px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: "pointer" }}>
                                <CheckCircle size={10} /> Approve
                              </button>
                              <button onClick={e => { e.stopPropagation(); setSelected(c); setShowReject(true); setError("") }}
                                style={{ display: "flex", alignItems: "center", gap: 3, padding: "4px 8px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 6, fontSize: 11, cursor: "pointer" }}>
                                <XCircle size={10} />
                              </button>
                            </>
                          )}
                          {c.status === "APPROVED" && (
                            <button onClick={e => { e.stopPropagation(); setSelected(c); setShowReimburseConfirm(true) }}
                              style={{ display: "flex", alignItems: "center", gap: 3, padding: "4px 10px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: "pointer" }}>
                              <DollarSign size={10} /> Pay
                            </button>
                          )}
                          <ChevronRight size={14} color="#94A3B8" />
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
              {/* Totals footer */}
              {filtered.length > 1 && (
                <tfoot>
                  <tr style={{ background: "#F8FAFC", borderTop: "1px solid #E2E8F0" }}>
                    <td colSpan={4} style={{ padding: "10px 16px", fontSize: 12, color: "#64748B", fontWeight: 600 }}>
                      {filtered.length} claims shown
                    </td>
                    <td style={{ padding: "10px 16px", fontWeight: 800, color: "#0F172A", fontSize: 14 }}>
                      {fmtR(filtered.reduce((s, c) => s + Number(c.amount), 0))}
                    </td>
                    <td colSpan={2} />
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        )}
      </div>

      {/* ── Claim detail slide-over ─────────────────────────────────────── */}
      {selected && !showReject && !showApproveConfirm && !showReimburseConfirm && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "flex-end", zIndex: 1000 }}>
          <div style={{ background: "#fff", width: 480, height: "100%", overflowY: "auto", boxShadow: "-8px 0 40px rgba(0,0,0,0.15)", padding: 28 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 22 }}>
              <div>
                <div style={{ fontWeight: 800, fontSize: 17, color: "#0F172A", marginBottom: 6 }}>{selected.description}</div>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  {(() => {
                    const sc = STATUS[selected.status] ?? STATUS.PENDING
                    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: "2px 9px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>
                      <span style={{ width: 5, height: 5, borderRadius: "50%", background: sc.dot }} />{sc.label}
                    </span>
                  })()}
                  <span style={{ fontSize: 12, color: "#94A3B8" }}>#{selected.claimNumber}</span>
                </div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: "#F1F5F9", border: "none", borderRadius: "50%", width: 30, height: 30, display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", color: "#64748B" }}>
                <X size={14} />
              </button>
            </div>

            {/* Amount hero */}
            <div style={{ background: "#F8FAFC", borderRadius: 12, padding: "18px 20px", marginBottom: 22, textAlign: "center" as const, border: "1px solid #E2E8F0" }}>
              <div style={{ fontSize: 32, fontWeight: 900, color: "#0F172A", letterSpacing: "-0.03em" }}>{fmtR(selected.amount)}</div>
              <div style={{ fontSize: 13, color: "#64748B", marginTop: 4 }}>
                {CATEGORIES[selected.category]?.label ?? selected.category} · {fmtDate(selected.claimDate)}
              </div>
            </div>

            {/* Metadata */}
            <div style={{ display: "flex", flexDirection: "column", gap: 12, marginBottom: 20 }}>
              {[
                { label: "Employee",   value: selected.employeeName,                    icon: <User size={13} /> },
                { label: "Category",   value: CATEGORIES[selected.category]?.label ?? selected.category, icon: <Tag size={13} /> },
                { label: "Claim date", value: fmtDate(selected.claimDate),              icon: <Calendar size={13} /> },
                { label: "Submitted",  value: fmtDT(selected.createdAt),               icon: <Clock size={13} /> },
                { label: "Approved",   value: selected.approvedAt ? fmtDT(selected.approvedAt) : "—", icon: <CheckCircle size={13} /> },
                { label: "Reimbursed", value: selected.reimbursedAt ? fmtDT(selected.reimbursedAt) : "—", icon: <DollarSign size={13} /> },
              ].map(row => (
                <div key={row.label} style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid #F1F5F9", fontSize: 13 }}>
                  <span style={{ display: "flex", alignItems: "center", gap: 6, color: "#94A3B8", fontWeight: 600 }}>
                    {row.icon}{row.label}
                  </span>
                  <span style={{ color: "#374151", fontWeight: 500 }}>{row.value}</span>
                </div>
              ))}
            </div>

            {selected.notes && (
              <div style={{ marginBottom: 16, padding: "12px 14px", background: "#FFFBEB", borderRadius: 9, border: "1px solid #FDE68A" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#D97706", marginBottom: 4, textTransform: "uppercase", letterSpacing: "0.06em" }}>Notes</div>
                <div style={{ fontSize: 13, color: "#374151" }}>{selected.notes}</div>
              </div>
            )}

            {selected.rejectionReason && (
              <div style={{ marginBottom: 16, padding: "12px 14px", background: "#FEF2F2", borderRadius: 9, border: "1px solid #FECACA" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#DC2626", marginBottom: 4, textTransform: "uppercase", letterSpacing: "0.06em" }}>Rejection reason</div>
                <div style={{ fontSize: 13, color: "#DC2626" }}>{selected.rejectionReason}</div>
              </div>
            )}

            {selected.journalEntryId && (
              <div style={{ marginBottom: 16, padding: "10px 14px", background: "#F0FDF4", borderRadius: 9, border: "1px solid #86EFAC", display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "#166534", fontWeight: 600 }}>
                <CheckCircle size={14} /> Journal entry posted to Accounting
              </div>
            )}

            {selected.receiptUrl && (
              <a href={selected.receiptUrl} target="_blank" rel="noreferrer"
                style={{ display: "inline-flex", alignItems: "center", gap: 6, marginBottom: 20, fontSize: 13, color: "#1D4ED8", fontWeight: 600 }}>
                <FileText size={14} /> View receipt
              </a>
            )}

            {error && <div style={{ marginBottom: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

            {/* Actions */}
            {selected.status === "PENDING" && (
              <div style={{ display: "flex", gap: 10 }}>
                <button onClick={() => { setShowReject(true); setError("") }}
                  style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "11px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                  <XCircle size={14} /> Reject
                </button>
                <button onClick={() => setShowApproveConfirm(true)}
                  style={{ flex: 2, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "11px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                  <CheckCircle size={14} /> Approve claim
                </button>
              </div>
            )}
            {selected.status === "APPROVED" && (
              <button onClick={() => setShowReimburseConfirm(true)}
                style={{ width: "100%", display: "flex", alignItems: "center", justifyContent: "center", gap: 7, padding: "12px", background: "#EFF6FF", color: "#1D4ED8", border: "1.5px solid #BFDBFE", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                <DollarSign size={14} /> Mark as reimbursed
              </button>
            )}
          </div>
        </div>
      )}

      {/* ── Reject modal ───────────────────────────────────────────────── */}
      {showReject && selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", alignItems: "flex-start", gap: 14, marginBottom: 20 }}>
              <div style={{ width: 40, height: 40, borderRadius: "50%", background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                <XCircle size={18} color="#DC2626" />
              </div>
              <div>
                <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 800, color: "#DC2626" }}>Reject claim</h3>
                <p style={{ margin: 0, fontSize: 13, color: "#64748B" }}>
                  {selected.description} — <strong>{fmtR(selected.amount)}</strong>
                </p>
              </div>
            </div>
            <div>
              <label style={lbl}>Reason for rejection *</label>
              <textarea value={rejectReason} onChange={e => setRejectReason(e.target.value)} rows={4} autoFocus
                placeholder="Explain why this claim is being rejected so the employee can resubmit or understand the decision..."
                style={{ ...inp, resize: "vertical" as const, fontFamily: "inherit" }} />
              <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 5 }}>The employee will see this reason on their claim.</div>
            </div>
            {error && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowReject(false); setRejectReason(""); setError("") }} style={btnSecondary}>Cancel</button>
              <button disabled={!rejectReason.trim() || rejectClaim.isPending}
                onClick={() => rejectClaim.mutate({ id: selected.id, reason: rejectReason })}
                style={{ ...btnPrimary, background: !rejectReason.trim() ? "#94A3B8" : "#DC2626", opacity: !rejectReason.trim() ? 0.7 : 1 }}>
                {rejectClaim.isPending ? "Rejecting..." : "Reject claim"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Submit claim modal ─────────────────────────────────────────── */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 20, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 580, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 25px 80px rgba(0,0,0,0.25)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>Submit Expense Claim</h3>
                <p style={{ margin: "3px 0 0", fontSize: 13, color: "#64748B" }}>Claims require approval before reimbursement</p>
              </div>
              <button onClick={() => { setShowCreate(false); setForm(initForm()) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Employee *</label>
                {employees.length > 0 ? (
                  <select value={form.employeeId} onChange={e => {
                    const emp = employees.find((x: Employee) => x.id === e.target.value) as Employee | undefined
                    f("employeeId", e.target.value)
                    f("employeeName", emp?.fullName ?? "")
                  }} style={{ ...inp, background: "#fff" }}>
                    <option value="">Select employee...</option>
                    {employees.map((e: Employee) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
                  </select>
                ) : (
                  <input value={form.employeeName} onChange={e => f("employeeName", e.target.value)} placeholder="Full name" style={inp} autoFocus />
                )}
              </div>

              <div>
                <label style={lbl}>Claim date *</label>
                <input type="date" value={form.claimDate} onChange={e => f("claimDate", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Category *</label>
                <select value={form.category} onChange={e => f("category", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {Object.entries(CATEGORIES).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                </select>
              </div>

              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description *</label>
                <input value={form.description} onChange={e => f("description", e.target.value)}
                  placeholder="Team lunch at Mugg & Bean, fuel for site visit to Centurion, etc." style={inp} />
              </div>

              <div>
                <label style={lbl}>Amount (R) *</label>
                <input type="number" min="0.01" step="0.01" value={form.amount} onChange={e => f("amount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div>
                <label style={lbl}>Receipt URL</label>
                <input value={form.receiptUrl} onChange={e => f("receiptUrl", e.target.value)} placeholder="https://drive.google.com/..." style={inp} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>Paste a link to the uploaded receipt image</div>
              </div>

              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Notes (optional)</label>
                <textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2}
                  placeholder="Business purpose, project name, attendees..." style={{ ...inp, resize: "vertical" as const, fontFamily: "inherit" }} />
              </div>
            </div>

            <div style={{ marginTop: 14, padding: "11px 14px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 9, fontSize: 12, color: "#166534" }}>
              Once approved, a journal entry (DR Expenses / CR Accounts Payable) will automatically be posted to Accounting.
            </div>

            {error && <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 22 }}>
              <button onClick={() => { setShowCreate(false); setForm(initForm()) }} style={btnSecondary}>Cancel</button>
              <button
                disabled={!form.employeeName || !form.description || !form.amount || !form.claimDate || submitClaim.isPending}
                onClick={() => submitClaim.mutate({
                  employeeId: form.employeeId || null,
                  employeeName: form.employeeName,
                  claimDate: form.claimDate,
                  category: form.category,
                  description: form.description,
                  amount: parseFloat(form.amount),
                  receiptUrl: form.receiptUrl || null,
                  notes: form.notes || null,
                })}
                style={{ ...btnPrimary, opacity: (!form.employeeName || !form.description || !form.amount) ? 0.5 : 1 }}>
                {submitClaim.isPending ? "Submitting..." : <><Receipt size={13} /> Submit claim</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Confirm modals ─────────────────────────────────────────────── */}
      {showApproveConfirm && selected && (
        <ConfirmModal
          title="Approve expense claim?"
          message={`Approve ${selected.description} (${fmtR(selected.amount)}) for ${selected.employeeName}? A journal entry will be automatically posted to Accounting.`}
          confirmLabel="Approve"
          onConfirm={() => approveClaim.mutate(selected.id)}
          onCancel={() => setShowApproveConfirm(false)}
        />
      )}
      {showReimburseConfirm && selected && (
        <ConfirmModal
          title="Mark as reimbursed?"
          message={`Confirm that ${fmtR(selected.amount)} has been paid out to ${selected.employeeName} for "${selected.description}".`}
          confirmLabel="Mark reimbursed"
          onConfirm={() => reimburse.mutate(selected.id)}
          onCancel={() => setShowReimburseConfirm(false)}
        />
      )}
    </div>
  )
}
