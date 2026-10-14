// Handing a month to the accountant. The accountant (a different person) accepts it, which closes the month, or returns it with a reason.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { dateText, errorOf, handoverProblem, returnProblem, rowsOf, type Handover, type WorkRow } from "./bookkeeping.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, td, th } from "./ui"

export default function HandoverTab() {
  return <ClientScope>{clientId => <Handovers clientId={clientId} />}</ClientScope>
}

const STATUS: Record<string, string> = { SENT: "With the accountant", ACCEPTED: "Accepted", RETURNED: "Returned for fixes" }

function Handovers({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const canRespond = usePermission("BOOKKEEPING_ADMIN")
  const today = new Date().toISOString().slice(0, 10)
  const [periodEnd, setPeriodEnd] = useState(""), [note, setNote] = useState("")
  const [responding, setResponding] = useState<{ id: string; action: "accept" | "return" } | null>(null), [reply, setReply] = useState("")
  const [message, setMessage] = useState<string | null>(null), [error, setError] = useState<string | null>(null)
  const { data: list = [] } = useQuery<Handover[]>({ queryKey: ["bk-handovers", clientId], queryFn: async () => rowsOf<Handover>(await apiClient.get(`/api/v1/bookkeeping/clients/${clientId}/handovers`)) })
  const { data: queue = [] } = useQuery<WorkRow[]>({ queryKey: ["bk-work-queue"], queryFn: async () => rowsOf<WorkRow>(await apiClient.get("/api/v1/bookkeeping/work-queue")) })
  const work = queue.find(r => r.clientId === clientId)
  const openOne = list.some(h => h.status === "SENT")
  const problem = handoverProblem(periodEnd, today, work, openOne)
  const changed = (r: any) => { setError(null); qc.setQueryData(["bk-handovers", clientId], rowsOf<Handover>(r)); qc.invalidateQueries({ queryKey: ["bk-work-queue"] }); qc.invalidateQueries({ queryKey: ["bk-periods"] }) }
  const fail = (e: any) => { setMessage(null); setError(errorOf(e)) }
  const send = useMutation({ mutationFn: () => apiClient.post(`/api/v1/bookkeeping/clients/${clientId}/handovers`, { periodEnd, note: note.trim() || null }),
    onSuccess: r => { changed(r); const w = rowsOf<Handover>(r)[0]?.warnings ?? []; setMessage(w.length ? `Sent. ${w.join(" ")}` : "Sent to the accountant."); setPeriodEnd(""); setNote("") }, onError: fail })
  const respond = useMutation({ mutationFn: () => apiClient.post(`/api/v1/bookkeeping/handovers/${responding!.id}/${responding!.action}`, { note: reply.trim() || null }),
    onSuccess: r => { changed(r); setMessage(responding!.action === "accept" ? "Accepted. The month is closed." : "Returned to the bookkeeper."); setResponding(null); setReply("") }, onError: fail })
  const replyProblem = responding?.action === "return" ? returnProblem(reply) : null
  return (
    <div>
      {work && (work.unreconciled > 0 || work.draftJournals > 0 || work.openQueries > 0) && (
        <div style={{ ...card, fontSize: 13 }}>Before handing over: {work.unreconciled} not categorised, {work.draftJournals} draft {work.draftJournals === 1 ? "journal" : "journals"}, {work.openQueries} open {work.openQueries === 1 ? "query" : "queries"}.</div>
      )}
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Hand over to the accountant</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <label style={{ fontSize: 12 }}>Up to <input aria-label="Handover date" type="date" max={today} value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} style={field} /></label>
            <input aria-label="Note" placeholder="Note for the accountant (optional)" value={note} onChange={e => setNote(e.target.value)} style={{ ...field, width: 320 }} />
            <button style={primary} disabled={!!problem || send.isPending} onClick={() => send.mutate()}>Send</button>
          </div>
          {problem && periodEnd && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
        </div>
      )}
      {message && <div role="status" style={{ ...card, fontSize: 13 }}>{message}</div>}
      {error && <div role="alert" style={{ ...danger, marginBottom: 8 }}>{error}</div>}
      <div style={card}>
        {list.length === 0 && <p style={muted}>No handovers yet.</p>}
        {list.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Up to</th><th style={th}>Sent</th><th style={th}>Status</th><th style={th}>Notes</th><th /></tr></thead>
            <tbody>{list.map(h => (
              <tr key={h.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}>{dateText(h.periodEnd)}</td><td style={td}>{dateText(h.sentAt)}</td><td style={td}>{STATUS[h.status] ?? h.status}</td>
                <td style={td}>{h.note}{h.responseNote && <div style={muted}>Accountant: {h.responseNote}</div>}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canRespond && h.status === "SENT" && <>
                  <button style={btn} onClick={() => setResponding({ id: h.id, action: "accept" })}>Accept</button>{" "}
                  <button style={btn} onClick={() => setResponding({ id: h.id, action: "return" })}>Return</button></>}</td>
              </tr>))}</tbody>
          </table>
        )}
        {responding && (
          <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <input aria-label="Reply note" placeholder={responding.action === "return" ? "What needs fixing" : "Note (optional)"} value={reply} onChange={e => setReply(e.target.value)} style={{ ...field, width: 340 }} />
            <button style={primary} disabled={!!replyProblem || respond.isPending} onClick={() => respond.mutate()}>{responding.action === "accept" ? "Accept handover" : "Return handover"}</button>
            <button style={btn} onClick={() => { setResponding(null); setReply("") }}>Cancel</button>
            {replyProblem && reply && <span style={muted}>{replyProblem}</span>}
          </div>
        )}
      </div>
    </div>
  )
}
