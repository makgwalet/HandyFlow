import { describe, it, expect } from "vitest"
import { buildItemPayload, isValidGtin, validateItemForm, ItemFormState } from "./itemForm"

const base: ItemFormState = {
  sku: "A1", description: "Widget", uom: "each", storageRate: "", barcode: "", lengthMm: "", widthMm: "", heightMm: "", weightKg: "",
  unitCost: "", minQty: "", maxQty: "", temperatureClass: "AMBIENT", hazardous: false, fragile: false, stackable: true,
}

describe("item form", () => {
  it("accepts valid GTINs and rejects a wrong check digit", () => {
    expect(isValidGtin("4006381333931")).toBe(true)
    expect(isValidGtin("036000291452")).toBe(true)
    expect(isValidGtin("4006381333932")).toBe(false)
    expect(isValidGtin("12345")).toBe(false)
  })

  it("passes a form with nothing optional filled in", () => {
    expect(validateItemForm(base)).toBeNull()
  })

  it("catches a bad barcode, a zero size, a negative cost and min above max", () => {
    expect(validateItemForm({ ...base, barcode: "4006381333932" })).toMatch(/not a valid/)
    expect(validateItemForm({ ...base, weightKg: "0" })).toMatch(/Weight/)
    expect(validateItemForm({ ...base, unitCost: "-1" })).toMatch(/Unit cost/)
    expect(validateItemForm({ ...base, minQty: "10", maxQty: "5" })).toMatch(/maximum level/)
  })

  it("sends blanks as null and accepts a decimal comma", () => {
    const p = buildItemPayload({ ...base, unitCost: "12,50", lengthMm: "300", barcode: " 4006381333931 ", fragile: true })
    expect(p.unitCost).toBe(12.5)
    expect(p.lengthMm).toBe(300)
    expect(p.widthMm).toBeNull()
    expect(p.barcode).toBe("4006381333931")
    expect(p.fragile).toBe(true)
    expect(p.storageRatePerUnitPerMonth).toBeNull()
  })
})
