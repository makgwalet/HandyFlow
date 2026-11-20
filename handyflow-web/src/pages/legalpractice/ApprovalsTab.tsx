// Payments out of trust waiting for a second person. One person asks (on the client's trust account), a different person decides
// here. Approving posts the payment; rejecting or withdrawing moves no money. A firm with a single attorney can switch on an
// exception that lets that person approve their own request, with a written reason every time; every change to it is recorded.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, money, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import { REQUEST_STATUS, REQUEST_TYPE, approvalMode, canWithdraw, reasonProblem } from "./legal.logic"

interface Req {
  id: string; clientId: string; clientName: string | null; paymentType: string; amount: number; transactionDate: string | null; payee: string | null
  reference: string | null; notes: string | null; status: string; requestedBy: string; requestedByName: string | null; requestedAt: string
  decidedByName: string | null; decidedAt: string | null; decisionReason: string | null; selfApproved: boolean
}
interface Settings { selfApprovalAllowed: boolean; history: { selfApprovalAllowed: boolean; reason: string; changedByName: string | null; changedAt: string }[] }

const FILTERS: (readonly [string, string])[] = [["PENDING", "Waiting for approval"], ["APPROVED", "Approved"], ["REJECTED", "Rejected"], ["CANCELLED", "Withdrawn"], ["", "All"]]
const unwrap = <T,>(r: { data?: { data?: T } | T }): T => ((r.data as { data?: T } | undefined)?.data ?? r.data) as T

