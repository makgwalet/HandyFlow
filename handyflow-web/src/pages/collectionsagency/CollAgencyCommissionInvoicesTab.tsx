// src/pages/collectionsagency/CollAgencyCommissionInvoicesTab.tsx
//
// No standalone "create invoice" endpoint exists — confirmed in
// CollAgencyCommissionInvoiceController's own Javadoc: invoices are only
// ever created as part of CollAgencyTrustController's processRemittance()
// (see the Trust Ledger tab). This tab is read + record-payment only.
//
// CommissionInvoiceResponse's exact status enum values weren't directly
// confirmed (invoice.markSent() is called on creation, and
// recordPayment() presumably moves it toward paid) — so status is
// rendered as whatever string the backend returns, generically styled,
// rather than assuming a closed set of values that might not match.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"

interface CommissionInvoiceResponse {
  id: string; clientId: string; invoiceNumber: string; description: string | null
  invoiceDate: string; dueDate: string; subtotal: number; vatAmount: number; total: number
  amountPaid: number; balance: number; status: string; sentAt: string | null; paidAt: string | null
}

const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)
const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 11px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }

function badgeColor(status: string) {
  if (status === "PAID") return { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" }
  if (status.includes("PARTIAL")) return { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)" }
  if (status === "OVERDUE") return { bg: "var(--hf-danger-soft-strong)", fg: "var(--hf-danger-text-strong)" }
  return { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" }
}

function RecordPaymentModal({ invoice, onClose }: { invoice: CommissionInvoiceResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [amount, setAmount] = useState(String(invoice.balance))
  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/collections-agency/commission-invoices/${invoice.id}/payments`, { amount: parseFloat(amount) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-invoices", invoice.clientId] }); onClose() },
  })
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 26, width: 380 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>Record payment — {invoice.invoiceNumber}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={17} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "0 0 12px" }}>Outstanding balance: {fmtMoney(invoice.balance)}. Internal tracking only — does not post a second GL journal.</p>
        <input type="number" step="0.01" style={inputStyle} value={amount} onChange={e => setAmount(e.target.value)} />
        {save.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not record this payment"}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={!amount || save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: CA_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            {save.isPending ? "Recording…" : "Record payment"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function CollAgencyCommissionInvoicesTab({ clientId }: { clientId: string }) {
  const [selected, setSelected] = useState<CommissionInvoiceResponse | null>(null)
  const { data, isLoading } = useQuery<{ content: CommissionInvoiceResponse[] }>({
    queryKey: ["ca-invoices", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/clients/${clientId}/commission-invoices?size=50`)).data,
  })
  const invoices = data?.content ?? []

  return (
    <div>
      <p style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 14 }}>
        Commission invoices are created automatically when a remittance is processed (Trust Ledger tab) — there's no manual create here.
      </p>
      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
      ) : invoices.length === 0 ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No commission invoices yet.</p>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {invoices.map((inv, i) => {
            const colors = badgeColor(inv.status)
            return (
              <div key={inv.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{inv.invoiceNumber}</p>
                    <span style={{ fontSize: 10.5, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{inv.status}</span>
                  </div>
                  <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>{inv.description ?? `Invoiced ${inv.invoiceDate}`} · Due {inv.dueDate}</p>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 2px" }}>{fmtMoney(inv.total)}</p>
                    {inv.balance > 0 && <p style={{ fontSize: 11.5, color: "var(--hf-danger-text)", margin: 0 }}>{fmtMoney(inv.balance)} outstanding</p>}
                  </div>
                  {inv.balance > 0 && (
                    <button onClick={() => setSelected(inv)} style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "6px 12px", fontSize: 12, fontWeight: 600, color: CA_ACCENT, cursor: "pointer" }}>
                      Record payment
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
      {selected && <RecordPaymentModal invoice={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}
