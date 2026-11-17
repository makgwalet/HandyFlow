import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, ReasonModal, inputStyle } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { GAUGE_STATUS, canAccept, onlyAliases, onlyGauges, tankAliasProblem, type GaugeView, type TankAlias } from "./gaugeOps"
import type { CreatedKey, KeyView } from "./importOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const B = "/api/v1/fuel/gauge-imports"
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

/** Tank levels sent by a gauge system. Accepting one records an ordinary dip reading, so variance and alerts work as for a dip typed in. */
export default function GaugeImportsTab() {
  const canReview = usePermissions(["FUEL_MANAGE", "FUEL_OPERATIONS_MANAGE"])
  const canKeys = usePermissions(["FUEL_MANAGE"])
  const qc = useQueryClient()
  const [status, setStatus] = useState("PENDING")
  const [rejecting, setRejecting] = useState<GaugeView | null>(null)
  const [newKey, setNewKey] = useState<CreatedKey | null>(null)
  const [keyName, setKeyName] = useState("")
  const [source, setSource] = useState(""); const [alias, setAlias] = useState(""); const [tankId, setTankId] = useState("")
  const list = useQuery<GaugeView[]>({ queryKey: ["fuel-gauges", status], retry: false, queryFn: async () => onlyGauges(unwrap(await apiClient.get(`${B}?status=${status}`))) })
  const labels = useQuery<TankAlias[]>({ queryKey: ["fuel-gauge-aliases"], retry: false, queryFn: async () => onlyAliases(unwrap(await apiClient.get(`${B}/aliases`))) })
  const tanks = useQuery<any[]>({ queryKey: ["fuel-gauge-tanks"], retry: false, queryFn: async () => { const d = unwrap(await apiClient.get("/api/v1/fuel/tanks")); return Array.isArray(d) ? d : (d?.content ?? []) } })
  const keys = useQuery<KeyView[]>({ queryKey: ["fuel-gauge-keys"], enabled: canKeys, retry: false, queryFn: async () => { const d = unwrap(await apiClient.get(`${B}/keys`)); return Array.isArray(d) ? d : [] } })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-gauges"] }); qc.invalidateQueries({ queryKey: ["tanks"] }) }
  const accept = useMutation({ mutationFn: (id: string) => apiClient.post(`${B}/${id}/accept`, {}), onSuccess: refresh })
  const reject = useMutation({ mutationFn: (a: { id: string; reason: string }) => apiClient.post(`${B}/${a.id}/reject`, { reason: a.reason }), onSuccess: () => { setRejecting(null); refresh() } })
  const addLabel = useMutation({ mutationFn: () => apiClient.post(`${B}/aliases`, { source: source.trim(), alias: alias.trim(), tankId }), onSuccess: () => { setAlias(""); setTankId(""); qc.invalidateQueries({ queryKey: ["fuel-gauge-aliases"] }); refresh() } })
  const removeLabel = useMutation({ mutationFn: (id: string) => apiClient.delete(`${B}/aliases/${id}`), onSuccess: () => { qc.invalidateQueries({ queryKey: ["fuel-gauge-aliases"] }); refresh() } })
  const createKey = useMutation({ mutationFn: () => apiClient.post(`${B}/keys`, { name: keyName.trim() }), onSuccess: r => { setNewKey(unwrap(r)); setKeyName(""); qc.invalidateQueries({ queryKey: ["fuel-gauge-keys"] }) } })
  const revoke = useMutation({ mutationFn: (id: string) => apiClient.post(`${B}/keys/${id}/revoke`), onSuccess: () => qc.invalidateQueries({ queryKey: ["fuel-gauge-keys"] }) })
  const existing = labels.data ?? []
  const problem = tankAliasProblem(source, alias, tankId, existing)
  const rows = list.data ?? []
  const sources = Array.from(new Set(rows.map(r => r.source))).sort()

  return (
    <div>
      <h2 style={{ margin: "0 0 4px", fontSize: 17 }}>Gauge readings</h2>
      <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
        Tank levels sent by a gauge or tank-monitoring system. Nothing changes until you accept a reading; accepting records a dip reading for the tank, so variance and alerts work as for a dip you type in.
      </p>
      <div style={{ display: "flex", gap: 6, marginBottom: 10 }}>
        {GAUGE_STATUS.map(([v, l]) => <button key={v} style={{ ...btnGhost, ...(status === v ? { borderColor: "var(--hf-accent)", color: "var(--hf-text)" } : {}) }} onClick={() => setStatus(v)}>{l}</button>)}
      </div>
      <ErrorLine error={accept.error} fallback="Could not accept the reading" />
      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{["Read", "Source", "Tank", "Level", "Sender", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)" }}>{h}</th>)}</tr></thead>
          <tbody>
            {rows.map(g => (
              <tr key={g.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={{ padding: "8px 12px", fontSize: 13 }}>{when(g.readAt)}</td>
                <td style={{ padding: "8px 12px", fontSize: 13 }}>{g.source}</td>
                <td style={{ padding: "8px 12px", fontSize: 13 }}>{g.tankName ?? g.tankLabel}{!g.tankId && <div style={{ fontSize: 11.5, color: "var(--hf-danger-text)" }}>{g.problems[0] ?? "Tank not set up"}</div>}</td>
                <td style={{ padding: "8px 12px", fontSize: 13 }}>{fmtL(g.litres)}</td>
                <td style={{ padding: "8px 12px", fontSize: 13 }}>{g.senderName ?? ""}{g.status !== "PENDING" && <div><Chip bg={g.status === "ACCEPTED" ? "var(--hf-success-soft-strong)" : "var(--hf-surface-muted)"} fg={g.status === "ACCEPTED" ? "var(--hf-success-text-strong)" : "var(--hf-text-muted)"}>{g.status === "ACCEPTED" ? "Accepted" : "Rejected"}</Chip></div>}</td>
                <td style={{ padding: "8px 12px" }}>{canReview && g.status === "PENDING" && <>
                  <button style={btnPrimary} disabled={!canAccept(g) || accept.isPending} onClick={() => accept.mutate(g.id)}>Accept</button>{" "}
                  <button style={btnGhost} onClick={() => setRejecting(g)}>Reject</button></>}</td>
              </tr>))}
            {list.data && rows.length === 0 && <tr><td colSpan={6} style={{ padding: "10px 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing here.</td></tr>}
          </tbody>
        </table>
      </div>

      {canReview && (
        <div style={{ ...card, padding: 16, marginTop: 14 }} aria-label="Tank labels">
          <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Tank labels</h3>
          <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>When a system calls a tank something other than its name in Fuel, map that label here. Labels are per source; a tank with no label is matched by its name.</p>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
            <input aria-label="Source" list="gauge-sources" placeholder="Source" value={source} onChange={e => setSource(e.target.value)} maxLength={40} style={{ ...inputStyle, width: 160 }} />
            <datalist id="gauge-sources">{sources.map(s => <option key={s} value={s} />)}</datalist>
            <input aria-label="Label the sender uses" placeholder="Label, e.g. T-9" value={alias} onChange={e => setAlias(e.target.value)} maxLength={60} style={{ ...inputStyle, width: 160 }} />
            <select aria-label="Fuel tank" value={tankId} onChange={e => setTankId(e.target.value)} style={{ ...inputStyle, width: 200 }}>
              <option value="">Choose the Fuel tank…</option>
              {(tanks.data ?? []).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
            <button style={btnPrimary} disabled={!!problem || addLabel.isPending} onClick={() => addLabel.mutate()}>Add label</button>
          </div>
          {problem && (source || alias || tankId) && <p role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "0 0 6px" }}>{problem}</p>}
          <ErrorLine error={addLabel.error ?? removeLabel.error} fallback="Could not change the label" />
          {existing.map(a => (
            <div key={a.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
              <div style={{ flex: 1 }}><strong>{a.source}</strong>: <code>{a.alias}</code> is {a.tankName}</div>
              <button style={btnGhost} onClick={() => removeLabel.mutate(a.id)}>Remove</button>
            </div>))}
        </div>
      )}

      {canKeys && (
        <div style={{ ...card, padding: 16, marginTop: 14 }} aria-label="Keys for gauge systems">
          <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Keys for gauge systems</h3>
          <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
            The sender posts one level at a time to <code>/api/v1/fuel/import/tank-gauges</code> with the key in the <code>X-Fuel-Import-Key</code> header. A key is shown once, when you create it, and works for the pump and POS import too.
          </p>
          {newKey && <p role="status" style={{ fontSize: 13, wordBreak: "break-all" }}>New key for {newKey.key.name}: <code>{newKey.apiKey}</code> Copy it now; it cannot be shown again.</p>}
          <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
            <input aria-label="Key name" placeholder="Name, for example Depot gauges" value={keyName} onChange={e => setKeyName(e.target.value)} maxLength={150} style={{ ...inputStyle, minWidth: 240 }} />
            <button style={btnPrimary} disabled={!keyName.trim() || createKey.isPending} onClick={() => createKey.mutate()}>Create key</button>
          </div>
          <ErrorLine error={createKey.error} fallback="Could not create the key" />
          {(keys.data ?? []).map(k => (
            <div key={k.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
              <div style={{ flex: 1 }}><strong>{k.name}</strong> <code style={{ fontSize: 12 }}>{k.keyPrefix}…</code>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{k.revokedAt ? "Revoked" : k.lastUsedAt ? `Last used ${k.lastUsedAt.slice(0, 16).replace("T", " ")}` : "Not used yet"}</div></div>
              {!k.revokedAt && <button style={btnGhost} onClick={() => { if (confirm(`Revoke ${k.name}? The system using it will stop being accepted.`)) revoke.mutate(k.id) }}>Revoke</button>}
            </div>))}
        </div>
      )}

      {rejecting && <ReasonModal title="Reject this reading" intro={`${rejecting.tankName ?? rejecting.tankLabel} · ${fmtL(rejecting.litres)}. It stays on record as rejected.`} confirmLabel="Reject" pending={reject.isPending} error={reject.error}
        onConfirm={reason => reject.mutate({ id: rejecting.id, reason })} onClose={() => setRejecting(null)} />}
    </div>
  )
}
