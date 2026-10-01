// src/pages/agriculture/AgCropInputsTab.tsx
// Inputs applied to one crop cycle (fertiliser, chemicals, ...). Logging from stock also issues the stock.
import { useState } from "react"
import { Plus } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import EmployeePicker, { type EmployeeOption } from "../training/EmployeePicker"
import { agKeys, api, useAgMutation, useInputs, useInventory } from "./agCrops.api"
import { canLog, stockProblem, suggestInputCost, todayISO } from "./agCrops.logic"
import type { CropCycle } from "./agCrops.types"
import { Empty, Field, Th, Warn } from "./agCropsUi"
import { btnGhost, btnPrimary, fmtDate, fmtMoney, grid, inp, panel, td, theadStyle } from "./constants"

const INPUT_TYPES = ["FERTILISER", "HERBICIDE", "PESTICIDE", "FUNGICIDE", "IRRIGATION", "OTHER"]
const STOCK_CATEGORIES = ["FERTILISER", "CHEMICAL", "SEED", "OTHER"]

function InputForm({ cycle, onDone }: { cycle: CropCycle; onDone: () => void }) {
  const items = (useInventory(cycle.farmId).data ?? []).filter(i => i.status === "ACTIVE" && STOCK_CATEGORIES.includes(i.category))
  const [f, setF] = useState({ date: todayISO(), type: "FERTILISER", itemId: "", qty: "", unit: "", product: "", method: "", hours: "", cost: "", weather: "", notes: "" })
  const [costTouched, setCostTouched] = useState(false)
  const [by, setBy] = useState<EmployeeOption | null>(null)
  const item = items.find(i => i.id === f.itemId)
  const qty = Number(f.qty)

  // prefill the cost from stock unless the user has typed their own: the server stores exactly what we send
  const update = (patch: Partial<typeof f>, itemOverride = item) => {
    const next = { ...f, ...patch }
    if (!costTouched) { const s = suggestInputCost(Number(next.qty), itemOverride?.unitCost); next.cost = s == null ? "" : String(s) }
    setF(next)
  }
  const pickItem = (id: string) => {
    const it = items.find(i => i.id === id)
    update({ itemId: id, unit: it?.unitOfMeasure ?? f.unit, product: it?.itemName ?? f.product }, it)
  }

  const problem = stockProblem(qty, item)
  const valid = qty > 0 && !!f.unit.trim() && !!f.date && !problem
  const create = useAgMutation(
    () => api.post(`/crop-cycles/${cycle.id}/input-applications`, {
      applicationDate: f.date, inputType: f.type, inventoryItemId: f.itemId || undefined, productUsed: f.product.trim() || undefined,
      quantityApplied: qty, unitOfMeasure: f.unit.trim(), applicationMethod: f.method.trim() || undefined, appliedBy: by?.id,
      laborHours: f.hours ? Number(f.hours) : undefined, cost: f.cost !== "" ? Number(f.cost) : undefined,
      weatherConditions: f.weather.trim() || undefined, notes: f.notes.trim() || undefined,
    }),
    { invalidate: [agKeys.inputs(cycle.id), agKeys.cycleCost(cycle.id), agKeys.inventory(cycle.farmId), ["ag", "costs", "crops", cycle.farmId]], success: "Input recorded", failure: "Couldn't record the input." })

  return (
    <div style={panel}>
      <div style={grid}>
        <Field label="Date *" htmlFor="in-date"><input id="in-date" type="date" style={inp} value={f.date} onChange={e => update({ date: e.target.value })} /></Field>
        <Field label="Type *" htmlFor="in-type"><select id="in-type" style={inp} value={f.type} onChange={e => update({ type: e.target.value })}>{INPUT_TYPES.map(t => <option key={t}>{t}</option>)}</select></Field>
        <Field label="From stock" htmlFor="in-item">
          <select id="in-item" style={inp} value={f.itemId} onChange={e => pickItem(e.target.value)}>
            <option value="">Not from stock</option>{items.map(i => <option key={i.id} value={i.id}>{i.itemName} ({i.currentQuantity} {i.unitOfMeasure})</option>)}
          </select></Field>
        <Field label="Quantity *" htmlFor="in-qty"><input id="in-qty" type="number" min="0" step="any" style={inp} value={f.qty} onChange={e => update({ qty: e.target.value })} /></Field>
        <Field label="Unit *" htmlFor="in-unit"><input id="in-unit" style={inp} value={f.unit} onChange={e => update({ unit: e.target.value })} placeholder="kg, l, bags" /></Field>
        <Field label="Product used" htmlFor="in-product"><input id="in-product" style={inp} value={f.product} onChange={e => update({ product: e.target.value })} /></Field>
        <Field label="Method" htmlFor="in-method"><input id="in-method" style={inp} value={f.method} onChange={e => update({ method: e.target.value })} placeholder="Broadcast, sprayer…" /></Field>
        <Field label="Cost (R)" htmlFor="in-cost"><input id="in-cost" type="number" min="0" step="0.01" style={inp} value={f.cost} onChange={e => { setCostTouched(true); setF({ ...f, cost: e.target.value }) }} /></Field>
        <Field label="Labour hours" htmlFor="in-hours"><input id="in-hours" type="number" min="0" step="0.25" style={inp} value={f.hours} onChange={e => update({ hours: e.target.value })} /></Field>
        <Field label="Applied by"><EmployeePicker value={by} onChange={setBy} /></Field>
        <Field label="Weather" htmlFor="in-weather"><input id="in-weather" style={inp} value={f.weather} onChange={e => update({ weather: e.target.value })} placeholder="e.g. calm, 22°C" /></Field>
        <Field label="Notes" htmlFor="in-notes"><input id="in-notes" style={inp} value={f.notes} onChange={e => update({ notes: e.target.value })} /></Field>
      </div>
      {problem && <Warn tone="danger">{problem}</Warn>}
      {f.cost === "" && <Warn>Without a cost, this input won't count towards the cycle's cost per hectare.</Warn>}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button type="button" style={{ ...btnPrimary, opacity: valid && !create.isPending ? 1 : 0.5 }} disabled={!valid || create.isPending} onClick={() => create.mutate(undefined, { onSuccess: onDone })}>Record input</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgCropInputsTab({ cycle }: { cycle: CropCycle }) {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const [adding, setAdding] = useState(false)
  const { data: inputs = [], isLoading } = useInputs(cycle.id)
  const total = inputs.reduce((s, i) => s + (i.cost ?? 0), 0)
  return (
    <div>
      {canManage && canLog("input", cycle.status) && !adding && (
        <div style={{ marginBottom: 12 }}><button type="button" style={btnPrimary} onClick={() => setAdding(true)}><Plus size={14} />Record input</button></div>
      )}
      {adding && <InputForm cycle={cycle} onDone={() => setAdding(false)} />}
      {isLoading ? <Empty>Loading inputs…</Empty> : inputs.length === 0 ? <Empty>No inputs recorded for this cycle yet.</Empty> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
            <thead><tr style={theadStyle}>{["Date", "Type", "Product", "Quantity", "Applied by", "Labour h", "Cost"].map(h => <Th key={h}>{h}</Th>)}</tr></thead>
            <tbody>
              {inputs.map(i => (
                <tr key={i.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={td}>{fmtDate(i.applicationDate)}</td><td style={td}>{i.inputType}</td>
                  <td style={td}>{i.productUsed ?? "—"}{i.applicationMethod ? <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{i.applicationMethod}</div> : null}</td>
                  <td style={td}>{i.quantityApplied} {i.unitOfMeasure}</td><td style={td}>{i.appliedByName ?? "—"}</td>
                  <td style={td}>{i.laborHours ?? "—"}</td><td style={td}>{fmtMoney(i.cost)}</td>
                </tr>
              ))}
              <tr style={{ borderTop: "2px solid var(--hf-border)", fontWeight: 700 }}><td style={td} colSpan={6}>Total recorded cost</td><td style={td}>{fmtMoney(total)}</td></tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
