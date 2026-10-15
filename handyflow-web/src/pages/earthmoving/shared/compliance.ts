// Service compliance: how many of each machine's active service plans are in order right now.
import type { Asset, MaintenancePlan } from "./types"

export interface ComplianceRow {
  assetId: string; machine: string; plans: number; ok: number; dueSoon: number; due: number; overdue: number
  /** Plans not yet due or overdue, as a percentage of all plans; 100 when the machine has no plans is NOT assumed: such machines are left out. */
  inOrderPercent: number
}

const label = (a: Pick<Asset, "name" | "fleetNumber">) => (a.fleetNumber ? `${a.fleetNumber} — ${a.name}` : a.name)

/** Worst machines first. Retired machines and machines without an active plan are not listed. */
export function complianceRows(assets: Pick<Asset, "id" | "name" | "fleetNumber" | "status">[], plans: MaintenancePlan[]): ComplianceRow[] {
  const rows: ComplianceRow[] = []
  for (const a of assets) {
    if (a.status === "RETIRED") continue
    const mine = plans.filter(p => p.assetId === a.id && p.active)
    if (mine.length === 0) continue
    const count = (s: string) => mine.filter(p => p.state === s).length
    const ok = count("OK"), dueSoon = count("DUE_SOON"), due = count("DUE"), overdue = count("OVERDUE")
    rows.push({ assetId: a.id, machine: label(a), plans: mine.length, ok, dueSoon, due, overdue,
      inOrderPercent: Math.round(((ok + dueSoon) / mine.length) * 1000) / 10 })
  }
  return rows.sort((x, y) => x.inOrderPercent - y.inOrderPercent || y.overdue - x.overdue || x.machine.localeCompare(y.machine))
}

export function complianceTotals(rows: ComplianceRow[]) {
  const plans = rows.reduce((n, r) => n + r.plans, 0)
  const inOrder = rows.reduce((n, r) => n + r.ok + r.dueSoon, 0)
  return { machines: rows.length, plans, overdue: rows.reduce((n, r) => n + r.overdue, 0), due: rows.reduce((n, r) => n + r.due, 0),
    inOrderPercent: plans === 0 ? null : Math.round((inOrder / plans) * 1000) / 10 }
}
