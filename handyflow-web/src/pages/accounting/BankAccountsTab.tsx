// src/pages/accounting/BankAccountsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Landmark, TrendingUp, TrendingDown, AlertCircle } from "lucide-react"

interface BankAccount {
  id: string; bankName: string; accountName: string; accountNumber: string
  branchCode: string; accountType: string; currency: string; currentBalance: number; active: boolean
}

const SA_BANKS = [
  "First National Bank", "Standard Bank", "ABSA", "Nedbank",
  "Capitec Bank", "African Bank", "Investec", "Bidvest Bank", "TymeBank",
]
const ACCOUNT_TYPES = ["CURRENT", "SAVINGS", "TRANSMISSION", "CREDIT_CARD"]

const EMPTY_FORM = { bankName: "", accountName: "", accountNumber: "", branchCode: "", accountType: "CURRENT", currency: "ZAR" }
const EMPTY_TXN  = { transactionDate: new Date().toISOString().split("T")[0], description: "", reference: "", type: "CREDIT", amount: "" }

export default function BankAccountsTab() {
  const qc = useQueryClient()

  const [showCreate, setShowCreate] = useState(false)
  const [showTxn, setShowTxn]       = useState<string | null>(null)

  const [form, setForm]       = useState(EMPTY_FORM)
  const [txnForm, setTxnForm] = useState(EMPTY_TXN)

  const [formErrors, setFormErrors] = useState<Record<string, string>>({})
  const [txnErrors, setTxnErrors]   = useState<Record<string, string>>({})
  const [createError, setCreateError] = useState("")
  const [txnError, setTxnError]       = useState("")

  // ── Queries ─────────────────────────────────────────────────────────────────
  const { data: accounts = [], isLoading } = useQuery<BankAccount[]>({
    queryKey: ["bank-accounts"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/bank-accounts")
      return (res.data?.data ?? res.data) as BankAccount[]
    },
  })

  // ── Mutations ────────────────────────────────────────────────────────────────
  const createAccount = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/accounting/bank-accounts", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bank-accounts"] })
      setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("")
    },
    onError: (e: any) => {
      const data = e.response?.data
      if (data?.data && typeof data.data === "object") {
        const map: Record<string, string> = {}
        Object.entries(data.data).forEach(([k, v]) => { map[k] = v as string })
        setFormErrors(map)
        setCreateError("")
      } else {
        setCreateError(data?.message ?? "Failed to create bank account")
      }
    },
  })

  const addTransaction = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/accounting/bank-accounts/${id}/transactions`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bank-accounts"] })
      setShowTxn(null); setTxnForm(EMPTY_TXN); setTxnErrors({}); setTxnError("")
    },
    onError: (e: any) => {
      const data = e.response?.data
      if (data?.data && typeof data.data === "object") {
        const map: Record<string, string> = {}
        Object.entries(data.data).forEach(([k, v]) => { map[k] = v as string })
        setTxnErrors(map)
        setTxnError("")
      } else {
        setTxnError(data?.message ?? "Failed to record transaction")
      }
    },
  })

  // ── Validation ───────────────────────────────────────────────────────────────
  const validateAccount = (): boolean => {
    const errs: Record<string, string> = {}
    if (!form.bankName)      errs.bankName      = "Please select a bank"
    if (!form.accountName.trim()) errs.accountName = "Account name is required"
    if (!form.accountNumber.trim()) errs.accountNumber = "Account number is required"
    else if (!/^\d+$/.test(form.accountNumber)) errs.accountNumber = "Account number must contain digits only"
    if (!form.branchCode.trim()) errs.branchCode = "Branch code is required"
    else if (!/^\d+$/.test(form.branchCode))     errs.branchCode = "Branch code must contain digits only"
    setFormErrors(errs)
    return Object.keys(errs).length === 0
  }

  const validateTxn = (): boolean => {
    const errs: Record<string, string> = {}
    if (!txnForm.transactionDate)        errs.transactionDate = "Date is required"
    if (!txnForm.description.trim())     errs.description     = "Description is required"
    if (!txnForm.amount)                 errs.amount          = "Amount is required"
    else if (parseFloat(txnForm.amount) <= 0) errs.amount     = "Amount must be greater than zero"
    setTxnErrors(errs)
    return Object.keys(errs).length === 0
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────
  const fmtR        = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
  const totalBalance = accounts.reduce((s, a) => s + (a.currentBalance ?? 0), 0)

  const inpStyle = (key: string, errors: Record<string, string>): React.CSSProperties => ({
    ...inputStyle,
    ...(errors[key] ? { borderColor: "#DC2626", background: "#FFF5F5" } : {}),
  })

  const FieldErr = ({ name, errors }: { name: string; errors: Record<string, string> }) =>
    errors[name] ? (
      <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
        <AlertCircle size={12} color="#DC2626" />{errors[name]}
      </div>
    ) : null

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading bank accounts...</div>

  return (
    <div>
      {/* Summary */}
      {accounts.length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
          <div style={{ background: totalBalance >= 0 ? "#F0FDF4" : "#FEF2F2", border: `1px solid ${totalBalance >= 0 ? "#86EFAC" : "#FECACA"}`, borderRadius: 10, padding: "12px 20px" }}>
            <div style={{ fontSize: 11, color: totalBalance >= 0 ? "#166534" : "#DC2626", fontWeight: 600, marginBottom: 2 }}>TOTAL CASH POSITION</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: totalBalance >= 0 ? "#166534" : "#DC2626" }}>{fmtR(totalBalance)}</div>
          </div>
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 20px" }}>
            <div style={{ fontSize: 11, color: "#64748B", fontWeight: 600, marginBottom: 2 }}>ACCOUNTS</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#0F172A" }}>{accounts.length}</div>
          </div>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }}
          style={btnPrimary}>
          <Plus size={15} /> Add Bank Account
        </button>
      </div>

      {accounts.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Landmark size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No bank accounts yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Add your business bank accounts to track cash flow.</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))", gap: 16 }}>
          {accounts.map(acc => (
            <div key={acc.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              <div style={{ background: "#1B3A6B", padding: "18px 20px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div>
                    <div style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", marginBottom: 4, letterSpacing: "0.06em" }}>{acc.accountType} · {acc.currency}</div>
                    <div style={{ fontSize: 16, fontWeight: 700, color: "#fff" }}>{acc.accountName}</div>
                    <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)", marginTop: 2 }}>{acc.bankName}</div>
                  </div>
                  <div style={{ background: "rgba(255,255,255,0.15)", borderRadius: 8, padding: "4px 8px" }}>
                    <Landmark size={18} color="rgba(255,255,255,0.8)" />
                  </div>
                </div>
                <div style={{ marginTop: 16 }}>
                  <div style={{ fontSize: 11, color: "rgba(255,255,255,0.6)" }}>CURRENT BALANCE</div>
                  <div style={{ fontSize: 24, fontWeight: 700, color: acc.currentBalance >= 0 ? "#4ADE80" : "#F87171" }}>
                    {fmtR(acc.currentBalance)}
                  </div>
                </div>
              </div>
              <div style={{ padding: "14px 20px", background: "#fff" }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                  <div>
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>Account Number</div>
                    <div style={{ fontSize: 13, fontFamily: "monospace", color: "#0F172A" }}>{acc.accountNumber}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>Branch Code</div>
                    <div style={{ fontSize: 13, fontFamily: "monospace", color: "#0F172A" }}>{acc.branchCode}</div>
                  </div>
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button
                    onClick={() => { setShowTxn(acc.id); setTxnForm({ ...EMPTY_TXN, type: "CREDIT" }); setTxnErrors({}); setTxnError("") }}
                    style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 5, padding: "7px 12px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, cursor: "pointer" }}>
                    <TrendingUp size={13} /> Money In
                  </button>
                  <button
                    onClick={() => { setShowTxn(acc.id); setTxnForm({ ...EMPTY_TXN, type: "DEBIT" }); setTxnErrors({}); setTxnError("") }}
                    style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 5, padding: "7px 12px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, cursor: "pointer" }}>
                    <TrendingDown size={13} /> Money Out
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Add Bank Account Modal ───────────────────────────────────────── */}
      {showCreate && (
        <Modal title="Add Bank Account" onClose={() => { setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>

            <div style={{ gridColumn: "1 / -1" }}>
              <FLabel>Bank Name *</FLabel>
              <select value={form.bankName}
                onChange={e => { setForm(f => ({ ...f, bankName: e.target.value })); setFormErrors(f => { const n = { ...f }; delete n.bankName; return n }) }}
                style={inpStyle("bankName", formErrors)}>
                <option value="">Select bank...</option>
                {SA_BANKS.map(b => <option key={b} value={b}>{b}</option>)}
              </select>
              <FieldErr name="bankName" errors={formErrors} />
            </div>

            <div>
              <FLabel>Account Name *</FLabel>
              <input value={form.accountName}
                onChange={e => { setForm(f => ({ ...f, accountName: e.target.value })); setFormErrors(f => { const n = { ...f }; delete n.accountName; return n }) }}
                placeholder="Business Cheque" style={inpStyle("accountName", formErrors)} />
              <FieldErr name="accountName" errors={formErrors} />
            </div>

            <div>
              <FLabel>Account Type</FLabel>
              <select value={form.accountType} onChange={e => setForm(f => ({ ...f, accountType: e.target.value }))} style={inputStyle}>
                {ACCOUNT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>

            <div>
              <FLabel>Account Number *</FLabel>
              <input value={form.accountNumber}
                onChange={e => { setForm(f => ({ ...f, accountNumber: e.target.value.replace(/\D/g, "") })); setFormErrors(f => { const n = { ...f }; delete n.accountNumber; return n }) }}
                placeholder="62012345678" inputMode="numeric" style={inpStyle("accountNumber", formErrors)} />
              <FieldErr name="accountNumber" errors={formErrors} />
            </div>

            <div>
              <FLabel>Branch Code *</FLabel>
              <input value={form.branchCode}
                onChange={e => { setForm(f => ({ ...f, branchCode: e.target.value.replace(/\D/g, "") })); setFormErrors(f => { const n = { ...f }; delete n.branchCode; return n }) }}
                placeholder="250655" inputMode="numeric" style={inpStyle("branchCode", formErrors)} />
              <FieldErr name="branchCode" errors={formErrors} />
            </div>
          </div>

          {createError && (
            <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
              <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />{createError}
            </div>
          )}

          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
            <button onClick={() => { setShowCreate(false); setForm(EMPTY_FORM); setFormErrors({}); setCreateError("") }} style={btnCancel}>Cancel</button>
            <button
              onClick={() => { if (validateAccount()) createAccount.mutate(form) }}
              disabled={createAccount.isPending}
              style={{ ...btnPrimary, opacity: createAccount.isPending ? 0.7 : 1 }}>
              {createAccount.isPending ? "Adding..." : "Add Account"}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Add Transaction Modal ────────────────────────────────────────── */}
      {showTxn && (
        <Modal
          title={txnForm.type === "CREDIT" ? "Record Money In" : "Record Money Out"}
          onClose={() => { setShowTxn(null); setTxnForm(EMPTY_TXN); setTxnErrors({}); setTxnError("") }}>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>

            <div>
              <FLabel>Date *</FLabel>
              <input type="date" value={txnForm.transactionDate}
                onChange={e => { setTxnForm(f => ({ ...f, transactionDate: e.target.value })); setTxnErrors(f => { const n = { ...f }; delete n.transactionDate; return n }) }}
                style={inpStyle("transactionDate", txnErrors)} />
              <FieldErr name="transactionDate" errors={txnErrors} />
            </div>

            <div>
              <FLabel>Type</FLabel>
              <select value={txnForm.type} onChange={e => setTxnForm(f => ({ ...f, type: e.target.value }))} style={inputStyle}>
                <option value="CREDIT">CREDIT (money in)</option>
                <option value="DEBIT">DEBIT (money out)</option>
              </select>
            </div>

            <div style={{ gridColumn: "1 / -1" }}>
              <FLabel>Description *</FLabel>
              <input value={txnForm.description}
                onChange={e => { setTxnForm(f => ({ ...f, description: e.target.value })); setTxnErrors(f => { const n = { ...f }; delete n.description; return n }) }}
                placeholder="Customer payment received"
                style={inpStyle("description", txnErrors)} />
              <FieldErr name="description" errors={txnErrors} />
            </div>

            <div>
              <FLabel>Amount (R) *</FLabel>
              <input
                type="number" min="0.01" step="0.01"
                value={txnForm.amount}
                onChange={e => { setTxnForm(f => ({ ...f, amount: e.target.value })); setTxnErrors(f => { const n = { ...f }; delete n.amount; return n }) }}
                placeholder="5000.00"
                style={inpStyle("amount", txnErrors)} />
              <FieldErr name="amount" errors={txnErrors} />
            </div>

            <div>
              <FLabel>Reference <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></FLabel>
              <input value={txnForm.reference}
                onChange={e => setTxnForm(f => ({ ...f, reference: e.target.value }))}
                placeholder="INV-001" style={inputStyle} />
            </div>
          </div>

          {txnError && (
            <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
              <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />{txnError}
            </div>
          )}

          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
            <button onClick={() => { setShowTxn(null); setTxnForm(EMPTY_TXN); setTxnErrors({}); setTxnError("") }} style={btnCancel}>Cancel</button>
            <button
              onClick={() => {
                if (!validateTxn()) return
                addTransaction.mutate({
                  id: showTxn,
                  body: {
                    transactionDate: txnForm.transactionDate,
                    description:     txnForm.description,
                    reference:       txnForm.reference || undefined,
                    amount:          parseFloat(txnForm.amount),
                    transactionType: txnForm.type,   // ← correct field name
                  },
                })
              }}
              disabled={addTransaction.isPending}
              style={{
                ...btnPrimary,
                background: txnForm.type === "CREDIT" ? "#166534" : "#DC2626",
                opacity: addTransaction.isPending ? 0.7 : 1,
              }}>
              {addTransaction.isPending ? "Saving..." : txnForm.type === "CREDIT" ? "Record Money In" : "Record Money Out"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}

// ── Shared sub-components ─────────────────────────────────────────────────────
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 520, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

function FLabel({ children }: { children: React.ReactNode }) {
  return <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>{children}</label>
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const btnCancel:  React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }