// Tanker compartment view: for one tanker and day, each compartment as a block filled to what is planned in it, with what it may
// carry, what it last carried and when it was last cleaned. Read-only; changes are made under Planning and Site & tanker rules.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inputStyle } from "../warehousing/whseUi"
import { EmptyState, Panel, ProgressBar } from "./fuelUi"
import { fmtL } from "./orderOps"
import type { CompartmentRating } from "./eligibilityOps"
import type { Tanker } from "./transportOps"
import { layOut, problems, totalCapacity, totalPlanned, type CompartmentCell, type CompartmentLoad } from "./compartmentOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const todayZA = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })
const day = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "never recorded")
const hhmm = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

function Block({ c }: { c: CompartmentCell }) {
  const warn = c.over || c.mixed || c.notRated.length > 0
  return (
    <div role="group" aria-label={`Compartment ${c.rating.number}`} style={{ flex: "1 1 150px", minWidth: 150, border: `1px solid ${warn ? "var(--hf-warning-border)" : "var(--hf-border)"}`, background: "var(--hf-surface)", borderRadius: 10, padding: 10, display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <strong>Compartment {c.rating.number}</strong><span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{fmtL(c.rating.capacityLitres)}</span>
      </div>
      <ProgressBar percent={c.percent} tone={c.over ? "red" : c.litres > 0 ? "blue" : "slate"} />
      <div style={{ fontSize: 12.5 }}>{c.litres > 0 ? `${fmtL(c.litres)} planned${c.fuels.length ? ` · ${c.fuels.join(", ")}` : ""}` : "Empty"}</div>
      {c.loads.map(l => <div key={l.deliveryId + l.compartmentId} style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{hhmm(l.scheduledAt)} · {l.customerName ?? "Stock transfer"} · {fmtL(l.litres)}</div>)}
      <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", borderTop: "1px solid var(--hf-border)", paddingTop: 6 }}>
        <div>Rated for: {c.rating.fuelTypes.length ? c.rating.fuelTypes.join(", ") : "any fuel (not restricted)"}</div>
        <div>Last carried: {c.rating.lastCargo ? `${c.rating.lastCargo}, ${day(c.rating.lastCargoAt)}` : "nothing recorded"}</div>
        <div>Last cleaned: {day(c.rating.lastCleanedAt)}</div>
      </div>
    </div>
  )
}

export default function CompartmentsTab() {
  const [tankerId, setTankerId] = useState(""), [date, setDate] = useState(todayZA())
  const tankers = useQuery<Tanker[]>({ queryKey: ["fuel-tankers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tankers")) })
  const id = tankerId || tankers.data?.find(t => t.active)?.id || ""
  const ratings = useQuery<CompartmentRating[]>({ queryKey: ["fuel-compartment-ratings", id], enabled: !!id, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/tankers/${id}/compartment-ratings`)) ?? [] })
  const loads = useQuery<CompartmentLoad[]>({ queryKey: ["fuel-compartment-loads", id, date], enabled: !!id, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/tankers/${id}/compartment-loads`, { params: { date } })) ?? [] })

  if (tankers.isLoading) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  if (tankers.isError) return <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load the tankers.</p>
  const active = (tankers.data ?? []).filter(t => t.active)
  if (active.length === 0) return <EmptyState title="No tankers yet" hint="Add tankers under Planning." />
  const cells = layOut(ratings.data ?? [], loads.data ?? [])
  const issues = problems(cells)
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          Each compartment filled to what is planned in it for the day. Litres are as planned when the load was allocated. This page only reports; change loads under Planning and ratings under Site & tanker rules.
        </p>
        <select aria-label="Tanker" value={id} onChange={e => setTankerId(e.target.value)} style={{ ...inputStyle, width: "auto" }}>
          {active.map(t => <option key={t.id} value={t.id}>{t.registration}{t.name ? ` (${t.name})` : ""}</option>)}
        </select>
        <input aria-label="Day" type="date" value={date} onChange={e => e.target.value && setDate(e.target.value)} style={{ ...inputStyle, width: "auto" }} />
      </div>
      {(ratings.isError || loads.isError) && <p role="alert" style={{ color: "var(--hf-danger-text)", margin: 0 }}>Could not load this tanker's compartments.</p>}
      {issues.length > 0 && <ul role="status" aria-label="Problems with the plan" style={{ margin: 0, paddingLeft: 18, fontSize: 13, color: "var(--hf-warning-text-deep)" }}>{issues.map(i => <li key={i}>{i}</li>)}</ul>}
      {cells.length === 0 ? (ratings.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : <EmptyState title="This tanker has no compartments" />) : (
        <Panel title={`${fmtL(totalPlanned(cells))} planned of ${fmtL(totalCapacity(cells))}`}>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>{cells.map(c => <Block key={c.rating.compartmentId} c={c} />)}</div>
        </Panel>)}
    </div>
  )
}
