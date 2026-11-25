// SMS reminders for this client: who has agreed to be texted, how that was given, and what has been sent.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import SmsLogTable from "./SmsLogTable"
import { consentProblem, tidyPhone, withdrawProblem, type Consent, type SmsLog } from "./smsRules"

const B = "/api/v1/compliance-services/sms-reminders"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { ...solid, background: "transparent", color: "var(--hf-text-muted)", border: "1px solid var(--hf-border)" }

export default function ClientSmsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState({ contactName: "", phone: "", basis: "" })
  const [withdrawing, setWithdrawing] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: consents = [], isLoading } = useQuery<Consent[]>({ queryKey: ["cs-sms-consents", clientId], queryFn: async () => unwrap<Consent[]>(await apiClient.get(`${B}/clients/${clientId}/consents`)) })
  const { data: log = [] } = useQuery<SmsLog[]>({ queryKey: ["cs-sms-log", clientId], queryFn: async () => unwrap<SmsLog[]>(await apiClient.get(`${B}/log`, { params: { clientId } })) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["cs-sms-consents", clientId] })

  const save = async () => {
    const p = consentProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      await apiClient.post(`${B}/clients/${clientId}/consents`, { contactName: f.contactName.trim(), phone: tidyPhone(f.phone), basis: f.basis.trim() })
      setAdding(false); setF({ contactName: "", phone: "", basis: "" }); refresh()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const withdraw = async (id: string) => {
    const p = withdrawProblem(reason); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${B}/consents/${id}/withdraw`, { note: reason.trim() }); setWithdrawing(null); setReason(""); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }

  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Only numbers listed here as agreed are sent deadline reminders by SMS, and only while SMS reminders are switched on for the practice. Withdrawing consent stops them straight away.</div>
      {canManage && !adding && <div><button style={solid} onClick={() => setAdding(true)}>Record consent for a number</button></div>}
      {adding && (
        <div style={{ display: "grid", gap: 8, background: "var(--hf-surface-muted)", borderRadius: 10, padding: 12 }}>
          <input aria-label="Who the number belongs to" style={input} placeholder="Who the number belongs to" value={f.contactName} onChange={e => setF(x => ({ ...x, contactName: e.target.value }))} />
          <input aria-label="Cell number" style={input} placeholder="+27821234567" value={f.phone} onChange={e => setF(x => ({ ...x, phone: e.target.value }))} />
          <textarea aria-label="How consent was given" style={{ ...input, minHeight: 50 }} placeholder="How consent was given, for example: agreed by phone on 3 Nov, confirmed in the engagement letter" value={f.basis} onChange={e => setF(x => ({ ...x, basis: e.target.value }))} />
          {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Save consent</button><button style={ghost} onClick={() => { setAdding(false); setError("") }}>Cancel</button></div>
        </div>
      )}
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && consents.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No consent has been recorded, so no SMS reminders are sent to this client.</div>}
      {consents.map(c => (
        <div key={c.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 4, fontSize: 13 }}>
          <div><strong>{c.contactName}</strong> {c.phone} {c.active ? "· Agreed" : "· Withdrawn"}</div>
          <div style={{ color: "var(--hf-text-muted)" }}>{c.basis}</div>
          {!c.active && <div style={{ color: "var(--hf-text-muted)" }}>Withdrawn: {c.withdrawnNote}</div>}
          {c.active && canManage && withdrawing !== c.id && <div><button style={ghost} onClick={() => { setWithdrawing(c.id); setError("") }}>Withdraw consent</button></div>}
          {withdrawing === c.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <textarea aria-label="Why consent is withdrawn" style={{ ...input, minHeight: 40 }} placeholder="Why consent is withdrawn" value={reason} onChange={e => setReason(e.target.value)} />
              {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => withdraw(c.id)}>Confirm withdrawal</button><button style={ghost} onClick={() => { setWithdrawing(null); setError("") }}>Cancel</button></div>
            </div>
          )}
        </div>
      ))}
      <div style={{ fontSize: 13, fontWeight: 600 }}>Sent to this client</div>
      <SmsLogTable rows={log} showClient={false} />
    </div>
  )
}
