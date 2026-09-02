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
const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 11px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }

function badgeColor(status: string) {
  if (status === "PAID") return { bg: "#DCFCE7", fg: "#166534" }
  if (status.includes("PARTIAL")) return { bg: "#FEF3C7", fg: "#92400E" }
  if (status === "OVERDUE") return { bg: "#FEE2E2", fg: "#991B1B" }
  return { bg: "#F1F5F9", fg: "#64748B" }
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
      <div style={{ background: "#fff", borderRadius: 16, padding: 26, width: 380 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 700, color: "#0F172A", margin: 0 }}>Record payment — {invoice.invoiceNumber}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={17} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 12px" }}>Outstanding balance: {fmtMoney(invoice.balance)}. Internal tracking only — does not post a second GL journal.</p>
        <input type="number" step="0.01" style={inputStyle} value={amount} onChange={e => setAmount(e.target.value)} />
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not record this payment"}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={!amount || save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: CA_ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
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
      <p style={{ fontSize: 12, color: "#94A3B8", marginBottom: 14 }}>
        Commission invoices are created automatically when a remittance is processed (Trust Ledger tab) — there's no manual create here.
      </p>
      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : invoices.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No commission invoices yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {invoices.map((inv, i) => {
            const colors = badgeColor(inv.status)
            return (
              <div key={inv.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{inv.invoiceNumber}</p>
                    <span style={{ fontSize: 10.5, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{inv.status}</span>
                  </div>
                  <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>{inv.description ?? `Invoiced ${inv.invoiceDate}`} · Due {inv.dueDate}</p>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: "0 0 2px" }}>{fmtMoney(inv.total)}</p>
                    {inv.balance > 0 && <p style={{ fontSize: 11.5, color: "#DC2626", margin: 0 }}>{fmtMoney(inv.balance)} outstanding</p>}
                  </div>
                  {inv.balance > 0 && (
                    <button onClick={() => setSelected(inv)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "6px 12px", fontSize: 12, fontWeight: 600, color: CA_ACCENT, cursor: "pointer" }}>
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
