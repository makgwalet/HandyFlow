// src/pages/fuel/dashboardOps.ts
//
// Pure figures for the Fuel dashboard, kept out of the component so they can be tested.
import type { Tone } from "./fuelUi"

export const DELIVERY_STEPS = ["Scheduled", "In transit", "Delivered"]

/** Index of the step a delivery is on; a finished one returns the step count so every step shows ticked. */
export function deliveryStep(status: string | null | undefined): number {
  switch ((status ?? "").toUpperCase()) {
    case "SCHEDULED": return 0
    case "IN_TRANSIT": return 1
    case "DELIVERED": return DELIVERY_STEPS.length
    default: return 0
  }
}

export const FUEL_TONE: Record<string, Tone> = { DIESEL: "blue", PETROL: "violet", PARAFFIN: "green", GAS: "slate", OTHER: "slate" }

export const fmtLitres = (n: number) => `${Math.round(n).toLocaleString("en-ZA")}\u00a0L`
export const fmtShortDate = (iso: string | null | undefined) => {
  if (!iso) return "–"
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? "–" : d.toLocaleDateString("en-ZA", { day: "numeric", month: "short" })
}

export interface TankView { id: string; name: string; fuelType: string; location: string | null; litres: number; capacity: number; available: number; percent: number; low: boolean }
export interface AlertItem { key: string; title: string; detail: string; section: string; severity: "high" | "medium" }

export interface DashboardInput { tanks: any[]; dispatches: any[]; deliveries: any[]; receipts: any[]; now: Date }

const num = (v: unknown) => { const n = Number(v ?? 0); return Number.isFinite(n) ? n : 0 }
const monthKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`

export function buildDashboard({ tanks, dispatches, deliveries, receipts, now }: DashboardInput) {
  const tankViews: TankView[] = tanks.map(t => {
    const capacity = num(t.capacityLitres), litres = num(t.currentLitres)
    const percent = t.fillPercentage != null ? num(t.fillPercentage) : capacity > 0 ? (litres / capacity) * 100 : 0
    return { id: String(t.id), name: String(t.name ?? "Tank"), fuelType: String(t.fuelType ?? "OTHER"), location: t.location || null,
      litres, capacity, available: Math.max(0, capacity - litres), percent: Math.min(100, Math.max(0, percent)), low: !!t.low }
  })
  const totalStock = tankViews.reduce((s, t) => s + t.litres, 0)
  const capacity = tankViews.reduce((s, t) => s + t.capacity, 0)
  const lowTanks = tankViews.filter(t => t.low)

  // The month is matched on the local calendar, not on the UTC date string, so a late-night dispatch lands in the right month.
  const mk = monthKey(now)
  const inMonth = (iso: unknown) => { if (!iso) return false; const d = new Date(String(iso)); return !Number.isNaN(d.getTime()) && monthKey(d) === mk }
  const monthDispatches = dispatches.filter(d => inMonth(d.dispatchedAt))
  const monthLitres = monthDispatches.reduce((s, d) => s + num(d.litresDispensed), 0)
  const monthReceipts = receipts.filter(r => inMonth(r.receivedAt)).length

  // Litres per day up to today, for the sparkline.
  const dailyLitres = Array.from({ length: now.getDate() }, () => 0)
  for (const d of monthDispatches) {
    const day = new Date(String(d.dispatchedAt)).getDate()
    if (day >= 1 && day <= dailyLitres.length) dailyLitres[day - 1] += num(d.litresDispensed)
  }

  const pendingDeliveries = deliveries
    .filter(d => d.status === "SCHEDULED" || d.status === "IN_TRANSIT")
    .sort((a, b) => String(a.scheduledAt ?? "").localeCompare(String(b.scheduledAt ?? "")))
  const inTransit = pendingDeliveries.filter(d => d.status === "IN_TRANSIT").length

  const alerts: AlertItem[] = []
  for (const t of lowTanks) alerts.push({ key: `low-${t.id}`, title: `${t.name} is low`, detail: `${fmtLitres(t.litres)} left (${t.percent.toFixed(0)}%)`, section: "tanks", severity: "high" })
  const neg = dispatches.filter(d => num(d.varianceLitres) < -5)
  if (neg.length > 0) alerts.push({ key: "variance", title: `${neg.length} negative variance reading${neg.length === 1 ? "" : "s"}`, detail: "Possible leak or theft. Review reconciliation.", section: "dispatches", severity: "high" })
  const overdue = pendingDeliveries.filter(d => d.status === "SCHEDULED" && d.scheduledAt && new Date(d.scheduledAt).getTime() < now.getTime() - 24 * 3600_000)
  if (overdue.length > 0) alerts.push({ key: "overdue", title: `${overdue.length} delivery${overdue.length === 1 ? " is" : "ies are"} overdue`, detail: "Scheduled more than a day ago and not yet on the road.", section: "deliveries", severity: "medium" })

  return {
    tanks: tankViews, totalStock, capacity, stockPercent: capacity > 0 ? (totalStock / capacity) * 100 : 0, lowTanks,
    monthLitres, monthDispatchCount: monthDispatches.length, monthReceipts, dailyLitres,
    pendingDeliveries, inTransit, alerts,
  }
}