export default function ApprovalsTab() {
  const me = useAuthStore(s => s.user?.userId)
  const isAdmin = usePermission("LEGALPRACTICE_ADMIN")
  const canApprove = usePermission("LEGALPRACTICE_TRUST_PAYMENT_APPROVE") || isAdmin
  const [status, setStatus] = useState("PENDING"), [page, setPage] = useState(0)
  const [acting, setActing] = useState<{ r: Req; how: "approve" | "self" | "reject" } | null>(null)
  const [showSettings, setShowSettings] = useState(false)
  const qc = useQueryClient()
  const settings = useQuery({ queryKey: ["lp-trust-settings"], queryFn: async () => unwrap<Settings>(await apiClient.get(`${BASE}/trust-settings`)) })
  const list = useQuery({ queryKey: ["lp-trust-requests", status, page], queryFn: async () => pageOf<Req>(await apiClient.get(`${BASE}/trust-payment-requests`, { params: { status: status || undefined, page, size: 15 } })) })
  const selfAllowed = settings.data?.selfApprovalAllowed ?? false
  const refresh = () => { for (const k of ["lp-trust-requests", "lp-trust", "lp-client", "lp-clients", "lp-clients-all", "lp-client-invoices", "lp-invoices"]) qc.invalidateQueries({ queryKey: [k] }) }
  const withdraw = useMutation({
    mutationFn: (id: string) => apiClient.post(`${BASE}/trust-payment-requests/${id}/cancel`),
    onSuccess: () => { toast.success("Request withdrawn"); refresh() },
    onError: async e => toast.error(await errorText(e, "Could not withdraw that")),
  })
  const rows = list.data?.content ?? []
  return (
    <div>
      <SectionTitle right={isAdmin && <Btn onClick={() => setShowSettings(true)}>Approval rules</Btn>}>Trust payment approvals</SectionTitle>
      <div style={{ ...muted, marginBottom: 10 }}>
        Money leaves a client's trust account only after one person asks and a different person approves.
        {selfAllowed && <> <b>This firm allows one person to approve their own request</b>, with a written reason each time.</>}
      </div>
      <div style={{ maxWidth: 260, marginBottom: 8 }}><Pick label="Show" value={status} onChange={v => { setStatus(v); setPage(0) }} options={FILTERS} /></div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <div style={{ ...muted, padding: 14 }}>Could not load the requests.</div> : list.isLoading ? <div style={{ ...muted, padding: 14 }}>Loading…</div> : rows.length === 0 ? <Empty>{status === "PENDING" ? "Nothing is waiting for approval." : "No requests to show."}</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Asked</th><th style={th}>Client</th><th style={th}>What</th><th style={{ ...th, textAlign: "right" }}>Amount</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(r => {
              const st = REQUEST_STATUS[r.status] ?? { label: r.status, tone: "neutral" as const }
              const mode = canApprove ? approvalMode(r, me, selfAllowed) : "none"
              return (
                <tr key={r.id}>
                  <td style={td}>{day(r.requestedAt)}<div style={muted}>{r.requestedByName ?? ""}</div></td>
                  <td style={td}>{r.clientName ?? "—"}</td>
                  <td style={td}>{REQUEST_TYPE[r.paymentType] ?? r.paymentType}<div style={muted}>{[r.payee, r.reference, r.notes].filter(Boolean).join(" · ")}</div></td>
                  <td style={{ ...td, textAlign: "right" }}>{money(r.amount)}</td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill>
                    {r.decidedByName && <div style={muted}>{r.decidedByName}{r.decidedAt ? ` · ${day(r.decidedAt)}` : ""}</div>}
                    {r.selfApproved && <div style={muted}><b>Approved by the same person who asked</b></div>}
                    {r.decisionReason && <div style={muted}>{r.decisionReason}</div>}
                  </td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    {(mode === "approve" || mode === "self") && <Btn small kind="primary" onClick={() => setActing({ r, how: mode })}>{mode === "self" ? "Approve (own request)" : "Approve"}</Btn>}{" "}
                    {(mode === "approve" || mode === "self") && <Btn small onClick={() => setActing({ r, how: "reject" })}>Reject</Btn>}
                    {mode === "blocked" && <span style={muted}>Needs someone else</span>}
                    {canWithdraw(r, me) && <Btn small disabled={withdraw.isPending} onClick={() => withdraw.mutate(r.id)}>Withdraw</Btn>}
                  </td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />
      {acting && <DecideDialog r={acting.r} how={acting.how} onDone={refresh} onClose={() => setActing(null)} />}
      {showSettings && settings.data && <SettingsDialog s={settings.data} onClose={() => setShowSettings(false)} />}
    </div>
  )
}

function DecideDialog({ r, how, onDone, onClose }: { r: Req; how: "approve" | "self" | "reject"; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""), [err, setErr] = useState("")
  const needsReason = how !== "approve"
  const problem = needsReason ? reasonProblem(reason) : null
  const go = useMutation({
    mutationFn: () => how === "reject"
      ? apiClient.post(`${BASE}/trust-payment-requests/${r.id}/reject`, { reason: reason.trim() })
      : apiClient.post(`${BASE}/trust-payment-requests/${r.id}/approve`, how === "self" ? { selfApprovalReason: reason.trim() } : {}),
    onSuccess: () => { toast.success(how === "reject" ? "Request rejected" : "Approved and paid out of trust"); onDone(); onClose() },
    onError: async e => setErr(await errorText(e, "That did not work")),
  })
  const title = how === "reject" ? "Reject this request" : how === "self" ? "Approve your own request" : "Approve this payment"
  return (
    <Dialog title={title} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || go.isPending} onClick={() => { setErr(""); go.mutate() }}>{go.isPending ? "Working…" : how === "reject" ? "Reject" : "Approve and pay"}</Btn></>}>
      <div style={{ marginBottom: 10 }}><b>{money(r.amount)}</b> · {REQUEST_TYPE[r.paymentType] ?? r.paymentType}{r.clientName ? ` · ${r.clientName}` : ""}{r.payee ? ` · to ${r.payee}` : ""}</div>
      {how === "approve" && <div style={muted}>The payment is posted to the client's trust ledger as soon as you approve. The balance is checked again at that moment.</div>}
      {how === "self" && <div style={{ ...muted, marginBottom: 8 }}>You asked for this payment and nobody else is approving it. The firm allows that, but it is recorded as approved by the same person, with your reason.</div>}
      {needsReason && <Input label={how === "reject" ? "Why is it rejected?" : "Why are you approving your own request?"} value={reason} onChange={setReason} autoFocus hint="At least 10 characters" />}
      {needsReason && problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function SettingsDialog({ s, onClose }: { s: Settings; onClose: () => void }) {
  const qc = useQueryClient()
  const next = !s.selfApprovalAllowed
  const [reason, setReason] = useState(""), [err, setErr] = useState("")
  const problem = reasonProblem(reason)
  const save = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/trust-settings`, { selfApprovalAllowed: next, reason: reason.trim() }),
    onSuccess: () => { toast.success("Setting changed"); qc.invalidateQueries({ queryKey: ["lp-trust-settings"] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not change the setting")),
  })
  return (
    <Dialog title="Approval rules" onClose={onClose} footer={<><Btn onClick={onClose}>Close</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{next ? "Allow self-approval" : "Require a second person"}</Btn></>}>
      <div style={{ marginBottom: 8 }}>Right now: <b>{s.selfApprovalAllowed ? "one person may approve their own trust payment, with a reason" : "a second person must approve every trust payment"}</b>.</div>
      <div style={{ ...muted, marginBottom: 8 }}>{next
        ? "Use this only if the firm has a single person able to approve, such as a sole practitioner. Each self-approval still needs a written reason and is flagged in the record."
        : "This restores the normal rule: the person who asks can never approve the same payment."}</div>
      <Input label={next ? "Why is the exception needed?" : "Why is it being switched off?"} value={reason} onChange={setReason} hint="At least 10 characters. Kept permanently." />
      {err && <ErrorLine text={err} />}
      {s.history.length > 0 && <>
        <div style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 6px" }}>History</div>
        {s.history.map((h, i) => <div key={i} style={{ ...muted, marginBottom: 4 }}>{day(h.changedAt)} · {h.changedByName ?? "Someone"}: {h.selfApprovalAllowed ? "allowed self-approval" : "required a second person"} — {h.reason}</div>)}
      </>}
    </Dialog>
  )
}
