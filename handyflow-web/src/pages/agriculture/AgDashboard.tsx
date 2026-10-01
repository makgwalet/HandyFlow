// src/pages/agriculture/AgDashboard.tsx
//
// All-farms overview from ONE call (GET /agriculture/dashboard): totals, what needs attention, farm types, livestock by species,
// crops in production, farm locations and a per-farm table. Shows only what Agriculture records; there is no revenue, margin,
// labour or equipment cost, weather or trend data yet, so none is shown.
import { Suspense, lazy } from "react"
import { Link } from "react-router-dom"
import { PieChart, Pie, Cell, Tooltip } from "recharts"
import { MapPin, Sprout, Tractor, Wheat } from "lucide-react"
import { useAgDashboard, type AttentionItem } from "./agDashboard.api"
import { barPercent, dueLabel, fmtNum, percent, severityLabel, tidy, typeLabel } from "./agDashboard.logic"
import { todayISO } from "./agCrops.logic"
import { AG_ACCENT, AG_ACCENT_TEXT, btnGhost, card, kpiLabel, kpiValue, statusBadge } from "./constants"

const AgFarmMap = lazy(() => import("./AgFarmMap"))
const SLICES = ["var(--hf-accent)", "var(--hf-info)", "var(--hf-warning)", "var(--hf-violet)", "var(--hf-success)", "var(--hf-danger)"]
const title: React.CSSProperties = { fontSize: 13, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 12px" }
const muted: React.CSSProperties = { fontSize: 12, color: "var(--hf-text-muted)", margin: 0 }

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string | null }) {
  return (
    <div style={card}>
      <p style={kpiLabel}>{label}</p>
      <p style={kpiValue}>{value}</p>
      {sub && <p style={{ ...muted, marginTop: 4 }}>{sub}</p>}
    </div>
  )
}

function Bars({ rows, empty, unit }: { rows: { key: string; label: string; value: number; note?: string }[]; empty: string; unit: string }) {
  const max = Math.max(0, ...rows.map(r => r.value))
  if (rows.length === 0) return <p style={muted}>{empty}</p>
  return (
    <div style={{ display: "grid", gap: 10 }}>
      {rows.map(r => (
        <div key={r.key}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, marginBottom: 3 }}>
            <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{r.label}</span>
            <span style={{ color: "var(--hf-text-muted)" }}>{fmtNum(r.value)} {unit}{r.note ? ` · ${r.note}` : ""}</span>
          </div>
          <div style={{ height: 8, borderRadius: 4, background: "var(--hf-surface-sunken)" }}>
            <div role="presentation" style={{ height: 8, borderRadius: 4, background: AG_ACCENT, width: `${barPercent(r.value, max)}%` }} />
          </div>
        </div>
      ))}
    </div>
  )
}

function AttentionRow({ item, today }: { item: AttentionItem; today: string }) {
  const due = dueLabel(item.dueDate, today)
  return (
    <li style={{ display: "flex", gap: 10, alignItems: "flex-start", padding: "10px 0", borderTop: "1px solid var(--hf-border-subtle)" }}>
      <span style={{ ...statusBadge(item.severity), marginTop: 2, minWidth: 64, textAlign: "center" }}>{severityLabel(item.severity)}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>{item.title}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
          {[typeLabel(item.type), item.farmName, item.description].filter(Boolean).join(" · ")}
        </div>
      </div>
      {due && <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)", whiteSpace: "nowrap" }}>{due}</span>}
    </li>
  )
}

