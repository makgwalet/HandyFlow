// Fuel fleet map: where each tanker was last seen, with today's load for it. Positions come from Fleet tracking; without Fleet access
// the tab still lists the tankers and loads and says why there is no map.
import { Suspense, lazy, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { LIVE_COLOUR, LIVE_LABEL, fixText, speedText, type LivePosition } from "../fleet/liveMap.logic"
import { EmptyState, Panel, StatusPill } from "./fuelUi"
import { fmtL } from "./orderOps"
import type { Plan, Tanker } from "./transportOps"
import { STATE_TEXT, drawable, joinTankers } from "./fleetMapOps"

const FleetMap = lazy(() => import("../fleet/FleetMap"))
const unwrap = (r: any) => r.data?.data ?? r.data
const listOf = (r: any): LivePosition[] => { const p = unwrap(r); return Array.isArray(p) ? p : p?.content ?? [] }
const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })
const time = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

export default function FleetMapTab() {
  const [selected, setSelected] = useState<string | null>(null)
  const date = today()
  const tankers = useQuery<Tanker[]>({ queryKey: ["fuel-tankers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tankers")) })
  const plan = useQuery<Plan>({ queryKey: ["fuel-plan", date], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/planning", { params: { date } })) })
  const live = useQuery<LivePosition[]>({ queryKey: ["fleet", "live"], retry: false, refetchInterval: 30000, queryFn: async () => listOf(await apiClient.get("/api/v1/fleet/telematics/live")) })

  if (tankers.isLoading || plan.isLoading) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  if (tankers.isError || !tankers.data) return <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load the tankers.</p>
  const rows = joinTankers(tankers.data, live.data ?? [], plan.data?.loads ?? [])
  const points = drawable(rows)
  const noFleet = live.isError
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>
        Where each tanker was last seen, with its load for today. Positions come from Fleet tracking and refresh every 30 seconds. This page only reports; it changes nothing.
      </p>
      {noFleet && <div role="status" style={{ padding: "10px 14px", borderRadius: 8, background: "var(--hf-warning-bg)", color: "var(--hf-warning-text)", fontSize: 13 }}>
        Positions are not available. Tracking needs Fleet access, or Fleet is not switched on for this company. The tankers and loads are listed below.</div>}
      {plan.isError && <p role="alert" style={{ color: "var(--hf-danger-text)", margin: 0 }}>Could not load today's loads.</p>}
      {rows.length === 0 ? <EmptyState title="No tankers yet" hint="Add tankers under Planning." /> : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16, alignItems: "start" }}>
          {!noFleet && (points.length > 0
            ? <Suspense fallback={<p>Loading map…</p>}><FleetMap positions={points} selectedId={selected} track={[]} onSelect={setSelected} /></Suspense>
            : <EmptyState title="No tanker positions yet" hint="A tanker shows on the map once its Fleet vehicle has a tracking device that has sent a position." />)}
          <Panel title={`Tankers (${rows.length})`} pad={false}>
            <ul style={{ listStyle: "none", margin: 0, padding: 8, display: "flex", flexDirection: "column", gap: 6, maxHeight: 460, overflow: "auto" }}>
              {rows.map(r => {
                const t = r.tanker, on = selected === t.id, p = r.position
                return (
                  <li key={t.id}>
                    <button type="button" aria-label={`Show ${t.registration}`} aria-pressed={on} disabled={r.state !== "TRACKED"} onClick={() => setSelected(s => (s === t.id ? null : t.id))}
                      style={{ width: "100%", textAlign: "left", padding: "8px 10px", borderRadius: 8, border: `1px solid ${on ? "var(--hf-brand)" : "var(--hf-border)"}`, background: "var(--hf-surface)", cursor: r.state === "TRACKED" ? "pointer" : "default", color: "var(--hf-text)" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                        {p && r.state === "TRACKED" && <span aria-hidden="true" style={{ width: 9, height: 9, borderRadius: 99, background: LIVE_COLOUR[p.status] }} />}
                        <strong>{t.registration}</strong>
                        <span style={{ marginLeft: "auto", fontSize: 11.5, color: "var(--hf-text-muted)" }}>{fmtL(t.totalCapacity)}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                        {r.state === "TRACKED" && p ? `${LIVE_LABEL[p.status]} · ${speedText(p.speedKph)} · ${fixText(p)}` : STATE_TEXT[r.state]}
                      </div>
                      <div style={{ fontSize: 12, marginTop: 2, display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap" }}>
                        {r.current
                          ? <><StatusPill status={r.current.status} /><span>{r.current.customerName ?? "Stock transfer"} · {fmtL(r.current.litres)} {r.current.fuelType} · {time(r.current.scheduledAt)}</span></>
                          : <span style={{ color: "var(--hf-text-muted)" }}>{r.loads.length === 0 ? "No loads today" : "No open loads"}</span>}
                      </div>
                      {r.loads.length > 1 && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{r.loads.length} loads today · {fmtL(r.litresToday)}</div>}
                    </button>
                  </li>)
              })}
            </ul>
          </Panel>
        </div>)}
    </div>
  )
}
