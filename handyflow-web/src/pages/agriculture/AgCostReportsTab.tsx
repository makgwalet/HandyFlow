// src/pages/agriculture/AgCostReportsTab.tsx
//
// Farm cost reports from the backend's cost-summary endpoints: crops (per hectare), animals (per kg liveweight) and
// groups (per head). COSTS ONLY: Agriculture records no revenue, labour cost or equipment cost, so there is no margin
// or profit here. Crop cost = seed + recorded inputs (labour hours are shown, not costed).
import { useState } from "react"
import { PieChart, Pie, Cell, Tooltip } from "recharts"
import { Download } from "lucide-react"
import { downloadCsv, useCropTypes, useFarmCosts } from "./agCrops.api"
import { round, sumBy, toCsv } from "./agCrops.logic"
import type { AnimalCost, CropCycleCost, GroupCost } from "./agCrops.types"
import { Empty, Th } from "./agCropsUi"
import { AG_ACCENT_TEXT, btnGhost, card, fmtMoney, kpiLabel, kpiValue, td, theadStyle } from "./constants"

type Kind = "crops" | "animals" | "groups"
const KINDS: { key: Kind; label: string }[] = [{ key: "crops", label: "Crops" }, { key: "animals", label: "Animals" }, { key: "groups", label: "Groups" }]
const FILLS = ["var(--hf-info)", "var(--hf-success)", "var(--hf-warning)"]
const num = (n: number | null | undefined, dp = 2) => (n == null ? "—" : n.toLocaleString("en-ZA", { maximumFractionDigits: dp }))

function Split({ parts }: { parts: { label: string; value: number }[] }) {
  const data = parts.filter(p => p.value > 0)
  if (data.length === 0) return null
  return (
    <div style={{ ...card, display: "flex", alignItems: "center", gap: 12 }} aria-label="Cost breakdown">
      <PieChart width={72} height={72}>
        <Pie data={data} dataKey="value" nameKey="label" innerRadius={24} outerRadius={34} stroke="none">{data.map((d, i) => <Cell key={d.label} fill={FILLS[i % FILLS.length]} />)}</Pie>
        <Tooltip formatter={(v, n) => [fmtMoney(Number(v)), n]} />
      </PieChart>
      <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", lineHeight: 1.7 }}>
        {data.map((d, i) => <div key={d.label}><span aria-hidden style={{ display: "inline-block", width: 8, height: 8, borderRadius: "50%", background: FILLS[i % FILLS.length], marginRight: 6 }} />{d.label} {fmtMoney(d.value)}</div>)}
      </div>
    </div>
  )
}

function Kpis({ items }: { items: [string, string][] }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 12, marginBottom: 14 }}>
      {items.map(([l, v]) => <div key={l} style={card}><p style={kpiLabel}>{l}</p><p style={kpiValue}>{v}</p></div>)}
    </div>
  )
}

function Table({ head, rows, foot }: { head: string[]; rows: (string | number)[][]; foot?: (string | number)[] }) {
  return (
    <div style={{ overflowX: "auto" }}>
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
        <thead><tr style={theadStyle}>{head.map(h => <Th key={h}>{h}</Th>)}</tr></thead>
        <tbody>
          {rows.map((r, i) => <tr key={i} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>{r.map((c, j) => <td key={j} style={{ ...td, fontWeight: j === 0 ? 700 : undefined }}>{c}</td>)}</tr>)}
          {foot && <tr style={{ borderTop: "2px solid var(--hf-border)", fontWeight: 800 }}>{foot.map((c, j) => <td key={j} style={td}>{c}</td>)}</tr>}
        </tbody>
      </table>
    </div>
  )
}

