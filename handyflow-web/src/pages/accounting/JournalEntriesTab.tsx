import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ChevronDown, ChevronUp, AlertCircle } from "lucide-react"

interface JournalLine {
  id: string
  accountId: string
  accountCode: string
  accountName: string
  description: string
  debitAmount: number
  creditAmount: number
}

interface JournalEntry {
  id: string
  entryNumber: string
  entryDate: string
  description: string
  reference: string
  entryType: string
  status: string
  totalDebit: number
  totalCredit: number
  balanced: boolean
  lines: JournalLine[]
  createdAt: string
}

interface Account {
  id: string
  accountCode: string
  accountName: string
  accountType: string
}

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  DRAFT:  { color: "#D97706", bg: "#FFFBEB" },
  POSTED: { color: "#166534", bg: "#DCFCE7" },
}

export default function JournalEntriesTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [statusFilter, setStatus]   = useState("")
  const [error, setError]           = useState("")

  const [form, setForm] = useState({
    entryDate: new Date().toISOString().split("T")[0],
    description: "",
    reference: "",
    entryType: "MANUAL",
    lines: [
      { accountId: "", description: "", debitAmount: "", creditAmount: "" },
      { accountId: "", description: "", debitAmount: "", creditAmount: "" },
    ],
  })

  const { data: entriesPage, isLoading } = useQuery({
    queryKey: ["journal-entries", statusFilter],
    queryFn: async () => {
      const params = statusFilter ? `?status=${statusFilter}&size=50` : "?size=50"
      const res = await apiClient.get(`/api/v1/accounting/journal-entries${params}`)
      return res.data
    },
  })

  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ["acc-accounts"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/accounts")
      return res.data
    },
  })

  const entries: JournalEntry[] = entriesPage?.content || []

  const createEntry = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/accounting/journal-entries", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["journal-entries"] })
      setShowCreate(false)
      resetForm()
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create journal entry"),
  })

  const postEntry = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accounting/journal-entries/${id}/post`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["journal-entries"] }),
  })

  const resetForm = () => {
    setForm({
      entryDate: new Date().toISOString().split("T")[0],
      description: "", reference: "", entryType: "MANUAL",
      lines: [
        { accountId: "", description: "", debitAmount: "", creditAmount: "" },
        { accountId: "", description: "", debitAmount: "", creditAmount: "" },
      ],
    })
    setError("")
  }

  const addLine = () => setForm(f => ({ ...f, lines: [...f.lines, { accountId: "", description: "", debitAmount: "", creditAmount: "" }] }))
  const removeLine = (i: number) => setForm(f => ({ ...f, lines: f.lines.filter((_, idx) => idx !== i) }))
  const updateLine = (i: number, field: string, value: string) => {
    setForm(f => ({ ...f, lines: f.lines.map((l, idx) => idx === i ? { ...l, [field]: value } : l) }))
  }

  const totalDebit  = form.lines.reduce((s, l) => s + (parseFloat(l.debitAmount)  || 0), 0)
  const totalCredit = form.lines.reduce((s, l) => s + (parseFloat(l.creditAmount) || 0), 0)
  const balanced    = Math.abs(totalDebit - totalCredit) < 0.01 && totalDebit > 0

  const handleSubmit = () => {
    if (!form.description) { setError("Description is required"); return }
    if (!balanced) { setError("Journal entry must balance: debits must equal credits"); return }
    createEntry.mutate({
      entryDate: form.entryDate,
      description: form.description,
      reference: form.reference || null,
      entryType: form.entryType,
      lines: form.lines
        .filter(l => l.accountId)
        .map(l => ({
          accountId: l.accountId,
          description: l.description || null,
          debitAmount:  parseFloat(l.debitAmount)  || null,
          creditAmount: parseFloat(l.creditAmount) || null,
        })),
    })
  }

  const fmtR = (n: number) => n ? `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", gap: 8 }}>
          {["", "DRAFT", "POSTED"].map(s => (
            <button
              key={s}
              onClick={() => setStatus(s)}
              style={{
                padding: "6px 14px", borderRadius: 6, fontSize: 13, cursor: "pointer",
                border: statusFilter === s ? "1px solid #0D9488" : "1px solid #E2E8F0",
                background: statusFilter === s ? "#F0FDF4" : "#fff",
                color: statusFilter === s ? "#0D9488" : "#64748B",
                fontWeight: statusFilter === s ? 600 : 400,
              }}
            >
              {s || "All"}
            </button>
          ))}
        </div>
        <button onClick={() => setShowCreate(true)} style={btnPrimary}>
          <Plus size={15} /> New Entry
        </button>
      </div>

      {/* Entries list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading entries...</div>
      ) : entries.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>No journal entries yet</div>
          <div style={{ fontSize: 14 }}>Create your first double-entry journal entry.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {entries.map(entry => {
            const style = STATUS_STYLE[entry.status] || { color: "#475569", bg: "#F8FAFC" }
            const isOpen = expanded === entry.id
            return (
              <div key={entry.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
                <div
                  onClick={() => setExpanded(isOpen ? null : entry.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 18px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff" }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{entry.entryNumber}</span>
                        <span style={{ background: style.bg, color: style.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{entry.status}</span>
                        {!entry.balanced && (
                          <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>UNBALANCED</span>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>
                        {entry.entryDate} · {entry.description}
                        {entry.reference && ` · Ref: ${entry.reference}`}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{fmtR(entry.totalDebit)}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>debit / credit</div>
                    </div>
                    {entry.status === "DRAFT" && (
                      <button
                        onClick={e => { e.stopPropagation(); postEntry.mutate(entry.id) }}
                        style={{ padding: "5px 12px", background: "#0D9488", color: "#fff", border: "none", borderRadius: 6, fontSize: 12, cursor: "pointer" }}
                      >
                        Post
                      </button>
                    )}
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {isOpen && entry.lines?.length > 0 && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "12px 18px", background: "#FAFAFA" }}>
                    <table style={{ width: "100%", borderCollapse: "collapse" }}>
                      <thead>
                        <tr>
                          <th style={th}>Account</th>
                          <th style={th}>Description</th>
                          <th style={{ ...th, textAlign: "right" }}>Debit</th>
                          <th style={{ ...th, textAlign: "right" }}>Credit</th>
                        </tr>
                      </thead>
                      <tbody>
                        {entry.lines.map(line => (
                          <tr key={line.id}>
                            <td style={td}>
                              <span style={{ fontFamily: "monospace", fontSize: 12, color: "#64748B" }}>{line.accountCode}</span>
                              <span style={{ marginLeft: 8, fontSize: 13, color: "#0F172A" }}>{line.accountName}</span>
                            </td>
                            <td style={{ ...td, color: "#64748B" }}>{line.description || "—"}</td>
                            <td style={{ ...td, textAlign: "right", color: "#1D4ED8", fontWeight: line.debitAmount ? 600 : 400 }}>
                              {line.debitAmount ? fmtR(line.debitAmount) : "—"}
                            </td>
                            <td style={{ ...td, textAlign: "right", color: "#166534", fontWeight: line.creditAmount ? 600 : 400 }}>
                              {line.creditAmount ? fmtR(line.creditAmount) : "—"}
                            </td>
                          </tr>
                        ))}
                        <tr style={{ borderTop: "2px solid #E2E8F0" }}>
                          <td colSpan={2} style={{ ...td, fontWeight: 700, color: "#0F172A" }}>TOTAL</td>
                          <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "#1D4ED8" }}>{fmtR(entry.totalDebit)}</td>
                          <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "#166534" }}>{fmtR(entry.totalCredit)}</td>
                        </tr>
                      </tbody>
                    </table>
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
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 720, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Journal Entry</h3>
              <button onClick={() => { setShowCreate(false); resetForm() }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 20 }}>
              <Field label="Date *">
                <input type="date" value={form.entryDate} onChange={e => setForm(f => ({ ...f, entryDate: e.target.value }))} style={inputStyle} />
              </Field>
              <Field label="Entry Type">
                <select value={form.entryType} onChange={e => setForm(f => ({ ...f, entryType: e.target.value }))} style={inputStyle}>
                  {["MANUAL", "ADJUSTMENT", "OPENING", "CLOSING", "ACCRUAL"].map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </Field>
              <Field label="Reference">
                <input value={form.reference} onChange={e => setForm(f => ({ ...f, reference: e.target.value }))} placeholder="INV-001" style={inputStyle} />
              </Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Description *">
                  <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="Monthly salary expense" style={inputStyle} />
                </Field>
              </div>
            </div>

            {/* Lines */}
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 10, letterSpacing: "0.04em" }}>JOURNAL LINES</div>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#F8FAFC" }}>
                    <th style={th}>Account</th>
                    <th style={th}>Description</th>
                    <th style={{ ...th, textAlign: "right" }}>Debit (R)</th>
                    <th style={{ ...th, textAlign: "right" }}>Credit (R)</th>
                    <th style={th}></th>
                  </tr>
                </thead>
                <tbody>
                  {form.lines.map((line, i) => (
                    <tr key={i}>
                      <td style={{ padding: "6px 8px" }}>
                        <select
                          value={line.accountId}
                          onChange={e => updateLine(i, "accountId", e.target.value)}
                          style={{ ...inputStyle, minWidth: 200 }}
                        >
                          <option value="">Select account...</option>
                          {accounts.map(a => (
                            <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>
                          ))}
                        </select>
                      </td>
                      <td style={{ padding: "6px 8px" }}>
                        <input value={line.description} onChange={e => updateLine(i, "description", e.target.value)} placeholder="Optional" style={{ ...inputStyle, minWidth: 120 }} />
                      </td>
                      <td style={{ padding: "6px 8px" }}>
                        <input type="number" value={line.debitAmount} onChange={e => updateLine(i, "debitAmount", e.target.value)} placeholder="0.00" style={{ ...inputStyle, textAlign: "right", minWidth: 100 }} />
                      </td>
                      <td style={{ padding: "6px 8px" }}>
                        <input type="number" value={line.creditAmount} onChange={e => updateLine(i, "creditAmount", e.target.value)} placeholder="0.00" style={{ ...inputStyle, textAlign: "right", minWidth: 100 }} />
                      </td>
                      <td style={{ padding: "6px 8px" }}>
                        {form.lines.length > 2 && (
                          <button onClick={() => removeLine(i)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={14} /></button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <button onClick={addLine} style={{ ...btnOutline, marginTop: 8 }}><Plus size={13} /> Add Line</button>
            </div>

            {/* Balance indicator */}
            <div style={{
              display: "flex", justifyContent: "space-between", alignItems: "center",
              padding: "10px 14px", borderRadius: 8,
              background: balanced ? "#DCFCE7" : "#FEF2F2",
              border: `1px solid ${balanced ? "#86EFAC" : "#FECACA"}`,
              marginBottom: 16,
            }}>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                {!balanced && <AlertCircle size={14} color="#DC2626" />}
                <span style={{ fontSize: 13, fontWeight: 600, color: balanced ? "#166534" : "#DC2626" }}>
                  {balanced ? "✓ Balanced" : "Entry is not balanced"}
                </span>
              </div>
              <div style={{ fontSize: 13, color: "#475569" }}>
                Debits: <strong>R {totalDebit.toFixed(2)}</strong> · Credits: <strong>R {totalCredit.toFixed(2)}</strong>
              </div>
            </div>

            {error && <div style={{ marginBottom: 12, color: "#DC2626", fontSize: 13 }}>{error}</div>}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => { setShowCreate(false); resetForm() }} style={btnCancel}>Cancel</button>
              <button onClick={handleSubmit} disabled={createEntry.isPending} style={btnPrimary}>
                {createEntry.isPending ? "Saving..." : "Create Entry"}
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

const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13, boxSizing: "border-box" as const, background: "#fff" }
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnOutline: React.CSSProperties = { display: "flex", alignItems: "center", gap: 5, background: "#fff", color: "#64748B", border: "1px solid #E2E8F0", borderRadius: 6, padding: "6px 12px", fontSize: 13, cursor: "pointer" }
const btnCancel: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const th: React.CSSProperties = { padding: "8px 10px", textAlign: "left", fontSize: 11, fontWeight: 600, color: "#64748B", letterSpacing: "0.05em", borderBottom: "1px solid #E2E8F0" }
const td: React.CSSProperties = { padding: "10px 10px", fontSize: 13, borderBottom: "1px solid #F1F5F9" }
