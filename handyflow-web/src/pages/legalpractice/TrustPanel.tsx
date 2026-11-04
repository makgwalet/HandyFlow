// A client's trust account: the running balance, every movement, and the four ways money moves. Withdrawals are admin only
// and can never take more than the client has in trust; the server checks again.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Input, Pager, Pick, Pill, card, day, errorText, money, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import { TRUST_KINDS, TRUST_TYPE, emptyTrust, payableByTrust, trustKindsFor, trustProblem, trustRequest, type TrustForm, type TrustKind } from "./legal.logic"

interface Tx { id: string; matterId: string | null; transactionType: string; amount: number; transactionDate: string; invoiceId: string | null; payee: string | null; reference: string | null; capturedByName: string | null; notes: string | null; clientTrustBalanceAfter: number }
interface Inv { id: string; invoiceNumber: string; outstandingBalance: number; status: string }
interface Mat { id: string; matterNumber: string; matterName: string }

export default function TrustPanel({ clientId, balance, isAdmin, canWrite }: { clientId: string; balance: number; isAdmin: boolean; canWrite: boolean }) {
  const [page, setPage] = useState(0), [kind, setKind] = useState<TrustKind | null>(null)
  const kinds = trustKindsFor(isAdmin, canWrite)
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
                  <td style={td}><Pill tone={ty.tone}>{ty.label}</Pill><div style={muted}>{[x.payee, x.reference, x.notes].filter(Boolean).join(" · ")}{x.capturedByName ? `${x.payee || x.reference || x.notes ? " · " : ""}by ${x.capturedByName}` : ""}</div></td>
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

function MoveDialog({ kind, clientId, balance, onClose }: { kind: TrustKind; clientId: string; balance: number; onClose: () => void }) {
  const qc = useQueryClient()
  const def = TRUST_KINDS[kind]
  const [f, setF] = useState<TrustForm>(emptyTrust()), [err, setErr] = useState("")
  const set = (k: keyof TrustForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const invoices = useQuery({ enabled: kind === "transfer", queryKey: ["lp-client-invoices", clientId], queryFn: async () => pageOf<Inv>(await apiClient.get(`${BASE}/invoices`, { params: { clientId, size: 100 } })) })
  const matters = useQuery({ queryKey: ["lp-client-matters", clientId], queryFn: async () => pageOf<Mat>(await apiClient.get(`${BASE}/matters`, { params: { clientId, size: 100 } })) })
  const payable = (invoices.data?.content ?? []).filter(i => payableByTrust(i.status, i.outstandingBalance))
  const chosen = payable.find(i => i.id === f.invoiceId)
  const problem = trustProblem(kind, f, balance, chosen?.outstandingBalance)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/clients/${clientId}/${def.path}`, trustRequest(kind, f)),
    onSuccess: () => {
      toast.success("Trust account updated")
      for (const k of ["lp-trust", "lp-client", "lp-clients", "lp-clients-all", "lp-client-invoices", "lp-invoices"]) qc.invalidateQueries({ queryKey: [k] })
      onClose()
    },
    onError: async e => setErr(await errorText(e, "Could not record that")),
  })
  return (
    <Dialog title={def.label} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Recording…" : "Record"}</Btn></>}>
      {def.takesOut && <div style={{ ...muted, marginBottom: 10 }}>The client has {money(balance)} in trust.</div>}
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
