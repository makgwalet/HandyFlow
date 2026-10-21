// src/pages/fleet/VehiclesTab.tsx
//
// The one screen for the fleet: every vehicle in a searchable, sortable table with status, driver, odometer, a fuel-efficiency bar, a service bar and
// a document badge, plus the things you do to a vehicle (register, assign a driver, change status). A row opens the Vehicle 360 panel underneath it.
// Fuel figures are the last 30 days from fuel analytics. This replaces the old separate "Fleet List" and "Vehicles" screens.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Plus, UserCog, Edit2 } from "lucide-react"
import { apiClient } from "../../api/client"
import VehicleDetail from "./VehicleDetail"
import { Meter } from "./FleetBars"
import { TONE_COLOUR } from "./fleetTones"
import { fetchAllPages } from "./apiShape"
import { periodRange, type FuelRow } from "./fuelAnalytics.logic"
import { fleetRows, fleetTiles, viewFleet, SORT_OPTIONS, type FleetSort, type ListVehicle } from "./fleetBoards.logic"
import { AssignDriverModal, RegisterVehicleModal, StatusBadge, StatusModal } from "./VehicleActions"
import { STATUS_CFG, daysUntil, fmtDate, fmtOdo, fmtR } from "./vehicleStatus"

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] } | null>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const th: React.CSSProperties = { textAlign: "left", padding: "6px 10px", fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 12.5, verticalAlign: "top" }
const iconButton: React.CSSProperties = { background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 6, padding: "5px 7px", cursor: "pointer", color: "var(--hf-text-muted)", display: "inline-flex" }
const STATUSES = Object.keys(STATUS_CFG)

