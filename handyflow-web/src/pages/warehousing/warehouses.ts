// Pure helpers for choosing and setting up warehouses. The server checks everything again.

export interface WarehouseLite { id: string; code: string; name: string; active: boolean; isDefault: boolean }

/** The selector is only worth showing when there is a real choice. */
export const showSelector = (list: WarehouseLite[]) => list.filter(w => w.active).length > 1

/** The warehouse to work in: the one chosen if it is still on, else the default, else the first one that is on. Undefined when there is nothing to choose. */
export function pickWarehouse(list: WarehouseLite[], chosen: string | null | undefined): string | undefined {
  const on = list.filter(w => w.active)
  if (on.length < 2) return undefined
  return on.find(w => w.id === chosen)?.id ?? on.find(w => w.isDefault)?.id ?? on[0].id
}

export const normaliseCode = (raw: string) => raw.trim().toUpperCase()

/** Null when the code and name can be saved, else what is wrong. Mirrors the server. */
export function warehouseProblem(code: string, name: string, existing: WarehouseLite[]): string | null {
  const c = normaliseCode(code)
  if (!/^[A-Z0-9][A-Z0-9_-]{0,19}$/.test(c)) return "The code is 1 to 20 letters, digits, dashes or underscores (for example CPT or JHB-2)."
  if (existing.some(w => w.code === c)) return `A warehouse with code ${c} already exists.`
  if (!name.trim()) return "Name the warehouse."
  if (name.trim().length > 120) return "The name can be at most 120 characters."
  return null
}

/** Query parameters for the warehouse in use; none when there is only one. */
export const scope = (warehouseId: string | undefined) => (warehouseId ? { params: { warehouseId } } : undefined)

/** The same, as a query string for a url. */
export const scopeQuery = (warehouseId: string | undefined) => (warehouseId ? `?warehouseId=${encodeURIComponent(warehouseId)}` : "")
