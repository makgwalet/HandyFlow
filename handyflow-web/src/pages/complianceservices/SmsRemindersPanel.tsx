// The practice-wide side of SMS reminders: the on/off switch, sending today's now, and what has been handed over.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import SmsLogTable from "./SmsLogTable"
import { runText, type SmsLog, type SmsRun, type SmsSettings } from "./smsRules"

const B = "/api/v1/compliance-services/sms-reminders"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const note: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

export default function SmsRemindersPanel() {
  const qc = useQueryClient()
  const admin = usePermissions(["COMPLIANCE_SERVICES_ADMIN"])
  const [msg, setMsg] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data } = useQuery<SmsSettings>({ queryKey: ["cs-sms-settings"], queryFn: async () => unwrap<SmsSettings>(await apiClient.get(`${B}/settings`)) })
  const { data: log = [] } = useQuery<SmsLog[]>({ queryKey: ["cs-sms-log", "all"], queryFn: async () => unwrap<SmsLog[]>(await apiClient.get(`${B}/log`)) })

  const toggle = async (enabled: boolean) => {
    setBusy(true); setError(""); setMsg("")
    try { await apiClient.put(`${B}/settings`, { enabled }); qc.invalidateQueries({ queryKey: ["cs-sms-settings"] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const sendNow = async () => {
    setBusy(true); setError(""); setMsg("")
    try {
      setMsg(runText(unwrap<SmsRun>(await apiClient.post(`${B}/send-now`, {}))))
      qc.invalidateQueries({ queryKey: ["cs-sms-log"] })
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }

  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={note}>
        Clients can be sent an SMS a week before a deadline and again the day before, at about 08:30, but only at a number where consent has been recorded on the client's SMS tab.
        Withdrawing consent stops the texts straight away. Delivery is not confirmed: the history shows what was handed to the SMS provider.
      </div>
      <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}>
        <input type="checkbox" aria-label="Send SMS reminders to clients" disabled={!admin || busy} checked={!!data?.enabled} onChange={e => toggle(e.target.checked)} />
        Send SMS reminders to clients
      </label>
      {admin && <div><button style={solid} disabled={busy || !data?.enabled} onClick={sendNow}>Send today's reminders now</button></div>}
      {msg && <div role="status" style={note}>{msg}</div>}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
      <SmsLogTable rows={log} showClient />
    </div>
  )
}
