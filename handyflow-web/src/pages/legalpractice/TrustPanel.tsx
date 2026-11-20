// A client's trust account: the running balance, every movement, and the four ways money moves. Money in is recorded straight
// away; money out is only REQUESTED here and moves once a second person approves it (Approvals section). Nothing can take more
// than the client has in trust; the server checks again, both when asked and when approved.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Input, Pager, Pick, Pill, card, day, errorText, money, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import { TRUST_KINDS, TRUST_TYPE, emptyTrust, nextAttempt, payableByTrust, trustKindsFor, trustProblem, trustRequest, type Attempt, type TrustForm, type TrustKind } from "./legal.logic"

interface Tx { id: string; matterId: string | null; transactionType: string; amount: number; transactionDate: string; invoiceId: string | null; payee: string | null; reference: string | null; capturedByName: string | null; approvedByName: string | null; notes: string | null; clientTrustBalanceAfter: number }
interface Inv { id: string; invoiceNumber: string; outstandingBalance: number; status: string }
interface Mat { id: string; matterNumber: string; matterName: string }

export default function TrustPanel({ clientId, balance, canReceive, canRequest }: { clientId: string; balance: number; canReceive: boolean; canRequest: boolean }) {
  const [page, setPage] = useState(0), [kind, setKind] = useState<TrustKind | null>(null)
  const kinds = trustKindsFor(canReceive, canRequest)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-trust", clientId, page], queryFn: async () => pageOf<Tx>(await apiClient.get(`${BASE}/clients/${clientId}/trust-transactions`, { params: { page, size: 15 } })) })
  const rows = data?.content ?? []
  return (
    <div style={{ marginTop: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", margin: "0 0 6px" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Trust account · <span style={{ fontWeight: 600 }}>{money(balance)} held</span></div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>{kinds.map(k => <Btn key={k} small kind={k === "receipt" ? "primary" : "secondary"} onClick={() => setKind(k)}>{TRUST_KINDS[k].label}</Btn>)}</div>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <div style={{ ...muted, padding: 14 }}>Could not load the trust account.</div> : isLoading ? <div style={{ ...muted, padding: 14 }}>Loading…</div> : rows.length === 0 ? <div style={{ ...muted, padding: 14 }}>Nothing has gone through trust yet.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>What</th><th style={{ ...th, textAlign: "right" }}>Amount</th><th style={{ ...th, textAlign: "right" }}>Balance after</th></tr></thead>
            <tbody>{rows.map(x => {
              const ty = TRUST_TYPE[x.transactionType] ?? { label: x.transactionType, tone: "neutral" as const }
              return (
                <tr key={x.id}>
                  <td style={td}>{day(x.transactionDate)}</td>
                  <td style={td}><Pill tone={ty.tone}>{ty.label}</Pill><div style={muted}>{[x.payee, x.reference, x.notes].filter(Boolean).join(" · ")}{x.capturedByName ? `${x.payee || x.reference || x.notes ? " · " : ""}by ${x.capturedByName}` : ""}{x.approvedByName ? ` · approved by ${x.approvedByName}` : ""}</div></td>
                  <td style={{ ...td, textAlign: "right" }}>{x.transactionType === "RECEIPT" ? "+" : "−"}{money(x.amount)}</td>
                  <td style={{ ...td, textAlign: "right" }}>{money(x.clientTrustBalanceAfter)}</td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {kind && <MoveDialog kind={kind} clientId={clientId} balance={balance} onClose={() => setKind(null)} />}
    </div>
  )
}

const newKey = () => globalThis.crypto?.randomUUID?.() ?? `k-${Date.now()}-${Math.random().toString(36).slice(2)}`

function MoveDialog({ kind, clientId, balance, onClose }: { kind: TrustKind; clientId: string; balance: number; onClose: () => void }) {
  const qc = useQueryClient()
  const def = TRUST_KINDS[kind]
  const [f, setF] = useState<TrustForm>(emptyTrust()), [err, setErr] = useState("")
  const attempt = useRef<Attempt | null>(null)
  const set = (k: keyof TrustForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const invoices = useQuery({ enabled: kind === "transfer", queryKey: ["lp-client-invoices", clientId], queryFn: async () => pageOf<Inv>(await apiClient.get(`${BASE}/invoices`, { params: { clientId, size: 100 } })) })
  const matters = useQuery({ queryKey: ["lp-client-matters", clientId], queryFn: async () => pageOf<Mat>(await apiClient.get(`${BASE}/matters`, { params: { clientId, size: 100 } })) })
  const payable = (invoices.data?.content ?? []).filter(i => payableByTrust(i.status, i.outstandingBalance))
  const chosen = payable.find(i => i.id === f.invoiceId)
  const problem = trustProblem(kind, f, balance, chosen?.outstandingBalance)
  const save = useMutation({
    mutationFn: () => {
      attempt.current = nextAttempt(attempt.current, kind, f, newKey)
      return apiClient.post(`${BASE}/clients/${clientId}/${def.path}`, trustRequest(kind, f, attempt.current.key))
    },
    onSuccess: () => {
      toast.success(def.takesOut ? "Requested. It will be paid once a second person approves it" : "Trust account updated")
      for (const k of ["lp-trust", "lp-client", "lp-clients", "lp-clients-all", "lp-client-invoices", "lp-invoices", "lp-trust-requests"]) qc.invalidateQueries({ queryKey: [k] })
      onClose()
    },
    onError: async e => setErr(await errorText(e, "Could not record that")),
  })
  return (
    <Dialog title={def.label} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? (def.takesOut ? "Requesting…" : "Recording…") : def.takesOut ? "Send for approval" : "Record"}</Btn></>}>
      {def.takesOut && <div style={{ ...muted, marginBottom: 10 }}>The client has {money(balance)} in trust. Nothing is paid until a second person approves this request.</div>}
      {kind === "transfer" && (
        <Pick label="Invoice being paid" value={f.invoiceId} onChange={set("invoiceId")} blank={invoices.isLoading ? "Loading…" : payable.length ? "Choose an invoice" : "No invoices owing"}
          options={payable.map(i => [i.id, `${i.invoiceNumber} — ${money(i.outstandingBalance)} owing`] as const)} />)}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Amount (R)" type="number" min="0" step="0.01" value={f.amount} onChange={set("amount")} autoFocus />
        <Input label="Date" type="date" value={f.transactionDate} onChange={set("transactionDate")} hint="Leave empty for today" />
      </div>
      {(kind === "disbursement" || kind === "refund") && <Input label={kind === "refund" ? "Paid to" : "Payee"} value={f.payee} onChange={set("payee")} maxLength={255} />}
      <Pick label="Matter (optional)" value={f.matterId} onChange={set("matterId")} blank="Not for one matter" options={(matters.data?.content ?? []).map(m => [m.id, `${m.matterNumber} · ${m.matterName}`] as const)} />
      <Input label="Reference" value={f.reference} onChange={set("reference")} maxLength={255} />
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.amount.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
