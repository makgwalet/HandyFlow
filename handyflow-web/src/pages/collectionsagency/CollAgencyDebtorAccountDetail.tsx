// src/pages/collectionsagency/CollAgencyDebtorAccountDetail.tsx
//
// Contact method/outcome are plain validated Strings on the backend
// (CollAgencyContactLog), not a closed Java enum — so the dropdown
// options below are sensible UI conventions, not confirmed backend
// constants, EXCEPT "PROMISE_TO_PAY", which IS special-cased in
// CollAgencyContactLog.record() (requires promisedPaymentDate +
// promisedPaymentAmount) and must be spelled exactly that way.
//
// The three NCA disclosure checkboxes are NOT optional — the backend
// throws IllegalArgumentException if any of the three is false, so the
// Record Contact button stays disabled until all three are checked
// here, matching that hard rule rather than letting the request round-
// trip to fail.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, Download, Plus, X, ShieldAlert } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"
import { STATUSES, STATUS_COLORS, fmtMoney, type DebtorAccountResponse } from "./CollAgencyDebtorAccountsTab"

interface ContactLogResponse {
  id: string; debtorAccountId: string; contactDate: string; contactMethod: string; outcome: string
  disclosedThirdPartyCollector: boolean; disclosedOriginalCreditor: boolean; disclosedDebtorRights: boolean
  notes: string | null; promisedPaymentDate: string | null; promisedPaymentAmount: number | null
  recordedByUserId: string; recordedByUserName: string | null
}
interface PaymentPlanResponse {
  id: string; debtorAccountId: string; status: string; totalAgreedAmount: number; installmentAmount: number
  frequency: string; startDate: string; nextDueDate: string | null; numberOfInstallments: number
  installmentsPaid: number; notes: string | null
}
interface CollectorResponse { id: string; fullName: string; active: boolean }

const CONTACT_METHODS = ["PHONE", "EMAIL", "SMS", "WHATSAPP", "LETTER", "IN_PERSON"]
const OUTCOMES = ["NO_ANSWER", "LEFT_MESSAGE", "PROMISE_TO_PAY", "DISPUTED", "REFUSED_TO_PAY", "WRONG_NUMBER", "PAID_IN_FULL", "OTHER"]
const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 11px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 11.5, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }

