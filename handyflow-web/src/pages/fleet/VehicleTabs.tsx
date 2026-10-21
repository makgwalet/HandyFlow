// src/pages/fleet/VehicleTabs.tsx
//
// The four read-mostly tabs on a vehicle: Location (where it is and where it has been), Maintenance (service due, work orders,
// tyres, batteries, service history), Fuel & Usage (consumption, cost per km, fill-ups, trips) and History (one merged timeline).
// Every figure comes from an endpoint the Fleet module already has; the rules are in vehicleTabs.logic.ts.
import { Suspense, lazy, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { LIVE_LABEL, ageText, hasFix, speedText, eventText, type LivePosition } from "./liveMap.logic"
import { TYPE_LABEL as ORDER_TYPE, STATUS_LABEL as ORDER_STATUS } from "./workOrders.logic"
import { TREAD_LABEL, treadTone, TEST_TONE, humanise } from "./tyres.logic"
import { consumptionText, costPerKmText, deviationText, deviationTone, periodRange } from "./fuelAnalytics.logic"
import { fetchAllPages } from "./apiShape"
import {
  TRACK_RANGES, trackWindow, trackLine, topSpeed, trackSummary, serviceDue, isOpenOrder, serviceSpend, SEGMENT_LABEL, segmentTone, INDICATOR_LABEL, indicatorTone,
  tripTotals, inRange, HISTORY_KINDS, buildTimeline, filterTimeline, kindCounts, type TrackRange, type Track, type Trip, type HistoryKind,
} from "./vehicleTabs.logic"

const FleetMap = lazy(() => import("./FleetMap"))

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] } | null>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const R = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA", { maximumFractionDigits: 2 })}`)
const km = (n: number | null | undefined) => (n == null ? "—" : `${Number(n).toLocaleString("en-ZA")} km`)
const day = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })
const TONE = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5 }
const th: React.CSSProperties = { textAlign: "left", padding: "4px 12px 4px 0", fontSize: 11.5, color: "var(--hf-text-muted)", fontWeight: 700 }
const td: React.CSSProperties = { padding: "4px 12px 4px 0", fontSize: 13 }
const muted: React.CSSProperties = { color: "var(--hf-text-muted)", fontSize: 12.5 }
const Heading = ({ children }: { children: React.ReactNode }) => <h4 style={{ margin: "0 0 8px", fontSize: 13.5 }}>{children}</h4>
const Stat = ({ label, value, tone }: { label: string; value: string; tone?: keyof typeof TONE }) => (
  <div aria-label={label} style={{ flex: "1 1 130px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 12px" }}>
    <div style={{ fontSize: 17, fontWeight: 800, color: tone ? TONE[tone] : "var(--hf-text)" }}>{value}</div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{label}</div></div>)

// ── shared queries ──────────────────────────────────────────────────────────
interface Service { id: string; type: string; description: string | null; serviceDate: string; odometerAtService: number | null; nextServiceKm: number | null; cost: number | null; supplier: string | null }
interface Order { id: string; referenceNumber: string; type: string; title: string; priority: string; status: string; assignedTechnicianName: string | null; createdAt: string; completedAt: string | null; actualTotal: number | null; quotedAmount: number | null }
interface Accident { id: string; referenceNumber: string; occurredAt: string; description: string; status: string; vehicleDrivable: boolean }
interface Inspection { id: string; inspectionType: string; status: string; verdict: string | null; failedCount: number; startedAt: string; completedAt: string | null }
interface LifecycleEvent { id: string; eventType: string; toStatus: string | null; occurredAt: string; reason: string | null }

const useServices = (id: string) => useQuery<Service[]>({ queryKey: ["fleet", "services", "vehicle", id], queryFn: async () => (await fetchAllPages<Service>(u => apiClient.get(u), `/api/v1/fleet/vehicles/${id}/services`)).items })
const useOrders = (id: string) => useQuery<Order[]>({ queryKey: ["fleet", "orders", "vehicle", id], queryFn: async () => (await fetchAllPages<Order>(u => apiClient.get(u), `/api/v1/fleet/work-orders?vehicleId=${id}`)).items })

