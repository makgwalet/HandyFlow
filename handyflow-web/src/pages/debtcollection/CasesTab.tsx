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
// No staff/customer picker is wired up for this module (no confirmed
// user-list or customer-search endpoint in scope here) — customerId and
// assignedToUserId are plain UUID text inputs, same flagged simplification
// used in Module 1's Assign DSAR flow.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  Plus, Landmark, ChevronDown, ChevronUp, X, UserPlus, CalendarClock,
  ArrowRightCircle, Flag, AlertOctagon, Trash2, Download, FileDown,
  Phone, Wallet, Paperclip, FileText, AlertCircle, Link2, RefreshCw,
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
interface OutstandingInvoice { id: string; invoiceNumber: string; dueDate: string | null; total: number; amountPaid: number; outstanding: number }
interface Evidence { id: string; fileName: string; contentType: string; fileSizeBytes: number; evidenceType: string; status: string; uploadedByName: string; createdAt: string }

const STATUSES = ["OPEN", "DEMAND_SENT", "PAYMENT_PLAN_ACTIVE", "DISPUTED", "HANDED_TO_LEGAL", "SETTLED", "WRITTEN_OFF", "CLOSED"]
const TERMINAL = new Set(["SETTLED", "WRITTEN_OFF", "CLOSED"])
const CLOSURE_REASONS = ["PAID_IN_FULL", "SETTLED_PARTIAL", "WRITTEN_OFF", "HANDED_TO_LEGAL", "DISPUTE_UPHELD", "OTHER"]
const CONTACT_METHODS = ["PHONE_CALL", "EMAIL", "SMS", "WHATSAPP", "LETTER", "IN_PERSON", "OTHER"]
const CONTACT_OUTCOMES = ["NO_ANSWER", "LEFT_MESSAGE", "PROMISE_TO_PAY", "DISPUTED", "REFUSED_TO_PAY", "ALREADY_PAID", "WRONG_CONTACT_DETAILS", "OTHER"]
const PLAN_FREQUENCIES = ["WEEKLY", "FORTNIGHTLY", "MONTHLY"]

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string }> = {
  OPEN:                { color: "#9A3412", bg: "#FFEDD5", border: "#FED7AA", label: "Open"              },
  DEMAND_SENT:         { color: "#B45309", bg: "#FEF3C7", border: "#FDE68A", label: "Demand Sent"       },
  PAYMENT_PLAN_ACTIVE: { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", label: "Payment Plan"      },
  DISPUTED:            { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", label: "Disputed"          },
  HANDED_TO_LEGAL:     { color: "#7C3AED", bg: "#F5F3FF", border: "#DDD6FE", label: "Handed to Legal"   },
  SETTLED:             { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", label: "Settled"           },
  WRITTEN_OFF:         { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", label: "Written Off"       },
  CLOSED:              { color: "#334155", bg: "#F8FAFC", border: "#E2E8F0", label: "Closed"            },
}
const PLAN_STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:    { color: "#1D4ED8", bg: "#EFF6FF", label: "Active"    },
  COMPLETED: { color: "#166534", bg: "#DCFCE7", label: "Completed" },
  DEFAULTED: { color: "#DC2626", bg: "#FEF2F2", label: "Defaulted" },
  CANCELLED: { color: "#64748B", bg: "#F1F5F9", label: "Cancelled" },
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtBytes = (b: number) => b < 1024 ? `${b} B` : b < 1024 * 1024 ? `${(b / 1024).toFixed(1)} KB` : `${(b / (1024 * 1024)).toFixed(1)} MB`
const daysUntil = (d: string) => Math.ceil((new Date(d).getTime() - Date.now()) / 86400000)

const EMPTY_NEW_CASE = { mode: "customer" as "customer" | "manual", customerId: "", debtorName: "", debtorEmail: "", debtorPhone: "", openedDate: new Date().toISOString().split("T")[0], assignedToUserName: "", notes: "", selectedInvoiceIds: [] as string[], manualInvoiceIds: "" }

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

  const inp: React.CSSProperties = { width: "100%", padding: "7px 10px", border: "1.5px solid #E2E8F0", borderRadius: 7, fontSize: 12, boxSizing: "border-box" as const }
  const activePlan = plans.find(p => p.status === "ACTIVE")

  return (
    <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
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
            <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
            <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
          </div>
        ))}
      </div>
      {c.notes && <div style={{ marginBottom: 14, padding: "8px 12px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, color: "#374151" }}>{c.notes}</div>}

      {/* Contact log */}
      <div style={{ marginBottom: 18, paddingTop: 14, borderTop: "1px solid #E2E8F0" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}><Phone size={12} />Contact log</div>
          {canManage && <button onClick={() => setShowContactForm(s => !s)} style={{ fontSize: 11, color: "#9A3412", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>{showContactForm ? "Cancel" : "+ Record contact"}</button>}
        </div>
        {showContactForm && (
          <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 10 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
              <div><label style={{ fontSize: 11, color: "#64748B" }}>Date</label><input type="date" value={contactForm.contactDate} onChange={e => setContactForm(f => ({ ...f, contactDate: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "#64748B" }}>Method</label><select value={contactForm.contactMethod} onChange={e => setContactForm(f => ({ ...f, contactMethod: e.target.value }))} style={{ ...inp, background: "#fff" }}>{CONTACT_METHODS.map(m => <option key={m} value={m}>{m.replace(/_/g, " ")}</option>)}</select></div>
              <div><label style={{ fontSize: 11, color: "#64748B" }}>Outcome</label><select value={contactForm.outcome} onChange={e => setContactForm(f => ({ ...f, outcome: e.target.value }))} style={{ ...inp, background: "#fff" }}>{CONTACT_OUTCOMES.map(o => <option key={o} value={o}>{o.replace(/_/g, " ")}</option>)}</select></div>
            </div>
            {contactForm.outcome === "PROMISE_TO_PAY" && (
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 8 }}>
                <div><label style={{ fontSize: 11, color: "#64748B" }}>Promised date *</label><input type="date" value={contactForm.promisedPaymentDate} onChange={e => setContactForm(f => ({ ...f, promisedPaymentDate: e.target.value }))} style={inp} /></div>
                <div><label style={{ fontSize: 11, color: "#64748B" }}>Promised amount *</label><input type="number" value={contactForm.promisedPaymentAmount} onChange={e => setContactForm(f => ({ ...f, promisedPaymentAmount: e.target.value }))} style={inp} /></div>
              </div>
            )}
            <textarea value={contactForm.notes} onChange={e => setContactForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Notes..." style={{ ...inp, resize: "vertical" as const, marginBottom: 8 }} />
            <button onClick={() => recordContact.mutate({ contactDate: contactForm.contactDate, contactMethod: contactForm.contactMethod, outcome: contactForm.outcome, notes: contactForm.notes || null, promisedPaymentDate: contactForm.promisedPaymentDate || null, promisedPaymentAmount: contactForm.promisedPaymentAmount ? Number(contactForm.promisedPaymentAmount) : null })}
              disabled={recordContact.isPending || (contactForm.outcome === "PROMISE_TO_PAY" && (!contactForm.promisedPaymentDate || !contactForm.promisedPaymentAmount))}
              style={{ padding: "6px 14px", background: "#9A3412", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
              {recordContact.isPending ? "Saving..." : "Record"}
            </button>
          </div>
        )}
        {contacts.length === 0 ? <div style={{ fontSize: 12, color: "#94A3B8" }}>No contact recorded yet.</div> : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {contacts.map(l => (
              <div key={l.id} style={{ padding: "8px 12px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ fontWeight: 700, color: "#0F172A" }}>{fmtDate(l.contactDate)} · {l.contactMethod.replace(/_/g, " ")}</span>
                  <span style={{ fontWeight: 700, color: l.outcome === "PROMISE_TO_PAY" ? "#166534" : l.outcome === "DISPUTED" ? "#DC2626" : "#64748B" }}>{l.outcome.replace(/_/g, " ")}</span>
                </div>
                {l.notes && <div style={{ color: "#475569", marginTop: 3 }}>{l.notes}</div>}
                {l.promisedPaymentDate && <div style={{ color: "#166534", marginTop: 3 }}>Promised {fmtR(l.promisedPaymentAmount)} by {fmtDate(l.promisedPaymentDate)}</div>}
                <div style={{ color: "#94A3B8", marginTop: 3 }}>{l.recordedByUserName ?? "—"}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Payment plan */}
      <div style={{ marginBottom: 18, paddingTop: 14, borderTop: "1px solid #E2E8F0" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}><Wallet size={12} />Payment plans</div>
          {canManage && !activePlan && <button onClick={() => setShowPlanForm(s => !s)} style={{ fontSize: 11, color: "#9A3412", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>{showPlanForm ? "Cancel" : "+ Propose plan"}</button>}
        </div>
        {showPlanForm && (
          <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 10 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
              <div><label style={{ fontSize: 11, color: "#64748B" }}>Total agreed *</label><input type="number" value={planForm.totalAgreedAmount} onChange={e => setPlanForm(f => ({ ...f, totalAgreedAmount: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "#64748B" }}>Installment *</label><input type="number" value={planForm.installmentAmount} onChange={e => setPlanForm(f => ({ ...f, installmentAmount: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "#64748B" }}># Installments *</label><input type="number" value={planForm.numberOfInstallments} onChange={e => setPlanForm(f => ({ ...f, numberOfInstallments: e.target.value }))} style={inp} /></div>
              <div><label style={{ fontSize: 11, color: "#64748B" }}>Frequency</label><select value={planForm.frequency} onChange={e => setPlanForm(f => ({ ...f, frequency: e.target.value }))} style={{ ...inp, background: "#fff" }}>{PLAN_FREQUENCIES.map(f => <option key={f} value={f}>{f}</option>)}</select></div>
              <div><label style={{ fontSize: 11, color: "#64748B" }}>Start date</label><input type="date" value={planForm.startDate} onChange={e => setPlanForm(f => ({ ...f, startDate: e.target.value }))} style={inp} /></div>
            </div>
            <textarea value={planForm.notes} onChange={e => setPlanForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Notes..." style={{ ...inp, resize: "vertical" as const, marginBottom: 8 }} />
            <button onClick={() => proposePlan.mutate({ totalAgreedAmount: Number(planForm.totalAgreedAmount), installmentAmount: Number(planForm.installmentAmount), frequency: planForm.frequency, startDate: planForm.startDate, numberOfInstallments: Number(planForm.numberOfInstallments), notes: planForm.notes || null })}
              disabled={proposePlan.isPending || !planForm.totalAgreedAmount || !planForm.installmentAmount || !planForm.numberOfInstallments}
              style={{ padding: "6px 14px", background: "#9A3412", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
              {proposePlan.isPending ? "Saving..." : "Propose Plan"}
            </button>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 6 }}>Proposing a plan also moves the case to Payment Plan status.</div>
          </div>
        )}
        {plans.length === 0 ? <div style={{ fontSize: 12, color: "#94A3B8" }}>No payment plan proposed yet.</div> : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {plans.map(p => {
              const cfg = PLAN_STATUS_CFG[p.status] ?? PLAN_STATUS_CFG.ACTIVE
              return (
                <div key={p.id} style={{ padding: "10px 12px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
                    <span style={{ fontWeight: 700, color: "#0F172A" }}>{fmtR(p.totalAgreedAmount)} over {p.numberOfInstallments} × {fmtR(p.installmentAmount)} ({p.frequency.toLowerCase()})</span>
                    <span style={{ fontWeight: 700, color: cfg.color, background: cfg.bg, padding: "2px 8px", borderRadius: 20, fontSize: 10 }}>{cfg.label}</span>
                  </div>
                  <div style={{ color: "#64748B" }}>{p.installmentsPaid} / {p.numberOfInstallments} paid{p.nextDueDate ? ` · next due ${fmtDate(p.nextDueDate)}` : ""}</div>
                  {canManage && p.status === "ACTIVE" && (
                    <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
                      <button onClick={() => markPaid.mutate(p.id)} style={{ padding: "4px 10px", background: "#DCFCE7", color: "#166534", border: "none", borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>Mark installment paid</button>
                      <button onClick={() => { if (confirm("Mark this plan as defaulted?")) markDefaulted.mutate(p.id) }} style={{ padding: "4px 10px", background: "#FEF2F2", color: "#DC2626", border: "none", borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>Mark defaulted</button>
                      <button onClick={() => { if (confirm("Cancel this plan?")) cancelPlan.mutate(p.id) }} style={{ padding: "4px 10px", background: "#F1F5F9", color: "#64748B", border: "none", borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Evidence */}
      <div style={{ paddingTop: 14, borderTop: "1px solid #E2E8F0" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}><FileText size={12} />Evidence — demand letters, AOD, correspondence</div>
        {evidence.length === 0 ? (
          <div style={{ fontSize: 12, color: "#94A3B8", marginBottom: 10 }}>No documents attached yet.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
            {evidence.map(ev => (
              <div key={ev.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "7px 10px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                <FileText size={13} color="#64748B" />
                <span style={{ fontWeight: 600, color: "#0F172A" }}>{ev.fileName}</span>
                <span style={{ color: "#94A3B8" }}>{ev.evidenceType} · {fmtBytes(ev.fileSizeBytes)}</span>
                <span style={{ marginLeft: "auto", color: "#94A3B8" }}>{ev.uploadedByName} · {fmtDate(ev.createdAt)}</span>
              </div>
            ))}
          </div>
        )}
        {canManage && (
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <input type="text" value={evidenceType} onChange={e => setEvidenceType(e.target.value)} placeholder="Type e.g. AOD" style={{ flex: 1, padding: "7px 10px", border: "1.5px solid #E2E8F0", borderRadius: 7, fontSize: 12 }} />
            <input type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 12, flex: 1 }} />
            <button onClick={() => attachEvidence.mutate()} disabled={!file || !evidenceType.trim() || attachEvidence.isPending}
              style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 12px", background: (!file || !evidenceType.trim()) ? "#CBD5E1" : "#9A3412", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: (!file || !evidenceType.trim()) ? "not-allowed" : "pointer" }}>
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
  const [newCase, setNewCase] = useState(EMPTY_NEW_CASE)
  const [outstandingInvoices, setOutstandingInvoices] = useState<OutstandingInvoice[]>([])
  const [loadingInvoices, setLoadingInvoices] = useState(false)

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

  const openManual = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/debtcollection/cases", body),
    onSuccess: () => { invalidate(); setShowNewCase(false); setNewCase(EMPTY_NEW_CASE); setOutstandingInvoices([]); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to open case"),
  })
  const openForCustomer = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/debtcollection/cases/open-for-customer", body),
    onSuccess: () => { invalidate(); setShowNewCase(false); setNewCase(EMPTY_NEW_CASE); setOutstandingInvoices([]); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to open case"),
  })

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

  const loadOutstandingInvoices = async () => {
    if (!newCase.customerId.trim()) return
    setLoadingInvoices(true)
    try {
      const res = await apiClient.get(`/api/v1/debtcollection/cases/outstanding-invoices?customerId=${newCase.customerId.trim()}`)
      setOutstandingInvoices(unwrap(res))
    } catch (e: any) {
      setApiError(e.response?.data?.message ?? "Failed to load outstanding invoices")
    } finally {
      setLoadingInvoices(false)
    }
  }

  const toggleInvoice = (id: string) => setNewCase(f => ({ ...f, selectedInvoiceIds: f.selectedInvoiceIds.includes(id) ? f.selectedInvoiceIds.filter(x => x !== id) : [...f.selectedInvoiceIds, id] }))

  const stats = [
    { label: "Total",           value: cases.length,                                                    color: "#9A3412" },
    { label: "Open",            value: cases.filter(c => !TERMINAL.has(c.status)).length,                color: "#B45309" },
    { label: "On plan",         value: cases.filter(c => c.status === "PAYMENT_PLAN_ACTIVE").length,     color: "#1D4ED8" },
    { label: "Outstanding",     value: fmtR(cases.filter(c => !TERMINAL.has(c.status)).reduce((s, c) => s + (c.totalOutstanding ?? 0), 0)), color: "#DC2626" },
  ]

  const StatusBadge = ({ status }: { status: string }) => {
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.OPEN
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>{cfg.label}</span>
  }
  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "#9A3412" : STATUS_CFG[s]?.color ?? "#9A3412") : "#F1F5F9",
                color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={downloadCaseRegisterPdf} style={{ display: "flex", alignItems: "center", gap: 6, background: "#fff", color: "#9A3412", border: "1px solid #E2E8F0", borderRadius: 8, padding: "9px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Download size={14} /> Export PDF
          </button>
          {canManage && (
            <button onClick={() => { setShowNewCase(true); setNewCase(EMPTY_NEW_CASE); setOutstandingInvoices([]); setApiError("") }}
              style={{ display: "flex", alignItems: "center", gap: 7, background: "#9A3412", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={15} /> Open Case
            </button>
          )}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading cases...</div>
      ) : cases.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Landmark size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No cases found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {cases.map(c => {
            const isOpen = expanded === c.id
            const cfg = STATUS_CFG[c.status] ?? STATUS_CFG.OPEN
            const isTerminal = TERMINAL.has(c.status)
            const overdue = c.nextActionDate && daysUntil(c.nextActionDate) < 0 && !isTerminal
            return (
              <div key={c.id} style={{ border: `1px solid ${overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 44, height: 44, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Landmark size={18} color={cfg.color} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{c.caseNumber}</span>
                        <span style={{ fontSize: 14, color: "#64748B" }}>{c.debtorName}</span>
                        {overdue && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20 }}>FOLLOW-UP OVERDUE</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        Opened {fmtDate(c.openedDate)}{c.assignedToUserName ? ` · ${c.assignedToUserName}` : ""}{c.nextActionDate ? ` · Next action ${fmtDate(c.nextActionDate)}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>{fmtR(c.totalOutstanding)}</div>
                    </div>
                    <StatusBadge status={c.status} />
                    {canManage && !isTerminal && (
                      <div style={{ display: "flex", gap: 5 }}>
                        <button onClick={() => { setShowAssign(c); setAssignUserId(c.assignedToUserId ?? ""); setAssignUserName(c.assignedToUserName ?? "") }} title="Assign" style={{ background: "#F5F3FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#7C3AED" }}><UserPlus size={13} /></button>
                        <button onClick={() => { setShowSchedule(c); setNextActionDate(c.nextActionDate ?? "") }} title="Schedule next action" style={{ background: "#FFFBEB", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#D97706" }}><CalendarClock size={13} /></button>
                        <button onClick={() => { setShowAdvance(c); setNewStatus("") }} title="Advance status" style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><ArrowRightCircle size={13} /></button>
                        <button onClick={() => downloadDemandLetter(c.id, c.caseNumber)} title="Demand letter PDF" style={{ background: "#F1F5F9", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#334155" }}><FileDown size={13} /></button>
                        <button onClick={() => { setShowClose(c); setClosureReason("PAID_IN_FULL"); setOutcomeNotes("") }} title="Close case" style={{ background: "#F1F5F9", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#334155" }}><Flag size={13} /></button>
                        {canAdmin && <button onClick={() => { setShowWriteOff(c); setWriteOffAmount(String(c.totalOutstanding ?? "")); setWriteOffReason("") }} title="Write off (Admin)" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><AlertOctagon size={13} /></button>}
                        {canAdmin && <button onClick={() => { if (confirm(`Delete case "${c.caseNumber}"?`)) deleteCase.mutate(c.id) }} title="Delete" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>}
                      </div>
                    )}
                    <button onClick={() => setExpanded(isOpen ? null : c.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
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

      {showNewCase && (
        <Overlay onClose={() => { setShowNewCase(false); setApiError("") }}>
          <MHead title="Open Debt Collection Case" onClose={() => { setShowNewCase(false); setApiError("") }} />
          <div style={{ display: "flex", gap: 6, marginBottom: 18 }}>
            <button onClick={() => setNewCase(f => ({ ...f, mode: "customer" }))} style={{ flex: 1, padding: "8px", borderRadius: 8, border: `2px solid ${newCase.mode === "customer" ? "#9A3412" : "#E2E8F0"}`, background: newCase.mode === "customer" ? "#FFEDD5" : "#fff", color: newCase.mode === "customer" ? "#9A3412" : "#64748B", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>From CRM Customer</button>
            <button onClick={() => setNewCase(f => ({ ...f, mode: "manual" }))} style={{ flex: 1, padding: "8px", borderRadius: 8, border: `2px solid ${newCase.mode === "manual" ? "#9A3412" : "#E2E8F0"}`, background: newCase.mode === "manual" ? "#FFEDD5" : "#fff", color: newCase.mode === "manual" ? "#9A3412" : "#64748B", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Manual / Walk-in</button>
          </div>

          {newCase.mode === "customer" ? (
            <>
              <div style={{ padding: "10px 12px", background: "#EEF2FF", border: "1px solid #C7D2FE", borderRadius: 8, fontSize: 12, color: "#4338CA", marginBottom: 14 }}>
                Pulls debtor contact details and ALL outstanding invoices for this customer automatically.
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Customer ID (UUID) *</label>
                <input value={newCase.customerId} onChange={e => setNewCase(f => ({ ...f, customerId: e.target.value }))} placeholder="CRM customer ID" style={inp} />
              </div>
            </>
          ) : (
            <>
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Customer ID (UUID) <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional — link to a CRM customer, or leave blank for a walk-in debtor)</span></label>
                <input value={newCase.customerId} onChange={e => setNewCase(f => ({ ...f, customerId: e.target.value, selectedInvoiceIds: [] }))} style={inp} />
              </div>
              {newCase.customerId.trim() && (
                <div style={{ marginBottom: 14 }}>
                  <button onClick={loadOutstandingInvoices} disabled={loadingInvoices} style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, fontWeight: 600, color: "#9A3412", cursor: "pointer" }}>
                    <RefreshCw size={12} /> {loadingInvoices ? "Loading..." : "Load outstanding invoices"}
                  </button>
                  {outstandingInvoices.length > 0 && (
                    <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 6, maxHeight: 160, overflowY: "auto" }}>
                      {outstandingInvoices.map(inv => (
                        <label key={inv.id} style={{ display: "flex", alignItems: "center", gap: 8, padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, cursor: "pointer" }}>
                          <input type="checkbox" checked={newCase.selectedInvoiceIds.includes(inv.id)} onChange={() => toggleInvoice(inv.id)} />
                          <span style={{ fontWeight: 600 }}>{inv.invoiceNumber}</span>
                          <span style={{ color: "#94A3B8" }}>Due {fmtDate(inv.dueDate)}</span>
                          <span style={{ marginLeft: "auto", fontWeight: 700 }}>{fmtR(inv.outstanding)}</span>
                        </label>
                      ))}
                    </div>
                  )}
                </div>
              )}
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Manual invoice IDs <span style={{ fontWeight: 400, color: "#94A3B8" }}>(comma-separated UUIDs — required if not selecting above)</span></label>
                <input value={newCase.manualInvoiceIds} onChange={e => setNewCase(f => ({ ...f, manualInvoiceIds: e.target.value }))} placeholder="invoice-uuid-1, invoice-uuid-2" style={inp} />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Debtor name *</label>
                <input value={newCase.debtorName} onChange={e => setNewCase(f => ({ ...f, debtorName: e.target.value }))} style={inp} />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
                <div><label style={lbl}>Debtor email</label><input type="email" value={newCase.debtorEmail} onChange={e => setNewCase(f => ({ ...f, debtorEmail: e.target.value }))} style={inp} /></div>
                <div><label style={lbl}>Debtor phone</label><input value={newCase.debtorPhone} onChange={e => setNewCase(f => ({ ...f, debtorPhone: e.target.value }))} style={inp} /></div>
              </div>
            </>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
            <div><label style={lbl}>Opened date</label><input type="date" value={newCase.openedDate} onChange={e => setNewCase(f => ({ ...f, openedDate: e.target.value }))} style={inp} /></div>
            <div><label style={lbl}>Assigned to (name)</label><input value={newCase.assignedToUserName} onChange={e => setNewCase(f => ({ ...f, assignedToUserName: e.target.value }))} style={inp} /></div>
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Notes</label>
            <textarea value={newCase.notes} onChange={e => setNewCase(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp, resize: "vertical" as const }} />
          </div>

          {apiError && <ErrBanner msg={apiError} />}
          <MFoot
            onCancel={() => { setShowNewCase(false); setApiError("") }}
            onSubmit={() => {
              if (newCase.mode === "customer") {
                if (!newCase.customerId.trim()) { setApiError("Customer ID is required"); return }
                openForCustomer.mutate({ customerId: newCase.customerId.trim(), openedDate: newCase.openedDate || null, assignedToUserId: null, assignedToUserName: newCase.assignedToUserName || null, notes: newCase.notes || null })
              } else {
                const invoiceIds = newCase.selectedInvoiceIds.length > 0
                  ? newCase.selectedInvoiceIds
                  : newCase.manualInvoiceIds.split(",").map(s => s.trim()).filter(Boolean)
                if (!newCase.debtorName.trim()) { setApiError("Debtor name is required"); return }
                if (invoiceIds.length === 0) { setApiError("At least one invoice must be selected or entered"); return }
                openManual.mutate({ customerId: newCase.customerId.trim() || null, debtorName: newCase.debtorName, debtorEmail: newCase.debtorEmail || null, debtorPhone: newCase.debtorPhone || null, invoiceIds, openedDate: newCase.openedDate || null, assignedToUserId: null, assignedToUserName: newCase.assignedToUserName || null, notes: newCase.notes || null })
              }
            }}
            loading={openManual.isPending || openForCustomer.isPending}
            label="Open Case"
          />
        </Overlay>
      )}

      {showAssign && (
        <Overlay onClose={() => { setShowAssign(null); setApiError("") }}>
          <MHead title={`Assign — ${showAssign.caseNumber}`} onClose={() => { setShowAssign(null); setApiError("") }} />
          <div style={{ marginBottom: 14 }}><label style={lbl}>User ID (UUID)</label><input value={assignUserId} onChange={e => setAssignUserId(e.target.value)} style={inp} /></div>
          <div><label style={lbl}>User name</label><input value={assignUserName} onChange={e => setAssignUserName(e.target.value)} style={inp} /></div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowAssign(null); setApiError("") }} onSubmit={() => assign.mutate({ id: showAssign.id, userId: assignUserId.trim(), userName: assignUserName.trim() })} loading={assign.isPending} label="Assign" disabled={!assignUserId.trim() || !assignUserName.trim()} />
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
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `2px solid ${sel ? cfg.color : "#E2E8F0"}`, borderRadius: 10, cursor: "pointer", background: sel ? cfg.bg : "#fff", textAlign: "left" as const, width: "100%" }}>
                  <span style={{ fontWeight: 600, color: sel ? cfg.color : "#0F172A" }}>{cfg.label}</span>
                </button>
              )
            })}
          </div>
          <div style={{ fontSize: 11, color: "#94A3B8", marginBottom: 10 }}>Use "Close case" to close — CLOSED can't be set here.</div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowAdvance(null); setApiError("") }} onSubmit={() => advanceStatus.mutate({ id: showAdvance.id, status: newStatus })} loading={advanceStatus.isPending} label="Update Status" disabled={!newStatus} />
        </Overlay>
      )}

      {showClose && (
        <Overlay onClose={() => { setShowClose(null); setApiError("") }}>
          <MHead title={`Close Case — ${showClose.caseNumber}`} onClose={() => { setShowClose(null); setApiError("") }} />
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Closure reason *</label>
            <select value={closureReason} onChange={e => setClosureReason(e.target.value)} style={{ ...inp, background: "#fff" }}>
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
          <div style={{ padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", marginBottom: 14 }}>
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
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#9A3412", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
