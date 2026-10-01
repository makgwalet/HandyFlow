// src/pages/agriculture/AgCropScoutingTab.tsx
// Field scouting for one crop cycle: observations, severity, follow-ups. Follow-ups feed the farm's attention list
// and the daily scouting notification.
import { useState } from "react"
import { Plus } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import EmployeePicker, { type EmployeeOption } from "../training/EmployeePicker"
import { agKeys, api, useAgMutation, useScouting } from "./agCrops.api"
import { canLog, todayISO } from "./agCrops.logic"
import type { CropCycle, ScoutingRecord } from "./agCrops.types"
import { Empty, Field, Th, Warn } from "./agCropsUi"
import { btnGhost, btnPrimary, fmtDate, grid, inp, panel, statusBadge, td, theadStyle } from "./constants"

// observationType is free text on the server (VARCHAR 30); this is the vocabulary the screens offer
const OBSERVATIONS = ["PEST", "DISEASE", "WEED", "NUTRIENT_DEFICIENCY", "WATER_STRESS", "GROWTH_CHECK", "OTHER"]
const SEVERITIES = ["LOW", "MEDIUM", "HIGH"] as const

function ScoutingForm({ cycle, onDone }: { cycle: CropCycle; onDone: () => void }) {
  const [f, setF] = useState({ date: todayISO(), type: "PEST", severity: "LOW" as (typeof SEVERITIES)[number], description: "", action: "", followUp: "", notes: "" })
  const [by, setBy] = useState<EmployeeOption | null>(null)
  const followUpBeforeScouting = !!f.followUp && f.followUp < f.date
  const valid = !!f.date && !!f.description.trim() && !followUpBeforeScouting
  const create = useAgMutation(
    () => api.post(`/crop-cycles/${cycle.id}/scouting-records`, {
      scoutingDate: f.date, observationType: f.type, severity: f.severity, description: f.description.trim(),
      recommendedAction: f.action.trim() || undefined, scoutedBy: by?.id, followUpDate: f.followUp || undefined, notes: f.notes.trim() || undefined,
    }),
    { invalidate: [agKeys.scouting(cycle.id)], success: "Scouting recorded", failure: "Couldn't record the scouting." })
  return (
    <div style={panel}>
      <div style={grid}>
        <Field label="Date *" htmlFor="sc-date"><input id="sc-date" type="date" style={inp} value={f.date} onChange={e => setF({ ...f, date: e.target.value })} /></Field>
        <Field label="Observation" htmlFor="sc-type"><select id="sc-type" style={inp} value={f.type} onChange={e => setF({ ...f, type: e.target.value })}>{OBSERVATIONS.map(o => <option key={o} value={o}>{o.replace("_", " ")}</option>)}</select></Field>
        <Field label="Severity" htmlFor="sc-sev"><select id="sc-sev" style={inp} value={f.severity} onChange={e => setF({ ...f, severity: e.target.value as (typeof SEVERITIES)[number] })}>{SEVERITIES.map(s => <option key={s}>{s}</option>)}</select></Field>
        <Field label="Follow-up date" htmlFor="sc-fu"><input id="sc-fu" type="date" style={inp} value={f.followUp} onChange={e => setF({ ...f, followUp: e.target.value })} /></Field>
        <Field label="Scouted by"><EmployeePicker value={by} onChange={setBy} /></Field>
      </div>
      <div style={{ marginTop: 10 }}><Field label="What did you see? *" htmlFor="sc-desc"><textarea id="sc-desc" style={{ ...inp, minHeight: 60, resize: "vertical" }} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} /></Field></div>
      <div style={{ ...grid, marginTop: 10 }}>
        <Field label="Recommended action" htmlFor="sc-act"><input id="sc-act" style={inp} value={f.action} onChange={e => setF({ ...f, action: e.target.value })} /></Field>
        <Field label="Notes" htmlFor="sc-notes"><input id="sc-notes" style={inp} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} /></Field>
      </div>
      {followUpBeforeScouting && <Warn tone="danger">The follow-up date can't be before the scouting date.</Warn>}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button type="button" style={{ ...btnPrimary, opacity: valid && !create.isPending ? 1 : 0.5 }} disabled={!valid || create.isPending} onClick={() => create.mutate(undefined, { onSuccess: onDone })}>Record scouting</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgCropScoutingTab({ cycle }: { cycle: CropCycle }) {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const [adding, setAdding] = useState(false)
  const { data: records = [], isLoading } = useScouting(cycle.id)
  const keys = [agKeys.scouting(cycle.id)]
  const resolve = useAgMutation((id: string) => api.patch(`/scouting-records/${id}/resolve`), { invalidate: keys, success: "Marked resolved", failure: "Couldn't resolve that record." })
  const reopen = useAgMutation((id: string) => api.patch(`/scouting-records/${id}/reopen`), { invalidate: keys, success: "Reopened", failure: "Couldn't reopen that record." })
  const ack = useAgMutation((id: string) => api.patch(`/scouting-records/${id}/acknowledge-follow-up`), { invalidate: keys, success: "Follow-up acknowledged", failure: "Couldn't acknowledge the follow-up." })
  const open = records.filter(r => r.status === "OPEN").length

  const actions = (r: ScoutingRecord) => !canManage ? null : (
    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
      {r.status === "OPEN"
        ? <button type="button" style={{ ...btnGhost, padding: "4px 10px", fontSize: 11.5 }} onClick={() => resolve.mutate(r.id)}>Resolve</button>
        : <button type="button" style={{ ...btnGhost, padding: "4px 10px", fontSize: 11.5 }} onClick={() => reopen.mutate(r.id)}>Reopen</button>}
      {r.status === "OPEN" && r.followUpDate && !r.followUpAcknowledged && (
        <button type="button" style={{ ...btnGhost, padding: "4px 10px", fontSize: 11.5 }} onClick={() => ack.mutate(r.id)}>Acknowledge follow-up</button>
      )}
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
        {canManage && canLog("scouting", cycle.status) && !adding && <button type="button" style={btnPrimary} onClick={() => setAdding(true)}><Plus size={14} />Record scouting</button>}
        {records.length > 0 && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{open} open · {records.length - open} resolved</span>}
      </div>
      {adding && <ScoutingForm cycle={cycle} onDone={() => setAdding(false)} />}
      {isLoading ? <Empty>Loading scouting records…</Empty> : records.length === 0 ? <Empty>No scouting recorded for this cycle yet.</Empty> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
            <thead><tr style={theadStyle}>{["Date", "Observation", "Severity", "Details", "Follow-up", "Status", ""].map((h, i) => <Th key={i}>{h}</Th>)}</tr></thead>
            <tbody>
              {records.map(r => (
                <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={td}>{fmtDate(r.scoutingDate)}{r.scoutedByName ? <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{r.scoutedByName}</div> : null}</td>
                  <td style={td}>{r.observationType.replace("_", " ")}</td>
                  <td style={td}><span style={statusBadge(r.severity)}>{r.severity}</span></td>
                  <td style={{ ...td, maxWidth: 320 }}>{r.description}{r.recommendedAction ? <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>Action: {r.recommendedAction}</div> : null}</td>
                  <td style={td}>{r.followUpDate ? <>{fmtDate(r.followUpDate)}{r.followUpAcknowledged ? <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>acknowledged</div> : null}</> : "—"}</td>
                  <td style={td}><span style={statusBadge(r.status)}>{r.status === "OPEN" ? "Open" : "Resolved"}</span></td>
                  <td style={td}>{actions(r)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
