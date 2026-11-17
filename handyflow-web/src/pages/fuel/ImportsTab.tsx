import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import AutoAcceptSettings from "./AutoAcceptSettings"
import ShiftControls from "./ShiftControls"
import AutoAcceptLog from "./AutoAcceptLog"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, ReasonModal, inputStyle } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import PumpLabels from "./PumpLabels"
import { STATUS_FILTERS, statusLabel, totalLitres, totalTendered, type CreatedKey, type ImportView, type KeyView } from "./importOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const B = "/api/v1/fuel/forecourt/imports"

export default function ImportsTab() {
  const canReview = usePermissions(["FUEL_SHIFT_SUPERVISE", "FUEL_FORECOURT_MANAGE"])
  const canConfigure = usePermissions(["FUEL_CONFIGURATION_MANAGE"])
  const canKeys = usePermissions(["FUEL_MANAGE", "FUEL_FORECOURT_MANAGE"])
  const qc = useQueryClient()
  const [status, setStatus] = useState("PENDING")
  const [open, setOpen] = useState<string | null>(null)
  const [rejecting, setRejecting] = useState<ImportView | null>(null)
  const [variance, setVariance] = useState("")
  const [newKey, setNewKey] = useState<CreatedKey | null>(null)
  const [keyName, setKeyName] = useState("")
  const list = useQuery<ImportView[]>({ queryKey: ["fuel-imports", status], queryFn: async () => unwrap(await apiClient.get(`${B}?status=${status}`)) ?? [] })
  const keys = useQuery<KeyView[]>({ queryKey: ["fuel-import-keys"], enabled: canKeys, queryFn: async () => unwrap(await apiClient.get(`${B}/keys`)) ?? [] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-imports"] }); qc.invalidateQueries({ queryKey: ["fuel-shifts"] }) }
  const accept = useMutation({ mutationFn: (id: string) => apiClient.post(`${B}/${id}/accept`, { varianceReason: variance.trim() || null }), onSuccess: () => { setVariance(""); refresh() } })
  const reject = useMutation({ mutationFn: (a: { id: string; reason: string }) => apiClient.post(`${B}/${a.id}/reject`, { reason: a.reason }), onSuccess: () => { setRejecting(null); refresh() } })
  const create = useMutation({ mutationFn: () => apiClient.post(`${B}/keys`, { name: keyName.trim() }), onSuccess: r => { setNewKey(unwrap(r)); setKeyName(""); qc.invalidateQueries({ queryKey: ["fuel-import-keys"] }) } })
  const revoke = useMutation({ mutationFn: (id: string) => apiClient.post(`${B}/keys/${id}/revoke`), onSuccess: () => qc.invalidateQueries({ queryKey: ["fuel-import-keys"] }) })

  return (
    <div>
      <div style={{ ...card, padding: 16 }}>
        <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Imported shifts</h3>
        <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
          Shifts sent by a pump or POS system wait here. Nothing changes your stock or takings until you accept one, and accepting applies the same checks as typing the shift in yourself.
        </p>
        <select aria-label="Show" value={status} onChange={e => setStatus(e.target.value)} style={{ ...inputStyle, width: "auto", marginBottom: 10 }}>
          {STATUS_FILTERS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <ErrorLine error={accept.error} fallback="Could not accept this shift" />
        {list.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : (list.data ?? []).length === 0 ? <p style={{ fontSize: 13 }}>Nothing here.</p> : (list.data ?? []).map(i => (
          <div key={i.id} style={{ borderTop: "1px solid var(--hf-border)", padding: "10px 0" }}>
            <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <div style={{ flex: 1, minWidth: 220 }}>
                <strong style={{ fontSize: 13 }}>{i.shift.attendantName}</strong> <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{i.source} · {i.externalId}</span>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{fmtL(totalLitres(i.shift))} · taken {fmtR(totalTendered(i.shift))} · received {i.receivedAt.slice(0, 16).replace("T", " ")}</div>
              </div>
              <Chip bg="var(--hf-surface-2)" fg="var(--hf-text-muted)">{statusLabel(i.status)}</Chip>
              {i.problems.length > 0 && <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">{i.problems.length} to fix</Chip>}
              <button style={btnGhost} onClick={() => setOpen(open === i.id ? null : i.id)} aria-label={`Details for ${i.externalId}`}>{open === i.id ? "Hide" : "Details"}</button>
            </div>
            {open === i.id && (
              <div style={{ margin: "8px 0 0 4px", fontSize: 12.5 }}>
                {i.problems.map(p => <p key={p} role="status" style={{ margin: "0 0 4px", color: "var(--hf-warning-text-strong)" }}>{p}</p>)}
                <table style={{ borderCollapse: "collapse", fontSize: 12.5 }}>
                  <thead><tr>{["Pump", "Opening", "Closing", "Test", "Litres"].map(h => <th key={h} style={{ textAlign: "left", padding: "2px 12px 2px 0", color: "var(--hf-text-muted)" }}>{h}</th>)}</tr></thead>
                  <tbody>{i.shift.readings.map(r => <tr key={r.pumpName}><td style={{ padding: "2px 12px 2px 0" }}>{r.pumpName}</td><td>{r.openingMeter}</td><td>{r.closingMeter}</td><td>{r.testLitres ?? 0}</td><td>{r.closingMeter - r.openingMeter - (r.testLitres ?? 0)}</td></tr>)}</tbody>
                </table>
                <p style={{ margin: "6px 0 0" }}>Cash {fmtR(i.shift.cash)} · Card {fmtR(i.shift.card)} · Account {fmtR(i.shift.account)} · Other {fmtR(i.shift.other)}{i.shift.fleetCard != null ? ` · Fleet card ${fmtR(i.shift.fleetCard)}` : ""}</p>
                {i.decisionNote && <p style={{ margin: "6px 0 0", color: "var(--hf-text-muted)" }}>{i.decisionNote}</p>}
                {i.status === "PENDING" && canReview && (
                  <div style={{ marginTop: 8, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                    <input aria-label="Reason for any variance" placeholder="Reason for any variance (if needed)" value={variance} onChange={e => setVariance(e.target.value)} maxLength={500} style={{ ...inputStyle, minWidth: 260 }} />
                    <button style={btnPrimary} disabled={accept.isPending} onClick={() => accept.mutate(i.id)}>Accept shift</button>
                    <button style={btnGhost} onClick={() => setRejecting(i)}>Reject</button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {canKeys && (
        <div style={{ ...card, padding: 16, marginTop: 14 }}>
          <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Keys for pump and POS systems</h3>
          <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
            Give one key to each system that sends shifts. The sender posts to <code>/api/v1/fuel/import/pos-shifts</code> with the key in the <code>X-Fuel-Import-Key</code> header. A key is shown once, when you create it.
          </p>
          {newKey && <p role="status" style={{ fontSize: 13, wordBreak: "break-all" }}>New key for {newKey.key.name}: <code>{newKey.apiKey}</code> Copy it now; it cannot be shown again.</p>}
          <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
            <input aria-label="Key name" placeholder="Name, for example Site 2 POS" value={keyName} onChange={e => setKeyName(e.target.value)} maxLength={150} style={{ ...inputStyle, minWidth: 240 }} />
            <button style={btnPrimary} disabled={!keyName.trim() || create.isPending} onClick={() => create.mutate()}>Create key</button>
          </div>
          <ErrorLine error={create.error} fallback="Could not create the key" />
          {(keys.data ?? []).map(k => (
            <div key={k.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
              <div style={{ flex: 1 }}><strong>{k.name}</strong> <code style={{ fontSize: 12 }}>{k.keyPrefix}…</code>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{k.revokedAt ? "Revoked" : k.lastUsedAt ? `Last used ${k.lastUsedAt.slice(0, 16).replace("T", " ")}` : "Not used yet"}</div></div>
              {!k.revokedAt && <button style={btnGhost} onClick={() => { if (confirm(`Revoke ${k.name}? The system using it will stop being accepted.`)) revoke.mutate(k.id) }}>Revoke</button>}
            </div>
          ))}
        </div>
      )}

      {canKeys && <PumpLabels seenSources={list.data ?? []} />}

      {canConfigure && <AutoAcceptSettings />}
      {canConfigure && <ShiftControls />}
      {canConfigure && <AutoAcceptLog />}

      {rejecting && <ReasonModal title="Reject this shift" intro={`${rejecting.shift.attendantName} · ${rejecting.externalId}. It stays on record as rejected.`} confirmLabel="Reject" pending={reject.isPending} error={reject.error}
        onConfirm={reason => reject.mutate({ id: rejecting.id, reason })} onClose={() => setRejecting(null)} />}
    </div>
  )
}