// ── Location ────────────────────────────────────────────────────────────────
interface VehicleEvent { id: string; type: string; occurredAt: string; value: number | null; limitValue: number | null; durationSeconds: number | null; geofenceName: string | null; driverName: string | null; acknowledged: boolean }

export function LocationTab({ id }: { id: string }) {
  const [range, setRange] = useState<TrackRange>("24h")
  const live = useQuery<LivePosition[]>({ queryKey: ["fleet", "live"], refetchInterval: 30000, queryFn: async () => list<LivePosition>(await apiClient.get("/api/v1/fleet/telematics/live")) })
  const track = useQuery<Track>({ queryKey: ["fleet", "track", "vehicle", id, range], queryFn: async () => { const w = trackWindow(range); return unwrap<Track>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/telematics/track?from=${encodeURIComponent(w.from)}&to=${encodeURIComponent(w.to)}`)) } })
  const events = useQuery<VehicleEvent[]>({ queryKey: ["fleet", "events", "vehicle", id], queryFn: async () => list<VehicleEvent>(await apiClient.get(`/api/v1/fleet/telematics/events?vehicleId=${id}&size=10`)) })
  const pos = (live.data ?? []).find(p => p.vehicleId === id)
  const line = trackLine(track.data), top = topSpeed(track.data)
  return (
    <div>
      {live.isLoading ? <p>Loading position…</p> : !pos || !hasFix(pos) ? (
        <div style={card} role="status"><strong>No position yet.</strong><div style={muted}>This vehicle has not reported a location. Check that it has an active tracking device (Tracking Setup).</div></div>
      ) : (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }} aria-label="Current position">
          <Stat label="Status" value={LIVE_LABEL[pos.status] ?? pos.status} tone={pos.status === "MOVING" ? "ok" : pos.status === "OFFLINE" ? "none" : "warn"} />
          <Stat label="Speed" value={speedText(pos.speedKph)} />
          <Stat label="Last fix" value={ageText(pos.ageMinutes)} tone={pos.ageMinutes > 60 ? "warn" : "ok"} />
          <Stat label="Ignition" value={pos.ignitionOn == null ? "—" : pos.ignitionOn ? "On" : "Off"} />
          <Stat label="Coordinates" value={`${pos.latitude.toFixed(4)}, ${pos.longitude.toFixed(4)}`} />
        </div>)}
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 8, flexWrap: "wrap" }}>
        <label style={muted}>Route <select aria-label="Route period" value={range} onChange={e => setRange(e.target.value as TrackRange)} style={field}>{TRACK_RANGES.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}</select></label>
        <span style={muted} aria-label="Route summary">{track.isLoading ? "Loading route…" : track.isError ? "Could not load the route." : trackSummary(track.data)}{top != null ? ` · top speed ${Math.round(top)} km/h` : ""}</span>
      </div>
      {(pos && hasFix(pos)) || line.length > 1
        ? <Suspense fallback={<p>Loading map…</p>}><FleetMap positions={pos ? [pos] : []} selectedId={id} track={line} onSelect={() => undefined} /></Suspense>
        : null}
      <div style={{ ...card, marginTop: 12 }}>
        <Heading>Recent driving events</Heading>
        {events.isLoading ? <p>Loading…</p> : (events.data ?? []).length === 0 ? <p style={muted}>No driving events recorded.</p> : (
          <ul style={{ margin: 0, paddingLeft: 18 }}>{(events.data ?? []).map(e => (
            <li key={e.id} aria-label={eventText(e)} style={{ fontSize: 13, marginBottom: 3 }}>{eventText(e)} <span style={muted}>· {when(e.occurredAt)}{e.driverName ? ` · ${e.driverName}` : ""}{e.acknowledged ? " · acknowledged" : ""}</span></li>))}</ul>)}
      </div>
    </div>)
}

// ── Maintenance ─────────────────────────────────────────────────────────────
interface Fitted { id: string; position: string | null; serialNumber: string; brand: string; status: string }
interface TyreRow extends Fitted { size: string; lastTreadDepthMm: number | null; treadStatus: string }
interface BatteryRow extends Fitted { model: string | null; lastVoltage: number | null; lastTestResult: string | null; warrantyExpiry: string | null; inWarranty: boolean }
export interface ServiceFacts { currentOdometer?: number | null; lastServiceKm?: number | null; serviceIntervalKm?: number | null }

export function MaintenanceTab({ id, facts }: { id: string; facts: ServiceFacts }) {
  const services = useServices(id), orders = useOrders(id)
  const tyres = useQuery<TyreRow[]>({ queryKey: ["fleet", "tyres", "vehicle", id], queryFn: async () => list<TyreRow>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/tyres`)) })
  const batteries = useQuery<BatteryRow[]>({ queryKey: ["fleet", "batteries", "vehicle", id], queryFn: async () => list<BatteryRow>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/batteries`)) })
  const svc = services.data ?? [], open = (orders.data ?? []).filter(o => isOpenOrder(o.status))
  const latest = [...svc].sort((a, b) => b.serviceDate.localeCompare(a.serviceDate))[0]
  const due = serviceDue({ ...facts, latestNextServiceKm: latest?.nextServiceKm ?? null })
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
        <Stat label="Next service" value={due.remainingKm == null ? "—" : due.remainingKm < 0 ? "Overdue" : km(due.remainingKm)} tone={due.tone} />
        <Stat label="Odometer" value={km(facts.currentOdometer)} />
        <Stat label="Open work orders" value={String(open.length)} tone={open.length ? "warn" : "ok"} />
        <Stat label="Service spend, 12 months" value={R(serviceSpend(svc))} />
      </div>
      <p aria-label="Service due" style={{ ...muted, marginTop: 0, color: TONE[due.tone] }}>{due.text}</p>

      <div style={card}><Heading>Open work orders</Heading>
        {orders.isLoading ? <p>Loading…</p> : open.length === 0 ? <p style={muted}>Nothing open for this vehicle.</p> : (
          <table><thead><tr><th style={th}>Ref</th><th style={th}>Job</th><th style={th}>Status</th><th style={th}>Technician</th></tr></thead><tbody>{open.map(o => (
            <tr key={o.id} aria-label={o.referenceNumber}><td style={td}>{o.referenceNumber}</td><td style={td}>{o.title} <span style={muted}>({ORDER_TYPE[o.type] ?? humanise(o.type)})</span></td>
              <td style={{ ...td, fontWeight: 700, color: o.priority === "URGENT" || o.priority === "HIGH" ? TONE.warn : "var(--hf-text)" }}>{ORDER_STATUS[o.status] ?? o.status}</td><td style={td}>{o.assignedTechnicianName ?? "Unassigned"}</td></tr>))}</tbody></table>)}</div>

      <div style={card}><Heading>Tyres</Heading>
        {tyres.isLoading ? <p>Loading…</p> : (tyres.data ?? []).length === 0 ? <p style={muted}>No tyres fitted to this vehicle.</p> : (
          <table><thead><tr><th style={th}>Position</th><th style={th}>Tyre</th><th style={th}>Tread</th><th style={th}>Condition</th></tr></thead><tbody>{(tyres.data ?? []).map(t => (
            <tr key={t.id} aria-label={`Tyre ${t.position ?? t.serialNumber}`}><td style={td}>{t.position ?? "—"}</td><td style={td}>{t.brand} {t.size} <span style={muted}>{t.serialNumber}</span></td>
              <td style={td}>{t.lastTreadDepthMm == null ? "—" : `${t.lastTreadDepthMm} mm`}</td><td style={{ ...td, fontWeight: 700, color: TONE[treadTone(t.treadStatus)] }}>{TREAD_LABEL[t.treadStatus] ?? t.treadStatus}</td></tr>))}</tbody></table>)}</div>

      <div style={card}><Heading>Batteries</Heading>
        {batteries.isLoading ? <p>Loading…</p> : (batteries.data ?? []).length === 0 ? <p style={muted}>No battery installed on this vehicle.</p> : (
          <table><thead><tr><th style={th}>Position</th><th style={th}>Battery</th><th style={th}>Last test</th><th style={th}>Warranty</th></tr></thead><tbody>{(batteries.data ?? []).map(b => (
            <tr key={b.id} aria-label={`Battery ${b.position ?? b.serialNumber}`}><td style={td}>{b.position ?? "—"}</td><td style={td}>{b.brand} {b.model ?? ""} <span style={muted}>{b.serialNumber}</span></td>
              <td style={{ ...td, fontWeight: 700, color: b.lastTestResult ? TONE[TEST_TONE[b.lastTestResult] ?? "none"] : TONE.none }}>{b.lastTestResult ? `${humanise(b.lastTestResult)}${b.lastVoltage != null ? ` · ${b.lastVoltage} V` : ""}` : "Never tested"}</td>
              <td style={td}>{b.warrantyExpiry ? `${b.inWarranty ? "Until" : "Expired"} ${day(b.warrantyExpiry)}` : "—"}</td></tr>))}</tbody></table>)}</div>

      <div style={card}><Heading>Service history</Heading>
        {services.isLoading ? <p>Loading…</p> : svc.length === 0 ? <p style={muted}>No services recorded.</p> : (
          <table><thead><tr><th style={th}>Date</th><th style={th}>Service</th><th style={th}>Odometer</th><th style={th}>Supplier</th><th style={th}>Cost</th></tr></thead><tbody>{svc.map(s => (
            <tr key={s.id} aria-label={`Service ${day(s.serviceDate)}`}><td style={td}>{day(s.serviceDate)}</td><td style={td}>{humanise(s.type)}{s.description ? <span style={muted}> · {s.description}</span> : null}</td>
              <td style={td}>{km(s.odometerAtService)}</td><td style={td}>{s.supplier ?? "—"}</td><td style={td}>{R(s.cost)}</td></tr>))}</tbody></table>)}</div>
    </div>)
}

// ── Fuel & usage ────────────────────────────────────────────────────────────
interface Analytics {
  summary: { fillups: number; totalLitres: number; totalSpend: number; measuredKm: number; litresPer100Km: number | null; kmPerLitre: number | null; costPerKm: number | null; flaggedSegments: number }
  settings: { tolerancePercent: number }
  segments: { startDate: string; endDate: string; km: number; litres: number; litresPer100Km: number | null; deviationPercent: number | null; status: string }[]
  indicators: { type: string; severity: string; date: string; message: string; fillupId: string }[]
}
interface CostSummary { totalServiceCost: number; totalFuelCost: number; totalCost: number; totalKm: number; costPerKm: number | null }
interface Fillup { id: string; filledAt: string; litres: number; totalCost: number | null; odometerAtFillup: number | null; station: string | null; fullTank: boolean }

export function FuelUsageTab({ id }: { id: string }) {
  const [period, setPeriod] = useState<"month" | "30" | "90">("30")
  const { from, to } = periodRange(period)
  const a = useQuery<Analytics>({ queryKey: ["fleet", "fuel-analytics", "vehicle", id, period], queryFn: async () => unwrap<Analytics>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/fuel-analytics?from=${from}&to=${to}`)) })
  const cost = useQuery<CostSummary>({ queryKey: ["fleet", "cost-summary", id], queryFn: async () => unwrap<CostSummary>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/cost-summary`)) })
  const fills = useQuery<Fillup[]>({ queryKey: ["fleet", "fuel", "vehicle", id], queryFn: async () => list<Fillup>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/fuel?size=50`)) })
  const trips = useQuery<Trip[]>({ queryKey: ["fleet", "trips", "vehicle", id], queryFn: async () => (await fetchAllPages<Trip>(u => apiClient.get(u), `/api/v1/fleet/vehicles/${id}/trips`)).items })
  const s = a.data?.summary, tol = a.data?.settings.tolerancePercent ?? 20
  const periodTrips = tripTotals((trips.data ?? []).filter(t => inRange(t.startAt, from, to)))
  const lastTrips = (trips.data ?? []).slice(0, 8)
  return (
    <div>
      <label style={muted}>Period <select aria-label="Fuel period" value={period} onChange={e => setPeriod(e.target.value as typeof period)} style={field}>
        <option value="month">This month</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option></select></label>
      {a.isError && <p role="alert" style={{ color: TONE.bad }}>Could not load the fuel figures.</p>}
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", margin: "10px 0 12px" }}>
        <Stat label="Consumption" value={consumptionText(s?.litresPer100Km)} />
        <Stat label="Fuel cost per km" value={costPerKmText(s?.costPerKm)} />
        <Stat label="Fuel spend" value={R(s?.totalSpend)} />
        <Stat label="Litres" value={s ? s.totalLitres.toLocaleString("en-ZA", { maximumFractionDigits: 1 }) : "—"} />
        <Stat label="Flagged fills" value={String(s?.flaggedSegments ?? 0)} tone={s?.flaggedSegments ? "bad" : "ok"} />
        <Stat label="Whole-life cost per km" value={costPerKmText(cost.data?.costPerKm)} />
      </div>
      {cost.data && <p aria-label="Whole-life cost" style={{ ...muted, marginTop: 0 }}>Whole life: {R(cost.data.totalFuelCost)} fuel + {R(cost.data.totalServiceCost)} service = {R(cost.data.totalCost)} over {km(cost.data.totalKm)}.</p>}

      {(a.data?.indicators ?? []).length > 0 && <div style={card} aria-label="Fuel warnings"><Heading>Fuel warnings</Heading><ul style={{ margin: 0, paddingLeft: 18 }}>{(a.data?.indicators ?? []).map((i, n) => (
        <li key={`${i.fillupId}${n}`} style={{ fontSize: 13, marginBottom: 3 }}><strong style={{ color: TONE[indicatorTone(i.severity)] }}>{INDICATOR_LABEL[i.type] ?? humanise(i.type)}</strong> <span style={muted}>· {day(i.date)} · {i.message}</span></li>))}</ul></div>}

      <div style={card}><Heading>Tank to tank</Heading>
        {(a.data?.segments ?? []).length === 0 ? <p style={muted}>No full-tank to full-tank stretches in this period.</p> : (
          <table><thead><tr><th style={th}>Dates</th><th style={th}>Distance</th><th style={th}>Litres</th><th style={th}>L/100 km</th><th style={th}>Against usual</th></tr></thead><tbody>{(a.data?.segments ?? []).slice(0, 8).map(g => (
            <tr key={g.endDate + g.startDate} aria-label={`Stretch ending ${day(g.endDate)}`}><td style={td}>{day(g.startDate)} – {day(g.endDate)}</td><td style={td}>{km(g.km)}</td><td style={td}>{g.litres.toFixed(1)}</td><td style={td}>{g.litresPer100Km?.toFixed(1) ?? "—"}</td>
              <td style={{ ...td, fontWeight: 700, color: TONE[g.status === "HIGH_CONSUMPTION" ? "bad" : g.deviationPercent != null ? deviationTone(g.deviationPercent, tol) : segmentTone(g.status)] }}>{g.deviationPercent != null ? deviationText(g.deviationPercent) : SEGMENT_LABEL[g.status] ?? g.status}</td></tr>))}</tbody></table>)}</div>

      <div style={card}><Heading>Recent fill-ups</Heading>
        {(fills.data ?? []).length === 0 ? <p style={muted}>No fill-ups recorded.</p> : (
          <table><thead><tr><th style={th}>Date</th><th style={th}>Litres</th><th style={th}>Cost</th><th style={th}>Odometer</th><th style={th}>Station</th></tr></thead><tbody>{(fills.data ?? []).slice(0, 10).map(f => (
            <tr key={f.id} aria-label={`Fill-up ${day(f.filledAt)}`}><td style={td}>{day(f.filledAt)}{f.fullTank ? "" : " (part)"}</td><td style={td}>{Number(f.litres).toFixed(1)}</td><td style={td}>{R(f.totalCost)}</td><td style={td}>{km(f.odometerAtFillup)}</td><td style={td}>{f.station ?? "—"}</td></tr>))}</tbody></table>)}</div>

      <div style={card}><Heading>Usage</Heading>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 8 }}>
          <Stat label="Trips" value={String(periodTrips.count)} /><Stat label="Business km" value={km(periodTrips.businessKm)} /><Stat label="Private km" value={km(periodTrips.privateKm)} />
          <Stat label="Business share" value={periodTrips.businessPercent == null ? "—" : `${periodTrips.businessPercent}%`} /></div>
        {lastTrips.length === 0 ? <p style={muted}>No trips recorded.</p> : (
          <table><thead><tr><th style={th}>Started</th><th style={th}>Route</th><th style={th}>Km</th><th style={th}>Type</th><th style={th}>Driver</th></tr></thead><tbody>{lastTrips.map(t => (
            <tr key={t.id} aria-label={`Trip ${t.startAt ? day(t.startAt) : t.id}`}><td style={td}>{t.startAt ? day(t.startAt) : "—"}</td><td style={td}>{t.startLocation ?? "—"} → {t.endLocation ?? (t.status === "ACTIVE" ? "under way" : "—")}</td>
              <td style={td}>{t.distanceKm ?? "—"}</td><td style={td}>{humanise(t.tripType)}</td><td style={td}>{t.driverName ?? "—"}</td></tr>))}</tbody></table>)}</div>
    </div>)
}

