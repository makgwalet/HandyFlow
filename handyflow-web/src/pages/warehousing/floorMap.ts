// Pure helpers for the warehouse floor map: grouping locations into map cells, colour bands and the racking generator form.

export interface FloorLocation {
  id: string; code: string; zone: string | null; aisle: string | null; bay: number | null; level: number | null
  locationType: string; pickSequence: number | null; posX: number | null; posY: number | null
  capacityUnits: number | null; active: boolean
  onHand: number; allocated: number; held: number; skuCount: number; earliestExpiry: string | null; utilisationPct: number | null
}

export interface Cell {
  x: number; y: number; locations: FloorLocation[]
  onHand: number; held: number; allocated: number; skuCount: number
  utilisationPct: number | null; earliestExpiry: string | null; active: boolean; zone: string | null
}

export type Band = "inactive" | "empty" | "stocked" | "low" | "mid" | "high" | "over"

/** Groups placed locations into cells (levels of one bay share a cell), levels bottom to top. Locations without a position are left out. */
export function groupCells(locs: FloorLocation[], zone?: string | null): Cell[] {
  const map = new Map<string, Cell>()
  for (const l of locs) {
    if (l.posX == null || l.posY == null) continue
    if (zone && l.zone !== zone) continue
    const key = `${l.posX}:${l.posY}`
    let c = map.get(key)
    if (!c) {
      c = { x: l.posX, y: l.posY, locations: [], onHand: 0, held: 0, allocated: 0, skuCount: 0, utilisationPct: null, earliestExpiry: null, active: false, zone: l.zone }
      map.set(key, c)
    }
    c.locations.push(l)
  }
  const cells = [...map.values()]
  for (const c of cells) {
    c.locations.sort((a, b) => (a.level ?? 0) - (b.level ?? 0) || a.code.localeCompare(b.code))
    let capped = 0, cappedStock = 0
    for (const l of c.locations) {
      c.onHand += l.onHand; c.held += l.held; c.allocated += l.allocated; c.skuCount += l.skuCount
      if (l.active) c.active = true
      if (l.earliestExpiry && (!c.earliestExpiry || l.earliestExpiry < c.earliestExpiry)) c.earliestExpiry = l.earliestExpiry
      if (l.capacityUnits != null && l.capacityUnits > 0) { capped += l.capacityUnits; cappedStock += l.onHand }
    }
    c.utilisationPct = capped > 0 ? Math.round((cappedStock / capped) * 1000) / 10 : null
  }
  return cells.sort((a, b) => a.y - b.y || a.x - b.x)
}

export function bounds(cells: Cell[]): { minX: number; maxX: number; minY: number; maxY: number } | null {
  if (cells.length === 0) return null
  return {
    minX: Math.min(...cells.map(c => c.x)), maxX: Math.max(...cells.map(c => c.x)),
    minY: Math.min(...cells.map(c => c.y)), maxY: Math.max(...cells.map(c => c.y)),
  }
}

export function band(c: Cell): Band {
  if (!c.active) return "inactive"
  if (c.onHand <= 0) return "empty"
  if (c.utilisationPct == null) return "stocked"
  if (c.utilisationPct > 100) return "over"
  if (c.utilisationPct >= 85) return "high"
  if (c.utilisationPct >= 50) return "mid"
  return "low"
}

/** True when the date is within `days` of today (or already past). Dates are yyyy-mm-dd. */
export function expiresSoon(expiry: string | null, today: string, days = 30): boolean {
  if (!expiry) return false
  const ms = Date.parse(expiry + "T00:00:00Z") - Date.parse(today + "T00:00:00Z")
  return ms / 86_400_000 <= days
}

/** "A, B C" -> [A, B, C]; "A-D" -> [A, B, C, D] for single letters. Upper-cased, duplicates dropped. */
export function parseAisles(text: string): string[] {
  const out: string[] = []
  for (const part of text.split(/[\s,;]+/).map(p => p.trim().toUpperCase()).filter(Boolean)) {
    const m = /^([A-Z])-([A-Z])$/.exec(part)
    if (m) {
      const a = m[1].charCodeAt(0), b = m[2].charCodeAt(0)
      if (b < a) continue
      for (let c = a; c <= b; c++) out.push(String.fromCharCode(c))
    } else out.push(part)
  }
  return [...new Set(out)]
}

export interface GenerateForm { zone: string; aisles: string; bays: string; levels: string; locationType: string; originX: string; originY: string; aisleSpacing: string; capacityUnits: string }

/** Returns an error message, or null when the form can be sent. The server checks again. */
export function validateGenerate(f: GenerateForm): string | null {
  const aisles = parseAisles(f.aisles)
  if (aisles.length === 0) return "Give at least one aisle, for example A-D or A, B, C."
  if (aisles.some(a => !/^[A-Z0-9]{1,6}$/.test(a))) return "Aisle names are 1 to 6 letters or digits."
  const bays = Number(f.bays), levels = Number(f.levels)
  if (!Number.isInteger(bays) || bays < 1 || bays > 99) return "Bays per aisle must be between 1 and 99."
  if (!Number.isInteger(levels) || levels < 1 || levels > 10) return "Levels must be between 1 and 10."
  if (aisles.length * bays * levels > 2000) return "That is more than 2000 locations in one go. Generate it in parts."
  for (const [v, name] of [[f.originX, "Start column"], [f.originY, "Start row"]] as const) {
    const n = Number(v); if (!Number.isInteger(n) || n < 0) return `${name} must be 0 or more.`
  }
  const sp = Number(f.aisleSpacing)
  if (!Number.isInteger(sp) || sp < 1 || sp > 10) return "Aisle spacing must be between 1 and 10 rows."
  if (f.capacityUnits.trim() !== "" && !(Number(f.capacityUnits.replace(",", ".")) >= 0)) return "Capacity must be 0 or more."
  return null
}

export function buildGeneratePayload(f: GenerateForm) {
  return {
    zone: f.zone.trim() || null, aisles: parseAisles(f.aisles), bays: Number(f.bays), levels: Number(f.levels), locationType: f.locationType,
    originX: Number(f.originX), originY: Number(f.originY), aisleSpacing: Number(f.aisleSpacing),
    capacityUnits: f.capacityUnits.trim() === "" ? null : Number(f.capacityUnits.replace(",", ".")),
  }
}
