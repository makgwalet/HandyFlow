// src/pages/supply-chain/RequisitionsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, ClipboardList } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Badge, inp, TH, TD, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, Banner, filterPill, fmtR, fmtDate } from "./scm.shared"
import {
  asList, asOne, errorText, REQ_TYPES, emptyReqLine, requisitionProblem, requisitionBody, requisitionTotal, requisitionActions, REASON_REQUIRED,
  type ReqForm, type ReqAction,
} from "./scmOps"

interface Requisition { id: string; number: string; type: string; status: string; fulfilment: string | null; title: string; department: string | null; costCentre: string | null; projectRef: string | null; requiredByDate: string | null; estimatedTotal: number; requestedByMe?: boolean; mine?: boolean; advisories?: string[]; lines?: { itemName: string; quantity: number; estimatedUnitCost: number }[] }
interface BudgetCheck { state?: string; message?: string; available?: number }

const BASE = "/api/v1/supply-chain/requisitions"
const emptyForm = (): ReqForm => ({ type: "OPERATIONAL", title: "", businessReason: "", department: "", costCentre: "", projectRef: "", requiredByDate: "", lines: [emptyReqLine()] })
const LABEL: Record<ReqAction, string> = { submit: "Submit", approve: "Approve", reject: "Reject", withdraw: "Withdraw", reopen: "Reopen", cancel: "Cancel", convert: "Convert to PO" }

