// src/pages/agriculture/AgCropCycleDetail.tsx
//
// One crop cycle: lifecycle stepper with only the actions its state allows, cost summary, and the Inputs / Scouting /
// Harvests / Evidence tabs. Costs only: Agriculture has no revenue data, so no margin is shown.
import { useState } from "react"
import { ArrowLeft, Check } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { agKeys, api, cycleKeys, useAgMutation, useAreas, useCropCycle, useCropTypes, useCycleCost, useHarvests, useInventory, useSeasons } from "./agCrops.api"
import { STATUS_LABEL, STEPS, allowedActions, cropName, cycleLabel, distinctUnits, isHarvestOverdue, stepIndex, stockProblem, todayISO } from "./agCrops.logic"
import type { CropCycle } from "./agCrops.types"
import AgCropInputsTab from "./AgCropInputsTab"
import AgCropScoutingTab from "./AgCropScoutingTab"
import AgCropHarvestsTab from "./AgCropHarvestsTab"
import AgEvidenceTab from "./AgEvidenceTab"
import { Field, Warn } from "./agCropsUi"
import { AG_ACCENT, AG_ACCENT_TEXT, btnDanger, btnGhost, btnPrimary, card, fmtDate, fmtMoney, grid, inp, kpiLabel, panel, statusBadge } from "./constants"

type Tab = "overview" | "inputs" | "scouting" | "harvests" | "evidence"
const TABS: { key: Tab; label: string }[] = [
  { key: "overview", label: "Overview" }, { key: "inputs", label: "Inputs" }, { key: "scouting", label: "Scouting" },
  { key: "harvests", label: "Harvests" }, { key: "evidence", label: "Evidence" },
]
type Panel = "planting" | "fail" | "abandon" | "edit" | "delete" | "complete" | null