export default function VehiclesTab() {
  const [search, setSearch] = useState(""), [status, setStatus] = useState("ALL"), [sort, setSort] = useState<FleetSort>("registration"), [openId, setOpenId] = useState<string | null>(null)
  const [registering, setRegistering] = useState(false), [changing, setChanging] = useState<ListVehicle | null>(null), [assigning, setAssigning] = useState<ListVehicle | null>(null)
  const { from, to } = periodRange("30")
  const vehicles = useQuery<ListVehicle[]>({ queryKey: ["fleet", "list", "vehicles"], queryFn: async () => (await fetchAllPages<ListVehicle>(u => apiClient.get(u), "/api/v1/fleet/vehicles")).items })
  const fuel = useQuery<FuelRow[]>({ queryKey: ["fleet", "list", "fuel", from, to], queryFn: async () => list<FuelRow>(await apiClient.get(`/api/v1/fleet/fuel-analytics/vehicles?from=${from}&to=${to}`)) })
  const rows = viewFleet(fleetRows(vehicles.data ?? [], fuel.data ?? []), search, status, sort)
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
        {fleetTiles(vehicles.data ?? []).map(t => (
          <div key={t.id} role="group" aria-label={t.label} style={{ flex: "1 1 120px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: t.tone === "none" ? "var(--hf-text)" : TONE_COLOUR[t.tone] }}>{t.value}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{t.label}</div>
          </div>))}
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12, alignItems: "center" }}>
        <input aria-label="Search fleet" placeholder="Search registration, make, model, driver, VIN or engine number" style={{ ...field, minWidth: 300 }} value={search} onChange={e => setSearch(e.target.value)} />
        <select aria-label="Status" style={field} value={status} onChange={e => setStatus(e.target.value)}><option value="ALL">All statuses</option>{STATUSES.map(k => <option key={k} value={k}>{STATUS_CFG[k].label}</option>)}</select>
        <select aria-label="Sort fleet" style={field} value={sort} onChange={e => setSort(e.target.value as FleetSort)}>{SORT_OPTIONS.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}</select>
        <span style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }} aria-label="Result count">{rows.length} of {vehicles.data?.length ?? 0} vehicles</span>
        <button type="button" onClick={() => setRegistering(true)} style={{ marginLeft: "auto", display: "inline-flex", alignItems: "center", gap: 6, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "8px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}><Plus size={14} /> Register vehicle</button>
      </div>
      {vehicles.isError && <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Couldn't load the fleet.</p>}
      {fuel.isError && <p role="alert" style={{ color: "var(--hf-warning-text)", fontSize: 12.5 }}>Fuel figures couldn't be loaded; the fuel bars show no data.</p>}
      {vehicles.isLoading ? <p>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>{(vehicles.data ?? []).length === 0 ? "No vehicles yet. Use Register vehicle to add the first one." : "No vehicles match."}</p> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Vehicle</th><th style={th}>Status</th><th style={th}>Driver</th><th style={th}>Odometer</th><th style={th}>Fuel, last 30 days</th><th style={th}>Service</th><th style={th}>Documents</th><th style={th}><span style={{ position: "absolute", left: -9999 }}>Actions</span></th></tr></thead>
            <tbody>{rows.flatMap(r => {
              const v = r.vehicle, open = openId === v.id, expiry = [["Licence disc", v.licenceDiscExpiry], ["Roadworthy", v.roadworthyExpiry], ["Insurance", v.insuranceExpiry]] as const
              const facts: [string, string][] = [["Type", v.vehicleType ?? "—"], ["Colour", v.colour ?? "—"], ["Fuel type", v.fuelType ?? "—"], ["Daily rate", fmtR(v.dailyRate)], ["Last service", fmtOdo(v.lastServiceKm ?? 0)], ["Service interval", fmtOdo(v.serviceIntervalKm ?? 10000)]]
              return [
                <tr key={v.id} aria-label={v.registration} aria-expanded={open} onClick={() => setOpenId(open ? null : v.id)} style={{ borderTop: "1px solid var(--hf-border-subtle)", cursor: "pointer", background: open ? "var(--hf-surface-muted)" : undefined }}>
                  <td style={td}><strong>{v.registration}</strong><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{[v.make, v.model, v.year].filter(Boolean).join(" ")}</div></td>
                  <td style={td}><StatusBadge status={v.status} /></td>
                  <td style={td}>{v.assignedDriverName ?? "—"}</td>
                  <td style={td}>{v.currentOdometer == null ? "—" : `${v.currentOdometer.toLocaleString("en-ZA")} km`}</td>
                  <td style={td}><Meter label="Fuel" {...r.fuel} /></td>
                  <td style={td}><Meter label="Service" {...r.service} /></td>
                  <td style={{ ...td, fontWeight: 700, color: TONE_COLOUR[r.docs.tone] }}>{r.docs.text}</td>
                  <td style={{ ...td, whiteSpace: "nowrap" }}>
                    <button type="button" style={iconButton} title="Assign driver" aria-label={`Assign driver to ${v.registration}`} onClick={e => { e.stopPropagation(); setAssigning(v) }}><UserCog size={13} /></button>{" "}
                    <button type="button" style={iconButton} title="Change status" aria-label={`Change status of ${v.registration}`} onClick={e => { e.stopPropagation(); setChanging(v) }}><Edit2 size={13} /></button>
                  </td>
                </tr>,
                ...(open ? [<tr key={`${v.id}-detail`}><td colSpan={8} style={{ padding: "8px 10px 16px" }}>
                  <dl aria-label={`${v.registration} details`} style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: 10, margin: "0 0 12px" }}>
                    {facts.map(([k, val]) => <div key={k}><dt style={{ fontSize: 10, fontWeight: 700, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{k}</dt><dd style={{ margin: 0, fontSize: 13, fontWeight: 600 }}>{val}</dd></div>)}
                    {expiry.filter(([, d]) => d).map(([k, d]) => <div key={k}><dt style={{ fontSize: 10, fontWeight: 700, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{k}</dt><dd style={{ margin: 0, fontSize: 13, fontWeight: 600, color: daysUntil(d) <= 30 ? "var(--hf-warning-text)" : undefined }}>{fmtDate(d)}</dd></div>)}
                  </dl>
                  {v.notes && <p style={{ margin: "0 0 12px", padding: "8px 12px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text-deep)" }}>{v.notes}</p>}
                  <VehicleDetail vehicleId={v.id} identity={v as never} />
                </td></tr>] : []),
              ]
            })}</tbody>
          </table>
        </div>)}
      {registering && <RegisterVehicleModal onClose={() => setRegistering(false)} />}
      {changing && <StatusModal vehicle={changing} onClose={() => setChanging(null)} />}
      {assigning && <AssignDriverModal vehicle={assigning} onClose={() => setAssigning(null)} />}
    </div>)
}
