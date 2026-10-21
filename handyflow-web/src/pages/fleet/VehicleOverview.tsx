// src/pages/fleet/VehicleOverview.tsx
// The Overview tab of a vehicle: key information, the last 30 days of fuel as a chart, then the full identity and specification.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { TONE_COLOUR } from "./fleetTones"
import { fuelLastDays, keyFacts, type Fill, type KeyFacts } from "./vehicleOverview.logic"
import { identityRows, type Identity } from "./vehicleDetail.logic"
import { consumptionText } from "./fuelAnalytics.logic"
import { periodRange } from "./fuelAnalytics.logic"
import { unwrapList } from "./apiShape"

interface Analytics { summary: { litresPer100Km: number | null } }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 14px", background: "var(--hf-surface)" }
const label: React.CSSProperties = { fontSize: 10, fontWeight: 700, textTransform: "uppercase", color: "var(--hf-text-faint)" }
const unwrapOne = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }

function FuelUsageCard({ vehicleId }: { vehicleId: string }) {
  const { from, to } = periodRange("30")
  // The log is newest first, so one page of 100 covers 30 days for any one vehicle.
  const fills = useQuery<Fill[]>({ queryKey: ["fleet", "fuel", "vehicle-30d", vehicleId], queryFn: async () => unwrapList<Fill>(await apiClient.get(`/api/v1/fleet/vehicles/${vehicleId}/fuel?size=100`)) })
  const analytics = useQuery<Analytics>({ queryKey: ["fleet", "fuel-analytics", "vehicle", vehicleId, "30"], queryFn: async () => unwrapOne<Analytics>(await apiClient.get(`/api/v1/fleet/vehicles/${vehicleId}/fuel-analytics?from=${from}&to=${to}`)) })
  const f = fuelLastDays(fills.data ?? [])
  const tile = (name: string, value: string) => <div key={name} aria-label={name}><div style={label}>{name}</div><div style={{ fontSize: 16, fontWeight: 700 }}>{value}</div></div>
  return (
    <section aria-label="Fuel and usage" style={card}>
      <div style={{ fontWeight: 700, marginBottom: 8 }}>Fuel &amp; usage <span style={{ fontWeight: 400, color: "var(--hf-text-muted)", fontSize: 12 }}>(last 30 days)</span></div>
      {fills.isError ? <p role="alert" style={{ margin: 0, fontSize: 12.5, color: TONE_COLOUR.bad }}>Couldn't load the fuel figures.</p> : <>
        <div style={{ display: "flex", gap: 22, flexWrap: "wrap", marginBottom: 10 }}>
          {tile("Total fuel", `${f.litres.toLocaleString("en-ZA", { maximumFractionDigits: 1 })} L`)}
          {tile("Fuel cost", `R ${Math.round(f.cost).toLocaleString("en-ZA")}`)}
          {tile("Avg consumption", consumptionText(analytics.data?.summary.litresPer100Km))}
        </div>
        {f.fillups === 0 ? <p role="status" style={{ margin: 0, fontSize: 12.5, color: "var(--hf-text-muted)" }}>No fill-ups in the last 30 days.</p> : (
          <div role="img" aria-label={`Litres per day over the last 30 days, ${f.fillups} fill-ups`} style={{ display: "flex", alignItems: "flex-end", gap: 2, height: 70 }}>
            {f.bars.map(b => <div key={b.date} title={`${b.date}: ${b.litres.toLocaleString("en-ZA", { maximumFractionDigits: 1 })} L`} data-litres={b.litres} style={{ flex: 1, height: `${Math.max(b.fraction * 100, b.litres > 0 ? 6 : 2)}%`, background: b.litres > 0 ? "var(--hf-brand)" : "var(--hf-surface-sunken)", borderRadius: 2 }} />)}
          </div>)}
      </>}
    </section>)
}

export default function VehicleOverview({ vehicleId, vehicle }: { vehicleId: string; vehicle: KeyFacts & Identity }) {
  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 10, marginBottom: 14 }}>
        <section aria-label="Key information" style={card}>
          <div style={{ fontWeight: 700, marginBottom: 8 }}>Key information</div>
          <dl style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: "4px 10px", margin: 0, fontSize: 13 }}>
            {keyFacts(vehicle).map(r => <div key={r.label} style={{ display: "contents" }}><dt style={{ color: "var(--hf-text-faint)" }}>{r.label}</dt><dd style={{ margin: 0, fontWeight: 600, color: r.missing ? "var(--hf-warning-text)" : undefined }}>{r.value}</dd></div>)}
          </dl>
        </section>
        <FuelUsageCard vehicleId={vehicleId} />
      </div>
      <div style={{ fontWeight: 700, marginBottom: 6 }}>Identity &amp; specification</div>
      <dl style={{ display: "grid", gridTemplateColumns: "170px 1fr", gap: "5px 12px", fontSize: 13, margin: 0 }}>
        {identityRows(vehicle).map(r => <div key={r.label} style={{ display: "contents" }}><dt style={{ color: "var(--hf-text-faint)" }}>{r.label}</dt><dd style={{ margin: 0, color: r.missing ? "var(--hf-warning-text)" : undefined }}>{r.value}</dd></div>)}
      </dl>
    </div>)
}
