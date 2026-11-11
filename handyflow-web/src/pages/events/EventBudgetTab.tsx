// src/pages/events/EventBudgetTab.tsx
//
// Money out for the selected event: the budget (lines, then submit, approve or send back), supplier quotes and commitments, and the invoices and payments
// recorded against them. Reading needs EVENTS_BUDGET_READ (also given by budget-manage and supplier-manage); each action needs its own permission (manage, approve, supplier). The server holds the rules and
// its messages are shown as they come.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { rand } from "./paymentOps"
import {
  alertTitle, approvalOverrun, budgetActions, budgetStatusLabel, canApproveCommitment, canCancelCommitment, canCompleteCommitment, canEditLines, canRecordAgainst, CATEGORIES,
  categoryText, commitmentBody, commitmentProblem, commitmentStatusLabel, costEntryBody, costEntryProblem, lineProblem, varianceTone, type BudgetAction,
} from "./budgetOps"

const field: React.CSSProperties = { ...inputStyle, width: "100%" }
const TONE = { ok: "var(--hf-success-text-strong)", bad: "var(--hf-danger-text)", muted: "var(--hf-text-muted)" } as const
interface Status { status: string; lineCount: number; plannedTotal: number; submittedByName: string | null; approvedByName: string | null; rejectionReason: string | null }
interface Line { id: string; category: string; description: string; planned: number; committed: number; invoiced: number; paid: number; forecast: number; variance: number; overBudget: boolean }
interface Summary { planned: number; committed: number; invoiced: number; paid: number; forecast: number; variance: number; outstanding: number; costPerRegistered: number | null; lines: Line[]; alerts: { type: string; severity: string; message: string; commitmentId: string | null }[] }
interface Commitment { id: string; budgetLineId: string; category: string; vendorId: string | null; vendorName: string | null; description: string; amount: number; status: string; quoteReference: string | null; deliverable: string | null; dueDate: string | null; depositAmount: number | null; invoiced: number; paid: number; overBudgetReason: string | null; cancelledReason: string | null }
interface Entry { id: string; commitmentId: string | null; budgetLineId: string; kind: string; deposit: boolean; amount: number; entryDate: string; reference: string | null; description: string | null; reconciled: boolean; status: string; voidReason: string | null }
interface LineRow { id: string; category: string; description: string; plannedAmount: number; notes: string | null }

type View = "summary" | "commitments" | "costs"
type Dlg = { k: "line"; line?: LineRow } | { k: "workflow"; a: BudgetAction } | { k: "commitment" } | { k: "approve"; c: Commitment } | { k: "complete"; c: Commitment } | { k: "cancel"; c: Commitment } | { k: "cost" } | { k: "void"; e: Entry } | null

function Shell({ title, problem, pending, error, label, onClose, onSubmit, children, width }: { title: string; problem: string | null; pending: boolean; error: unknown; label: string; onClose: () => void; onSubmit: () => void; children: React.ReactNode; width?: number }) {
  return (
    <Modal title={title} onClose={onClose} width={width}>
      {children}
      <ErrorLine error={error} fallback="That could not be completed" />
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={onClose}>Back</button>
        <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || pending} onClick={onSubmit}>{pending ? "Working…" : label}</button>
      </div>
    </Modal>
  )
}
const L = ({ id, children }: { id: string; children: React.ReactNode }) => <label style={{ ...labelStyle, marginTop: 10 }} htmlFor={id}>{children}</label>

