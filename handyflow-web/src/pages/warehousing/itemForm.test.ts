import { describe, it, expect } from "vitest"
import { buildItemPayload, formatMoney, isValidGtin, validateItemForm, ItemFormState } from "./itemForm"

const base: ItemFormState = {
  sku: "A1", description: "Widget", uom: "each", storageRate: "", barcode: "", lengthMm: "", widthMm: "", heightMm: "", weightKg: "",
  unitCost: "", unitCostCurrency: "ZAR", minQty: "", maxQty: "", temperatureClass: "AMBIENT", hazardous: false, fragile: false, stackable: true,
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

  it("cost currency must be a 3-letter code and is sent upper-cased", () => {
    expect(validateItemForm({ ...base, unitCostCurrency: "RAND" })).toMatch(/currency/i)
    expect(validateItemForm({ ...base, unitCostCurrency: "" })).toMatch(/currency/i)
    expect(validateItemForm({ ...base, unitCostCurrency: "usd" })).toBeNull()
    expect(buildItemPayload({ ...base, unitCostCurrency: " usd " }).unitCostCurrency).toBe("USD")
  })

  it("formats an amount with its own currency and falls back for an unknown code", () => {
    expect(formatMoney(12.5, "USD")).toMatch(/12[.,]50/)
    expect(formatMoney(12.5, "USD")).not.toBe(formatMoney(12.5, "ZAR"))
    expect(formatMoney(3, "ZZZ")).toMatch(/3[.,]00/)
    expect(formatMoney(3, null)).toBe(formatMoney(3, "ZAR"))
  })
})
