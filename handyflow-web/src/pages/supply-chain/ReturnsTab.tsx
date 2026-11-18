// src/pages/supply-chain/ReturnsTab.tsx — supplier returns & claims with a Request → Authorised → Processing → Completed stepper.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Undo2 } from "lucide-react"
import { TH, TD, Modal, ModalFooter, ErrBox, Spinner, EmptyState, filterPill, fmtR, fmtDate } from "./scm.shared"
import { asList, errorText, returnStep, returnActions, creditProblem, RETURN_STEPS, type ReturnAction } from "./scmOps"
import { StatCard, Panel, Pill, Stepper, grid, type Tone } from "./scmUi"

export interface SupplierReturn { id: string; returnNumber: string; kind: string; reason: string; status: string; supplierName: string; expectedCredit: number | null; creditNoteRef: string | null; creditAmount: number | null; statusReason: string | null; createdAt: string; lines: { id: string; itemName: string; qty: number; lineTotal: number }[] }
const BASE = "/api/v1/supply-chain/returns"
const TONE: Record<string, Tone> = { DRAFT: "neutral", SUBMITTED: "info", APPROVED: "info", DISPATCHED: "warn", CREDITED: "ok", REJECTED: "bad", CANCELLED: "neutral" }
const LABEL: Record<ReturnAction, string> = { submit: "Submit", approve: "Authorise", reject: "Reject", cancel: "Cancel", dispatch: "Mark dispatched", credit: "Record credit" }
const NOTE_ACTIONS: ReturnAction[] = ["approve", "reject", "cancel"]
const FILTERS = ["ALL", "OPEN", "CREDITED", "CLOSED"] as const
const isOpen = (s: string) => ["DRAFT", "SUBMITTED", "APPROVED", "DISPATCHED"].includes(s)

