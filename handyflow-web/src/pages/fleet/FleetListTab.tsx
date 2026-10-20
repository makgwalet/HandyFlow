// src/pages/fleet/FleetListTab.tsx
//
// The whole fleet on one searchable table: status, driver, odometer, a fuel-efficiency bar, a service bar and a document badge per vehicle.
// A row opens the vehicle panel underneath it. Fuel figures are the last 30 days from fuel analytics.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import VehicleDetail from "./VehicleDetail"
import { Meter } from "./FleetBars"
import { TONE_COLOUR } from "./fleetTones"
import { periodRange, type FuelRow } from "./fuelAnalytics.logic"
import { fleetRows, viewFleet, SORT_OPTIONS, type FleetSort, type ListVehicle } from "./fleetBoards.logic"

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] } | null>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const STATUS_LABEL: Record<string, string> = { AVAILABLE: "Available", ON_TRIP: "On trip", MAINTENANCE: "Maintenance", BREAKDOWN: "Breakdown", RETIRED: "Retired" }
const STATUS_TONE: Record<string, keyof typeof TONE_COLOUR> = { AVAILABLE: "ok", ON_TRIP: "none", MAINTENANCE: "warn", BREAKDOWN: "bad", RETIRED: "none" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const th: React.CSSProperties = { textAlign: "left", padding: "6px 10px", fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 12.5, verticalAlign: "top" }

export default function FleetListTab() {
  const [search, setSearch] = useState(""), [status, setStatus] = useState("ALL"), [sort, setSort] = useState<FleetSort>("registration"), [openId, setOpenId] = useState<string | null>(null)
  const { from, to } = periodRange("30")
  const vehicles = useQuery<ListVehicle[]>({ queryKey: ["fleet", "list", "vehicles"], queryFn: async () => list<ListVehicle>(await apiClient.get("/api/v1/fleet/vehicles?size=200")) })
  const fuel = useQuery<FuelRow[]>({ queryKey: ["fleet", "list", "fuel", from, to], queryFn: async () => list<FuelRow>(await apiClient.get(`/api/v1/fleet/fuel-analytics/vehicles?from=${from}&to=${to}`)) })
  const rows = viewFleet(fleetRows(vehicles.data ?? [], fuel.data ?? []), search, status, sort)
  return (
    <div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
        <input aria-label="Search fleet" placeholder="Search registration, make, model or driver" style={{ ...field, minWidth: 280 }} value={search} onChange={e => setSearch(e.target.value)} />
        <select aria-label="Status" style={field} value={status} onChange={e => setStatus(e.target.value)}><option value="ALL">All statuses</option>{Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        <select aria-label="Sort fleet" style={field} value={sort} onChange={e => setSort(e.target.value as FleetSort)}>{SORT_OPTIONS.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}</select>
        <span style={{ alignSelf: "center", fontSize: 12.5, color: "var(--hf-text-muted)" }} aria-label="Result count">{rows.length} of {vehicles.data?.length ?? 0} vehicles</span>
      </div>
      {vehicles.isError && <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Couldn't load the fleet.</p>}
      {fuel.isError && <p role="alert" style={{ color: "var(--hf-warning-text)", fontSize: 12.5 }}>Fuel figures couldn't be loaded; the fuel bars show no data.</p>}
      {vehicles.isLoading ? <p>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>{(vehicles.data ?? []).length === 0 ? "No vehicles yet." : "No vehicles match."}</p> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Vehicle</th><th style={th}>Status</th><th style={th}>Driver</th><th style={th}>Odometer</th><th style={th}>Fuel, last 30 days</th><th style={th}>Service</th><th style={th}>Documents</th></tr></thead>
            <tbody>{rows.flatMap(r => {
              const v = r.vehicle, open = openId === v.id
              return [
                <tr key={v.id} aria-label={v.registration} aria-expanded={open} onClick={() => setOpenId(open ? null : v.id)} style={{ borderTop: "1px solid var(--hf-border-subtle)", cursor: "pointer", background: open ? "var(--hf-surface-muted)" : undefined }}>
                  <td style={td}><strong>{v.registration}</strong><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{[v.make, v.model, v.year].filter(Boolean).join(" ")}</div></td>
                  <td style={{ ...td, fontWeight: 700, color: TONE_COLOUR[STATUS_TONE[v.status] ?? "none"] }}>{STATUS_LABEL[v.status] ?? v.status}</td>
                  <td style={td}>{v.assignedDriverName ?? "—"}</td>
                  <td style={td}>{v.currentOdometer == null ? "—" : `${v.currentOdometer.toLocaleString("en-ZA")} km`}</td>
                  <td style={td}><Meter label="Fuel" {...r.fuel} /></td>
                  <td style={td}><Meter label="Service" {...r.service} /></td>
                  <td style={{ ...td, fontWeight: 700, color: TONE_COLOUR[r.docs.tone] }}>{r.docs.text}</td>
                </tr>,
                ...(open ? [<tr key={`${v.id}-detail`}><td colSpan={7} style={{ padding: "8px 10px 16px" }}><VehicleDetail vehicleId={v.id} identity={v as never} /></td></tr>] : []),
              ]
            })}</tbody>
          </table>
        </div>)}
    </div>)
}
