// src/pages/agriculture/AgEquipmentFuelTab.tsx
//
// Costs machine use and allocates own fuel into the cost ledger (ADR-001, W4).
//   Machine use: hours x the machine's operating rate from Fleet (service and repairs ONLY), split across what it worked on.
//   Own fuel: a fuel dispatch to the tenant's own vehicle, costed at the tank's cost per litre at the time, split across targets.
// Fuel is never in the hourly rate, so neither is counted twice. Both rates are snapshotted into the ledger. Needs AGRICULTURE_FINANCE; each
// panel also needs the owning module's permission (FLEET_READ for machines, FUEL_MARGIN_READ for fuel costs).
import { useState } from "react"
import { Lock } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import AgAllocationLines from "./AgAllocationLines"
import { addDays, todayISO } from "./agCrops.logic"
import { useAllocateFuel, useCostEquipment, useEquipment, useUnallocatedFuel, type FuelDispatchRow } from "./agEquipmentFuel.api"
import { equipmentCost, litresText, validateEquipmentUse, validateFuelAllocation } from "./agEquipmentFuel.logic"
import { splitPreview, type ShareInput } from "./agLedger.logic"
import { useTargetOptions, type TargetOption } from "./agTargets"
import { Empty, Field, Th, Warn } from "./agCropsUi"
import { btnGhost, btnPrimary, card, fmtDate, fmtMoney, grid, inp, panel } from "./constants"

const blankShares = (): ShareInput[] => [{ targetType: "", targetId: "", percentage: "100" }]
const pct = (s: string) => Number(s.replace(",", "."))

function Preview({ amount, shares, options }: { amount: number; shares: ShareInput[]; options: TargetOption[] }) {
  const parts = splitPreview(amount, shares.map(s => pct(s.percentage)))
  if (!parts) return null
  const label = (s: ShareInput) => options.find(o => o.type === s.targetType && o.id === s.targetId)?.label ?? "—"
  return (
    <div style={{ marginTop: 12, fontSize: 12.5 }} aria-label="Allocation preview">
      <div style={{ fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 4 }}>This will be saved as</div>
      {shares.map((s, i) => <div key={i} style={{ display: "flex", justifyContent: "space-between", maxWidth: 420 }}><span>{label(s)} ({s.percentage}%)</span><strong>{fmtMoney(parts[i])}</strong></div>)}
    </div>
  )
}