export default function EventBudgetTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canRead = usePermissions(["EVENTS_BUDGET_READ", "EVENTS_BUDGET_MANAGE", "EVENTS_SUPPLIER_MANAGE", "EVENTS_MANAGE"])
  const canManage = usePermissions(["EVENTS_BUDGET_MANAGE", "EVENTS_MANAGE"])
  const canApprove = usePermissions(["EVENTS_BUDGET_APPROVE", "EVENTS_MANAGE"])
  const canSupplier = usePermissions(["EVENTS_SUPPLIER_MANAGE", "EVENTS_MANAGE"])
  const [view, setView] = useState<View>("summary")
  const [dlg, setDlg] = useState<Dlg>(null)
  const [f, setF] = useState<Record<string, any>>({})
  const open = (d: Dlg, init: Record<string, any> = {}) => { setDlg(d); setF(init); run.reset() }
  const set = (k: string, v: any) => setF(p => ({ ...p, [k]: v }))
  const base = `/api/v1/events/${eventId}`
  const q = <T,>(key: string, url: string, enabled = true, dflt?: T) => ({ queryKey: [key, eventId], enabled: !!eventId && canRead && enabled, queryFn: async () => { const r = await apiClient.get(url); return (r.data?.data ?? r.data ?? dflt) as T } })

  const { data: status, error } = useQuery<Status>(q("event-budget", `${base}/budget`))
  const { data: summary } = useQuery<Summary>(q("event-budget-summary", `${base}/budget/summary`))
  const { data: lines = [] } = useQuery<LineRow[]>(q("event-budget-lines", `${base}/budget/lines`, true, []))
  const { data: commitments = [] } = useQuery<Commitment[]>(q("event-commitments", `${base}/commitments`, true, []))
  const { data: vendors = [] } = useQuery<any[]>({ queryKey: ["event-vendors-pick", eventId], enabled: !!eventId && canRead && dlg?.k === "commitment", queryFn: async () => { const r = await apiClient.get(`${base}/vendors`); const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] } })
  const { data: entries } = useQuery<{ content: Entry[] }>({ queryKey: ["event-cost-entries", eventId], enabled: !!eventId && canRead && view === "costs", queryFn: async () => { const r = await apiClient.get(`${base}/cost-entries`, { params: { size: 100 } }); return r.data?.data ?? r.data } })

  const refresh = () => ["event-budget", "event-budget-summary", "event-budget-lines", "event-commitments", "event-cost-entries", "event-audit"].forEach(k => qc.invalidateQueries({ queryKey: [k] }))
  const run = useMutation({
    mutationFn: async () => {
      if (!dlg) return
      if (dlg.k === "line") { const body = { category: f.category, description: f.description.trim(), plannedAmount: Number(f.plannedAmount), notes: (f.notes ?? "").trim() || null }; return dlg.line ? apiClient.put(`${base}/budget/lines/${dlg.line.id}`, body) : apiClient.post(`${base}/budget/lines`, body) }
      if (dlg.k === "workflow") return dlg.a === "submit" || dlg.a === "approve" ? apiClient.post(`${base}/budget/${dlg.a}`) : apiClient.post(`${base}/budget/${dlg.a}`, { reason: (f.reason ?? "").trim() })
      if (dlg.k === "commitment") return apiClient.post(`${base}/commitments`, commitmentBody(f as any))
      if (dlg.k === "approve") return apiClient.post(`${base}/commitments/${dlg.c.id}/approve`, { overrideReason: (f.reason ?? "").trim() || null })
      if (dlg.k === "complete") return apiClient.post(`${base}/commitments/${dlg.c.id}/complete`)
      if (dlg.k === "cancel") return apiClient.post(`${base}/commitments/${dlg.c.id}/cancel`, { reason: (f.reason ?? "").trim() })
      if (dlg.k === "cost") return apiClient.post(`${base}/cost-entries`, costEntryBody(f as any))
      return apiClient.post(`${base}/cost-entries/${dlg.e.id}/void`, { reason: (f.reason ?? "").trim() })
    },
    onSuccess: () => { refresh(); setDlg(null) },
  })
  const deleteLine = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/budget/lines/${id}`), onSuccess: refresh })

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  if (!canRead) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to the budget of events.</p>

  const st = status?.status ?? "NOT_STARTED"
  const editable = canEditLines(st)
  const actions = budgetActions(st, status?.lineCount ?? 0)
  const lineById = new Map((summary?.lines ?? []).map(l => [l.id, l]))
  const ACTION_LABEL: Record<BudgetAction, string> = { submit: "Submit for approval", approve: "Approve budget", reject: "Send back", reopen: "Reopen" }

  let title = "", label = "", problem: string | null = null, body: React.ReactNode = null, width = 440
  if (dlg?.k === "line") {
    title = dlg.line ? "Edit budget line" : "Add budget line"; label = "Save line"; problem = lineProblem({ category: f.category ?? "", description: f.description ?? "", plannedAmount: f.plannedAmount ?? "" })
    body = <>
      <L id="bl-cat">Category *</L><select id="bl-cat" style={field} value={f.category ?? ""} onChange={e => set("category", e.target.value)}><option value="">Choose…</option>{CATEGORIES.map(c => <option key={c} value={c}>{categoryText(c)}</option>)}</select>
      <L id="bl-desc">Description *</L><input id="bl-desc" style={field} maxLength={200} value={f.description ?? ""} onChange={e => set("description", e.target.value)} />
      <L id="bl-amt">Planned amount (R) *</L><input id="bl-amt" style={field} inputMode="decimal" value={f.plannedAmount ?? ""} onChange={e => set("plannedAmount", e.target.value)} />
      <L id="bl-notes">Notes</L><input id="bl-notes" style={field} maxLength={500} value={f.notes ?? ""} onChange={e => set("notes", e.target.value)} />
    </>
  } else if (dlg?.k === "workflow") {
    const needsReason = dlg.a === "reject" || dlg.a === "reopen"
    title = ACTION_LABEL[dlg.a]; label = ACTION_LABEL[dlg.a]; problem = needsReason && (f.reason ?? "").trim().length < 3 ? "Give a reason." : null
    body = <>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>{dlg.a === "submit" ? "Submitting locks the lines until it is approved, sent back or reopened." : dlg.a === "approve" ? `Approves a planned total of ${rand(status?.plannedTotal)}. Quotes can then be approved against it. The person who submitted cannot approve.` : dlg.a === "reject" ? "Sends the budget back to draft so it can be changed." : "Unlocks the lines and clears the approval. Quotes already approved stay approved."}</p>
      {needsReason && <><L id="bw-why">Reason *</L><input id="bw-why" style={field} maxLength={500} value={f.reason ?? ""} onChange={e => set("reason", e.target.value)} /></>}
    </>
  } else if (dlg?.k === "commitment") {
    title = "Record supplier quote"; label = "Record quote"; width = 560
    problem = commitmentProblem({ budgetLineId: f.budgetLineId ?? "", description: f.description ?? "", amount: f.amount ?? "", depositAmount: f.depositAmount ?? "", depositDueDate: f.depositDueDate ?? "" })
    const T = (id: string, lab: string, k: string, extra: React.InputHTMLAttributes<HTMLInputElement> = {}) => <div><L id={id}>{lab}</L><input id={id} style={field} value={f[k] ?? ""} onChange={e => set(k, e.target.value)} {...extra} /></div>
    body = <>
      <L id="cm-line">Budget line *</L><select id="cm-line" style={field} value={f.budgetLineId ?? ""} onChange={e => set("budgetLineId", e.target.value)}><option value="">Choose…</option>{lines.map(l => <option key={l.id} value={l.id}>{categoryText(l.category)} · {l.description}</option>)}</select>
      <L id="cm-vendor">Supplier</L><select id="cm-vendor" style={field} value={f.vendorId ?? ""} onChange={e => set("vendorId", e.target.value)}><option value="">No supplier chosen</option>{vendors.map((v: any) => <option key={v.id} value={v.id}>{v.companyName}</option>)}</select>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div style={{ gridColumn: "1/-1" }}>{T("cm-desc", "What they will provide *", "description", { maxLength: 300 })}</div>
        {T("cm-amt", "Quoted amount (R) *", "amount", { inputMode: "decimal" })}{T("cm-due", "Due / delivery date", "dueDate", { type: "date" })}
        {T("cm-dep", "Deposit (R)", "depositAmount", { inputMode: "decimal" })}{T("cm-depdue", "Deposit due", "depositDueDate", { type: "date" })}
        {T("cm-quote", "Quote reference", "quoteReference", { maxLength: 100 })}{T("cm-po", "PO reference", "poReference", { maxLength: 100 })}
        {T("cm-contract", "Contract reference", "contractReference", { maxLength: 100 })}{T("cm-deliv", "Deliverable", "deliverable", { maxLength: 500 })}
      </div>
    </>
  } else if (dlg?.k === "approve") {
    const over = approvalOverrun(lineById.get(dlg.c.budgetLineId), dlg.c.amount)
    title = "Approve quote"; label = "Approve commitment"; problem = over > 0 && (f.reason ?? "").trim().length < 3 ? "This takes the line over its plan; give a reason to approve anyway." : null
    body = <>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>{dlg.c.description} · {rand(dlg.c.amount)}{dlg.c.vendorName ? ` · ${dlg.c.vendorName}` : ""}. The budget must be approved first.</p>
      {over > 0 && <p role="note" style={{ fontSize: 12.5, color: "var(--hf-warning-text)", margin: "10px 0 0" }}>Approving takes this line {rand(over)} over its planned amount.</p>}
      <L id="ca-why">{over > 0 ? "Reason for going over *" : "Note (only needed if over the plan)"}</L><input id="ca-why" style={field} maxLength={500} value={f.reason ?? ""} onChange={e => set("reason", e.target.value)} />
    </>
  } else if (dlg?.k === "complete") {
    title = "Mark delivered"; label = "Mark completed"; body = <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>{dlg.c.description}: the forecast then uses what was invoiced ({rand(dlg.c.invoiced)}) instead of the quote.</p>
  } else if (dlg?.k === "cancel") {
    title = "Cancel quote"; label = "Cancel quote"; problem = (f.reason ?? "").trim().length < 3 ? "Give a reason." : null
    body = <><p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>{dlg.c.description} · {rand(dlg.c.amount)}</p><L id="cc-why">Reason *</L><input id="cc-why" style={field} maxLength={500} value={f.reason ?? ""} onChange={e => set("reason", e.target.value)} /></>
  } else if (dlg?.k === "cost") {
    title = "Record invoice or payment"; label = "Record"; problem = costEntryProblem({ target: f.target ?? "", kind: f.kind ?? "", amount: f.amount ?? "", entryDate: f.entryDate ?? "" })
    body = <>
      <L id="ce-target">Against *</L>
      <select id="ce-target" style={field} value={f.target ?? ""} onChange={e => set("target", e.target.value)}>
        <option value="">Choose…</option>
        <optgroup label="Commitments">{commitments.filter(c => canRecordAgainst(c.status)).map(c => <option key={c.id} value={`c:${c.id}`}>{c.description} ({c.vendorName ?? "no supplier"})</option>)}</optgroup>
        <optgroup label="Budget lines (no supplier)">{lines.map(l => <option key={l.id} value={`l:${l.id}`}>{categoryText(l.category)} · {l.description}</option>)}</optgroup>
      </select>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div><L id="ce-kind">Kind *</L><select id="ce-kind" style={field} value={f.kind ?? ""} onChange={e => set("kind", e.target.value)}><option value="">Choose…</option><option value="INVOICE">Invoice received</option><option value="PAYMENT">Payment made</option></select></div>
        <div><L id="ce-amt">Amount (R) *</L><input id="ce-amt" style={field} inputMode="decimal" value={f.amount ?? ""} onChange={e => set("amount", e.target.value)} /></div>
        <div><L id="ce-date">Date *</L><input id="ce-date" type="date" style={field} value={f.entryDate ?? ""} onChange={e => set("entryDate", e.target.value)} /></div>
        <div><L id="ce-ref">Reference</L><input id="ce-ref" style={field} maxLength={100} value={f.reference ?? ""} onChange={e => set("reference", e.target.value)} /></div>
      </div>
      {f.kind === "PAYMENT" && <label style={{ display: "block", fontSize: 13, marginTop: 10 }}><input type="checkbox" checked={!!f.deposit} onChange={e => set("deposit", e.target.checked)} /> This is a deposit</label>}
      <L id="ce-desc">Description</L><input id="ce-desc" style={field} maxLength={300} value={f.description ?? ""} onChange={e => set("description", e.target.value)} />
    </>
  } else if (dlg?.k === "void") {
    title = "Void entry"; label = "Void entry"; problem = (f.reason ?? "").trim().length < 3 ? "Give a reason; the entry stays in the history." : null
    body = <><p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>{dlg.e.kind === "INVOICE" ? "Invoice" : "Payment"} of {rand(dlg.e.amount)} dated {dlg.e.entryDate}.</p><L id="cv-why">Reason *</L><input id="cv-why" style={field} maxLength={500} value={f.reason ?? ""} onChange={e => set("reason", e.target.value)} /></>
  }

  const tab = (v: View, t: string) => <button type="button" onClick={() => setView(v)} aria-pressed={view === v} style={{ ...btnGhost, background: view === v ? "var(--hf-surface-sunken)" : "none", color: view === v ? "var(--hf-text)" : "var(--hf-text-muted)" }}>{t}</button>
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Budget for {eventTitle} · <strong>{budgetStatusLabel(st)}</strong>{status?.approvedByName ? ` by ${status.approvedByName}` : status?.submittedByName && st === "SUBMITTED" ? ` (submitted by ${status.submittedByName})` : ""}</p>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {actions.filter(a => (a === "submit" ? canManage : canApprove)).map(a => <button key={a} type="button" style={a === "approve" || a === "submit" ? btnPrimary : btnGhost} onClick={() => open({ k: "workflow", a })}>{ACTION_LABEL[a]}</button>)}
        </div>
      </div>
      {status?.rejectionReason && st === "DRAFT" && <p role="note" style={{ fontSize: 12.5, color: "var(--hf-warning-text)" }}>Sent back: {status.rejectionReason}</p>}
      <ErrorLine error={error ?? deleteLine.error} fallback="The budget could not be loaded" />
      <div style={{ display: "flex", gap: 6, marginBottom: 12 }}>{tab("summary", "Budget")}{tab("commitments", "Supplier quotes")}{tab("costs", "Invoices and payments")}</div>

      {view === "summary" && (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {summary && <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            {[["Planned", summary.planned], ["Committed", summary.committed], ["Invoiced", summary.invoiced], ["Paid", summary.paid], ["Forecast", summary.forecast], ["Still to pay", summary.outstanding]].map(([k, v]) => <div key={k as string} style={{ ...card, padding: "8px 14px", minWidth: 110 }}><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{k}</div><div style={{ fontWeight: 800 }}>{rand(v as number)}</div></div>)}
            <div style={{ ...card, padding: "8px 14px", minWidth: 110 }}><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{summary.variance < 0 ? "Over budget by" : "Under budget by"}</div><div style={{ fontWeight: 800, color: TONE[varianceTone(summary.variance)] }}>{rand(Math.abs(summary.variance))}</div></div>
          </div>}
          {(summary?.alerts ?? []).map((a, i) => <div key={i} style={{ ...card, padding: "8px 14px", borderLeft: `3px solid ${a.severity === "WARNING" ? "var(--hf-warning)" : "var(--hf-sky)"}`, fontSize: 13 }}><strong>{alertTitle(a.type)}</strong> · {a.message}</div>)}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={{ fontSize: 14, margin: 0 }}>Budget lines</h3>
            {canManage && editable && <button type="button" style={btnPrimary} onClick={() => open({ k: "line" }, { category: "", description: "", plannedAmount: "", notes: "" })}>Add line</button>}
          </div>
          {lines.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No budget lines yet.</p> : (
            <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)", fontSize: 11.5 }}>{["Line", "Planned", "Committed", "Invoiced", "Paid", "Forecast", "Variance", ""].map(h => <th key={h} style={{ padding: "6px 8px" }}>{h}</th>)}</tr></thead>
              <tbody>{lines.map(l => { const s = lineById.get(l.id); return (
                <tr key={l.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={{ padding: "8px" }}><strong>{categoryText(l.category)}</strong><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{l.description}</div></td>
                  <td style={{ padding: "8px" }}>{rand(l.plannedAmount)}</td><td style={{ padding: "8px" }}>{rand(s?.committed ?? 0)}</td><td style={{ padding: "8px" }}>{rand(s?.invoiced ?? 0)}</td><td style={{ padding: "8px" }}>{rand(s?.paid ?? 0)}</td><td style={{ padding: "8px" }}>{rand(s?.forecast ?? 0)}</td>
                  <td style={{ padding: "8px", color: TONE[varianceTone(s?.variance ?? 0)], fontWeight: 700 }}>{rand(s?.variance ?? l.plannedAmount)}</td>
                  <td style={{ padding: "8px", whiteSpace: "nowrap" }}>{canManage && editable && <>
                    <button type="button" style={btnGhost} onClick={() => open({ k: "line", line: l }, { category: l.category, description: l.description, plannedAmount: String(l.plannedAmount), notes: l.notes ?? "" })}>Edit</button>{" "}
                    <button type="button" style={btnGhost} disabled={deleteLine.isPending} onClick={() => deleteLine.mutate(l.id)}>Delete</button></>}</td>
                </tr>) })}</tbody></table></div>
          )}
        </div>
      )}

      {view === "commitments" && (
        <div>
          <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>{canSupplier && <button type="button" style={btnPrimary} disabled={lines.length === 0} onClick={() => open({ k: "commitment" }, {})}>Record supplier quote</button>}</div>
          {commitments.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No supplier quotes yet.{lines.length === 0 ? " Add budget lines first." : ""}</p> : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{commitments.map(c => (
              <div key={c.id} style={{ ...card, padding: "10px 14px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 13 }}>{c.description} · {rand(c.amount)} <span style={{ fontWeight: 400, fontSize: 11.5, color: "var(--hf-text-muted)" }}>{commitmentStatusLabel(c.status)}</span></div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{c.vendorName ?? "No supplier"} · {categoryText(c.category)} · invoiced {rand(c.invoiced)} · paid {rand(c.paid)}{c.dueDate ? ` · due ${c.dueDate}` : ""}</div>
                    {c.overBudgetReason && <div style={{ fontSize: 12, color: "var(--hf-warning-text)" }}>Over plan: {c.overBudgetReason}</div>}
                    {c.cancelledReason && <div style={{ fontSize: 12 }}>Cancelled: {c.cancelledReason}</div>}
                  </div>
                  <div style={{ display: "flex", gap: 6 }}>
                    {canApprove && canApproveCommitment(c.status) && <button type="button" style={btnPrimary} onClick={() => open({ k: "approve", c }, { reason: "" })}>Approve</button>}
                    {canSupplier && canCompleteCommitment(c.status) && <button type="button" style={btnGhost} onClick={() => open({ k: "complete", c })}>Mark delivered</button>}
                    {canSupplier && canCancelCommitment(c.status) && <button type="button" style={btnGhost} onClick={() => open({ k: "cancel", c }, { reason: "" })}>Cancel</button>}
                  </div>
                </div>
              </div>))}</div>
          )}
        </div>
      )}

      {view === "costs" && (
        <div>
          <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>{canManage && <button type="button" style={btnPrimary} onClick={() => open({ k: "cost" }, { kind: "", deposit: false, entryDate: new Date().toLocaleDateString("en-CA") })}>Record invoice or payment</button>}</div>
          {(entries?.content ?? []).length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Nothing recorded yet.</p> : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>{(entries?.content ?? []).map(e => (
              <div key={e.id} style={{ ...card, padding: "8px 14px", opacity: e.status === "VOIDED" ? 0.6 : 1, display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", fontSize: 13 }}>
                <span>{e.kind === "INVOICE" ? "Invoice" : e.deposit ? "Deposit paid" : "Payment"} {rand(e.amount)} · {e.entryDate}{e.reference ? ` · ${e.reference}` : ""}{e.description ? ` · ${e.description}` : ""} · {e.reconciled ? "reconciled" : "not reconciled"}{e.status === "VOIDED" ? ` · voided (${e.voidReason})` : ""}</span>
                {canManage && e.status === "ACTIVE" && <button type="button" style={btnGhost} onClick={() => open({ k: "void", e }, { reason: "" })}>Void</button>}
              </div>))}</div>
          )}
        </div>
      )}
      {dlg && <Shell title={title} problem={problem} pending={run.isPending} error={run.error} label={label} width={width} onClose={() => setDlg(null)} onSubmit={() => run.mutate()}>{body}</Shell>}
    </div>
  )
}
