import { describe, it, expect } from "vitest"
import { bagsToKg, feedEquivalent, feedQuantityPayload, hasPackSize, isCountUnit, kgToBags, unitSingular, areaChoices, feedChoices, feedPrefill, feedStockProblem, movementFormError, isPartGroupMove } from "./agPickers.logic"

const area = (id: string, name: string, status = "ACTIVE") => ({ id, name, areaType: "CAMP", status })
const item = (o: Partial<{ id: string; itemName: string; unitOfMeasure: string; currentQuantity: number; unitCost: number | null; status: string; packSizeKg: number | null }> = {}) =>
  ({ id: "i1", itemName: "Starter", unitOfMeasure: "kg", currentQuantity: 100, unitCost: 5, status: "ACTIVE", ...o })

describe("areaChoices", () => {
  it("leaves out the current area and inactive areas, sorted by name", () => {
    const r = areaChoices([area("a", "Zebra camp"), area("b", "Barn"), area("c", "Old", "INACTIVE"), area("d", "Home")], "d")
    expect(r.map(c => c.id)).toEqual(["b", "a"])
  })
  it("still offers resting and quarantine areas, labelled", () => {
    const r = areaChoices([area("a", "Pen", "QUARANTINE"), area("b", "Veld", "RESTING")], null)
    expect(r.map(c => c.label)).toEqual(["Pen (camp) · quarantine", "Veld (camp) · resting"])
  })
  it("shows an empty list when there is nowhere to move to", () => {
    expect(areaChoices([area("a", "Only")], "a")).toEqual([])
  })
})

describe("feedChoices", () => {
  it("offers active mass-unit items with the stock on hand", () => {
    const r = feedChoices([item({ currentQuantity: 20 })])
    expect(r).toEqual([{ id: "i1", disabled: false, label: "Starter · 20 kg on hand" }])
  })
  it("hides inactive items", () => {
    expect(feedChoices([item({ status: "INACTIVE" })])).toEqual([])
  })
  it("shows a bag-counted item but cannot pick it, and says why", () => {
    const [c] = feedChoices([item({ unitOfMeasure: "bags" })])
    expect(c.disabled).toBe(true)
    expect(c.label).toMatch(/counted in bags/)
  })
  it("accepts tonnes and the long spellings", () => {
    expect(feedChoices([item({ unitOfMeasure: "Tonnes" })])[0].disabled).toBe(false)
  })
})

describe("feedPrefill", () => {
  it("uses the item name and cost per kg", () => {
    expect(feedPrefill(item({ unitCost: 5 }))).toEqual({ feedType: "Starter", costPerKg: 5 })
  })
  it("converts a per-tonne cost to per kg", () => {
    expect(feedPrefill(item({ unitOfMeasure: "t", unitCost: 8500 })).costPerKg).toBe(8.5)
  })
  it("has no cost when the item has none or is not a mass unit", () => {
    expect(feedPrefill(item({ unitCost: null })).costPerKg).toBeNull()
    expect(feedPrefill(item({ unitOfMeasure: "bags", unitCost: 300 })).costPerKg).toBeNull()
  })
})

describe("feedStockProblem", () => {
  it("is fine when there is enough", () => {
    expect(feedStockProblem(item({ currentQuantity: 100 }), 50)).toBeNull()
    expect(feedStockProblem(item({ currentQuantity: 50 }), 50)).toBeNull()
  })
  it("says how much is on hand when there is not enough", () => {
    expect(feedStockProblem(item({ currentQuantity: 20 }), 50)).toMatch(/Only 20 kg of Starter on hand/)
  })
  it("compares in the item's own unit", () => {
    expect(feedStockProblem(item({ unitOfMeasure: "t", currentQuantity: 0.04 }), 50)).toMatch(/Only 0.04 t/)
    expect(feedStockProblem(item({ unitOfMeasure: "t", currentQuantity: 1 }), 50)).toBeNull()
  })
  it("says bags cannot be converted", () => {
    expect(feedStockProblem(item({ unitOfMeasure: "bags" }), 10)).toMatch(/counted in bags/)
  })
  it("says nothing for an empty quantity", () => {
    expect(feedStockProblem(item(), 0)).toBeNull()
    expect(feedStockProblem(item(), NaN)).toBeNull()
  })
})

describe("movementFormError", () => {
  const base = { type: "AREA_TRANSFER", toAreaId: "a1", targetType: "animal" as const, countMoved: "", currentCount: null }
  it("accepts a valid area move", () => { expect(movementFormError(base)).toBeNull() })
  it("needs an area for an area transfer, but not for a sale", () => {
    expect(movementFormError({ ...base, toAreaId: "" })).toMatch(/Choose the production area/)
    expect(movementFormError({ ...base, type: "SALE", toAreaId: "" })).toBeNull()
  })
  it("checks a group's count", () => {
    const g = { ...base, targetType: "group" as const, currentCount: 50 }
    expect(movementFormError({ ...g, countMoved: "20" })).toBeNull()
    expect(movementFormError({ ...g, countMoved: "" })).toBeNull()
    expect(movementFormError({ ...g, countMoved: "0" })).toMatch(/1 or more/)
    expect(movementFormError({ ...g, countMoved: "2.5" })).toMatch(/whole number/)
    expect(movementFormError({ ...g, countMoved: "51" })).toMatch(/only has 50/)
  })
})

