// src/pages/warehousing/WhseBillingInvoicesTab.tsx
//
// Financially-critical actions confirmed via WhseBillingController source:
// generateInvoice is ADMIN-only (creates the invoice AND immediately
// posts real GL revenue — WhseBillingService.generateInvoice() calls
// invoice.markSent() right after create(), so a generated invoice is
// already SENT, there's no separate draft->send step here unlike
// legalpractice). recordPayment is MANAGE/ADMIN and is confirmed
// "internal tracking only — does not post a second GL journal." VAT is
// hardcoded to R0 server-side pending a confirmed SA VAT treatment (see
// WhseBillingService's own inline comment) — surfaced here, not hidden.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { FileText, X, Download, CreditCard } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface BillingInvoiceResponse {
  id: string; clientId: string; invoiceNumber: string; periodStart: string; periodEnd: string
  invoiceDate: string; dueDate: string; storageFee: number; handlingFee: number; vatAmount: number
  subtotal: number; total: number; amountPaid: number; balance: number
  status: "DRAFT" | "SENT" | "PARTIAL" | "PAID"; sentAt: string | null; paidAt: string | null
}
interface InvoicePage { content: BillingInvoiceResponse[] }

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  DRAFT: { bg: "#F1F5F9", fg: "#64748B" }, SENT: { bg: "#DBEAFE", fg: "#1D4ED8" },
  PARTIAL: { bg: "#FEF3C7", fg: "#92400E" }, PAID: { bg: "#DCFCE7", fg: "#166534" },
}
const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function GenerateModal({ clientId, onClose }: { clientId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [periodEnd, setPeriodEnd] = useState(new Date().toISOString().slice(0, 10))

  const generate = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/clients/${clientId}/billing-invoices/generate`, { periodEnd }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-invoices", clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 400 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Generate billing invoice</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12, color: "#94A3B8", marginBottom: 14 }}>
          Bills storage + handling fees from the end of the last invoiced period (or client onboarding, if this is the first invoice) through the date below — and posts revenue to the GL immediately. This can't be undone from here.
        </p>
        <div><label style={labelStyle}>Bill through *</label><input type="date" style={inputStyle} value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} /></div>
        {generate.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(generate.error as any)?.response?.data?.message ?? "Could not generate this invoice"}</p>}
        <button onClick={() => generate.mutate()} disabled={!periodEnd || generate.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!periodEnd || generate.isPending) ? 0.6 : 1 }}>
          {generate.isPending ? "Generating…" : "Generate & issue invoice"}
        </button>
      </div>
    </div>
  )
}

function PaymentModal({ invoice, onClose }: { invoice: BillingInvoiceResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [amount, setAmount] = useState(invoice.balance.toString())

  const record = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/billing-invoices/${invoice.id}/payments`, { amount: parseFloat(amount) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-invoices", invoice.clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 380 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Record payment</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12.5, color: "#64748B", marginBottom: 14 }}>{invoice.invoiceNumber} — {fmtMoney(invoice.balance)} outstanding</p>
        <div><label style={labelStyle}>Amount *</label><input type="number" step="0.01" style={inputStyle} value={amount} onChange={e => setAmount(e.target.value)} /></div>
        {record.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(record.error as any)?.response?.data?.message ?? "Could not record this payment"}</p>}
        <button onClick={() => record.mutate()} disabled={!amount || record.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!amount || record.isPending) ? 0.6 : 1 }}>
          {record.isPending ? "Recording…" : "Record payment"}
        </button>
      </div>
    </div>
  )
}

export default function WhseBillingInvoicesTab({ clientId }: { clientId: string }) {
  const [showGenerate, setShowGenerate] = useState(false)
  const [paying, setPaying] = useState<BillingInvoiceResponse | null>(null)

  const { data, isLoading } = useQuery<InvoicePage>({
    queryKey: ["whse-invoices", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/billing-invoices?size=24`)).data,
  })
  const invoices = data?.content ?? []

  const downloadStatement = async () => {
    const res = await apiClient.get(`/api/v1/warehousing/clients/${clientId}/inventory-statement/pdf`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement("a")
    a.href = url
    a.download = "inventory-statement.pdf"
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{invoices.length} invoice{invoices.length === 1 ? "" : "s"}</p>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={downloadStatement} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid #E2E8F0", color: "#64748B", borderRadius: 8, padding: "9px 14px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}>
            <Download size={14} /> Inventory statement
          </button>
          {/* ADMIN-only server-side — not hidden client-side, see this tab's own header note */}
          <button onClick={() => setShowGenerate(true)}
            style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <FileText size={15} /> Generate invoice
          </button>
        </div>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : invoices.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No invoices generated yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {invoices.map((inv, i) => {
            const colors = STATUS_COLORS[inv.status] ?? { bg: "#F1F5F9", fg: "#64748B" }
            return (
              <div key={inv.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{inv.invoiceNumber}</p>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{inv.status}</span>
                  </div>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{inv.periodStart} → {inv.periodEnd} · Due {inv.dueDate}</p>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{fmtMoney(inv.total)}</p>
                    {inv.balance > 0 && <p style={{ fontSize: 11, color: "#D97706", margin: 0 }}>{fmtMoney(inv.balance)} outstanding</p>}
                  </div>
                  {inv.balance > 0 && (
                    <button onClick={() => setPaying(inv)} title="Record payment"
                      style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                      <CreditCard size={14} color={WHSE_ACCENT} />
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showGenerate && <GenerateModal clientId={clientId} onClose={() => setShowGenerate(false)} />}
      {paying && <PaymentModal invoice={paying} onClose={() => setPaying(null)} />}
    </div>
  )
}
