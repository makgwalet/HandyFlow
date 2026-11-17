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

/** The server's figures for the month and the open deliveries (GET /api/v1/fuel/dashboard-summary), limited to the tanks the user may see. */
export interface NextDelivery { id: string; status: string; fuelType: string; litresOrdered: number | null; scheduledAt: string | null }
export interface DashboardSummary {
  today: string; monthLitres: number; monthDispatches: number; monthReceipts: number; dailyLitres: number[]; negativeVarianceReadings: number
  openDeliveries: number; inTransitDeliveries: number; overdueDeliveries: number; nextDeliveries: NextDelivery[]
}

export interface DashboardInput { tanks: any[]; summary: DashboardSummary | null }

const num = (v: unknown) => { const n = Number(v ?? 0); return Number.isFinite(n) ? n : 0 }

/** The reply is an object with these numbers; anything else (an error body, an empty reply) is treated as not loaded. */
export function asSummary(v: unknown): DashboardSummary | null {
  const o = v as any
  if (!o || Array.isArray(o) || typeof o.monthDispatches !== "number" || !Array.isArray(o.dailyLitres)) return null
  return { ...o, nextDeliveries: Array.isArray(o.nextDeliveries) ? o.nextDeliveries : [] } as DashboardSummary
}

export function buildDashboard({ tanks, summary }: DashboardInput) {
  const tankViews: TankView[] = tanks.map(t => {
    const capacity = num(t.capacityLitres), litres = num(t.currentLitres)
    const percent = t.fillPercentage != null ? num(t.fillPercentage) : capacity > 0 ? (litres / capacity) * 100 : 0
    return { id: String(t.id), name: String(t.name ?? "Tank"), fuelType: String(t.fuelType ?? "OTHER"), location: t.location || null,
      litres, capacity, available: Math.max(0, capacity - litres), percent: Math.min(100, Math.max(0, percent)), low: !!t.low }
  })
  const totalStock = tankViews.reduce((s, t) => s + t.litres, 0)
  const capacity = tankViews.reduce((s, t) => s + t.capacity, 0)
  const lowTanks = tankViews.filter(t => t.low)

  const s = summary
  const monthLitres = num(s?.monthLitres), monthDispatchCount = s?.monthDispatches ?? 0, monthReceipts = s?.monthReceipts ?? 0
  const dailyLitres = (s?.dailyLitres ?? []).map(num)
  const pendingDeliveries = s?.nextDeliveries ?? []
  const openDeliveries = s?.openDeliveries ?? 0, inTransit = s?.inTransitDeliveries ?? 0
  const negative = s?.negativeVarianceReadings ?? 0, overdue = s?.overdueDeliveries ?? 0

  const alerts: AlertItem[] = []
  for (const t of lowTanks) alerts.push({ key: `low-${t.id}`, title: `${t.name} is low`, detail: `${fmtLitres(t.litres)} left (${t.percent.toFixed(0)}%)`, section: "tanks", severity: "high" })
  if (negative > 0) alerts.push({ key: "variance", title: `${negative} negative variance reading${negative === 1 ? "" : "s"}`, detail: "Dip readings this month more than 5 L below the book figure. Review reconciliation.", section: "tanks", severity: "high" })
  if (overdue > 0) alerts.push({ key: "overdue", title: overdue === 1 ? "1 delivery is overdue" : `${overdue} deliveries are overdue`, detail: "Scheduled more than a day ago and not yet on the road.", section: "deliveries", severity: "medium" })

  return {
    tanks: tankViews, totalStock, capacity, stockPercent: capacity > 0 ? (totalStock / capacity) * 100 : 0, lowTanks,
    monthLitres, monthDispatchCount, monthReceipts, dailyLitres,
    pendingDeliveries, openDeliveries, inTransit, alerts,
  }
}