function Stepper({ cycle }: { cycle: CropCycle }) {
  const at = stepIndex(cycle.status)
  return (
    <ol aria-label="Crop lifecycle" style={{ display: "flex", listStyle: "none", padding: 0, margin: "0 0 16px", gap: 4, overflowX: "auto" }}>
      {STEPS.map((s, i) => {
        const done = at > i, current = at === i
        return (
          <li key={s} aria-current={current ? "step" : undefined} style={{ flex: 1, minWidth: 90, textAlign: "center" }}>
            <div style={{ height: 4, borderRadius: 2, marginBottom: 6, background: done || current ? AG_ACCENT : "var(--hf-border)", opacity: current ? 1 : done ? 0.55 : 1 }} />
            <span style={{ fontSize: 11.5, fontWeight: current ? 800 : 600, color: current ? AG_ACCENT_TEXT : done ? "var(--hf-text-secondary)" : "var(--hf-text-faint)" }}>
              {done && <Check size={11} style={{ marginRight: 3, verticalAlign: -1 }} />}{STATUS_LABEL[s]}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

export default function AgCropCycleDetail({ farmId, cycleId, onBack }: { farmId: string; cycleId: string; onBack: () => void }) {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const canDelete = usePermission("AGRICULTURE_ADMIN")
  const { data: cycle, isLoading, isError } = useCropCycle(cycleId)
  const types = useCropTypes().data ?? []
  const areas = useAreas(farmId).data ?? []
  const seasons = useSeasons(farmId).data ?? []
  const seeds = (useInventory(farmId).data ?? []).filter(i => i.category === "SEED" && i.status === "ACTIVE")
  const harvests = useHarvests(cycleId).data ?? []
  const cost = useCycleCost(cycleId)
  const [tab, setTab] = useState<Tab>("overview")
  const [open, setOpen] = useState<Panel>(null)
  const [f, setF] = useState({ date: todayISO(), seedItem: "", seedQty: "", seedSource: "", reason: "", variety: "", name: "", hectares: "", expected: "", notes: "" })
  const keys = cycleKeys(farmId, cycleId)
  const close = () => setOpen(null)

  const act = (success: string) => ({ invalidate: keys, success, failure: "That didn't work." })
  const grow = useAgMutation(() => api.patch(`/crop-cycles/${cycleId}/mark-growing`), act("Marked growing"))
  const startHarvest = useAgMutation(() => api.patch(`/crop-cycles/${cycleId}/start-harvest`), act("Harvest started"))
  const completeHarvest = useAgMutation(() => api.patch(`/crop-cycles/${cycleId}/complete-harvest`), act("Harvest completed"))
  const planting = useAgMutation(() => api.patch(`/crop-cycles/${cycleId}/record-planting`, {
    plantingDate: f.date, seedInventoryItemId: f.seedItem || undefined, seedQuantity: f.seedItem ? Number(f.seedQty) : undefined, seedSource: f.seedSource.trim() || undefined,
  }), { invalidate: [...keys, agKeys.inventory(farmId)], success: "Planting recorded", failure: "Couldn't record planting." })
  const fail = useAgMutation(() => api.patch(`/crop-cycles/${cycleId}/mark-failed`, { reason: f.reason.trim() || undefined }), { invalidate: keys, success: "Marked failed", failure: "Couldn't mark it failed." })
  const abandon = useAgMutation(() => api.patch(`/crop-cycles/${cycleId}/abandon`, { reason: f.reason.trim() || undefined }), { invalidate: keys, success: "Cycle abandoned", failure: "Couldn't abandon it." })
  const save = useAgMutation(() => api.put(`/crop-cycles/${cycleId}`, {
    variety: f.variety.trim() || undefined, cycleName: f.name.trim() || undefined, areaPlantedHectares: f.hectares ? Number(f.hectares) : undefined,
    expectedHarvestDate: f.expected || undefined, notes: f.notes.trim() || undefined,
  }), { invalidate: keys, success: "Saved", failure: "Couldn't save your changes." })
  const remove = useAgMutation(() => api.del(`/crop-cycles/${cycleId}`), { invalidate: [agKeys.cyclesOfFarm(farmId), ["ag", "costs", "crops", farmId]], success: "Crop cycle deleted", failure: "Couldn't delete it." })

  if (isLoading) return <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading crop cycle…</p>
  if (isError || !cycle) return <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load this crop cycle. <button type="button" onClick={onBack} style={btnGhost}>Back</button></p>

  const a = allowedActions(cycle.status)
  const seedItem = seeds.find(i => i.id === f.seedItem)
  const seedProblem = stockProblem(Number(f.seedQty), seedItem)
  const label = cycleLabel(cycle, types)
  const season = seasons.find(s => s.id === cycle.seasonId)
  const area = areas.find(x => x.id === cycle.productionAreaId)
  const units = distinctUnits(harvests)
  const overdue = isHarvestOverdue(cycle)
  const reasonPanel = (kind: "fail" | "abandon") => (
    <div style={panel}>
      <Field label={kind === "fail" ? "What went wrong? (optional)" : "Why is it being abandoned? (optional)"} htmlFor="cy-reason"><input id="cy-reason" style={inp} value={f.reason} onChange={e => setF({ ...f, reason: e.target.value })} /></Field>
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button type="button" style={btnDanger} onClick={() => (kind === "fail" ? fail : abandon).mutate(undefined, { onSuccess: close })}>{kind === "fail" ? "Mark failed" : "Abandon cycle"}</button>
        <button type="button" style={btnGhost} onClick={close}>Cancel</button>
      </div>
    </div>
  )

  return (
    <div>
      <button type="button" onClick={onBack} style={{ ...btnGhost, border: "none", padding: "0 0 8px", background: "none" }}><ArrowLeft size={13} />Back to crop cycles</button>
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 4 }}>
        <h2 style={{ fontSize: 18, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{label}</h2>
        <span style={statusBadge(cycle.status)}>{STATUS_LABEL[cycle.status]}</span>
      </div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 16px" }}>{[cropName(cycle, types), area?.name, season?.name, `${cycle.areaPlantedHectares} ha`].filter(Boolean).join(" · ")}</p>

      {(cycle.status === "FAILED" || cycle.status === "ABANDONED")
        ? <div role="status" style={{ ...panel, color: "var(--hf-text-secondary)", fontSize: 13 }}><strong>This cycle was {cycle.status === "FAILED" ? "marked failed" : "abandoned"}.</strong>{cycle.notes ? ` ${cycle.notes}` : ""}</div>
        : <Stepper cycle={cycle} />}

      {canManage && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14 }}>
          {a.recordPlanting && <button type="button" style={btnPrimary} onClick={() => setOpen("planting")}>Record planting</button>}
          {a.markGrowing && <button type="button" style={btnPrimary} onClick={() => grow.mutate(undefined)}>Mark growing</button>}
          {a.startHarvest && <button type="button" style={btnPrimary} onClick={() => startHarvest.mutate(undefined)}>Start harvest</button>}
          {a.completeHarvest && <button type="button" style={btnPrimary} onClick={() => (harvests.length === 0 ? setOpen("complete") : completeHarvest.mutate(undefined))}>Complete harvest</button>}
          {a.edit && <button type="button" style={btnGhost} onClick={() => { setF({ ...f, variety: cycle.variety ?? "", name: cycle.cycleName ?? "", hectares: String(cycle.areaPlantedHectares), expected: cycle.expectedHarvestDate ?? "", notes: cycle.notes ?? "" }); setOpen("edit") }}>Edit</button>}
          {a.fail && <button type="button" style={btnDanger} onClick={() => setOpen("fail")}>Mark failed</button>}
          {a.abandon && <button type="button" style={btnDanger} onClick={() => setOpen("abandon")}>Abandon</button>}
          {canDelete && <button type="button" style={btnDanger} onClick={() => setOpen("delete")}>Delete</button>}
        </div>
      )}

      {open === "planting" && (
        <div style={panel}>
          <div style={grid}>
            <Field label="Planting date *" htmlFor="pl-date"><input id="pl-date" type="date" style={inp} value={f.date} onChange={e => setF({ ...f, date: e.target.value })} /></Field>
            <Field label="Seed from stock" htmlFor="pl-seed"><select id="pl-seed" style={inp} value={f.seedItem} onChange={e => setF({ ...f, seedItem: e.target.value })}><option value="">Not from stock</option>{seeds.map(i => <option key={i.id} value={i.id}>{i.itemName} ({i.currentQuantity} {i.unitOfMeasure})</option>)}</select></Field>
            {f.seedItem && <Field label="Seed quantity *" htmlFor="pl-qty"><input id="pl-qty" type="number" min="0" step="any" style={inp} value={f.seedQty} onChange={e => setF({ ...f, seedQty: e.target.value })} /></Field>}
            <Field label="Seed source" htmlFor="pl-src"><input id="pl-src" style={inp} value={f.seedSource} onChange={e => setF({ ...f, seedSource: e.target.value })} /></Field>
          </div>
          {seedProblem && <Warn tone="danger">{seedProblem}</Warn>}
          <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Seed taken from stock is issued now and counted in this cycle's cost.</p>
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button type="button" style={{ ...btnPrimary, opacity: f.date && !seedProblem && (!f.seedItem || Number(f.seedQty) > 0) ? 1 : 0.5 }} disabled={!f.date || !!seedProblem || (!!f.seedItem && !(Number(f.seedQty) > 0))}
              onClick={() => planting.mutate(undefined, { onSuccess: close })}>Record planting</button>
            <button type="button" style={btnGhost} onClick={close}>Cancel</button>
          </div>
        </div>
      )}
      {open === "fail" && reasonPanel("fail")}
      {open === "abandon" && reasonPanel("abandon")}
      {open === "complete" && (
        <div style={panel}>
          <p style={{ fontSize: 13, margin: "0 0 10px" }}>No harvest has been recorded for this cycle. Complete it anyway?</p>
          <div style={{ display: "flex", gap: 8 }}><button type="button" style={btnPrimary} onClick={() => completeHarvest.mutate(undefined, { onSuccess: close })}>Complete harvest</button><button type="button" style={btnGhost} onClick={close}>Cancel</button></div>
        </div>
      )}
      {open === "edit" && (
        <div style={panel}>
          <div style={grid}>
            <Field label="Cycle name" htmlFor="ed-name"><input id="ed-name" style={inp} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></Field>
            <Field label="Variety" htmlFor="ed-var"><input id="ed-var" style={inp} value={f.variety} onChange={e => setF({ ...f, variety: e.target.value })} /></Field>
            <Field label="Area planted (ha)" htmlFor="ed-ha"><input id="ed-ha" type="number" min="0" step="0.01" style={inp} value={f.hectares} onChange={e => setF({ ...f, hectares: e.target.value })} /></Field>
            <Field label="Expected harvest" htmlFor="ed-exp"><input id="ed-exp" type="date" style={inp} value={f.expected} onChange={e => setF({ ...f, expected: e.target.value })} /></Field>
            <Field label="Notes" htmlFor="ed-notes"><input id="ed-notes" style={inp} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} /></Field>
          </div>
          <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Saving replaces these fields with what is shown here, so clearing one removes it.</p>
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}><button type="button" style={btnPrimary} onClick={() => save.mutate(undefined, { onSuccess: close })}>Save</button><button type="button" style={btnGhost} onClick={close}>Cancel</button></div>
        </div>
      )}
      {open === "delete" && (
        <div style={panel}>
          <p style={{ fontSize: 13, margin: "0 0 10px" }}>Delete this crop cycle? Its inputs, scouting and harvests will no longer be reachable. Stock already issued is not returned.</p>
          <div style={{ display: "flex", gap: 8 }}><button type="button" style={btnDanger} onClick={() => remove.mutate(undefined, { onSuccess: onBack })}>Delete crop cycle</button><button type="button" style={btnGhost} onClick={close}>Cancel</button></div>
        </div>
      )}

      <div role="tablist" style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border)", margin: "6px 0 18px", overflowX: "auto" }}>
        {TABS.map(t => (
          <button key={t.key} role="tab" aria-selected={tab === t.key} type="button" onClick={() => setTab(t.key)}
            style={{ padding: "10px 16px", border: "none", background: "none", cursor: "pointer", fontSize: 13, fontWeight: 600, whiteSpace: "nowrap", marginBottom: -1,
              color: tab === t.key ? AG_ACCENT_TEXT : "var(--hf-text-muted)", borderBottom: tab === t.key ? `2px solid ${AG_ACCENT}` : "2px solid transparent" }}>{t.label}</button>
        ))}
      </div>

      {tab === "overview" && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 14 }}>
          <div style={card}>
            <p style={kpiLabel}>Cycle</p>
            <dl style={{ margin: 0, fontSize: 12.5, display: "grid", gridTemplateColumns: "auto 1fr", gap: "6px 14px" }}>
              <dt style={{ color: "var(--hf-text-faint)" }}>Planted</dt><dd style={{ margin: 0 }}>{fmtDate(cycle.plantingDate)}</dd>
              <dt style={{ color: "var(--hf-text-faint)" }}>Expected harvest</dt><dd style={{ margin: 0, color: overdue ? "var(--hf-danger-text)" : undefined, fontWeight: overdue ? 700 : undefined }}>{fmtDate(cycle.expectedHarvestDate)}{overdue ? " · overdue" : ""}</dd>
              <dt style={{ color: "var(--hf-text-faint)" }}>Seed</dt><dd style={{ margin: 0 }}>{cycle.seedQuantity != null ? `${cycle.seedQuantity}${cycle.seedSource ? ` · ${cycle.seedSource}` : ""}` : cycle.seedSource ?? "—"}</dd>
              <dt style={{ color: "var(--hf-text-faint)" }}>Notes</dt><dd style={{ margin: 0 }}>{cycle.notes ?? "—"}</dd>
            </dl>
          </div>
          <div style={card} aria-label="Cost summary">
            <p style={kpiLabel}>Cost summary</p>
            {cost.isLoading ? <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>Loading…</p> : cost.data ? (
              <>
                <dl style={{ margin: 0, fontSize: 12.5, display: "grid", gridTemplateColumns: "auto 1fr", gap: "6px 14px" }}>
                  <dt style={{ color: "var(--hf-text-faint)" }}>Seed</dt><dd style={{ margin: 0 }}>{fmtMoney(cost.data.totalSeedCost)}</dd>
                  <dt style={{ color: "var(--hf-text-faint)" }}>Inputs</dt><dd style={{ margin: 0 }}>{fmtMoney(cost.data.totalInputCost)}</dd>
                  <dt style={{ color: "var(--hf-text-faint)", fontWeight: 700 }}>Total</dt><dd style={{ margin: 0, fontWeight: 800 }}>{fmtMoney(cost.data.totalCost)}</dd>
                  <dt style={{ color: "var(--hf-text-faint)" }}>Cost per hectare</dt><dd style={{ margin: 0 }}>{fmtMoney(cost.data.costPerHectare)}</dd>
                  <dt style={{ color: "var(--hf-text-faint)" }}>Yield</dt><dd style={{ margin: 0 }}>{cost.data.totalYieldHarvested} {cost.data.yieldUnitOfMeasure ?? ""}</dd>
                  <dt style={{ color: "var(--hf-text-faint)" }}>Yield per hectare</dt><dd style={{ margin: 0 }}>{cost.data.yieldPerHectare ?? "—"}</dd>
                  <dt style={{ color: "var(--hf-text-faint)" }}>Labour hours</dt><dd style={{ margin: 0 }}>{cost.data.totalLaborHours ?? 0}</dd>
                </dl>
                <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: "10px 0 0" }}>Costs only: seed and recorded inputs. Labour hours are shown but not costed.</p>
                {units.length > 1 && <Warn>Harvests use more than one unit ({units.join(", ")}), so yield is not reliable.</Warn>}
              </>
            ) : <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>Cost summary unavailable.</p>}
          </div>
        </div>
      )}
      {tab === "inputs" && <AgCropInputsTab cycle={cycle} />}
      {tab === "scouting" && <AgCropScoutingTab cycle={cycle} />}
      {tab === "harvests" && <AgCropHarvestsTab cycle={cycle} />}
      {tab === "evidence" && <AgEvidenceTab targetType="crop-cycle" targetId={cycle.id} />}
    </div>
  )
}
