// src/pages/agriculture/AgCropCyclesTab.tsx
//
// Crop cycles for one farm: KPIs, a status breakdown, filters, the list, and a create form. Opening a cycle
// shows its detail (lifecycle, inputs, scouting, harvests, costs, evidence) in place.
import { useState } from "react"
import { useLocation } from "react-router-dom"
import { PieChart, Pie, Cell, Tooltip } from "recharts"
import { Plus, Sprout } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { agKeys, api, useAgMutation, useAreas, useCropCycles, useCropTypes, useEnterprises, useInventory, useSeasons } from "./agCrops.api"
import { STATUS_LABEL, cropName, cycleKpis, cycleLabel, isHarvestOverdue, statusCounts, stockProblem, suggestExpectedHarvest } from "./agCrops.logic"
import type { CropCycle, CycleStatus } from "./agCrops.types"
import AgCropCycleDetail from "./AgCropCycleDetail"
import { AG_ACCENT_TEXT, btnGhost, btnPrimary, card, fmtDate, inp, kpiLabel, kpiValue, lbl, panel, statusBadge } from "./constants"

const STATUS_FILL: Record<CycleStatus, string> = {
  PLANNED: "var(--hf-text-faint)", PLANTED: "var(--hf-info)", GROWING: "var(--hf-success)", HARVESTING: "var(--hf-warning)",
  HARVESTED: "var(--hf-accent)", FAILED: "var(--hf-danger)", ABANDONED: "var(--hf-text-muted)",
}
const MAX_ROWS = 200

