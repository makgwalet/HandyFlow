// src/pages/security/SharedDevicesPanel.tsx
//
// Phones/tablets kept at a post and used by whichever guard is on duty. A supervisor registers the device once;
// guards then sign in on it with employee code and PIN. Revoking takes a lost or stolen device out of service.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Tablet } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import { canRevokeShared, orderShared, registerBody, sharedLabel, sharedStatusTone, validateRegister, type SharedDevice } from "./sharedDeviceLogic"
import { getCompanyZone, useCompanyZone } from "./companyZone"

const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text-primary)", fontSize: 13 }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }
const fmt = (d?: string | null) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: getCompanyZone() }) : "-"
const errText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "That did not work. Please try again."

export default function SharedDevicesPanel() {
  useCompanyZone()
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE")
  const [form, setForm] = useState({ hardwareId: "", name: "", siteId: "" })
  const [open, setOpen] = useState(false)
  const [err, setErr] = useState("")
  const [msg, setMsg] = useState("")
  const [confirmId, setConfirmId] = useState<string | null>(null)

  const { data: devices = [], isLoading, error } = useQuery<SharedDevice[]>({
    queryKey: ["shared-devices"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/devices/shared"); return r.data?.data ?? r.data ?? [] },
  })
  const { data: sites = [] } = useQuery<{ id: string; name: string }[]>({
    queryKey: ["sites"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/sites?size=100"); const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] },
  })
  const siteNames = Object.fromEntries(sites.map(s => [s.id, s.name]))

  const register = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/security/devices/shared", registerBody(form)),
    onSuccess: () => { setForm({ hardwareId: "", name: "", siteId: "" }); setOpen(false); setErr(""); setMsg("Shared device registered."); qc.invalidateQueries({ queryKey: ["shared-devices"] }) },
    onError: e => setErr(errText(e)),
  })
  const revoke = useMutation({
    mutationFn: async (id: string) => { const r = await apiClient.post(`/api/v1/security/devices/shared/${id}/revoke`); return (r.data?.data ?? r.data) as { sessionsEnded?: number } },
    onSuccess: r => { setConfirmId(null); setErr(""); setMsg(`Device revoked. ${r?.sessionsEnded ?? 0} session${r?.sessionsEnded === 1 ? "" : "s"} ended.`); qc.invalidateQueries({ queryKey: ["shared-devices"] }) },
    onError: e => { setErr(errText(e)); setConfirmId(null) },
  })

  const submit = () => {
    const problem = validateRegister(form)
    if (problem) { setErr(problem); return }
    setErr(""); register.mutate()
  }
  const rows = orderShared(devices, siteNames)

  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text-primary)", display: "flex", alignItems: "center", gap: 8 }}><Tablet size={16} /> Shared site devices</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Kept at a post and used by whoever is on duty. Each sign-in ends the previous guard's session on that device.</div>
        </div>
        {canManage && <button style={primary} onClick={() => { setOpen(o => !o); setMsg("") }}>{open ? "Close" : "Register a device"}</button>}
      </div>

      {err && <div role="alert" style={{ marginBottom: 10, padding: "8px 12px", borderRadius: 8, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text-strong)", fontSize: 12 }}>{err}</div>}
      {msg && <div role="status" style={{ marginBottom: 10, fontSize: 12, color: "var(--hf-success-text-strong)" }}>{msg}</div>}

      {open && canManage && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
          <input aria-label="Hardware ID" placeholder="Hardware ID" style={input} value={form.hardwareId} onChange={e => setForm({ ...form, hardwareId: e.target.value })} />
          <input aria-label="Device name" placeholder="Name, e.g. Gate 2 tablet" style={input} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
          <select aria-label="Site" style={input} value={form.siteId} onChange={e => setForm({ ...form, siteId: e.target.value })}>
            <option value="">No site</option>
            {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <button style={primary} onClick={submit} disabled={register.isPending}>{register.isPending ? "Saving…" : "Register"}</button>
        </div>
      )}

      {confirmId && (
        <div role="alert" style={{ marginBottom: 12, padding: 12, borderRadius: 10, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text-deep)", fontSize: 13 }}>
          This takes the device out of service and signs out whoever is using it.{" "}
          <button style={{ ...btn, padding: "4px 10px", marginLeft: 6 }} onClick={() => revoke.mutate(confirmId)} disabled={revoke.isPending}>Yes, revoke</button>{" "}
          <button style={{ ...btn, padding: "4px 10px" }} onClick={() => setConfirmId(null)}>Cancel</button>
        </div>
      )}

      {isLoading ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Loading…</div>
        : error ? <div style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>Could not load shared devices.</div>
        : rows.length === 0 ? <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No shared devices registered. Guards with their own phones do not need one.</div>
        : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Device</th><th style={th}>Site</th><th style={th}>Status</th><th style={th}>Last seen</th><th style={th}></th></tr></thead>
              <tbody>{rows.map(d => (
                <tr key={d.id}>
                  <td style={td}>{sharedLabel(d)}</td>
                  <td style={td}>{d.siteId ? siteNames[d.siteId] ?? "-" : "-"}</td>
                  <td style={td}><Chip tone={sharedStatusTone(d.status)}>{d.status.toLowerCase()}</Chip></td>
                  <td style={td}>{fmt(d.lastSeenAt)}</td>
                  <td style={td}>{canManage && canRevokeShared(d.status) && <button style={{ ...btn, padding: "4px 10px" }} onClick={() => { setMsg(""); setConfirmId(d.id) }}>Revoke</button>}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
    </div>
  )
}
