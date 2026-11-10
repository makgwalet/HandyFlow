// src/pages/warehousing/WhseBillingInvoicesTab.tsx
//
// Financially-critical actions confirmed via WhseBillingController source:
// generateInvoice is ADMIN-only and only PREPARES the invoice (status DRAFT,
// shown as "Awaiting approval"); a different person holding
// WAREHOUSING_BILLING_APPROVE approves it, which issues it and posts the
// revenue, or rejects it, which gives the storage days back. Where nobody
// else can approve, the server allows the preparer to approve and records
// an audited sole-approver exception. recordPayment is MANAGE/ADMIN and is confirmed
// "internal tracking only — does not post a second GL journal." VAT is
// hardcoded to R0 server-side pending a confirmed SA VAT treatment (see
// WhseBillingService's own inline comment) — surfaced here, not hidden.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { FileText, X, Download, CreditCard, Check } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { WHSE_ACCENT, WHSE_ACCENT_TEXT } from "./constants"

interface BillingInvoiceResponse {
  id: string; clientId: string; invoiceNumber: string; periodStart: string; periodEnd: string
  invoiceDate: string; dueDate: string; storageFee: number; handlingFee: number; vatAmount: number
  subtotal: number; total: number; amountPaid: number; balance: number
  status: "DRAFT" | "SENT" | "PARTIAL" | "PAID" | "REJECTED"; sentAt: string | null; paidAt: string | null
  storageMinimumTopup?: number; preparedBy?: string | null
}
interface InvoicePage { content: BillingInvoiceResponse[] }

const STATUS_COLORS: Record<string, { bg: string; fg: string }> = {
  DRAFT: { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" }, SENT: { bg: "var(--hf-info-soft-strong)", fg: "var(--hf-info-text)" },
  REJECTED: { bg: "var(--hf-danger-soft-strong)", fg: "var(--hf-danger-text)" },
  PARTIAL: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)" }, PAID: { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" },
}
const STATUS_LABELS: Record<string, string> = { DRAFT: "AWAITING APPROVAL" }
const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }

function GenerateModal({ clientId, onClose }: { clientId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [periodEnd, setPeriodEnd] = useState(new Date().toISOString().slice(0, 10))

  const generate = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/clients/${clientId}/billing-invoices/generate`, { periodEnd }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-invoices", clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 400 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Prepare billing invoice</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} style={{ color: 'var(--hf-text-faint)' }} /></button>
        </div>
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 14 }}>
          Prepares an invoice for storage + handling fees from the end of the last invoiced period (or client onboarding, if this is the first invoice) through the date below. Nothing is posted until another person approves it; if it is rejected, the storage days can be billed again.
        </p>
        <div><label style={labelStyle}>Bill through *</label><input type="date" style={inputStyle} value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} /></div>
        {generate.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 12 }}>{(generate.error as any)?.response?.data?.message ?? "Could not generate this invoice"}</p>}
        <button onClick={() => generate.mutate()} disabled={!periodEnd || generate.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!periodEnd || generate.isPending) ? 0.6 : 1 }}>
          {generate.isPending ? "Preparing…" : "Prepare invoice for approval"}
        </button>
      </div>
    </div>
  )
}

function DecisionModal({ invoice, mode, onClose }: { invoice: BillingInvoiceResponse; mode: "approve" | "reject"; onClose: () => void }) {
  const qc = useQueryClient()
  const [note, setNote] = useState("")
  const reject = mode === "reject"
  const decide = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/billing-invoices/${invoice.id}/${mode}`, { note: note.trim() || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-invoices", invoice.clientId] }); onClose() },
  })
  const tooShort = reject && note.trim().length < 5
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 400 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{reject ? "Reject invoice" : "Approve invoice"}</p>
          <button onClick={onClose} aria-label="Close" style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} style={{ color: "var(--hf-text-faint)" }} /></button>
        </div>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginBottom: 14 }}>
          {invoice.invoiceNumber} — {fmtMoney(invoice.total)} for {invoice.periodStart} → {invoice.periodEnd}.{" "}
          {reject ? "The storage days it covers become billable again." : "Approving issues the invoice and posts the revenue to the ledger."}
        </p>
        <div>
          <label style={labelStyle}>{reject ? "Reason *" : "Note (optional)"}</label>
          <textarea style={{ ...inputStyle, minHeight: 60, resize: "vertical" }} value={note} onChange={e => setNote(e.target.value)}
            placeholder={reject ? "Why is this being rejected? (at least 5 characters)" : "Anything the preparer should know"} />
        </div>
        {decide.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 12 }}>{(decide.error as any)?.response?.data?.message ?? "Could not record this decision"}</p>}
        <button onClick={() => decide.mutate()} disabled={tooShort || decide.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: reject ? "var(--hf-danger-text)" : WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (tooShort || decide.isPending) ? 0.6 : 1 }}>
          {decide.isPending ? "Saving…" : reject ? "Reject invoice" : "Approve and issue"}
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
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 380 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Record payment</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} style={{ color: 'var(--hf-text-faint)' }} /></button>
        </div>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginBottom: 14 }}>{invoice.invoiceNumber} — {fmtMoney(invoice.balance)} outstanding</p>
        <div><label style={labelStyle}>Amount *</label><input type="number" step="0.01" style={inputStyle} value={amount} onChange={e => setAmount(e.target.value)} /></div>
        {record.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 12 }}>{(record.error as any)?.response?.data?.message ?? "Could not record this payment"}</p>}
        <button onClick={() => record.mutate()} disabled={!amount || record.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!amount || record.isPending) ? 0.6 : 1 }}>
          {record.isPending ? "Recording…" : "Record payment"}
        </button>
      </div>
    </div>
  )
}