describe("isPartGroupMove", () => {
  const g = { type: "AREA_TRANSFER", toAreaId: "a", targetType: "group" as const, currentCount: 50 }
  it("is true only for fewer head than the group has", () => {
    expect(isPartGroupMove({ ...g, countMoved: "20" })).toBe(true)
    expect(isPartGroupMove({ ...g, countMoved: "50" })).toBe(false)
    expect(isPartGroupMove({ ...g, countMoved: "" })).toBe(false)
    expect(isPartGroupMove({ ...g, countMoved: "20", targetType: "animal" })).toBe(false)
    expect(isPartGroupMove({ ...g, countMoved: "20", type: "SALE" })).toBe(false)
  })
})

describe("movementFormError, farm transfer and sale", () => {
  const b = { type: "FARM_TRANSFER", toAreaId: "", targetType: "animal" as const, countMoved: "", currentCount: null }
  it("needs a farm for a farm transfer", () => {
    expect(movementFormError(b)).toMatch(/Choose the farm/)
    expect(movementFormError({ ...b, toFarmId: "f2" })).toBeNull()
  })
  it("refuses a part-group farm transfer", () => {
    const g = { ...b, targetType: "group" as const, toFarmId: "f2", currentCount: 50 }
    expect(movementFormError({ ...g, countMoved: "20" })).toMatch(/whole group/)
    expect(movementFormError({ ...g, countMoved: "50" })).toBeNull()
    expect(movementFormError({ ...g, countMoved: "" })).toBeNull()
  })
  it("needs a reason for a group sale or transfer out, not for an animal", () => {
    const g = { ...b, type: "SALE", targetType: "group" as const, currentCount: 50 }
    expect(movementFormError(g)).toMatch(/reason/)
    expect(movementFormError({ ...g, reason: "Sold to Van der Merwe" })).toBeNull()
    expect(movementFormError({ ...g, type: "TRANSFER_OUT", reason: "x" })).toMatch(/reason/)
    expect(movementFormError({ ...b, type: "SALE" })).toBeNull()
  })
})

describe("kg per bag", () => {
  const bag = (o = {}) => item({ unitOfMeasure: "bags", currentQuantity: 10, unitCost: 450, packSizeKg: 50, ...o })
  it("knows count units and singulars", () => {
    expect(["bag", "Bags", " bale ", "sacks"].every(isCountUnit)).toBe(true)
    expect(["kg", "t", "crates", ""].some(isCountUnit)).toBe(false)
    expect(unitSingular("Bags")).toBe("bag")
    expect(unitSingular("sack")).toBe("sack")
  })
  it("has a pack size only for a count unit with a positive value", () => {
    expect(hasPackSize(bag())).toBe(true)
    expect(hasPackSize(bag({ packSizeKg: null }))).toBe(false)
    expect(hasPackSize(bag({ packSizeKg: 0 }))).toBe(false)
    expect(hasPackSize(item({ unitOfMeasure: "kg", packSizeKg: 50 }))).toBe(false)
  })
  it("converts between kg and bags to three decimals", () => {
    expect(kgToBags(120, 50)).toBe(2.4)
    expect(kgToBags(10, 30)).toBe(0.333)
    expect(bagsToKg(2.4, 50)).toBe(120)
  })
  it("offers a bag item with a pack size, and blocks one without, with the prompt", () => {
    const ok = feedChoices([bag()])[0]
    expect(ok.disabled).toBe(false)
    expect(ok.label).toMatch(/50 kg per bag/)
    const blocked = feedChoices([bag({ packSizeKg: null })])[0]
    expect(blocked.disabled).toBe(true)
    expect(blocked.label).toMatch(/set kg per bag in Stock/)
  })
  it("prefills cost per kg as price per bag over kg per bag", () => {
    expect(feedPrefill(bag()).costPerKg).toBe(9)
    expect(feedPrefill(bag({ unitCost: 260, packSizeKg: 30 })).costPerKg).toBe(8.6667)
  })
  it("checks stock in bags", () => {
    expect(feedStockProblem(bag(), 120)).toBeNull()
    expect(feedStockProblem(bag({ currentQuantity: 2 }), 120)).toMatch(/Only 2 bags of Starter on hand \(120 kg is 2.4 bags\)/)
    expect(feedStockProblem(bag({ packSizeKg: null }), 120)).toMatch(/Set kg per bag in Stock/)
  })
  it("sends kg or bags, never both", () => {
    expect(feedQuantityPayload("kg", "120")).toEqual({ quantityKg: 120, quantityBags: null })
    expect(feedQuantityPayload("bags", "2.4")).toEqual({ quantityKg: null, quantityBags: 2.4 })
  })
  it("shows the other unit", () => {
    expect(feedEquivalent(bag(), "kg", "120")).toBe("= 2.4 bags")
    expect(feedEquivalent(bag(), "kg", "50")).toBe("= 1 bag")
    expect(feedEquivalent(bag(), "bags", "2.4")).toBe("= 120 kg")
    expect(feedEquivalent(bag({ packSizeKg: null }), "kg", "120")).toBe("")
    expect(feedEquivalent(bag(), "kg", "")).toBe("")
  })
})
