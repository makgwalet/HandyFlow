// src/pages/debtcollection/CasesTab.tsx
//
// Confirmed against the real DebtCollectionCaseController +
// PaymentPlanController. Two ways to open a case (both real endpoints):
// POST /cases (manual — debtor snapshot + explicit invoiceIds, for a
// walk-in debtor or picking specific invoices) and POST
// /cases/open-for-customer (pulls debtor contact details + ALL their
// outstanding invoices automatically from a CRM customer). Every other
// action below — assign, schedule-next-action, advance-status, write-off
// (ADMIN only, a financial determination), close (requires a
// ClosureReason), link/unlink invoice, link-contract, contact log,
// payment plans, evidence, PDF exports — maps 1:1 to a real endpoint;
// none of this is inferred.
//
// Customers, invoices and collectors are chosen from lists (CustomerPicker,
// NewCaseDialog, CollectorSelect) — no IDs are typed.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import DisputesPanel from "./DisputesPanel"
import StrategyNote from "./StrategyNote"
import NewCaseDialog from "./NewCaseDialog"
import CollectorSelect from "./CollectorSelect"
import { useAuth } from "../../hooks/useAuth"
import { QUEUES, inQueue, queueCounts, searchCases, type Queue } from "./collections.logic"
import { pill } from "./ui"
import {
  Plus, Landmark, ChevronDown, ChevronUp, X, UserPlus, CalendarClock,
  ArrowRightCircle, Flag, AlertOctagon, Trash2, Download, FileDown,
  Phone, Wallet, Paperclip, FileText, AlertCircle, Link2, Search,
} from "lucide-react"

interface Case {
  id: string; caseNumber: string; customerId: string | null; debtorName: string
  debtorEmail: string | null; debtorPhone: string | null; status: string
  totalOutstanding: number; linkedInvoiceIds: string[]; openedDate: string
  closedDate: string | null; closureReason: string | null; assignedToUserId: string | null
  assignedToUserName: string | null; linkedContractId: string | null
  lastContactDate: string | null; nextActionDate: string | null
  writeOffAmount: number | null; notes: string | null; createdAt: string; updatedAt: string
}
interface ContactLog {
  id: string; caseId: string; contactDate: string; contactMethod: string; outcome: string
  notes: string | null; promisedPaymentDate: string | null; promisedPaymentAmount: number | null
  recordedByUserId: string; recordedByUserName: string | null; createdAt: string
}
interface PaymentPlan {
  id: string; caseId: string; status: string; totalAgreedAmount: number; installmentAmount: number
  frequency: string; startDate: string; nextDueDate: string | null; numberOfInstallments: number
  installmentsPaid: number; notes: string | null; createdAt: string
}
interface Evidence { id: string; fileName: string; contentType: string; fileSizeBytes: number; evidenceType: string; status: string; uploadedByName: string; createdAt: string }

