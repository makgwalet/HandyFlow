import { describe, it, expect } from "vitest"
import {
  buildReceiptPayload, buildReturnReceivePayload, buildTrackingPayload, daysLabel, emptyReceipt, emptyReturnReceive, parseQty, shelfLifeWarning,
  validateClose, validateInspection, validateNewReturn, validateReason, validateReceipt, validateReturnReceive, validateTracking,
} from "./receiving"

const plain = { lotTracked: false, expiryTracked: false }
const lot = { lotTracked: true, expiryTracked: true, minShelfLifeDays: 30 }
const ok = { ...emptyReceipt, qtyGood: "10", locationId: "L1" }

describe("receipt form", () => {
  it("parses quantities with a decimal comma and blanks as zero", () => {
    expect(parseQty("")).toBe(0); expect(parseQty("2,5")).toBe(2.5); expect(parseQty("abc")).toBeNull(); expect(parseQty("-3")).toBe(-3)
  })
  it("accepts good stock with a location", () => { expect(validateReceipt(ok, plain)).toBeNull() })
  it("needs a quantity, numbers, no negatives and a location", () => {
    expect(validateReceipt({ ...ok, qtyGood: "" }, plain)).toMatch(/Enter a quantity/)
    expect(validateReceipt({ ...ok, qtyGood: "x" }, plain)).toMatch(/numbers/)
    expect(validateReceipt({ ...ok, qtyDamaged: "-1" }, plain)).toMatch(/negative/)
    expect(validateReceipt({ ...ok, locationId: "" }, plain)).toMatch(/put away/)
  })
  it("needs a reason when anything is damaged or held", () => {
    expect(validateReceipt({ ...ok, qtyDamaged: "2" }, plain)).toMatch(/wrong with/)
    expect(validateReceipt({ ...ok, qtyHeld: "2", reason: "awaiting QC" }, plain)).toBeNull()
  })
  it("needs the lot and expiry for tracked items, and a sane date order", () => {
    expect(validateReceipt(ok, lot)).toMatch(/lot number/)
    expect(validateReceipt({ ...ok, lotNumber: "L1" }, lot)).toMatch(/expiry date/)
    expect(validateReceipt({ ...ok, lotNumber: "L1", expiryDate: "2027-01-01" }, lot)).toBeNull()
    expect(validateReceipt({ ...ok, lotNumber: "L1", expiryDate: "2027-01-01", manufacturedDate: "2027-02-01" }, lot)).toMatch(/before the manufactured/)
  })
  it("sends lot fields only for tracked items and blanks as null", () => {
    expect(buildReceiptPayload({ ...ok, lotNumber: " L1 ", expiryDate: "2027-01-01", manufacturedDate: "2026-06-01" }, lot))
      .toMatchObject({ qtyGood: 10, qtyDamaged: 0, qtyHeld: 0, lotNumber: "L1", expiryDate: "2027-01-01", manufacturedDate: "2026-06-01", reason: null })
    expect(buildReceiptPayload({ ...ok, lotNumber: "L1", expiryDate: "2027-01-01" }, plain)).toMatchObject({ lotNumber: null, expiryDate: null, manufacturedDate: null })
  })
  it("warns about short shelf life without refusing", () => {
    expect(shelfLifeWarning("2026-11-20", "2026-11-10", 30)).toMatch(/Only 10 days/)
    expect(shelfLifeWarning("2026-12-30", "2026-11-10", 30)).toBeNull()
    expect(shelfLifeWarning("2026-11-01", "2026-11-10", 30)).toMatch(/expired/)
    expect(shelfLifeWarning("", "2026-11-10", 30)).toBeNull()
  })
})

describe("returns forms", () => {
  const r = { ...emptyReturnReceive, qtyRestock: "3", locationId: "L1" }
  it("checks a return receipt", () => {
    expect(validateReturnReceive(r, plain)).toBeNull()
    expect(validateReturnReceive({ ...r, qtyDamaged: "1" }, plain)).toMatch(/wrong with/)
    expect(validateReturnReceive({ ...r, locationId: "" }, plain)).toMatch(/put away/)
    expect(validateReturnReceive(r, lot)).toMatch(/lot/)
    expect(buildReturnReceivePayload({ ...r, lotNumber: " L2 " }, lot)).toMatchObject({ qtyRestock: 3, lotNumber: "L2" })
    expect(buildReturnReceivePayload(r, plain).lotNumber).toBeNull()
  })
  it("checks a new return", () => {
    expect(validateNewReturn("ab", [{ itemId: "i", qty: "1" }])).toMatch(/why/)
    expect(validateNewReturn("damaged in transit", [{ itemId: "", qty: "" }])).toMatch(/at least one/)
    expect(validateNewReturn("damaged in transit", [{ itemId: "", qty: "2" }])).toMatch(/Choose an item/)
    expect(validateNewReturn("damaged in transit", [{ itemId: "i", qty: "0" }])).toMatch(/above zero/)
    expect(validateNewReturn("damaged in transit", [{ itemId: "i", qty: "1" }, { itemId: "i", qty: "2" }])).toMatch(/once/)
    expect(validateNewReturn("damaged in transit", [{ itemId: "i", qty: "1" }, { itemId: "", qty: "" }])).toBeNull()
  })
})

describe("inspection, closing, reasons", () => {
  it("checks an inspection", () => {
    expect(validateInspection("", "looks fine to me", 5)).toMatch(/quantity/)
    expect(validateInspection("6", "looks fine to me", 5)).toMatch(/Only 5/)
    expect(validateInspection("2", "ok", 5)).toMatch(/reason/)
    expect(validateInspection("2", "looks fine to me", 5)).toBeNull()
  })
  it("needs a note to close a delivery with discrepancies", () => {
    expect(validateClose(true, " ")).toMatch(/discrepancies/)
    expect(validateClose(true, "short delivery agreed with driver")).toBeNull()
    expect(validateClose(false, "")).toBeNull()
  })
  it("needs a reason", () => { expect(validateReason("no", "blocking the lot")).toMatch(/blocking the lot/); expect(validateReason("supplier recall", "x")).toBeNull() })
  it("labels days to expiry", () => {
    expect(daysLabel(-1)).toBe("expired 1 day ago"); expect(daysLabel(-5)).toBe("expired 5 days ago"); expect(daysLabel(0)).toBe("expires today")
    expect(daysLabel(1)).toBe("1 day left"); expect(daysLabel(12)).toBe("12 days left")
  })
})

describe("item tracking", () => {
  it("expiry needs lot tracking, and shelf life needs expiry", () => {
    expect(validateTracking({ lotTracked: false, expiryTracked: true, minShelfLifeDays: "" })).toMatch(/lot tracked/)
    expect(validateTracking({ lotTracked: true, expiryTracked: false, minShelfLifeDays: "10" })).toMatch(/only applies/)
    expect(validateTracking({ lotTracked: true, expiryTracked: true, minShelfLifeDays: "1.5" })).toMatch(/whole number/)
    expect(validateTracking({ lotTracked: true, expiryTracked: true, minShelfLifeDays: "30" })).toBeNull()
    expect(buildTrackingPayload({ lotTracked: true, expiryTracked: true, minShelfLifeDays: "" })).toEqual({ lotTracked: true, expiryTracked: true, minShelfLifeDays: null })
  })
})
