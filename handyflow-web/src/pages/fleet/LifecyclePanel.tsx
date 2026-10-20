// src/pages/fleet/LifecyclePanel.tsx
//
// The Lifecycle tab of the vehicle panel: where the vehicle is in its life as an asset (active, stolen, written off, sold…), the history,
// and the actions that are allowed from here. The server enforces the same rules and permissions.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  LIFECYCLE_LABEL, actionsFor, disposalBody, disposalProblem, emptyDisposal, emptyRecovery, emptyTheft, emptyWriteOff, gainLossText,
  recoveryBody, recoveryProblem, theftBody, theftProblem, writeOffBody, writeOffProblem, writeOffSummary, type ActionOption, type TheftData, type WriteOffData,
} from "./lifecycle.logic"
import { EventEvidence, TheftEdit, WriteOffEdit, type Evidence } from "./LifecycleEdits"

interface Event { id: string; eventType: string; fromStatus: string | null; toStatus: string | null; occurredAt: string; reason: string | null; disposal: { gainLoss: number | null; salePrice: number | null; counterpartyName: string | null } | null; theft: TheftData | null; writeOff: WriteOffData | null; evidence: Evidence[] | null }
interface Lifecycle { lifecycleStatus: string; operationalStatus: string; events: Event[] }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5 }
const btn: React.CSSProperties = { ...field, cursor: "pointer", fontWeight: 700 }
const EVENT_LABEL: Record<string, string> = { STATUS_CHANGE: "Status change", THEFT_REPORTED: "Theft reported", RECOVERED: "Recovered", WRITE_OFF: "Written off", DISPOSAL: "Disposal" }

