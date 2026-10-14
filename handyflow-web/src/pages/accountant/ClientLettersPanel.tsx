// src/pages/accountant/ClientLettersPanel.tsx
// Engagement letters for one client (finance F22): draft, send (the wording is frozen), record the client's acceptance.
// Sending is recorded here; the letter itself is shown as text to copy into an email or document.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { emptyLetter, FEE_BASIS, formFromLetter, fmtR, LETTER_STATUS, letterActions, letterProblem, letterRequest, type LetterForm } from "./practice.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const today = () => new Date().toISOString().slice(0, 10)
const box: React.CSSProperties = { padding: "9px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12, marginBottom: 8 }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const inp: React.CSSProperties = { width: "100%", padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function ClientLettersPanel({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canWrite = usePermission("ACCOUNTANT_WRITE")
  const base = `/api/v1/accountant`
  const [form, setForm] = useState<LetterForm | null>(null)
  const [editing, setEditing] = useState<string | null>(null)
  const [accepting, setAccepting] = useState<string | null>(null)
  const [acc, setAcc] = useState({ name: "", method: "SIGNED_COPY", on: today() })
  const [text, setText] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const { data: letters = [] } = useQuery<any[]>({ queryKey: ["acc-letters", clientId], queryFn: async () => unwrap(await apiClient.get(`${base}/clients/${clientId}/letters`)) ?? [] })
  const refresh = () => qc.invalidateQueries({ queryKey: ["acc-letters", clientId] })
  const fail = (e: any) => setError(errorOf(e))
  const save = useMutation({
    mutationFn: () => editing ? apiClient.put(`${base}/letters/${editing}`, letterRequest(form!)) : apiClient.post(`${base}/clients/${clientId}/letters`, letterRequest(form!)),
    onSuccess: () => { setError(null); setForm(null); setEditing(null); refresh() }, onError: fail,
  })
  const act = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: any }) => apiClient.post(`${base}/letters/${id}/${action}`, body ?? {}),
    onSuccess: () => { setError(null); setAccepting(null); refresh() }, onError: fail,
  })
  const view = async (id: string) => { try { setText(unwrap(await apiClient.get(`${base}/letters/${id}/text`)).text) } catch (e) { fail(e) } }

  const problem = form ? letterProblem(form) : null
  const set = (p: Partial<LetterForm>) => setForm(f => ({ ...f!, ...p }))

  return (
    <div>
      <h4 style={{ margin: "0 0 8px", fontSize: 14 }}>Engagement letters</h4>
      {letters.length === 0 && <div style={box}>No engagement letter yet.</div>}
      {letters.map(l => (
        <div key={l.id} style={box}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
            <span><b>Version {l.version}</b> · {l.title} · {FEE_BASIS[l.feeBasis]}{l.feeAmount != null ? ` ${fmtR(l.feeAmount)}` : ""}</span>
            <span>{LETTER_STATUS[l.status] ?? l.status}</span>
          </div>
          <div style={{ color: "var(--hf-text-secondary)" }}>From {l.startDate}{l.endDate ? ` to ${l.endDate}` : ""}
            {l.status === "ACCEPTED" && ` · accepted by ${l.acceptedByName} on ${l.acceptedOn}`}
            {l.status === "DECLINED" && l.declineReason && ` · ${l.declineReason}`}</div>
          <div style={{ display: "flex", gap: 6, marginTop: 6, flexWrap: "wrap" }}>
            {letterActions(l.status).map(a => {
              if (a === "view") return <button key={a} style={btn} onClick={() => view(l.id)}>Show letter</button>
              if (!canWrite) return null
              if (a === "edit") return <button key={a} style={btn} onClick={() => { setForm(formFromLetter(l)); setEditing(l.id) }}>Edit</button>
              if (a === "send") return <button key={a} style={btn} onClick={() => { if (window.confirm("Mark as sent? The wording is fixed from now on.")) act.mutate({ id: l.id, action: "send" }) }}>Mark as sent</button>
              if (a === "accept") return <button key={a} style={btn} onClick={() => setAccepting(l.id)}>Record acceptance</button>
              if (a === "decline") return <button key={a} style={btn} onClick={() => { const reason = window.prompt("Why was it declined? (optional)") ; if (reason !== null) act.mutate({ id: l.id, action: "decline", body: { reason } }) }}>Declined</button>
              return <button key={a} style={btn} onClick={() => { if (window.confirm("End this letter?")) act.mutate({ id: l.id, action: "end" }) }}>End letter</button>
            })}
          </div>
          {accepting === l.id && (
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8, alignItems: "center" }}>
              <input aria-label="Accepted by" placeholder="Name of the person at the client" value={acc.name} onChange={e => setAcc({ ...acc, name: e.target.value })} style={{ ...inp, width: 220 }} />
              <select aria-label="How" value={acc.method} onChange={e => setAcc({ ...acc, method: e.target.value })} style={{ ...inp, width: 140 }}>
                <option value="SIGNED_COPY">Signed copy</option><option value="EMAIL">Email</option><option value="OTHER">Other</option></select>
              <input aria-label="Accepted on" type="date" value={acc.on} onChange={e => setAcc({ ...acc, on: e.target.value })} style={{ ...inp, width: 140 }} />
              <button style={btn} disabled={!acc.name.trim() || act.isPending} onClick={() => act.mutate({ id: l.id, action: "accept", body: { acceptedByName: acc.name, method: acc.method, acceptedOn: acc.on } })}>Save</button>
            </div>
          )}
        </div>
      ))}
      {text && (
        <div style={box}>
          <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", margin: 0 }}>{text}</pre>
          <button style={{ ...btn, marginTop: 6 }} onClick={() => setText(null)}>Close</button>
        </div>
      )}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginBottom: 8 }}>{error}</div>}
      {canWrite && !form && <button style={btn} onClick={() => { setForm(emptyLetter(today())); setEditing(null) }}>New letter</button>}
      {form && (
        <div style={box}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <label style={{ gridColumn: "1/-1", fontSize: 11 }}>Title<input aria-label="Letter title" value={form.title} onChange={e => set({ title: e.target.value })} style={inp} /></label>
            <label style={{ gridColumn: "1/-1", fontSize: 11 }}>Services covered<textarea aria-label="Services covered" rows={4} value={form.scope} onChange={e => set({ scope: e.target.value })} style={inp} /></label>
            <label style={{ gridColumn: "1/-1", fontSize: 11 }}>Not covered (optional, a standard sentence is used if empty)<textarea aria-label="Not covered" rows={2} value={form.exclusions} onChange={e => set({ exclusions: e.target.value })} style={inp} /></label>
            <label style={{ fontSize: 11 }}>Fees<select aria-label="Fee basis" value={form.feeBasis} onChange={e => set({ feeBasis: e.target.value })} style={inp}>
              {Object.entries(FEE_BASIS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
            <label style={{ fontSize: 11 }}>Amount (R, excl. VAT)<input aria-label="Fee amount" value={form.feeAmount} onChange={e => set({ feeAmount: e.target.value })} style={inp} /></label>
            <label style={{ fontSize: 11 }}>From<input aria-label="Starts" type="date" value={form.startDate} onChange={e => set({ startDate: e.target.value })} style={inp} /></label>
            <label style={{ fontSize: 11 }}>To (optional)<input aria-label="Ends" type="date" value={form.endDate} onChange={e => set({ endDate: e.target.value })} style={inp} /></label>
          </div>
          <p style={{ fontSize: 11, color: "var(--hf-text-secondary)" }}>The standard clauses are a starting point. Have your professional body or lawyer check them before you rely on them.</p>
          {problem && <div style={{ fontSize: 11, color: "var(--hf-danger-text, #b91c1c)" }}>{problem}</div>}
          <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
            <button style={btn} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Save draft</button>
            <button style={btn} onClick={() => { setForm(null); setEditing(null) }}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}
