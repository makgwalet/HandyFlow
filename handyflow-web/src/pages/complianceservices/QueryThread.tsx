// src/pages/complianceservices/QueryThread.tsx
//
// One client query: the messages, a reply box and closing. A reply is recorded under the person who wrote it; the client sees only "The practice".
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { bodyProblem, queryStatusLabel, type StaffQueryDetail } from "./contactRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }

export default function QueryThread({ id, canManage, onChanged }: { id: string; canManage: boolean; onChanged: () => void }) {
  const qc = useQueryClient()
  const [body, setBody] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data, isLoading } = useQuery<StaffQueryDetail>({
    queryKey: ["cs-portal-query", id],
    queryFn: async () => unwrap<StaffQueryDetail>(await apiClient.get(`/api/v1/compliance-services/portal-queries/${id}`)),
  })
  const send = async (path: string, payload?: object) => {
    setBusy(true); setError("")
    try {
      const res = await apiClient.post(`/api/v1/compliance-services/portal-queries/${id}/${path}`, payload)
      qc.setQueryData(["cs-portal-query", id], unwrap<StaffQueryDetail>(res))
      setBody(""); onChanged()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const reply = () => { const p = bodyProblem(body); if (p) { setError(p); return } void send("messages", { body: body.trim() }) }

  if (isLoading || !data) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>
  const closed = data.query.status === "CLOSED"
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{queryStatusLabel(data.query.status)} · {data.query.messageCount} messages</div>
      {data.messages.map((m, i) => (
        <div key={i} style={{ padding: "8px 12px", borderRadius: 10, background: m.authorType === "CLIENT" ? "var(--hf-surface-muted)" : "var(--hf-success-soft)", fontSize: 13, whiteSpace: "pre-wrap" }}>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 2 }}>{m.authorType === "CLIENT" ? (m.authorName ?? "Client") : "The practice"} · {m.createdAt.slice(0, 16).replace("T", " ")}</div>
          {m.body}
        </div>
      ))}
      {canManage && !closed && (
        <>
          <textarea aria-label="Your reply" rows={3} style={input} value={body} onChange={e => setBody(e.target.value)} placeholder="Write your reply. The client will see it as coming from the practice." />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={reply}>Send reply</button>
            <button style={ghost} disabled={busy} onClick={() => void send("close")}>Close this query</button>
          </div>
        </>
      )}
      {closed && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>This query is closed. The client can raise a new one if they need to.</div>}
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}
    </div>
  )
}
