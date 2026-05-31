import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Search, ChevronDown, ChevronRight } from "lucide-react"

interface Account {
  id: string
  accountCode: string
  accountName: string
  accountType: string
  accountSubtype: string
  isSystem: boolean
  openingBalance: number
  description: string
}

const TYPE_COLORS: Record<string, { color: string; bg: string }> = {
  ASSET:     { color: "#1D4ED8", bg: "#EFF6FF" },
  LIABILITY: { color: "#B45309", bg: "#FFFBEB" },
  EQUITY:    { color: "#7C3AED", bg: "#F5F3FF" },
  INCOME:    { color: "#166534", bg: "#DCFCE7" },
  EXPENSE:   { color: "#DC2626", bg: "#FEF2F2" },
}

const TYPE_ORDER = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"]

export default function ChartOfAccountsTab() {
  const [search, setSearch]       = useState("")
  const [expanded, setExpanded]   = useState<Set<string>>(new Set(TYPE_ORDER))

  const { data: accounts = [], isLoading } = useQuery<Account[]>({
    queryKey: ["acc-accounts"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/accounts")
      return res.data
    },
  })

  const filtered = accounts.filter(a =>
    !search ||
    a.accountCode.toLowerCase().includes(search.toLowerCase()) ||
    a.accountName.toLowerCase().includes(search.toLowerCase())
  )

  const grouped = TYPE_ORDER.reduce((acc, type) => {
    acc[type] = filtered.filter(a => a.accountType === type)
    return acc
  }, {} as Record<string, Account[]>)

  const toggle = (type: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(type) ? next.delete(type) : next.add(type)
      return next
    })
  }

  const fmtR = (n: number) => n ? `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  if (isLoading) return (
    <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading accounts...</div>
  )

  return (
    <div>
      {/* Summary stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
        {TYPE_ORDER.map(type => {
          const style = TYPE_COLORS[type] || { color: "#475569", bg: "#F8FAFC" }
          const count = grouped[type]?.length || 0
          return (
            <div key={type} style={{ flex: 1, background: style.bg, borderRadius: 10, padding: "12px 16px", border: `1px solid ${style.bg}` }}>
              <div style={{ fontSize: 20, fontWeight: 700, color: style.color }}>{count}</div>
              <div style={{ fontSize: 12, color: style.color, marginTop: 2, opacity: 0.8 }}>{type}</div>
            </div>
          )
        })}
      </div>

      {/* Search */}
      <div style={{ position: "relative", marginBottom: 20, maxWidth: 340 }}>
        <Search size={15} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
        <input
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search accounts..."
          style={{ width: "100%", padding: "9px 12px 9px 36px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }}
        />
      </div>

      {/* Grouped accounts */}
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {TYPE_ORDER.map(type => {
          const accs = grouped[type] || []
          const style = TYPE_COLORS[type] || { color: "#475569", bg: "#F8FAFC" }
          const isOpen = expanded.has(type)

          return (
            <div key={type} style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
              {/* Group header */}
              <div
                onClick={() => toggle(type)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", background: style.bg, cursor: "pointer" }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  {isOpen ? <ChevronDown size={16} color={style.color} /> : <ChevronRight size={16} color={style.color} />}
                  <span style={{ fontWeight: 700, fontSize: 13, color: style.color, letterSpacing: "0.05em" }}>{type}</span>
                  <span style={{ background: style.color, color: "#fff", borderRadius: 20, padding: "1px 8px", fontSize: 11 }}>{accs.length}</span>
                </div>
              </div>

              {/* Account rows */}
              {isOpen && accs.length > 0 && (
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ background: "#F8FAFC" }}>
                      <th style={th}>Code</th>
                      <th style={th}>Account Name</th>
                      <th style={th}>Subtype</th>
                      <th style={{ ...th, textAlign: "right" }}>Opening Balance</th>
                      <th style={th}>Type</th>
                    </tr>
                  </thead>
                  <tbody>
                    {accs.map((acc, i) => (
                      <tr key={acc.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                        <td style={td}>
                          <span style={{ fontFamily: "monospace", fontSize: 13, color: "#475569" }}>{acc.accountCode}</span>
                        </td>
                        <td style={td}>
                          <span style={{ fontWeight: 500, color: "#0F172A" }}>{acc.accountName}</span>
                          {acc.isSystem && (
                            <span style={{ marginLeft: 6, fontSize: 10, color: "#94A3B8", background: "#F1F5F9", padding: "1px 6px", borderRadius: 4 }}>SYSTEM</span>
                          )}
                        </td>
                        <td style={td}>
                          <span style={{ fontSize: 12, color: "#64748B" }}>{acc.accountSubtype || "—"}</span>
                        </td>
                        <td style={{ ...td, textAlign: "right" }}>
                          <span style={{ fontSize: 13, color: "#0F172A" }}>{fmtR(acc.openingBalance)}</span>
                        </td>
                        <td style={td}>
                          <span style={{ background: style.bg, color: style.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                            {type}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              {isOpen && accs.length === 0 && (
                <div style={{ padding: "16px 20px", color: "#94A3B8", fontSize: 13 }}>No accounts in this category.</div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

const th: React.CSSProperties = {
  padding: "8px 16px", textAlign: "left", fontSize: 11,
  fontWeight: 600, color: "#64748B", letterSpacing: "0.05em",
  borderBottom: "1px solid #E2E8F0",
}
const td: React.CSSProperties = {
  padding: "10px 16px", fontSize: 13,
  borderBottom: "1px solid #F1F5F9",
}
