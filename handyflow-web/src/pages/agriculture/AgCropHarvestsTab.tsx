// src/pages/agriculture/AgCropHarvestsTab.tsx
// Harvest records for one crop cycle. Yield is summed across records and labelled with the crop type's default unit,
// so this tab defaults the unit and warns when entries would mix units.
import { useState } from "react"
import { Plus } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import EmployeePicker, { type EmployeeOption } from "../training/EmployeePicker"
import { agKeys, api, useAgMutation, useCropTypes, useHarvests } from "./agCrops.api"
import { canLog, distinctUnits, harvestUnitWarning, round, todayISO } from "./agCrops.logic"
import type { CropCycle } from "./agCrops.types"
import { Empty, Field, Th, Warn } from "./agCropsUi"
import { btnGhost, btnPrimary, fmtDate, grid, inp, panel, td, theadStyle } from "./constants"

const GRADES = ["A", "B", "C", "Reject"]

function HarvestForm({ cycle, defaultUnit, onDone }: { cycle: CropCycle; defaultUnit: string | null; onDone: () => void }) {
  const existing = useHarvests(cycle.id).data ?? []
  const [f, setF] = useState({ date: todayISO(), qty: "", unit: defaultUnit ?? existing[0]?.unitOfMeasure ?? "kg", grade: "", moisture: "", storage: "", hours: "", notes: "" })
  const [by, setBy] = useState<EmployeeOption | null>(null)
  const warning = harvestUnitWarning(f.unit, defaultUnit, existing)
  const valid = !!f.date && Number(f.qty) > 0 && !!f.unit.trim()
  const create = useAgMutation(
    () => api.post(`/crop-cycles/${cycle.id}/harvest-records`, {
      harvestDate: f.date, quantityHarvested: Number(f.qty), unitOfMeasure: f.unit.trim(), qualityGrade: f.grade.trim() || undefined,
      moistureContent: f.moisture ? Number(f.moisture) : undefined, storageLocation: f.storage.trim() || undefined,
      harvestedBy: by?.id, laborHours: f.hours ? Number(f.hours) : undefined, notes: f.notes.trim() || undefined,
    }),
    { invalidate: [agKeys.harvests(cycle.id), agKeys.cycleCost(cycle.id), ["ag", "costs", "crops", cycle.farmId]], success: "Harvest recorded", failure: "Couldn't record the harvest." })
  return (
    <div style={panel}>
      <div style={grid}>
        <Field label="Date *" htmlFor="hv-date"><input id="hv-date" type="date" style={inp} value={f.date} onChange={e => setF({ ...f, date: e.target.value })} /></Field>
        <Field label="Quantity *" htmlFor="hv-qty"><input id="hv-qty" type="number" min="0" step="any" style={inp} value={f.qty} onChange={e => setF({ ...f, qty: e.target.value })} /></Field>
        <Field label="Unit *" htmlFor="hv-unit"><input id="hv-unit" style={inp} value={f.unit} onChange={e => setF({ ...f, unit: e.target.value })} /></Field>
        <Field label="Quality grade" htmlFor="hv-grade"><input id="hv-grade" list="hv-grades" style={inp} value={f.grade} onChange={e => setF({ ...f, grade: e.target.value })} /><datalist id="hv-grades">{GRADES.map(g => <option key={g} value={g} />)}</datalist></Field>
        <Field label="Moisture (%)" htmlFor="hv-moist"><input id="hv-moist" type="number" min="0" max="100" step="0.1" style={inp} value={f.moisture} onChange={e => setF({ ...f, moisture: e.target.value })} /></Field>
        <Field label="Storage location" htmlFor="hv-store"><input id="hv-store" style={inp} value={f.storage} onChange={e => setF({ ...f, storage: e.target.value })} /></Field>
        <Field label="Labour hours" htmlFor="hv-hours"><input id="hv-hours" type="number" min="0" step="0.25" style={inp} value={f.hours} onChange={e => setF({ ...f, hours: e.target.value })} /></Field>
        <Field label="Harvested by"><EmployeePicker value={by} onChange={setBy} /></Field>
        <Field label="Notes" htmlFor="hv-notes"><input id="hv-notes" style={inp} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} /></Field>
      </div>
      {warning && <Warn>{warning}</Warn>}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button type="button" style={{ ...btnPrimary, opacity: valid && !create.isPending ? 1 : 0.5 }} disabled={!valid || create.isPending} onClick={() => create.mutate(undefined, { onSuccess: onDone })}>Record harvest</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgCropHarvestsTab({ cycle }: { cycle: CropCycle }) {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const [adding, setAdding] = useState(false)
  const { data: records = [], isLoading } = useHarvests(cycle.id)
  const defaultUnit = (useCropTypes().data ?? []).find(t => t.id === cycle.cropTypeId)?.defaultUnitOfMeasure ?? null
  const units = distinctUnits(records)
  const total = round(records.reduce((s, r) => s + r.quantityHarvested, 0), 3)
  return (
    <div>
      {canManage && canLog("harvest", cycle.status) && !adding && <div style={{ marginBottom: 12 }}><button type="button" style={btnPrimary} onClick={() => setAdding(true)}><Plus size={14} />Record harvest</button></div>}
      {adding && <HarvestForm cycle={cycle} defaultUnit={defaultUnit} onDone={() => setAdding(false)} />}
      {isLoading ? <Empty>Loading harvests…</Empty> : records.length === 0 ? <Empty>{cycle.status === "PLANNED" ? "Harvests can be recorded once the crop is planted." : "No harvest recorded for this cycle yet."}</Empty> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
            <thead><tr style={theadStyle}>{["Date", "Quantity", "Grade", "Moisture", "Storage", "Harvested by", "Labour h"].map(h => <Th key={h}>{h}</Th>)}</tr></thead>
            <tbody>
              {records.map(r => (
                <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={td}>{fmtDate(r.harvestDate)}</td><td style={td}>{r.quantityHarvested} {r.unitOfMeasure}</td><td style={td}>{r.qualityGrade ?? "—"}</td>
                  <td style={td}>{r.moistureContent != null ? `${r.moistureContent}%` : "—"}</td><td style={td}>{r.storageLocation ?? "—"}</td>
                  <td style={td}>{r.harvestedByName ?? "—"}</td><td style={td}>{r.laborHours ?? "—"}</td>
                </tr>
              ))}
              <tr style={{ borderTop: "2px solid var(--hf-border)", fontWeight: 700 }}>
                <td style={td}>Total</td><td style={td} colSpan={6}>{units.length === 1 ? `${total} ${units[0]}` : `${total} (mixed units: ${units.join(", ")})`}</td>
              </tr>
            </tbody>
          </table>
          {units.length > 1 && <Warn>These records use different units, so the yield figures in the cost report will be wrong until they match.</Warn>}
        </div>
      )}
    </div>
  )
}
