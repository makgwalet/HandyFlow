// src/pages/complianceservices/RemindersPage.tsx
//
// The daily digest of the work queue: who gets it, how urgent an item must be to be listed, and a record of what was handed over.
// It repeats what the work queue already shows and never creates work. Handed over is not the same as read.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  LEVELS, MAX_ITEMS, MIN_ITEMS, available, body, formOf, problem, runText, same, staffName,
  type Form, type LogEntry, type Preview, type RunResult, type Settings, type Staff,
} from "./reminderRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const B = "/api/v1/compliance-services/reminders"
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }
const note: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

export default function RemindersPage() {
  const [view, setView] = useState<"settings" | "preview" | "log">("settings")
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={note}>
        A daily message listing what is in the work queue, sent on working days at about 07:20. It repeats what is already recorded and never creates work.
      </div>
      <div style={{ display: "flex", gap: 6 }}>
        <button style={chip(view === "settings")} onClick={() => setView("settings")}>Settings</button>
        <button style={chip(view === "preview")} onClick={() => setView("preview")}>Preview and send</button>
        <button style={chip(view === "log")} onClick={() => setView("log")}>History</button>
      </div>
      {view === "settings" && <SettingsPanel />}
      {view === "preview" && <PreviewPanel />}
      {view === "log" && <LogPanel />}
    </div>
  )
}

function SettingsPanel() {
  const qc = useQueryClient()
  const { data } = useQuery<Settings>({ queryKey: ["cs-reminder-settings"], queryFn: async () => unwrap<Settings>(await apiClient.get(`${B}/settings`)) })
  const { data: staff = [] } = useQuery<Staff[]>({
    queryKey: ["cs-reminder-staff"],
    queryFn: async () => { const d = unwrap<Staff[] | { content?: Staff[] }>(await apiClient.get("/api/v1/identity/users")); return Array.isArray(d) ? d : d?.content ?? [] },
  })
  const [edit, setEdit] = useState<Form | null>(null)
  const [pick, setPick] = useState("")
  const [error, setError] = useState("")
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)
  if (!data) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>

  const f = edit ?? formOf(data)
  const change = (patch: Partial<Form>) => { setSaved(false); setEdit({ ...f, ...patch }) }
  const nameOf = (id: string) => data.recipients.find(r => r.userId === id)?.name ?? (staff.find(s => s.id === id) ? staffName(staff.find(s => s.id === id)!) : "Unknown person")
  const blocked = problem(f)
  const dirty = !same(f, formOf(data))
  const save = async () => {
    setBusy(true); setError("")
    try {
      await apiClient.put(`${B}/settings`, body(f))
      setEdit(null); setSaved(true)
      qc.invalidateQueries({ queryKey: ["cs-reminder-settings"] }); qc.invalidateQueries({ queryKey: ["cs-reminder-preview"] })
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 10, maxWidth: 560 }}>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, color: "var(--hf-text)" }}>
        <input type="checkbox" checked={f.enabled} onChange={e => change({ enabled: e.target.checked })} /> Send the daily digest
      </label>
      <div>
        <span style={label}>What to include</span>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {LEVELS.map(l => <button key={l[0]} style={chip(f.minSeverity === l[0])} aria-pressed={f.minSeverity === l[0]} onClick={() => change({ minSeverity: l[0] })}>{l[1]}</button>)}
        </div>
      </div>
      <div>
        <label style={label} htmlFor="r-max">Most items listed ({MIN_ITEMS} to {MAX_ITEMS})</label>
        <input id="r-max" type="number" style={{ ...input, maxWidth: 120 }} value={f.maxItems} onChange={e => change({ maxItems: e.target.value })} />
      </div>
      <div style={{ display: "grid", gap: 6 }}>
        <span style={label}>Who receives it</span>
        {f.recipients.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Nobody chosen yet.</div>}
        {f.recipients.map((r, i) => (
          <div key={r.userId} style={{ display: "flex", gap: 10, alignItems: "center", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "6px 10px" }}>
            <span style={{ flex: 1, fontSize: 13, color: "var(--hf-text)" }}>{nameOf(r.userId)}</span>
            <label style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
              <input type="checkbox" checked={r.adminItems} onChange={e => change({ recipients: f.recipients.map((x, n) => (n === i ? { ...x, adminItems: e.target.checked } : x)) })} /> Also administrator items
            </label>
            <button style={ghost} aria-label={`Remove ${nameOf(r.userId)}`} onClick={() => change({ recipients: f.recipients.filter((_, n) => n !== i) })}>Remove</button>
          </div>
        ))}
        <div style={{ display: "flex", gap: 6 }}>
          <select style={input} aria-label="Add a person" value={pick} onChange={e => setPick(e.target.value)}>
            <option value="">Add a person...</option>
            {available(staff, f).map(s => <option key={s.id} value={s.id}>{staffName(s)}</option>)}
          </select>
          <button style={ghost} disabled={!pick} onClick={() => { change({ recipients: [...f.recipients, { userId: pick, adminItems: false }] }); setPick("") }}>Add</button>
        </div>
        <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Administrator items are the ones only administrators act on, such as approvals.</div>
      </div>
      {blocked && dirty && <div style={bad}>{blocked}</div>}
      {error && <div style={bad}>{error}</div>}
      {saved && !dirty && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Saved.</div>}
      <div><button style={solid} disabled={busy || !dirty || !!blocked} onClick={save}>Save</button></div>
    </div>
  )
}

