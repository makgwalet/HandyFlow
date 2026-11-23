import { describe, it, expect } from "vitest"
import {
  readStockCsv, stockDoneMessage, stockImportButtonLabel, stockResultSummary, stockRowsNeedingAttention, STOCK_TEMPLATE_CSV,
  type StockImportResult,
} from "./agStockImport.logic"

const result = (o: Partial<StockImportResult> = {}): StockImportResult => ({ dryRun: true, total: 3, ready: 1, duplicates: 1, errors: 1, rows: [], ...o })

describe("readStockCsv", () => {
  it("reads the template exactly", () => {
    const r = readStockCsv(STOCK_TEMPLATE_CSV)
    expect(r.problems).toEqual([])
    expect(r.rows).toHaveLength(3)
    expect(r.rows[0]).toEqual({ itemName: "Layer mash", category: "Feed", unit: "bags", quantity: "40", unitCost: "285.50", reorderLevel: "10", supplier: "Epol", packSizeKg: "50" })
    expect(r.rows[1].supplier).toBe("")
  })
  it("reads a semicolon file with decimal commas, headers in any order and the usual spellings", () => {
    const r = readStockCsv("\uFEFFProduct;Qty;UOM;Type;Price\nMaize;12,5;kg;feed;4,20\n")
    expect(r.problems).toEqual([])
    expect(r.rows[0]).toMatchObject({ itemName: "Maize", quantity: "12,5", unit: "kg", category: "feed", unitCost: "4,20" })
  })
  it("says which required columns are missing", () => {
    const r = readStockCsv("Item name,Quantity\nMaize,5\n")
    expect(r.rows).toEqual([])
    expect(r.problems[0]).toBe("The first row must name these columns: Category, Unit.")
  })
  it("lists columns it does not use", () => {
    expect(readStockCsv("Item name,Category,Unit,Colour\nA,feed,kg,red\n").ignored).toEqual(["Colour"])
  })
  it("says so for an empty file and a header with no items", () => {
    expect(readStockCsv("").problems).toEqual(["The file is empty."])
    expect(readStockCsv("Item name,Category,Unit\n").problems).toEqual(["The file has a header but no stock items."])
  })
  it("refuses more than 500 items", () => {
    const lines = ["Item name,Category,Unit"]
    for (let i = 0; i < 501; i++) lines.push(`Item ${i},feed,kg`)
    const r = readStockCsv(lines.join("\n"))
    expect(r.rows).toEqual([])
    expect(r.problems[0]).toContain("at most 500 stock items")
    expect(r.problems[0]).toContain("501")
  })
  it("skips blank lines and keeps quoted commas in names", () => {
    const r = readStockCsv('Item name,Category,Unit\n\n"Mash, layer",feed,kg\n')
    expect(r.rows).toHaveLength(1)
    expect(r.rows[0].itemName).toBe("Mash, layer")
  })
})

describe("messages", () => {
  it("summarises", () => {
    expect(stockResultSummary(result())).toBe("1 ready, 1 already in stock or repeated, 1 with a problem.")
    expect(stockResultSummary(result({ duplicates: 0, errors: 0 }))).toBe("1 ready.")
  })
  it("labels the import button, or nothing when there is nothing to import", () => {
    expect(stockImportButtonLabel(null)).toBeNull()
    expect(stockImportButtonLabel(result({ ready: 0 }))).toBeNull()
    expect(stockImportButtonLabel(result({ ready: 1 }))).toBe("Import 1 item")
    expect(stockImportButtonLabel(result({ ready: 5 }))).toBe("Import 5 items")
  })
  it("says what was done", () => {
    expect(stockDoneMessage(result({ ready: 1, duplicates: 0, errors: 0 }))).toBe("1 stock item imported.")
    expect(stockDoneMessage(result({ ready: 4 }))).toBe("4 stock items imported, 2 skipped.")
  })
  it("lists only rows that need attention", () => {
    const rows = [
      { line: 1, itemName: "A", status: "OK" as const, message: null },
      { line: 2, itemName: "B", status: "ERROR" as const, message: "x" },
      { line: 3, itemName: "C", status: "DUPLICATE" as const, message: "y" },
    ]
    expect(stockRowsNeedingAttention(result({ rows })).map(r => r.line)).toEqual([2, 3])
  })
})
