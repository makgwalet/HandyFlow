// src/pages/accounting/ChartOfAccountsTab.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BookOpen, AlertCircle } from "lucide-react"

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

export default function ChartOfAccountsTab() {
  const { data: accounts = [], isLoading, isError } = useQuery<Account[]>({
    queryKey: ["coa"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/accounts")
      return (res.data?.data ?? res.data) as Account[]
    },
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
    </div>
  )
}
