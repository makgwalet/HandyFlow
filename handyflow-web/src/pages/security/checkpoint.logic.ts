// src/pages/security/checkpoint.logic.ts
//
// Pure rules for the Checkpoints screen: filters, summary numbers, and the warning shown before a checkpoint that patrol
// routes depend on is switched off. No React, no network.
import { checkpointState } from "./site.logic"

export interface CheckpointRow {
  id: string; siteId: string; siteName: string; name: string; description: string | null; active: boolean
  hasNfc: boolean; hasBle: boolean; siteRequiresSignedQr: boolean
  scans30d: number; lastScanAt: string | null; activeRoutes: number
}

/** An active checkpoint with no scan in 30 days (never scanned counts): the sticker may be damaged, moved or skipped. */
export const isQuiet = (c: CheckpointRow) => c.active && checkpointState(c).tone === "warn"

export function filterCheckpoints(list: CheckpointRow[], f: { search: string; quietOnly: boolean }): CheckpointRow[] {
  const q = f.search.trim().toLowerCase()
  return list.filter(c => (!f.quietOnly || isQuiet(c)) && (!q || c.name.toLowerCase().includes(q) || c.siteName.toLowerCase().includes(q)))
}

export function summarise(list: CheckpointRow[]) {
  const active = list.filter(c => c.active)
  return {
    total: list.length, active: active.length, inactive: list.length - active.length,
    quiet: active.filter(isQuiet).length, onRoutes: active.filter(c => c.activeRoutes > 0).length,
    unsignedSites: new Set(active.filter(c => !c.siteRequiresSignedQr).map(c => c.siteId)).size,
  }
}

/** What to tell an administrator before they deactivate. Null when nothing depends on the checkpoint. */
export function deactivateWarning(c: CheckpointRow): string | null {
  if (!c.active) return null
  return c.activeRoutes > 0
    ? `${c.name} is on ${c.activeRoutes} active patrol route${c.activeRoutes === 1 ? "" : "s"}. Once it is switched off it cannot be scanned, so rounds on those routes that are still open stop waiting for it and later rounds leave it out. Switching it back on puts it back.`
    : `${c.name} will no longer be scannable. Its scan history is kept.`
}

/** The scan methods a checkpoint can be verified with. QR always exists; NFC and Bluetooth only once an identifier is set. */
export function methods(c: Pick<CheckpointRow, "hasNfc" | "hasBle">): string[] {
  return ["QR", ...(c.hasNfc ? ["NFC"] : []), ...(c.hasBle ? ["Bluetooth"] : [])]
}

export const groupBySite = (list: CheckpointRow[]): { siteId: string; siteName: string; items: CheckpointRow[] }[] => {
  const out = new Map<string, { siteId: string; siteName: string; items: CheckpointRow[] }>()
  for (const c of list) { const g = out.get(c.siteId) ?? { siteId: c.siteId, siteName: c.siteName, items: [] }; g.items.push(c); out.set(c.siteId, g) }
  return [...out.values()]
}
