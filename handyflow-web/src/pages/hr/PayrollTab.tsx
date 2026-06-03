// src/pages/hr/PayrollTab.tsx
// KEY FIXES:
// 1. API unwrap — was page?.content and r.data (no wrapper), needs r.data?.data?.content
// 2. payslips unwrap fixed — was r.data, needs unwrapList
// 3. STATUS_STYLE had PROCESSED/PAID — backend returns DRAFT/PROCESSING/COMPLETED
// 4. window.__AUTH_TOKEN__ removed — apiClient handles auth automatically
// 5. Payslip query now properly scoped to expanded pay run ID
// 6. Variance vs prior month added
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Play, ChevronDown, ChevronUp, Download, DollarSign, AlertTriangle, AlertCircle } from "lucide-react"

// FIX: correct ApiResponse<Page<T>> unwrap
const unwrap     = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

// FIX: status values match what the backend actually returns
const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  DRAFT:      { color: "#D97706", bg: "#FFFBEB", label: "Draft"      },
  PROCESSING: { color: "#1D4ED8", bg: "#EFF6FF", label: "Processing" },
  COMPLETED:  { color: "#166534", bg: "#DCFCE7", label: "Completed"  },
  CANCELLED:  { color: "#94A3B8", bg: "#F8FAFC", label: "Cancelled"  },
}

