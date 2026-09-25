// src/pages/collectionsagency/CollAgencyDebtorAccountsTab.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import CollAgencyDebtorAccountDetail from "./CollAgencyDebtorAccountDetail"

export interface DebtorAccountResponse {
  id: string; clientId: string; placementBatchId: string | null; accountReference: string | null
  debtorName: string; debtorIdNumber: string | null; debtorEmail: string | null; debtorPhone: string | null
  debtorAddress: string | null; originalCreditorName: string; originalDebtDate: string | null
  originalDebtAmount: number; currentBalance: number; status: string; assignedCollectorId: string | null
  placedDate: string; closedDate: string | null; notes: string | null
}

const STATUSES = ["PLACED", "IN_PROGRESS", "PAYMENT_PLAN_ACTIVE", "DISPUTED", "RECOVERED", "RETURNED_TO_CLIENT", "WRITTEN_OFF", "CLOSED"]
const TERMINAL = new Set(["RECOVERED", "RETURNED_TO_CLIENT", "WRITTEN_OFF", "CLOSED"])

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  PLACED:              { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-tertiary)" },
  IN_PROGRESS:         { bg: "var(--hf-info-soft-strong)", fg: "var(--hf-info-text)" },
  PAYMENT_PLAN_ACTIVE: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)" },
  DISPUTED:            { bg: "var(--hf-danger-soft-strong)", fg: "var(--hf-danger-text-strong)" },
  RECOVERED:           { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" },
  RETURNED_TO_CLIENT:  { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" },
  WRITTEN_OFF:         { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" },
  CLOSED:              { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" },
}

const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)

export default function CollAgencyDebtorAccountsTab({ clientId }: { clientId: string }) {
  const [statusFilter, setStatusFilter] = useState<string>("")
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data, isLoading } = useQuery<{ content: DebtorAccountResponse[] }>({
    queryKey: ["ca-debtor-accounts", clientId, statusFilter],
    queryFn: async () => (await apiClient.get(
      `/api/v1/collections-agency/clients/${clientId}/debtor-accounts?size=100${statusFilter ? `&status=${statusFilter}` : ""}`
    )).data,
  })
  const accounts = data?.content ?? []

  if (selectedId) {
    return <CollAgencyDebtorAccountDetail accountId={selectedId} clientId={clientId} onBack={() => setSelectedId(null)} />
  }

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
        <button onClick={() => setStatusFilter("")}
          style={{ padding: "6px 12px", borderRadius: 20, border: "1px solid var(--hf-border)", background: statusFilter === "" ? "var(--hf-inverse-surface)" : "var(--hf-surface)", color: statusFilter === "" ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
          All
        </button>
        {STATUSES.map(s => (
          <button key={s} onClick={() => setStatusFilter(s)}
            style={{ padding: "6px 12px", borderRadius: 20, border: "1px solid var(--hf-border)", background: statusFilter === s ? "var(--hf-inverse-surface)" : "var(--hf-surface)", color: statusFilter === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
            {s.replace(/_/g, " ")}
          </button>
        ))}
      </div>

      <p style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 12 }}>
        {accounts.length} account{accounts.length === 1 ? "" : "s"} — placed via a placement batch (see the Placement Batches tab to add new accounts)
      </p>

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
      ) : accounts.length === 0 ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No debtor accounts{statusFilter ? ` with status ${statusFilter}` : ""} yet.</p>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {accounts.map((a, i) => {
            const colors = STATUS_COLORS[a.status] ?? { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" }
            return (
              <div key={a.id} onClick={() => setSelectedId(a.id)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", cursor: "pointer" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{a.debtorName}</p>
                    <span style={{ fontSize: 10.5, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{a.status.replace(/_/g, " ")}</span>
                  </div>
                  <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>
                    {a.accountReference ? `${a.accountReference} · ` : ""}Original creditor: {a.originalCreditorName}
                  </p>
                </div>
                <div style={{ textAlign: "right" }}>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: "0 0 2px" }}>Balance / Original</p>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>
                    {fmtMoney(a.currentBalance)} <span style={{ fontWeight: 400, color: "var(--hf-text-disabled)" }}>/ {fmtMoney(a.originalDebtAmount)}</span>
                  </p>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export { STATUSES, TERMINAL, STATUS_COLORS, fmtMoney }
