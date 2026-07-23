// src/pages/accounting/ChartOfAccountsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BookOpen, AlertCircle, Plus, X } from "lucide-react"

interface Account {
  id: string; accountCode: string; accountName: string
  accountType: string; accountSubtype: string; isSystem: boolean
  openingBalance: number; description: string | null
}

const TYPE_COLOR: Record<string, { bg: string; color: string }> = {
  ASSET:     { bg: "#EFF6FF", color: "#1D4ED8" },
  LIABILITY: { bg: "#FEF2F2", color: "#DC2626" },
  EQUITY:    { bg: "#F3E8FF", color: "#7C3AED" },
  INCOME:    { bg: "#F0FDF4", color: "#166534" },
  EXPENSE:   { bg: "#FEF3C7", color: "#92400E" },
}

const fmtR = (n: number) =>
  n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

const inp: React.CSSProperties = {
  width: "100%", padding: "8px 12px", border: "1.5px solid #E2E8F0",
  borderRadius: 8, fontSize: 13, outline: "none", boxSizing: "border-box",
}

export default function ChartOfAccountsTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState({ accountCode: "", accountName: "", accountType: "EXPENSE", accountSubtype: "", description: "" })
  const [error, setError] = useState("")

  const { data: accounts = [], isLoading, isError } = useQuery<Account[]>({
    queryKey: ["coa"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/accounts")
      return (res.data?.data ?? res.data) as Account[]
    },
  })

  const createAccount = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/accounts", form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["coa"] })
      setShowCreate(false); setError("")
      setForm({ accountCode: "", accountName: "", accountType: "EXPENSE", accountSubtype: "", description: "" })
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create account"),
  })

  const grouped = accounts.reduce((acc, a) => {
    if (!acc[a.accountType]) acc[a.accountType] = []
    acc[a.accountType].push(a)
    return acc
  }, {} as Record<string, Account[]>)

  const types = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"]

  if (isLoading) return <div style={{ padding: 60, textAlign: "center", color: "#94A3B8" }}>Loading chart of accounts...</div>
  if (isError)   return <div style={{ padding: 60, textAlign: "center", color: "#DC2626" }}>Failed to load accounts</div>

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Chart of Accounts</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: "3px 0 0" }}>
            Standard South African chart of accounts — {accounts.length} accounts across {types.length} types
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          {types.map(t => {
            const c = TYPE_COLOR[t]
            return (
              <span key={t} style={{ fontSize: 11, fontWeight: 600, background: c.bg, color: c.color,
                padding: "4px 10px", borderRadius: 20 }}>
                {t} ({grouped[t]?.length ?? 0})
              </span>
            )
          })}
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, background: "#0D9488", color: "white",
            border: "none", borderRadius: 9, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer", marginLeft: 12 }}>
          <Plus size={14} /> Add Account
        </button>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        {types.map(type => {
          const accs = grouped[type] ?? []
          if (accs.length === 0) return null
          const c = TYPE_COLOR[type]
          return (
            <div key={type} style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              <div style={{ background: c.bg, padding: "10px 20px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <BookOpen size={14} color={c.color} />
                  <span style={{ fontSize: 13, fontWeight: 700, color: c.color }}>{type}</span>
                  <span style={{ fontSize: 12, color: c.color, opacity: 0.7 }}>({accs.length} accounts)</span>
                </div>
                <span style={{ fontSize: 13, fontWeight: 700, color: c.color }}>
                  {fmtR(accs.reduce((s, a) => s + (a.openingBalance ?? 0), 0))} opening
                </span>
              </div>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #F1F5F9" }}>
                    {["Code", "Account Name", "Subtype", "Opening Balance"].map(h => (
                      <th key={h} style={{ textAlign: "left", padding: "8px 16px", fontSize: 11,
                        fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {accs.map((acc, i) => (
                    <tr key={acc.id}
                      style={{ borderBottom: i < accs.length - 1 ? "1px solid #F8FAFC" : "none" }}
                      onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")}
                      onMouseLeave={e => (e.currentTarget.style.background = "white")}>
                      <td style={{ padding: "10px 16px" }}>
                        <span style={{ fontFamily: "monospace", fontSize: 13, fontWeight: 600, color: c.color }}>
                          {acc.accountCode}
                        </span>
                      </td>
                      <td style={{ padding: "10px 16px" }}>
                        <span style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{acc.accountName}</span>
                        {acc.description && (
                          <p style={{ fontSize: 11, color: "#94A3B8", margin: "1px 0 0" }}>{acc.description}</p>
                        )}
                      </td>
                      <td style={{ padding: "10px 16px" }}>
                        <span style={{ fontSize: 11, background: "#F1F5F9", color: "#475569",
                          padding: "2px 8px", borderRadius: 10, fontWeight: 500 }}>
                          {acc.accountSubtype}
                        </span>
                      </td>
                      <td style={{ padding: "10px 16px", fontSize: 13, fontWeight: 600,
                        color: acc.openingBalance > 0 ? "#166534" : "#0F172A" }}>
                        {fmtR(acc.openingBalance)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        })}
      </div>

      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "white", borderRadius: 16, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>Add Custom Account</h3>
              <button onClick={() => setShowCreate(false)}
                style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={18} /></button>
            </div>
            <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 16px" }}>
              The 47 seeded accounts cover most cases — this is for the one or two a real business always ends up needing.
            </p>
            <div style={{ display: "flex", gap: 12, marginBottom: 14 }}>
              <div style={{ flex: 1 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Code *</label>
                <input value={form.accountCode} onChange={e => setForm(f => ({ ...f, accountCode: e.target.value }))}
                  placeholder="5250" style={inp} />
              </div>
              <div style={{ flex: 2 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Name *</label>
                <input value={form.accountName} onChange={e => setForm(f => ({ ...f, accountName: e.target.value }))}
                  placeholder="Security Callout Fees" style={inp} />
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Type *</label>
              <div style={{ display: "flex", gap: 6 }}>
                {types.map(t => {
                  const c = TYPE_COLOR[t]
                  const active = form.accountType === t
                  return (
                    <button key={t} type="button" onClick={() => setForm(f => ({ ...f, accountType: t }))}
                      style={{ flex: 1, padding: "7px 8px", borderRadius: 8, fontSize: 11, fontWeight: 700, cursor: "pointer",
                        border: `1.5px solid ${active ? c.color : "#E2E8F0"}`,
                        background: active ? c.bg : "white", color: active ? c.color : "#94A3B8" }}>
                      {t}
                    </button>
                  )
                })}
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Subtype (optional)</label>
              <input value={form.accountSubtype} onChange={e => setForm(f => ({ ...f, accountSubtype: e.target.value }))}
                placeholder="e.g. OPERATING" style={inp} />
            </div>
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "#374151", marginBottom: 5 }}>Description (optional)</label>
              <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} style={inp} />
            </div>
            {error && (
              <div style={{ padding: "8px 12px", background: "#FEF2F2", borderRadius: 8,
                fontSize: 12, color: "#DC2626", marginBottom: 12 }}>{error}</div>
            )}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setShowCreate(false)}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "white", fontSize: 13, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button disabled={createAccount.isPending || !form.accountCode || !form.accountName}
                onClick={() => createAccount.mutate()}
                style={{ padding: "9px 20px", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700,
                  background: "#0D9488", color: "white", cursor: "pointer",
                  opacity: (!form.accountCode || !form.accountName) ? 0.5 : 1 }}>
                {createAccount.isPending ? "Creating..." : "Create Account"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
