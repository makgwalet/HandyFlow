// ══════════════════════════════════════════════════════════════════
// BankAccountsTab.tsx
// ══════════════════════════════════════════════════════════════════
// src/pages/accounting/BankAccountsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, AlertCircle, TrendingUp, TrendingDown, ChevronDown, ChevronUp, Upload, Link2, CheckCircle, FilePlus2, BellRing } from "lucide-react"
import { apiClient } from "../../api/client"

interface BankAccount { id: string; bankName: string; accountName: string; accountNumber: string
  branchCode: string; accountType: string; currency: string; currentBalance: number; active: boolean
  accountId: string | null; lowBalanceThreshold: number | null }
interface BankTx { id: string; transactionDate: string; description: string; reference: string
  amount: number; transactionType: string; balanceAfter: number; reconciled: boolean
  journalLineId: string | null; reconciledAt: string | null }
interface MatchCandidate { journalLineId: string; journalEntryId: string; journalEntryNumber: string
  entryDate: string; description: string; amount: number; exactMatch: boolean }
interface Account { id: string; accountCode: string; accountName: string; accountType: string }
interface ImportResult { imported: number; skippedDuplicates: number; failed: number; errors: string[]; newBalance: number }

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
  const [showImport, setShowImport] = useState(false)
  const [importFileName, setImportFileName] = useState("")
  const [importCsvBase64, setImportCsvBase64] = useState("")
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [reconcileTx, setReconcileTx] = useState<BankTx | null>(null)
  const [reconcileMode, setReconcileMode] = useState<"match" | "new">("match")
  const [newJournalAccountId, setNewJournalAccountId] = useState("")
  const [newJournalDescription, setNewJournalDescription] = useState("")
  const [showLinkAccount, setShowLinkAccount] = useState(false)
  const [linkAccountId, setLinkAccountId] = useState("")
  const [showThreshold, setShowThreshold] = useState(false)
  const [thresholdInput, setThresholdInput] = useState("")

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

  // Chart of Accounts — only needed for the "create new journal" reconcile
  // path, so only fetched once that mode is actually open.
  const { data: coaAccounts = [] } = useQuery<Account[]>({
    queryKey: ["coa"],
    enabled: (reconcileMode === "new" && !!reconcileTx) || showLinkAccount,
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/accounts")
      return (res.data?.data ?? res.data) as Account[]
    },
  })

  const { data: matchCandidates = [], isLoading: candidatesLoading, isError: candidatesError, error: candidatesErrorObj } = useQuery<MatchCandidate[]>({
    queryKey: ["match-candidates", showTx?.id, reconcileTx?.id],
    enabled: !!showTx && !!reconcileTx && reconcileMode === "match",
    retry: false, // a 409 here (unlinked bank account) is a real, non-transient state — retrying it can't help
    queryFn: async () => {
      const res = await apiClient.get(
        `/api/v1/accounting/bank-accounts/${showTx!.id}/transactions/${reconcileTx!.id}/match-candidates`)
      return (res.data?.data ?? res.data) as MatchCandidate[]
    },
  })

  const createAccount = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/bank-accounts", bankForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["bank-accounts"] }); setShowCreate(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create bank account"),
  })

  const linkAccount = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/accounting/bank-accounts/${showTx!.id}/link-account`, {
      accountId: linkAccountId,
    }),
    onSuccess: (r: any) => {
      qc.invalidateQueries({ queryKey: ["bank-accounts"] })
      // showTx is a local snapshot taken when the card was clicked — the
      // list refetch above won't update it. Same staleness fix used
      // elsewhere this session: sync it directly from the mutation's own
      // response instead of waiting on a refetch that won't reach it.
      setShowTx((r.data?.data ?? r.data) as BankAccount)
      setShowLinkAccount(false); setLinkAccountId(""); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to link account"),
  })

  const setThreshold = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/accounting/bank-accounts/${showTx!.id}/low-balance-threshold`, {
      threshold: thresholdInput.trim() === "" ? null : parseFloat(thresholdInput),
    }),
    onSuccess: (r: any) => {
      qc.invalidateQueries({ queryKey: ["bank-accounts"] })
      setShowTx((r.data?.data ?? r.data) as BankAccount)  // same staleness fix as linkAccount above
      setShowThreshold(false); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to update threshold"),
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

  const importTx = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/accounting/bank-accounts/${showTx!.id}/transactions/import`, {
      csvBase64: importCsvBase64,
    }),
    onSuccess: (r: any) => {
      qc.invalidateQueries({ queryKey: ["bank-accounts"] })
      qc.invalidateQueries({ queryKey: ["bank-tx", showTx?.id] })
      setImportResult((r.data?.data ?? r.data) as ImportResult)
      setImportFileName(""); setImportCsvBase64("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Import failed"),
  })

  const reconcileExisting = useMutation({
    mutationFn: (journalLineId: string) => apiClient.post(
      `/api/v1/accounting/bank-accounts/${showTx!.id}/transactions/${reconcileTx!.id}/reconcile`,
      { journalLineId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bank-tx", showTx?.id] })
      setReconcileTx(null)
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Reconcile failed"),
  })

  const reconcileNew = useMutation({
    mutationFn: () => apiClient.post(
      `/api/v1/accounting/bank-accounts/${showTx!.id}/transactions/${reconcileTx!.id}/reconcile-new`,
      { otherAccountId: newJournalAccountId, description: newJournalDescription || null }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bank-tx", showTx?.id] })
      setReconcileTx(null); setNewJournalAccountId(""); setNewJournalDescription("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Reconcile failed"),
  })

  const handleImportFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      setImportCsvBase64((reader.result as string).split(",")[1])
      setImportFileName(file.name)
    }
    reader.readAsDataURL(file)
  }

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
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2, display: "flex", alignItems: "center", gap: 5 }}>
                  {showTx.lowBalanceThreshold != null ? (
                    <>Low-balance alert at {fmtR(showTx.lowBalanceThreshold)}</>
                  ) : (
                    <>No low-balance alert set</>
                  )}
                  <button onClick={() => { setShowThreshold(true); setThresholdInput(showTx.lowBalanceThreshold?.toString() ?? ""); setError("") }}
                    style={{ background: "none", border: "none", cursor: "pointer", color: "#7C3AED",
                      fontSize: 11, fontWeight: 600, padding: 0, textDecoration: "underline" }}>
                    {showTx.lowBalanceThreshold != null ? "Change" : "Set alert"}
                  </button>
                </div>
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <button onClick={() => { setShowImport(true); setError(""); setImportResult(null) }}
                  style={{ display: "flex", alignItems: "center", gap: 5, background: "white", color: "#1B3A6B",
                    border: "1.5px solid #1B3A6B", borderRadius: 8, padding: "8px 14px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  <Upload size={12} /> Import CSV
                </button>
                <button onClick={() => { setShowAddTx(true); setError("") }}
                  style={{ display: "flex", alignItems: "center", gap: 5, background: "#0D9488", color: "white",
                    border: "none", borderRadius: 8, padding: "8px 14px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  <Plus size={12} /> Add Transaction
                </button>
              </div>
            </div>

            {showTx.lowBalanceThreshold != null && showTx.currentBalance < showTx.lowBalanceThreshold && (
              <div style={{ padding: "10px 20px", background: "#FEF2F2", borderBottom: "1px solid #FECACA",
                display: "flex", alignItems: "center", gap: 8 }}>
                <BellRing size={13} color="#DC2626" />
                <span style={{ fontSize: 12, color: "#DC2626" }}>
                  <strong>Below threshold.</strong> A daily alert email will keep sending until the balance recovers or the threshold is changed.
                </span>
              </div>
            )}

            {showThreshold && (
              <div style={{ padding: 16, background: "#F5F3FF", borderBottom: "1px solid #DDD6FE" }}>
                <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 5 }}>
                  Alert when balance drops below (leave blank to disable)
                </label>
                <input type="number" value={thresholdInput} onChange={e => setThresholdInput(e.target.value)}
                  placeholder="e.g. 10000" style={{ ...inp, marginBottom: 10 }} />
                {error && (
                  <div style={{ padding: "8px 10px", background: "#FEF2F2", borderRadius: 7,
                    fontSize: 12, color: "#DC2626", marginBottom: 10 }}>{error}</div>
                )}
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                  <button onClick={() => setShowThreshold(false)}
                    style={{ padding: "7px 14px", border: "1px solid #E2E8F0", borderRadius: 7,
                      background: "white", fontSize: 12, cursor: "pointer", color: "#374151" }}>Cancel</button>
                  <button disabled={setThreshold.isPending}
                    onClick={() => setThreshold.mutate()}
                    style={{ padding: "7px 14px", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600,
                      background: "#7C3AED", color: "white", cursor: "pointer" }}>
                    {setThreshold.isPending ? "Saving..." : "Save"}
                  </button>
                </div>
              </div>
            )}

            {!showTx.accountId && (
              <div style={{ padding: "12px 20px", background: "#FEF3C7", borderBottom: "1px solid #FDE68A",
                display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ fontSize: 12, color: "#92400E" }}>
                  <strong>Not linked to the Chart of Accounts.</strong> Reconciliation can't search for matching
                  journal entries until this is linked to the GL account it represents.
                </div>
                <button onClick={() => { setShowLinkAccount(true); setError("") }}
                  style={{ padding: "6px 12px", border: "1.5px solid #92400E", borderRadius: 7,
                    background: "white", fontSize: 11, fontWeight: 700, color: "#92400E", cursor: "pointer",
                    whiteSpace: "nowrap" as const, marginLeft: 12 }}>
                  Link now
                </button>
              </div>
            )}

            {showLinkAccount && (
              <div style={{ padding: 16, background: "#FFFBEB", borderBottom: "1px solid #FDE68A" }}>
                <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 5 }}>
                  Which Chart of Accounts entry is this bank account? *
                </label>
                <select value={linkAccountId} onChange={e => setLinkAccountId(e.target.value)} style={{ ...inp, marginBottom: 10 }}>
                  <option value="">Select an account...</option>
                  {coaAccounts.map(a => (
                    <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>
                  ))}
                </select>
                {error && (
                  <div style={{ padding: "8px 10px", background: "#FEF2F2", borderRadius: 7,
                    fontSize: 12, color: "#DC2626", marginBottom: 10 }}>{error}</div>
                )}
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                  <button onClick={() => { setShowLinkAccount(false); setLinkAccountId("") }}
                    style={{ padding: "7px 14px", border: "1px solid #E2E8F0", borderRadius: 7,
                      background: "white", fontSize: 12, cursor: "pointer", color: "#374151" }}>Cancel</button>
                  <button disabled={!linkAccountId || linkAccount.isPending}
                    onClick={() => linkAccount.mutate()}
                    style={{ padding: "7px 14px", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600,
                      background: "#92400E", color: "white", cursor: "pointer", opacity: !linkAccountId ? 0.5 : 1 }}>
                    {linkAccount.isPending ? "Linking..." : "Link account"}
                  </button>
                </div>
              </div>
            )}


            {showImport && (
              <div style={{ padding: 16, background: "#EFF6FF", borderBottom: "1px solid #E2E8F0" }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 6 }}>Import bank statement (CSV)</div>
                <div style={{ fontSize: 11, color: "#64748B", marginBottom: 12 }}>
                  4 columns with a header row: Date, Description, Reference, Amount. Amount is signed —
                  positive = money in, negative = money out. Duplicate rows are skipped automatically.
                </div>
                {!importResult ? (
                  <>
                    <input type="file" accept=".csv,text/csv" onChange={handleImportFile}
                      style={{ fontSize: 12, marginBottom: 12 }} />
                    {error && (
                      <div style={{ padding: "8px 10px", background: "#FEF2F2", borderRadius: 7,
                        fontSize: 12, color: "#DC2626", marginBottom: 10 }}>{error}</div>
                    )}
                    <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                      <button onClick={() => { setShowImport(false); setImportFileName(""); setImportCsvBase64("") }}
                        style={{ padding: "7px 14px", border: "1px solid #E2E8F0", borderRadius: 7,
                          background: "white", fontSize: 12, cursor: "pointer", color: "#374151" }}>Cancel</button>
                      <button disabled={!importCsvBase64 || importTx.isPending}
                        onClick={() => importTx.mutate()}
                        style={{ padding: "7px 14px", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600,
                          background: "#1B3A6B", color: "white", cursor: "pointer",
                          opacity: !importCsvBase64 ? 0.5 : 1 }}>
                        {importTx.isPending ? "Importing..." : `Import ${importFileName || "file"}`}
                      </button>
                    </div>
                  </>
                ) : (
                  <>
                    <div style={{ display: "flex", gap: 10, marginBottom: 12 }}>
                      <div style={{ flex: 1, background: "#F0FDF4", borderRadius: 8, padding: "10px 12px" }}>
                        <div style={{ fontSize: 18, fontWeight: 800, color: "#166534" }}>{importResult.imported}</div>
                        <div style={{ fontSize: 10, color: "#166534" }}>Imported</div>
                      </div>
                      <div style={{ flex: 1, background: "#FFFBEB", borderRadius: 8, padding: "10px 12px" }}>
                        <div style={{ fontSize: 18, fontWeight: 800, color: "#92400E" }}>{importResult.skippedDuplicates}</div>
                        <div style={{ fontSize: 10, color: "#92400E" }}>Skipped (duplicate)</div>
                      </div>
                      <div style={{ flex: 1, background: importResult.failed > 0 ? "#FEF2F2" : "#F8FAFC", borderRadius: 8, padding: "10px 12px" }}>
                        <div style={{ fontSize: 18, fontWeight: 800, color: importResult.failed > 0 ? "#DC2626" : "#94A3B8" }}>{importResult.failed}</div>
                        <div style={{ fontSize: 10, color: importResult.failed > 0 ? "#DC2626" : "#94A3B8" }}>Failed</div>
                      </div>
                    </div>
                    {importResult.errors.length > 0 && (
                      <div style={{ maxHeight: 100, overflowY: "auto", background: "#FEF2F2", borderRadius: 7,
                        padding: "8px 10px", marginBottom: 12 }}>
                        {importResult.errors.map((e, i) => (
                          <div key={i} style={{ fontSize: 11, color: "#DC2626" }}>{e}</div>
                        ))}
                      </div>
                    )}
                    <div style={{ display: "flex", justifyContent: "flex-end" }}>
                      <button onClick={() => { setShowImport(false); setImportResult(null) }}
                        style={{ padding: "7px 14px", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600,
                          background: "#1B3A6B", color: "white", cursor: "pointer" }}>Done</button>
                    </div>
                  </>
                )}
              </div>
            )}

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
                  <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontSize: 14, fontWeight: 700,
                        color: tx.transactionType === "CREDIT" ? "#166534" : "#DC2626" }}>
                        {tx.transactionType === "CREDIT" ? "+" : "-"}{fmtR(tx.amount)}
                      </div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>Balance: {fmtR(tx.balanceAfter)}</div>
                    </div>
                    {tx.reconciled ? (
                      <span title="Reconciled" style={{ display: "flex", alignItems: "center", gap: 4,
                        fontSize: 11, fontWeight: 600, color: "#166534", background: "#F0FDF4",
                        padding: "4px 8px", borderRadius: 20 }}>
                        <CheckCircle size={11} /> Reconciled
                      </span>
                    ) : (
                      <button onClick={() => { setReconcileTx(tx); setReconcileMode("match"); setError("") }}
                        style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 11, fontWeight: 600,
                          color: "#7C3AED", background: "#F5F3FF", border: "1px solid #DDD6FE",
                          padding: "4px 10px", borderRadius: 20, cursor: "pointer" }}>
                        <Link2 size={11} /> Reconcile
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Reconcile Modal */}
      {reconcileTx && showTx && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "white", borderRadius: 16, padding: 24, width: 520, maxHeight: "80vh",
            overflowY: "auto" as const, boxShadow: "0 20px 60px rgba(0,0,0,0.25)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700 }}>Reconcile transaction</h3>
                <p style={{ margin: "3px 0 0", fontSize: 12, color: "#94A3B8" }}>
                  {reconcileTx.description} · {fmtDt(reconcileTx.transactionDate)} ·{" "}
                  <strong style={{ color: reconcileTx.transactionType === "CREDIT" ? "#166534" : "#DC2626" }}>
                    {reconcileTx.transactionType === "CREDIT" ? "+" : "-"}{fmtR(reconcileTx.amount)}
                  </strong>
                </p>
              </div>
              <button onClick={() => setReconcileTx(null)}
                style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={18} /></button>
            </div>

            <div style={{ display: "flex", gap: 6, marginBottom: 16 }}>
              <button onClick={() => setReconcileMode("match")}
                style={{ flex: 1, padding: "8px 12px", borderRadius: 8, border: "none", fontSize: 12, fontWeight: 600, cursor: "pointer",
                  background: reconcileMode === "match" ? "#7C3AED" : "#F1F5F9",
                  color: reconcileMode === "match" ? "white" : "#64748B" }}>
                <Link2 size={12} style={{ marginRight: 5, verticalAlign: -2 }} />Match existing
              </button>
              <button onClick={() => setReconcileMode("new")}
                style={{ flex: 1, padding: "8px 12px", borderRadius: 8, border: "none", fontSize: 12, fontWeight: 600, cursor: "pointer",
                  background: reconcileMode === "new" ? "#7C3AED" : "#F1F5F9",
                  color: reconcileMode === "new" ? "white" : "#64748B" }}>
                <FilePlus2 size={12} style={{ marginRight: 5, verticalAlign: -2 }} />Create new journal
              </button>
            </div>

            {error && (
              <div style={{ padding: "8px 10px", background: "#FEF2F2", borderRadius: 7,
                fontSize: 12, color: "#DC2626", marginBottom: 12 }}>{error}</div>
            )}

            {reconcileMode === "match" ? (
              candidatesLoading ? (
                <div style={{ padding: 30, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>Searching for matching journal entries...</div>
              ) : candidatesError ? (
                <div style={{ padding: 20, background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 9, textAlign: "center" as const }}>
                  <div style={{ fontSize: 13, color: "#DC2626", fontWeight: 600, marginBottom: 4 }}>Couldn't search for matches</div>
                  <div style={{ fontSize: 12, color: "#991B1B" }}>
                    {(candidatesErrorObj as any)?.response?.data?.message ?? "This bank account may not be linked to the Chart of Accounts yet — close this and use \"Link now\" first."}
                  </div>
                </div>
              ) : matchCandidates.length === 0 ? (
                <div style={{ padding: 30, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>
                  No matching journal lines found within 30 days.<br />Try "Create new journal" instead.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column" as const, gap: 8 }}>
                  {matchCandidates.map(c => (
                    <div key={c.journalLineId}
                      style={{ display: "flex", justifyContent: "space-between", alignItems: "center",
                        padding: "10px 12px", border: `1.5px solid ${c.exactMatch ? "#86EFAC" : "#E2E8F0"}`,
                        background: c.exactMatch ? "#F0FDF4" : "white", borderRadius: 9 }}>
                      <div>
                        <div style={{ fontSize: 12, fontWeight: 700, color: "#0F172A", display: "flex", alignItems: "center", gap: 6 }}>
                          {c.journalEntryNumber}
                          {c.exactMatch && <span style={{ fontSize: 9, fontWeight: 700, color: "#166534", background: "#DCFCE7", padding: "1px 6px", borderRadius: 10 }}>EXACT MATCH</span>}
                        </div>
                        <div style={{ fontSize: 11, color: "#64748B" }}>{c.description}</div>
                        <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtDt(c.entryDate)} · {fmtR(c.amount)}</div>
                      </div>
                      <button disabled={reconcileExisting.isPending}
                        onClick={() => reconcileExisting.mutate(c.journalLineId)}
                        style={{ padding: "6px 12px", border: "none", borderRadius: 7, fontSize: 11, fontWeight: 600,
                          background: "#166534", color: "white", cursor: "pointer" }}>
                        Link
                      </button>
                    </div>
                  ))}
                </div>
              )
            ) : (
              <div>
                <div style={{ fontSize: 12, color: "#64748B", marginBottom: 12 }}>
                  No existing journal explains this transaction? Categorize it directly — the bank's own account
                  is filled in automatically on the correct side.
                </div>
                <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 5 }}>
                  Other account (the {reconcileTx.transactionType === "CREDIT" ? "income" : "expense"} this represents) *
                </label>
                <select value={newJournalAccountId} onChange={e => setNewJournalAccountId(e.target.value)} style={{ ...inp, marginBottom: 12 }}>
                  <option value="">Select an account...</option>
                  {coaAccounts.map(a => (
                    <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>
                  ))}
                </select>
                <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 5 }}>
                  Description (optional — defaults to the transaction's own description)
                </label>
                <input value={newJournalDescription} onChange={e => setNewJournalDescription(e.target.value)}
                  placeholder={reconcileTx.description} style={{ ...inp, marginBottom: 16 }} />
                <div style={{ display: "flex", justifyContent: "flex-end" }}>
                  <button disabled={!newJournalAccountId || reconcileNew.isPending}
                    onClick={() => reconcileNew.mutate()}
                    style={{ padding: "8px 16px", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 700,
                      background: "#166534", color: "white", cursor: "pointer", opacity: !newJournalAccountId ? 0.5 : 1 }}>
                    {reconcileNew.isPending ? "Creating..." : "Create journal & reconcile"}
                  </button>
                </div>
              </div>
            )}
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
