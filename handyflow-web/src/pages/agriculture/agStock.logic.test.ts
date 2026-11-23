import { describe, it, expect } from "vitest"
import { packSizeError, packSizeToSend, averageCostAfterReceipt, adjustmentPreview, adjustmentText, adjustError, receiveError } from "./agStock.logic"

describe("averageCostAfterReceipt", () => {
  it("weighs what was on hand", () => {
    expect(averageCostAfterReceipt(100, 10, 300, 14)).toBe(13)
    expect(averageCostAfterReceipt(200, 10, 100, 12)).toBe(10.67)
  })
  it("takes the delivery's cost when nothing is on hand or no cost was known", () => {
    expect(averageCostAfterReceipt(0, 10, 300, 14)).toBe(14)
    expect(averageCostAfterReceipt(50, null, 300, 14)).toBe(14)
  })
  it("leaves the cost alone when none is given", () => {
    expect(averageCostAfterReceipt(100, 10, 300, null)).toBe(10)
    expect(averageCostAfterReceipt(100, null, 300, null)).toBeNull()
  })
})

describe("adjustment", () => {
  it("knows the direction and size", () => {
    expect(adjustmentPreview(10, 12.5)).toEqual({ direction: "up", size: 2.5 })
    expect(adjustmentPreview(10, 4)).toEqual({ direction: "down", size: 6 })
    expect(adjustmentPreview(10, 10)).toEqual({ direction: "none", size: 0 })
  })
  it("explains it", () => {
    expect(adjustmentText(10, 4, "kg")).toBe("This writes 6 kg off the stock (10 → 4).")
    expect(adjustmentText(10, 12, "kg")).toBe("This writes 2 kg on to the stock (10 → 12).")
    expect(adjustmentText(10, 10, "kg")).toMatch(/nothing to adjust/)
  })
  it("needs a count, a change and a reason", () => {
    expect(adjustError("", "Stock count", 10)).toMatch(/counted quantity/)
    expect(adjustError("-1", "Stock count", 10)).toMatch(/counted quantity/)
    expect(adjustError("10", "Stock count", 10)).toMatch(/nothing to adjust/)
    expect(adjustError("8", "  ", 10)).toMatch(/reason/)
    expect(adjustError("8", "Stock count", 10)).toBeNull()
    expect(adjustError("0", "Spilled", 10)).toBeNull()
  })
})

describe("receiveError", () => {
  it("needs a quantity, and a cost if given must not be negative", () => {
    expect(receiveError("", "")).toMatch(/greater than 0/)
    expect(receiveError("0", "")).toMatch(/greater than 0/)
    expect(receiveError("5", "-1")).toMatch(/negative/)
    expect(receiveError("5", "")).toBeNull()
    expect(receiveError("5", "12.5")).toBeNull()
  })
})

describe("kg per bag", () => {
  it("accepts empty, and a believable value for a count unit", () => {
    expect(packSizeError("bags", "")).toBeNull()
    expect(packSizeError("bags", "50")).toBeNull()
    expect(packSizeError("sack", "10000")).toBeNull()
  })
  it("refuses zero, negatives, too much and a weighed unit", () => {
    expect(packSizeError("bags", "0")).toMatch(/more than zero/)
    expect(packSizeError("bags", "-5")).toMatch(/more than zero/)
    expect(packSizeError("bags", "10001")).toMatch(/not credible/)
    expect(packSizeError("kg", "50")).toMatch(/only applies/)
  })
  it("sends a number or null", () => {
    expect(packSizeToSend("bags", "50")).toBe(50)
    expect(packSizeToSend("bags", " ")).toBeNull()
    expect(packSizeToSend("kg", "50")).toBeNull()
  })
})
