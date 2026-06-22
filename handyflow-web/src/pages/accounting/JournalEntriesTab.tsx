// src/pages/accounting/JournalEntriesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, ChevronDown, ChevronUp, CheckCircle, RotateCcw, AlertCircle, X, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"

interface Account { id: string; accountCode: string; accountName: string; accountType: string }
interface JournalLine { id: string; accountId: string; accountCode: string; accountName: string
  description: string; debitAmount: number; creditAmount: number }
interface Journal { id: string; entryNumber: string; entryDate: string; description: string
  reference: string; entryType: string; status: string; totalDebit: number; totalCredit: number
  balanced: boolean; lines: JournalLine[]; createdAt: string }

const STATUS: Record<string, { label: string; bg: string; color: string }> = {
  DRAFT:    { label: "Draft",    bg: "#F1F5F9", color: "#475569" },
  POSTED:   { label: "Posted",  bg: "#F0FDF4", color: "#166534" },
  REVERSED: { label: "Reversed",bg: "#FEF3C7", color: "#92400E" },
}

const fmtR = (n: number) => n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDate = (d: string) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"
const inp: React.CSSProperties = {
  width: "100%", padding: "8px 12px", border: "1.5px solid #E2E8F0",
  borderRadius: 8, fontSize: 13, outline: "none", boxSizing: "border-box",
}

interface DraftLine { tempId: string; accountId: string; description: string; debit: string; credit: string }

