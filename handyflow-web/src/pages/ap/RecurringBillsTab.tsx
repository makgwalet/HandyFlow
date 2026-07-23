// src/pages/ap/RecurringBillsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, RefreshCw, Pause, Play, Zap, Calendar, AlertTriangle,
} from "lucide-react"

interface Template {
  id: string; supplierId: string | null; supplierName: string
  category: string; description: string
  amount: number; vatAmount: number; totalAmount: number
  frequency: string; dayOfMonth: number; leadDays: number
  nextDueDate: string; lastGeneratedBillId: string | null; lastGeneratedAt: string | null
  active: boolean; notes: string | null; createdAt: string
}

const CATEGORIES = ["RENT","UTILITIES","FUEL","SALARY","PROFESSIONAL_FEES","EQUIPMENT","MAINTENANCE","INSURANCE","SUBSCRIPTIONS","MARKETING","OTHER"]
const CAT_LABELS: Record<string, string> = { RENT: "Rent", UTILITIES: "Utilities", FUEL: "Fuel", SALARY: "Salary", PROFESSIONAL_FEES: "Professional Fees", EQUIPMENT: "Equipment", MAINTENANCE: "Maintenance", INSURANCE: "Insurance", SUBSCRIPTIONS: "Subscriptions", MARKETING: "Marketing", OTHER: "Other" }

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 700, color: "#6B7280", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }
const btnP: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const btnS: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "9px 14px", border: "1.5px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151", fontWeight: 500 }