function MachineUse({ farmId, options }: { farmId: string; options: TargetOption[] }) {
  const machines = useEquipment()
  const cost = useCostEquipment(farmId)
  const today = todayISO()
  const [vehicleId, setVehicleId] = useState("")
  const [date, setDate] = useState(today)
  const [hours, setHours] = useState("")
  const [notes, setNotes] = useState("")
  const [shares, setShares] = useState<ShareInput[]>(blankShares())

  if (machines.isLoading) return <Empty>Loading machines…</Empty>
  if (machines.isError || !machines.data) return <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load the machines. <button type="button" onClick={() => machines.refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
  const list = machines.data
  const machine = list.find(m => m.vehicleId === vehicleId)
  const h = pct(hours)
  const total = machine && Number.isFinite(h) ? equipmentCost(h, machine.operatingRatePerHour) : null
  const problem = validateEquipmentUse(machine, hours, date, shares, today)
  const reset = () => { setHours(""); setNotes(""); setShares(blankShares()) }

  const submit = () => cost.mutate({
    vehicleId, workDate: date, hours: h, notes: notes.trim() || undefined,
    allocations: shares.map(s => ({ targetType: s.targetType as "CROP_CYCLE", targetId: s.targetId, percentage: pct(s.percentage) })),
  }, { onSuccess: reset })

  return (
    <div style={panel}>
      <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>Cost a machine's use</p>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "0 0 12px" }}>One day's use of one machine. The rate is Fleet's operating rate per hour: service and repairs only. Fuel is allocated below, so it isn't counted twice.</p>
      {list.length === 0 ? <Empty>No machines found in Fleet.</Empty> : (
        <>
          <div style={grid}>
            <Field label="Machine *" htmlFor="eq-machine">
              <select id="eq-machine" style={inp} value={vehicleId} onChange={e => setVehicleId(e.target.value)}>
                <option value="">Select…</option>
                {list.map(m => <option key={m.vehicleId} value={m.vehicleId}>{m.description}{m.operatingRatePerHour != null ? ` — ${fmtMoney(m.operatingRatePerHour)}/h` : " — no rate set"}</option>)}
              </select>
            </Field>
            <Field label="Date *" htmlFor="eq-date"><input id="eq-date" type="date" max={today} style={inp} value={date} onChange={e => setDate(e.target.value)} /></Field>
            <Field label="Hours *" htmlFor="eq-hours"><input id="eq-hours" type="number" min="0" step="any" style={inp} value={hours} onChange={e => setHours(e.target.value)} /></Field>
            <Field label="Notes" htmlFor="eq-notes"><input id="eq-notes" style={inp} value={notes} onChange={e => setNotes(e.target.value)} placeholder="e.g. Spraying block 3" /></Field>
          </div>
          {machine && machine.operatingRatePerHour == null && <Warn>{machine.registration} has no operating rate yet. Set it in Fleet, under Equipment, then come back.</Warn>}
          {machine && total != null && <p style={{ fontSize: 12.5, margin: "10px 0 0" }} aria-label="Machine use cost">{hours.trim()} h at {fmtMoney(machine.operatingRatePerHour!)}/h = <strong>{fmtMoney(total)}</strong></p>}

          <p style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text)", margin: "16px 0 8px" }}>What did it work on?</p>
          <AgAllocationLines shares={shares} setShares={setShares} options={options} idPrefix="eq" labelPrefix="Machine use: " />
          {!problem && total != null && <Preview amount={total} shares={shares} options={options} />}
          {problem && machine && hours.trim() && <Warn>{problem}</Warn>}

          <div style={{ marginTop: 14 }}>
            <button type="button" style={{ ...btnPrimary, opacity: !problem && !cost.isPending ? 1 : 0.5 }} disabled={!!problem || cost.isPending} onClick={submit}>Cost machine use</button>
          </div>
        </>
      )}
    </div>
  )
}

