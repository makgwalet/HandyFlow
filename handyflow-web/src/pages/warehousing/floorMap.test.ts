import { describe, it, expect } from "vitest"
import { band, bounds, buildGeneratePayload, expiresSoon, type FloorLocation, type GenerateForm, groupCells, parseAisles, validateGenerate } from "./floorMap"

const loc = (o: Partial<FloorLocation>): FloorLocation => ({
  id: Math.random().toString(), code: "A-01-01", zone: "Z", aisle: "A", bay: 1, level: 1, locationType: "BIN", pickSequence: 1, posX: 0, posY: 0,
  capacityUnits: null, active: true, onHand: 0, allocated: 0, held: 0, skuCount: 0, earliestExpiry: null, utilisationPct: null, ...o,
})

describe("floor map cells", () => {
  it("puts the levels of one bay in one cell, lowest first, and sums the stock", () => {
    const cells = groupCells([
      loc({ code: "A-01-02", level: 2, onHand: 5, held: 2, skuCount: 1, capacityUnits: 10 }),
      loc({ code: "A-01-01", level: 1, onHand: 10, skuCount: 2, capacityUnits: 10, earliestExpiry: "2026-12-01" }),
    ])
    expect(cells).toHaveLength(1)
    expect(cells[0].locations.map(l => l.code)).toEqual(["A-01-01", "A-01-02"])
    expect(cells[0].onHand).toBe(15); expect(cells[0].held).toBe(2); expect(cells[0].skuCount).toBe(3)
    expect(cells[0].utilisationPct).toBe(75)
    expect(cells[0].earliestExpiry).toBe("2026-12-01")
  })

  it("leaves out locations that are not placed, and filters by zone", () => {
    const l = [loc({ posX: null, posY: null }), loc({ posX: 1, posY: 0, zone: "Cold" }), loc({ posX: 2, posY: 0, zone: "Dry" })]
    expect(groupCells(l)).toHaveLength(2)
    expect(groupCells(l, "Cold").map(c => c.x)).toEqual([1])
  })

  it("orders cells row by row and finds the bounds", () => {
    const cells = groupCells([loc({ posX: 3, posY: 2 }), loc({ posX: 1, posY: 0 }), loc({ posX: 2, posY: 0 })])
    expect(cells.map(c => `${c.x},${c.y}`)).toEqual(["1,0", "2,0", "3,2"])
    expect(bounds(cells)).toEqual({ minX: 1, maxX: 3, minY: 0, maxY: 2 })
    expect(bounds([])).toBeNull()
  })

  it("ignores capacity-less levels when working out the share of capacity", () => {
    const [c] = groupCells([loc({ level: 1, onHand: 5, capacityUnits: 10 }), loc({ code: "x", level: 2, onHand: 100, capacityUnits: null })])
    expect(c.utilisationPct).toBe(50)
    expect(c.onHand).toBe(105)
  })
})

describe("colour bands", () => {
  const cell = (onHand: number, pct: number | null, active = true) => ({ ...groupCells([loc({ onHand, capacityUnits: pct == null ? null : 100, active })])[0], utilisationPct: pct })
  it("bands by share of capacity", () => {
    expect(band(cell(0, 0))).toBe("empty")
    expect(band(cell(10, 10))).toBe("low")
    expect(band(cell(60, 60))).toBe("mid")
    expect(band(cell(90, 90))).toBe("high")
    expect(band(cell(120, 120))).toBe("over")
    expect(band(cell(7, null))).toBe("stocked")
    expect(band(cell(7, 10, false))).toBe("inactive")
  })
})

describe("expiry", () => {
  it("flags dates within the window or past", () => {
    expect(expiresSoon("2026-11-20", "2026-11-10")).toBe(true)
    expect(expiresSoon("2026-12-30", "2026-11-10")).toBe(false)
    expect(expiresSoon("2026-11-01", "2026-11-10")).toBe(true)
    expect(expiresSoon(null, "2026-11-10")).toBe(false)
  })
})

describe("generate form", () => {
  const f: GenerateForm = { zone: " Dry ", aisles: "A-C", bays: "10", levels: "3", locationType: "PALLET", originX: "0", originY: "0", aisleSpacing: "2", capacityUnits: "" }
  it("expands aisle ranges and lists", () => {
    expect(parseAisles("A-C")).toEqual(["A", "B", "C"])
    expect(parseAisles("a, b  c;A")).toEqual(["A", "B", "C"])
    expect(parseAisles("D-A")).toEqual([])
    expect(parseAisles("")).toEqual([])
  })
  it("accepts a good form and builds the payload", () => {
    expect(validateGenerate(f)).toBeNull()
    expect(buildGeneratePayload({ ...f, capacityUnits: "1,5" })).toMatchObject({ zone: "Dry", aisles: ["A", "B", "C"], bays: 10, levels: 3, capacityUnits: 1.5 })
    expect(buildGeneratePayload(f).capacityUnits).toBeNull()
  })
  it("catches bad numbers and oversized blocks", () => {
    expect(validateGenerate({ ...f, aisles: "" })).toMatch(/aisle/i)
    expect(validateGenerate({ ...f, bays: "0" })).toMatch(/Bays/)
    expect(validateGenerate({ ...f, levels: "11" })).toMatch(/Levels/)
    expect(validateGenerate({ ...f, aisles: "A-Z", bays: "99" })).toMatch(/2000/)
    expect(validateGenerate({ ...f, originX: "-1" })).toMatch(/Start column/)
    expect(validateGenerate({ ...f, aisleSpacing: "0" })).toMatch(/spacing/)
    expect(validateGenerate({ ...f, capacityUnits: "-3" })).toMatch(/Capacity/)
  })
})
