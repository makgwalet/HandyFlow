// src/pages/agriculture/agStockImport.logic.ts
//
// Reading a stock-items CSV in the browser, before it is sent. Reuses the CSV reading from the animal import (delimiter, quotes,
// byte-order mark). The server checks every row again (POST /farms/{id}/inventory-items/import) and decides what is created.

import { MAX_IMPORT_ROWS, parseCsv } from "./agImport.logic"

export interface StockImportRow {
  itemName: string; category: string; unit: string; quantity: string; unitCost: string
  reorderLevel: string; supplier: string; packSizeKg: string
}
export type StockImportField = keyof StockImportRow

export const REQUIRED_STOCK_FIELDS: StockImportField[] = ["itemName", "category", "unit"]

const HEADER_NAMES: Record<StockImportField, string[]> = {
  itemName: ["item name", "itemname", "item", "name", "product"],
  category: ["category", "type"],
  unit: ["unit", "unit of measure", "unitofmeasure", "uom"],
  quantity: ["quantity", "qty", "opening quantity", "opening stock", "on hand", "stock"],
  unitCost: ["unit cost", "unitcost", "cost", "price", "cost per unit"],
  reorderLevel: ["reorder level", "reorderlevel", "reorder", "min", "minimum"],
  supplier: ["supplier", "vendor"],
  packSizeKg: ["kg per bag", "kg per pack", "pack size", "pack size kg", "packsizekg", "bag size"],
}
const LABELS: Record<StockImportField, string> = {
  itemName: "Item name", category: "Category", unit: "Unit", quantity: "Quantity", unitCost: "Unit cost",
  reorderLevel: "Reorder level", supplier: "Supplier", packSizeKg: "Kg per bag",
}
export const stockFieldLabel = (f: StockImportField) => LABELS[f]

const normHeader = (h: string) => h.replace(/^\uFEFF/, "").trim().toLowerCase().replace(/[_-]+/g, " ").replace(/\s+/g, " ")

export interface ReadStockFile { rows: StockImportRow[]; problems: string[]; ignored: string[] }

export function readStockCsv(text: string): ReadStockFile {
  const table = parseCsv(text).filter(r => r.some(c => c.trim() !== ""))
  if (table.length === 0) return { rows: [], problems: ["The file is empty."], ignored: [] }
  const index: Partial<Record<StockImportField, number>> = {}
  const ignored: string[] = []
  table[0].map(normHeader).forEach((h, i) => {
    const field = (Object.keys(HEADER_NAMES) as StockImportField[]).find(f => HEADER_NAMES[f].includes(h))
    if (field && index[field] === undefined) index[field] = i
    else if (h !== "") ignored.push(table[0][i].replace(/^\uFEFF/, "").trim())
  })
  const problems: string[] = []
  const missing = REQUIRED_STOCK_FIELDS.filter(f => index[f] === undefined)
  if (missing.length > 0) problems.push(`The first row must name these columns: ${missing.map(stockFieldLabel).join(", ")}.`)
  const body = table.slice(1)
  if (missing.length === 0 && body.length === 0) problems.push("The file has a header but no stock items.")
  if (body.length > MAX_IMPORT_ROWS) problems.push(`Import at most ${MAX_IMPORT_ROWS} stock items at a time (the file has ${body.length}). Split it and import in parts.`)
  if (problems.length > 0) return { rows: [], problems, ignored }
  const get = (r: string[], f: StockImportField) => (index[f] === undefined ? "" : (r[index[f] as number] ?? "").trim())
  const rows = body.map(r => ({
    itemName: get(r, "itemName"), category: get(r, "category"), unit: get(r, "unit"), quantity: get(r, "quantity"),
    unitCost: get(r, "unitCost"), reorderLevel: get(r, "reorderLevel"), supplier: get(r, "supplier"), packSizeKg: get(r, "packSizeKg"),
  }))
  return { rows, problems: [], ignored }
}

export interface StockImportRowResult { line: number; itemName: string; status: "OK" | "DUPLICATE" | "ERROR"; message: string | null }
export interface StockImportResult { dryRun: boolean; total: number; ready: number; duplicates: number; errors: number; rows: StockImportRowResult[] }

export function stockResultSummary(r: StockImportResult): string {
  const parts = [`${r.ready} ready`]
  if (r.duplicates > 0) parts.push(`${r.duplicates} already in stock or repeated`)
  if (r.errors > 0) parts.push(`${r.errors} with a problem`)
  return `${parts.join(", ")}.`
}

export function stockImportButtonLabel(r: StockImportResult | null): string | null {
  if (!r || r.ready === 0) return null
  return `Import ${r.ready} ${r.ready === 1 ? "item" : "items"}`
}

export function stockDoneMessage(r: StockImportResult): string {
  const skipped = r.duplicates + r.errors
  return `${r.ready} stock ${r.ready === 1 ? "item" : "items"} imported${skipped > 0 ? `, ${skipped} skipped` : ""}.`
}

export function stockRowsNeedingAttention(r: StockImportResult): StockImportRowResult[] {
  return r.rows.filter(x => x.status !== "OK")
}

export const STOCK_TEMPLATE_CSV =
  "Item name,Category,Unit,Quantity,Unit cost,Reorder level,Supplier,Kg per bag\n" +
  "Layer mash,Feed,bags,40,285.50,10,Epol,50\n" +
  "Lucerne,Feed,bales,120,95,30,,\n" +
  "Ivomec,Veterinary,mL,500,1.80,100,Vet shop,\n"
