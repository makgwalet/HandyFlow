// src/pages/complianceservices/TimePage.tsx
//
// Record the hours you worked for a client, and see your last two weeks. An entry is never edited or deleted: if it is wrong, void it with a
// reason and enter it again. Hours only; what anyone costs is not shown here.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { entryProblem, formatMinutes, isoDay, kindLabel, KINDS, parseDuration, reasonProblem, type EntryForm, type TimeEntry, type TimeSummary } from "./timeRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12 }

type ClientOption = { id: string; name: string }
const B = "/api/v1/compliance-services"

export default function TimePage() {
  const qc = useQueryClient()
  const [today] = useState(() => isoDay(new Date()))
  const [from] = useState(() => { const d = new Date(); d.setDate(d.getDate() - 13); return isoDay(d) })
  const [weekFrom] = useState(() => { const d = new Date(); d.setDate(d.getDate() - 6); return isoDay(d) })
  const [form, setForm] = useState<EntryForm>({ clientId: "", workDate: today, duration: "", serviceCode: "", kind: "PLANNED", billable: true, note: "" })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [voiding, setVoiding] = useState<string | null>(null)
  const [reason, setReason] = useState("")

  const { data: clients = [] } = useQuery<ClientOption[]>({
    queryKey: ["cs-time-clients"],
    queryFn: async () => { const d = unwrap<ClientOption[] | { content?: ClientOption[] }>(await apiClient.get(`${B}/clients?size=200`)); return Array.isArray(d) ? d : d?.content ?? [] },
  })
  const mine = useQuery<TimeEntry[]>({ queryKey: ["cs-time-mine", from, today], queryFn: async () => unwrap<TimeEntry[]>(await apiClient.get(`${B}/time-entries/mine`, { params: { from, to: today } })) })
  const summary = useQuery<TimeSummary>({ queryKey: ["cs-time-summary", weekFrom, today], queryFn: async () => unwrap<TimeSummary>(await apiClient.get(`${B}/time-entries/summary`, { params: { from: weekFrom, to: today } })) })
  const changed = () => { qc.invalidateQueries({ queryKey: ["cs-time-mine"] }); qc.invalidateQueries({ queryKey: ["cs-time-summary"] }); qc.invalidateQueries({ queryKey: ["cs-time-client"] }) }
  const set = <K extends keyof EntryForm>(k: K, v: EntryForm[K]) => setForm(f => ({ ...f, [k]: v }))

  const save = async () => {
    const p = entryProblem(form, today); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      await apiClient.post(`${B}/time-entries`, { clientId: form.clientId, workDate: form.workDate, minutes: parseDuration(form.duration), serviceCode: form.serviceCode.trim() || null, kind: form.kind, billable: form.billable, note: form.note.trim() || null })
      setForm(f => ({ ...f, duration: "", note: "", serviceCode: "" })); changed()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const doVoid = async (id: string) => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${B}/time-entries/${id}/void`, { reason: reason.trim() }); setVoiding(null); setReason(""); changed() }
    catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }

  const s = summary.data
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>My time</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Record the hours you worked. An entry is never changed or deleted; if it is wrong, void it with a reason and enter it again.</div></div>
      {s && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", fontSize: 13 }}>
          <span style={card}>Last 7 days: <strong>{formatMinutes(s.minutes)}</strong></span>
          <span style={card}>Billable: <strong>{formatMinutes(s.billableMinutes)}</strong></span>
          <span style={card}>Rework: <strong>{formatMinutes(s.reworkMinutes)}</strong></span>
          <span style={card}>Unplanned: <strong>{formatMinutes(s.unplannedMinutes)}</strong></span>
        </div>
      )}
      <div style={{ ...card, display: "grid", gap: 10, background: "var(--hf-surface-muted)" }}>
        <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))" }}>
          <div><label htmlFor="t-client" style={label}>Client</label>
            <select id="t-client" style={input} value={form.clientId} onChange={e => set("clientId", e.target.value)}>
              <option value="">Choose a client</option>{clients.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select></div>
          <div><label htmlFor="t-date" style={label}>Day worked</label><input id="t-date" type="date" max={today} style={input} value={form.workDate} onChange={e => set("workDate", e.target.value)} /></div>
          <div><label htmlFor="t-dur" style={label}>How long</label><input id="t-dur" style={input} placeholder="1h 30m" value={form.duration} onChange={e => set("duration", e.target.value)} /></div>
          <div><label htmlFor="t-kind" style={label}>Kind of work</label>
            <select id="t-kind" style={input} value={form.kind} onChange={e => set("kind", e.target.value)}>{KINDS.map(([id, text]) => <option key={id} value={id}>{text}</option>)}</select></div>
          <div><label htmlFor="t-svc" style={label}>Service code (optional)</label><input id="t-svc" style={input} placeholder="CS-REVIEW" value={form.serviceCode} onChange={e => set("serviceCode", e.target.value)} /></div>
        </div>
        <div><label htmlFor="t-note" style={label}>Note (optional)</label><input id="t-note" style={input} value={form.note} onChange={e => set("note", e.target.value)} /></div>
        <label style={{ fontSize: 13 }}><input type="checkbox" checked={form.billable} onChange={e => set("billable", e.target.checked)} /> Billable</label>
        {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}
        <div><button style={solid} disabled={busy} onClick={save}>Record time</button></div>
      </div>
      <div style={{ display: "grid", gap: 8 }}>
        <h3 style={{ margin: 0, fontSize: 14 }}>Your last two weeks</h3>
        {mine.isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
        {!mine.isLoading && (mine.data ?? []).length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>You have not recorded any time in the last two weeks.</div>}
        {(mine.data ?? []).map(e => (
          <div key={e.id} style={{ ...card, opacity: e.status === "VOID" ? 0.6 : 1, display: "grid", gap: 6 }}>
            <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "baseline", fontSize: 13 }}>
              <strong>{formatMinutes(e.minutes)}</strong><span>{e.clientName ?? "Client"}</span>
              <span style={{ color: "var(--hf-text-muted)", fontSize: 12 }}>{e.workDate} · {kindLabel(e.kind)}{e.serviceCode ? ` · ${e.serviceCode}` : ""}{e.billable ? "" : " · not billable"}</span>
              {e.status === "VOID" && <span style={{ fontSize: 12 }}>Voided: {e.voidReason}</span>}
            </div>
            {e.note && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{e.note}</div>}
            {e.status === "ACTIVE" && voiding !== e.id && <div><button style={ghost} onClick={() => { setVoiding(e.id); setReason(""); setError("") }}>Void</button></div>}
            {voiding === e.id && (
              <div style={{ display: "grid", gap: 6 }}>
                <label htmlFor={`void-${e.id}`} style={label}>Why is this entry wrong?</label>
                <input id={`void-${e.id}`} style={input} value={reason} onChange={ev => setReason(ev.target.value)} />
                <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => doVoid(e.id)}>Confirm void</button><button style={ghost} onClick={() => setVoiding(null)}>Back</button></div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