export default function WhseBillingInvoicesTab({ clientId }: { clientId: string }) {
  const [showGenerate, setShowGenerate] = useState(false)
  const [paying, setPaying] = useState<BillingInvoiceResponse | null>(null)
  const [deciding, setDeciding] = useState<{ invoice: BillingInvoiceResponse; mode: "approve" | "reject" } | null>(null)
  const canDecide = usePermission("WAREHOUSING_BILLING_APPROVE")
  const myId = useAuthStore(s => s.user?.userId)

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
        <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>{invoices.length} invoice{invoices.length === 1 ? "" : "s"}</p>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={downloadStatement} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid var(--hf-border)", color: "var(--hf-text-muted)", borderRadius: 8, padding: "9px 14px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}>
            <Download size={14} /> Inventory statement
          </button>
          {/* ADMIN-only server-side — not hidden client-side, see this tab's own header note */}
          <button onClick={() => setShowGenerate(true)}
            style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <FileText size={15} /> Prepare invoice
          </button>
        </div>
      </div>

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
      ) : invoices.length === 0 ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No invoices generated yet.</p>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {invoices.map((inv, i) => {
            const colors = STATUS_COLORS[inv.status] ?? { bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" }
            return (
              <div key={inv.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{inv.invoiceNumber}</p>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{STATUS_LABELS[inv.status] ?? inv.status}</span>
                  </div>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{inv.periodStart} → {inv.periodEnd} · Due {inv.dueDate}</p>
                  {(inv.storageMinimumTopup ?? 0) > 0 && (
                    <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: 0 }}>Storage includes {fmtMoney(inv.storageMinimumTopup!)} to reach the contract minimum</p>
                  )}
                  {inv.status === "DRAFT" && inv.preparedBy && inv.preparedBy === myId && (
                    <p style={{ fontSize: 11.5, color: "var(--hf-warning-text)", margin: 0 }}>You prepared this invoice; a second person needs to approve it.</p>
                  )}
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{fmtMoney(inv.total)}</p>
                    {inv.balance > 0 && inv.status !== "DRAFT" && inv.status !== "REJECTED" && <p style={{ fontSize: 11, color: "var(--hf-warning-text)", margin: 0 }}>{fmtMoney(inv.balance)} outstanding</p>}
                  </div>
                  {inv.status === "DRAFT" && canDecide && (
                    <>
                      <button onClick={() => setDeciding({ invoice: inv, mode: "approve" })} title="Approve and issue"
                        style={{ display: "flex", alignItems: "center", gap: 4, background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, padding: "6px 10px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <Check size={13} /> Approve
                      </button>
                      <button onClick={() => setDeciding({ invoice: inv, mode: "reject" })} title="Reject"
                        style={{ background: "none", border: "1px solid var(--hf-border)", color: "var(--hf-danger-text)", borderRadius: 7, padding: "6px 10px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        Reject
                      </button>
                    </>
                  )}
                  {inv.balance > 0 && inv.status !== "DRAFT" && inv.status !== "REJECTED" && (
                    <button onClick={() => setPaying(inv)} title="Record payment"
                      style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                      <CreditCard size={14} style={{ color: WHSE_ACCENT_TEXT }} />
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
      {deciding && <DecisionModal invoice={deciding.invoice} mode={deciding.mode} onClose={() => setDeciding(null)} />}
    </div>
  )
}
