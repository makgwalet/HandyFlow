// src/pages/fleet/MaintenanceBoard.tsx
//
// Maintenance at a glance: what is due, what is in the workshop, what it has cost, and which tyres and batteries need attention.
// Every tile and list opens the section where the work is done.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BarRow, Columns, Meter } from "./FleetBars"
import { TONE_COLOUR, type Tone } from "./fleetTones"
import { STATUS_LABEL as ORDER_STATUS } from "./workOrders.logic"
import { TREAD_LABEL } from "./tyres.logic"
import { orderSummary, serviceQueue, spendByMonth, statusBars, tyreAttention, batteryAttention, type BoardOrder, type ListVehicle, type TyreItem, type BatteryItem } from "./fleetBoards.logic"
import { fetchAllPages } from "./apiShape"

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const list = <T,>(r: { data: unknown }): T[] => { const p = unwrap<T[] | { content?: T[] } | null>(r); return (Array.isArray(p) ? p : p?.content) ?? [] }
const R = (n: number) => `R ${Math.round(n).toLocaleString("en-ZA")}`
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14 }
const muted: React.CSSProperties = { color: "var(--hf-text-muted)", fontSize: 12.5 }
const link: React.CSSProperties = { background: "none", border: "none", padding: 0, color: "var(--hf-primary-text)", fontSize: 12, fontWeight: 600, cursor: "pointer" }

