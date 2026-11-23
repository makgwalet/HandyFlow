// src/pages/security/GuardDevicesPanel.tsx
//
// The Devices tab on Guard 360: every phone this guard has been bound to, a supervisor action to issue a
// replacement code (a lost or broken phone), and one to sign the guard out everywhere.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Smartphone, KeyRound, LogOut } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import { ACTION_CONFIRM, ACTION_LABEL, deviceActions, deviceLabel, minutesLeft, orderDevices, spacedCode, statusTone, type DeviceAction, type GuardDevice } from "./deviceLogic"
import { getCompanyZone, useCompanyZone } from "./companyZone"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }
const fmt = (d?: string | null) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: getCompanyZone() }) : "-"
const errText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "That did not work. Please try again."

export default function GuardDevicesPanel({ guardId, canManage }: { guardId: string; canManage: boolean }) {
  useCompanyZone()
  const qc = useQueryClient()
  const key = ["guard-devices", guardId]
  const [issued, setIssued] = useState<{ code: string; minutes: number } | null>(null)
  const [signedOut, setSignedOut] = useState<number | null>(null)
  const [confirmSignOut, setConfirmSignOut] = useState(false)
  const [err, setErr] = useState("")
  const canAdmin = usePermission("SECURITY_ADMIN")
  const [pending, setPending] = useState<{ id: string; action: DeviceAction } | null>(null)
  const [deviceMsg, setDeviceMsg] = useState("")

  const { data = [], isLoading, error } = useQuery<GuardDevice[]>({
    queryKey: key,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/guards/${guardId}/devices`); return r.data?.data ?? r.data ?? [] },
  })

  const issue = useMutation({
    mutationFn: async () => { const r = await apiClient.post(`/api/v1/security/guards/${guardId}/initiate-device-replacement`); return (r.data?.data ?? r.data) as { code: string; expiresAt: string } },
    onSuccess: c => { setIssued({ code: c.code, minutes: minutesLeft(c.expiresAt, Date.now()) }); setErr(""); qc.invalidateQueries({ queryKey: key }) },
    onError: e => setErr(errText(e)),
  })

  const signOut = useMutation({
    mutationFn: async () => { const r = await apiClient.post(`/api/v1/security/guards/${guardId}/revoke-tokens`, { reason: "Signed out by supervisor" }); return (r.data?.data ?? r.data) as { tokensRevoked: number } },
    onSuccess: r => { setSignedOut(r?.tokensRevoked ?? 0); setConfirmSignOut(false); setErr("") },
    onError: e => { setErr(errText(e)); setConfirmSignOut(false) },
  })

  const act = useMutation({
    mutationFn: async (v: { id: string; action: DeviceAction }) => {
      const r = await apiClient.post(`/api/v1/security/devices/personal/${v.id}/${v.action}`)
      return (r.data?.data ?? r.data) as { sessionsEnded?: number }
    },
    onSuccess: (r, v) => {
      setPending(null); setErr("")
      setDeviceMsg(v.action === "reinstate" ? "Phone reinstated." : `Done. ${r?.sessionsEnded ?? 0} session${r?.sessionsEnded === 1 ? "" : "s"} ended.`)
      qc.invalidateQueries({ queryKey: key })
    },
    onError: e => { setErr(errText(e)); setPending(null) },
  })

  const devices = orderDevices(data)

  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 12 }}>
        <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text-primary)", display: "flex", alignItems: "center", gap: 8 }}><Smartphone size={16} /> Devices</div>
        {canManage && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button style={primary} onClick={() => issue.mutate()} disabled={issue.isPending}><KeyRound size={14} /> {issue.isPending ? "Issuing…" : "Replace device"}</button>
            <button style={btn} onClick={() => setConfirmSignOut(true)}><LogOut size={14} /> Sign out everywhere</button>
          </div>
        )}
      </div>

      {err && <div role="alert" style={{ marginBottom: 10, padding: "8px 12px", borderRadius: 8, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text-strong)", fontSize: 12 }}>{err}</div>}

      {issued && (
        <div role="status" style={{ marginBottom: 12, padding: 14, borderRadius: 10, background: "var(--hf-info-soft)", border: "1px solid var(--hf-info-border)" }}>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Give this code to the guard. It works once, on the new phone.</div>
          <div style={{ fontSize: 28, fontWeight: 800, letterSpacing: 4, color: "var(--hf-text-primary)", margin: "6px 0" }}>{spacedCode(issued.code)}</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Expires in {issued.minutes} minutes. Their current phone keeps working until the code is used.</div>
        </div>
      )}

      {confirmSignOut && (
        <div role="alert" style={{ marginBottom: 12, padding: 12, borderRadius: 10, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text-deep)", fontSize: 13 }}>
          This signs the guard out of every phone. They will have to log in again.{" "}
          <button style={{ ...btn, padding: "4px 10px", marginLeft: 6 }} onClick={() => signOut.mutate()} disabled={signOut.isPending}>Yes, sign out</button>{" "}
          <button style={{ ...btn, padding: "4px 10px" }} onClick={() => setConfirmSignOut(false)}>Cancel</button>
        </div>
      )}
      {pending && (
        <div role="alert" style={{ marginBottom: 12, padding: 12, borderRadius: 10, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text-deep)", fontSize: 13 }}>
          {ACTION_CONFIRM[pending.action]}{" "}
          <button style={{ ...btn, padding: "4px 10px", marginLeft: 6 }} onClick={() => act.mutate(pending)} disabled={act.isPending}>Yes, {ACTION_LABEL[pending.action].toLowerCase()}</button>{" "}
          <button style={{ ...btn, padding: "4px 10px" }} onClick={() => setPending(null)}>Cancel</button>
        </div>
      )}
      {deviceMsg && <div role="status" style={{ marginBottom: 10, fontSize: 12, color: "var(--hf-success-text-strong)" }}>{deviceMsg}</div>}
      {signedOut !== null && <div role="status" style={{ marginBottom: 10, fontSize: 12, color: "var(--hf-success-text-strong)" }}>Signed out. {signedOut} session{signedOut === 1 ? "" : "s"} ended.</div>}

      {isLoading ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Loading…</div>
        : error ? <div style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>Could not load this guard's devices.</div>
        : devices.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No device has been registered for this guard yet.</div>
        : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Device</th><th style={th}>Status</th><th style={th}>Added</th><th style={th}>Last seen</th><th style={th}></th></tr></thead>
              <tbody>{devices.map(d => (
                <tr key={d.id}><td style={td}>{deviceLabel(d)}</td><td style={td}><Chip tone={statusTone(d.status)}>{d.status.toLowerCase()}</Chip></td>
                  <td style={td}>{fmt(d.createdAt)}</td><td style={td}>{fmt(d.lastSeenAt)}</td>
                  <td style={td}>{deviceActions(d, { manage: canManage, admin: canAdmin }).map(a => (
                    <button key={a} style={{ ...btn, padding: "4px 10px", marginRight: 6 }} onClick={() => { setDeviceMsg(""); setPending({ id: d.id, action: a }) }}>{ACTION_LABEL[a]}</button>
                  ))}</td></tr>
              ))}</tbody>
            </table>
          </div>
        )}
    </div>
  )
}
