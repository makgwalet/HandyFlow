// src/pages/collectionsagency/CollAgencyClientDetail.tsx
//
// Drill-down for one creditor client. Every sub-resource in this module
// (debtor accounts, placement batches, trust ledger, commission
// invoices, portal access) is confirmed to live under
// /clients/{clientId}/... in the real backend, so this is a genuine
// client-scoped workspace, not just a display convenience.
//
// PERMISSION NOTE: processing a remittance and hard-deleting a client
// are COLLECTIONSAGENCY_ADMIN-only on the backend (confirmed via
// @PreAuthorize). This file does not hide those actions from
// non-admin users client-side — I don't have visibility into this
// app's existing permission-check hook/pattern (e.g. whether
// useAuthStore exposes a permissions array and a hasPermission()
// helper) and didn't want to guess at one. The backend enforces the
// real gate either way (a non-admin gets a 403), so this is safe, just
// not as polished as it could be — wire in a permission check here to
// match your app's convention if you have one.
import { useState } from "react"
import { ArrowLeft, Users2, Package, Wallet, Receipt, KeyRound } from "lucide-react"
import { CA_ACCENT } from "./constants"
import type { ClientResponse } from "./CollAgencyClientsTab"
import CollAgencyDebtorAccountsTab from "./CollAgencyDebtorAccountsTab"
import CollAgencyPlacementBatchesTab from "./CollAgencyPlacementBatchesTab"
import CollAgencyTrustLedgerTab from "./CollAgencyTrustLedgerTab"
import CollAgencyCommissionInvoicesTab from "./CollAgencyCommissionInvoicesTab"
import CollAgencyPortalAccessTab from "./CollAgencyPortalAccessTab"

type SubTab = "debtors" | "batches" | "trust" | "invoices" | "portal"

const SUB_TABS: { id: SubTab; label: string; icon: React.ElementType }[] = [
  { id: "debtors",  label: "Debtor Accounts",     icon: Users2   },
  { id: "batches",  label: "Placement Batches",   icon: Package  },
  { id: "trust",    label: "Trust Ledger",        icon: Wallet   },
  { id: "invoices", label: "Commission Invoices", icon: Receipt  },
  { id: "portal",   label: "Portal Access",       icon: KeyRound },
]

const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)

export default function CollAgencyClientDetail({ clientId, client, onBack }: { clientId: string; client?: ClientResponse; onBack: () => void }) {
  const [sub, setSub] = useState<SubTab>("debtors")

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 16, padding: 0 }}>
        <ArrowLeft size={15} /> Back to clients
      </button>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
        <div>
          <h2 style={{ fontSize: 19, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>{client?.tradingName ?? "Client"}</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>
            {client?.commissionRatePct}% commission{client?.registrationNumber ? ` · Reg. ${client.registrationNumber}` : ""}
          </p>
        </div>
        {client && (
          <div style={{ textAlign: "right" }}>
            <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 2px" }}>Trust held</p>
            <p style={{ fontSize: 20, fontWeight: 800, color: "#059669", margin: 0 }}>{fmtMoney(client.trustBalance)}</p>
          </div>
        )}
      </div>

      <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 22, overflowX: "auto" }}>
        {SUB_TABS.map(t => {
          const Icon = t.icon
          const active = sub === t.id
          return (
            <button key={t.id} onClick={() => setSub(t.id)}
              style={{
                display: "flex", alignItems: "center", gap: 6, padding: "9px 14px",
                background: "none", border: "none", whiteSpace: "nowrap",
                borderBottom: active ? `2px solid ${CA_ACCENT}` : "2px solid transparent",
                color: active ? CA_ACCENT : "#64748B",
                fontWeight: active ? 600 : 400, fontSize: 12.5, cursor: "pointer",
                marginBottom: -1,
              }}>
              <Icon size={13} />{t.label}
            </button>
          )
        })}
      </div>

      {sub === "debtors"  && <CollAgencyDebtorAccountsTab clientId={clientId} />}
      {sub === "batches"  && <CollAgencyPlacementBatchesTab clientId={clientId} />}
      {sub === "trust"    && <CollAgencyTrustLedgerTab clientId={clientId} client={client} />}
      {sub === "invoices" && <CollAgencyCommissionInvoicesTab clientId={clientId} />}
      {sub === "portal"   && <CollAgencyPortalAccessTab clientId={clientId} />}
    </div>
  )
}