export default function AgDashboard() {
  const { data, isLoading, isError, refetch } = useAgDashboard()
  if (isLoading) return <p style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading dashboard…</p>
  if (isError || !data) {
    return (
      <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>
        We couldn't load the dashboard. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button>
      </p>
    )
  }
  const t = data.totals
  if (t.farmCount === 0) {
    return (
      <div style={{ textAlign: "center", padding: "40px 12px" }}>
        <Tractor size={30} style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />
        <p style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>No active farms yet</p>
        <p style={{ ...muted, marginBottom: 14 }}>Register a farm, then add production areas, animals and crop cycles to see them here.</p>
        <Link to="/agriculture/farms" style={{ ...btnGhost, textDecoration: "none" }}>Go to Farms</Link>
      </div>
    )
  }
  const today = data.asOf || todayISO()
  const inProductionShare = percent(t.hectaresInProduction, t.totalHectares)
  const hasLocations = data.farms.some(f => f.latitude != null && f.longitude != null)

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 16 }}>
        <Kpi label="Active farms" value={String(t.farmCount)} />
        <Kpi label="Total land" value={`${fmtNum(t.totalHectares)} ha`} sub={t.farmsWithoutHectares > 0 ? `${t.farmsWithoutHectares} farm${t.farmsWithoutHectares === 1 ? "" : "s"} with no size recorded` : null} />
        <Kpi label="In production" value={`${fmtNum(t.hectaresInProduction)} ha`} sub={inProductionShare != null ? `${inProductionShare}% of recorded land` : null} />
        <Kpi label="Crop cycles in production" value={String(t.cropCyclesInProduction)} sub={`${t.plannedCropCycles} planned`} />
        <Kpi label="Livestock" value={fmtNum(t.totalHead, 0)} sub={`${fmtNum(t.animalCount, 0)} animals · ${fmtNum(t.groupHead, 0)} in ${t.groupCount} group${t.groupCount === 1 ? "" : "s"}`} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 14, marginBottom: 14 }}>
        <section style={card} aria-label="Needs attention">
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 8, marginBottom: 4 }}>
            <h3 style={{ ...title, margin: 0 }}>Needs attention</h3>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {data.attention.bySeverity.map(s => <span key={s.severity} style={statusBadge(s.severity)}>{s.count} {severityLabel(s.severity).toLowerCase()}</span>)}
            </div>
          </div>
          {data.attention.items.length === 0 ? <p style={{ ...muted, padding: "14px 0" }}>Nothing needs attention across your farms.</p> : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>{data.attention.items.map(i => <AttentionRow key={`${i.type}-${i.referenceId}`} item={i} today={today} />)}</ul>
          )}
          {data.attention.total > data.attention.items.length && <p style={{ ...muted, marginTop: 8 }}>Showing the {data.attention.items.length} most urgent of {data.attention.total}.</p>}
        </section>

        <section style={card} aria-label="Farm types">
          <h3 style={title}>Farm types</h3>
          <div style={{ display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
            <PieChart width={120} height={120}>
              <Pie data={data.farmTypes} dataKey="farmCount" nameKey="farmType" innerRadius={38} outerRadius={56} stroke="none">
                {data.farmTypes.map((f, i) => <Cell key={f.farmType} fill={SLICES[i % SLICES.length]} />)}
              </Pie>
              <Tooltip formatter={(v, n) => [v, tidy(String(n))]} />
            </PieChart>
            <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 12.5, display: "grid", gap: 6 }}>
              {data.farmTypes.map((f, i) => (
                <li key={f.farmType}><span aria-hidden style={{ display: "inline-block", width: 9, height: 9, borderRadius: "50%", background: SLICES[i % SLICES.length], marginRight: 8 }} />
                  <strong>{tidy(f.farmType)}</strong> · {f.farmCount} farm{f.farmCount === 1 ? "" : "s"} · {fmtNum(f.hectares)} ha</li>
              ))}
            </ul>
          </div>
        </section>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 14, marginBottom: 14 }}>
        <section style={card} aria-label="Livestock by species">
          <h3 style={title}><Tractor size={14} style={{ verticalAlign: -2, marginRight: 6, color: AG_ACCENT_TEXT }} />Livestock by species</h3>
          <Bars unit="head" empty="No animals or groups registered yet." rows={data.livestock.map(s => ({ key: s.speciesId, label: s.name, value: s.totalHead, note: s.animals > 0 && s.groupHead > 0 ? `${fmtNum(s.animals, 0)} animals + ${fmtNum(s.groupHead, 0)} in groups` : undefined }))} />
        </section>
        <section style={card} aria-label="Crops in production">
          <h3 style={title}><Wheat size={14} style={{ verticalAlign: -2, marginRight: 6, color: AG_ACCENT_TEXT }} />Crops in production</h3>
          <Bars unit="ha" empty="No crops are planted, growing or being harvested." rows={data.crops.inProduction.map(c => ({ key: c.cropTypeId, label: c.cropName, value: c.hectares, note: `${c.cycles} cycle${c.cycles === 1 ? "" : "s"}` }))} />
        </section>
      </div>

      <section style={{ ...card, marginBottom: 14 }} aria-label="Farm locations">
        <h3 style={title}><MapPin size={14} style={{ verticalAlign: -2, marginRight: 6, color: AG_ACCENT_TEXT }} />Farm locations</h3>
        {hasLocations ? (
          <Suspense fallback={<p style={muted}>Loading map…</p>}><AgFarmMap farms={data.farms} /></Suspense>
        ) : <p style={muted}>No farm has a GPS position yet. Add coordinates to a farm to see it on the map.</p>}
      </section>

      <section style={{ ...card, overflowX: "auto" }} aria-label="Farms">
        <h3 style={title}><Sprout size={14} style={{ verticalAlign: -2, marginRight: 6, color: AG_ACCENT_TEXT }} />Farms</h3>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase", letterSpacing: 0.4 }}>
            {["Farm", "Type", "Land", "In production", "Livestock", "Attention", ""].map(h => <th key={h} style={{ padding: "8px 10px", fontWeight: 700 }}>{h}</th>)}
          </tr></thead>
          <tbody>
            {data.farms.map(f => (
              <tr key={f.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={{ padding: "10px", fontWeight: 700 }}>{f.name}{f.province ? <div style={{ fontSize: 11, color: "var(--hf-text-faint)", fontWeight: 400 }}>{f.province}</div> : null}</td>
                <td style={{ padding: "10px" }}>{f.farmType ? tidy(f.farmType) : "—"}</td>
                <td style={{ padding: "10px" }}>{f.totalHectares != null ? `${fmtNum(f.totalHectares)} ha` : "—"}</td>
                <td style={{ padding: "10px" }}>{fmtNum(f.hectaresInProduction)} ha · {f.cropCyclesInProduction} cycle{f.cropCyclesInProduction === 1 ? "" : "s"}</td>
                <td style={{ padding: "10px" }}>{fmtNum(f.animalCount + f.groupHead, 0)}</td>
                <td style={{ padding: "10px" }}>{f.attentionCount === 0 ? "—" : <span style={statusBadge(f.urgentCount > 0 ? "OVERDUE" : "UPCOMING")}>{f.attentionCount}{f.urgentCount > 0 ? ` · ${f.urgentCount} urgent` : ""}</span>}</td>
                <td style={{ padding: "10px", whiteSpace: "nowrap" }}>
                  <Link to={`/agriculture/crop-cycles?farm=${f.id}`} style={{ color: AG_ACCENT_TEXT, fontWeight: 600, marginRight: 12 }}>Crops</Link>
                  <Link to={`/agriculture/costs?farm=${f.id}`} style={{ color: AG_ACCENT_TEXT, fontWeight: 600 }}>Costs</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p style={{ ...muted, marginTop: 10 }}>Revenue, margin, labour and equipment cost, weather and trends are not tracked yet.</p>
      </section>
    </div>
  )
}