export default function JournalEntriesTab() {
  const qc = useQueryClient()
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [statusFilter, setStatusFilter] = useState("ALL")
  const [reverseTarget, setReverseTarget] = useState<Journal | null>(null)
  const [reversalDate, setReversalDate] = useState(new Date().toISOString().split("T")[0])
  const [error, setError] = useState("")

  // Form state
  const [form, setForm] = useState({
    entryDate: new Date().toISOString().split("T")[0],
    description: "", reference: "", entryType: "MANUAL",
  })
  const [lines, setLines] = useState<DraftLine[]>([
    { tempId: "1", accountId: "", description: "", debit: "", credit: "" },
    { tempId: "2", accountId: "", description: "", debit: "", credit: "" },
  ])

  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ["coa"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/accounts")
      return (res.data?.data ?? res.data) as Account[]
    },
  })

  const { data, isLoading } = useQuery({
    queryKey: ["journals", statusFilter],
    queryFn: async () => {
      const qs = statusFilter !== "ALL" ? `&status=${statusFilter}` : ""
      const res = await apiClient.get(`/api/v1/accounting/journal-entries?size=50&sort=entryDate,desc${qs}`)
      const payload = res.data?.data ?? res.data
      return payload as { content: Journal[]; totalElements: number }
    },
  })

  const journals = data?.content ?? []

  const totalDebit  = lines.reduce((s, l) => s + (parseFloat(l.debit)  || 0), 0)
  const totalCredit = lines.reduce((s, l) => s + (parseFloat(l.credit) || 0), 0)
  const balanced    = Math.abs(totalDebit - totalCredit) < 0.01 && totalDebit > 0

  const create = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/journal-entries", {
      entryDate: form.entryDate, description: form.description,
      reference: form.reference || undefined, entryType: form.entryType,
      lines: lines.filter(l => l.accountId).map(l => ({
        accountId: l.accountId, description: l.description || undefined,
        debitAmount:  parseFloat(l.debit)  || 0,
        creditAmount: parseFloat(l.credit) || 0,
      })),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["journals"] })
      setShowCreate(false)
      setError("")
      setForm({ entryDate: new Date().toISOString().split("T")[0], description: "", reference: "", entryType: "MANUAL" })
      setLines([
        { tempId: "1", accountId: "", description: "", debit: "", credit: "" },
        { tempId: "2", accountId: "", description: "", debit: "", credit: "" },
      ])
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create journal entry"),
  })

  const post = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accounting/journal-entries/${id}/post`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["journals"] }),
  })

  const reverse = useMutation({
    mutationFn: ({ id, date }: { id: string; date: string }) =>
      apiClient.post(`/api/v1/accounting/journal-entries/${id}/reverse`, { reversalDate: date }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["journals"] })
      setReverseTarget(null)
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to reverse entry"),
  })

  const addLine = () =>
    setLines(l => [...l, { tempId: Date.now().toString(), accountId: "", description: "", debit: "", credit: "" }])
  const removeLine = (id: string) => setLines(l => l.filter(x => x.tempId !== id))
  const updateLine = (id: string, field: keyof DraftLine, val: string) =>
    setLines(l => l.map(x => x.tempId === id ? { ...x, [field]: val } : x))

  return (
    <div>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Journal Entries</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: "3px 0 0" }}>
            Double-entry bookkeeping — every debit must equal every credit
          </p>
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "white",
            border: "none", borderRadius: 9, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Journal Entry
        </button>
      </div>

      {/* Status filter */}
      <div style={{ display: "flex", gap: 6, marginBottom: 16 }}>
        {["ALL", "DRAFT", "POSTED", "REVERSED"].map(s => (
          <button key={s} onClick={() => setStatusFilter(s)}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, fontWeight: 600,
              cursor: "pointer", border: "none",
              background: statusFilter === s ? "#1B3A6B" : "#F1F5F9",
              color:      statusFilter === s ? "white"   : "#64748B" }}>
            {s === "ALL" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {/* Table */}
      <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
        {isLoading ? (
          <div style={{ padding: 60, textAlign: "center", color: "#94A3B8" }}>Loading journal entries...</div>
        ) : journals.length === 0 ? (
          <div style={{ padding: 60, textAlign: "center", color: "#94A3B8" }}>
            No journal entries yet — create your first entry above.
          </div>
        ) : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #F1F5F9" }}>
                {["Journal #", "Date", "Description", "Debit", "Credit", "Status", "Actions"].map(h => (
                  <th key={h} style={{ textAlign: "left", padding: "10px 14px", fontSize: 11,
                    fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {journals.map(j => {
                const ss = STATUS[j.status] ?? STATUS.DRAFT
                const isExp = expanded === j.id
                return (
                  <>
                    <tr key={j.id}
                      onClick={() => setExpanded(isExp ? null : j.id)}
                      style={{ borderBottom: "1px solid #F8FAFC", cursor: "pointer",
                        background: isExp ? "#FAFBFF" : "white" }}
                      onMouseEnter={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = "#F8FAFC" }}
                      onMouseLeave={e => { if (!isExp) (e.currentTarget as HTMLElement).style.background = "white" }}>
                      <td style={{ padding: "12px 14px" }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                          <span style={{ fontFamily: "monospace", fontSize: 13, fontWeight: 700, color: "#1B3A6B" }}>{j.entryNumber}</span>
                          {isExp ? <ChevronUp size={12} color="#94A3B8" /> : <ChevronDown size={12} color="#94A3B8" />}
                        </div>
                        <span style={{ fontSize: 10, color: "#94A3B8" }}>{j.entryType}</span>
                      </td>
                      <td style={{ padding: "12px 14px", fontSize: 13, color: "#64748B" }}>{fmtDate(j.entryDate)}</td>
                      <td style={{ padding: "12px 14px" }}>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{j.description}</div>
                        {j.reference && <div style={{ fontSize: 11, color: "#94A3B8" }}>{j.reference}</div>}
                      </td>
                      <td style={{ padding: "12px 14px", fontSize: 13, fontWeight: 600, color: "#1B3A6B" }}>{fmtR(j.totalDebit)}</td>
                      <td style={{ padding: "12px 14px", fontSize: 13, fontWeight: 600, color: "#0D9488" }}>{fmtR(j.totalCredit)}</td>
                      <td style={{ padding: "12px 14px" }}>
                        <span style={{ background: ss.bg, color: ss.color, fontSize: 11,
                          fontWeight: 700, padding: "3px 8px", borderRadius: 10 }}>{ss.label}</span>
                        {!j.balanced && <span style={{ fontSize: 10, color: "#DC2626", marginLeft: 6 }}>UNBALANCED</span>}
                      </td>
                      <td style={{ padding: "12px 14px" }}>
                        <div style={{ display: "flex", gap: 5 }} onClick={e => e.stopPropagation()}>
                          {j.status === "DRAFT" && (
                            <button onClick={() => post.mutate(j.id)} disabled={post.isPending}
                              style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px",
                                background: "#F0FDF4", color: "#166534", border: "1px solid #BBF7D0",
                                borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                              <CheckCircle size={11} /> Post
                            </button>
                          )}
                          {j.status === "POSTED" && (
                            <button onClick={() => { setReverseTarget(j); setError("") }}
                              style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px",
                                background: "#FEF3C7", color: "#92400E", border: "1px solid #FCD34D",
                                borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                              <RotateCcw size={11} /> Reverse
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>

                    {isExp && (
                      <tr key={`${j.id}-exp`}>
                        <td colSpan={7} style={{ padding: 0 }}>
                          <div style={{ background: "#F8FAFC", padding: "14px 24px", borderBottom: "1px solid #F1F5F9" }}>
                            <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", marginBottom: 10, letterSpacing: "0.06em" }}>JOURNAL LINES</div>
                            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                              <thead>
                                <tr style={{ borderBottom: "1px solid #E2E8F0" }}>
                                  {["Account", "Description", "Debit", "Credit"].map(h => (
                                    <th key={h} style={{ textAlign: "left", padding: "6px 10px", fontSize: 11,
                                      fontWeight: 600, color: "#64748B", textTransform: "uppercase" }}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {j.lines.map((l, i) => (
                                  <tr key={l.id} style={{ borderBottom: i < j.lines.length - 1 ? "1px solid #F1F5F9" : "none" }}>
                                    <td style={{ padding: "7px 10px" }}>
                                      <span style={{ fontFamily: "monospace", fontSize: 12, fontWeight: 600, color: "#1B3A6B" }}>{l.accountCode}</span>
                                      <span style={{ fontSize: 12, color: "#374151", marginLeft: 8 }}>{l.accountName}</span>
                                    </td>
                                    <td style={{ padding: "7px 10px", fontSize: 12, color: "#64748B" }}>{l.description || "—"}</td>
                                    <td style={{ padding: "7px 10px", fontSize: 13, fontWeight: l.debitAmount > 0 ? 600 : 400,
                                      color: l.debitAmount > 0 ? "#1B3A6B" : "#94A3B8" }}>
                                      {l.debitAmount > 0 ? fmtR(l.debitAmount) : "—"}
                                    </td>
                                    <td style={{ padding: "7px 10px", fontSize: 13, fontWeight: l.creditAmount > 0 ? 600 : 400,
                                      color: l.creditAmount > 0 ? "#0D9488" : "#94A3B8" }}>
                                      {l.creditAmount > 0 ? fmtR(l.creditAmount) : "—"}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                              <tfoot>
                                <tr style={{ borderTop: "2px solid #E2E8F0" }}>
                                  <td colSpan={2} style={{ padding: "8px 10px", fontSize: 12, fontWeight: 700, color: "#0F172A" }}>Totals</td>
                                  <td style={{ padding: "8px 10px", fontSize: 13, fontWeight: 700, color: "#1B3A6B" }}>{fmtR(j.totalDebit)}</td>
                                  <td style={{ padding: "8px 10px", fontSize: 13, fontWeight: 700, color: "#0D9488" }}>{fmtR(j.totalCredit)}</td>
                                </tr>
                              </tfoot>
                            </table>
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Create Journal Modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "white", borderRadius: 16, padding: 28, width: 700, maxHeight: "90vh",
            overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>New Journal Entry</h3>
              <button onClick={() => setShowCreate(false)}
                style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                <X size={20} />
              </button>
            </div>

            {/* Header fields */}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 20 }}>
              {[
                { label: "Date *", key: "entryDate", type: "date" },
                { label: "Reference", key: "reference", type: "text" },
                { label: "Type", key: "entryType", type: "select" },
              ].map(f => (
                <div key={f.key}>
                  <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>{f.label}</label>
                  {f.type === "select" ? (
                    <select value={(form as any)[f.key]}
                      onChange={e => setForm(p => ({ ...p, [f.key]: e.target.value }))}
                      style={{ ...inp }}>
                      {["MANUAL","INVOICE","PAYMENT","BANK","DEPRECIATION","ADJUSTMENT","VAT"].map(t =>
                        <option key={t}>{t}</option>)}
                    </select>
                  ) : (
                    <input type={f.type} value={(form as any)[f.key]}
                      onChange={e => setForm(p => ({ ...p, [f.key]: e.target.value }))}
                      style={inp} />
                  )}
                </div>
              ))}
            </div>
            <div style={{ marginBottom: 20 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Description *</label>
              <input value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
                placeholder="e.g. Cash sale — Black Flamingo" style={inp} />
            </div>

            {/* Lines */}
            <div style={{ marginBottom: 12 }}>
              <div style={{ display: "grid", gridTemplateColumns: "3fr 2fr 1fr 1fr 32px", gap: 8, marginBottom: 6 }}>
                {["Account", "Description", "Debit (R)", "Credit (R)", ""].map(h => (
                  <div key={h} style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.04em" }}>{h}</div>
                ))}
              </div>
              {lines.map(l => (
                <div key={l.tempId} style={{ display: "grid", gridTemplateColumns: "3fr 2fr 1fr 1fr 32px", gap: 8, marginBottom: 8, alignItems: "center" }}>
                  <select value={l.accountId} onChange={e => updateLine(l.tempId, "accountId", e.target.value)}
                    style={{ ...inp, fontSize: 12 }}>
                    <option value="">Select account...</option>
                    {accounts.map(a => (
                      <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>
                    ))}
                  </select>
                  <input value={l.description} onChange={e => updateLine(l.tempId, "description", e.target.value)}
                    placeholder="Optional note" style={{ ...inp, fontSize: 12 }} />
                  <input type="number" value={l.debit} onChange={e => updateLine(l.tempId, "debit", e.target.value)}
                    placeholder="0.00" style={{ ...inp, fontSize: 12 }} />
                  <input type="number" value={l.credit} onChange={e => updateLine(l.tempId, "credit", e.target.value)}
                    placeholder="0.00" style={{ ...inp, fontSize: 12 }} />
                  <button onClick={() => removeLine(l.tempId)} disabled={lines.length <= 2}
                    style={{ background: "none", border: "none", cursor: lines.length <= 2 ? "not-allowed" : "pointer",
                      color: lines.length <= 2 ? "#CBD5E1" : "#FDA4AF", padding: 4, borderRadius: 6, display: "flex" }}>
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
              <button onClick={addLine}
                style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: "#1B3A6B",
                  background: "none", border: "1px dashed #BFDBFE", borderRadius: 7, padding: "6px 12px", cursor: "pointer" }}>
                <Plus size={12} /> Add line
              </button>
            </div>

            {/* Totals summary */}
            <div style={{ background: balanced ? "#F0FDF4" : "#FEF2F2", borderRadius: 8,
              padding: "10px 14px", marginBottom: 14, fontSize: 13 }}>
              <span style={{ color: "#64748B" }}>Total Debit: </span>
              <strong style={{ color: "#1B3A6B", marginRight: 20 }}>{fmtR(totalDebit)}</strong>
              <span style={{ color: "#64748B" }}>Total Credit: </span>
              <strong style={{ color: "#0D9488", marginRight: 20 }}>{fmtR(totalCredit)}</strong>
              <strong style={{ color: balanced ? "#166534" : "#DC2626" }}>
                {balanced ? "✓ Balanced" : `✗ Off by R${Math.abs(totalDebit - totalCredit).toFixed(2)}`}
              </strong>
            </div>

            {error && (
              <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 12px",
                background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8,
                fontSize: 13, color: "#DC2626", marginBottom: 14 }}>
                <AlertCircle size={14} />{error}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setShowCreate(false)}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9,
                  background: "white", fontSize: 13, cursor: "pointer", color: "#374151" }}>
                Cancel
              </button>
              <button disabled={create.isPending || !balanced || !form.description}
                onClick={() => create.mutate()}
                style={{ padding: "9px 20px", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700,
                  background: balanced && form.description ? "#1B3A6B" : "#E2E8F0",
                  color: balanced && form.description ? "white" : "#94A3B8",
                  cursor: balanced && form.description ? "pointer" : "not-allowed" }}>
                {create.isPending ? "Creating..." : "Create Journal Entry"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reverse Modal */}
      {reverseTarget && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1001, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "white", borderRadius: 16, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700 }}>Reverse Journal Entry</h3>
            <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 20px" }}>
              Creates an equal-and-opposite posted journal entry. The original will be marked REVERSED.
            </p>
            <div style={{ background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8,
              padding: "10px 14px", marginBottom: 20, fontSize: 13, color: "#92400E" }}>
              Reversing: <strong>{reverseTarget.entryNumber}</strong> — {reverseTarget.description}
            </div>
            <div style={{ marginBottom: 20 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Reversal Date</label>
              <input type="date" value={reversalDate} onChange={e => setReversalDate(e.target.value)} style={inp} />
            </div>
            {error && (
              <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA",
                borderRadius: 8, fontSize: 12, color: "#DC2626", marginBottom: 12 }}>{error}</div>
            )}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => { setReverseTarget(null); setError("") }}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "white", fontSize: 13, cursor: "pointer", color: "#374151" }}>
                Cancel
              </button>
              <button disabled={reverse.isPending}
                onClick={() => reverse.mutate({ id: reverseTarget.id, date: reversalDate })}
                style={{ padding: "9px 20px", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700,
                  background: "#D97706", color: "white", cursor: "pointer" }}>
                {reverse.isPending ? "Reversing..." : "Confirm Reversal"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