// ── History ────────────────────────────────────────────────────────────────
export function HistoryTab({ id }: { id: string }) {
  const [kinds, setKinds] = useState<HistoryKind[]>([])
  const services = useServices(id), orders = useOrders(id)
  const lifecycle = useQuery<{ events: LifecycleEvent[] }>({ queryKey: ["fleet", "lifecycle", "history", id], queryFn: async () => unwrap<{ events: LifecycleEvent[] }>(await apiClient.get(`/api/v1/fleet/vehicles/${id}/lifecycle`)) })
  const accidents = useQuery<Accident[]>({ queryKey: ["fleet", "accidents", "vehicle", id], queryFn: async () => list<Accident>(await apiClient.get(`/api/v1/fleet/accidents?vehicleId=${id}`)) })
  const inspections = useQuery<Inspection[]>({ queryKey: ["fleet", "inspections", "vehicle", id], queryFn: async () => (await fetchAllPages<Inspection>(u => apiClient.get(u), `/api/v1/fleet/vehicles/${id}/inspections`)).items })
  const loading = [services, orders, lifecycle, accidents, inspections].some(q => q.isLoading)
  const all = buildTimeline({ lifecycle: lifecycle.data?.events, services: services.data, orders: orders.data, accidents: accidents.data, inspections: inspections.data })
  const shown = filterTimeline(all, kinds)
  const toggle = (k: HistoryKind) => setKinds(cur => (cur.includes(k) ? cur.filter(x => x !== k) : [...cur, k]))
  return (
    <div>
      <div role="group" aria-label="Show" style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 10 }}>
        {kindCounts(all).map(k => (
          <button key={k.id} type="button" aria-pressed={kinds.includes(k.id)} onClick={() => toggle(k.id)}
            style={{ ...field, cursor: "pointer", fontWeight: kinds.includes(k.id) ? 800 : 500, background: kinds.includes(k.id) ? "var(--hf-primary)" : "var(--hf-surface)", color: kinds.includes(k.id) ? "var(--hf-text-on-solid)" : "var(--hf-text)" }}>
            {HISTORY_KINDS.find(x => x.id === k.id)?.label} ({k.count})</button>))}
        {kinds.length > 0 && <button type="button" style={{ ...field, cursor: "pointer" }} onClick={() => setKinds([])}>Show all</button>}
      </div>
      {loading ? <p>Loading history…</p> : shown.length === 0 ? <p style={muted}>{all.length === 0 ? "Nothing has been recorded for this vehicle yet." : "Nothing of that kind."}</p> : (
        <ol style={{ listStyle: "none", margin: 0, padding: 0, borderLeft: "2px solid var(--hf-border)" }}>{shown.map(i => (
          <li key={i.key} aria-label={i.title} style={{ position: "relative", padding: "0 0 12px 14px" }}>
            <span style={{ position: "absolute", left: -6, top: 4, width: 10, height: 10, borderRadius: "50%", background: TONE[i.tone] }} />
            <div style={{ fontSize: 13, fontWeight: 700 }}>{i.title}</div>
            <div style={muted}>{day(i.at)} · {HISTORY_KINDS.find(k => k.id === i.kind)?.label}{i.detail ? ` · ${i.detail}` : ""}</div></li>))}</ol>)}
    </div>)
}