const fmtR    = (n: any) => n != null ? `R\u00A0${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: any) => d ? new Date(d + "T00:00:00").toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"

export function RecurringBillsTab({ onRefreshSummary }: { onRefreshSummary: () => void }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")

  const initForm = () => ({
    supplierName: "", category: "OTHER", description: "",
    amount: "", vatAmount: "0", dayOfMonth: "1", leadDays: "7", notes: "",
  })
  const [form, setForm] = useState(initForm())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["ap-recurring-templates"] })
    onRefreshSummary()
  }

  const { data: templates = [], isLoading } = useQuery<Template[]>({
    queryKey: ["ap-recurring-templates"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/ap/recurring-templates")
      return r.data?.data ?? r.data
    },
  })

  const createTemplate = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/ap/recurring-templates", body),
    onSuccess: () => { invalidate(); setShowCreate(false); setForm(initForm()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create template"),
  })

  const pauseTemplate = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/ap/recurring-templates/${id}/pause`),
    onSuccess: invalidate,
  })

  const resumeTemplate = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/ap/recurring-templates/${id}/resume`),
    onSuccess: invalidate,
  })

  const generateNow = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/ap/recurring-templates/${id}/generate-now`),
    onSuccess: (r: any) => {
      invalidate()
      const bill = r.data?.data ?? r.data
      setNotice(`Generated bill #${bill?.billNumber} for ${fmtR(bill?.totalAmount)} — check the Bills tab.`)
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to generate bill"),
  })

  const vatTotal = (amount: string) => (parseFloat(amount) || 0) + (parseFloat(form.vatAmount) || 0)

  return (
    <div>
      {notice && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
          padding: "12px 16px", background: "#F0FDF4", border: "1.5px solid #86EFAC", borderRadius: 10, marginBottom: 14 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <Zap size={16} color="#166534" />
            <span style={{ fontSize: 13, color: "#166534" }}>{notice}</span>
          </div>
          <button onClick={() => setNotice("")} style={{ background: "none", border: "none", cursor: "pointer", color: "#166534", display: "flex" }}><X size={14} /></button>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Recurring Bill Templates</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: "3px 0 0" }}>
            Auto-generates a DRAFT bill each month, a set number of days before it's due — still needs manual approval like any other bill
          </p>
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }} style={btnP}><Plus size={14} /> New Template</button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 48, color: "#94A3B8" }}>Loading templates...</div>
      ) : templates.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px" }}>
          <RefreshCw size={40} style={{ marginBottom: 12, color: "#CBD5E1" }} />
          <div style={{ fontWeight: 700, color: "#475569", fontSize: 15, marginBottom: 6 }}>No recurring templates yet</div>
          <div style={{ fontSize: 13, color: "#94A3B8", marginBottom: 16 }}>Rent, salaries, subscriptions — anything that bills the same amount on a schedule.</div>
          <button onClick={() => setShowCreate(true)} style={{ ...btnP, margin: "0 auto" }}><Plus size={14} /> Add first template</button>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" as const, fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Supplier / Description", "Category", "Amount", "Schedule", "Next Due", "Status", ""].map(h => (
                  <th key={h} style={{ padding: "10px 16px", textAlign: "left" as const, fontSize: 11, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {templates.map((t, i) => (
                <tr key={t.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ fontWeight: 700, color: "#0F172A" }}>{t.supplierName}</div>
                    <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 1 }}>{t.description}</div>
                  </td>
                  <td style={{ padding: "12px 16px", fontSize: 12, color: "#64748B" }}>{CAT_LABELS[t.category] ?? t.category}</td>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ fontWeight: 800, color: "#0F172A" }}>{fmtR(t.totalAmount)}</div>
                    {t.vatAmount > 0 && <div style={{ fontSize: 10, color: "#94A3B8" }}>excl. VAT {fmtR(t.amount)}</div>}
                  </td>
                  <td style={{ padding: "12px 16px", fontSize: 12, color: "#64748B" }}>
                    Day {t.dayOfMonth} monthly
                    <div style={{ fontSize: 10, color: "#94A3B8" }}>{t.leadDays}d lead time</div>
                  </td>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: "#374151", fontWeight: 600 }}>
                      <Calendar size={12} color="#94A3B8" />{fmtDate(t.nextDueDate)}
                    </div>
                    {t.lastGeneratedAt && (
                      <div style={{ fontSize: 10, color: "#94A3B8", marginTop: 2 }}>Last generated {fmtDT(t.lastGeneratedAt)}</div>
                    )}
                  </td>
                  <td style={{ padding: "12px 16px" }}>
                    <span style={{ display: "inline-flex", alignItems: "center", gap: 4,
                      background: t.active ? "#DCFCE7" : "#F1F5F9", color: t.active ? "#166534" : "#64748B",
                      border: `1px solid ${t.active ? "#86EFAC" : "#E2E8F0"}`, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                      <span style={{ width: 5, height: 5, borderRadius: "50%", background: t.active ? "#22C55E" : "#CBD5E1" }} />
                      {t.active ? "Active" : "Paused"}
                    </span>
                  </td>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                      <button onClick={() => generateNow.mutate(t.id)} disabled={generateNow.isPending}
                        title="Generate a bill from this template right now"
                        style={{ padding: "5px 9px", background: "#F5F3FF", color: "#7C3AED", border: "1px solid #DDD6FE", borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", gap: 3 }}>
                        <Zap size={10} /> Generate now
                      </button>
                      {t.active ? (
                        <button onClick={() => pauseTemplate.mutate(t.id)} title="Pause"
                          style={{ padding: "5px 8px", background: "#F8FAFC", color: "#64748B", border: "1px solid #E2E8F0", borderRadius: 6, cursor: "pointer", display: "flex" }}>
                          <Pause size={11} />
                        </button>
                      ) : (
                        <button onClick={() => resumeTemplate.mutate(t.id)} title="Resume"
                          style={{ padding: "5px 8px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 6, cursor: "pointer", display: "flex" }}>
                          <Play size={11} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, padding: 20, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 620, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 25px 80px rgba(0,0,0,0.25)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>New Recurring Bill Template</h3>
              <button onClick={() => { setShowCreate(false); setError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Supplier name *</label>
                <input autoFocus value={form.supplierName} onChange={e => f("supplierName", e.target.value)} placeholder="Growthpoint Properties, Adcorp..." style={inp} />
              </div>
              <div>
                <label style={lbl}>Category *</label>
                <select value={form.category} onChange={e => f("category", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {CATEGORIES.map(c => <option key={c} value={c}>{CAT_LABELS[c]}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Day of month (1–28) *</label>
                <input type="number" min="1" max="28" value={form.dayOfMonth} onChange={e => f("dayOfMonth", e.target.value)} style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description *</label>
                <input value={form.description} onChange={e => f("description", e.target.value)} placeholder="Monthly office rent — Sandton, floor 3" style={inp} />
              </div>
              <div>
                <label style={lbl}>Amount excl. VAT (R) *</label>
                <input type="number" min="0.01" step="0.01" value={form.amount} onChange={e => f("amount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div>
                <label style={lbl}>VAT amount (R)</label>
                <input type="number" min="0" step="0.01" value={form.vatAmount} onChange={e => f("vatAmount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              {form.amount && (
                <div style={{ gridColumn: "1/-1", padding: "10px 14px", background: "#F0F9FF", borderRadius: 8, border: "1px solid #BAE6FD", fontSize: 13 }}>
                  <strong>Total incl. VAT: {fmtR(vatTotal(form.amount))}</strong>
                </div>
              )}
              <div>
                <label style={lbl}>Lead time (days before due)</label>
                <input type="number" min="0" value={form.leadDays} onChange={e => f("leadDays", e.target.value)} style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Notes</label>
                <textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2} placeholder="Contract reference, lease terms..." style={{ ...inp, resize: "vertical" as const, fontFamily: "inherit" }} />
              </div>
            </div>
            <div style={{ marginTop: 14, padding: "10px 14px", background: "#FFFBEB", borderRadius: 8, border: "1px solid #FDE68A", fontSize: 12, color: "#92400E", display: "flex", alignItems: "flex-start", gap: 8 }}>
              <AlertTriangle size={13} style={{ marginTop: 1, flexShrink: 0 }} />
              Every generated bill still lands as a DRAFT needing manual approval — this only saves the re-typing, not the review.
            </div>
            {error && <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 22 }}>
              <button onClick={() => { setShowCreate(false); setError("") }} style={btnS}>Cancel</button>
              <button
                disabled={!form.supplierName || !form.description || !form.amount || !form.dayOfMonth || createTemplate.isPending}
                onClick={() => createTemplate.mutate({
                  supplierName: form.supplierName, category: form.category, description: form.description,
                  amount: parseFloat(form.amount), vatAmount: parseFloat(form.vatAmount) || 0,
                  dayOfMonth: parseInt(form.dayOfMonth), leadDays: parseInt(form.leadDays) || 7,
                  notes: form.notes || null,
                })}
                style={{ ...btnP, opacity: (!form.supplierName || !form.description || !form.amount) ? 0.5 : 1 }}>
                {createTemplate.isPending ? "Saving..." : "Create template"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