export function RequisitionsTab() {
  const qc = useQueryClient()
  const canCreate = usePermissions(["SCM_REQUISITION_CREATE", "SCM_ADMIN"])
  const canApprove = usePermissions(["SCM_REQUISITION_APPROVE", "SCM_ADMIN"])
  const [status, setStatus] = useState("")
  const [mine, setMine] = useState(false)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState<ReqForm>(emptyForm())
  const [formErr, setFormErr] = useState("")
  const [acting, setActing] = useState<{ r: Requisition; action: ReqAction } | null>(null)
  const [note, setNote] = useState("")
  const [actErr, setActErr] = useState("")

  const list = useQuery({
    queryKey: ["scm-requisitions", status, mine],
    queryFn: () => apiClient.get(BASE, { params: { status: status || undefined, mine: mine || undefined, size: 100 } }).then(asList<Requisition>),
  })

  const total = requisitionTotal(form.lines)
  const scope = { department: form.department.trim(), costCentre: form.costCentre.trim(), projectRef: form.projectRef.trim() }
  const hasScope = !!(scope.department || scope.costCentre || scope.projectRef)
  const check = useQuery({
    queryKey: ["scm-budget-check", scope, total],
    enabled: creating && hasScope && total > 0,
    queryFn: () => apiClient.get("/api/v1/supply-chain/budgets/check", { params: { ...scope, amount: total } }).then(r => asOne<BudgetCheck>(r)),
  })

  const done = () => { qc.invalidateQueries({ queryKey: ["scm-requisitions"] }); setActing(null); setNote(""); setActErr("") }

  const create = useMutation({
    mutationFn: () => apiClient.post(BASE, requisitionBody(form)),
    onSuccess: () => { setCreating(false); setForm(emptyForm()); setFormErr(""); qc.invalidateQueries({ queryKey: ["scm-requisitions"] }) },
    onError: (e) => setFormErr(errorText(e)),
  })

  const act = useMutation({
    mutationFn: ({ r, action, note }: { r: Requisition; action: ReqAction; note: string }) => {
      const path = action === "convert" ? "convert-to-po" : action
      return apiClient.post(`${BASE}/${r.id}/${path}`, REASON_REQUIRED.includes(action) || action === "withdraw" ? { note } : {})
    },
    onSuccess: done,
    onError: (e) => setActErr(errorText(e)),
  })

  const submitCreate = () => {
    const p = requisitionProblem(form)
    if (p) { setFormErr(p); return }
    setFormErr(""); create.mutate()
  }

  const setLine = (i: number, k: string, v: string) => setForm(f => ({ ...f, lines: f.lines.map((l, j) => (j === i ? { ...l, [k]: v } : l)) }))
  const rows = list.data ?? []

  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 14 }}>
        {["", "DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED"].map(s => (
          <button key={s || "all"} style={filterPill(status === s)} onClick={() => setStatus(s)}>{s ? s.replace(/_/g, " ") : "All"}</button>
        ))}
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
          <input type="checkbox" checked={mine} onChange={e => setMine(e.target.checked)} /> Mine only
        </label>
        <div style={{ flex: 1 }} />
        {canCreate && <button onClick={() => setCreating(true)} style={{ ...filterPill(true), display: "flex", gap: 6, alignItems: "center" }}><Plus size={14} /> New requisition</button>}
      </div>

      {list.isLoading ? <Spinner /> : list.isError ? <ErrBox msg={errorText(list.error, "Could not load requisitions")} /> : rows.length === 0 ? (
        <EmptyState icon={ClipboardList} title="No requisitions" sub="Requests to buy something appear here once raised." />
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{["Number", "Title", "For", "Required by", "Estimate", "Status", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
          <tbody>
            {rows.map(r => {
              const actions = requisitionActions(r.status, r.fulfilment, canApprove, canCreate)
              return (
                <tr key={r.id}>
                  <td style={TD}>{r.number}</td>
                  <td style={TD}>{r.title}</td>
                  <td style={TD}>{r.department || r.costCentre || r.projectRef || "—"}</td>
                  <td style={TD}>{fmtDate(r.requiredByDate)}</td>
                  <td style={TD}>{fmtR(r.estimatedTotal)}</td>
                  <td style={TD}><Badge status={r.status} /></td>
                  <td style={TD}>
                    {actions.map(a => (
                      <button key={a} style={{ ...filterPill(false), marginRight: 6 }} onClick={() => { setActing({ r, action: a }); setNote(""); setActErr("") }}>{LABEL[a]}</button>
                    ))}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {creating && (
        <Modal title="New requisition" wide onClose={() => setCreating(false)}>
          {formErr && <ErrBox msg={formErr} />}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Type">
              <select style={inp} value={form.type} onChange={e => setForm({ ...form, type: e.target.value })}>{REQ_TYPES.map(t => <option key={t}>{t}</option>)}</select>
            </Field>
            <Field label="Title"><input style={inp} aria-label="Title" value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></Field>
            <Field label="Department"><input style={inp} aria-label="Department" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} /></Field>
            <Field label="Cost centre"><input style={inp} aria-label="Cost centre" value={form.costCentre} onChange={e => setForm({ ...form, costCentre: e.target.value })} /></Field>
            <Field label="Project"><input style={inp} aria-label="Project" value={form.projectRef} onChange={e => setForm({ ...form, projectRef: e.target.value })} /></Field>
            <Field label="Required by"><input style={inp} type="date" value={form.requiredByDate} onChange={e => setForm({ ...form, requiredByDate: e.target.value })} /></Field>
            <Field label="Business reason" span={2}><textarea style={inp} value={form.businessReason} onChange={e => setForm({ ...form, businessReason: e.target.value })} /></Field>
          </div>
          <h4 style={{ margin: "14px 0 6px" }}>Lines</h4>
          {form.lines.map((l, i) => (
            <div key={i} style={{ display: "grid", gridTemplateColumns: "3fr 1fr 1fr 1fr auto", gap: 8, marginBottom: 6 }}>
              <input style={inp} placeholder="Item" aria-label={`Item ${i + 1}`} value={l.itemName} onChange={e => setLine(i, "itemName", e.target.value)} />
              <input style={inp} placeholder="Qty" aria-label={`Quantity ${i + 1}`} value={l.quantity} onChange={e => setLine(i, "quantity", e.target.value)} />
              <input style={inp} placeholder="Unit cost" aria-label={`Unit cost ${i + 1}`} value={l.estimatedUnitCost} onChange={e => setLine(i, "estimatedUnitCost", e.target.value)} />
              <input style={inp} placeholder="VAT %" value={l.vatRate} onChange={e => setLine(i, "vatRate", e.target.value)} />
              <button type="button" disabled={form.lines.length === 1} onClick={() => setForm(f => ({ ...f, lines: f.lines.filter((_, j) => j !== i) }))}>✕</button>
            </div>
          ))}
          <button type="button" style={filterPill(false)} onClick={() => setForm(f => ({ ...f, lines: [...f.lines, emptyReqLine()] }))}>Add line</button>
          <p style={{ fontWeight: 700 }}>Estimated total: {fmtR(total)}</p>
          {check.data?.message && <Banner variant={check.data.state === "EXCEEDED" ? "error" : check.data.state === "NEAR_LIMIT" ? "warning" : "info"}>{check.data.message}</Banner>}
          <ModalFooter onCancel={() => setCreating(false)} onConfirm={submitCreate} label="Save draft" loading={create.isPending} />
        </Modal>
      )}

      {acting && (
        <Modal title={`${LABEL[acting.action]} — ${acting.r.number}`} onClose={() => setActing(null)}>
          {actErr && <ErrBox msg={actErr} />}
          {(REASON_REQUIRED.includes(acting.action) || acting.action === "withdraw") && (
            <Field label={REASON_REQUIRED.includes(acting.action) ? "Reason (required)" : "Note"}>
              <textarea style={inp} aria-label="Reason" value={note} onChange={e => setNote(e.target.value)} />
            </Field>
          )}
          <ModalFooter onCancel={() => setActing(null)} label={LABEL[acting.action]} loading={act.isPending}
            onConfirm={() => {
              if (REASON_REQUIRED.includes(acting.action) && !note.trim()) { setActErr("A reason is required"); return }
              act.mutate({ r: acting.r, action: acting.action, note: note.trim() })
            }} />
        </Modal>
      )}
    </div>
  )
}