function ContactLogForm({ accountId, onDone }: { accountId: string; onDone: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    contactDate: new Date().toISOString().slice(0, 10), contactMethod: "PHONE", outcome: "NO_ANSWER",
    disclosedThirdPartyCollector: false, disclosedOriginalCreditor: false, disclosedDebtorRights: false,
    notes: "", promisedPaymentDate: "", promisedPaymentAmount: "",
  })
  const allDisclosed = form.disclosedThirdPartyCollector && form.disclosedOriginalCreditor && form.disclosedDebtorRights
  const needsPromise = form.outcome === "PROMISE_TO_PAY"
  const valid = allDisclosed && form.contactMethod && form.outcome && (!needsPromise || (form.promisedPaymentDate && form.promisedPaymentAmount))

  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/collections-agency/debtor-accounts/${accountId}/contacts`, {
      ...form,
      promisedPaymentDate: needsPromise ? form.promisedPaymentDate : null,
      promisedPaymentAmount: needsPromise ? parseFloat(form.promisedPaymentAmount) : null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-contacts", accountId] }); onDone() },
  })

  return (
    <div style={{ background: "#FAFBFF", border: "1px solid #E2E8F0", borderRadius: 12, padding: 18, marginBottom: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12, marginBottom: 12 }}>
        <div>
          <label style={labelStyle}>Contact date</label>
          <input type="date" style={inputStyle} value={form.contactDate} onChange={e => setForm({ ...form, contactDate: e.target.value })} />
        </div>
        <div>
          <label style={labelStyle}>Method</label>
          <select style={inputStyle} value={form.contactMethod} onChange={e => setForm({ ...form, contactMethod: e.target.value })}>
            {CONTACT_METHODS.map(m => <option key={m} value={m}>{m.replace(/_/g, " ")}</option>)}
          </select>
        </div>
        <div>
          <label style={labelStyle}>Outcome</label>
          <select style={inputStyle} value={form.outcome} onChange={e => setForm({ ...form, outcome: e.target.value })}>
            {OUTCOMES.map(o => <option key={o} value={o}>{o.replace(/_/g, " ")}</option>)}
          </select>
        </div>
      </div>

      {needsPromise && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 12 }}>
          <div>
            <label style={labelStyle}>Promised payment date *</label>
            <input type="date" style={inputStyle} value={form.promisedPaymentDate} onChange={e => setForm({ ...form, promisedPaymentDate: e.target.value })} />
          </div>
          <div>
            <label style={labelStyle}>Promised amount *</label>
            <input type="number" step="0.01" style={inputStyle} value={form.promisedPaymentAmount} onChange={e => setForm({ ...form, promisedPaymentAmount: e.target.value })} />
          </div>
        </div>
      )}

      <div style={{ marginBottom: 12 }}>
        <label style={labelStyle}>Notes</label>
        <textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
      </div>

      <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, padding: 12, marginBottom: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }}>
          <ShieldAlert size={14} color="#92400E" />
          <p style={{ fontSize: 12, fontWeight: 700, color: "#92400E", margin: 0 }}>Mandatory NCA disclosures — all three required to record this contact</p>
        </div>
        {[
          ["disclosedThirdPartyCollector", "Disclosed that this agency is a third-party collector"],
          ["disclosedOriginalCreditor", "Disclosed the name of the original creditor"],
          ["disclosedDebtorRights", "Disclosed the debtor's rights"],
        ].map(([key, label]) => (
          <label key={key} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12.5, color: "#78350F", marginBottom: 4, cursor: "pointer" }}>
            <input type="checkbox" checked={(form as any)[key]} onChange={e => setForm({ ...form, [key]: e.target.checked })} />
            {label}
          </label>
        ))}
      </div>

      {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not record this contact"}</p>}

      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <button onClick={onDone} style={{ padding: "8px 14px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 12.5, cursor: "pointer" }}>Cancel</button>
        <button onClick={() => save.mutate()} disabled={!valid || save.isPending}
          style={{ padding: "8px 16px", borderRadius: 8, border: "none", background: valid ? CA_ACCENT : "#CBD5E1", color: "#fff", fontSize: 12.5, fontWeight: 600, cursor: valid ? "pointer" : "not-allowed" }}>
          {save.isPending ? "Recording…" : "Record contact"}
        </button>
      </div>
    </div>
  )
}

function PaymentPlanForm({ accountId, onDone }: { accountId: string; onDone: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({ totalAgreedAmount: "", installmentAmount: "", frequency: "MONTHLY", startDate: new Date().toISOString().slice(0, 10), numberOfInstallments: "", notes: "" })
  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/collections-agency/debtor-accounts/${accountId}/payment-plans`, {
      ...form, totalAgreedAmount: parseFloat(form.totalAgreedAmount), installmentAmount: parseFloat(form.installmentAmount),
      numberOfInstallments: parseInt(form.numberOfInstallments, 10),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-plans", accountId] }); qc.invalidateQueries({ queryKey: ["ca-debtor-account", accountId] }); onDone() },
  })
  const valid = form.totalAgreedAmount && form.installmentAmount && form.numberOfInstallments

  return (
    <div style={{ background: "#FAFBFF", border: "1px solid #E2E8F0", borderRadius: 12, padding: 18, marginBottom: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 12 }}>
        <div><label style={labelStyle}>Total agreed amount</label><input type="number" step="0.01" style={inputStyle} value={form.totalAgreedAmount} onChange={e => setForm({ ...form, totalAgreedAmount: e.target.value })} /></div>
        <div><label style={labelStyle}>Installment amount</label><input type="number" step="0.01" style={inputStyle} value={form.installmentAmount} onChange={e => setForm({ ...form, installmentAmount: e.target.value })} /></div>
        <div>
          <label style={labelStyle}>Frequency</label>
          <select style={inputStyle} value={form.frequency} onChange={e => setForm({ ...form, frequency: e.target.value })}>
            <option value="WEEKLY">Weekly</option><option value="FORTNIGHTLY">Fortnightly</option><option value="MONTHLY">Monthly</option>
          </select>
        </div>
        <div><label style={labelStyle}># of installments</label><input type="number" style={inputStyle} value={form.numberOfInstallments} onChange={e => setForm({ ...form, numberOfInstallments: e.target.value })} /></div>
        <div><label style={labelStyle}>Start date</label><input type="date" style={inputStyle} value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} /></div>
      </div>
      <div style={{ marginBottom: 12 }}><label style={labelStyle}>Notes</label><textarea style={{ ...inputStyle, minHeight: 44 }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
      {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 10 }}>{(save.error as any)?.response?.data?.message ?? "Could not propose this plan"}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <button onClick={onDone} style={{ padding: "8px 14px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 12.5, cursor: "pointer" }}>Cancel</button>
        <button onClick={() => save.mutate()} disabled={!valid || save.isPending}
          style={{ padding: "8px 16px", borderRadius: 8, border: "none", background: valid ? CA_ACCENT : "#CBD5E1", color: "#fff", fontSize: 12.5, fontWeight: 600, cursor: valid ? "pointer" : "not-allowed" }}>
          {save.isPending ? "Proposing…" : "Propose plan"}
        </button>
      </div>
    </div>
  )
}

