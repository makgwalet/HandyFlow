// src/pages/agriculture/AgLabourTab.tsx
//
// Costs the labour recorded on crop work (input applications and harvests) into the cost ledger (ADR-001, W3). Costing is an explicit finance
// action: whoever logs a spray does not need to see what the sprayer is paid. The rate comes from HR (when this user may see HR data) or is
// typed in, which is how casual workers with no HR record are handled; it is loaded with the on-cost and SNAPSHOTTED, so a later raise never
// rewrites a past cost. Needs AGRICULTURE_FINANCE. This screen never shows a salary, only the hourly rate derived from it.
import { useState } from "react"
import { Lock } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { useCropCycles, useCropTypes } from "./agCrops.api"
import { cycleLabel } from "./agCrops.logic"
import { useCostLabour, useUncostedLabour, useUpdateFinanceSettings, type FinanceSettings, type LabourCandidate } from "./agLabour.api"
import { baseRateFor, costFor, hoursText, rowKey, settingsProblem, validateCosting } from "./agLabour.logic"
import { Empty, Field, Warn } from "./agCropsUi"
import { btnGhost, btnPrimary, card, fmtDate, fmtMoney, inp, kpiLabel, kpiValue, panel } from "./constants"

function SettingsPanel({ settings }: { settings: FinanceSettings }) {
  const [editing, setEditing] = useState(false)
  const [hours, setHours] = useState(String(settings.standardHoursPerWeek))
  const [onCost, setOnCost] = useState(String(settings.labourOnCostPercent))
  const save = useUpdateFinanceSettings()
  const problem = editing ? settingsProblem(hours, onCost) : null

  if (!editing) {
    return (
      <div style={{ ...card, marginBottom: 14 }}>
        <div style={{ display: "flex", gap: 24, flexWrap: "wrap", alignItems: "center" }}>
          <div><p style={kpiLabel}>Standard week</p><p style={{ ...kpiValue, fontSize: 18 }}>{settings.standardHoursPerWeek} h</p></div>
          <div><p style={kpiLabel}>Employer on-cost</p><p style={{ ...kpiValue, fontSize: 18 }}>{settings.labourOnCostPercent}%</p></div>
          <div style={{ flex: 1, minWidth: 220, fontSize: 12, color: "var(--hf-text-muted)" }}>
            An HR salary is per pay period, so the hourly rate is that salary over the ordinary hours in the period. The on-cost (UIF, SDL and so on) is added to the rate. Changes apply to labour costed from now on.
          </div>
          <button type="button" style={btnGhost} onClick={() => { setHours(String(settings.standardHoursPerWeek)); setOnCost(String(settings.labourOnCostPercent)); setEditing(true) }}>Change</button>
        </div>
        {!settings.configured && <Warn>These are the defaults (45 hours, no on-cost). Set your employer on-cost so labour is not understated.</Warn>}
      </div>
    )
  }
  return (
    <div style={{ ...panel, marginBottom: 14 }}>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "end" }}>
        <Field label="Standard hours per week" htmlFor="lab-hours"><input id="lab-hours" type="number" min="0" step="any" style={{ ...inp, width: 160 }} value={hours} onChange={e => setHours(e.target.value)} /></Field>
        <Field label="Employer on-cost (%)" htmlFor="lab-oncost"><input id="lab-oncost" type="number" min="0" step="any" style={{ ...inp, width: 160 }} value={onCost} onChange={e => setOnCost(e.target.value)} /></Field>
        <button type="button" style={{ ...btnPrimary, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending}
          onClick={() => save.mutate({ standardHoursPerWeek: Number(hours.replace(",", ".")), labourOnCostPercent: Number(onCost.replace(",", ".")) }, { onSuccess: () => setEditing(false) })}>Save settings</button>
        <button type="button" style={btnGhost} onClick={() => setEditing(false)}>Cancel</button>
      </div>
      {problem && <Warn tone="danger">{problem}</Warn>}
    </div>
  )
}

