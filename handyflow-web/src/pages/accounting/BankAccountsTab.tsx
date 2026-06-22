// ══════════════════════════════════════════════════════════════════
// BankAccountsTab.tsx
// ══════════════════════════════════════════════════════════════════
// src/pages/accounting/BankAccountsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, AlertCircle, TrendingUp, TrendingDown, ChevronDown, ChevronUp } from "lucide-react"
import { apiClient } from "../../api/client"

interface BankAccount { id: string; bankName: string; accountName: string; accountNumber: string
  branchCode: string; accountType: string; currency: string; currentBalance: number; active: boolean }
interface BankTx { id: string; transactionDate: string; description: string; reference: string
  amount: number; transactionType: string; balanceAfter: number; reconciled: boolean }

const fmtR  = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDt = (d: string) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"
const inp: React.CSSProperties = {
  width: "100%", padding: "8px 12px", border: "1.5px solid #E2E8F0",
  borderRadius: 8, fontSize: 13, outline: "none", boxSizing: "border-box",
}

export default function BankAccountsTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [showTx, setShowTx] = useState<BankAccount | null>(null)
  const [showAddTx, setShowAddTx] = useState(false)
  const [error, setError] = useState("")
  const [bankForm, setBankForm] = useState({ bankName: "", accountName: "", accountNumber: "", branchCode: "", accountType: "CURRENT" })
  const [txForm, setTxForm] = useState({ transactionDate: new Date().toISOString().split("T")[0],
    description: "", reference: "", amount: "", transactionType: "CREDIT" })

  const { data: accounts = [], isLoading } = useQuery<BankAccount[]>({
    queryKey: ["bank-accounts"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/bank-accounts")
      return (res.data?.data ?? res.data) as BankAccount[]
    },
  })

  const { data: txData } = useQuery({
    queryKey: ["bank-tx", showTx?.id],
    enabled: !!showTx,
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/accounting/bank-accounts/${showTx!.id}/transactions?size=50&sort=transactionDate,desc`)
      const payload = res.data?.data ?? res.data
      return (payload.content ?? payload) as BankTx[]
    },
  })

  const createAccount = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/bank-accounts", bankForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["bank-accounts"] }); setShowCreate(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create bank account"),
  })

  const addTx = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/accounting/bank-accounts/${showTx!.id}/transactions`, {
      ...txForm, amount: parseFloat(txForm.amount),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bank-accounts"] })
      qc.invalidateQueries({ queryKey: ["bank-tx", showTx?.id] })
      setShowAddTx(false)
      setTxForm({ transactionDate: new Date().toISOString().split("T")[0], description: "", reference: "", amount: "", transactionType: "CREDIT" })
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record transaction"),
  })

  const totalBalance = accounts.reduce((s, a) => s + a.currentBalance, 0)

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Bank Accounts</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: "3px 0 0" }}>
            Total cash position: <strong style={{ color: totalBalance >= 0 ? "#166534" : "#DC2626" }}>{fmtR(totalBalance)}</strong>
          </p>
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "white",
            border: "none", borderRadius: 9, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Add Bank Account
        </button>
      </div>

      {isLoading ? (
        <div style={{ padding: 60, textAlign: "center", color: "#94A3B8" }}>Loading bank accounts...</div>
      ) : accounts.length === 0 ? (
        <div style={{ padding: 60, textAlign: "center", color: "#94A3B8", background: "white",
          border: "1px solid #E2E8F0", borderRadius: 12 }}>
          No bank accounts yet — add one to start tracking your cash position.
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: 14 }}>
          {accounts.map(acc => (
            <div key={acc.id}
              style={{ background: "#1B3A6B", borderRadius: 14, padding: "20px 22px", cursor: "pointer",
                boxShadow: "0 2px 8px rgba(27,58,107,0.15)" }}
              onClick={() => setShowTx(acc)}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
                <div>
                  <div style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", marginBottom: 3 }}>
                    {acc.bankName} · {acc.accountType} · {acc.currency}
                  </div>
                  <div style={{ fontSize: 15, fontWeight: 700, color: "white" }}>{acc.accountName}</div>
                  <div style={{ fontSize: 12, color: "rgba(255,255,255,0.5)", marginTop: 2 }}>{acc.accountNumber}</div>
                </div>
                <span style={{ fontSize: 10, background: "rgba(255,255,255,0.15)", color: "rgba(255,255,255,0.8)",
                  padding: "3px 8px", borderRadius: 10 }}>{acc.accountType}</span>
              </div>
              <div style={{ fontSize: 24, fontWeight: 800, color: acc.currentBalance >= 0 ? "#4ADE80" : "#F87171" }}>
                {fmtR(acc.currentBalance)}
              </div>
              <div style={{ fontSize: 11, color: "rgba(255,255,255,0.5)", marginTop: 4 }}>Current balance · click to view transactions</div>
            </div>
          ))}
        </div>
      )}

      {/* Transaction panel */}
      {showTx && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex",
          justifyContent: "flex-end", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "white", width: 560, height: "100%", overflowY: "auto",
            boxShadow: "-4px 0 24px rgba(0,0,0,0.12)" }}>
            <div style={{ background: "#1B3A6B", padding: "20px 24px", display: "flex",
              justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontSize: 16, fontWeight: 700, color: "white" }}>{showTx.accountName}</div>
                <div style={{ fontSize: 12, color: "rgba(255,255,255,0.6)" }}>{showTx.bankName} · {showTx.accountNumber}</div>
              </div>
              <button onClick={() => setShowTx(null)}
                style={{ background: "none", border: "none", cursor: "pointer", color: "rgba(255,255,255,0.7)" }}>
                <X size={20} />
              </button>
            </div>
            <div style={{ padding: 20, borderBottom: "1px solid #F1F5F9", display: "flex",
              justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontSize: 11, color: "#94A3B8" }}>Current Balance</div>
                <div style={{ fontSize: 22, fontWeight: 800, color: showTx.currentBalance >= 0 ? "#166534" : "#DC2626" }}>
                  {fmtR(showTx.currentBalance)}
                </div>
              </div>
              <button onClick={() => { setShowAddTx(true); setError("") }}
                style={{ display: "flex", alignItems: "center", gap: 5, background: "#0D9488", color: "white",
                  border: "none", borderRadius: 8, padding: "8px 14px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                <Plus size={12} /> Add Transaction
              </button>
            </div>

            {showAddTx && (
              <div style={{ padding: 16, background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12 }}>New Transaction</div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 10 }}>
                  <div>
                    <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 4 }}>Date</label>
                    <input type="date" value={txForm.transactionDate}
                      onChange={e => setTxForm(p => ({ ...p, transactionDate: e.target.value }))} style={inp} />
                  </div>
                  <div>
                    <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 4 }}>Type</label>
                    <select value={txForm.transactionType}
                      onChange={e => setTxForm(p => ({ ...p, transactionType: e.target.value }))}
                      style={{ ...inp }}>
                      <option value="CREDIT">CREDIT (Money In)</option>
                      <option value="DEBIT">DEBIT (Money Out)</option>
                    </select>
                  </div>
                </div>
                <div style={{ marginBottom: 10 }}>
                  <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 4 }}>Description *</label>
                  <input value={txForm.description} onChange={e => setTxForm(p => ({ ...p, description: e.target.value }))}
                    placeholder="e.g. Client payment — INV-001" style={inp} />
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 12 }}>
                  <div>
                    <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 4 }}>Amount (R) *</label>
                    <input type="number" value={txForm.amount}
                      onChange={e => setTxForm(p => ({ ...p, amount: e.target.value }))}
                      placeholder="0.00" style={inp} />
                  </div>
                  <div>
                    <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 4 }}>Reference</label>
                    <input value={txForm.reference} onChange={e => setTxForm(p => ({ ...p, reference: e.target.value }))}
                      placeholder="e.g. EFT-001" style={inp} />
                  </div>
                </div>
                {error && (
                  <div style={{ padding: "8px 10px", background: "#FEF2F2", borderRadius: 7,
                    fontSize: 12, color: "#DC2626", marginBottom: 10 }}>{error}</div>
                )}
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                  <button onClick={() => setShowAddTx(false)}
                    style={{ padding: "7px 14px", border: "1px solid #E2E8F0", borderRadius: 7,
                      background: "white", fontSize: 12, cursor: "pointer", color: "#374151" }}>Cancel</button>
                  <button disabled={addTx.isPending || !txForm.description || !txForm.amount}
                    onClick={() => addTx.mutate()}
                    style={{ padding: "7px 14px", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600,
                      background: "#0D9488", color: "white", cursor: "pointer" }}>
                    {addTx.isPending ? "Saving..." : "Record Transaction"}
                  </button>
                </div>
              </div>
            )}

            <div style={{ padding: "0 20px" }}>
              {!txData || txData.length === 0 ? (
                <div style={{ padding: 40, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>
                  No transactions yet
                </div>
              ) : txData.map((tx, i) => (
                <div key={tx.id} style={{ padding: "14px 0", borderBottom: i < txData.length - 1 ? "1px solid #F1F5F9" : "none",
                  display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <div style={{ width: 32, height: 32, borderRadius: "50%", display: "flex", alignItems: "center",
                      justifyContent: "center",
                      background: tx.transactionType === "CREDIT" ? "#F0FDF4" : "#FEF2F2" }}>
                      {tx.transactionType === "CREDIT"
                        ? <TrendingUp size={14} color="#166534" />
                        : <TrendingDown size={14} color="#DC2626" />}
                    </div>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{tx.description}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>
                        {fmtDt(tx.transactionDate)}{tx.reference ? ` · ${tx.reference}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ textAlign: "right" }}>
                    <div style={{ fontSize: 14, fontWeight: 700,
                      color: tx.transactionType === "CREDIT" ? "#166534" : "#DC2626" }}>
                      {tx.transactionType === "CREDIT" ? "+" : "-"}{fmtR(tx.amount)}
                    </div>
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>Balance: {fmtR(tx.balanceAfter)}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Create Bank Account Modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "white", borderRadius: 16, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>Add Bank Account</h3>
              <button onClick={() => setShowCreate(false)}
                style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={18} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {[
                { label: "Bank Name *", key: "bankName", placeholder: "e.g. First National Bank" },
                { label: "Account Name *", key: "accountName", placeholder: "e.g. Business Current Account" },
                { label: "Account Number *", key: "accountNumber", placeholder: "e.g. 62012345678" },
                { label: "Branch Code", key: "branchCode", placeholder: "e.g. 250655" },
              ].map(f => (
                <div key={f.key}>
                  <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>{f.label}</label>
                  <input value={(bankForm as any)[f.key]} placeholder={f.placeholder}
                    onChange={e => setBankForm(p => ({ ...p, [f.key]: e.target.value }))} style={inp} />
                </div>
              ))}
              <div>
                <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Account Type</label>
                <select value={bankForm.accountType} onChange={e => setBankForm(p => ({ ...p, accountType: e.target.value }))} style={inp}>
                  <option value="CURRENT">Current / Cheque</option>
                  <option value="SAVINGS">Savings</option>
                  <option value="CREDIT">Credit</option>
                </select>
              </div>
            </div>
            {error && (
              <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA",
                borderRadius: 8, fontSize: 12, color: "#DC2626", marginTop: 12 }}>{error}</div>
            )}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "white", fontSize: 13, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button disabled={createAccount.isPending || !bankForm.bankName || !bankForm.accountName || !bankForm.accountNumber}
                onClick={() => createAccount.mutate()}
                style={{ padding: "9px 20px", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700,
                  background: "#1B3A6B", color: "white", cursor: "pointer" }}>
                {createAccount.isPending ? "Adding..." : "Add Account"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
