// Trust account reconciliation: the bank statement against the trust ledger at a date. One person prepares and submits; a different
// person approves, which locks the period so nothing dated inside it can be posted afterwards.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, fail, money, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import { unwrap } from "./deadlineApi"
import {
  RECON_KINDS, RECON_STATUS, approveProblem, canEditRecon, canSubmitRecon, emptyReconItem, labelOf, reconItemProblem, reconItemRequest, reconNextStep, startReconProblem,
  type ReconItemForm, type ReconSummary, type Reconciliation,
} from "./legal.logic"

const URL = `${BASE}/trust-reconciliations`
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }

export default function ReconciliationTab() {
  const canPrepare = usePermissions(["LEGALPRACTICE_TRUST_RECONCILE", "LEGALPRACTICE_ADMIN"])
  const canApprove = usePermissions(["LEGALPRACTICE_TRUST_RECONCILE_APPROVE", "LEGALPRACTICE_ADMIN"])
  const [openId, setOpenId] = useState<string | null>(null), [starting, setStarting] = useState(false), [page, setPage] = useState(0)
  const lock = useQuery({ queryKey: ["lp-trust-lock"], queryFn: async () => unwrap<{ lockedThrough: string | null }>(await apiClient.get(`${URL}/lock`))?.lockedThrough ?? null })
  const list = useQuery({ queryKey: ["lp-trust-recons", page], queryFn: async () => pageOf<ReconSummary>(await apiClient.get(URL, { params: { page, size: 15 } })) })
  if (openId) return <Detail id={openId} canPrepare={canPrepare} canApprove={canApprove} onBack={() => setOpenId(null)} />
  const rows = list.data?.content ?? []
  return (
    <div>
      <SectionTitle right={canPrepare && <Btn kind="primary" onClick={() => setStarting(true)}>Start a reconciliation</Btn>}>Trust reconciliation</SectionTitle>
      <div style={{ ...muted, marginBottom: 10 }}>Compare the trust bank statement with the trust ledger at a date, list what explains any difference, and have a second person approve it. Approving locks the period.</div>
      <div style={{ marginBottom: 10 }}>{lock.data
        ? <Pill tone="success">Trust ledger locked through {day(lock.data)}</Pill>
        : <Pill tone="warning">No period has been reconciled yet</Pill>}</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <Empty>Could not load the reconciliations.</Empty> : list.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No reconciliations yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Up to</th><th style={th}>Status</th><th style={{ ...th, textAlign: "right" }}>Statement balance</th><th style={th}>Prepared</th><th style={th}>Approved</th><th style={th} /></tr></thead>
            <tbody>{rows.map(r => {
              const st = RECON_STATUS[r.status] ?? { label: r.status, tone: "neutral" as const }
              return (
                <tr key={r.id}>
                  <td style={td}><b>{day(r.periodEnd)}</b></td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill></td>
                  <td style={{ ...td, textAlign: "right" }}>{money(r.statementBalance)}</td>
                  <td style={td}>{r.preparedByName ?? ""}<div style={muted}>{day(r.preparedAt)}</div></td>
                  <td style={td}>{r.approvedByName ?? "—"}{r.approvedAt && <div style={muted}>{day(r.approvedAt)}</div>}</td>
                  <td style={{ ...td, textAlign: "right" }}><Btn small onClick={() => setOpenId(r.id)}>Open</Btn></td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />
      {starting && <StartDialog lockedThrough={lock.data ?? null} onClose={() => setStarting(false)} onStarted={id => { setStarting(false); setOpenId(id) }} />}
    </div>
  )
}

function StartDialog({ lockedThrough, onClose, onStarted }: { lockedThrough: string | null; onClose: () => void; onStarted: (id: string) => void }) {
  const qc = useQueryClient()
  const [periodEnd, setPeriodEnd] = useState(""), [balance, setBalance] = useState(""), [note, setNote] = useState(""), [err, setErr] = useState("")
  const problem = startReconProblem(periodEnd, balance, today(), lockedThrough)
  const save = useMutation({
    mutationFn: async () => unwrap<Reconciliation>(await apiClient.post(URL, { periodEnd, statementBalance: Number(balance), note: note.trim() || null })),
    onSuccess: r => { toast.success("Reconciliation started"); qc.invalidateQueries({ queryKey: ["lp-trust-recons"] }); onStarted(r.id) },
    onError: async e => setErr(await errorText(e, "Could not start the reconciliation")),
  })
  return (
    <Dialog title="Start a reconciliation" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Starting…" : "Start"}</Btn></>}>
      {lockedThrough && <div style={{ ...muted, marginBottom: 8 }}>The trust ledger is locked through {day(lockedThrough)}. Reconcile a later date.</div>}
      <Input label="Statement date" type="date" value={periodEnd} onChange={setPeriodEnd} hint="Usually the last day of the month." max={today()} />
      <Input label="Closing balance on the bank statement" type="number" step="0.01" value={balance} onChange={setBalance} hint="As at that date, from the trust bank account statement." />
      <Input label="Note (optional)" value={note} onChange={setNote} />
      {problem && (periodEnd || balance) && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function Detail({ id, canPrepare, canApprove, onBack }: { id: string; canPrepare: boolean; canApprove: boolean; onBack: () => void }) {
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false), [statement, setStatement] = useState(false), [returning, setReturning] = useState(false), [approving, setApproving] = useState(false)
  const q = useQuery({ queryKey: ["lp-trust-recon", id], queryFn: async () => unwrap<Reconciliation>(await apiClient.get(`${URL}/${id}`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lp-trust-recon", id] }); qc.invalidateQueries({ queryKey: ["lp-trust-recons"] }); qc.invalidateQueries({ queryKey: ["lp-trust-lock"] }) }
  const remove = useMutation({ mutationFn: (itemId: string) => apiClient.delete(`${URL}/${id}/items/${itemId}`), onSuccess: refresh, onError: e => { void fail(e, "Could not remove the item") } })
  const submit = useMutation({ mutationFn: () => apiClient.post(`${URL}/${id}/submit`), onSuccess: () => { toast.success("Submitted for approval"); refresh() }, onError: e => { void fail(e, "Could not submit") } })
  const r = q.data
  if (q.isError) return <div><Btn onClick={onBack}>← Back</Btn><ErrorLine text="Could not load the reconciliation." /></div>
  if (!r) return <div style={muted}>Loading…</div>
  const st = RECON_STATUS[r.status] ?? { label: r.status, tone: "neutral" as const }, next = reconNextStep(r), editable = canEditRecon(r, canPrepare)
  const line = (label: string, value: number, strong = false) => <tr><td style={td}>{strong ? <b>{label}</b> : label}</td><td style={{ ...td, textAlign: "right" }}>{strong ? <b>{money(value)}</b> : money(value)}</td></tr>
  return (
    <div>
      <div style={{ marginBottom: 8 }}><Btn small onClick={onBack}>← All reconciliations</Btn></div>
      <SectionTitle right={<Pill tone={st.tone}>{st.label}</Pill>}>Trust account to {day(r.periodEnd)}</SectionTitle>
      <div style={{ ...card, background: "var(--hf-surface-alt, var(--hf-surface))", marginBottom: 12 }}><Pill tone={next.tone}>{next.text}</Pill></div>
      {r.returnReason && r.status === "DRAFT" && <div style={{ ...muted, marginBottom: 8 }}>Returned{r.returnedByName ? ` by ${r.returnedByName}` : ""}: {r.returnReason}</div>}

      <div style={{ ...card, padding: 0, overflowX: "auto", marginBottom: 12 }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>
          {line("Bank statement balance", r.statementBalance)}
          {line("Add: deposits the bank has not yet credited", r.outstandingDeposits)}
          {line("Less: payments the bank has not yet debited", r.outstandingPayments)}
          {line("Adjusted bank balance", r.adjustedBankBalance, true)}
          {line("Trust ledger (cash book) at this date", r.cashBookBalance, true)}
          {line("Client ledgers added together", r.clientLedgerTotal)}
          {line("Difference (ledger less adjusted bank)", r.difference, true)}
        </tbody></table>
      </div>
      {r.statementNote && <div style={{ ...muted, marginBottom: 8 }}>Statement note: {r.statementNote}</div>}
      {editable && <div style={{ marginBottom: 12 }}><Btn small onClick={() => setStatement(true)}>Change the statement balance</Btn></div>}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
        <div style={{ fontWeight: 700 }}>Items that explain the difference</div>
        {editable && <Btn small onClick={() => setAdding(true)}>Add an item</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto", marginBottom: 12 }}>
        {r.items.length === 0 ? <Empty>No items.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Item</th><th style={{ ...th, textAlign: "right" }}>Amount</th><th style={th} /></tr></thead>
            <tbody>{r.items.map(i => (
              <tr key={i.id}>
                <td style={td}>{day(i.itemDate)}</td>
                <td style={td}>{i.description}<div style={muted}>{labelOf(RECON_KINDS, i.kind)}{i.reference ? ` · ${i.reference}` : ""}</div></td>
                <td style={{ ...td, textAlign: "right" }}>{money(i.amount)}</td>
                <td style={{ ...td, textAlign: "right" }}>{editable && <Btn small disabled={remove.isPending} onClick={() => remove.mutate(i.id)}>Remove</Btn>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>

      {r.negativeClients.length > 0 && <div style={{ ...card, marginBottom: 12 }}>
        <div style={{ fontWeight: 700, marginBottom: 4 }}>Clients overdrawn at this date</div>
        {r.negativeClients.map(c => <div key={c.clientId} style={{ fontSize: 13 }}>{c.clientName}: {money(c.amount)}</div>)}
        <div style={muted}>Trust money may never be used for one client's benefit at another's expense. Have these looked at before approving.</div>
      </div>}
      {r.ledgerMismatchCount > 0 && <div style={{ ...card, marginBottom: 12 }}>
        <div style={{ fontWeight: 700, marginBottom: 4 }}>{r.ledgerMismatchCount} client {r.ledgerMismatchCount === 1 ? "balance disagrees" : "balances disagree"} with the ledger</div>
        {r.ledgerMismatches.map(m => <div key={m.clientId} style={{ fontSize: 13 }}>{m.clientName}: client record {money(m.clientRecord)}, ledger {money(m.ledger)}</div>)}
        <div style={muted}>Compared as of now, not as at the reconciliation date. The approver must say what was checked.</div>
      </div>}

      <div style={{ ...muted, marginBottom: 8 }}>Prepared by {r.preparedByName ?? "—"} on {day(r.preparedAt)}{r.submittedAt ? ` · submitted ${day(r.submittedAt)}` : ""}{r.approvedByName ? ` · approved by ${r.approvedByName} on ${r.approvedAt ? day(r.approvedAt) : ""}${r.selfApproved ? " (the person who submitted it, as the firm allows)" : ""}` : ""}{r.approvalNote ? ` · ${r.approvalNote}` : ""}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {canSubmitRecon(r, canPrepare) && <Btn kind="primary" disabled={submit.isPending} onClick={() => submit.mutate()}>{submit.isPending ? "Submitting…" : "Submit for approval"}</Btn>}
        {r.status === "SUBMITTED" && (canApprove || canPrepare) && <Btn onClick={() => setReturning(true)}>Return to draft</Btn>}
        {r.status === "SUBMITTED" && canApprove && !r.changedSinceSubmitted && <Btn kind="primary" onClick={() => setApproving(true)}>Approve and lock the period</Btn>}
      </div>
      {adding && <ItemDialog rec={r} onClose={() => setAdding(false)} onSaved={refresh} />}
      {statement && <StatementDialog rec={r} onClose={() => setStatement(false)} onSaved={refresh} />}
      {returning && <ReturnDialog id={id} onClose={() => setReturning(false)} onSaved={refresh} />}
      {approving && <ApproveDialog rec={r} onClose={() => setApproving(false)} onSaved={refresh} />}
    </div>
  )
}

function ItemDialog({ rec, onClose, onSaved }: { rec: Reconciliation; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<ReconItemForm>(emptyReconItem), [err, setErr] = useState("")
  const set = (k: keyof ReconItemForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = reconItemProblem(f, rec.periodEnd)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${rec.id}/items`, reconItemRequest(f)),
    onSuccess: () => { toast.success("Item added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not add the item")),
  })
  return (
    <Dialog title="Add a reconciling item" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Add"}</Btn></>}>
      <Pick label="What kind" value={f.kind} onChange={set("kind")} options={RECON_KINDS} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Amount" type="number" step="0.01" value={f.amount} onChange={set("amount")} />
        <Input label="Date of the item" type="date" value={f.itemDate} onChange={set("itemDate")} max={rec.periodEnd} />
      </div>
      <Input label="What it is" value={f.description} onChange={set("description")} hint="For example: Cheque 1042 to the Sheriff, not yet presented" />
      <Input label="Reference (optional)" value={f.reference} onChange={set("reference")} />
      {problem && (f.amount || f.description) && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function StatementDialog({ rec, onClose, onSaved }: { rec: Reconciliation; onClose: () => void; onSaved: () => void }) {
  const [balance, setBalance] = useState(String(rec.statementBalance)), [note, setNote] = useState(rec.statementNote ?? ""), [err, setErr] = useState("")
  const bad = !balance.trim() || !Number.isFinite(Number(balance))
  const save = useMutation({
    mutationFn: () => apiClient.put(`${URL}/${rec.id}/statement`, { statementBalance: Number(balance), note: note.trim() || null }),
    onSuccess: () => { toast.success("Statement balance saved"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save")),
  })
  return (
    <Dialog title="Statement balance" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={bad || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Closing balance on the bank statement" type="number" step="0.01" value={balance} onChange={setBalance} />
      <Input label="Note (optional)" value={note} onChange={setNote} />
      {bad && <div style={muted}>Enter the closing balance from the bank statement</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ReturnDialog({ id, onClose, onSaved }: { id: string; onClose: () => void; onSaved: () => void }) {
  const [reason, setReason] = useState(""), [err, setErr] = useState("")
  const bad = reason.trim().length < 10
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${id}/return`, { reason: reason.trim() }),
    onSuccess: () => { toast.success("Returned to draft"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not return it")),
  })
  return (
    <Dialog title="Return to draft" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={bad || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Return it"}</Btn></>}>
      <Input label="Why is it being returned" value={reason} onChange={setReason} hint="At least 10 characters. The preparer sees this." />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ApproveDialog({ rec, onClose, onSaved }: { rec: Reconciliation; onClose: () => void; onSaved: () => void }) {
  const [note, setNote] = useState(""), [err, setErr] = useState("")
  const problem = approveProblem(rec.ledgerMismatchCount, note)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${rec.id}/approve`, { note: note.trim() || null }),
    onSuccess: () => { toast.success("Reconciliation approved"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not approve")),
  })
  return (
    <Dialog title={`Approve the reconciliation to ${day(rec.periodEnd)}`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Approving…" : "Approve and lock"}</Btn></>}>
      <div style={muted}>Approving locks the trust ledger through {day(rec.periodEnd)}: no transaction dated on or before it can be posted afterwards. This cannot be undone. The server stops you approving one you submitted yourself unless the firm has allowed it.</div>
      <div style={{ marginTop: 10 }}><Input label={rec.ledgerMismatchCount > 0 ? "What you checked" : "Note (optional)"} value={note} onChange={setNote} /></div>
      {problem && note.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
