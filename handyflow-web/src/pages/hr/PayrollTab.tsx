import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Play, ChevronDown, ChevronUp, Download } from "lucide-react"

interface PayRun {
  id: string
  payRunNumber: string
  periodStart: string
  periodEnd: string
  payDate: string
  taxYear: number
  status: string
  totalGross: number
  totalPaye: number
  totalUif: number
  totalSdl: number
  totalNet: number
  employeeCount: number
  notes: string | null
  processedAt: string | null
}

interface Payslip {
  id: string
  employeeId: string
  employeeName: string
  employeeNumber: string
  payRunId: string
  payRunNumber: string
  grossSalary: number
  overtimeAmount: number
  bonusAmount: number
  travelAllowance: number
  totalEarnings: number
  payeAmount: number
  uifEmployee: number
  medicalAid: number
  pension: number
  totalDeductions: number
  uifEmployer: number
  sdlAmount: number
  netPay: number
  ytdGross: number
  ytdPaye: number
  taxableIncome: number
  taxYear: number
}

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  DRAFT:     { color: "#D97706", bg: "#FFFBEB" },
  PROCESSED: { color: "#166534", bg: "#DCFCE7" },
  PAID:      { color: "#1D4ED8", bg: "#EFF6FF" },
}

export default function PayrollTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [error, setError]           = useState("")

  const today = new Date()
  const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().split("T")[0]
  const lastOfMonth  = new Date(today.getFullYear(), today.getMonth() + 1, 0).toISOString().split("T")[0]

  const [form, setForm] = useState({
    periodStart: firstOfMonth,
    periodEnd: lastOfMonth,
    payDate: lastOfMonth,
    notes: "",
  })

  const { data: page, isLoading } = useQuery({
    queryKey: ["pay-runs"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/hr/pay-runs?size=50")
      return r.data
    },
  })

  const { data: payslips } = useQuery<Payslip[]>({
    queryKey: ["payslips", expanded],
    queryFn: async () => {
      if (!expanded) return []
      const r = await apiClient.get(`/api/v1/hr/pay-runs/${expanded}/payslips`)
      return r.data
    },
    enabled: !!expanded,
  })

  const createPayRun = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/hr/pay-runs", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["pay-runs"] }); setShowCreate(false) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create pay run"),
  })

  const processPayRun = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/hr/pay-runs/${id}/process`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["pay-runs"] }); qc.invalidateQueries({ queryKey: ["payslips"] }) },
    onError: (e: any) => alert(e.response?.data?.message || "Failed to process pay run"),
  })

  const downloadPayslip = (id: string, name: string) => {
    const token = (window as any).__AUTH_TOKEN__ || ""
    apiClient.get(`/api/v1/hr/payslips/${id}/pdf`, { responseType: "blob" } as any)
      .then((res: any) => {
        const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
        const a = document.createElement("a"); a.href = url; a.download = `payslip-${name}.pdf`
        a.click(); URL.revokeObjectURL(url)
      })
      .catch(() => alert("Failed to download payslip PDF"))
  }

  const payRuns: PayRun[] = page?.content || []
  const fmtR = (n: number) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 20 }}>
        <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={15} /> New Pay Run</button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading pay runs...</div>
      ) : payRuns.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <div style={{ fontWeight: 600, color: "#475569" }}>No pay runs yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Create your first pay run to process payroll.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {payRuns.map(run => {
            const style = STATUS_STYLE[run.status] || { color: "#475569", bg: "#F8FAFC" }
            const isOpen = expanded === run.id
            return (
              <div key={run.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
                {/* Pay run header */}
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "16px 20px", background: isOpen ? "#F8FAFC" : "#fff", cursor: "pointer" }}
                  onClick={() => setExpanded(isOpen ? null : run.id)}>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{run.payRunNumber}</span>
                      <span style={{ background: style.bg, color: style.color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{run.status}</span>
                      <span style={{ fontSize: 12, color: "#94A3B8" }}>Tax Year {run.taxYear}</span>
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B" }}>
                      Period: {run.periodStart} → {run.periodEnd} · Pay date: {run.payDate} · {run.employeeCount} employees
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
                    {run.status === "DRAFT" && (
                      <button
                        onClick={e => { e.stopPropagation(); processPayRun.mutate(run.id) }}
                        disabled={processPayRun.isPending}
                        style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", background: "#0D9488", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, cursor: "pointer" }}
                      >
                        <Play size={12} /> {processPayRun.isPending ? "Processing..." : "Process"}
                      </button>
                    )}
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{fmtR(run.totalNet)}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>net pay</div>
                    </div>
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {/* Summary row */}
                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "12px 20px", background: "#FAFAFA" }}>
                    <div style={{ display: "flex", gap: 20, marginBottom: 16, flexWrap: "wrap" }}>
                      {[
                        ["Gross",  run.totalGross, "#0F172A"],
                        ["PAYE",   run.totalPaye,  "#DC2626"],
                        ["UIF",    run.totalUif,   "#D97706"],
                        ["SDL",    run.totalSdl,   "#7C3AED"],
                        ["Net Pay",run.totalNet,   "#166534"],
                      ].map(([label, val, color]) => (
                        <div key={label as string} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "10px 16px", minWidth: 110 }}>
                          <div style={{ fontSize: 10, fontWeight: 600, color: "#94A3B8", marginBottom: 3 }}>{label as string}</div>
                          <div style={{ fontWeight: 700, fontSize: 15, color: color as string }}>{fmtR(val as number)}</div>
                        </div>
                      ))}
                    </div>

                    {/* Payslips */}
                    {payslips && payslips.length > 0 ? (
                      <div>
                        <div style={{ fontSize: 12, fontWeight: 600, color: "#64748B", marginBottom: 8 }}>PAYSLIPS</div>
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                          {payslips.map(slip => (
                            <div key={slip.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8 }}>
                              <div>
                                <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{slip.employeeName}</div>
                                <div style={{ fontSize: 11, color: "#94A3B8" }}>{slip.employeeNumber}</div>
                              </div>
                              <div style={{ display: "flex", gap: 20, alignItems: "center" }}>
                                <div style={{ textAlign: "right" }}>
                                  <div style={{ fontSize: 11, color: "#94A3B8" }}>Gross</div>
                                  <div style={{ fontSize: 13, fontWeight: 500 }}>{fmtR(slip.grossSalary)}</div>
                                </div>
                                <div style={{ textAlign: "right" }}>
                                  <div style={{ fontSize: 11, color: "#94A3B8" }}>PAYE</div>
                                  <div style={{ fontSize: 13, color: "#DC2626" }}>{fmtR(slip.payeAmount)}</div>
                                </div>
                                <div style={{ textAlign: "right" }}>
                                  <div style={{ fontSize: 11, color: "#94A3B8" }}>Net Pay</div>
                                  <div style={{ fontSize: 14, fontWeight: 700, color: "#166534" }}>{fmtR(slip.netPay)}</div>
                                </div>
                                <button
                                  onClick={() => downloadPayslip(slip.id, `${slip.employeeNumber}-${slip.payRunNumber}`)}
                                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 6, fontSize: 12, cursor: "pointer" }}
                                >
                                  <Download size={12} /> PDF
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : run.status === "DRAFT" ? (
                      <div style={{ fontSize: 13, color: "#94A3B8", textAlign: "center", padding: "12px 0" }}>
                        Process this pay run to generate payslips for all active employees.
                      </div>
                    ) : (
                      <div style={{ fontSize: 13, color: "#94A3B8" }}>No payslips found.</div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Create modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Pay Run</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <Field label="Period Start *"><input type="date" value={form.periodStart} onChange={e => setForm(f => ({ ...f, periodStart: e.target.value }))} style={inputStyle} /></Field>
              <Field label="Period End *"><input type="date" value={form.periodEnd} onChange={e => setForm(f => ({ ...f, periodEnd: e.target.value }))} style={inputStyle} /></Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Pay Date *"><input type="date" value={form.payDate} onChange={e => setForm(f => ({ ...f, payDate: e.target.value }))} style={inputStyle} /></Field>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Notes"><textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Optional notes..." style={{ ...inputStyle, resize: "vertical" as const }} /></Field>
              </div>
            </div>
            <div style={{ marginTop: 12, padding: "10px 14px", background: "#F0FDF4", borderRadius: 8, fontSize: 12, color: "#166534" }}>
              Processing will calculate PAYE, UIF and SDL for all active employees automatically.
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createPayRun.mutate(form)} disabled={createPayRun.isPending} style={btnPrimary}>
                {createPayRun.isPending ? "Creating..." : "Create Pay Run"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnCancel: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff" }