function Tile({ label, value, tone, to, go }: { label: string; value: string; tone?: Tone; to: string; go?: (s: string) => void }) {
  return (
    <button type="button" aria-label={label} onClick={() => go?.(to)} style={{ flex: "1 1 150px", textAlign: "left", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px", cursor: go ? "pointer" : "default" }}>
      <div style={{ fontSize: 22, fontWeight: 800, color: tone ? TONE_COLOUR[tone] : "var(--hf-text)" }}>{value}</div><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{label}</div></button>)
}

export default function MaintenanceBoard({ onNavigate }: { onNavigate?: (section: string) => void }) {
  const vehicles = useQuery<ListVehicle[]>({ queryKey: ["fleet", "list", "vehicles"], queryFn: async () => (await fetchAllPages<ListVehicle>(u => apiClient.get(u), "/api/v1/fleet/vehicles")).items })
  const orders = useQuery<BoardOrder[]>({ queryKey: ["fleet", "board", "orders"], queryFn: async () => (await fetchAllPages<BoardOrder>(u => apiClient.get(u), "/api/v1/fleet/work-orders")).items })
  const tyres = useQuery<TyreItem[]>({ queryKey: ["fleet", "board", "tyres"], queryFn: async () => list<TyreItem>(await apiClient.get("/api/v1/fleet/tyres?status=FITTED")) })
  const batteries = useQuery<BatteryItem[]>({ queryKey: ["fleet", "board", "batteries"], queryFn: async () => list<BatteryItem>(await apiClient.get("/api/v1/fleet/batteries?status=INSTALLED")) })
  const o = orders.data ?? [], sum = orderSummary(o), queue = serviceQueue(vehicles.data ?? []), months = spendByMonth(o)
  const overdueServices = queue.filter(q => q.remainingKm < 0).length, tyreList = tyreAttention(tyres.data ?? []), batList = batteryAttention(batteries.data ?? [])
  const failed = [vehicles, orders, tyres, batteries].some(q => q.isError)
  return (
    <div>
      {failed && <p role="alert" style={{ color: "var(--hf-warning-text)", fontSize: 12.5 }}>Some figures couldn't be loaded; the tiles and lists below may be incomplete.</p>}
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
        <Tile label="Open work orders" value={String(sum.open)} tone={sum.open ? "warn" : "ok"} to="workshop" go={onNavigate} />
        <Tile label="Awaiting approval" value={String(sum.awaitingApproval)} tone={sum.awaitingApproval ? "warn" : "ok"} to="workshop" go={onNavigate} />
        <Tile label="Past scheduled date" value={String(sum.overdue)} tone={sum.overdue ? "bad" : "ok"} to="workshop" go={onNavigate} />
        <Tile label="Services overdue" value={String(overdueServices)} tone={overdueServices ? "bad" : "ok"} to="vehicles" go={onNavigate} />
        <Tile label="Average days to complete (90 days)" value={sum.avgDaysToComplete == null ? "—" : String(sum.avgDaysToComplete)} to="workshop" go={onNavigate} />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 12 }}>
        <div style={card}><div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}><strong style={{ fontSize: 13 }}>Service queue</strong><button type="button" style={link} onClick={() => onNavigate?.("vehicles")}>Vehicles</button></div>
          {vehicles.isLoading ? <p>Loading…</p> : queue.length === 0 ? <p style={muted}>No vehicle is overdue or within 2 000 km of its service.</p> : queue.map(q => (
            <div key={q.vehicle.id} aria-label={q.vehicle.registration} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, marginBottom: 8 }}>
              <span style={{ fontSize: 12.5 }}><strong>{q.vehicle.registration}</strong><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{[q.vehicle.make, q.vehicle.model].filter(Boolean).join(" ")}</div></span>
              <Meter label="Service" {...q.bar} /></div>))}</div>

        <div style={card}><div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}><strong style={{ fontSize: 13 }}>Work in the workshop</strong><button type="button" style={link} onClick={() => onNavigate?.("workshop")}>Workshop</button></div>
          {orders.isLoading ? <p>Loading…</p> : sum.open === 0 ? <p style={muted}>Nothing open.</p> : statusBars(o).map(b => <BarRow key={b.status} name={ORDER_STATUS[b.status] ?? b.status} fraction={b.fraction} valueText={String(b.count)} tone={b.status === "ON_HOLD" ? "warn" : "ok"} />)}</div>

        <div style={card}><strong style={{ fontSize: 13 }}>Completed work, last 6 months</strong><div style={{ marginTop: 10 }}>
          {orders.isLoading ? <p>Loading…</p> : <Columns label="Maintenance spend by month" items={months} format={R} />}</div></div>

        <div style={card}><div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}><strong style={{ fontSize: 13 }}>Tyres to replace</strong><button type="button" style={link} onClick={() => onNavigate?.("tyres")}>Tyres & Batteries</button></div>
          {tyres.isLoading ? <p>Loading…</p> : tyreList.length === 0 ? <p style={muted}>No fitted tyre is at or near its limit.</p> : <ul style={{ margin: 0, paddingLeft: 18 }}>{tyreList.map(t => (
            <li key={t.id} aria-label={`Tyre ${t.registration ?? ""} ${t.position ?? ""}`.trim()} style={{ fontSize: 12.5, marginBottom: 3 }}><strong>{t.registration ?? "Unfitted"}</strong> {t.position ?? ""} <span style={muted}>· {t.brand} {t.size} · {t.lastTreadDepthMm ?? "?"} mm</span> <strong style={{ color: TONE_COLOUR[t.treadStatus === "REPLACE_NOW" ? "bad" : "warn"] }}>{TREAD_LABEL[t.treadStatus]}</strong></li>))}</ul>}</div>

        <div style={card}><div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}><strong style={{ fontSize: 13 }}>Batteries to check</strong><button type="button" style={link} onClick={() => onNavigate?.("tyres")}>Tyres & Batteries</button></div>
          {batteries.isLoading ? <p>Loading…</p> : batList.length === 0 ? <p style={muted}>No installed battery has failed or tested weak.</p> : <ul style={{ margin: 0, paddingLeft: 18 }}>{batList.map(b => (
            <li key={b.id} aria-label={`Battery ${b.registration ?? ""} ${b.position ?? ""}`.trim()} style={{ fontSize: 12.5, marginBottom: 3 }}><strong>{b.registration ?? "Uninstalled"}</strong> {b.position ?? ""} <span style={muted}>· {b.brand}{b.lastVoltage != null ? ` · ${b.lastVoltage} V` : ""}</span> <strong style={{ color: TONE_COLOUR[b.lastTestResult === "WEAK" ? "warn" : "bad"] }}>{b.status === "FAULTY" ? "Faulty" : b.lastTestResult === "WEAK" ? "Weak" : "Failed"}</strong></li>))}</ul>}</div>
      </div>
    </div>)
}
