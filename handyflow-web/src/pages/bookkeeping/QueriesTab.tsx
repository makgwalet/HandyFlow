// Questions and document requests to the client, and what they answered.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { QUERY_KINDS, answerProblem, dateText, errorOf, kindText, queryProblem, rowsOf, type Query } from "./bookkeeping.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, td, th } from "./ui"

export default function QueriesTab() {
  return <ClientScope>{clientId => <Queries clientId={clientId} />}</ClientScope>
}

function Queries({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const base = `/api/v1/bookkeeping/clients/${clientId}/queries`
  const [status, setStatus] = useState("OPEN")
  const [kind, setKind] = useState("QUESTION"), [text, setText] = useState("")
  const [answering, setAnswering] = useState<string | null>(null), [answer, setAnswer] = useState(""), [docRef, setDocRef] = useState("")
  const [error, setError] = useState<string | null>(null)
  const { data: all = [] } = useQuery<Query[]>({ queryKey: ["bk-queries", clientId], queryFn: async () => rowsOf<Query>(await apiClient.get(base)) })
  const list = all.filter(q => status === "ALL" || q.status === status)
  const changed = (r: any) => { setError(null); qc.setQueryData(["bk-queries", clientId], rowsOf<Query>(r)); qc.invalidateQueries({ queryKey: ["bk-work-queue"] }) }
  const fail = (e: any) => setError(errorOf(e))
  const ask = useMutation({ mutationFn: () => apiClient.post(base, { kind, transactionId: null, question: text.trim() }), onSuccess: r => { changed(r); setText("") }, onError: fail })
  const reply = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/answer`, { answer: answer.trim() || null, documentRef: docRef.trim() || null }),
    onSuccess: r => { changed(r); setAnswering(null); setAnswer(""); setDocRef("") }, onError: fail })
  const close = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/close`), onSuccess: changed, onError: fail })
  const askProblem = queryProblem(kind, text)
  return (
    <div>
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Ask the client</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <select aria-label="Kind" value={kind} onChange={e => setKind(e.target.value)} style={field}>{QUERY_KINDS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}</select>
            <input aria-label="What do you need" placeholder="What do you need?" value={text} onChange={e => setText(e.target.value)} style={{ ...field, width: 380 }} />
            <button style={primary} disabled={!!askProblem || ask.isPending} onClick={() => ask.mutate()}>Ask</button>
          </div>
          {askProblem && text && <div style={{ ...muted, marginTop: 6 }}>{askProblem}</div>}
        </div>
      )}
      <div style={{ marginBottom: 8, fontSize: 12 }}>
        <label>Show <select aria-label="Show" value={status} onChange={e => setStatus(e.target.value)} style={field}>
          <option value="OPEN">Open</option><option value="ANSWERED">Answered</option><option value="CLOSED">Closed</option><option value="ALL">All</option></select></label>
      </div>
      {error && <div role="alert" style={{ ...danger, marginBottom: 8 }}>{error}</div>}
      <div style={card}>
        {list.length === 0 && <p style={muted}>Nothing here.</p>}
        {list.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Asked</th><th style={th}>Type</th><th style={th}>Request</th><th style={th}>Answer</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{list.map(q => (
              <tr key={q.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}>{dateText(q.askedAt)}{q.status === "OPEN" && q.daysOpen > 0 && <div style={muted}>{q.daysOpen} {q.daysOpen === 1 ? "day" : "days"} open</div>}</td>
                <td style={td}>{kindText(q.kind)}</td><td style={td}>{q.question}</td>
                <td style={td}>{q.answer || q.documentRef ? <>{q.answer}{q.documentRef && <div style={muted}>Document: {q.documentRef}</div>}</> : "—"}</td>
                <td style={td}>{q.status === "OPEN" ? "Open" : q.status === "CLOSED" ? "Closed" : "Answered"}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canManage && <>
                  {q.status === "OPEN" && <><button style={btn} onClick={() => setAnswering(answering === q.id ? null : q.id)}>Record answer</button>{" "}</>}
                  {q.status !== "CLOSED" && <button style={btn} onClick={() => close.mutate(q.id)}>Close</button>}</>}</td>
              </tr>))}</tbody>
          </table>
        )}
        {answering && (
          <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Client's answer" placeholder="What the client said" value={answer} onChange={e => setAnswer(e.target.value)} style={{ ...field, width: 280 }} />
            <input aria-label="Where the document is" placeholder="Where the document is" value={docRef} onChange={e => setDocRef(e.target.value)} style={{ ...field, width: 220 }} />
            <button style={primary} disabled={!!answerProblem(answer, docRef) || reply.isPending} onClick={() => reply.mutate(answering)}>Save answer</button>
          </div>
        )}
      </div>
    </div>
  )
}