function LabourBody({ farmId }: { farmId: string }) {
  const overview = useUncostedLabour(farmId)
  const cycles = useCropCycles(farmId).data ?? []
  const cropTypes = useCropTypes().data ?? []
  const cost = useCostLabour(farmId)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [typed, setTyped] = useState<Record<string, string>>({})

  if (overview.isLoading) return <Empty>Loading labour…</Empty>
  if (overview.isError || !overview.data) {
    return <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load labour. <button type="button" onClick={() => overview.refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
  }
  const { settings, hrRatesAvailable, candidates } = overview.data
  const cycleName = (id: string) => { const c = cycles.find(x => x.id === id); return c ? cycleLabel(c, cropTypes) : "Crop cycle" }
  const chosen = candidates.filter(r => selected.has(rowKey(r)))
  const problem = chosen.length ? validateCosting(chosen, typed) : null
  const total = chosen.reduce((t, r) => t + (costFor(r, typed[rowKey(r)], settings.labourOnCostPercent) ?? 0), 0)
  const toggle = (r: LabourCandidate) => setSelected(prev => { const n = new Set(prev); const k = rowKey(r); if (n.has(k)) n.delete(k); else n.add(k); return n })
  const allSelected = candidates.length > 0 && chosen.length === candidates.length

  const submit = () => cost.mutate(
    { items: chosen.map(r => { const rate = typed[rowKey(r)]?.trim() ? Number(typed[rowKey(r)].replace(",", ".")) : undefined; return { sourceType: r.sourceType, sourceId: r.sourceId, hourlyRate: rate } }) },
    { onSuccess: () => { setSelected(new Set()); setTyped({}) } })

  return (
    <div>
      <SettingsPanel settings={settings} />
      {!hrRatesAvailable && <Warn>Salary-based rates need HR access, so type an hourly rate for each piece of work. You can still cost casual workers this way.</Warn>}

      {candidates.length === 0 ? <Empty>No labour is waiting to be costed. Hours recorded on input applications and harvests appear here.</Empty> : (
        <>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
              <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase", letterSpacing: 0.4 }}>
                <th style={{ padding: "8px 10px" }}><input type="checkbox" aria-label="Select all" checked={allSelected} onChange={() => setSelected(allSelected ? new Set() : new Set(candidates.map(rowKey)))} /></th>
                {["Date", "Work", "Worker", "Hours", "Hourly rate", "Cost"].map(h => <th key={h} style={{ padding: "8px 10px", fontWeight: 700 }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {candidates.map(r => {
                  const k = rowKey(r)
                  const effective = baseRateFor(r, typed[k])
                  const est = costFor(r, typed[k], settings.labourOnCostPercent)
                  return (
                    <tr key={k} aria-label={`${r.description} ${r.date}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                      <td style={{ padding: "9px 10px" }}><input type="checkbox" aria-label={`Select ${r.description} ${r.date}`} checked={selected.has(k)} onChange={() => toggle(r)} /></td>
                      <td style={{ padding: "9px 10px" }}>{fmtDate(r.date)}</td>
                      <td style={{ padding: "9px 10px" }}>{r.description}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{cycleName(r.cropCycleId)}</div></td>
                      <td style={{ padding: "9px 10px" }}>{r.workerName ?? "—"}</td>
                      <td style={{ padding: "9px 10px" }}>{hoursText(r.hours)}</td>
                      <td style={{ padding: "9px 10px", minWidth: 190 }}>
                        {r.suggestedRate != null && <div style={{ fontSize: 11.5, marginBottom: 3 }}>{fmtMoney(r.suggestedRate)}/h from HR</div>}
                        <input aria-label={`Hourly rate for ${r.description} ${r.date}`} type="number" min="0" step="any" style={{ ...inp, width: 150 }} value={typed[k] ?? ""} placeholder={r.suggestedRate != null ? "Override" : "Hourly rate"}
                          onChange={e => setTyped(prev => ({ ...prev, [k]: e.target.value }))} />
                        {r.suggestedRate == null && typed[k] == null && r.rateNote && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3, maxWidth: 220 }}>{r.rateNote[0].toUpperCase() + r.rateNote.slice(1)}</div>}
                      </td>
                      <td style={{ padding: "9px 10px", fontWeight: 700 }}>{effective != null && est != null ? fmtMoney(est) : "—"}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>The cost includes the {settings.labourOnCostPercent}% on-cost. A typed rate is the base rate, before the on-cost.</p>
          {problem && <Warn tone="danger">{problem}</Warn>}
          <div style={{ marginTop: 12 }}>
            <button type="button" style={{ ...btnPrimary, opacity: chosen.length && !problem && !cost.isPending ? 1 : 0.5 }} disabled={!chosen.length || !!problem || cost.isPending} onClick={submit}>
              {chosen.length ? `Cost ${chosen.length} selected (about ${fmtMoney(total)})` : "Cost selected"}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

export default function AgLabourTab({ farmId }: { farmId: string }) {
  const canFinance = usePermission("AGRICULTURE_FINANCE")
  if (!canFinance) {
    return (
      <div role="note" style={{ textAlign: "center", padding: "36px 12px" }}>
        <Lock size={26} style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />
        <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>You don't have access to labour costs</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Costing labour needs the Agriculture finance permission. Ask an administrator.</p>
      </div>
    )
  }
  return <LabourBody farmId={farmId} />
}
