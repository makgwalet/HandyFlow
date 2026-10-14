// src/pages/ap/SupplierCreditNotesTab.tsx
// Finance patch F10: credit notes received from suppliers - captured, approved when large, applied to bills.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Plus, X, CheckCircle, XCircle, Layers } from "lucide-react"
import { PAYABLE, allocateOldestFirst, allocationError, balanceOf, creditAmountError, money } from "./ap.logic"

interface CreditNote {
  id: string; creditNoteNumber: string; supplierName: string; billId: string | null; billNumber: string | null
  creditDate: string; reason: string; amount: number; vatAmount: number; totalAmount: number
  allocated: number; available: number; status: string; rejectionReason: string | null
}
interface BillRow {
  id: string; supplierName: string; billNumber: string; dueDate: string; status: string
  totalAmount: number; paidAmount?: number; creditedAmount?: number; balance?: number; category: string
}

const STATUS: Record<string, { label: string; color: string; bg: string }> = {
  PENDING_APPROVAL: { label: "Waiting for approval", color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
  ISSUED:           { label: "Issued",               color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  REJECTED:         { label: "Rejected",             color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
}
const CATEGORIES = ["RENT","UTILITIES","FUEL","SALARY","PROFESSIONAL_FEES","EQUIPMENT","MAINTENANCE","INSURANCE","SUBSCRIPTIONS","MARKETING","OTHER"]

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }
const btnP: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const btnS: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "7px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 12, cursor: "pointer", color: "var(--hf-text-secondary)", fontWeight: 500 }
const overlay: React.CSSProperties = { position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100 }
const dialog: React.CSSProperties = { background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 480, maxWidth: "94vw", maxHeight: "90vh", overflow: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }

const fmtDate = (d: any) => d ? new Date(d + (String(d).includes("T") ? "" : "T00:00:00")).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const msg = (e: any, fallback: string) => e?.response?.data?.message || fallback

export function SupplierCreditNotesTab({ onRefreshSummary }: { onRefreshSummary?: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermission("AP_MANAGE")
  const canApprove = usePermission("AP_APPROVE")
  const canPay = usePermission("AP_PAY")
  const [showCreate, setShowCreate] = useState(false)
  const [rejecting, setRejecting] = useState<CreditNote | null>(null)
  const [rejectReason, setRejectReason] = useState("")
  const [applying, setApplying] = useState<CreditNote | null>(null)
  const [applyAmounts, setApplyAmounts] = useState<Record<string, string>>({})
  const [error, setError] = useState("")
  const [listError, setListError] = useState("")

  const today = new Date().toISOString().split("T")[0]
  const blank = { billId: "", supplierName: "", creditNoteNumber: "", creditDate: today, reason: "", category: "OTHER", amount: "", vatAmount: "0" }
  const [form, setForm] = useState(blank)
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["ap-credit-notes"] })
    qc.invalidateQueries({ queryKey: ["ap-bills"] })
    onRefreshSummary?.()
  }

  const { data: notes = [], isLoading } = useQuery<CreditNote[]>({
    queryKey: ["ap-credit-notes"],
    queryFn: async () => (await apiClient.get("/api/v1/ap/credit-notes")).data?.data ?? [],
  })
  const { data: bills = [] } = useQuery<BillRow[]>({
    queryKey: ["ap-bills", "for-credit"],
    queryFn: async () => {
      const p = (await apiClient.get("/api/v1/ap/bills?size=200")).data?.data
      return p?.content ?? p ?? []
    },
    enabled: showCreate || !!applying,
  })
  const { data: supplierNames = [] } = useQuery<string[]>({
    queryKey: ["ap-known-supplier-names"],
    queryFn: async () => (await apiClient.get("/api/v1/ap/suppliers/known-names")).data?.data ?? [],
    enabled: showCreate,
  })

  const create = useMutation({
    mutationFn: () => apiClient.post("/api/v1/ap/credit-notes", {
      creditNoteNumber: form.creditNoteNumber.trim(), creditDate: form.creditDate, reason: form.reason.trim(),
      billId: form.billId || null, supplierName: form.billId ? null : form.supplierName,
      category: form.billId ? null : form.category,
      amount: Number(form.amount.replace(",", ".")), vatAmount: Number((form.vatAmount || "0").replace(",", ".")),
    }),
    onSuccess: () => { refresh(); setShowCreate(false); setForm(blank); setError("") },
    onError: (e: any) => setError(msg(e, "Could not capture the credit note")),
  })
  const approve = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/ap/credit-notes/${id}/approve`),
    onSuccess: () => { refresh(); setListError("") },
    onError: (e: any) => setListError(msg(e, "Could not approve")),
  })
  const reject = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/ap/credit-notes/${rejecting!.id}/reject`, { reason: rejectReason.trim() }),
    onSuccess: () => { refresh(); setRejecting(null); setRejectReason(""); setListError("") },
    onError: (e: any) => setError(msg(e, "Could not reject")),
  })
  const allocate = useMutation({
    mutationFn: (lines: { billId: string; amount: number }[]) => apiClient.post(`/api/v1/ap/credit-notes/${applying!.id}/allocate`, { lines }),
    onSuccess: () => { refresh(); setApplying(null); setApplyAmounts({}); setError("") },
    onError: (e: any) => setError(msg(e, "Could not apply the credit")),
  })

  const approvedBills = bills.filter(b => ["APPROVED", "OVERDUE", "PARTIALLY_PAID", "PAID"].includes(b.status))
  const openFor = (c: CreditNote) => bills
    .filter(b => b.supplierName.toLowerCase() === c.supplierName.toLowerCase() && PAYABLE.includes(b.status) && balanceOf(b) > 0)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))

  const startApply = (c: CreditNote) => { setApplying(c); setApplyAmounts({}); setError("") }
  const suggested = () => {
    if (!applying) return
    const lines = allocateOldestFirst(applying.available, openFor(applying).map(b => ({ id: b.id, balance: balanceOf(b) })))
    setApplyAmounts(Object.fromEntries(lines.map(l => [l.billId, String(l.amount)])))
  }
  const applyLines = () => Object.entries(applyAmounts).filter(([, v]) => v.trim() !== "")
    .map(([billId, v]) => ({ billId, amount: Number(v.replace(",", ".")) }))
  const applyProblem = applying
    ? allocationError(applyLines(), applying.available, openFor(applying).map(b => ({ id: b.id, balance: balanceOf(b) }))) : null

  const createProblem = creditAmountError(form.amount, form.vatAmount)
    ?? (form.creditNoteNumber.trim() === "" ? "Enter the supplier's credit note number" : null)
    ?? (form.reason.trim().length < 5 ? "Say what the credit is for (at least 5 characters)" : null)
    ?? (!form.billId && form.supplierName === "" ? "Choose the bill or the supplier this credit note is from" : null)

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
          Credit notes received from suppliers. Large ones wait for a second person; once issued they reduce what is owed.
        </div>
        {canManage && <button style={btnP} onClick={() => { setShowCreate(true); setError("") }}><Plus size={13} /> Capture credit note</button>}
      </div>

      {listError && <div role="alert" style={{ marginBottom: 12, padding: "8px 12px", background: "var(--hf-danger-soft)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{listError}</div>}

      {isLoading ? <div style={{ color: "var(--hf-text-faint)" }}>Loading...</div> : notes.length === 0 ? (
        <div style={{ padding: 28, textAlign: "center", color: "var(--hf-text-faint)", border: "1px dashed var(--hf-border)", borderRadius: 12 }}>No supplier credit notes yet.</div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase" }}>
            {["Credit note", "Supplier", "Bill", "Date", "Total", "Credit left", "Status", ""].map(h => <th key={h} style={{ padding: "8px 10px" }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {notes.map(c => {
              const st = STATUS[c.status] ?? STATUS.PENDING_APPROVAL
              return (
                <tr key={c.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={{ padding: "10px" }}><div style={{ fontWeight: 700 }}>{c.creditNoteNumber}</div><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{c.reason}</div></td>
                  <td style={{ padding: "10px" }}>{c.supplierName}</td>
                  <td style={{ padding: "10px" }}>{c.billNumber ?? "—"}</td>
                  <td style={{ padding: "10px" }}>{fmtDate(c.creditDate)}</td>
                  <td style={{ padding: "10px", fontWeight: 700 }}>{money(c.totalAmount)}</td>
                  <td style={{ padding: "10px" }}>{c.status === "ISSUED" ? money(c.available) : "—"}</td>
                  <td style={{ padding: "10px" }}>
                    <span style={{ padding: "3px 9px", borderRadius: 99, fontSize: 11, fontWeight: 700, color: st.color, background: st.bg }}>{st.label}</span>
                    {c.status === "REJECTED" && c.rejectionReason && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>{c.rejectionReason}</div>}
                  </td>
                  <td style={{ padding: "10px", whiteSpace: "nowrap" }}>
                    {c.status === "PENDING_APPROVAL" && canApprove && <>
                      <button style={{ ...btnS, marginRight: 6 }} aria-label={`Approve credit note ${c.creditNoteNumber}`} onClick={() => approve.mutate(c.id)} disabled={approve.isPending}><CheckCircle size={13} /> Approve</button>
                      <button style={btnS} aria-label={`Reject credit note ${c.creditNoteNumber}`} onClick={() => { setRejecting(c); setRejectReason(""); setError("") }}><XCircle size={13} /> Reject</button>
                    </>}
                    {c.status === "ISSUED" && Number(c.available) > 0 && canPay &&
                      <button style={btnS} aria-label={`Apply credit note ${c.creditNoteNumber} to bills`} onClick={() => startApply(c)}><Layers size={13} /> Apply to bills</button>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {showCreate && (
        <div style={overlay}><div style={dialog} role="dialog" aria-label="Capture supplier credit note">
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16 }}>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Capture supplier credit note</h3>
            <button aria-label="Close" onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} /></button>
          </div>
          <div style={{ display: "grid", gap: 12 }}>
            <div><label style={lbl} htmlFor="cn-bill">Bill it is for (optional)</label>
              <select id="cn-bill" style={inp} value={form.billId} onChange={e => f("billId", e.target.value)}>
                <option value="">No bill - keep as credit with the supplier</option>
                {approvedBills.map(b => <option key={b.id} value={b.id}>{b.supplierName} - {b.billNumber} ({money(b.totalAmount)})</option>)}
              </select></div>
            {!form.billId && <>
              <div><label style={lbl} htmlFor="cn-supplier">Supplier</label>
                <select id="cn-supplier" style={inp} value={form.supplierName} onChange={e => f("supplierName", e.target.value)}>
                  <option value="">Choose supplier</option>
                  {supplierNames.map(n => <option key={n} value={n}>{n}</option>)}
                </select></div>
              <div><label style={lbl} htmlFor="cn-cat">Expense it reverses</label>
                <select id="cn-cat" style={inp} value={form.category} onChange={e => f("category", e.target.value)}>
                  {CATEGORIES.map(c => <option key={c} value={c}>{c.replace("_", " ").toLowerCase()}</option>)}
                </select></div>
            </>}
            <div><label style={lbl} htmlFor="cn-number">Supplier's credit note number</label>
              <input id="cn-number" style={inp} value={form.creditNoteNumber} onChange={e => f("creditNoteNumber", e.target.value)} /></div>
            <div><label style={lbl} htmlFor="cn-date">Credit note date</label>
              <input id="cn-date" type="date" style={inp} value={form.creditDate} onChange={e => f("creditDate", e.target.value)} /></div>
            <div><label style={lbl} htmlFor="cn-reason">What is it for</label>
              <input id="cn-reason" style={inp} value={form.reason} onChange={e => f("reason", e.target.value)} placeholder="Returned goods, price correction..." /></div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div><label style={lbl} htmlFor="cn-amount">Amount (before VAT)</label>
                <input id="cn-amount" inputMode="decimal" style={inp} value={form.amount} onChange={e => f("amount", e.target.value)} /></div>
              <div><label style={lbl} htmlFor="cn-vat">VAT</label>
                <input id="cn-vat" inputMode="decimal" style={inp} value={form.vatAmount} onChange={e => f("vatAmount", e.target.value)} /></div>
            </div>
          </div>
          {(error || (form.amount !== "" && createProblem)) && <div role="alert" style={{ marginTop: 12, padding: "8px 12px", background: "var(--hf-danger-soft)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error || createProblem}</div>}
          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 18 }}>
            <button style={btnS} onClick={() => setShowCreate(false)}>Cancel</button>
            <button style={{ ...btnP, opacity: createProblem ? 0.5 : 1 }} disabled={!!createProblem || create.isPending} onClick={() => { setError(""); create.mutate() }}>
              {create.isPending ? "Saving..." : "Capture"}
            </button>
          </div>
        </div></div>
      )}

      {rejecting && (
        <div style={overlay}><div style={dialog} role="dialog" aria-label="Reject credit note">
          <h3 style={{ margin: "0 0 12px", fontSize: 16, fontWeight: 800 }}>Reject credit note {rejecting.creditNoteNumber}</h3>
          <label style={lbl} htmlFor="cn-reject">Why is it rejected</label>
          <input id="cn-reject" style={inp} value={rejectReason} onChange={e => setRejectReason(e.target.value)} />
          {error && <div role="alert" style={{ marginTop: 10, padding: "8px 12px", background: "var(--hf-danger-soft)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 16 }}>
            <button style={btnS} onClick={() => setRejecting(null)}>Cancel</button>
            <button style={{ ...btnP, background: "var(--hf-danger)", opacity: rejectReason.trim().length < 5 ? 0.5 : 1 }}
              disabled={rejectReason.trim().length < 5 || reject.isPending} onClick={() => { setError(""); reject.mutate() }}>Reject</button>
          </div>
        </div></div>
      )}

      {applying && (
        <div style={overlay}><div style={dialog} role="dialog" aria-label="Apply credit to bills">
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Apply credit to {applying.supplierName}'s bills</h3>
            <button aria-label="Close" onClick={() => setApplying(null)} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} /></button>
          </div>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 12 }}>{money(applying.available)} of credit left on {applying.creditNoteNumber}.</div>
          {openFor(applying).length === 0 ? <div style={{ color: "var(--hf-text-faint)" }}>This supplier has no open bills.</div> : (
            <div style={{ display: "grid", gap: 8 }}>
              {openFor(applying).map(b => (
                <div key={b.id} style={{ display: "grid", gridTemplateColumns: "1fr 120px", gap: 10, alignItems: "center" }}>
                  <div><div style={{ fontWeight: 600 }}>{b.billNumber}</div><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Due {fmtDate(b.dueDate)} · owes {money(balanceOf(b))}</div></div>
                  <input aria-label={`Amount to apply to ${b.billNumber}`} inputMode="decimal" style={inp} value={applyAmounts[b.id] ?? ""}
                    onChange={e => setApplyAmounts(p => ({ ...p, [b.id]: e.target.value }))} />
                </div>
              ))}
            </div>
          )}
          {(error || (applyLines().length > 0 && applyProblem)) && <div role="alert" style={{ marginTop: 12, padding: "8px 12px", background: "var(--hf-danger-soft)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error || applyProblem}</div>}
          <div style={{ display: "flex", gap: 10, justifyContent: "space-between", marginTop: 16 }}>
            <button style={btnS} onClick={suggested}>Oldest first</button>
            <div style={{ display: "flex", gap: 10 }}>
              <button style={btnS} onClick={() => setApplying(null)}>Cancel</button>
              <button style={{ ...btnP, opacity: applyProblem ? 0.5 : 1 }} disabled={!!applyProblem || allocate.isPending}
                onClick={() => { setError(""); allocate.mutate(applyLines()) }}>Apply credit</button>
            </div>
          </div>
        </div></div>
      )}
    </div>
  )
}
