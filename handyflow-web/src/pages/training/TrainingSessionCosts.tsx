// What a session actually cost: trainer, venue, materials, travel and exam fees. The Training reports add these up.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"

type Cost = { id: string; category: string; amount: number; incurredOn: string; note: string | null }
type SessionCosts = { sessionId: string; total: number; people: number; perPerson: number | null; costs: Cost[] }
const CATEGORIES: [string, string][] = [["TRAINER_FEE", "Trainer fee"], ["VENUE", "Venue"], ["MATERIALS", "Materials"], ["TRAVEL", "Travel"], ["CERTIFICATION_FEE", "Certification or exam fee"], ["OTHER", "Other"]]
const word = (k: string) => CATEGORIES.find(c => c[0] === k)?.[1] ?? k
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const field = { padding: "5px 8px", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12 } as const
const rand = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n)
const message = (e: unknown): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message || "That did not work. Try again."
const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export default function TrainingSessionCosts({ sessionId }: { sessionId: string }) {
  const mayView = usePermissions(["TRAINING_REPORTS", "TRAINING_ADMIN"])
  const mayEdit = usePermissions(["TRAINING_MANAGE", "TRAINING_ADMIN"])
  const qc = useQueryClient()
  const key = ["training-session-costs", sessionId]
  const [category, setCategory] = useState("TRAINER_FEE")
  const [amount, setAmount] = useState("")
  const [date, setDate] = useState(today())
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const q = useQuery<SessionCosts>({ queryKey: key, queryFn: async () => data(await apiClient.get(`/api/v1/training/sessions/${sessionId}/costs`)), enabled: mayView, retry: false })
  const set = (v: SessionCosts) => { qc.setQueryData(key, v); qc.invalidateQueries({ queryKey: ["training-report"] }); setError("") }
  const add = useMutation({
    mutationFn: async () => data<SessionCosts>(await apiClient.post(`/api/v1/training/sessions/${sessionId}/costs`, { category, amount: Number(amount), incurredOn: date, note: note.trim() || null })),
    onSuccess: v => { set(v); setAmount(""); setNote("") }, onError: e => setError(message(e)),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => data<SessionCosts>(await apiClient.delete(`/api/v1/training/sessions/${sessionId}/costs/${id}`)),
    onSuccess: set, onError: e => setError(message(e)),
  })
  if (!mayView) return null
  return (
    <div style={{ marginBottom: 24, fontSize: 12 }}>
      <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 8px" }}>
        Costs {q.data && <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>
          {rand(q.data.total)} in total{q.data.perPerson != null ? `, ${rand(q.data.perPerson)} for each of ${q.data.people} ${q.data.people === 1 ? "person" : "people"} who took part` : ""}
        </span>}
      </p>
      {q.data?.costs.map(c => (
        <div key={c.id} style={{ display: "flex", gap: 10, padding: "4px 0", borderBottom: "1px solid var(--hf-border-subtle)", alignItems: "center" }}>
          <span style={{ minWidth: 90 }}>{c.incurredOn}</span><span style={{ minWidth: 150 }}>{word(c.category)}</span>
          <span style={{ minWidth: 90, textAlign: "right" }}>{rand(c.amount)}</span><span style={{ flex: 1, color: "var(--hf-text-muted)" }}>{c.note ?? ""}</span>
          {mayEdit && <button style={{ ...field, cursor: "pointer" }} disabled={remove.isPending} onClick={() => remove.mutate(c.id)}>Remove</button>}
        </div>
      ))}
      {q.data && q.data.costs.length === 0 && <div style={{ color: "var(--hf-text-muted)" }}>No costs recorded yet.</div>}
      {mayEdit && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
          <select aria-label="What the cost was for" value={category} onChange={e => setCategory(e.target.value)} style={field}>{CATEGORIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <input aria-label="Amount in rand" inputMode="decimal" placeholder="Amount (R)" value={amount} onChange={e => setAmount(e.target.value)} style={{ ...field, width: 110 }} />
          <input aria-label="Date" type="date" max={today()} value={date} onChange={e => setDate(e.target.value)} style={field} />
          <input aria-label="Note" placeholder="Note (optional)" value={note} onChange={e => setNote(e.target.value)} style={{ ...field, width: 180 }} />
          <button style={{ ...field, cursor: "pointer" }} disabled={add.isPending || !(Number(amount) > 0)} onClick={() => add.mutate()}>Add cost</button>
        </div>
      )}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
    </div>
  )
}