export default function CollAgencyDebtorAccountDetail({ accountId, clientId, onBack }: { accountId: string; clientId: string; onBack: () => void }) {
  const qc = useQueryClient()
  const [showContactForm, setShowContactForm] = useState(false)
  const [showPlanForm, setShowPlanForm] = useState(false)

  const { data: account } = useQuery<DebtorAccountResponse>({
    queryKey: ["ca-debtor-account", accountId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/debtor-accounts/${accountId}`)).data,
  })
  const { data: contacts = [] } = useQuery<ContactLogResponse[]>({
    queryKey: ["ca-contacts", accountId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/debtor-accounts/${accountId}/contacts`)).data,
  })
  const { data: plans = [] } = useQuery<PaymentPlanResponse[]>({
    queryKey: ["ca-plans", accountId],
    queryFn: async () => (await apiClient.get(`/api/v1/collections-agency/debtor-accounts/${accountId}/payment-plans`)).data,
  })
  const { data: collectors = [] } = useQuery<CollectorResponse[]>({
    queryKey: ["ca-collectors"],
    queryFn: async () => (await apiClient.get("/api/v1/collections-agency/collectors")).data,
  })

  const advanceStatus = useMutation({
    mutationFn: async (newStatus: string) => apiClient.post(`/api/v1/collections-agency/debtor-accounts/${accountId}/status`, { newStatus }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-debtor-account", accountId] }),
  })
  const assignCollector = useMutation({
    mutationFn: async (collectorId: string) => apiClient.post(`/api/v1/collections-agency/debtor-accounts/${accountId}/assign`, { collectorId }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-debtor-account", accountId] }),
  })
  const markPaid = useMutation({
    mutationFn: async (planId: string) => apiClient.post(`/api/v1/collections-agency/payment-plans/${planId}/installment-paid`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-plans", accountId] }); qc.invalidateQueries({ queryKey: ["ca-debtor-account", accountId] }) },
  })
  const defaultPlan = useMutation({
    mutationFn: async (planId: string) => { const reason = prompt("Reason for default (optional):") ?? undefined; return apiClient.post(`/api/v1/collections-agency/payment-plans/${planId}/default`, reason ? { reason } : {}) },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-plans", accountId] }),
  })
  const cancelPlan = useMutation({
    mutationFn: async (planId: string) => { const reason = prompt("Reason for cancelling (optional):") ?? undefined; return apiClient.post(`/api/v1/collections-agency/payment-plans/${planId}/cancel`, reason ? { reason } : {}) },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-plans", accountId] }),
  })

  const downloadDemandLetter = async () => {
    const res = await apiClient.get(`/api/v1/collections-agency/debtor-accounts/${accountId}/demand-letter/pdf`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
    const a = document.createElement("a")
    a.href = url; a.download = `demand-letter-${account?.accountReference ?? accountId}.pdf`
    document.body.appendChild(a); a.click(); a.remove()
    window.URL.revokeObjectURL(url)
  }

  if (!account) return <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>

  const colors = STATUS_COLORS[account.status] ?? { bg: "#F1F5F9", fg: "#64748B" }
  const isTerminal = ["RECOVERED", "RETURNED_TO_CLIENT", "WRITTEN_OFF", "CLOSED"].includes(account.status)

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 16, padding: 0 }}>
        <ArrowLeft size={15} /> Back to debtor accounts
      </button>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
            <h2 style={{ fontSize: 18, fontWeight: 800, color: "#0F172A", margin: 0 }}>{account.debtorName}</h2>
            <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 9px", borderRadius: 20, background: colors.bg, color: colors.fg }}>{account.status.replace(/_/g, " ")}</span>
          </div>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>
            {account.accountReference ? `${account.accountReference} · ` : ""}Original creditor: {account.originalCreditorName}
            {account.debtorEmail ? ` · ${account.debtorEmail}` : ""}{account.debtorPhone ? ` · ${account.debtorPhone}` : ""}
          </p>
        </div>
        <button onClick={downloadDemandLetter}
          style={{ display: "flex", alignItems: "center", gap: 6, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "8px 14px", fontSize: 12.5, fontWeight: 600, color: "#374151", cursor: "pointer" }}>
          <Download size={14} /> Demand letter PDF
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12, marginBottom: 24 }}>
        <div style={{ background: "#F8FAFC", borderRadius: 10, padding: 14 }}>
          <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 4px" }}>Current balance / Original</p>
          <p style={{ fontSize: 16, fontWeight: 800, color: "#0F172A", margin: 0 }}>{fmtMoney(account.currentBalance)} <span style={{ fontWeight: 400, fontSize: 12, color: "#94A3B8" }}>/ {fmtMoney(account.originalDebtAmount)}</span></p>
        </div>
        <div style={{ background: "#F8FAFC", borderRadius: 10, padding: 14 }}>
          <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 6px" }}>Assigned collector</p>
          <select disabled={isTerminal} value={account.assignedCollectorId ?? ""} onChange={e => assignCollector.mutate(e.target.value)}
            style={{ ...inputStyle, padding: "5px 8px", fontSize: 12.5 }}>
            <option value="">Unassigned</option>
            {collectors.filter(c => c.active).map(c => <option key={c.id} value={c.id}>{c.fullName}</option>)}
          </select>
        </div>
        <div style={{ background: "#F8FAFC", borderRadius: 10, padding: 14 }}>
          <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 6px" }}>Advance status</p>
          <select disabled={isTerminal} value="" onChange={e => { if (e.target.value) advanceStatus.mutate(e.target.value) }} style={{ ...inputStyle, padding: "5px 8px", fontSize: 12.5 }}>
            <option value="">{isTerminal ? "Closed — no further changes" : "Choose a new status…"}</option>
            {STATUSES.filter(s => s !== account.status).map(s => <option key={s} value={s}>{s.replace(/_/g, " ")}</option>)}
          </select>
        </div>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>Contact trail ({contacts.length})</p>
        {!isTerminal && !showContactForm && (
          <button onClick={() => setShowContactForm(true)} style={{ display: "flex", alignItems: "center", gap: 5, background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "6px 12px", fontSize: 12, fontWeight: 600, color: CA_ACCENT, cursor: "pointer" }}>
            <Plus size={13} /> Record contact
          </button>
        )}
      </div>
      {showContactForm && <ContactLogForm accountId={accountId} onDone={() => setShowContactForm(false)} />}
      {contacts.length === 0 ? (
        <p style={{ fontSize: 12.5, color: "#94A3B8", marginBottom: 24 }}>No contact recorded yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", marginBottom: 24 }}>
          {contacts.map((c, i) => (
            <div key={c.id} style={{ padding: "11px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <p style={{ fontSize: 12.5, fontWeight: 600, color: "#0F172A", margin: 0 }}>{c.contactMethod.replace(/_/g, " ")} — {c.outcome.replace(/_/g, " ")}</p>
                <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{c.contactDate}{c.recordedByUserName ? ` · ${c.recordedByUserName}` : ""}</p>
              </div>
              {c.notes && <p style={{ fontSize: 12, color: "#64748B", margin: "4px 0 0" }}>{c.notes}</p>}
              {c.promisedPaymentDate && <p style={{ fontSize: 11.5, color: "#92400E", margin: "4px 0 0" }}>Promised {fmtMoney(c.promisedPaymentAmount ?? 0)} by {c.promisedPaymentDate}</p>}
            </div>
          ))}
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>Payment plans ({plans.length})</p>
        {!isTerminal && !showPlanForm && !plans.some(p => p.status === "ACTIVE") && (
          <button onClick={() => setShowPlanForm(true)} style={{ display: "flex", alignItems: "center", gap: 5, background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "6px 12px", fontSize: 12, fontWeight: 600, color: CA_ACCENT, cursor: "pointer" }}>
            <Plus size={13} /> Propose plan
          </button>
        )}
      </div>
      {showPlanForm && <PaymentPlanForm accountId={accountId} onDone={() => setShowPlanForm(false)} />}
      {plans.length === 0 ? (
        <p style={{ fontSize: 12.5, color: "#94A3B8" }}>No payment plan proposed yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {plans.map((p, i) => (
            <div key={p.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div>
                <p style={{ fontSize: 12.5, fontWeight: 600, color: "#0F172A", margin: "0 0 2px" }}>
                  {fmtMoney(p.installmentAmount)} {p.frequency.toLowerCase()} — {p.installmentsPaid}/{p.numberOfInstallments} paid
                  <span style={{ marginLeft: 8, fontSize: 10.5, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: p.status === "ACTIVE" ? "#FEF3C7" : p.status === "COMPLETED" ? "#DCFCE7" : "#F1F5F9", color: p.status === "ACTIVE" ? "#92400E" : p.status === "COMPLETED" ? "#166534" : "#64748B" }}>{p.status}</span>
                </p>
                <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>Total {fmtMoney(p.totalAgreedAmount)}{p.nextDueDate ? ` · Next due ${p.nextDueDate}` : ""}</p>
              </div>
              {p.status === "ACTIVE" && (
                <div style={{ display: "flex", gap: 6 }}>
                  <button onClick={() => markPaid.mutate(p.id)} style={{ fontSize: 11.5, fontWeight: 600, color: "#059669", background: "none", border: "1px solid #D1FAE5", borderRadius: 6, padding: "5px 10px", cursor: "pointer" }}>Mark installment paid</button>
                  <button onClick={() => defaultPlan.mutate(p.id)} style={{ fontSize: 11.5, fontWeight: 600, color: "#DC2626", background: "none", border: "1px solid #FECACA", borderRadius: 6, padding: "5px 10px", cursor: "pointer" }}>Default</button>
                  <button onClick={() => cancelPlan.mutate(p.id)} style={{ fontSize: 11.5, fontWeight: 600, color: "#64748B", background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: "5px 10px", cursor: "pointer" }}>Cancel</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