function FuelRow({ farmId, row, options, onDone }: { farmId: string; row: FuelDispatchRow; options: TargetOption[]; onDone: () => void }) {
  const allocate = useAllocateFuel(farmId)
  const today = todayISO()
  const [notes, setNotes] = useState("")
  const [shares, setShares] = useState<ShareInput[]>(blankShares())
  const problem = validateFuelAllocation(row, shares, today)
  const submit = () => allocate.mutate({
    dispatchId: row.dispatchId, notes: notes.trim() || undefined,
    allocations: shares.map(s => ({ targetType: s.targetType as "CROP_CYCLE", targetId: s.targetId, percentage: pct(s.percentage) })),
  }, { onSuccess: onDone })
  return (
    <div style={{ ...panel, margin: "6px 0 10px" }}>
      <p style={{ fontSize: 12.5, fontWeight: 700, margin: "0 0 8px" }}>Allocate {litresText(row.litres)} ({row.cost != null ? fmtMoney(row.cost) : "no cost"}) to…</p>
      <AgAllocationLines shares={shares} setShares={setShares} options={options} idPrefix="fu" labelPrefix="Fuel: " />
      {!problem && row.cost != null && <Preview amount={row.cost} shares={shares} options={options} />}
      <div style={{ marginTop: 10, maxWidth: 360 }}><Field label="Notes" htmlFor="fu-notes"><input id="fu-notes" style={inp} value={notes} onChange={e => setNotes(e.target.value)} placeholder="e.g. Ploughing" /></Field></div>
      {problem && <Warn>{problem}</Warn>}
      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        <button type="button" style={{ ...btnPrimary, opacity: !problem && !allocate.isPending ? 1 : 0.5 }} disabled={!!problem || allocate.isPending} onClick={submit}>Allocate fuel</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

function OwnFuel({ farmId, options }: { farmId: string; options: TargetOption[] }) {
  const today = todayISO()
  const [from, setFrom] = useState(addDays(today, -30))
  const [to, setTo] = useState(today)
  const [openId, setOpenId] = useState<string | null>(null)
  const fuel = useUnallocatedFuel(farmId, from, to)

  return (
    <div style={panel}>
      <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>Allocate own fuel</p>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "0 0 12px" }}>Fuel dispensed to your own vehicles. Each dispatch is costed at the tank's cost per litre when it was dispensed and allocated once. Fuel sold to customers isn't listed.</p>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 10 }}>
        <Field label="From" htmlFor="fu-from"><input id="fu-from" type="date" max={today} style={{ ...inp, width: 160 }} value={from} onChange={e => setFrom(e.target.value)} /></Field>
        <Field label="To" htmlFor="fu-to"><input id="fu-to" type="date" max={today} style={{ ...inp, width: 160 }} value={to} onChange={e => setTo(e.target.value)} /></Field>
      </div>
      {fuel.isLoading && <Empty>Loading fuel…</Empty>}
      {(fuel.isError || (!fuel.isLoading && !fuel.data)) && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load fuel. <button type="button" onClick={() => fuel.refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>}
      {fuel.data && (fuel.data.dispatches.length === 0
        ? <Empty>No fuel is waiting to be allocated in this range.{fuel.data.alreadyAllocated > 0 ? ` ${fuel.data.alreadyAllocated} already allocated.` : ""}</Empty>
        : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
              <thead><tr style={{ textAlign: "left" }}>{["Date", "Vehicle", "Tank", "Litres", "Cost", ""].map(h => <Th key={h}>{h}</Th>)}</tr></thead>
              <tbody>
                {fuel.data.dispatches.map(r => (
                  <tr key={r.dispatchId} aria-label={`Fuel ${r.date} ${r.vehicle}`} style={{ borderTop: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }}>
                    <td style={{ padding: "9px 10px" }}>{fmtDate(r.date)}</td>
                    <td style={{ padding: "9px 10px" }}>{r.vehicle}</td>
                    <td style={{ padding: "9px 10px" }}>{r.tankName ?? "—"}</td>
                    <td style={{ padding: "9px 10px" }}>{litresText(r.litres)}</td>
                    <td style={{ padding: "9px 10px", fontWeight: 700 }}>{r.cost != null ? fmtMoney(r.cost) : <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>No cost recorded</span>}</td>
                    <td style={{ padding: "9px 10px" }}>
                      {openId === r.dispatchId ? <FuelRow farmId={farmId} row={r} options={options} onDone={() => setOpenId(null)} />
                        : <button type="button" style={btnGhost} disabled={r.cost == null} aria-label={`Allocate fuel ${r.date} ${r.vehicle}`} onClick={() => setOpenId(r.dispatchId)}>Allocate</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {fuel.data.alreadyAllocated > 0 && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>{fuel.data.alreadyAllocated} more in this range already allocated.</p>}
          </div>
        ))}
    </div>
  )
}

export default function AgEquipmentFuelTab({ farmId }: { farmId: string }) {
  const canFinance = usePermission("AGRICULTURE_FINANCE")
  const canFleet = usePermission("FLEET_READ")
  const canFuel = usePermission("FUEL_MARGIN_READ")
  const options = useTargetOptions(farmId)
  if (!canFinance) {
    return (
      <div role="note" style={{ textAlign: "center", padding: "36px 12px" }}>
        <Lock size={26} style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />
        <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>You don't have access to equipment and fuel costs</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Costing needs the Agriculture finance permission. Ask an administrator.</p>
      </div>
    )
  }
  return (
    <div style={{ display: "grid", gap: 16 }}>
      {canFleet ? <MachineUse farmId={farmId} options={options} /> : <div style={card}><Warn>Costing machine use also needs access to Fleet. Ask an administrator for Fleet read access.</Warn></div>}
      {canFuel ? <OwnFuel farmId={farmId} options={options} /> : <div style={card}><Warn>Allocating fuel also needs access to Fuel's cost data (the fuel margin permission). Ask an administrator.</Warn></div>}
    </div>
  )
}