export function ReturnsTab() {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_RETURN_MANAGE", "SCM_ADMIN"])
  const canApprove = usePermissions(["SCM_RETURN_APPROVE", "SCM_ADMIN"])
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("OPEN")
  const [openId, setOpenId] = useState<string | null>(null)
  const [pending, setPending] = useState<{ r: SupplierReturn; action: ReturnAction } | null>(null)
  const [note, setNote] = useState(""); const [ref, setRef] = useState(""); const [amount, setAmount] = useState("")
  const [err, setErr] = useState("")

  const q = useQuery({ queryKey: ["scm-returns"], queryFn: () => apiClient.get(BASE).then(asList<SupplierReturn>) })
  const run = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: ReturnAction; body?: unknown }) => apiClient.post(`${BASE}/${id}/${action}`, body),
    onSuccess: () => { setPending(null); setNote(""); setRef(""); setAmount(""); setErr(""); qc.invalidateQueries({ queryKey: ["scm-returns"] }) },
    onError: e => setErr(errorText(e)),
  })

  const go = (r: SupplierReturn, a: ReturnAction) => {
    if (a === "credit" || NOTE_ACTIONS.includes(a)) { setPending({ r, action: a }); setErr(""); return }
    run.mutate({ id: r.id, action: a })
  }
  const confirm = () => {
    if (!pending) return
    const { r, action } = pending
    if (action === "credit") {
      const p = creditProblem(ref, amount); if (p) { setErr(p); return }
      run.mutate({ id: r.id, action, body: { creditNoteRef: ref.trim(), creditAmount: Number(amount) } })
    } else {
      if (action !== "approve" && !note.trim()) { setErr("Give a reason."); return }
      run.mutate({ id: r.id, action, body: { note } })
    }
  }

  const list = q.data ?? []
  const open = list.filter(r => isOpen(r.status))
  const awaiting = list.filter(r => r.status === "SUBMITTED").length
  const expected = open.reduce((s, r) => s + (r.expectedCredit ?? 0), 0)
  const recovered = list.filter(r => r.status === "CREDITED").reduce((s, r) => s + (r.creditAmount ?? 0), 0)
  const rows = list.filter(r => filter === "ALL" || (filter === "OPEN" ? isOpen(r.status) : filter === "CREDITED" ? r.status === "CREDITED" : ["REJECTED", "CANCELLED"].includes(r.status)))

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={grid(180)}>
        <StatCard label="Open returns" value={open.length} icon={Undo2} />
        <StatCard label="Awaiting authorisation" value={awaiting} tone={awaiting ? "warn" : "neutral"} />
        <StatCard label="Credit expected" value={fmtR(expected)} />
        <StatCard label="Credit recovered" value={fmtR(recovered)} tone="ok" />
      </div>
      {err && !pending && <ErrBox msg={err} />}
      <div style={{ display: "flex", gap: 8 }}>{FILTERS.map(f => <button key={f} style={filterPill(filter === f)} onClick={() => setFilter(f)}>{f[0] + f.slice(1).toLowerCase()}</button>)}</div>

      {q.isLoading ? <Spinner /> : q.isError ? <ErrBox msg={errorText(q.error, "Could not load returns")} /> : rows.length === 0 ? (
        <EmptyState icon={Undo2} title="No returns" sub="Goods sent back to suppliers, and claims, appear here." />
      ) : (
        <div style={{ display: "grid", gap: 10 }}>
          {rows.map(r => {
            const actions = returnActions(r.status, canManage, canApprove)
            return (
              <Panel key={r.id} title={<span>{r.returnNumber} · {r.supplierName}</span>} right={<Pill tone={TONE[r.status] ?? "neutral"}>{r.status[0] + r.status.slice(1).toLowerCase()}</Pill>}>
                {returnStep(r.status) >= 0 ? <Stepper steps={RETURN_STEPS} current={returnStep(r.status)} /> : <div style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{r.status === "REJECTED" ? "Rejected" : "Cancelled"}{r.statusReason ? `: ${r.statusReason}` : ""}</div>}
                <div style={{ display: "flex", gap: 18, flexWrap: "wrap", fontSize: 13, color: "var(--hf-text-secondary)", margin: "12px 0" }}>
                  <span>{r.kind[0] + r.kind.slice(1).toLowerCase()} · {r.reason.replace(/_/g, " ").toLowerCase()}</span>
                  <span>Raised {fmtDate(r.createdAt)}</span>
                  <span>Expected credit {fmtR(r.expectedCredit)}</span>
                  {r.creditNoteRef && <span>Credit note {r.creditNoteRef} ({fmtR(r.creditAmount)})</span>}
                </div>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <button style={filterPill(false)} onClick={() => setOpenId(openId === r.id ? null : r.id)}>{openId === r.id ? "Hide" : "Show"} lines ({r.lines.length})</button>
                  {actions.map(a => <button key={a} style={filterPill(a === "approve" || a === "credit")} onClick={() => go(r, a)}>{LABEL[a]}</button>)}
                </div>
                {openId === r.id && (
                  <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 10 }}>
                    <thead><tr><th style={TH}>Item</th><th style={TH}>Qty</th><th style={TH}>Value</th></tr></thead>
                    <tbody>{r.lines.map(l => <tr key={l.id} style={{ borderTop: "1px solid var(--hf-border)" }}><td style={TD}>{l.itemName}</td><td style={TD}>{l.qty}</td><td style={TD}>{fmtR(l.lineTotal)}</td></tr>)}</tbody>
                  </table>
                )}
              </Panel>
            )
          })}
        </div>
      )}

      {pending && (
        <Modal title={`${LABEL[pending.action]} — ${pending.r.returnNumber}`} onClose={() => { setPending(null); setErr("") }}>
          {pending.action === "credit" ? (
            <div style={{ display: "grid", gap: 10 }}>
              <label style={{ fontSize: 12, fontWeight: 600 }}>Credit note reference<input aria-label="Credit note reference" value={ref} onChange={e => setRef(e.target.value)} style={{ width: "100%", padding: 9, marginTop: 5, border: "1.5px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }} /></label>
              <label style={{ fontSize: 12, fontWeight: 600 }}>Credit total incl. VAT<input aria-label="Credit total" inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} style={{ width: "100%", padding: 9, marginTop: 5, border: "1.5px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }} /></label>
            </div>
          ) : (
            <label style={{ fontSize: 12, fontWeight: 600 }}>{pending.action === "approve" ? "Note (optional)" : "Reason (required)"}
              <textarea aria-label="Note" rows={3} value={note} onChange={e => setNote(e.target.value)} style={{ width: "100%", marginTop: 5, padding: 9, border: "1.5px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }} /></label>
          )}
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={() => { setPending(null); setErr("") }} onConfirm={confirm} label={LABEL[pending.action]} loading={run.isPending} />
        </Modal>
      )}
    </div>
  )
}
