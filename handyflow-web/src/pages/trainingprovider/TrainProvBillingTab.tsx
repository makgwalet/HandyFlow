// src/pages/trainingprovider/TrainProvBillingTab.tsx
//
// Per-client invoicing — confirmed via TrainProvBillingController:
// GET /clients/{clientId}/invoices, GET /invoices/{id},
// POST /clients/{clientId}/invoices/generate (ADMIN-only,
// GenerateInvoiceRequest{periodEnd}), POST /invoices/{id}/send
// (a SEPARATE step here — unlike Warehousing, where generate already
// marks the invoice SENT — confirmed by TrainProvBillingController
// having its own distinct markSent endpoint), POST /invoices/{id}/payments
// (ADMIN-only, RecordPaymentRequest{amount}), GET /invoices/{id}/pdf.
//
// Real GL posting happens on generate (AR debit 1100 / Revenue credit
// 4000 via AccountingFacade, try/catch-swallow) — confirmed in the
// status doc's own §2.3. VAT is computed server-side; this tab doesn't
// assume a rate, it just renders whatever vatAmount comes back.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { FileText, X, Download, CreditCard, Send } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"

interface InvoiceResponse {
  id: string; clientId: string; invoiceNumber: string; periodStart: string; periodEnd: string
  issueDate: string; dueDate: string; delegateCount: number; subtotal: number; vatAmount: number
  total: number; amountPaid: number; balance: number; status: "DRAFT" | "SENT" | "PARTIAL" | "PAID"; createdAt: string
}
interface InvoicePage { content: InvoiceResponse[] }

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
    mutationFn: async () => apiClient.post(`/api/v1/training-provider/clients/${clientId}/invoices/generate`, { periodEnd }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["trainprov-invoices", clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 400 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Generate invoice</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12, color: "#94A3B8", marginBottom: 14 }}>
          Bills every not-yet-invoiced billable enrollment for this client through the date below, and posts revenue to the GL immediately. The invoice is created as DRAFT — use "Send" afterwards to mark it sent.
        </p>
        <div><label style={labelStyle}>Bill through *</label><input type="date" style={inputStyle} value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} /></div>
        {generate.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(generate.error as any)?.response?.data?.message ?? "Could not generate this invoice"}</p>}
        <button onClick={() => generate.mutate()} disabled={!periodEnd || generate.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!periodEnd || generate.isPending) ? 0.6 : 1 }}>
          {generate.isPending ? "Generating…" : "Generate invoice"}
        </button>
      </div>
    </div>
  )
}

function PaymentModal({ invoice, onClose }: { invoice: InvoiceResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [amount, setAmount] = useState(invoice.balance.toString())

  const record = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/training-provider/invoices/${invoice.id}/payments`, { amount: parseFloat(amount) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["trainprov-invoices", invoice.clientId] }); onClose() },
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
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!amount || record.isPending) ? 0.6 : 1 }}>
          {record.isPending ? "Recording…" : "Record payment"}
        </button>
      </div>
    </div>
  )
}

export default function TrainProvBillingTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const [showGenerate, setShowGenerate] = useState(false)
  const [paying, setPaying] = useState<InvoiceResponse | null>(null)

  const { data, isLoading } = useQuery<InvoicePage>({
    queryKey: ["trainprov-invoices", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/training-provider/clients/${clientId}/invoices?size=24`)).data,
  })
  const invoices = data?.content ?? []

  const send = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/invoices/${id}/send`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-invoices", clientId] }),
  })

  const downloadPdf = async (inv: InvoiceResponse) => {
    const res = await apiClient.get(`/api/v1/training-provider/invoices/${inv.id}/pdf`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement("a")
    a.href = url; a.download = `${inv.invoiceNumber}.pdf`
    document.body.appendChild(a); a.click(); a.remove()
    window.URL.revokeObjectURL(url)
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{invoices.length} invoice{invoices.length === 1 ? "" : "s"}</p>
        {/* ADMIN-only server-side — not hidden client-side, real gate is the backend @PreAuthorize */}
        <button onClick={() => setShowGenerate(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: TRAINPROV_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <FileText size={15} /> Generate invoice
        </button>
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
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{inv.periodStart} → {inv.periodEnd} · Due {inv.dueDate} · {inv.delegateCount} delegate{inv.delegateCount === 1 ? "" : "s"}</p>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{fmtMoney(inv.total)}</p>
                    {inv.balance > 0 && <p style={{ fontSize: 11, color: "#D97706", margin: 0 }}>{fmtMoney(inv.balance)} outstanding</p>}
                  </div>
                  <button onClick={() => downloadPdf(inv)} title="Download PDF" style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <Download size={14} color="#64748B" />
                  </button>
                  {inv.status === "DRAFT" && (
                    <button onClick={() => send.mutate(inv.id)} title="Send to client" style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                      <Send size={14} color={TRAINPROV_ACCENT} />
                    </button>
                  )}
                  {inv.balance > 0 && inv.status !== "DRAFT" && (
                    <button onClick={() => setPaying(inv)} title="Record payment" style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                      <CreditCard size={14} color={TRAINPROV_ACCENT} />
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