const STATUSES = ["OPEN", "DEMAND_SENT", "PAYMENT_PLAN_ACTIVE", "DISPUTED", "HANDED_TO_LEGAL", "SETTLED", "WRITTEN_OFF", "CLOSED"]
const TERMINAL = new Set(["SETTLED", "WRITTEN_OFF", "CLOSED"])
const CLOSURE_REASONS = ["PAID_IN_FULL", "SETTLED_PARTIAL", "WRITTEN_OFF", "HANDED_TO_LEGAL", "DISPUTE_UPHELD", "OTHER"]
const CONTACT_METHODS = ["PHONE_CALL", "EMAIL", "SMS", "WHATSAPP", "LETTER", "IN_PERSON", "OTHER"]
const CONTACT_OUTCOMES = ["NO_ANSWER", "LEFT_MESSAGE", "PROMISE_TO_PAY", "DISPUTED", "REFUSED_TO_PAY", "ALREADY_PAID", "WRONG_CONTACT_DETAILS", "OTHER"]
const PLAN_FREQUENCIES = ["WEEKLY", "FORTNIGHTLY", "MONTHLY"]

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string }> = {
  OPEN:                { color: "var(--hf-orange-text-strong)", bg: "var(--hf-orange-soft)", border: "var(--hf-orange-border)", label: "Open"              },
  DEMAND_SENT:         { color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft-strong)", border: "var(--hf-warning-border)", label: "Demand Sent"       },
  PAYMENT_PLAN_ACTIVE: { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", border: "var(--hf-info-border)", label: "Payment Plan"      },
  DISPUTED:            { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", border: "var(--hf-warning-border)", label: "Disputed"          },
  HANDED_TO_LEGAL:     { color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", border: "var(--hf-violet-border)", label: "Handed to Legal"   },
  SETTLED:             { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", border: "var(--hf-success-border)", label: "Settled"           },
  WRITTEN_OFF:         { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)", label: "Written Off"       },
  CLOSED:              { color: "var(--hf-text-secondary)", bg: "var(--hf-surface-muted)", border: "var(--hf-border)", label: "Closed"            },
}
const PLAN_STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:    { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", label: "Active"    },
  COMPLETED: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", label: "Completed" },
  DEFAULTED: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Defaulted" },
  CANCELLED: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", label: "Cancelled" },
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtBytes = (b: number) => b < 1024 ? `${b} B` : b < 1024 * 1024 ? `${(b / 1024).toFixed(1)} KB` : `${(b / (1024 * 1024)).toFixed(1)} MB`
const daysUntil = (d: string) => Math.ceil((new Date(d).getTime() - Date.now()) / 86400000)

async function downloadCaseRegisterPdf() {
  const res = await apiClient.get("/api/v1/debtcollection/cases/export/pdf", { responseType: "blob" })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = url; a.download = "debt-collection-case-register.pdf"
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}
async function downloadDemandLetter(caseId: string, caseNumber: string) {
  const res = await apiClient.get(`/api/v1/debtcollection/cases/${caseId}/demand-letter/pdf`, { responseType: "blob" })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = url; a.download = `demand-letter-${caseNumber}.pdf`
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

function CaseDetailPanel({ c, canManage, canAdmin, invalidate }: { c: Case; canManage: boolean; canAdmin: boolean; invalidate: () => void }) {
  const qc = useQueryClient()
  const [showContactForm, setShowContactForm] = useState(false)
  const [contactForm, setContactForm] = useState({ contactDate: new Date().toISOString().split("T")[0], contactMethod: "PHONE_CALL", outcome: "NO_ANSWER", notes: "", promisedPaymentDate: "", promisedPaymentAmount: "" })
  const [showPlanForm, setShowPlanForm] = useState(false)
  const [planForm, setPlanForm] = useState({ totalAgreedAmount: String(c.totalOutstanding ?? ""), installmentAmount: "", frequency: "MONTHLY", startDate: new Date().toISOString().split("T")[0], numberOfInstallments: "", notes: "" })
  const [file, setFile] = useState<File | null>(null)
  const [evidenceType, setEvidenceType] = useState("")

  const { data: contacts = [] } = useQuery<ContactLog[]>({
    queryKey: ["dc-contacts", c.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/debtcollection/cases/${c.id}/contacts`)),
  })
  const { data: plans = [] } = useQuery<PaymentPlan[]>({
    queryKey: ["dc-plans", c.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/debtcollection/cases/${c.id}/payment-plans`)),
  })
  const { data: evidence = [] } = useQuery<Evidence[]>({
    queryKey: ["dc-evidence", c.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/debtcollection/cases/${c.id}/evidence`)),
  })

  const recordContact = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/debtcollection/cases/${c.id}/contacts`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["dc-contacts", c.id] }); invalidate(); setShowContactForm(false); setContactForm({ contactDate: new Date().toISOString().split("T")[0], contactMethod: "PHONE_CALL", outcome: "NO_ANSWER", notes: "", promisedPaymentDate: "", promisedPaymentAmount: "" }) },
  })

  const proposePlan = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/debtcollection/cases/${c.id}/payment-plans`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["dc-plans", c.id] }); invalidate(); setShowPlanForm(false) },
  })

  const markPaid = useMutation({
    mutationFn: (planId: string) => apiClient.post(`/api/v1/debtcollection/payment-plans/${planId}/mark-installment-paid`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["dc-plans", c.id] }),
  })
  const markDefaulted = useMutation({
    mutationFn: (planId: string) => apiClient.post(`/api/v1/debtcollection/payment-plans/${planId}/mark-defaulted`, { reason: "Missed installment" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["dc-plans", c.id] }),
  })
  const cancelPlan = useMutation({
    mutationFn: (planId: string) => apiClient.post(`/api/v1/debtcollection/payment-plans/${planId}/cancel`, { reason: "Cancelled by staff" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["dc-plans", c.id] }),
  })

  const attachEvidence = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("file", file as File)
      fd.append("evidenceType", evidenceType)
      return apiClient.post(`/api/v1/debtcollection/cases/${c.id}/evidence`, fd, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["dc-evidence", c.id] }); setFile(null); setEvidenceType("") },
  })

  const inp: React.CSSProperties = { width: "100%", padding: "7px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 7, fontSize: 12, boxSizing: "border-box" as const }
  const activePlan = plans.find(p => p.status === "ACTIVE")

  return (
    <div style={{ borderTop: "1px solid var(--hf-border-subtle)", padding: "16px 20px", background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 14, marginBottom: 16 }}>
        {[
          { l: "Debtor email",    v: c.debtorEmail || "—" },
          { l: "Debtor phone",    v: c.debtorPhone || "—" },
          { l: "Linked contract", v: c.linkedContractId || "None" },
          { l: "Linked invoices", v: `${c.linkedInvoiceIds.length} invoice(s)` },
          { l: "Last contact",    v: fmtDate(c.lastContactDate) },
          { l: "Write-off amount",v: c.writeOffAmount != null ? fmtR(c.writeOffAmount) : "—" },
        ].map(item => (
          <div key={item.l}>
            <div style={{ fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
            <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{item.v}</div>
          </div>
        ))}
      </div>
      {c.notes && <div style={{ marginBottom: 14, padding: "8px 12px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-text-secondary)" }}>{c.notes}</div>}

      <StrategyNote caseId={c.id} />
      <DisputesPanel caseId={c.id} invoiceIds={c.linkedInvoiceIds} canManage={canManage} onChanged={invalidate} />

      {/* Contact log */}
      <div style={{ marginBottom: 18, paddingTop: 14, borderTop: "1px solid var(--hf-border)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}><Phone size={12} />Contact log</div>
          {canManage && <button onClick={() => setShowContactForm(s => !s)} style={{ fontSize: 11, color: "var(--hf-orange-text-strong)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>{showContactForm ? "Cancel" : "+ Record contact"}</button>}
        </div>
        {showContactForm && (
          <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12, marginBottom: 10 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Date</label><input type="date" value={contactForm.contactDate} onChange={e => setContactForm(f => ({ ...f, contactDate: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Method</label><select value={contactForm.contactMethod} onChange={e => setContactForm(f => ({ ...f, contactMethod: e.target.value }))} style={{ ...inp, background: "var(--hf-surface)" }}>{CONTACT_METHODS.map(m => <option key={m} value={m}>{m.replace(/_/g, " ")}</option>)}</select></div>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Outcome</label><select value={contactForm.outcome} onChange={e => setContactForm(f => ({ ...f, outcome: e.target.value }))} style={{ ...inp, background: "var(--hf-surface)" }}>{CONTACT_OUTCOMES.map(o => <option key={o} value={o}>{o.replace(/_/g, " ")}</option>)}</select></div>
            </div>
            {contactForm.outcome === "PROMISE_TO_PAY" && (
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 8 }}>
                <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Promised date *</label><input type="date" value={contactForm.promisedPaymentDate} onChange={e => setContactForm(f => ({ ...f, promisedPaymentDate: e.target.value }))} style={inp} /></div>
                <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Promised amount *</label><input type="number" value={contactForm.promisedPaymentAmount} onChange={e => setContactForm(f => ({ ...f, promisedPaymentAmount: e.target.value }))} style={inp} /></div>
              </div>
            )}
            <textarea value={contactForm.notes} onChange={e => setContactForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Notes..." style={{ ...inp, resize: "vertical" as const, marginBottom: 8 }} />
            <button onClick={() => recordContact.mutate({ contactDate: contactForm.contactDate, contactMethod: contactForm.contactMethod, outcome: contactForm.outcome, notes: contactForm.notes || null, promisedPaymentDate: contactForm.promisedPaymentDate || null, promisedPaymentAmount: contactForm.promisedPaymentAmount ? Number(contactForm.promisedPaymentAmount) : null })}
              disabled={recordContact.isPending || (contactForm.outcome === "PROMISE_TO_PAY" && (!contactForm.promisedPaymentDate || !contactForm.promisedPaymentAmount))}
              style={{ padding: "6px 14px", background: "var(--hf-orange-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
              {recordContact.isPending ? "Saving..." : "Record"}
            </button>
          </div>
        )}
        {contacts.length === 0 ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No contact recorded yet.</div> : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {contacts.map(l => (
              <div key={l.id} style={{ padding: "8px 12px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12 }}>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ fontWeight: 700, color: "var(--hf-text)" }}>{fmtDate(l.contactDate)} · {l.contactMethod.replace(/_/g, " ")}</span>
                  <span style={{ fontWeight: 700, color: l.outcome === "PROMISE_TO_PAY" ? "var(--hf-success-text-strong)" : l.outcome === "DISPUTED" ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>{l.outcome.replace(/_/g, " ")}</span>
                </div>
                {l.notes && <div style={{ color: "var(--hf-text-tertiary)", marginTop: 3 }}>{l.notes}</div>}
                {l.promisedPaymentDate && <div style={{ color: "var(--hf-success-text-strong)", marginTop: 3 }}>Promised {fmtR(l.promisedPaymentAmount)} by {fmtDate(l.promisedPaymentDate)}</div>}
                <div style={{ color: "var(--hf-text-faint)", marginTop: 3 }}>{l.recordedByUserName ?? "—"}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Payment plan */}
      <div style={{ marginBottom: 18, paddingTop: 14, borderTop: "1px solid var(--hf-border)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}><Wallet size={12} />Payment plans</div>
          {canManage && !activePlan && <button onClick={() => setShowPlanForm(s => !s)} style={{ fontSize: 11, color: "var(--hf-orange-text-strong)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>{showPlanForm ? "Cancel" : "+ Propose plan"}</button>}
        </div>
        {showPlanForm && (
          <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12, marginBottom: 10 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Total agreed *</label><input type="number" value={planForm.totalAgreedAmount} onChange={e => setPlanForm(f => ({ ...f, totalAgreedAmount: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Installment *</label><input type="number" value={planForm.installmentAmount} onChange={e => setPlanForm(f => ({ ...f, installmentAmount: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}># Installments *</label><input type="number" value={planForm.numberOfInstallments} onChange={e => setPlanForm(f => ({ ...f, numberOfInstallments: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Frequency</label><select value={planForm.frequency} onChange={e => setPlanForm(f => ({ ...f, frequency: e.target.value }))} style={{ ...inp, background: "var(--hf-surface)" }}>{PLAN_FREQUENCIES.map(f => <option key={f} value={f}>{f}</option>)}</select></div>
              <div><label style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Start date</label><input type="date" value={planForm.startDate} onChange={e => setPlanForm(f => ({ ...f, startDate: e.target.value }))} style={inp} /></div>
            </div>
            <textarea value={planForm.notes} onChange={e => setPlanForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Notes..." style={{ ...inp, resize: "vertical" as const, marginBottom: 8 }} />
            <button onClick={() => proposePlan.mutate({ totalAgreedAmount: Number(planForm.totalAgreedAmount), installmentAmount: Number(planForm.installmentAmount), frequency: planForm.frequency, startDate: planForm.startDate, numberOfInstallments: Number(planForm.numberOfInstallments), notes: planForm.notes || null })}
              disabled={proposePlan.isPending || !planForm.totalAgreedAmount || !planForm.installmentAmount || !planForm.numberOfInstallments}
              style={{ padding: "6px 14px", background: "var(--hf-orange-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
              {proposePlan.isPending ? "Saving..." : "Propose Plan"}
            </button>
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 6 }}>Proposing a plan also moves the case to Payment Plan status.</div>
          </div>
        )}
        {plans.length === 0 ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No payment plan proposed yet.</div> : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {plans.map(p => {
              const cfg = PLAN_STATUS_CFG[p.status] ?? PLAN_STATUS_CFG.ACTIVE
              return (
                <div key={p.id} style={{ padding: "10px 12px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
                    <span style={{ fontWeight: 700, color: "var(--hf-text)" }}>{fmtR(p.totalAgreedAmount)} over {p.numberOfInstallments} × {fmtR(p.installmentAmount)} ({p.frequency.toLowerCase()})</span>
                    <span style={{ fontWeight: 700, color: cfg.color, background: cfg.bg, padding: "2px 8px", borderRadius: 20, fontSize: 10 }}>{cfg.label}</span>
                  </div>
                  <div style={{ color: "var(--hf-text-muted)" }}>{p.installmentsPaid} / {p.numberOfInstallments} paid{p.nextDueDate ? ` · next due ${fmtDate(p.nextDueDate)}` : ""}</div>
                  {canManage && p.status === "ACTIVE" && (
                    <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
                      <button onClick={() => markPaid.mutate(p.id)} style={{ padding: "4px 10px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "none", borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>Mark installment paid</button>
                      <button onClick={() => { if (confirm("Mark this plan as defaulted?")) markDefaulted.mutate(p.id) }} style={{ padding: "4px 10px", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", border: "none", borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>Mark defaulted</button>
                      <button onClick={() => { if (confirm("Cancel this plan?")) cancelPlan.mutate(p.id) }} style={{ padding: "4px 10px", background: "var(--hf-surface-sunken)", color: "var(--hf-text-muted)", border: "none", borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Evidence */}
      <div style={{ paddingTop: 14, borderTop: "1px solid var(--hf-border)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}><FileText size={12} />Evidence — demand letters, AOD, correspondence</div>
        {evidence.length === 0 ? (
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 10 }}>No documents attached yet.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
            {evidence.map(ev => (
              <div key={ev.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "7px 10px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12 }}>
                <FileText size={13} style={{ color: 'var(--hf-text-muted)' }} />
                <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{ev.fileName}</span>
                <span style={{ color: "var(--hf-text-faint)" }}>{ev.evidenceType} · {fmtBytes(ev.fileSizeBytes)}</span>
                <span style={{ marginLeft: "auto", color: "var(--hf-text-faint)" }}>{ev.uploadedByName} · {fmtDate(ev.createdAt)}</span>
              </div>
            ))}
          </div>
        )}
        {canManage && (
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <input type="text" value={evidenceType} onChange={e => setEvidenceType(e.target.value)} placeholder="Type e.g. AOD" style={{ flex: 1, padding: "7px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 7, fontSize: 12 }} />
            <input type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 12, flex: 1 }} />
            <button onClick={() => attachEvidence.mutate()} disabled={!file || !evidenceType.trim() || attachEvidence.isPending}
              style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 12px", background: (!file || !evidenceType.trim()) ? "var(--hf-border-strong)" : "var(--hf-orange-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: (!file || !evidenceType.trim()) ? "not-allowed" : "pointer" }}>
              <Paperclip size={12} /> {attachEvidence.isPending ? "Uploading..." : "Attach"}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default function CasesTab() {
  const qc = useQueryClient()
  const canManage = usePermission("DEBTCOLLECTION_MANAGE") || usePermission("DEBTCOLLECTION_ADMIN")
  const canAdmin = usePermission("DEBTCOLLECTION_ADMIN")

  const [filterStatus, setFilterStatus] = useState("ALL")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showNewCase, setShowNewCase] = useState(false)
  const [queue, setQueue] = useState<Queue>("all")
  const [search, setSearch] = useState("")
  const { user } = useAuth()
  const myId: string | null = (user as any)?.userId ?? (user as any)?.id ?? null

  const [showAssign, setShowAssign] = useState<Case | null>(null)
  const [assignUserId, setAssignUserId] = useState("")
  const [assignUserName, setAssignUserName] = useState("")
  const [showSchedule, setShowSchedule] = useState<Case | null>(null)
  const [nextActionDate, setNextActionDate] = useState("")
  const [showAdvance, setShowAdvance] = useState<Case | null>(null)
  const [newStatus, setNewStatus] = useState("")
  const [showClose, setShowClose] = useState<Case | null>(null)
  const [closureReason, setClosureReason] = useState("PAID_IN_FULL")
  const [outcomeNotes, setOutcomeNotes] = useState("")
  const [showWriteOff, setShowWriteOff] = useState<Case | null>(null)
  const [writeOffAmount, setWriteOffAmount] = useState("")
  const [writeOffReason, setWriteOffReason] = useState("")
  const [apiError, setApiError] = useState("")

  const { data: cases = [], isLoading } = useQuery<Case[]>({
    queryKey: ["dc-cases", filterStatus],
    queryFn: async () => unwrap(await apiClient.get(
      `/api/v1/debtcollection/cases?size=200${filterStatus !== "ALL" ? `&status=${filterStatus}` : ""}`
    )),
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["dc-cases"] }); qc.invalidateQueries({ queryKey: ["dc-cases-all"] }) }

  const refreshOutstanding = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/debtcollection/cases/${id}/refresh-outstanding`),
    onSuccess: () => invalidate(),
  })
  const assign = useMutation({
    mutationFn: ({ id, userId, userName }: { id: string; userId: string; userName: string }) =>
      apiClient.post(`/api/v1/debtcollection/cases/${id}/assign`, { userId, userName }),
    onSuccess: () => { invalidate(); setShowAssign(null); setAssignUserId(""); setAssignUserName(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to assign"),
  })
  const scheduleNextAction = useMutation({
    mutationFn: ({ id, date }: { id: string; date: string }) => apiClient.post(`/api/v1/debtcollection/cases/${id}/schedule-next-action`, { nextActionDate: date }),
    onSuccess: () => { invalidate(); setShowSchedule(null); setNextActionDate(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to schedule"),
  })
  const advanceStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => apiClient.post(`/api/v1/debtcollection/cases/${id}/advance-status`, { status }),
    onSuccess: () => { invalidate(); setShowAdvance(null); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to advance status"),
  })
  const closeCase = useMutation({
    mutationFn: ({ id, reason, notes }: { id: string; reason: string; notes: string }) => apiClient.post(`/api/v1/debtcollection/cases/${id}/close`, { reason, outcomeNotes: notes || null }),
    onSuccess: () => { invalidate(); setShowClose(null); setOutcomeNotes(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to close case"),
  })
  const writeOff = useMutation({
    mutationFn: ({ id, amount, reason }: { id: string; amount: number; reason: string }) => apiClient.post(`/api/v1/debtcollection/cases/${id}/write-off`, { amount, reason: reason || null }),
    onSuccess: () => { invalidate(); setShowWriteOff(null); setWriteOffAmount(""); setWriteOffReason(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to write off case"),
  })
  const deleteCase = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/debtcollection/cases/${id}`),
    onSuccess: () => invalidate(),
  })

  const counts = queueCounts(cases as any, myId, new Date().toISOString().split("T")[0])
  const visible = searchCases(cases.filter(c => inQueue(c as any, queue, myId, new Date().toISOString().split("T")[0])) as any, search) as unknown as Case[]

  const stats = [
    { label: "Total",           value: cases.length,                                                    color: "var(--hf-orange-text-strong)" },
    { label: "Open",            value: cases.filter(c => !TERMINAL.has(c.status)).length,                color: "var(--hf-warning-text-strong)" },
    { label: "On plan",         value: cases.filter(c => c.status === "PAYMENT_PLAN_ACTIVE").length,     color: "var(--hf-info-text)" },
    { label: "Outstanding",     value: fmtR(cases.filter(c => !TERMINAL.has(c.status)).reduce((s, c) => s + (c.totalOutstanding ?? 0), 0)), color: "var(--hf-danger-text)" },
  ]

  const StatusBadge = ({ status }: { status: string }) => {
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.OPEN
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>{cfg.label}</span>
  }
  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, background: "var(--hf-surface)", outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap", alignItems: "center" }}>
        {QUEUES.map(q => (
          <button key={q.id} onClick={() => setQueue(q.id)} style={pill(queue === q.id)}>{q.label} <span style={{ opacity: 0.7 }}>({counts[q.id]})</span></button>
        ))}
        <div style={{ position: "relative", marginLeft: "auto", minWidth: 220 }}>
          <Search size={14} style={{ position: "absolute", left: 10, top: 11, color: "var(--hf-text-faint)" }} />
          <input aria-label="Search cases" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search case, debtor or collector" style={{ ...inp, paddingLeft: 30 }} />
        </div>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "var(--hf-orange-solid-strong)" : STATUS_CFG[s]?.color ?? "var(--hf-orange-solid-strong)") : "var(--hf-surface-sunken)",
                color: filterStatus === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={downloadCaseRegisterPdf} style={{ display: "flex", alignItems: "center", gap: 6, background: "var(--hf-surface)", color: "var(--hf-orange-text-strong)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "9px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Download size={14} /> Export PDF
          </button>
          {canManage && (
            <button onClick={() => setShowNewCase(true)}
              style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-orange-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={15} /> Open Case
            </button>
          )}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading cases...</div>
      ) : visible.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <Landmark size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>{search || queue !== "all" ? "No cases match this view" : "No cases found"}</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {visible.map(c => {
            const isOpen = expanded === c.id
            const cfg = STATUS_CFG[c.status] ?? STATUS_CFG.OPEN
            const isTerminal = TERMINAL.has(c.status)
            const overdue = c.nextActionDate && daysUntil(c.nextActionDate) < 0 && !isTerminal
            return (
              <div key={c.id} style={{ border: `1px solid ${overdue ? "var(--hf-danger-border)" : "var(--hf-border)"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "var(--hf-surface)", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 44, height: 44, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Landmark size={18} style={{ color: cfg.color }} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)" }}>{c.caseNumber}</span>
                        <span style={{ fontSize: 14, color: "var(--hf-text-muted)" }}>{c.debtorName}</span>
                        {overdue && <span style={{ fontSize: 10, fontWeight: 700, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", padding: "1px 7px", borderRadius: 20 }}>FOLLOW-UP OVERDUE</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
                        Opened {fmtDate(c.openedDate)}{c.assignedToUserName ? ` · ${c.assignedToUserName}` : ""}{c.nextActionDate ? ` · Next action ${fmtDate(c.nextActionDate)}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>{fmtR(c.totalOutstanding)}</div>
                    </div>
                    <StatusBadge status={c.status} />
                    {canManage && !isTerminal && (
                      <div style={{ display: "flex", gap: 5 }}>
                        <button onClick={() => { setShowAssign(c); setAssignUserId(c.assignedToUserId ?? ""); setAssignUserName(c.assignedToUserName ?? "") }} title="Assign" style={{ background: "var(--hf-violet-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-violet-text)" }}><UserPlus size={13} /></button>
                        <button onClick={() => { setShowSchedule(c); setNextActionDate(c.nextActionDate ?? "") }} title="Schedule next action" style={{ background: "var(--hf-warning-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-warning-text)" }}><CalendarClock size={13} /></button>
                        <button onClick={() => { setShowAdvance(c); setNewStatus("") }} title="Advance status" style={{ background: "var(--hf-info-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-info-text)" }}><ArrowRightCircle size={13} /></button>
                        <button onClick={() => downloadDemandLetter(c.id, c.caseNumber)} title="Demand letter PDF" style={{ background: "var(--hf-surface-sunken)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-text-secondary)" }}><FileDown size={13} /></button>
                        <button onClick={() => { setShowClose(c); setClosureReason("PAID_IN_FULL"); setOutcomeNotes("") }} title="Close case" style={{ background: "var(--hf-surface-sunken)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-text-secondary)" }}><Flag size={13} /></button>
                        {canAdmin && <button onClick={() => { setShowWriteOff(c); setWriteOffAmount(String(c.totalOutstanding ?? "")); setWriteOffReason("") }} title="Write off (Admin)" style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><AlertOctagon size={13} /></button>}
                        {canAdmin && <button onClick={() => { if (confirm(`Delete case "${c.caseNumber}"?`)) deleteCase.mutate(c.id) }} title="Delete" style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>}
                      </div>
                    )}
                    <button onClick={() => setExpanded(isOpen ? null : c.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                {isOpen && <CaseDetailPanel c={c} canManage={canManage} canAdmin={canAdmin} invalidate={invalidate} />}
              </div>
            )
          })}
        </div>
      )}

      {showNewCase && <NewCaseDialog onClose={() => setShowNewCase(false)} onOpened={invalidate} />}

      {showAssign && (
        <Overlay onClose={() => { setShowAssign(null); setApiError("") }}>
          <MHead title={`Assign — ${showAssign.caseNumber}`} onClose={() => { setShowAssign(null); setApiError("") }} />
          <CollectorSelect label="Collector" blankLabel="Choose a collector" value={assignUserId} onChange={(id, c) => { setAssignUserId(id); setAssignUserName(c?.userName ?? "") }} />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowAssign(null); setApiError("") }} onSubmit={() => assign.mutate({ id: showAssign.id, userId: assignUserId, userName: assignUserName })} loading={assign.isPending} label="Assign" disabled={!assignUserId} />
        </Overlay>
      )}

      {showSchedule && (
        <Overlay onClose={() => { setShowSchedule(null); setApiError("") }}>
          <MHead title={`Schedule Next Action — ${showSchedule.caseNumber}`} onClose={() => { setShowSchedule(null); setApiError("") }} />
          <label style={lbl}>Next action date</label>
          <input type="date" value={nextActionDate} onChange={e => setNextActionDate(e.target.value)} style={{ ...inp, width: "100%" }} />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowSchedule(null); setApiError("") }} onSubmit={() => scheduleNextAction.mutate({ id: showSchedule.id, date: nextActionDate })} loading={scheduleNextAction.isPending} label="Schedule" disabled={!nextActionDate} />
        </Overlay>
      )}

      {showAdvance && (
        <Overlay onClose={() => { setShowAdvance(null); setApiError("") }}>
          <MHead title={`Advance Status — ${showAdvance.caseNumber}`} onClose={() => { setShowAdvance(null); setApiError("") }} />
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
            {STATUSES.filter(s => s !== showAdvance.status && s !== "CLOSED").map(s => {
              const cfg = STATUS_CFG[s]; const sel = newStatus === s
              return (
                <button key={s} onClick={() => setNewStatus(s)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `2px solid ${sel ? cfg.color : "var(--hf-border)"}`, borderRadius: 10, cursor: "pointer", background: sel ? cfg.bg : "var(--hf-surface)", textAlign: "left" as const, width: "100%" }}>
                  <span style={{ fontWeight: 600, color: sel ? cfg.color : "var(--hf-text)" }}>{cfg.label}</span>
                </button>
              )
            })}
          </div>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 10 }}>Use "Close case" to close — CLOSED can't be set here.</div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowAdvance(null); setApiError("") }} onSubmit={() => advanceStatus.mutate({ id: showAdvance.id, status: newStatus })} loading={advanceStatus.isPending} label="Update Status" disabled={!newStatus} />
        </Overlay>
      )}

      {showClose && (
        <Overlay onClose={() => { setShowClose(null); setApiError("") }}>
          <MHead title={`Close Case — ${showClose.caseNumber}`} onClose={() => { setShowClose(null); setApiError("") }} />
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Closure reason *</label>
            <select value={closureReason} onChange={e => setClosureReason(e.target.value)} style={{ ...inp, background: "var(--hf-surface)" }}>
              {CLOSURE_REASONS.map(r => <option key={r} value={r}>{r.replace(/_/g, " ")}</option>)}
            </select>
          </div>
          <label style={lbl}>Outcome notes</label>
          <textarea value={outcomeNotes} onChange={e => setOutcomeNotes(e.target.value)} rows={3} style={{ ...inp, width: "100%", resize: "vertical" as const }} />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowClose(null); setApiError("") }} onSubmit={() => closeCase.mutate({ id: showClose.id, reason: closureReason, notes: outcomeNotes })} loading={closeCase.isPending} label="Close Case" />
        </Overlay>
      )}

      {showWriteOff && (
        <Overlay onClose={() => { setShowWriteOff(null); setApiError("") }}>
          <MHead title={`Write Off — ${showWriteOff.caseNumber}`} onClose={() => { setShowWriteOff(null); setApiError("") }} />
          <div style={{ padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", marginBottom: 14 }}>
            A formal financial determination that this debt will not be recovered. Restricted to Admin.
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Write-off amount *</label>
            <input type="number" value={writeOffAmount} onChange={e => setWriteOffAmount(e.target.value)} style={inp} />
          </div>
          <label style={lbl}>Reason</label>
          <textarea value={writeOffReason} onChange={e => setWriteOffReason(e.target.value)} rows={2} style={{ ...inp, width: "100%", resize: "vertical" as const }} />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowWriteOff(null); setApiError("") }} onSubmit={() => writeOff.mutate({ id: showWriteOff.id, amount: Number(writeOffAmount), reason: writeOffReason })} loading={writeOff.isPending} label="Write Off" disabled={!writeOffAmount || Number(writeOffAmount) <= 0} />
        </Overlay>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "var(--hf-text-faint)" : "var(--hf-orange-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