function CycleForm({ farmId, onDone }: { farmId: string; onDone: () => void }) {
  const types = (useCropTypes().data ?? []).filter(t => t.status === "ACTIVE")
  const areas = useAreas(farmId).data ?? []
  const seasons = (useSeasons(farmId).data ?? []).filter(s => s.status !== "CLOSED")
  const enterprises = useEnterprises(farmId).data ?? []
  const seeds = (useInventory(farmId).data ?? []).filter(i => i.category === "SEED" && i.status === "ACTIVE")
  const [f, setF] = useState({ areaId: "", cropTypeId: "", variety: "", cycleName: "", hectares: "", seasonId: "", enterpriseId: "",
    plantingDate: "", expected: "", seedItemId: "", seedQty: "", seedSource: "", notes: "" })
  const [expectedTouched, setExpectedTouched] = useState(false)
  const set = (patch: Partial<typeof f>) => setF(prev => ({ ...prev, ...patch }))

  // keep the harvest date in step with planting date + crop type until the user types their own
  const withSuggestion = (patch: Partial<typeof f>) => {
    const next = { ...f, ...patch }
    if (!expectedTouched) next.expected = suggestExpectedHarvest(next.plantingDate, types.find(t => t.id === next.cropTypeId)?.typicalGrowingDays) ?? ""
    setF(next)
  }

  const area = areas.find(a => a.id === f.areaId)
  const hectares = Number(f.hectares)
  const seedItem = seeds.find(i => i.id === f.seedItemId)
  const seedProblem = f.plantingDate && seedItem ? stockProblem(Number(f.seedQty), seedItem) : null
  const oversize = !!area?.sizeHectares && hectares > area.sizeHectares
  const valid = !!f.areaId && !!f.cropTypeId && hectares > 0 && !seedProblem && !(f.plantingDate && f.seedItemId && !(Number(f.seedQty) > 0))

  const create = useAgMutation(
    () => api.post(`/farms/${farmId}/crop-cycles`, {
      farmId, productionAreaId: f.areaId, cropTypeId: f.cropTypeId, areaPlantedHectares: hectares,
      enterpriseId: f.enterpriseId || undefined, seasonId: f.seasonId || undefined,
      variety: f.variety.trim() || undefined, cycleName: f.cycleName.trim() || undefined, notes: f.notes.trim() || undefined,
      plantingDate: f.plantingDate || undefined, expectedHarvestDate: f.expected || undefined,
      // the server only issues seed when a planting date is given with the item
      seedInventoryItemId: f.plantingDate && f.seedItemId ? f.seedItemId : undefined,
      seedQuantity: f.plantingDate && f.seedItemId ? Number(f.seedQty) : undefined,
      seedSource: f.plantingDate && f.seedSource.trim() ? f.seedSource.trim() : undefined,
    }),
    { invalidate: [agKeys.cyclesOfFarm(farmId), agKeys.inventory(farmId), ["ag", "costs", "crops", farmId]], success: "Crop cycle created", failure: "Couldn't create the crop cycle." })

  return (
    <div style={panel}>
      <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 12px" }}>New crop cycle</p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 10 }}>
        <div><label style={lbl} htmlFor="cc-area">Production area *</label>
          <select id="cc-area" style={inp} value={f.areaId} onChange={e => { const a = areas.find(x => x.id === e.target.value); set({ areaId: e.target.value, hectares: f.hectares || (a?.sizeHectares ? String(a.sizeHectares) : "") }) }}>
            <option value="">Select…</option>{areas.map(a => <option key={a.id} value={a.id}>{a.name}{a.sizeHectares ? ` (${a.sizeHectares} ha)` : ""}</option>)}
          </select></div>
        <div><label style={lbl} htmlFor="cc-crop">Crop *</label>
          <select id="cc-crop" style={inp} value={f.cropTypeId} onChange={e => withSuggestion({ cropTypeId: e.target.value })}>
            <option value="">Select…</option>{types.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select></div>
        <div><label style={lbl} htmlFor="cc-variety">Variety</label><input id="cc-variety" style={inp} value={f.variety} onChange={e => set({ variety: e.target.value })} placeholder="e.g. PAN 6767" /></div>
        <div><label style={lbl} htmlFor="cc-name">Cycle name</label><input id="cc-name" style={inp} value={f.cycleName} onChange={e => set({ cycleName: e.target.value })} placeholder="Optional, e.g. Maize – Field 3" /></div>
        <div><label style={lbl} htmlFor="cc-ha">Area planted (ha) *</label><input id="cc-ha" type="number" min="0" step="0.01" style={inp} value={f.hectares} onChange={e => set({ hectares: e.target.value })} /></div>
        <div><label style={lbl} htmlFor="cc-season">Season</label>
          <select id="cc-season" style={inp} value={f.seasonId} onChange={e => set({ seasonId: e.target.value })}>
            <option value="">None</option>{seasons.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select></div>
        <div><label style={lbl} htmlFor="cc-ent">Enterprise</label>
          <select id="cc-ent" style={inp} value={f.enterpriseId} onChange={e => set({ enterpriseId: e.target.value })}>
            <option value="">None</option>{enterprises.map(e => <option key={e.id} value={e.id}>{e.name}</option>)}
          </select></div>
        <div><label style={lbl} htmlFor="cc-plant">Planting date</label><input id="cc-plant" type="date" style={inp} value={f.plantingDate} onChange={e => withSuggestion({ plantingDate: e.target.value })} /></div>
        <div><label style={lbl} htmlFor="cc-exp">Expected harvest</label><input id="cc-exp" type="date" style={inp} value={f.expected} onChange={e => { setExpectedTouched(true); set({ expected: e.target.value }) }} /></div>
      </div>
      {oversize && <p role="status" style={{ fontSize: 12, color: "var(--hf-warning-text)", margin: "10px 0 0" }}>{hectares} ha is more than {area?.name}'s recorded size of {area?.sizeHectares} ha.</p>}
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "10px 0 0" }}>
        {f.plantingDate ? "With a planting date the cycle starts as Planted." : "Without a planting date the cycle starts as Planned; record planting later."}
      </p>
      {f.plantingDate && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 10, marginTop: 10 }}>
          <div><label style={lbl} htmlFor="cc-seed">Seed from stock</label>
            <select id="cc-seed" style={inp} value={f.seedItemId} onChange={e => set({ seedItemId: e.target.value })}>
              <option value="">Not from stock</option>{seeds.map(i => <option key={i.id} value={i.id}>{i.itemName} ({i.currentQuantity} {i.unitOfMeasure})</option>)}
            </select></div>
          {f.seedItemId && <div><label style={lbl} htmlFor="cc-qty">Seed quantity *</label><input id="cc-qty" type="number" min="0" step="any" style={inp} value={f.seedQty} onChange={e => set({ seedQty: e.target.value })} /></div>}
          <div><label style={lbl} htmlFor="cc-src">Seed source</label><input id="cc-src" style={inp} value={f.seedSource} onChange={e => set({ seedSource: e.target.value })} placeholder="Supplier or own saved seed" /></div>
        </div>
      )}
      {seedProblem && <p role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>{seedProblem}</p>}
      <div style={{ marginTop: 10 }}><label style={lbl} htmlFor="cc-notes">Notes</label><input id="cc-notes" style={inp} value={f.notes} onChange={e => set({ notes: e.target.value })} /></div>
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button type="button" style={{ ...btnPrimary, opacity: valid && !create.isPending ? 1 : 0.5 }} disabled={!valid || create.isPending}
          onClick={() => create.mutate(undefined, { onSuccess: onDone })}>Create cycle</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgCropCyclesTab({ farmId }: { farmId: string }) {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const location = useLocation()
  const [open, setOpen] = useState<{ id: string; locationKey: string } | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [status, setStatus] = useState("")
  const [seasonId, setSeasonId] = useState("")
  const [search, setSearch] = useState("")
  const { data: cycles = [], isLoading, isError, refetch } = useCropCycles(farmId)
  const types = useCropTypes().data ?? []
  const seasons = useSeasons(farmId).data ?? []
  const areas = useAreas(farmId).data ?? []

  // like the farm drill-down: any navigation (including clicking the section again) returns to the list
  if (open && open.locationKey === location.key) return <AgCropCycleDetail farmId={farmId} cycleId={open.id} onBack={() => setOpen(null)} />

  const kpis = cycleKpis(cycles)
  const q = search.trim().toLowerCase()
  const rows = cycles.filter(c =>
    (!status || c.status === status) && (!seasonId || c.seasonId === seasonId) &&
    (!q || cycleLabel(c, types).toLowerCase().includes(q) || (c.variety ?? "").toLowerCase().includes(q)))
  const counts = statusCounts(cycles)

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 12, marginBottom: 16 }}>
        {[["Cycles", kpis.total], ["In production", kpis.active], ["Area in production", `${kpis.areaInProduction} ha`], ["Harvesting", kpis.harvesting]].map(([l, v]) => (
          <div key={String(l)} style={card}><p style={kpiLabel}>{l}</p><p style={kpiValue}>{v}</p></div>
        ))}
        <div style={{ ...card, display: "flex", alignItems: "center", gap: 12 }} aria-label="Cycles by status">
          {counts.length > 0 ? (
            <>
              <PieChart width={64} height={64}>
                <Pie data={counts} dataKey="count" nameKey="status" innerRadius={20} outerRadius={30} stroke="none">
                  {counts.map(c => <Cell key={c.status} fill={STATUS_FILL[c.status]} />)}
                </Pie>
                <Tooltip formatter={(v, n) => [v, STATUS_LABEL[n as CycleStatus]]} />
              </PieChart>
              <div style={{ fontSize: 11, color: "var(--hf-text-muted)", lineHeight: 1.6 }}>
                {counts.map(c => <div key={c.status}><span aria-hidden style={{ display: "inline-block", width: 8, height: 8, borderRadius: "50%", background: STATUS_FILL[c.status], marginRight: 6 }} />{STATUS_LABEL[c.status]} {c.count}</div>)}
              </div>
            </>
          ) : <p style={{ ...kpiLabel, margin: 0 }}>No cycles yet</p>}
        </div>
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <input aria-label="Search crop cycles" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search crop or variety" style={{ ...inp, width: 200 }} />
        <select aria-label="Filter by status" value={status} onChange={e => setStatus(e.target.value)} style={{ ...inp, width: "auto" }}>
          <option value="">All statuses</option>{(Object.keys(STATUS_LABEL) as CycleStatus[]).map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
        <select aria-label="Filter by season" value={seasonId} onChange={e => setSeasonId(e.target.value)} style={{ ...inp, width: "auto" }}>
          <option value="">All seasons</option>{seasons.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <div style={{ flex: 1 }} />
        {canManage && !showForm && <button type="button" style={btnPrimary} onClick={() => setShowForm(true)}><Plus size={14} />New crop cycle</button>}
      </div>

      {showForm && canManage && <CycleForm farmId={farmId} onDone={() => setShowForm(false)} />}

      {isLoading ? <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading crop cycles…</p>
        : isError ? <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load crop cycles. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
        : rows.length === 0 ? (
          <div style={{ textAlign: "center", padding: "32px 12px", color: "var(--hf-text-muted)", fontSize: 13 }}>
            <Sprout size={26} style={{ color: "var(--hf-text-disabled)", marginBottom: 6 }} />
            <p style={{ margin: 0 }}>{cycles.length === 0 ? "No crop cycles yet. Create one to start tracking planting, inputs, scouting and harvest." : "No crop cycles match these filters."}</p>
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
              <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase", letterSpacing: 0.4 }}>
                {["Crop", "Area", "Production area", "Status", "Planted", "Expected harvest"].map(h => <th key={h} style={{ padding: "8px 10px", fontWeight: 700 }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {rows.map(c => <CycleRow key={c.id} c={c} label={cycleLabel(c, types)} crop={cropName(c, types)} areaName={areas.find(a => a.id === c.productionAreaId)?.name ?? "—"}
                  onOpen={() => setOpen({ id: c.id, locationKey: location.key })} />)}
              </tbody>
            </table>
            {cycles.length >= MAX_ROWS && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 10px" }}>Showing the first {MAX_ROWS} crop cycles.</p>}
          </div>
        )}
    </div>
  )
}

function CycleRow({ c, label, crop, areaName, onOpen }: { c: CropCycle; label: string; crop: string; areaName: string; onOpen: () => void }) {
  const overdue = isHarvestOverdue(c)
  return (
    <tr onClick={onOpen} style={{ borderTop: "1px solid var(--hf-border-subtle)", cursor: "pointer" }}>
      <td style={{ padding: "10px" }}>
        <button type="button" onClick={e => { e.stopPropagation(); onOpen() }} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", textAlign: "left", color: "var(--hf-text)" }}>
          <div style={{ fontWeight: 700, color: AG_ACCENT_TEXT }}>{label}</div>
          {label !== crop && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{crop}</div>}
        </button>
      </td>
      <td style={{ padding: "10px" }}>{c.areaPlantedHectares} ha</td>
      <td style={{ padding: "10px" }}>{areaName}</td>
      <td style={{ padding: "10px" }}><span style={statusBadge(c.status)}>{STATUS_LABEL[c.status]}</span></td>
      <td style={{ padding: "10px" }}>{fmtDate(c.plantingDate)}</td>
      <td style={{ padding: "10px", color: overdue ? "var(--hf-danger-text)" : undefined, fontWeight: overdue ? 700 : undefined }}>
        {fmtDate(c.expectedHarvestDate)}{overdue ? " · overdue" : ""}
      </td>
    </tr>
  )
}