export default function AgCostReportsTab({ farmId }: { farmId: string }) {
  const [kind, setKind] = useState<Kind>("crops")
  const crops = useFarmCosts("crops", farmId)
  const animals = useFarmCosts("animals", farmId)
  const groups = useFarmCosts("groups", farmId)
  const types = useCropTypes().data ?? []
  const q = kind === "crops" ? crops : kind === "animals" ? animals : groups

  let body: React.ReactNode = null
  let exportRows: (string | number | null)[][] = []

  if (kind === "crops" && crops.data) {
    const rows: CropCycleCost[] = crops.data
    const label = (c: CropCycleCost) => c.cycleName?.trim() || types.find(t => t.id === c.cropTypeId)?.name || "Crop cycle"
    const total = sumBy(rows, r => r.totalCost), area = sumBy(rows, r => r.areaPlantedHectares)
    // per-hectare figures only make sense over cycles that HAVE an area: numerator and denominator must cover the same cycles
    const withArea = rows.filter(r => (r.areaPlantedHectares ?? 0) > 0)
    const areaCost = sumBy(withArea, r => r.totalCost), costedArea = sumBy(withArea, r => r.areaPlantedHectares)
    const head = ["Crop cycle", "Area (ha)", "Seed", "Inputs", "Total", "Cost / ha", "Labour h", "Yield", "Yield / ha"]
    const data = rows.map(c => [label(c), num(c.areaPlantedHectares), fmtMoney(c.totalSeedCost), fmtMoney(c.totalInputCost), fmtMoney(c.totalCost), fmtMoney(c.costPerHectare), num(c.totalLaborHours), `${num(c.totalYieldHarvested, 3)} ${c.yieldUnitOfMeasure ?? ""}`.trim(), num(c.yieldPerHectare, 3)])
    // the file keeps the yield unit in its own column; the on-screen table shows it next to the quantity
    exportRows = [[...head.slice(0, 8), "Yield unit", head[8]], ...rows.map(c => [label(c), c.areaPlantedHectares, c.totalSeedCost, c.totalInputCost, c.totalCost, c.costPerHectare, c.totalLaborHours, c.totalYieldHarvested, c.yieldUnitOfMeasure, c.yieldPerHectare])]
    body = rows.length === 0 ? <Empty>No crop cycles to report on yet.</Empty> : (
      <>
        <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 12, alignItems: "stretch", marginBottom: 14 }}>
          <Kpis items={[["Total crop cost", fmtMoney(total)], ["Area", `${num(area)} ha`], ["Average cost / ha", costedArea > 0 ? fmtMoney(round(areaCost / costedArea, 2)) : "—"]]} />
          <Split parts={[{ label: "Seed", value: sumBy(rows, r => r.totalSeedCost) }, { label: "Inputs", value: sumBy(rows, r => r.totalInputCost) }]} />
        </div>
        <Table head={head} rows={data} foot={["Total", num(area), fmtMoney(sumBy(rows, r => r.totalSeedCost)), fmtMoney(sumBy(rows, r => r.totalInputCost)), fmtMoney(total), "", num(sumBy(rows, r => r.totalLaborHours)), "", ""]} />
        {rows.some(r => (r.unconvertedYieldUnits ?? 0) > 0) && <p role="status" style={{ fontSize: 12, color: "var(--hf-warning-text)", marginTop: 10 }}>{rows.filter(r => (r.unconvertedYieldUnits ?? 0) > 0).length} crop cycle(s) have harvests in a unit that can't be converted to the crop's unit, so their yield is understated.</p>}
        <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", marginTop: 10 }}>Crop cost is seed plus recorded inputs; labour hours are shown but not costed. Cycles are listed highest cost per hectare first. Yield is converted into each crop's own unit (kg, t, g and lb convert; other units must match).</p>
      </>
    )
  } else if (kind === "animals" && animals.data) {
    const rows: AnimalCost[] = animals.data
    const total = sumBy(rows, r => r.totalCost)
    const head = ["Animal", "Acquisition", "Health", "Feed", "Total", "Weight (kg)", "Cost / kg"]
    exportRows = [head, ...rows.map(a => [a.tagNumber, a.acquisitionCost, a.totalHealthCost, a.totalFeedCost, a.totalCost, a.currentWeightKg, a.costPerKgLiveweight])]
    body = rows.length === 0 ? <Empty>No animals to report on yet.</Empty> : (
      <>
        <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 12, alignItems: "stretch", marginBottom: 14 }}>
          <Kpis items={[["Total animal cost", fmtMoney(total)], ["Animals", String(rows.length)], ["Average per animal", fmtMoney(round(total / rows.length, 2))]]} />
          <Split parts={[{ label: "Acquisition", value: sumBy(rows, r => r.acquisitionCost) }, { label: "Health", value: sumBy(rows, r => r.totalHealthCost) }, { label: "Feed", value: sumBy(rows, r => r.totalFeedCost) }]} />
        </div>
        <Table head={head} rows={rows.map(a => [a.tagNumber, fmtMoney(a.acquisitionCost), fmtMoney(a.totalHealthCost), fmtMoney(a.totalFeedCost), fmtMoney(a.totalCost), num(a.currentWeightKg), fmtMoney(a.costPerKgLiveweight)])}
          foot={["Total", fmtMoney(sumBy(rows, r => r.acquisitionCost)), fmtMoney(sumBy(rows, r => r.totalHealthCost)), fmtMoney(sumBy(rows, r => r.totalFeedCost)), fmtMoney(total), "", ""]} />
        <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", marginTop: 10 }}>A cost per kg of "—" means no weight has been recorded yet; it does not mean the animal is free to keep.</p>
      </>
    )
  } else if (kind === "groups" && groups.data) {
    const rows: GroupCost[] = groups.data
    const total = sumBy(rows, r => r.totalCost), heads = sumBy(rows, r => r.currentCount)
    const head = ["Batch", "Head", "Health", "Feed", "Total", "Cost / head", "Avg weight (kg)", "Cost / kg"]
    exportRows = [head, ...rows.map(g => [g.batchNumber, g.currentCount, g.totalHealthCost, g.totalFeedCost, g.totalCost, g.costPerHead, g.averageWeightKg, g.costPerKgLiveweight])]
    body = rows.length === 0 ? <Empty>No groups to report on yet.</Empty> : (
      <>
        <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 12, alignItems: "stretch", marginBottom: 14 }}>
          <Kpis items={[["Total group cost", fmtMoney(total)], ["Head", String(heads)], ["Average per head", heads > 0 ? fmtMoney(round(total / heads, 2)) : "—"]]} />
          <Split parts={[{ label: "Health", value: sumBy(rows, r => r.totalHealthCost) }, { label: "Feed", value: sumBy(rows, r => r.totalFeedCost) }]} />
        </div>
        <Table head={head} rows={rows.map(g => [g.batchNumber, g.currentCount, fmtMoney(g.totalHealthCost), fmtMoney(g.totalFeedCost), fmtMoney(g.totalCost), fmtMoney(g.costPerHead), num(g.averageWeightKg), fmtMoney(g.costPerKgLiveweight)])}
          foot={["Total", heads, fmtMoney(sumBy(rows, r => r.totalHealthCost)), fmtMoney(sumBy(rows, r => r.totalFeedCost)), fmtMoney(total), "", "", ""]} />
      </>
    )
  }

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16, flexWrap: "wrap" }}>
        <div role="tablist" aria-label="Cost report" style={{ display: "inline-flex", background: "var(--hf-surface-sunken)", borderRadius: 9, padding: 3 }}>
          {KINDS.map(k => (
            <button key={k.key} role="tab" aria-selected={kind === k.key} type="button" onClick={() => setKind(k.key)}
              style={{ border: "none", borderRadius: 7, padding: "6px 16px", fontSize: 12.5, fontWeight: 600, cursor: "pointer", background: kind === k.key ? "var(--hf-surface)" : "transparent", color: kind === k.key ? AG_ACCENT_TEXT : "var(--hf-text-muted)", boxShadow: kind === k.key ? "var(--hf-shadow-sm)" : "none" }}>{k.label}</button>
          ))}
        </div>
        <div style={{ flex: 1 }} />
        <button type="button" style={{ ...btnGhost, opacity: exportRows.length ? 1 : 0.5 }} disabled={!exportRows.length} onClick={() => downloadCsv(`${kind}-costs.csv`, toCsv(exportRows))}><Download size={14} />Export CSV</button>
      </div>
      {q.isLoading ? <Empty>Loading cost report…</Empty> : q.isError ? (
        <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load this report. <button type="button" onClick={() => q.refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
      ) : body}
    </div>
  )
}

