import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

export interface ReviewNote {
  id: string; workpaperId: string; note: string; raisedBy: string; raisedAt: string
  status: "OPEN" | "RESPONDED" | "CLEARED"; response: string | null; timesReturned: number
}

const MIN_TEXT = 5
const listOf = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? d?.content ?? [])
const STATUS_TEXT = { OPEN: "Waiting for an answer", RESPONDED: "Answered, to be checked", CLEARED: "Cleared" } as const

/** A reviewer's notes on one workpaper. The workpaper cannot be signed off until every note is cleared. */
export default function ReviewNotesPanel({ workpaperId }: { workpaperId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("AUDIT_MANAGE") || usePermission("AUDIT_ADMIN")
  const [text, setText] = useState("")
  const [error, setError] = useState("")
  const key = ["ia-review-notes", workpaperId]

  const { data: notes = [] } = useQuery<ReviewNote[]>({
    queryKey: key,
    queryFn: async () => listOf<ReviewNote>((await apiClient.get(`/api/v1/internal-audit/workpapers/${workpaperId}/review-notes`)).data),
  })
  const done = () => { qc.invalidateQueries({ queryKey: key }); setError("") }
  const fail = (e: any) => setError(e?.response?.data?.message ?? "That did not work")

  const raise = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/internal-audit/workpapers/${workpaperId}/review-notes`, { text: text.trim() }),
    onSuccess: () => { done(); setText("") }, onError: fail,
  })
  const act = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: "respond" | "clear" | "return"; body?: string }) =>
      apiClient.post(`/api/v1/internal-audit/review-notes/${id}/${action}`, body === undefined ? {} : { text: body.trim() }),
    onSuccess: done, onError: fail,
  })
  const ask = (label: string) => window.prompt(label) ?? ""
  const open = notes.filter(n => n.status !== "CLEARED").length

  return (
    <div style={{ marginTop: 6, padding: "8px 12px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
      <div style={{ fontWeight: 700, color: "var(--hf-text-muted)" }}>
        Review notes {open > 0 ? `— ${open} not cleared, so this workpaper cannot be signed off` : notes.length ? "— all cleared" : ""}
      </div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", margin: "4px 0" }}>{error}</div>}
      {notes.map(n => (
        <div key={n.id} style={{ margin: "6px 0", paddingLeft: 8, borderLeft: "2px solid var(--hf-border)" }}>
          <div>{n.note}</div>
          {n.response && <div style={{ color: "var(--hf-text-faint)" }}>Answer: {n.response}</div>}
          <div style={{ color: "var(--hf-text-faint)" }}>{STATUS_TEXT[n.status]}{n.timesReturned ? ` · sent back ${n.timesReturned}×` : ""}</div>
          {canManage && n.status === "OPEN" && (
            <button onClick={() => { const r = ask("Your answer"); if (r.trim().length >= MIN_TEXT) act.mutate({ id: n.id, action: "respond", body: r }) }}>Answer</button>
          )}
          {canManage && n.status === "RESPONDED" && (
            <>
              <button onClick={() => act.mutate({ id: n.id, action: "clear" })}>Clear</button>{" "}
              <button onClick={() => { const r = ask("Why is the answer not enough?"); if (r.trim().length >= MIN_TEXT) act.mutate({ id: n.id, action: "return", body: r }) }}>Send back</button>
            </>
          )}
        </div>
      ))}
      {canManage && (
        <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
          <input aria-label="Review note" placeholder="Add a review note" value={text} onChange={e => setText(e.target.value)} style={{ flex: 1 }} />
          <button disabled={text.trim().length < MIN_TEXT || raise.isPending} onClick={() => raise.mutate()}>Add note</button>
        </div>
      )}
    </div>
  )
}
