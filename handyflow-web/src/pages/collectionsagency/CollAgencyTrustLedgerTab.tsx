// src/pages/collectionsagency/CollAgencyTrustLedgerTab.tsx
//
// Money received from a debtor never touches the tenant's own GL — it's
// held in trust until a remittance clears it (see
// CollAgencyTrustTransactionService's own Javadoc). Processing a
// remittance is COLLECTIONSAGENCY_ADMIN-only on the backend — see the
// permission note in CollAgencyClientDetail.tsx.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, Banknote, ArrowDownCircle, ArrowUpCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"
import type { ClientResponse } from "./CollAgencyClientsTab"
import type { DebtorAccountResponse } from "./CollAgencyDebtorAccountsTab"

interface TrustTransactionResponse {
  id: string; clientId: string; debtorAccountId: string | null; transactionType: string; amount: number
  transactionDate: string; reference: string | null; notes: string | null; recordedByUserId: string; createdAt: string
}

const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)
const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 11px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 11.5, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }

function RecordPaymentModal({ clientId, onClose }: { clientId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [debtorAccountId, setDebtorAccountId] = useState("")
  const [amount, setAmount] = useState("")
  const [transactionDate, setTransactionDate] = useState(new Date().toISOString().slice(0, 10))
  const [reference, setReference] = useState("")
  const [notes, setNotes] = useState("")

  const { data: accounts = [] } = useQuery<DebtorAccountResponse[]>({
    queryKey: ["ca-debtor-accounts-all", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/clients/${clientId}/debtor-accounts/all`)).data,
  })
  const eligible = accounts.filter(a => !["RECOVERED", "RETURNED_TO_CLIENT", "WRITTEN_OFF", "CLOSED"].includes(a.status) && a.currentBalance > 0)

  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/collections-agency/debtor-accounts/${debtorAccountId}/trust-transactions/receipt`, {
      amount: parseFloat(amount), transactionDate, reference: reference || null, notes: notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ca-trust", clientId] })
      qc.invalidateQueries({ queryKey: ["ca-clients"] })
      qc.invalidateQueries({ queryKey: ["ca-debtor-account", debtorAccountId] })
      onClose()
    },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 26, width: 440 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Record a debtor payment</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          <div>
            <label style={labelStyle}>Debtor account *</label>
            <select style={inputStyle} value={debtorAccountId} onChange={e => setDebtorAccountId(e.target.value)}>
              <option value="">Select…</option>
              {eligible.map(a => <option key={a.id} value={a.id}>{a.debtorName} — balance {fmtMoney(a.currentBalance)}</option>)}
            </select>
          </div>
          <div><label style={labelStyle}>Amount received *</label><input type="number" step="0.01" style={inputStyle} value={amount} onChange={e => setAmount(e.target.value)} /></div>
          <div><label style={labelStyle}>Transaction date</label><input type="date" style={inputStyle} value={transactionDate} onChange={e => setTransactionDate(e.target.value)} /></div>
          <div><label style={labelStyle}>Reference</label><input style={inputStyle} value={reference} onChange={e => setReference(e.target.value)} /></div>
          <div><label style={labelStyle}>Notes</label><textarea style={{ ...inputStyle, minHeight: 50 }} value={notes} onChange={e => setNotes(e.target.value)} /></div>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not record this payment"}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={!debtorAccountId || !amount || save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: CA_ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer", opacity: (!debtorAccountId || !amount) ? 0.5 : 1 }}>
            {save.isPending ? "Recording…" : "Record payment"}
          </button>
        </div>
      </div>
    </div>
  )
}

function RemittanceModal({ clientId, client, onClose }: { clientId: string; client?: ClientResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [remittanceDate, setRemittanceDate] = useState(new Date().toISOString().slice(0, 10))
  const [overrideRate, setOverrideRate] = useState("")

  const commission = client ? (client.trustBalance * (parseFloat(overrideRate) || client.commissionRatePct)) / 100 : 0
  const net = client ? client.trustBalance - commission : 0

  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/collections-agency/clients/${clientId}/trust-transactions/remittance`, {
      remittanceDate, commissionRatePctOverride: overrideRate ? parseFloat(overrideRate) : null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ca-trust", clientId] })
      qc.invalidateQueries({ queryKey: ["ca-clients"] })
      qc.invalidateQueries({ queryKey: ["ca-invoices", clientId] })
      onClose()
    },
  })

  if (!client || client.trustBalance <= 0) {
    return (
      <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
        <div style={{ background: "#fff", borderRadius: 16, padding: 26, width: 380 }} onClick={e => e.stopPropagation()}>
          <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 16px" }}>No trust balance is currently held for this client — nothing to remit.</p>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Close</button>
        </div>
      </div>
    )
  }

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 26, width: 440 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Process remittance</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 18px" }}>Clears the client's entire trust balance, issues a commission invoice, and pays out the net. This action is ADMIN-only and cannot be undone.</p>

        <div style={{ display: "grid", gap: 12, marginBottom: 16 }}>
          <div><label style={labelStyle}>Remittance date</label><input type="date" style={inputStyle} value={remittanceDate} onChange={e => setRemittanceDate(e.target.value)} /></div>
          <div><label style={labelStyle}>Commission rate override % (optional — defaults to {client.commissionRatePct}%)</label><input type="number" step="0.5" style={inputStyle} value={overrideRate} onChange={e => setOverrideRate(e.target.value)} /></div>
        </div>

        <div style={{ background: "#F8FAFC", borderRadius: 10, padding: 14, marginBottom: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, marginBottom: 6 }}><span style={{ color: "#64748B" }}>Trust balance held</span><span style={{ fontWeight: 700 }}>{fmtMoney(client.trustBalance)}</span></div>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, marginBottom: 6 }}><span style={{ color: "#64748B" }}>Commission ({overrideRate || client.commissionRatePct}%)</span><span style={{ fontWeight: 700, color: "#D97706" }}>−{fmtMoney(commission)}</span></div>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13.5, paddingTop: 8, borderTop: "1px solid #E2E8F0" }}><span style={{ fontWeight: 700 }}>Net paid to client</span><span style={{ fontWeight: 800, color: "#059669" }}>{fmtMoney(net)}</span></div>
        </div>

        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not process this remittance"}</p>}

        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: "#DC2626", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            {save.isPending ? "Processing…" : "Process remittance"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function CollAgencyTrustLedgerTab({ clientId, client }: { clientId: string; client?: ClientResponse }) {
  const [showPayment, setShowPayment] = useState(false)
  const [showRemittance, setShowRemittance] = useState(false)

  const { data: txns = [], isLoading } = useQuery<TrustTransactionResponse[]>({
    queryKey: ["ca-trust", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/clients/${clientId}/trust-transactions`)).data,
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Banknote size={16} color={CA_ACCENT} />
          <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>Currently held: {fmtMoney(client?.trustBalance ?? 0)}</p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={() => setShowPayment(true)} style={{ display: "flex", alignItems: "center", gap: 6, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "8px 14px", fontSize: 12.5, fontWeight: 600, color: "#374151", cursor: "pointer" }}>
            <Plus size={13} /> Record payment
          </button>
          <button onClick={() => setShowRemittance(true)} style={{ background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, padding: "8px 16px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}>
            Process remittance
          </button>
        </div>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : txns.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No trust movements recorded yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {txns.map((t, i) => (
            <div key={t.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                {t.transactionType === "RECEIPT" ? <ArrowDownCircle size={18} color="#059669" /> : <ArrowUpCircle size={18} color="#D97706" />}
                <div>
                  <p style={{ fontSize: 12.5, fontWeight: 600, color: "#0F172A", margin: "0 0 2px" }}>{t.transactionType === "RECEIPT" ? "Payment received" : "Remittance"}{t.reference ? ` · ${t.reference}` : ""}</p>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{t.transactionDate}{t.notes ? ` · ${t.notes}` : ""}</p>
                </div>
              </div>
              <p style={{ fontSize: 13.5, fontWeight: 700, color: t.transactionType === "RECEIPT" ? "#059669" : "#D97706", margin: 0 }}>
                {t.transactionType === "RECEIPT" ? "+" : "−"}{fmtMoney(t.amount)}
              </p>
            </div>
          ))}
        </div>
      )}

      {showPayment && <RecordPaymentModal clientId={clientId} onClose={() => setShowPayment(false)} />}
      {showRemittance && <RemittanceModal clientId={clientId} client={client} onClose={() => setShowRemittance(false)} />}
    </div>
  )
}
