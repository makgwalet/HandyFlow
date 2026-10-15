// Search, sort and page the fleet list. The whole fleet is already loaded, so this is done in the browser.
import type { Asset } from "./types"

export const PAGE_SIZE = 25
export type SortKey = "fleetNumber" | "name" | "assetType" | "status" | "currentHours" | "dailyRate"
export type SortDir = "asc" | "desc"

/** Matches the words typed against fleet number, name, make, model, serial number, registration, site and client. All words must match. */
export function searchAssets<T extends Partial<Asset>>(assets: T[], query: string): T[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean)
  if (words.length === 0) return assets
  return assets.filter(a => {
    const hay = [a.fleetNumber, a.name, a.make, a.model, a.serialNumber, a.registration, a.currentSite, a.currentClient, a.assetType]
      .filter(Boolean).join(" ").toLowerCase()
    return words.every(w => hay.includes(w))
  })
}

/** Text sorts ignoring case, numbers as numbers; a missing value always goes last whichever way it is sorted. */
export function sortAssets<T extends Partial<Asset>>(assets: T[], key: SortKey, dir: SortDir): T[] {
  const sign = dir === "asc" ? 1 : -1
  return [...assets].sort((a, b) => {
    const x = a[key] as string | number | null | undefined, y = b[key] as string | number | null | undefined
    const xm = x == null || x === "", ym = y == null || y === ""
    if (xm || ym) return xm && ym ? 0 : xm ? 1 : -1
    if (typeof x === "number" && typeof y === "number") return (x - y) * sign
    return String(x).localeCompare(String(y), undefined, { numeric: true, sensitivity: "base" }) * sign
  })
}

export function pageOf<T>(items: T[], page: number, size = PAGE_SIZE) {
  const pages = Math.max(1, Math.ceil(items.length / size))
  const current = Math.min(Math.max(0, page), pages - 1)
  return { items: items.slice(current * size, current * size + size), page: current, pages, from: items.length === 0 ? 0 : current * size + 1, to: Math.min(items.length, current * size + size) }
}
