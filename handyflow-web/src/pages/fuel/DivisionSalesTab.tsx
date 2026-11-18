// Revenue and sales by division, read-only. Money appears only when the server sends it.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inputStyle } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { EmptyState, KpiGrid, KpiTile, Panel, Sparkline, type Tone } from "./fuelUi"
import { changeText } from "./groupReportOps"
import { DEFINITIONS, shares, unitFor, type DivisionRow, type DivisionSales } from "./divisionSalesOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const PERIODS = [[7, "7 days"], [30, "30 days"], [90, "90 days"]] as const
const TONE: Record<string, Tone> = { DISTRIBUTION: "blue", RETAIL_FORECOURT: "green", WHOLESALE_SUPPLY: "violet", TRUCK_STOP: "amber" }

function Change({ now, before }: { now: number | null; before: number | null }) {
  const t = changeText(now, before)
  if (!t) return null
  return <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{t === "new" ? "nothing in the period before" : `${t} on the period before`}</span>
}

function Card({ r, share }: { r: DivisionRow; share: number | null }) {
  const money = r.revenue != null
  return (
    <Panel title={r.label}>
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 10, flexWrap: "wrap" }}>
          <span style={{ fontSize: 24, fontWeight: 800, color: "var(--hf-text)" }}>{money ? fmtR(r.revenue!) : `${r.count} ${unitFor(r.division, r.count)}`}</span>
          {money && <Change now={r.revenue} before={r.previousRevenue} />}
        </div>
        <div style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>
          {money && <>{r.count} {unitFor(r.division, r.count)}{r.litres > 0 && <> · </>}</>}
          {r.litres > 0 && <>{fmtL(r.litres)}</>}
          {r.litres > 0 && <> <Change now={r.litres} before={r.previousLitres} /></>}
        </div>
        {r.dailyRevenue && r.dailyRevenue.length > 1 && <div style={{ color: `var(--hf-${TONE[r.division] ?? "blue"}, var(--hf-primary))` }}><Sparkline values={r.dailyRevenue} width={220} height={36} /></div>}
        {share != null && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{share.toFixed(0)}% of revenue</div>}
        <p style={{ margin: 0, fontSize: 11.5, color: "var(--hf-text-muted)" }}>{DEFINITIONS[r.division]}</p>
      </div>
    </Panel>
  )
}

export default function DivisionSalesTab() {
  const [days, setDays] = useState(30)
  const q = useQuery<DivisionSales>({ queryKey: ["fuel-division-sales", days], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/division-sales", { params: { days } })) })
  if (q.isLoading) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  if (q.isError || !q.data) return <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load sales by division.</p>
  const d = q.data
  const share = shares(d.divisions)
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          What each division sold from {d.from} to {d.to}, against the same number of days before. This page only reports; it changes nothing.
          {!d.moneyShown && " Amounts are hidden because you do not have the margin permission; litres and counts are shown."}
        </p>
        <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))} style={{ ...inputStyle, width: "auto" }}>
          {PERIODS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </div>
      {d.divisions.length === 0 ? <EmptyState title="No divisions to show" hint="Choose your divisions under Setup, or ask for access to one." /> : (<>
        <KpiGrid min={200}>
          {d.moneyShown && <KpiTile label="Revenue" tone="green" value={fmtR(d.total.revenue ?? 0)} hint={changeText(d.total.revenue, d.total.previousRevenue) ?? "no earlier period"} />}
          <KpiTile label="Litres" tone="blue" value={fmtL(d.total.litres)} hint={changeText(d.total.litres, d.total.previousLitres) ?? "no earlier period"} />
          <KpiTile label="Sales" tone="violet" value={d.total.count} hint="loads, shifts, dispatches and stays" />
        </KpiGrid>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", gap: 16 }}>
          {d.divisions.map(r => <Card key={r.division} r={r} share={share ? share[r.division] : null} />)}
        </div>
      </>)}
    </div>
  )
}