export default function PayrollTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [error, setError]           = useState("")

  const today = new Date()
  const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().split("T")[0]
  const lastOfMonth  = new Date(today.getFullYear(), today.getMonth() + 1, 0).toISOString().split("T")[0]
  const [form, setForm] = useState({ periodStart: firstOfMonth, periodEnd: lastOfMonth, payDate: lastOfMonth, notes: "" })

  // FIX: unwrap ApiResponse<Page<PayRun>>
  const { data: payRuns = [], isLoading } = useQuery<any[]>({
    queryKey: ["pay-runs"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/hr/pay-runs?size=50&sort=periodStart,desc")),
  })

  // FIX: unwrap ApiResponse<List<Payslip>> — was r.data (no wrapper)
  const { data: payslips = [] } = useQuery<any[]>({
    queryKey: ["payslips", expanded],
    queryFn: async () => expanded ? unwrapList(await apiClient.get(`/api/v1/hr/pay-runs/${expanded}/payslips`)) : [],
    enabled: !!expanded,
  })

  const createPayRun = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/hr/pay-runs", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["pay-runs"] }); setShowCreate(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create pay run"),
  })

  const processPayRun = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/hr/pay-runs/${id}/process`),
    onSuccess: (_, id) => { qc.invalidateQueries({ queryKey: ["pay-runs"] }); qc.invalidateQueries({ queryKey: ["payslips", id] }) },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to process pay run"),
  })

  // FIX: no window.__AUTH_TOKEN__ — apiClient handles auth headers automatically
  const downloadPayslip = async (id: string, empNumber: string, runNumber: string) => {
    try {
      const res = await apiClient.get(`/api/v1/hr/payslips/${id}/pdf`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
      const a = document.createElement("a"); a.href = url; a.download = `payslip-${empNumber}-${runNumber}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert("Failed to download payslip PDF") }
  }

  const sorted = (payRuns as any[]).slice()

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      <div style={{ marginBottom: 18, padding: "10px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 12, color: "#1D4ED8" }}>
        <strong>SA Tax Year:</strong> March to February. Processing calculates PAYE (with MTC), UIF (1% capped at R17,712/month), and SDL (1% if payroll &gt; R500,000/year). Travel allowance: 80% taxable.
      </div>

      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 20 }}>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> New Pay Run
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading pay runs...</div>
      ) : sorted.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <DollarSign size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No pay runs yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Create your first pay run to calculate PAYE, UIF, and SDL.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {sorted.map((run: any, idx: number) => {
            const cfg    = STATUS_CFG[run.status] ?? STATUS_CFG.DRAFT
            const isOpen = expanded === run.id
            const prev   = sorted[idx + 1]
            const netVar = (prev?.status === "COMPLETED" && run.status === "COMPLETED" && run.totalNet && prev.totalNet)
              ? Number(run.totalNet) - Number(prev.totalNet) : null

            return (
              <div key={run.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "16px 20px", background: isOpen ? "#F8FAFC" : "#fff", cursor: "pointer" }}
                  onClick={() => setExpanded(isOpen ? null : run.id)}>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
                      <span style={{ fontWeight: 800, fontSize: 16, color: "#0F172A" }}>{run.payRunNumber}</span>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                      <span style={{ fontSize: 12, color: "#94A3B8" }}>Tax Year {run.taxYear}/{Number(run.taxYear)+1}</span>
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B" }}>
                      {fmtDate(run.periodStart)} → {fmtDate(run.periodEnd)} · Pay date: {fmtDate(run.payDate)}
                      {run.employeeCount > 0 && ` · ${run.employeeCount} employees`}
                    </div>
                    {run.status === "COMPLETED" && (
                      <div style={{ display: "flex", gap: 14, marginTop: 10, flexWrap: "wrap" }}>
                        {[
                          { l: "Gross",   v: fmtR(run.totalGross), c: "#0F172A" },
                          { l: "PAYE",    v: fmtR(run.totalPaye),  c: "#DC2626" },
                          { l: "UIF",     v: fmtR(run.totalUif),   c: "#D97706" },
                          { l: "SDL",     v: fmtR(run.totalSdl),   c: "#7C3AED" },
                          { l: "Net Pay", v: fmtR(run.totalNet),   c: "#166534", bold: true },
                        ].map(s => (
                          <div key={s.l} style={{ textAlign: "center" as const }}>
                            <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, marginBottom: 2 }}>{s.l}</div>
                            <div style={{ fontSize: 13, fontWeight: s.bold ? 800 : 600, color: s.c }}>{s.v}</div>
                          </div>
                        ))}
                        {netVar !== null && (
                          <div style={{ textAlign: "center" as const }}>
                            <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, marginBottom: 2 }}>vs Prior</div>
                            <div style={{ fontSize: 13, fontWeight: 700, color: netVar >= 0 ? "#166534" : "#DC2626" }}>
                              {netVar >= 0 ? "+" : ""}{fmtR(netVar)}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0, marginLeft: 14 }}>
                    {run.status === "DRAFT" && (
                      <button onClick={e => { e.stopPropagation(); processPayRun.mutate(run.id) }} disabled={processPayRun.isPending}
                        style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                        <Play size={12} />{processPayRun.isPending ? "Processing..." : "Process"}
                      </button>
                    )}
                    {run.status !== "DRAFT" && (
                      <div style={{ textAlign: "right" as const }}>
                        <div style={{ fontWeight: 700, fontSize: 15, color: "#166534" }}>{fmtR(run.totalNet)}</div>
                        <div style={{ fontSize: 11, color: "#94A3B8" }}>net pay</div>
                      </div>
                    )}
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {run.status === "DRAFT" && (
                  <div style={{ padding: "8px 20px 12px", background: "#FFFBEB", borderTop: "1px solid #FDE68A" }}>
                    <div style={{ fontSize: 12, color: "#92400E", display: "flex", alignItems: "center", gap: 6 }}>
                      <AlertTriangle size={12} /> Click Process to calculate PAYE, UIF, and SDL for all active employees.
                    </div>
                  </div>
                )}

                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "14px 20px", background: "#FAFAFA" }}>
                    {(payslips as any[]).length > 0 ? (
                      <div>
                        <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Payslips ({payslips.length} employees)</div>
                        <div style={{ border: "1px solid #E2E8F0", borderRadius: 9, overflow: "hidden" }}>
                          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                            <thead>
                              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                                {["Employee","Gross","PAYE","UIF","Deductions","Net Pay","YTD Gross",""].map(h => (
                                  <th key={h} style={{ padding: "9px 12px", textAlign: "left" as const, fontSize: 10, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {(payslips as any[]).map((slip: any, i) => (
                                <tr key={slip.id} style={{ borderBottom: i < payslips.length - 1 ? "1px solid #F1F5F9" : "none" }}>
                                  <td style={{ padding: "10px 12px" }}>
                                    <div style={{ fontWeight: 600 }}>{slip.employeeName}</div>
                                    <div style={{ fontSize: 11, color: "#94A3B8" }}>{slip.employeeNumber}</div>
                                  </td>
                                  <td style={{ padding: "10px 12px", color: "#0D9488", fontWeight: 600 }}>{fmtR(slip.grossSalary)}</td>
                                  <td style={{ padding: "10px 12px", color: "#DC2626" }}>{fmtR(slip.payeAmount)}</td>
                                  <td style={{ padding: "10px 12px", color: "#D97706" }}>{fmtR(slip.uifEmployee)}</td>
                                  <td style={{ padding: "10px 12px", color: "#64748B" }}>{fmtR(slip.totalDeductions)}</td>
                                  <td style={{ padding: "10px 12px", fontWeight: 800, color: "#166534" }}>{fmtR(slip.netPay)}</td>
                                  <td style={{ padding: "10px 12px", color: "#475569" }}>{fmtR(slip.ytdGross)}</td>
                                  <td style={{ padding: "10px 12px" }}>
                                    <button onClick={() => downloadPayslip(slip.id, slip.employeeNumber, run.payRunNumber)}
                                      style={{ display: "flex", alignItems: "center", gap: 4, background: "#EFF6FF", color: "#1D4ED8", border: "none", borderRadius: 6, padding: "5px 10px", fontSize: 12, cursor: "pointer" }}>
                                      <Download size={11} /> PDF
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                        {/* Totals */}
                        <div style={{ marginTop: 10, display: "flex", gap: 16, padding: "11px 14px", background: "#1B3A6B", borderRadius: 8, color: "#fff", flexWrap: "wrap" }}>
                          {[
                            { l: "Total gross", v: fmtR((payslips as any[]).reduce((s, p) => s + Number(p.grossSalary ?? 0), 0)) },
                            { l: "Total PAYE",  v: fmtR((payslips as any[]).reduce((s, p) => s + Number(p.payeAmount ?? 0), 0)) },
                            { l: "Total UIF",   v: fmtR((payslips as any[]).reduce((s, p) => s + Number(p.uifEmployee ?? 0), 0)) },
                            { l: "Total net",   v: fmtR((payslips as any[]).reduce((s, p) => s + Number(p.netPay ?? 0), 0)) },
                          ].map(s => (
                            <div key={s.l}>
                              <div style={{ fontSize: 10, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 1 }}>{s.l}</div>
                              <div style={{ fontSize: 13, fontWeight: 700 }}>{s.v}</div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : run.status === "DRAFT" ? (
                      <div style={{ fontSize: 13, color: "#94A3B8", textAlign: "center" as const, padding: "12px 0" }}>Process this pay run to generate payslips.</div>
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
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Pay Run</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Period Start *</label>
                  <input type="date" value={form.periodStart}
                    onChange={e => {
                      const s = e.target.value
                      const d = new Date(s); d.setMonth(d.getMonth() + 1); d.setDate(0)
                      const end = d.toISOString().split("T")[0]
                      setForm(f => ({ ...f, periodStart: s, periodEnd: end, payDate: end }))
                    }} style={inp} />
                </div>
                <div><label style={lbl}>Period End *</label><input type="date" value={form.periodEnd} onChange={e => setForm(f => ({ ...f, periodEnd: e.target.value }))} style={inp} /></div>
              </div>
              <div>
                <label style={lbl}>Pay Date *</label>
                <input type="date" value={form.payDate} onChange={e => setForm(f => ({ ...f, payDate: e.target.value }))} style={inp} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Date salary hits employee bank accounts</div>
              </div>
              <div>
                <label style={lbl}>Notes</label>
                <input value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} placeholder="e.g. June 2026 monthly payroll" style={inp} />
              </div>
              <div style={{ padding: "10px 14px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 8, fontSize: 12, color: "#166534" }}>
                After creating, click Process to calculate PAYE, UIF, and SDL for all active employees. Pay runs can be re-processed before finalising.
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "9px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 7 }}><AlertCircle size={13} />{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={() => createPayRun.mutate(form)} disabled={!form.periodStart || !form.periodEnd || !form.payDate || createPayRun.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createPayRun.isPending ? "Creating..." : "Create Pay Run"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