function ActionForm({ vehicleId, option, onDone, onCancel }: { vehicleId: string; option: ActionOption; onDone: () => void; onCancel: () => void }) {
  const [theft, setTheft] = useState(emptyTheft()), [rec, setRec] = useState(emptyRecovery()), [wo, setWo] = useState(emptyWriteOff()), [dp, setDp] = useState(emptyDisposal(option.target ?? "SOLD"))
  const [reason, setReason] = useState("")
  const [error, setError] = useState<string | null>(null)
  const base = `/api/v1/fleet/vehicles/${vehicleId}/lifecycle`
  const problem = option.action === "THEFT" ? theftProblem(theft) : option.action === "RECOVERY" ? recoveryProblem(rec) : option.action === "WRITE_OFF" ? writeOffProblem(wo) : option.action === "DISPOSAL" ? disposalProblem(dp) : null
  const send = useMutation({
    mutationFn: () => {
      switch (option.action) {
        case "THEFT": return apiClient.post(`${base}/theft`, theftBody(theft))
        case "RECOVERY": return apiClient.post(`${base}/recovery`, recoveryBody(rec))
        case "WRITE_OFF": return apiClient.post(`${base}/write-off`, writeOffBody(wo))
        case "DISPOSAL": return apiClient.post(`${base}/disposal`, disposalBody(dp))
        default: return apiClient.post(`${base}/transition`, { target: option.target, reason: reason.trim() || null })
      }
    },
    onSuccess: onDone, onError: e => setError(errorOf(e)),
  })
  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, margin: "10px 0" }}>
      <strong>{option.label}</strong>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
        {option.action === "TRANSITION" && <input aria-label="Reason" placeholder="Reason" style={{ ...field, minWidth: 260 }} value={reason} onChange={e => setReason(e.target.value)} />}
        {option.action === "THEFT" && <>
          <select aria-label="Stolen or missing" style={field} value={theft.kind} onChange={e => setTheft({ ...theft, kind: e.target.value })}><option value="STOLEN">Stolen</option><option value="MISSING">Missing</option></select>
          <input aria-label="When it was stolen or last seen" type="datetime-local" style={field} value={theft.occurredAt} onChange={e => setTheft({ ...theft, occurredAt: e.target.value })} />
          <input aria-label="Location" placeholder="Where" style={field} value={theft.location} onChange={e => setTheft({ ...theft, location: e.target.value })} />
          <input aria-label="Police case number" placeholder="SAPS case number" style={field} value={theft.policeCaseNumber} onChange={e => setTheft({ ...theft, policeCaseNumber: e.target.value })} />
          <input aria-label="SAPS station" placeholder="SAPS station" style={field} value={theft.sapsStation} onChange={e => setTheft({ ...theft, sapsStation: e.target.value })} />
          <input aria-label="Insurer" placeholder="Insurer" style={field} value={theft.insurer} onChange={e => setTheft({ ...theft, insurer: e.target.value })} />
          <input aria-label="Claim number" placeholder="Claim number" style={field} value={theft.claimNumber} onChange={e => setTheft({ ...theft, claimNumber: e.target.value })} /></>}
        {option.action === "RECOVERY" && <>
          <select aria-label="Condition" style={field} value={rec.recoveryStatus} onChange={e => setRec({ ...rec, recoveryStatus: e.target.value })}><option value="RECOVERED">Recovered</option><option value="RECOVERED_DAMAGED">Recovered damaged</option></select>
          <input aria-label="When it was recovered" type="datetime-local" style={field} value={rec.recoveredAt} onChange={e => setRec({ ...rec, recoveredAt: e.target.value })} />
          <input aria-label="Location" placeholder="Where" style={field} value={rec.location} onChange={e => setRec({ ...rec, location: e.target.value })} />
          <input aria-label="Odometer" type="number" min="0" placeholder="Odometer (km)" style={{ ...field, width: 130 }} value={rec.odometer} onChange={e => setRec({ ...rec, odometer: e.target.value })} /></>}
        {option.action === "WRITE_OFF" && <>
          <input aria-label="Decision date" type="date" style={field} value={wo.decisionDate} onChange={e => setWo({ ...wo, decisionDate: e.target.value })} />
          <input aria-label="Assessor" placeholder="Assessor" style={field} value={wo.assessorName} onChange={e => setWo({ ...wo, assessorName: e.target.value })} />
          <input aria-label="Salvage value" type="number" min="0" placeholder="Salvage value (R)" style={field} value={wo.salvageValue} onChange={e => setWo({ ...wo, salvageValue: e.target.value })} />
          <input aria-label="Insurance settlement" type="number" min="0" placeholder="Insurance settlement (R)" style={field} value={wo.insuranceSettlementAmount} onChange={e => setWo({ ...wo, insuranceSettlementAmount: e.target.value })} /></>}
        {option.action === "DISPOSAL" && <>
          <input aria-label="Date" type="date" style={field} value={dp.disposalDate} onChange={e => setDp({ ...dp, disposalDate: e.target.value })} />
          <input aria-label={dp.target === "SOLD" ? "Buyer" : "Counterparty"} placeholder={dp.target === "SOLD" ? "Buyer" : "Transferred to / scrapper"} style={field} value={dp.counterpartyName} onChange={e => setDp({ ...dp, counterpartyName: e.target.value })} />
          <input aria-label="Sale price" type="number" min="0" placeholder="Sale price (R)" style={field} value={dp.salePrice} onChange={e => setDp({ ...dp, salePrice: e.target.value })} />
          <input aria-label="Odometer" type="number" min="0" placeholder="Odometer (km)" style={{ ...field, width: 130 }} value={dp.odometerAtDisposal} onChange={e => setDp({ ...dp, odometerAtDisposal: e.target.value })} />
          <input aria-label="Accounting reference" placeholder="Accounting reference" style={field} value={dp.accountingReference} onChange={e => setDp({ ...dp, accountingReference: e.target.value })} />
          <label style={{ fontSize: 12.5 }}><input type="checkbox" aria-label="Finance cleared" checked={dp.financeCleared} onChange={e => setDp({ ...dp, financeCleared: e.target.checked })} /> Finance cleared</label>
          <label style={{ fontSize: 12.5 }}><input type="checkbox" aria-label="Registration transferred" checked={dp.registrationTransferred} onChange={e => setDp({ ...dp, registrationTransferred: e.target.checked })} /> Registration transferred</label></>}
      </div>
      {problem && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>{problem}</div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
      <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
        <button type="button" style={{ ...btn, opacity: problem || send.isPending ? 0.5 : 1 }} disabled={!!problem || send.isPending} onClick={() => send.mutate()}>Confirm</button>
        <button type="button" style={btn} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

export default function LifecyclePanel({ vehicleId }: { vehicleId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE"), canAdmin = usePermission("FLEET_ADMIN")
  const [chosen, setChosen] = useState<ActionOption | null>(null)
  const [editing, setEditing] = useState<string | null>(null)
  const { data, isLoading } = useQuery<Lifecycle>({ queryKey: ["fleet", "lifecycle", vehicleId], queryFn: async () => unwrap<Lifecycle>(await apiClient.get(`/api/v1/fleet/vehicles/${vehicleId}/lifecycle`)) })
  if (isLoading || !data) return <p>Loading…</p>
  const options = actionsFor(data.lifecycleStatus).filter(o => (o.adminOnly ? canAdmin : canManage))
  const done = () => { setChosen(null); setEditing(null); for (const k of ["lifecycle", "compliance"]) qc.invalidateQueries({ queryKey: ["fleet", k, vehicleId] }); qc.invalidateQueries({ queryKey: ["fleet-vehicles"] }) }
  const status = data.lifecycleStatus
  const openTheftId = status === "STOLEN" || status === "MISSING" ? data.events.find(e => e.eventType === "THEFT_REPORTED" && e.theft && !e.theft.recoveryStatus)?.id : undefined
  const writeOffId = status === "WRITTEN_OFF" ? data.events.find(e => e.eventType === "WRITE_OFF" && e.writeOff)?.id : undefined
  const link: React.CSSProperties = { ...field, cursor: "pointer", marginTop: 4 }
  return (
    <div>
      <div style={{ fontSize: 13, marginBottom: 8 }}>Lifecycle: <strong>{LIFECYCLE_LABEL[data.lifecycleStatus] ?? data.lifecycleStatus}</strong> · Operational: <strong>{data.operationalStatus}</strong></div>
      {options.length > 0 && !chosen && <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>{options.map(o => <button key={o.label} type="button" style={btn} onClick={() => setChosen(o)}>{o.label}</button>)}</div>}
      {chosen && <ActionForm key={chosen.label} vehicleId={vehicleId} option={chosen} onDone={done} onCancel={() => setChosen(null)} />}
      <h4 style={{ margin: "14px 0 4px" }}>History</h4>
      {data.events.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No lifecycle events yet.</p>}
      <ul style={{ listStyle: "none", padding: 0, margin: 0, fontSize: 13 }}>{data.events.map(e => (
        <li key={e.id} style={{ padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
          <strong>{EVENT_LABEL[e.eventType] ?? e.eventType}</strong>{e.toStatus ? `: ${e.fromStatus ? `${LIFECYCLE_LABEL[e.fromStatus] ?? e.fromStatus} → ` : ""}${LIFECYCLE_LABEL[e.toStatus] ?? e.toStatus}` : ""} · {new Date(e.occurredAt).toLocaleDateString("en-ZA")}
          {e.reason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{e.reason}</div>}
          {e.disposal && <div style={{ fontSize: 12 }}>{e.disposal.counterpartyName ?? ""} {e.disposal.salePrice != null ? `· R ${Number(e.disposal.salePrice).toLocaleString("en-ZA")}` : ""} · {gainLossText(e.disposal.gainLoss)}</div>}
          {e.theft && <div style={{ fontSize: 12 }}>{e.theft.kind === "MISSING" ? "Missing" : "Stolen"}{e.theft.location ? ` · ${e.theft.location}` : ""}{e.theft.policeCaseNumber ? ` · SAPS ${e.theft.policeCaseNumber}${e.theft.sapsStation ? ` (${e.theft.sapsStation})` : ""}` : ""}{e.theft.insurer ? ` · ${e.theft.insurer}${e.theft.claimNumber ? ` claim ${e.theft.claimNumber}` : ""}` : ""}</div>}
          {e.writeOff && <div style={{ fontSize: 12 }}>{writeOffSummary(e.writeOff)}{e.writeOff.assessorName ? ` · assessor ${e.writeOff.assessorName}` : ""}</div>}
          {e.theft && e.id === openTheftId && canManage && editing !== e.id && <button type="button" style={link} onClick={() => setEditing(e.id)}>Edit theft report</button>}
          {e.writeOff && e.id === writeOffId && canAdmin && editing !== e.id && <button type="button" style={link} onClick={() => setEditing(e.id)}>Edit write-off</button>}
          {editing === e.id && e.theft && <TheftEdit vehicleId={vehicleId} theft={e.theft} onDone={done} />}
          {editing === e.id && e.writeOff && <WriteOffEdit vehicleId={vehicleId} writeOff={e.writeOff} onDone={done} />}
          <EventEvidence vehicleId={vehicleId} eventId={e.id} eventType={e.eventType} evidence={e.evidence ?? []} />
        </li>))}</ul>
    </div>
  )
}
