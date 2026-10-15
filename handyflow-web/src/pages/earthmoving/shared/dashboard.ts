// Small calculations behind the dashboard panels, kept apart so they can be tested.
import type { Asset, MaintenancePlan } from "./types"

/** Machines per status, in the order the dashboard draws them. Retired machines are left out. */
export const STATUS_ORDER = ["AVAILABLE", "DEPLOYED", "HIRED_OUT", "MAINTENANCE", "BREAKDOWN"] as const

export function statusCounts(assets: Pick<Asset, "status">[]): { status: string; count: number }[] {
  return STATUS_ORDER.map(status => ({ status, count: assets.filter(a => a.status === status).length })).filter(r => r.count > 0)
}

const RANK: Record<string, number> = { OVERDUE: 0, DUE: 1, DUE_SOON: 2 }

/** Active plans that are due soon, due or overdue — the most urgent first. Plans that are fine, or switched off, are not listed. */
export function upcomingPlans(plans: MaintenancePlan[], limit = 6): MaintenancePlan[] {
  return plans
    .filter(p => p.active && p.state in RANK)
    .sort((a, b) => RANK[a.state] - RANK[b.state] || a.remaining - b.remaining)
    .slice(0, limit)
}

/** "3 days left", "120 hrs overdue" — remaining is negative once a plan is past due. */
export function remainingText(p: Pick<MaintenancePlan, "remaining" | "unit">): string {
  const n = Math.abs(Math.round(p.remaining * 10) / 10)
  return p.remaining < 0 ? `${n} ${p.unit} overdue` : `${n} ${p.unit} left`
}