function PreviewPanel() {
  const qc = useQueryClient()
  const { data = [], isLoading } = useQuery<Preview[]>({ queryKey: ["cs-reminder-preview"], queryFn: async () => unwrap<Preview[]>(await apiClient.get(`${B}/preview`)) })
  const [result, setResult] = useState<RunResult | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const send = async () => {
    setBusy(true); setError(""); setResult(null)
    try {
      setResult(unwrap<RunResult>(await apiClient.post(`${B}/send-now`, {})))
      qc.invalidateQueries({ queryKey: ["cs-reminder-log"] }); qc.invalidateQueries({ queryKey: ["cs-reminder-preview"] })
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={note}>What each person would get today. Nothing is sent from this list. Sending now skips anyone who has already had today's digest.</div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nobody is chosen to receive the digest.</div>}
      {data.map(p => (
        <div key={p.userId} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 4 }}>
          <div style={{ display: "flex", gap: 8 }}><span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{p.name}</span><span style={{ flex: 1 }} /><span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{p.subject}</span></div>
          {p.body && <pre style={{ margin: 0, fontSize: 12, whiteSpace: "pre-wrap", color: "var(--hf-text-muted)", fontFamily: "inherit" }}>{p.body}</pre>}
        </div>
      ))}
      {error && <div style={bad}>{error}</div>}
      {result && <div style={{ fontSize: 13, color: "var(--hf-text)" }}>{runText(result)}.</div>}
      <div><button style={solid} disabled={busy || data.length === 0} onClick={send}>Send now</button></div>
    </div>
  )
}

function LogPanel() {
  const { data = [], isLoading } = useQuery<LogEntry[]>({ queryKey: ["cs-reminder-log"], queryFn: async () => unwrap<LogEntry[]>(await apiClient.get(`${B}/log`)) })
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div style={note}>What happened to each person's digest. "Handed to the notification service" means it was passed on, not that it was read.</div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing has been sent yet.</div>}
      {data.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th scope="col">Day</th><th scope="col">Person</th><th scope="col">Items</th><th scope="col">What happened</th></tr></thead>
          <tbody>
            {data.map((l, i) => (
              <tr key={i} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td>{l.runDate}</td><td>{l.name}</td><td>{l.itemCount}</td>
                <td>{l.outcomeText}{l.detail ? ` (${l.detail})` : ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
